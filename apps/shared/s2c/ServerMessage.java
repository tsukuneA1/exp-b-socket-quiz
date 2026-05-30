package shared.s2c;

public sealed interface ServerMessage
    permits ConnectAckMessage,
        ConnectNgMessage,
        QuestionOptionsMessage,
        QuestionChunkMessage,
        WrongAnswerMessage,
        RoundEndMessage,
        ScoreMessage,
        DisconnectAckMessage,
        LobbyStatusMessage,
        GameEndMessage {}
