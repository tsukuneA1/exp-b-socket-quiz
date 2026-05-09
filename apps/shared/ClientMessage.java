package apps.shared;

public sealed interface ClientMessage
        permits ConnectMessage, AnswerMessage, DisconnectMessage {}
