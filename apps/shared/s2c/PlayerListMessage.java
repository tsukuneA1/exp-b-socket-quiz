package apps.shared.s2c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server -> Client: PLAYER_LIST
 * payload: "playerId1:playerName1,playerId2:playerName2,..." (UTF-8)
 *
 * 誰かがJOINするたびに全員に送信され、現在の参加者リストを同期する。
 */
public record PlayerListMessage(Map<Integer, String> players) implements ServerMessage {

    public byte[] toBytes() {
        StringBuilder sb = new StringBuilder();
        players.forEach((id, name) -> {
            if (!sb.isEmpty()) sb.append(',');
            sb.append(id).append(':').append(name);
        });
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static PlayerListMessage fromBytes(byte[] payload) throws IOException {
        String text = new String(payload, StandardCharsets.UTF_8);
        Map<Integer, String> players = new LinkedHashMap<>();
        if (!text.isBlank()) {
            for (String entry : text.split(",")) {
                String[] parts = entry.split(":", 2);
                if (parts.length != 2) {
                    throw new IOException("Malformed PLAYER_LIST entry: " + entry);
                }
                players.put(Integer.parseInt(parts[0].trim()), parts[1]);
            }
        }
        return new PlayerListMessage(players);
    }
}
