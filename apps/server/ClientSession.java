package apps.server;

import apps.game.LobbyManager;
import apps.shared.c2s.*;
import apps.shared.codec.*;
import apps.shared.s2c.*;
import java.io.*;
import java.net.*;

public class ClientSession implements Runnable {

  private final Socket socket;
  private final LobbyManager lobby;
  private int playerId;
  private String playerName;
  private DataOutputStream out;

  public ClientSession(Socket socket, LobbyManager lobby) {
    this.socket = socket;
    this.lobby = lobby;
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
            if (lobby.getGameManager() != null) {
              lobby.getGameManager().onAnswer(this, answer.index(), receivedNs);
            }
          }
          case StartMessage ignored -> {
            // ホストだけSTARTを受け付ける
            if (lobby.isHost(playerId)) {
              System.out.println("START: playerId=" + playerId + " [HOST]");
              if (lobby.getGameManager() != null) {
                lobby.getGameManager().onStart();
              }
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
}
