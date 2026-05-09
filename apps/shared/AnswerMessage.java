package apps.shared;

public record AnswerMessage(int index) implements ClientMessage {
    public AnswerMessage {
        if (index < 0 || index > 255)
            throw new InvalidMessageException("ANSWER index out of byte range: " + index);
    }

    public static AnswerMessage parse(byte[] body) {
        if (body.length != 1)
            throw new InvalidMessageException("ANSWER body must be 1 byte, got " + body.length);
        return new AnswerMessage(body[0] & 0xFF);
    }
}
