package apps.game;

import apps.server.ClientSession;
import apps.shared.codec.FrameEncoder;
import apps.shared.codec.MessageType;
import apps.shared.s2c.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import metrics.EventBus;

public class GameManager {

  private final LobbyManager lobby;
  private final BlockingQueue<GameEvent> inbox;
  private final List<Question> questions = Question.ALL;

  private final AtomicBoolean accepting = new AtomicBoolean(false);
  private final AtomicBoolean streaming = new AtomicBoolean(false);

  private final int[] scores = new int[GameConfig.MAX_PLAYERS + 1];
  private final AtomicInteger wrongCount = new AtomicInteger(0);
  private volatile int currentCorrectIndex = 0;
  private volatile int currentRound = 0;

  private volatile boolean roundDone = false;

  private final AtomicLong roundStartNs = new AtomicLong(0);
  private final AtomicLong winnerAnswerNs = new AtomicLong(0);

  public GameManager(LobbyManager lobby, BlockingQueue<GameEvent> inbox) {
    this.lobby = lobby;
    this.inbox = inbox;
  }

  public void start() {
    System.out.println("[GameManager] Waiting for all players to be ready...");
    if (!waitForStart()) {
      return;
    }

    System.out.println("[GameManager] Game started. questions=" + questions.size());

    for (int i = 0; i < questions.size(); i++) {
      Question q = questions.get(i);
      currentRound = i + 1;
      System.out.println("[GameManager] Round " + currentRound + ": " + q.text());

      currentCorrectIndex = q.correctIndex();
      wrongCount.set(0);
      roundDone = false;
      winnerAnswerNs.set(0);
      roundStartNs.set(System.nanoTime());

      EventBus.emit(
          "round_start",
          "{"
              + "\"round\":"
              + currentRound
              + ","
              + "\"total\":"
              + questions.size()
              + ","
              + "\"question\":"
              + jsonString(q.text())
              + "}");

      broadcast(MessageType.QUESTION_OPTIONS, new QuestionOptionsMessage(q.options()).toBytes());
      if (!streamQuestionText(q.text())) {
        return;
      }

      while (!roundDone) {
        try {
          handleEvent(takeEvent());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }

      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }

    sendFinalScore();
    System.out.println("[GameManager] Game over.");
  }

  private boolean waitForStart() {
    while (true) {
      try {
        GameEvent event = takeEvent();
        if (event instanceof GameEvent.Start) {
          System.out.println("[GameManager] All players ready. Starting game.");
          return true;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }

  private boolean streamQuestionText(String text) {
    streaming.set(true);
    accepting.set(true);

    int[] codePoints = text.codePoints().toArray();

    for (int cp : codePoints) {
      if (!streaming.get()) {
        System.out.println("[GameManager] Streaming stopped by answer.");
        break;
      }
      String ch = new String(Character.toChars(cp));
      broadcast(MessageType.QUESTION_CHUNK, new QuestionChunkMessage(ch).toBytes());

      try {
        GameEvent event = pollEvent(GameConfig.CHUNK_INTERVAL_MS);
        if (event != null) {
          handleEvent(event);
          drainPendingEvents();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return true;
  }

  private void drainPendingEvents() {
    GameEvent event;
    while (!roundDone && (event = pollEvent()) != null) {
      handleEvent(event);
    }
  }

  private GameEvent takeEvent() throws InterruptedException {
    GameEvent event = inbox.take();
    emitQueueDequeue(event);
    return event;
  }

  private GameEvent pollEvent(long timeoutMs) throws InterruptedException {
    GameEvent event = inbox.poll(timeoutMs, TimeUnit.MILLISECONDS);
    if (event != null) {
      emitQueueDequeue(event);
    }
    return event;
  }

  private GameEvent pollEvent() {
    GameEvent event = inbox.poll();
    if (event != null) {
      emitQueueDequeue(event);
    }
    return event;
  }

  private void handleEvent(GameEvent event) {
    switch (event) {
      case GameEvent.Start ignored ->
          System.out.println("[GameManager] START ignored: already running.");
      case GameEvent.Answer answer ->
          handleAnswer(answer.session(), answer.answerIndex(), answer.receivedNs());
    }
  }

  private void handleAnswer(ClientSession session, int answerIndex, long receivedNs) {
    long enterNs = System.nanoTime();
    String playerName = session.getPlayerName();
    int playerId = session.getPlayerId();

    emitAnswerTrace("received", session, answerIndex, receivedNs);
    emitAnswerTrace("enter", session, answerIndex, enterNs);

    if (!accepting.get()) {
      long winNs = winnerAnswerNs.get();
      if (winNs > 0) {
        long deltaUs = (receivedNs - winNs) / 1_000;
        emitAnswerResult("LATE", session, answerIndex, receivedNs, enterNs, 0, deltaUs);
        EventBus.emit(
            "answer_late",
            "{\"player\":" + jsonString(playerName) + ",\"delta_us\":" + deltaUs + "}");
      }
      return;
    }

    System.out.println("[GameManager] ANSWER: playerId=" + playerId + " index=" + answerIndex);

    if (answerIndex == currentCorrectIndex) {
      long casAttemptNs = System.nanoTime();
      emitAnswerTrace("cas_attempt", session, answerIndex, casAttemptNs);

      if (!accepting.compareAndSet(true, false)) {
        long winNs = winnerAnswerNs.get();
        long deltaUs = winNs > 0 ? (receivedNs - winNs) / 1_000 : 0;
        emitAnswerResult(
            "CAS_FAIL", session, answerIndex, receivedNs, enterNs, casAttemptNs, deltaUs);
        EventBus.emit(
            "answer_cas_rejected",
            "{\"player\":" + jsonString(playerName) + ",\"delta_us\":" + deltaUs + "}");
        return;
      }

      winnerAnswerNs.set(casAttemptNs);
      long roundMs = (receivedNs - roundStartNs.get()) / 1_000_000;
      emitAnswerResult("SUCCESS", session, answerIndex, receivedNs, enterNs, casAttemptNs, 0);

      EventBus.emit(
          "answer_correct",
          "{"
              + "\"player\":"
              + jsonString(playerName)
              + ",\"round\":"
              + currentRound
              + ",\"round_ms\":"
              + roundMs
              + "}");

      streaming.set(false);
      scores[playerId]++;

      System.out.println("[GameManager] Correct! playerId=" + playerId);
      broadcast(
          MessageType.ROUND_END,
          new RoundEndMessage(playerId, currentCorrectIndex, playerName).toBytes());
      broadcastScore();
      roundDone = true;

    } else {
      System.out.println("[GameManager] Wrong answer: playerId=" + playerId);
      emitAnswerResult("WRONG", session, answerIndex, receivedNs, enterNs, 0, 0);
      EventBus.emit(
          "answer_wrong",
          "{\"player\":" + jsonString(playerName) + ",\"round\":" + currentRound + "}");
      sendTo(session.getOut(), MessageType.WRONG_ANSWER, new WrongAnswerMessage().toBytes());

      int wrong = wrongCount.incrementAndGet();
      if (wrong >= lobby.size()) {
        streaming.set(false);
        accepting.set(false);
        long roundMs = (receivedNs - roundStartNs.get()) / 1_000_000;
        EventBus.emit(
            "round_all_wrong", "{\"round\":" + currentRound + ",\"round_ms\":" + roundMs + "}");
        System.out.println("[GameManager] All players answered wrong. Moving to next round.");
        broadcast(MessageType.ROUND_END, new RoundEndMessage(0, currentCorrectIndex, "").toBytes());
        roundDone = true;
      }
    }
  }

  private void broadcast(int type, byte[] body) {
    lobby.broadcast(type, body);
  }

  private void sendTo(DataOutputStream out, int type, byte[] body) {
    try {
      synchronized (out) {
        FrameEncoder.writeFrame(out, type, body);
      }
    } catch (IOException e) {
      System.err.println("[GameManager] Failed to send to client: " + e.getMessage());
    }
  }

  private void broadcastScore() {
    List<ScoreEntry> entries =
        lobby.getSessions().stream()
            .map(s -> new ScoreEntry(s.getPlayerId(), s.getPlayerName(), scores[s.getPlayerId()]))
            .toList();
    broadcast(MessageType.SCORE, new ScoreMessage(entries).toBytes());
  }

  private void sendFinalScore() {
    System.out.println("[GameManager] Sending final scores.");
    broadcastScore();
    ClientSession winner = getWinner();
    int winnerId = winner != null ? winner.getPlayerId() : 0;
    String winnerName = winner != null ? winner.getPlayerName() : "";

    EventBus.emit(
        "game_end", "{\"winner\":" + jsonString(winnerName) + ",\"winner_id\":" + winnerId + "}");

    broadcast(MessageType.GAME_END, new GameEndMessage(winnerId, winnerName).toBytes());
  }

  private ClientSession getWinner() {
    ClientSession winner = null;
    int maxScore = -1;
    boolean tie = false;

    for (ClientSession session : lobby.getSessions()) {
      int score = scores[session.getPlayerId()];
      if (score > maxScore) {
        maxScore = score;
        winner = session;
        tie = false;
      } else if (score == maxScore) {
        tie = true;
      }
    }

    return tie ? null : winner;
  }

  private void emitAnswerTrace(String stage, ClientSession session, int answerIndex, long atNs) {
    EventBus.emit(
        "answer_trace",
        "{"
            + "\"round\":"
            + currentRound
            + ",\"player_id\":"
            + session.getPlayerId()
            + ",\"player\":"
            + jsonString(session.getPlayerName())
            + ",\"answer\":"
            + answerIndex
            + ",\"stage\":"
            + jsonString(stage)
            + ",\"offset_us\":"
            + offsetUs(atNs)
            + "}");
  }

  private void emitAnswerResult(
      String result,
      ClientSession session,
      int answerIndex,
      long receivedNs,
      long enterNs,
      long casAttemptNs,
      long deltaUs) {
    EventBus.emit(
        "answer_result",
        "{"
            + "\"round\":"
            + currentRound
            + ",\"player_id\":"
            + session.getPlayerId()
            + ",\"player\":"
            + jsonString(session.getPlayerName())
            + ",\"answer\":"
            + answerIndex
            + ",\"recv_us\":"
            + offsetUs(receivedNs)
            + ",\"enter_us\":"
            + offsetUs(enterNs)
            + ",\"cas_us\":"
            + (casAttemptNs > 0 ? offsetUs(casAttemptNs) : "null")
            + ",\"result\":"
            + jsonString(result)
            + ",\"delta_us\":"
            + deltaUs
            + "}");
  }

  private void emitQueueDequeue(GameEvent event) {
    long dequeuedNs = System.nanoTime();
    ClientSession session = event.session();
    EventBus.emit(
        "queue_dequeue",
        "{"
            + "\"seq\":"
            + event.sequence()
            + ",\"kind\":"
            + jsonString(event.kind())
            + ",\"player_id\":"
            + session.getPlayerId()
            + ",\"player\":"
            + jsonString(session.getPlayerName())
            + ",\"round\":"
            + currentRound
            + ",\"size\":"
            + inbox.size()
            + ",\"queue_wait_us\":"
            + ((dequeuedNs - event.enqueuedNs()) / 1_000)
            + "}");
  }

  private long offsetUs(long atNs) {
    return (atNs - roundStartNs.get()) / 1_000;
  }

  private static String jsonString(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
