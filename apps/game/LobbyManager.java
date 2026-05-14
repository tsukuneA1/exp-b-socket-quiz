package apps.game;

import apps.server.ClientSession;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class LobbyManager {
    private final List<ClientSession> sessions = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextPlayerId = new AtomicInteger(1);

    public boolean isFull() {
        return sessions.size() >= GameConfig.MAX_PLAYERS;
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
}
