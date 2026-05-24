package apps.shared.s2c;

import apps.shared.codec.InvalidMessageException;

public record GameEndMessage(int winnerId) implements ServerMessage {
    public GameEndMessage {
        if (winnerId < 0 || winnerId > 255) {
            throw new InvalidMessageException("GAME_END winnerId out of byte range: " + winnerId);
        }
    }

    public static GameEndMessage parse(byte[] body) {
        if (body.length != 1) {
            throw new InvalidMessageException("GAME_END body must be 1 byte, got " + body.length);
        }

        return new GameEndMessage(body[0] & 0xFF);
    }

    public byte[] toBytes() {
        return new byte[] { (byte) winnerId };
    }
}