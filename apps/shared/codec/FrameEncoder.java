package apps.shared.codec;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * フレームフォーマット:
 *   [1 byte]  MessageType ordinal
 *   [4 bytes] payload length (int)
 *   [N bytes] payload
 */
public class FrameEncoder {

    public static void writeFrame(DataOutputStream out, MessageType type, byte[] payload)
            throws IOException {
        out.writeByte(type.ordinal());
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }
}
