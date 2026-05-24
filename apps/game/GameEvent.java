package apps.game;

import apps.server.ClientSession;

public sealed interface GameEvent permits GameEvent.Start, GameEvent.Answer {
  ClientSession session();

  long enqueuedNs();

  long sequence();

  String kind();

  record Start(ClientSession session, long enqueuedNs, long sequence) implements GameEvent {
    @Override
    public String kind() {
      return "START";
    }
  }

  record Answer(
      ClientSession session, int answerIndex, long receivedNs, long enqueuedNs, long sequence)
      implements GameEvent {
    @Override
    public String kind() {
      return "ANSWER";
    }
  }
}
