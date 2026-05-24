package apps;

import apps.game.GameEvent;
import apps.game.GameManager;
import apps.game.LobbyManager;
import apps.server.ClientSession;
import apps.shared.codec.FrameEncoder;
import apps.shared.codec.MessageType;
import apps.shared.s2c.ConnectNgMessage;
import java.io.*;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import metrics.EventBus;

public class Server {
  public static final int DEFAULT_PORT = 8080;

  public static void main(String[] args) throws IOException {
    int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
    EventBus.start(9090);
    LobbyManager lobby = new LobbyManager();
    BlockingQueue<GameEvent> gameEvents = new LinkedBlockingQueue<>();
    GameManager gameManager = new GameManager(lobby, gameEvents);
    lobby.setGameManager(gameManager);
    AtomicBoolean gameStarted = new AtomicBoolean(false);

    try (ServerSocket ss = new ServerSocket(port)) {
      System.out.println("Server started: " + ss);

      while (true) {
        Socket socket = ss.accept();

        if (lobby.isFull()) {
          try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            FrameEncoder.writeFrame(
                out, MessageType.CONNECT_NG, new ConnectNgMessage("FULL").toBytes());
          } catch (IOException ignored) {
          } finally {
            socket.close();
          }
          System.out.println("Rejected: server full");
          continue;
        }

        ClientSession session = new ClientSession(socket, lobby, gameEvents);
        lobby.add(session);
        new Thread(session).start();
        System.out.println("Session started, clients=" + lobby.size());

        if (lobby.isReady() && gameStarted.compareAndSet(false, true)) {
          new Thread(
                  () -> {
                    try {
                      Thread.sleep(1000);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      return;
                    }
                    System.out.println("[Server] Starting game with " + lobby.size() + " players.");
                    gameManager.start();
                  },
                  "game-thread")
              .start();
        }
      }
    }
  }
}
