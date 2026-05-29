package apps.game;

import apps.server.ClientSession;
import apps.shared.codec.FrameEncoder;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LobbyManager {
  private final List<ClientSession> sessions = new CopyOnWriteArrayList<>();
  private final AtomicInteger nextPlayerId = new AtomicInteger(1);
  private GameManager gameManager;

  private final AtomicInteger hostPlayerId = new AtomicInteger(-1);
  private final Set<Integer> readyPlayers = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean gameTriggered = new AtomicBoolean(false);

  public boolean isFull() {
    return sessions.size() >= GameConfig.MAX_PLAYERS;
  }

  public boolean isReady() {
    return sessions.size() >= GameConfig.MIN_PLAYERS;
  }

  public int assignId() {
    int id = nextPlayerId.getAndIncrement();
    if (hostPlayerId.compareAndSet(-1, id)) {
      System.out.println("[LobbyManager] Host assigned: playerId=" + id);
    }
    return id;
  }

  public boolean isHost(int playerId) {
    return playerId == hostPlayerId.get();
  }

  public void add(ClientSession session) {
    sessions.add(session);
  }

  public void remove(ClientSession session) {
    sessions.remove(session);
    System.out.println("Session removed, remaining=" + sessions.size());
  }

  /**
   * プレイヤーをReady状態にする。
   * 全員がReadyかつMIN_PLAYERSを満たした場合、初回のみtrueを返す（ゲームスタートのシグナル）。
   */
  public boolean markReady(int playerId) {
    readyPlayers.add(playerId);
    if (sessions.size() < GameConfig.MIN_PLAYERS) {
      return false;
    }
    boolean allReady =
        sessions.stream().map(ClientSession::getPlayerId).allMatch(readyPlayers::contains);
    return allReady && gameTriggered.compareAndSet(false, true);
  }

  public int readyCount() {
    return readyPlayers.size();
  }

  public int size() {
    return sessions.size();
  }

  public List<ClientSession> getSessions() {
    return List.copyOf(sessions);
  }

  public void setGameManager(GameManager gameManager) {
    this.gameManager = gameManager;
  }

  public GameManager getGameManager() {
    return gameManager;
  }

  public void broadcast(int type, byte[] body) {
    for (ClientSession session : sessions) {
      try {
        synchronized (session.getOut()) {
          FrameEncoder.writeFrame(session.getOut(), type, body);
        }
      } catch (IOException e) {
        System.err.println("[LobbyManager] Failed to broadcast: " + e.getMessage());
      }
    }
  }
}
