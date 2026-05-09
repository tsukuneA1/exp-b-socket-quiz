package apps.shared.s2c;

public sealed interface ServerMessage
        permits JoinOkMessage, JoinNgMessage, PlayerListMessage {}
