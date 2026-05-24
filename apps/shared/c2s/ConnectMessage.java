package apps.shared.c2s;

import apps.shared.codec.InvalidMessageException;
import java.nio.charset.StandardCharsets;

public record ConnectMessage(String playerName) implements ClientMessage {
  public static final int MAX_NAME_BYTES = 16;

  public ConnectMessage {
    if (playerName == null || playerName.isBlank())
      throw new InvalidMessageException("CONNECT playerName must not be blank");
    if (playerName.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES)
      throw new InvalidMessageException("playerName must not be larger than 16 bytes");
  }

  public static ConnectMessage parse(byte[] body) {
    if (body.length == 0) throw new InvalidMessageException("CONNECT body must not be empty");
    return new ConnectMessage(new String(body, StandardCharsets.UTF_8));
  }

  public byte[] toBytes() {
    return playerName.getBytes(StandardCharsets.UTF_8);
  }
}
