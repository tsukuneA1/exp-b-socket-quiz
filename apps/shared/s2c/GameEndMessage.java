package shared.s2c;

import java.nio.charset.StandardCharsets;
import shared.codec.InvalidMessageException;

public record GameEndMessage(int winnerId, String winnerName) implements ServerMessage {
  public GameEndMessage {
    if (winnerId < 0 || winnerId > 255)
      throw new InvalidMessageException("GAME_END winnerId out of byte range: " + winnerId);
    if (winnerName == null) winnerName = "";
  }

  public static GameEndMessage parse(byte[] body) {
    if (body.length < 2) throw new InvalidMessageException("GAME_END body too short");
    int winnerId = body[0] & 0xFF;
    int nameLen = body[1] & 0xFF;
    if (body.length != 2 + nameLen)
      throw new InvalidMessageException("GAME_END body length mismatch");
    String winnerName = new String(body, 2, nameLen, StandardCharsets.UTF_8);
    return new GameEndMessage(winnerId, winnerName);
  }

  public byte[] toBytes() {
    byte[] nameBytes = winnerName.getBytes(StandardCharsets.UTF_8);
    byte[] body = new byte[2 + nameBytes.length];
    body[0] = (byte) winnerId;
    body[1] = (byte) nameBytes.length;
    System.arraycopy(nameBytes, 0, body, 2, nameBytes.length);
    return body;
  }
}
