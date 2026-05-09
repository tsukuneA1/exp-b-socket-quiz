package apps.shared.c2s;

import apps.shared.codec.InvalidMessageException;

public record DisconnectMessage() implements ClientMessage {
    public static DisconnectMessage parse(byte[] body) {
        if (body.length != 0)
            throw new InvalidMessageException("DISCONNECT body must be empty, got " + body.length + " bytes");
        return new DisconnectMessage();
    }
}
