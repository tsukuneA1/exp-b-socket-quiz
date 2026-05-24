package apps.server;

import apps.game.GameEvent;
import apps.game.LobbyManager;
import apps.shared.c2s.*;
import apps.shared.codec.*;
import apps.shared.s2c.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import metrics.EventBus;

public class ClientSession implements Runnable {
  private static final AtomicLong EVENT_SEQUENCE = new AtomicLong(1);

  private final Socket socket;
  private final LobbyManager lobby;
  private final BlockingQueue<GameEvent> gameEvents;
  private int playerId;
  private String playerName;
  private DataOutputStream out;

  public ClientSession(Socket socket, LobbyManager lobby, BlockingQueue<GameEvent> gameEvents) {
    this.socket = socket;
    this.lobby = lobby;
    this.gameEvents = gameEvents;
  }

  public int getPlayerId() {
    return playerId;
  }

  public String getPlayerName() {
    return playerName;
  }

  public DataOutputStream getOut() {
    return out;
  }

  @Override
  public void run() {
    try {
      DataInputStream in = new DataInputStream(socket.getInputStream());
      this.out = new DataOutputStream(socket.getOutputStream());

      FrameDecoder.Frame frame = FrameDecoder.readFrame(in);
      ClientMessage first = FrameDecoder.decodeClient(frame);
      if (!(first instanceof ConnectMessage connect)) {
        System.out.println("Expected CONNECT but got: " + first);
        return;
      }
      this.playerId = lobby.assignId();
      this.playerName = connect.playerName();
      FrameEncoder.writeFrame(
          out, MessageType.CONNECT_ACK, new ConnectAckMessage(playerId).toBytes());
      System.out.println(
          "CONNECT_ACK: playerId="
              + playerId
              + " name="
              + playerName
              + (lobby.isHost(playerId) ? " [HOST]" : ""));

      while (true) {
        FrameDecoder.Frame f = FrameDecoder.readFrame(in);
        ClientMessage msg = FrameDecoder.decodeClient(f);
        switch (msg) {
          case DisconnectMessage ignored -> {
            FrameEncoder.writeFrame(
                out, MessageType.DISCONNECT_ACK, new DisconnectAckMessage().toBytes());
            System.out.println("DISCONNECT_ACK: playerId=" + playerId);
            return;
          }
          case AnswerMessage answer -> {
            long receivedNs = System.nanoTime();
            System.out.println("ANSWER: playerId=" + playerId + " index=" + answer.index());
            GameEvent event =
                new GameEvent.Answer(
                    this,
                    answer.index(),
                    receivedNs,
                    System.nanoTime(),
                    EVENT_SEQUENCE.getAndIncrement());
            enqueueGameEvent(event);
          }
          case StartMessage ignored -> {
            // ホストだけSTARTを受け付ける
            if (lobby.isHost(playerId)) {
              System.out.println("START: playerId=" + playerId + " [HOST]");
              GameEvent event =
                  new GameEvent.Start(this, System.nanoTime(), EVENT_SEQUENCE.getAndIncrement());
              enqueueGameEvent(event);
            } else {
              System.out.println("START rejected: playerId=" + playerId + " is not host");
            }
          }
          default -> System.out.println("Unknown message: " + msg);
        }
      }

    } catch (IOException e) {
      System.out.println("Session lost: playerId=" + playerId + " " + e.getMessage());
    } finally {
      lobby.remove(this);
      try {
        socket.close();
      } catch (IOException ignored) {
      }
      System.out.println("Session closed: playerId=" + playerId);
    }
  }

  private void enqueueGameEvent(GameEvent event) {
    gameEvents.offer(event);
    EventBus.emit(
        "queue_enqueue",
        "{"
            + "\"seq\":"
            + event.sequence()
            + ",\"kind\":"
            + jsonString(event.kind())
            + ",\"player_id\":"
            + playerId
            + ",\"player\":"
            + jsonString(playerName)
            + ",\"size\":"
            + gameEvents.size()
            + "}");
  }

  private static String jsonString(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
