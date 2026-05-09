package apps.shared.s2c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Server -> Client: JOIN_NG
 * payload: UTF-8エンコードした拒否理由
 */
public record JoinNgMessage(String reason) implements ServerMessage {

    public byte[] toBytes() {
        return reason.getBytes(StandardCharsets.UTF_8);
    }

    public static JoinNgMessage fromBytes(byte[] payload) throws IOException {
        return new JoinNgMessage(new String(payload, StandardCharsets.UTF_8));
    }
}
