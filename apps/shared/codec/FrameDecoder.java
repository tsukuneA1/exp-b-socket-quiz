package apps.shared.codec;

import apps.shared.c2s.AnswerMessage;
import apps.shared.c2s.ClientMessage;
import apps.shared.c2s.ConnectMessage;
import apps.shared.c2s.DisconnectMessage;
import apps.shared.c2s.ReadyMessage;
import apps.shared.s2c.ConnectAckMessage;
import apps.shared.s2c.ConnectNgMessage;
import apps.shared.s2c.DisconnectAckMessage;
import apps.shared.s2c.GameEndMessage;
import apps.shared.s2c.LobbyStatusMessage;
import apps.shared.s2c.QuestionChunkMessage;
import apps.shared.s2c.QuestionOptionsMessage;
import apps.shared.s2c.RoundEndMessage;
import apps.shared.s2c.ScoreMessage;
import apps.shared.s2c.ServerMessage;
import apps.shared.s2c.WrongAnswerMessage;
import java.io.DataInputStream;
import java.io.IOException;

public class FrameDecoder {

  public record Frame(int type, byte[] body) {}

  public static Frame readFrame(DataInputStream in) throws IOException {
    int type = in.readUnsignedByte();
    int bodyLength = in.readInt();
    byte[] body = new byte[bodyLength];
    in.readFully(body);
    return new Frame(type, body);
  }

  public static ClientMessage decodeClient(Frame frame) {
    return switch (frame.type()) {
      case MessageType.CONNECT -> ConnectMessage.parse(frame.body());
      case MessageType.ANSWER -> AnswerMessage.parse(frame.body());
      case MessageType.DISCONNECT -> DisconnectMessage.parse(frame.body());
      case MessageType.READY -> ReadyMessage.parse(frame.body());
      default ->
          throw new InvalidMessageException(
              "Unknown C→S type: 0x" + Integer.toHexString(frame.type()));
    };
  }

  public static ServerMessage decodeServer(Frame frame) {
    return switch (frame.type()) {
      case MessageType.CONNECT_ACK -> ConnectAckMessage.parse(frame.body());
      case MessageType.CONNECT_NG -> ConnectNgMessage.parse(frame.body());
      case MessageType.DISCONNECT_ACK -> DisconnectAckMessage.parse(frame.body());
      case MessageType.QUESTION_CHUNK -> QuestionChunkMessage.parse(frame.body());
      case MessageType.QUESTION_OPTIONS -> QuestionOptionsMessage.parse(frame.body());
      case MessageType.WRONG_ANSWER -> WrongAnswerMessage.parse(frame.body());
      case MessageType.ROUND_END -> RoundEndMessage.parse(frame.body());
      case MessageType.SCORE -> ScoreMessage.parse(frame.body());
      case MessageType.GAME_END -> GameEndMessage.parse(frame.body()); // ゲーム終了時
      case MessageType.LOBBY_STATUS -> LobbyStatusMessage.parse(frame.body());
      default ->
          throw new InvalidMessageException(
              "Unknown S→C type: 0x" + Integer.toHexString(frame.type()));
    };
  }
}
