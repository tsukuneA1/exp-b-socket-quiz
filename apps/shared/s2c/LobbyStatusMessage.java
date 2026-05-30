package shared.s2c;

import shared.codec.InvalidMessageException;

public record LobbyStatusMessage(int readyCount, int totalCount) implements ServerMessage {
  public static LobbyStatusMessage parse(byte[] body) {
    if (body.length != 2) throw new InvalidMessageException("LOBBY_STATUS body must be 2 bytes");
    return new LobbyStatusMessage(body[0] & 0xFF, body[1] & 0xFF);
  }

  public byte[] toBytes() {
    return new byte[] {(byte) readyCount, (byte) totalCount};
  }
}
