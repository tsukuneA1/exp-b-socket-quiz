import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Locale;
import java.util.Scanner;
import shared.c2s.ConnectMessage;
import shared.codec.FrameDecoder;
import shared.codec.FrameEncoder;
import shared.codec.MessageType;
import shared.s2c.ConnectAckMessage;
import shared.s2c.ConnectNgMessage;
import shared.s2c.DisconnectAckMessage;
import shared.s2c.GameEndMessage;
import shared.s2c.LobbyStatusMessage;
import shared.s2c.QuestionChunkMessage;
import shared.s2c.QuestionOptionsMessage;
import shared.s2c.RoundEndMessage;
import shared.s2c.ScoreMessage;
import shared.s2c.ServerMessage;
import shared.s2c.WrongAnswerMessage;

public class Client {

  private enum ClientState {
    CONNECTING,
    WAITING,
    READY,
    PLAYING,
    FINISHED,
    DISCONNECTED
  }

  private static volatile boolean running = true;
  private static volatile ClientState state = ClientState.CONNECTING;

  private static String playerName = "Player1";
  private static int playerId = -1;
  private static int readyCount = 0;
  private static int totalCount = 0;
  private static int currentRound = 0;
  private static int optionCount = 0;

  public static void main(String[] args) throws IOException {
    playerName = (args.length > 0) ? args[0] : "Player1";
    int port = (args.length > 1) ? Integer.parseInt(args[1]) : Server.DEFAULT_PORT;

    Socket socket = new Socket(InetAddress.getByName("localhost"), port);

    try {
      DataInputStream in = new DataInputStream(socket.getInputStream());
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());

      System.out.println("=== Socket Quiz Client ===");
      System.out.println("Player name: " + playerName);
      System.out.println("Server: localhost:" + port);
      System.out.println();

      ConnectMessage connect = new ConnectMessage(playerName);
      FrameEncoder.writeFrame(out, MessageType.CONNECT, connect.toBytes());
      System.out.println("Sent CONNECT name=" + playerName);

      FrameDecoder.Frame firstFrame = FrameDecoder.readFrame(in);
      ServerMessage firstMessage = FrameDecoder.decodeServer(firstFrame);

      if (firstMessage instanceof ConnectAckMessage ack) {
        playerId = ack.playerId();
        state = ClientState.WAITING;
        System.out.println("Connected. Your playerId = " + playerId);
        printStatus();
      } else if (firstMessage instanceof ConnectNgMessage ng) {
        System.out.println("Connection failed. reason=" + ng.reason());
        return;
      } else {
        System.out.println("Unexpected first message: " + firstMessage);
        return;
      }

      printHelp();

      Thread receiverThread = new Thread(() -> receiveLoop(in), "client-receiver");
      receiverThread.start();

      inputLoop(out);

      try {
        receiverThread.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

    } finally {
      state = ClientState.DISCONNECTED;
      socket.close();
      System.out.println("Socket closed.");
    }
  }

  private static void inputLoop(DataOutputStream out) {
    Scanner scanner = new Scanner(System.in);

    while (running) {
      System.out.print("> ");

      if (!scanner.hasNextLine()) {
        break;
      }

      String line = scanner.nextLine().trim();

      if (line.isEmpty()) {
        continue;
      }

      try {
        if (line.matches("\\d+")) {
          int answerIndex = Integer.parseInt(line);
          if (answerIndex < 0 || answerIndex > 255) {
            System.out.println("Answer index must be between 0 and 255.");
            continue;
          }
          if (optionCount > 0 && answerIndex >= optionCount) {
            System.out.println("回答番号は 0-" + (optionCount - 1) + " の範囲で入力してください。");
            continue;
          }
          byte[] body = new byte[] {(byte) answerIndex};
          FrameEncoder.writeFrame(out, MessageType.ANSWER, body);
          System.out.println("Sent ANSWER index=" + answerIndex);
          continue;
        }
        String[] parts = line.split("\\s+");
        String command = parts[0].toLowerCase(Locale.ROOT);
        switch (command) {
          case "ready", "r", "準備" -> {
            FrameEncoder.writeFrame(out, MessageType.READY, new byte[0]);
            state = ClientState.READY;
            System.out.println("準備完了にしました。他のプレイヤーを待っています...");
            printStatus();
          }
          case "d", "quit", "exit", "終了" -> {
            FrameEncoder.writeFrame(out, MessageType.DISCONNECT, new byte[0]);
            System.out.println("Sent DISCONNECT");
            running = false;
          }
          case "status", "s", "状態" -> printStatus();
          case "h", "help", "ヘルプ" -> printHelp();
          default -> {
            System.out.println("Unknown command: " + command);
            System.out.println("Type 'h' to show available commands.");
          }
        }
      } catch (NumberFormatException e) {
        System.out.println("Answer index must be a number.");
      } catch (IOException e) {
        System.out.println("Failed to send message: " + e.getMessage());
        running = false;
      }
    }
  }

