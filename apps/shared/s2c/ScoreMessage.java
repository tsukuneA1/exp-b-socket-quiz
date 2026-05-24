package apps.shared.s2c;

import apps.shared.codec.InvalidMessageException;
import java.nio.charset.StandardCharsets;
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
    List<ScoreEntry> scores = new ArrayList<>(numPlayers);
    int pos = 1;
    for (int n = 0; n < numPlayers; n++) {
      if (pos + 4 > body.length)
        throw new InvalidMessageException("SCORE body too short for entry");
      int playerId = body[pos] & 0xFF;
      int score = ((body[pos + 1] & 0xFF) << 8) | (body[pos + 2] & 0xFF);
      int nameLen = body[pos + 3] & 0xFF;
      if (pos + 4 + nameLen > body.length)
        throw new InvalidMessageException("SCORE body too short for playerName");
      String playerName = new String(body, pos + 4, nameLen, StandardCharsets.UTF_8);
      scores.add(new ScoreEntry(playerId, playerName, score));
      pos += 4 + nameLen;
    }
    return new ScoreMessage(scores);
  }

  public byte[] toBytes() {
    byte[][] nameBytes = new byte[scores.size()][];
    int totalSize = 1;
    for (int i = 0; i < scores.size(); i++) {
      nameBytes[i] = scores.get(i).playerName().getBytes(StandardCharsets.UTF_8);
      totalSize += 4 + nameBytes[i].length;
    }
    byte[] body = new byte[totalSize];
    body[0] = (byte) scores.size();
    int pos = 1;
    for (int i = 0; i < scores.size(); i++) {
      ScoreEntry e = scores.get(i);
      body[pos] = (byte) e.playerId();
      body[pos + 1] = (byte) (e.score() >> 8);
      body[pos + 2] = (byte) e.score();
      body[pos + 3] = (byte) nameBytes[i].length;
      System.arraycopy(nameBytes[i], 0, body, pos + 4, nameBytes[i].length);
      pos += 4 + nameBytes[i].length;
    }
    return body;
  }
}
