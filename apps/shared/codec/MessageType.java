package apps.shared.codec;

public class MessageType {
    // C → S
    public static final int CONNECT    = 0x01;
    public static final int ANSWER     = 0x02;
    public static final int DISCONNECT = 0x03;

    // S → C
    public static final int CONNECT_ACK      = 0x11;
    public static final int QUESTION_OPTIONS = 0x12;
    public static final int WRONG_ANSWER     = 0x13;
    public static final int ROUND_END        = 0x14;
    public static final int SCORE            = 0x15;
    public static final int DISCONNECT_ACK   = 0x16;
    public static final int QUESTION_CHUNK   = 0x17;
    public static final int CONNECT_NG       = 0x18;
}
