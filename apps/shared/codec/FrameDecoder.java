package apps.shared.codec;

import java.io.DataInputStream;
import java.io.IOException;
import apps.shared.c2s.ClientMessage;
import apps.shared.c2s.JoinMessage;
import apps.shared.s2c.JoinNgMessage;
import apps.shared.s2c.JoinOkMessage;
import apps.shared.s2c.PlayerListMessage;
import apps.shared.s2c.ServerMessage;

public class FrameDecoder {

    public record Frame(MessageType type, byte[] payload) {}

    public static Frame readFrame(DataInputStream in) throws IOException {
        int typeOrdinal = in.readByte() & 0xFF;
        MessageType[] values = MessageType.values();
        if (typeOrdinal >= values.length) {
            throw new IOException("Unknown message type ordinal: " + typeOrdinal);
        }
        MessageType type = values[typeOrdinal];
        int length = in.readInt();
        if (length < 0 || length > 65535) {
            throw new IOException("Invalid payload length: " + length);
        }
        byte[] payload = new byte[length];
        in.readFully(payload);
        return new Frame(type, payload);
    }

    /** クライアントから届いたフレームをデコードする */
    public static ClientMessage decodeClient(Frame frame) throws IOException {
        return switch (frame.type()) {
            case JOIN -> JoinMessage.fromBytes(frame.payload());
            default -> throw new IOException("Unexpected client message type: " + frame.type());
        };
    }

    /** サーバから届いたフレームをデコードする */
    public static ServerMessage decodeServer(Frame frame) throws IOException {
        return switch (frame.type()) {
            case JOIN_OK      -> JoinOkMessage.fromBytes(frame.payload());
            case JOIN_NG      -> JoinNgMessage.fromBytes(frame.payload());
            case PLAYER_LIST  -> PlayerListMessage.fromBytes(frame.payload());
            default -> throw new IOException("Unexpected server message type: " + frame.type());
        };
    }
}
