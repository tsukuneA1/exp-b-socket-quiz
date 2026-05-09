package apps.shared;

public sealed interface ServerMessage
        permits ConnectAckMessage, QuestionOptionsMessage, QuestionChunkMessage,
                WrongAnswerMessage, RoundEndMessage, ScoreMessage, DisconnectAckMessage {}
