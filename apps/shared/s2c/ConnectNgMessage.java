package apps.shared.s2c;

import apps.shared.codec.InvalidMessageException;
import java.nio.charset.StandardCharsets;

public record ConnectNgMessage(String reason) implements ServerMessage {
  public ConnectNgMessage {
    if (reason == null || reason.isBlank())
      throw new InvalidMessageException("CONNECT_NG reason must not be blank");
  }

  public static ConnectNgMessage parse(byte[] body) {
    if (body.length == 0) throw new InvalidMessageException("CONNECT_NG body must not be empty");
    return new ConnectNgMessage(new String(body, StandardCharsets.UTF_8));
  }

  public byte[] toBytes() {
    return reason.getBytes(StandardCharsets.UTF_8);
  }
}
