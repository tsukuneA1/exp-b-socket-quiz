package apps.shared.c2s;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Client -> Server: JOIN
 * payload: UTF-8エンコードしたプレイヤー名
 */
public record JoinMessage(String playerName) implements ClientMessage {

    public byte[] toBytes() {
        return playerName.getBytes(StandardCharsets.UTF_8);
    }

    public static JoinMessage fromBytes(byte[] payload) throws IOException {
        if (payload.length == 0) {
            throw new IOException("JoinMessage payload is empty");
        }
        return new JoinMessage(new String(payload, StandardCharsets.UTF_8));
    }
}
