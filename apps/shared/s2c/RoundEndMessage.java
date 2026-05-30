package shared.s2c;

import shared.codec.InvalidMessageException;
import java.nio.charset.StandardCharsets;

public record RoundEndMessage(int winnerId, int correctIndex, String winnerName)
    implements ServerMessage {
  public RoundEndMessage {
    if (winnerId < 0 || winnerId > 255)
      throw new InvalidMessageException("ROUND_END winnerId out of byte range: " + winnerId);
    if (correctIndex < 0 || correctIndex > 255)
      throw new InvalidMessageException(
          "ROUND_END correctIndex out of byte range: " + correctIndex);
    if (winnerName == null) winnerName = "";
  }

  public static RoundEndMessage parse(byte[] body) {
    if (body.length < 3) throw new InvalidMessageException("ROUND_END body too short");
    int winnerId = body[0] & 0xFF;
    int correctIndex = body[1] & 0xFF;
    int nameLen = body[2] & 0xFF;
    if (body.length != 3 + nameLen)
      throw new InvalidMessageException("ROUND_END body length mismatch");
    String winnerName = new String(body, 3, nameLen, StandardCharsets.UTF_8);
    return new RoundEndMessage(winnerId, correctIndex, winnerName);
  }

  public byte[] toBytes() {
    byte[] nameBytes = winnerName.getBytes(StandardCharsets.UTF_8);
    byte[] body = new byte[3 + nameBytes.length];
    body[0] = (byte) winnerId;
    body[1] = (byte) correctIndex;
    body[2] = (byte) nameBytes.length;
    System.arraycopy(nameBytes, 0, body, 3, nameBytes.length);
    return body;
  }
}
