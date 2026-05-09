package apps.shared.c2s;

import apps.shared.codec.InvalidMessageException;

public record ConnectMessage() implements ClientMessage {
    public static ConnectMessage parse(byte[] body) {
        if (body.length != 0)
            throw new InvalidMessageException("CONNECT body must be empty, got " + body.length + " bytes");
        return new ConnectMessage();
    }
}
