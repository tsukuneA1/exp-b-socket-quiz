package shared.c2s;

import shared.codec.InvalidMessageException;

public record ReadyMessage() implements ClientMessage {
  public static ReadyMessage parse(byte[] body) {
    if (body.length != 0) throw new InvalidMessageException("READY body must be empty");
    return new ReadyMessage();
  }

  public byte[] toBytes() {
    return new byte[0];
  }
}
