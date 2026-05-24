package apps.shared.s2c;

import apps.shared.codec.InvalidMessageException;
import java.util.ArrayList;
import java.util.List;

public record ScoreMessage(List<ScoreEntry> scores) implements ServerMessage {
  public ScoreMessage {
    if (scores == null || scores.isEmpty())
      throw new InvalidMessageException("SCORE must contain at least one entry");
  }

  public static ScoreMessage parse(byte[] body) {
    if (body.length < 1) throw new InvalidMessageException("SCORE body too short");
    int numPlayers = body[0] & 0xFF;
    if (body.length != 1 + numPlayers * 3)
      throw new InvalidMessageException(
          "SCORE body length mismatch: expected " + (1 + numPlayers * 3) + ", got " + body.length);
    List<ScoreEntry> scores = new ArrayList<>(numPlayers);
    int i = 1;
    for (int n = 0; n < numPlayers; n++) {
      int playerId = body[i] & 0xFF;
      int score = ((body[i + 1] & 0xFF) << 8) | (body[i + 2] & 0xFF);
      scores.add(new ScoreEntry(playerId, score));
      i += 3;
    }
    return new ScoreMessage(scores);
  }

  public byte[] toBytes() {
    byte[] body = new byte[1 + scores.size() * 3];
    body[0] = (byte) scores.size();
    int i = 1;
    for (ScoreEntry e : scores) {
      body[i] = (byte) e.playerId();
      body[i + 1] = (byte) (e.score() >> 8);
      body[i + 2] = (byte) e.score();
      i += 3;
    }
    return body;
  }
}
