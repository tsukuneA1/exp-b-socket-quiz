package apps.shared.s2c;

import apps.shared.codec.InvalidMessageException;
import java.nio.charset.StandardCharsets;

public record QuestionChunkMessage(String chunk) implements ServerMessage {
  public QuestionChunkMessage {
    if (chunk == null || chunk.isEmpty())
      throw new InvalidMessageException("QUESTION_CHUNK must not be empty");
  }

  public static QuestionChunkMessage parse(byte[] body) {
    if (body.length == 0)
      throw new InvalidMessageException("QUESTION_CHUNK body must not be empty");
    return new QuestionChunkMessage(new String(body, StandardCharsets.UTF_8));
  }

  public byte[] toBytes() {
    return chunk.getBytes(StandardCharsets.UTF_8);
  }
}
