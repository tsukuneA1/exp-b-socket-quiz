package game;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import metrics.EventBus;
import server.ClientSession;
import shared.codec.FrameEncoder;
import shared.codec.MessageType;
import shared.s2c.*;

public class GameManager {

  private final LobbyManager lobby;
  private final BlockingQueue<GameEvent> inbox;

  private boolean accepting = false;
  private boolean streaming = false;

  private final Map<Integer, Integer> scores = new ConcurrentHashMap<>();
  private int wrongCount = 0;
  private final Set<Integer> wrongPlayers = new HashSet<>();
  private int currentCorrectIndex = 0;
  private int currentRound = 0;

  private boolean roundDone = false;

  private long roundStartNs = 0;
  private long winnerAnswerNs = 0;

  public GameManager(LobbyManager lobby, BlockingQueue<GameEvent> inbox) {
    this.lobby = lobby;
    this.inbox = inbox;
  }

  public void start() {
    while (true) {
      System.out.println("[GameManager] Waiting for all players to be ready...");
      if (!waitForStart()) {
        return;
      }

      resetGameState();
      playGame();
      lobby.resetReadyForNextGame();
    }
  }

  private void playGame() {
    List<Question> questions = new ArrayList<>(Question.ALL);
    Collections.shuffle(questions);
    questions = questions.subList(0, Math.min(GameConfig.QUESTIONS_PER_GAME, questions.size()));
    System.out.println("[GameManager] Game started. questions=" + questions.size());

    for (int i = 0; i < questions.size(); i++) {
      Question q = questions.get(i);
      currentRound = i + 1;
      System.out.println("[GameManager] Round " + currentRound + ": " + q.text());

      currentCorrectIndex = q.correctIndex();
      wrongCount = 0;
      wrongPlayers.clear();
      roundDone = false;
      winnerAnswerNs = 0;
      drainStaleAnswers();
      roundStartNs = System.nanoTime();

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
          GameEvent event = pollEvent(remainingQuestionTimeMs());
          if (event == null) {
            finishRoundByTimeout();
          } else {
            handleEvent(event);
          }
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

  private void resetGameState() {
    scores.clear();
    accepting = false;
    streaming = false;
    wrongCount = 0;
    wrongPlayers.clear();
    currentCorrectIndex = 0;
    currentRound = 0;
    roundDone = false;
    roundStartNs = 0;
    winnerAnswerNs = 0;
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
    streaming = true;
    accepting = true;

    int[] codePoints = text.codePoints().toArray();

    for (int cp : codePoints) {
      if (remainingQuestionTimeMs() <= 0) {
        finishRoundByTimeout();
        break;
      }
      if (!streaming) {
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

  private long remainingQuestionTimeMs() {
    long elapsedMs = (System.nanoTime() - roundStartNs) / 1_000_000;
    return Math.max(0, GameConfig.QUESTION_TIMEOUT_MS - elapsedMs);
  }

  private void finishRoundByTimeout() {
    if (roundDone) {
      return;
    }
    streaming = false;
    accepting = false;
    System.out.println("[GameManager] Time up. Moving to next round.");
    EventBus.emit("round_timeout", "{\"round\":" + currentRound + "}");
    broadcast(
        MessageType.ROUND_END,
        new RoundEndMessage(0, currentCorrectIndex, "TIME_UP").toBytes());
    roundDone = true;
  }

  private void drainPendingEvents() {
    GameEvent event;
    while (!roundDone && (event = pollEvent()) != null) {
      handleEvent(event);
    }
  }

  private void drainStaleAnswers() {
    GameEvent event;
    while ((event = inbox.poll()) != null) {
      if (!(event instanceof GameEvent.Answer answer)) continue;
      System.out.println(
          "[GameManager] Discarded stale ANSWER: player=" + answer.session().getPlayerName());
      EventBus.emit(
          "answer_result",
          "{"
              + "\"round\":"
              + currentRound
              + ",\"player_id\":"
              + answer.session().getPlayerId()
              + ",\"player\":"
              + jsonString(answer.session().getPlayerName())
              + ",\"answer\":"
              + answer.answerIndex()
              + ",\"recv_us\":"
              + offsetUs(answer.receivedNs())
              + ",\"enter_us\":null"
              + ",\"accept_us\":null"
              + ",\"result\":\"DISCARD\""
              + ",\"delta_us\":0"
              + "}");
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

    if (!accepting) {
      if (winnerAnswerNs > 0) {
        long deltaUs = (receivedNs - winnerAnswerNs) / 1_000;
        emitAnswerResult("LATE", session, answerIndex, receivedNs, enterNs, 0, deltaUs);
        EventBus.emit(
            "answer_late",
            "{\"player\":" + jsonString(playerName) + ",\"delta_us\":" + deltaUs + "}");
      }
      return;
    }

    // お手付き判定: すでに誤答済みのプレイヤーは無視する
    if (wrongPlayers.contains(playerId)) {
      System.out.println("[GameManager] Already answered wrong: playerId=" + playerId);
      return;
    }

    System.out.println("[GameManager] ANSWER: playerId=" + playerId + " index=" + answerIndex);

    if (answerIndex == currentCorrectIndex) {
      long acceptAttemptNs = System.nanoTime();
      emitAnswerTrace("accept_attempt", session, answerIndex, acceptAttemptNs);

      accepting = false;
      winnerAnswerNs = acceptAttemptNs;
      long roundMs = (receivedNs - roundStartNs) / 1_000_000;
      emitAnswerResult("SUCCESS", session, answerIndex, receivedNs, enterNs, acceptAttemptNs, 0);

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

      streaming = false;
      scores.merge(playerId, 1, Integer::sum);

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

      wrongPlayers.add(playerId);
      int wrong = ++wrongCount;
      if (wrong >= lobby.size()) {
        streaming = false;
        accepting = false;
        long roundMs = (receivedNs - roundStartNs) / 1_000_000;
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
            .map(
                s ->
                    new ScoreEntry(
                        s.getPlayerId(),
                        s.getPlayerName(),
                        scores.getOrDefault(s.getPlayerId(), 0)))
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
      int score = scores.getOrDefault(session.getPlayerId(), 0);
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
      long acceptAttemptNs,
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
            + ",\"accept_us\":"
            + (acceptAttemptNs > 0 ? offsetUs(acceptAttemptNs) : "null")
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
    return (atNs - roundStartNs) / 1_000;
  }

  private static String jsonString(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
