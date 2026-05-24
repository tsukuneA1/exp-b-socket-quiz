package apps.shared.c2s;

import apps.shared.codec.InvalidMessageException;

public record StartMessage() implements ClientMessage {
  public static StartMessage parse(byte[] body) {
    if (body.length != 0) throw new InvalidMessageException("START body must be empty");
    return new StartMessage();
  }

  public byte[] toBytes() {
    return new byte[0];
  }
}
