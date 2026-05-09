package apps.shared.s2c;

import apps.shared.codec.InvalidMessageException;

public record RoundEndMessage(int winnerId, int correctIndex) implements ServerMessage {
    public RoundEndMessage {
        if (winnerId < 0 || winnerId > 255)
            throw new InvalidMessageException("ROUND_END winnerId out of byte range: " + winnerId);
        if (correctIndex < 0 || correctIndex > 255)
            throw new InvalidMessageException("ROUND_END correctIndex out of byte range: " + correctIndex);
    }

    public static RoundEndMessage parse(byte[] body) {
        if (body.length != 2)
            throw new InvalidMessageException("ROUND_END body must be 2 bytes, got " + body.length);
        return new RoundEndMessage(body[0] & 0xFF, body[1] & 0xFF);
    }

    public byte[] toBytes() {
        return new byte[]{(byte) winnerId, (byte) correctIndex};
    }
}
