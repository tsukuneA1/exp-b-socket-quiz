package apps.game;

import apps.server.ClientSession;
import apps.shared.codec.FrameEncoder;
import apps.shared.codec.MessageType;
import apps.shared.s2c.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class GameManager {

  private final LobbyManager lobby;
  private final List<Question> questions = Question.ALL;

  private final AtomicBoolean accepting = new AtomicBoolean(false);

  private final AtomicBoolean streaming = new AtomicBoolean(false);

  private final int[] scores = new int[GameConfig.MAX_PLAYERS + 1];
  private final AtomicInteger wrongCount = new AtomicInteger(0);
  private volatile int currentCorrectIndex = 0;

  private final Object roundLock = new Object();
  private volatile boolean roundDone = false;

  private final CountDownLatch startLatch = new CountDownLatch(1);

  public GameManager(LobbyManager lobby) {
    this.lobby = lobby;
  }

  public void onStart() {
    System.out.println("[GameManager] START received from host.");
    startLatch.countDown();
  }

  public void start() {
    System.out.println("[GameManager] Waiting for host to send START...");
    try {
      startLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    System.out.println("[GameManager] Game started. questions=" + questions.size());

    for (int i = 0; i < questions.size(); i++) {
      Question q = questions.get(i);
      System.out.println("[GameManager] Round " + (i + 1) + ": " + q.text());

      currentCorrectIndex = q.correctIndex();
      wrongCount.set(0);
      roundDone = false;

      broadcast(MessageType.QUESTION_OPTIONS, new QuestionOptionsMessage(q.options()).toBytes());

      streamQuestionText(q.text());

      synchronized (roundLock) {
        while (!roundDone) {
          try {
            roundLock.wait();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
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

  private void streamQuestionText(String text) {
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
        Thread.sleep(GameConfig.CHUNK_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  public void onAnswer(ClientSession session, int answerIndex) {
    if (!accepting.get()) return;

    int playerId = session.getPlayerId();
    System.out.println("[GameManager] ANSWER: playerId=" + playerId + " index=" + answerIndex);

    if (answerIndex == currentCorrectIndex) {
      if (!accepting.compareAndSet(true, false)) return;
      streaming.set(false);
      scores[playerId]++;

      String playerName = session.getPlayerName();
      System.out.println("[GameManager] Correct! playerId=" + playerId);
      broadcast(
          MessageType.ROUND_END,
          new RoundEndMessage(playerId, currentCorrectIndex, playerName).toBytes());
      broadcastScore();

      synchronized (roundLock) {
        roundDone = true;
        roundLock.notifyAll();
      }

    } else {
      System.out.println("[GameManager] Wrong answer: playerId=" + playerId);
      sendTo(session.getOut(), MessageType.WRONG_ANSWER, new WrongAnswerMessage().toBytes());

      int wrong = wrongCount.incrementAndGet();
      if (wrong >= lobby.size()) {
        streaming.set(false);
        accepting.set(false);
        System.out.println("[GameManager] All players answered wrong. Moving to next round.");
        broadcast(
            MessageType.ROUND_END, new RoundEndMessage(0, currentCorrectIndex, "").toBytes());

        synchronized (roundLock) {
          roundDone = true;
          roundLock.notifyAll();
        }
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
}
