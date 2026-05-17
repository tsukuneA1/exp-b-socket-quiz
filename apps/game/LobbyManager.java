package apps.game;

import apps.server.ClientSession;
import apps.shared.codec.FrameEncoder;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class LobbyManager {
    private final List<ClientSession> sessions = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextPlayerId = new AtomicInteger(1);
    private GameManager gameManager;

    public boolean isFull() {
        return sessions.size() >= GameConfig.MAX_PLAYERS;
    }

    public boolean isReady() {
        return sessions.size() >= GameConfig.MIN_PLAYERS;
    }

    public int assignId() {
        return nextPlayerId.getAndIncrement();
    }

    public void add(ClientSession session) {
        sessions.add(session);
    }

    public void remove(ClientSession session) {
        sessions.remove(session);
        System.out.println("Session removed, remaining=" + sessions.size());
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