  private static void receiveLoop(DataInputStream in) {
    while (running) {
      try {
        FrameDecoder.Frame frame = FrameDecoder.readFrame(in);
        ServerMessage message = FrameDecoder.decodeServer(frame);

        if (message instanceof DisconnectAckMessage) {
          System.out.println();
          System.out.println("Received DISCONNECT_ACK");
          System.out.println("Disconnected from server.");
          running = false;
          break;
        } else if (message instanceof ConnectAckMessage ack) {
          playerId = ack.playerId();
          state = ClientState.WAITING;
          System.out.println();
          System.out.println("Received CONNECT_ACK, playerId=" + playerId);
          printStatus();
        } else if (message instanceof ConnectNgMessage ng) {
          System.out.println();
          System.out.println("Received CONNECT_NG, reason=" + ng.reason());
          running = false;
          break;
        } else if (message instanceof QuestionChunkMessage chunk) {
          System.out.print(chunk.chunk());
        } else if (message instanceof QuestionOptionsMessage opts) {
          state = ClientState.PLAYING;
          currentRound++;
          optionCount = opts.options().size();

          System.out.println();
          System.out.println("==============================");
          System.out.println("第" + currentRound + "問");
          System.out.println("==============================");
          System.out.println("--- 選択肢 ---");
          for (int i = 0; i < opts.options().size(); i++) {
            System.out.println("  " + i + ": " + opts.options().get(i));
          }
          System.out.println();
          System.out.println("回答する番号を入力してください。例: 2");
        } else if (message instanceof WrongAnswerMessage) {
          System.out.println();
          System.out.println("不正解！もう一度考えてください。");
        } else if (message instanceof RoundEndMessage end) {
          optionCount = 0;
          System.out.println();
          if (end.winnerId() == 0) {
            System.out.println("全員不正解。正解は選択肢" + end.correctIndex() + "でした。");
          } else if (end.winnerId() == playerId) {
            System.out.println("あなたが正解！正解は選択肢" + end.correctIndex() + "でした。");
          } else {
            System.out.println(end.winnerName() + "が正解！正解は選択肢" + end.correctIndex() + "でした。");
          }
        } else if (message instanceof ScoreMessage score) {
          System.out.println();
          System.out.println("=== スコア ===");
          score
              .scores()
              .forEach(
                  e -> {
                    String marker = e.playerId() == playerId ? " <- you" : "";
                    System.out.println("  " + e.playerName() + ": " + e.score() + "点" + marker);
                  });
          System.out.println("=============");
        } else if (message instanceof LobbyStatusMessage status) {
          readyCount = status.readyCount();
          totalCount = status.totalCount();

          System.out.println();
          System.out.println("Ready: " + readyCount + "/" + totalCount + " players");
          if (readyCount < totalCount) {
            System.out.println("他のプレイヤーの準備を待っています...");
          } else {
            System.out.println("全員準備完了。ゲーム開始を待っています...");
          }
          printStatus();
        } else if (message instanceof GameEndMessage end) {
          state = ClientState.FINISHED;
          optionCount = 0;

          System.out.println();
          System.out.println("=== ゲーム終了 ===");
          if (end.winnerId() == 0) {
            System.out.println("結果: 引き分けです。");
          } else if (end.winnerId() == playerId) {
            System.out.println("結果: あなたの勝ちです！");
          } else {
            System.out.println("勝者: " + end.winnerName());
          }
          printStatus();
          running = false;
          break;
        } else {
          System.out.println();
          System.out.println("Received server message: " + message);
        }

      } catch (EOFException e) {
        System.out.println();
        System.out.println("Server closed the connection.");
        running = false;
        break;
      } catch (IOException e) {
        if (running) {
          System.out.println();
          System.out.println("Connection error: " + e.getMessage());
        }
        running = false;
        break;
      } catch (RuntimeException e) {
        System.out.println();
        System.out.println("Failed to decode server message: " + e.getMessage());
        running = false;
        break;
      }
    }
  }

  private static void printStatus() {
    System.out.println();
    System.out.println("=== 現在の状態 ===");
    System.out.println("Player : " + playerName + " (id=" + playerId + ")");
    System.out.println("Status : " + state);
    if (currentRound > 0) {
      System.out.println("Round  : " + currentRound);
    }
    if (totalCount > 0) {
      System.out.println("Ready  : " + readyCount + "/" + totalCount);
    }
    System.out.println("================");
    System.out.println();
  }

  private static void printHelp() {
    System.out.println();
    System.out.println("Commands:");
    System.out.println("  ready / r / 準備  : signal that you are ready to start");
    System.out.println("  <number>          : send answer index to server");
    System.out.println("  status / s / 状態 : show current client status");
    System.out.println("  d / quit / exit   : disconnect from server");
    System.out.println("  h / help          : show this help");
    System.out.println();
  }
}
