package apps.shared.s2c;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Server -> Client: JOIN_OK
 * payload: [4 bytes] playerId + [N bytes] playerName (UTF-8)
 */
public record JoinOkMessage(int playerId, String playerName) implements ServerMessage {

    public byte[] toBytes() {
        byte[] nameBytes = playerName.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(4 + nameBytes.length);
        buf.putInt(playerId);
        buf.put(nameBytes);
        return buf.array();
    }

    public static JoinOkMessage fromBytes(byte[] payload) throws IOException {
        if (payload.length < 4) {
            throw new IOException("JoinOkMessage payload too short");
        }
        ByteBuffer buf = ByteBuffer.wrap(payload);
        int playerId = buf.getInt();
        byte[] nameBytes = new byte[buf.remaining()];
        buf.get(nameBytes);
        return new JoinOkMessage(playerId, new String(nameBytes, StandardCharsets.UTF_8));
    }
}
