package apps.shared.s2c;

public sealed interface ServerMessage
        permits ConnectAckMessage, QuestionOptionsMessage, QuestionChunkMessage,
                WrongAnswerMessage, RoundEndMessage, ScoreMessage, DisconnectAckMessage {}
