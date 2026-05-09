package apps.shared.s2c;

import apps.shared.codec.InvalidMessageException;

public record DisconnectAckMessage() implements ServerMessage {
    public static DisconnectAckMessage parse(byte[] body) {
        if (body.length != 0)
            throw new InvalidMessageException("DISCONNECT_ACK body must be empty, got " + body.length + " bytes");
        return new DisconnectAckMessage();
    }

    public byte[] toBytes() {
        return new byte[0];
    }
}
