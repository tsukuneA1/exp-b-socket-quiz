package apps.shared;

public record ConnectAckMessage(int playerId) implements ServerMessage {
    public ConnectAckMessage {
        if (playerId < 0 || playerId > 255)
            throw new InvalidMessageException("CONNECT_ACK playerId out of byte range: " + playerId);
    }

    public static ConnectAckMessage parse(byte[] body) {
        if (body.length != 1)
            throw new InvalidMessageException("CONNECT_ACK body must be 1 byte, got " + body.length);
        return new ConnectAckMessage(body[0] & 0xFF);
    }

    public byte[] toBytes() {
        return new byte[]{(byte) playerId};
    }
}
