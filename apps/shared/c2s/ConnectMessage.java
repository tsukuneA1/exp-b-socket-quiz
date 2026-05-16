package apps.shared.c2s;

import apps.shared.codec.InvalidMessageException;
import java.nio.charset.StandardCharsets;

public record ConnectMessage() implements ClientMessage {
    public static ConnectMessage parse(byte[] body) {
        if (body.length != 0) {
            throw new InvalidMessageException("CONNECT body must be empty, got " + body.length);
            } 
    return new ConnectMessage();
    }

    public byte[] toBytes() {
        return new byte[0];
    }
}
