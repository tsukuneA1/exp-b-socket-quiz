package apps.shared.codec;

public enum MessageType {
    // Client -> Server
    JOIN,
    // Server -> Client
    JOIN_OK,
    JOIN_NG,
    PLAYER_LIST;
}
