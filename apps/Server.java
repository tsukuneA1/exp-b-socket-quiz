
import game.GameEvent;
import game.GameManager;
import game.LobbyManager;
import server.ClientSession;
import shared.codec.FrameEncoder;
import shared.codec.MessageType;
import shared.s2c.ConnectNgMessage;
import java.io.*;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Server {
  public static final int DEFAULT_PORT = 8080;

  public static void main(String[] args) throws IOException {
    int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;

    BlockingQueue<GameEvent> gameEvents = new LinkedBlockingQueue<>();
    LobbyManager lobby = new LobbyManager();
    GameManager gameManager = new GameManager(lobby, gameEvents);
    lobby.setGameManager(gameManager);

    // ゲーム進行スレッドを起動（全員READYになるまでブロック）
    new Thread(gameManager::start, "game-thread").start();

    try (ServerSocket ss = new ServerSocket(port)) {
      System.out.println("Server started: " + ss);
      System.out.println("Waiting for players... (type 'ready' to start)");

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
      }
    }
  }
}
