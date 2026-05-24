package apps;

import apps.shared.c2s.ConnectMessage;
import apps.shared.codec.FrameDecoder;
import apps.shared.codec.FrameEncoder;
import apps.shared.codec.MessageType;
import apps.shared.s2c.ConnectAckMessage;
import apps.shared.s2c.ConnectNgMessage;
import apps.shared.s2c.DisconnectAckMessage;
import apps.shared.s2c.GameEndMessage;
import apps.shared.s2c.QuestionChunkMessage;
import apps.shared.s2c.QuestionOptionsMessage;
import apps.shared.s2c.RoundEndMessage;
import apps.shared.s2c.ScoreMessage;
import apps.shared.s2c.ServerMessage;
import apps.shared.s2c.WrongAnswerMessage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class Client {

  private static volatile boolean running = true;

  public static void main(String[] args) throws IOException {
    String playerName = (args.length > 0) ? args[0] : "Player1";
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
        System.out.println("Connected. Your playerId = " + ack.playerId());
        if (ack.playerId() == 1) {
          System.out.println("You are the [HOST]. The game can be started by only [HOST]");
        }
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
          byte[] body = new byte[] {(byte) answerIndex};
          FrameEncoder.writeFrame(out, MessageType.ANSWER, body);
          System.out.println("Sent ANSWER index=" + answerIndex);
          continue;
        }
        String[] parts = line.split("\\s+");
        String command = parts[0].toLowerCase();
        switch (command) {
          case "s" -> {
            FrameEncoder.writeFrame(out, MessageType.START, new byte[0]);
            System.out.println("Sent START");
          }
          case "d" -> {
            FrameEncoder.writeFrame(out, MessageType.DISCONNECT, new byte[0]);
            System.out.println("Sent DISCONNECT");
            running = false;
          }
          case "h" -> printHelp();
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
          System.out.println();
          System.out.println("Received CONNECT_ACK, playerId=" + ack.playerId());
        } else if (message instanceof ConnectNgMessage ng) {
          System.out.println();
          System.out.println("Received CONNECT_NG, reason=" + ng.reason());
          running = false;
          break;
        } else if (message instanceof QuestionChunkMessage chunk) {
          System.out.print(chunk.chunk());
        } else if (message instanceof QuestionOptionsMessage opts) {
          System.out.println();
          System.out.println("--- 選択肢 ---");
          for (int i = 0; i < opts.options().size(); i++) {
            System.out.println("  " + i + ": " + opts.options().get(i));
          }
        } else if (message instanceof WrongAnswerMessage) {
          System.out.println();
          System.out.println("不正解！もう一度考えてください。");
        } else if (message instanceof RoundEndMessage end) {
          System.out.println();
          if (end.winnerId() == 0) {
            System.out.println("全員不正解。正解は選択肢" + end.correctIndex() + "でした。");
          } else {
            System.out.println(end.winnerName() + "が正解！正解は選択肢" + end.correctIndex() + "でした。");
          }
        } else if (message instanceof ScoreMessage score) {
          System.out.println("--- スコア ---");
          score
              .scores()
              .forEach(e -> System.out.println("  " + e.playerName() + ": " + e.score() + "点"));
        } else if (message instanceof GameEndMessage end) {
          System.out.println();
          System.out.println("=== ゲーム終了 ===");
          if (end.winnerId() == 0) {
            System.out.println("結果: 引き分けです。");
          } else {
            System.out.println("勝者: " + end.winnerName());
          }
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

  private static void printHelp() {
    System.out.println();
    System.out.println("Commands:");
    System.out.println("  s         : start the game (host only)");
    System.out.println("  <number>  : send answer index to server");
    System.out.println("  d         : disconnect from server");
    // System.out.println("  quit / exit      : same as disconnect");
    System.out.println("  h         : show this help");
    System.out.println();
  }
}
