package apps.shared;

public record WrongAnswerMessage() implements ServerMessage {
    public static WrongAnswerMessage parse(byte[] body) {
        if (body.length != 0)
            throw new InvalidMessageException("WRONG_ANSWER body must be empty, got " + body.length + " bytes");
        return new WrongAnswerMessage();
    }

    public byte[] toBytes() {
        return new byte[0];
    }
}
