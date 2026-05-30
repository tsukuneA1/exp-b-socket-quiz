package shared.codec;

public class InvalidMessageException extends RuntimeException {
  public InvalidMessageException(String message) {
    super(message);
  }
}
