package apps.game;

import apps.server.ClientSession;

public sealed interface GameEvent permits GameEvent.Start, GameEvent.Answer {
  record Start(ClientSession session) implements GameEvent {}

  record Answer(ClientSession session, int answerIndex, long receivedNs) implements GameEvent {}
}
