package apps.shared.c2s;

public sealed interface ClientMessage
    permits ConnectMessage, AnswerMessage, DisconnectMessage, ReadyMessage {}
