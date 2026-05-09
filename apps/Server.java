package apps;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import apps.shared.c2s.ClientMessage;
import apps.shared.c2s.JoinMessage;
import apps.shared.codec.FrameDecoder;
import apps.shared.codec.FrameEncoder;
import apps.shared.codec.MessageType;
import apps.shared.s2c.JoinNgMessage;
import apps.shared.s2c.JoinOkMessage;
import apps.shared.s2c.PlayerListMessage;

/**
 * リアルタイム早押しクイズ - サーバ
 *
 * 1週目ゴール: 2〜4人のプレイヤーが接続・参加登録できる
 *
 * 接続の流れ:
 *   Client  --[JOIN|playerName]-->  Server
 *   Server  <--[JOIN_OK|playerId|playerName]--  Client (成功)
 *   Server  <--[JOIN_NG|reason]--  Client (失敗: 満員 or 名前重複)
 *   Server  --[PLAYER_LIST|...]-->  全員 (誰かがJOINするたびにブロードキャスト)
 */
public class Server {

    public static final int DEFAULT_PORT = 8080;
    public static final int MIN_PLAYERS  = 2;
    public static final int MAX_PLAYERS  = 4;

    // playerId -> (playerName, output stream)
    private final Map<Integer, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger nextPlayerId = new AtomicInteger(1);

    // ロビーへの参加を順番に処理するためのロック
    private final Object lobbyLock = new Object();

    public static void main(String[] args) throws IOException {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new Server().start(port);
    }

    private void start(int port) throws IOException {
        ServerSocket ss = new ServerSocket(port);
        System.out.println("[Server] Listening on port " + port
                + "  (waiting for " + MIN_PLAYERS + "~" + MAX_PLAYERS + " players)");

        // ExecutorService でクライアントごとにスレッドを生成
        ExecutorService pool = Executors.newCachedThreadPool();

        try {
            while (true) {
                Socket socket = ss.accept();
                System.out.println("[Server] New connection: " + socket.getRemoteSocketAddress());
                pool.submit(() -> handleClient(socket));
            }
        } finally {
            pool.shutdownNow();
            ss.close();
        }
    }

    /** クライアント1人分のハンドラ（別スレッドで動く） */
    private void handleClient(Socket socket) {
        try (socket) {
            DataInputStream  in  = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // --- JOIN メッセージを受け取る ---
            FrameDecoder.Frame frame = FrameDecoder.readFrame(in);
            ClientMessage msg = FrameDecoder.decodeClient(frame);

            if (!(msg instanceof JoinMessage joinMsg)) {
                System.err.println("[Server] Expected JOIN but got: " + frame.type());
                return;
            }

            String playerName = joinMsg.playerName().trim();
            System.out.println("[Server] JOIN request: name=" + playerName);

            // --- ロビー参加判定（排他制御） ---
            int assignedId;
            synchronized (lobbyLock) {
                // 満員チェック
                if (sessions.size() >= MAX_PLAYERS) {
                    sendJoinNg(out, "FULL");
                    System.out.println("[Server] Rejected (FULL): " + playerName);
                    return;
                }
                // 名前の重複チェック
                boolean nameExists = sessions.values().stream()
                        .anyMatch(s -> s.name().equalsIgnoreCase(playerName));
                if (nameExists) {
                    sendJoinNg(out, "NAME_TAKEN");
                    System.out.println("[Server] Rejected (NAME_TAKEN): " + playerName);
                    return;
                }

                assignedId = nextPlayerId.getAndIncrement();
                sessions.put(assignedId, new PlayerSession(playerName, out));
                System.out.printf("[Server] Joined: playerId=%d name=%s  (total=%d)%n",
                        assignedId, playerName, sessions.size());
            }

            // --- JOIN_OK を本人に送る ---
            JoinOkMessage ack = new JoinOkMessage(assignedId, playerName);
            FrameEncoder.writeFrame(out, MessageType.JOIN_OK, ack.toBytes());

            // --- PLAYER_LIST を全員にブロードキャスト ---
            broadcastPlayerList();

            // ここでスレッドをブロックして接続を保持する
            // （2週目以降: BUZZ/ANSWERのメッセージ受信ループに置き換える）
            waitForDisconnect(in, assignedId, playerName);

        } catch (IOException e) {
            System.err.println("[Server] Connection error: " + e.getMessage());
        }
    }

    /** JOIN_NG を送る */
    private void sendJoinNg(DataOutputStream out, String reason) throws IOException {
        JoinNgMessage ng = new JoinNgMessage(reason);
        FrameEncoder.writeFrame(out, MessageType.JOIN_NG, ng.toBytes());
    }

    /** 現在の参加者リストを全プレイヤーにブロードキャスト */
    private void broadcastPlayerList() {
        // スナップショットを作成
        Map<Integer, String> snapshot = new LinkedHashMap<>();
        sessions.forEach((id, s) -> snapshot.put(id, s.name()));
        PlayerListMessage listMsg = new PlayerListMessage(snapshot);
        byte[] payload = listMsg.toBytes();

        System.out.println("[Server] Broadcasting PLAYER_LIST: " + snapshot);

        // 各クライアントへ送信（送れなかった場合は切断扱い）
        List<Integer> disconnected = new ArrayList<>();
        for (var entry : sessions.entrySet()) {
            try {
                synchronized (entry.getValue().out()) {
                    FrameEncoder.writeFrame(entry.getValue().out(),
                            MessageType.PLAYER_LIST, payload);
                }
            } catch (IOException e) {
                disconnected.add(entry.getKey());
            }
        }
        disconnected.forEach(sessions::remove);
    }

    /**
     * クライアントが切断するまで待機するループ。
     * 2週目以降はここを BUZZ / ANSWER の受信ループに拡張する。
     */
    private void waitForDisconnect(DataInputStream in, int playerId, String playerName) {
        try {
            // EOF が来るまでブロック
            while (in.read() != -1) { /* 何もしない */ }
        } catch (IOException ignored) {
            // 切断
        }
        sessions.remove(playerId);
        System.out.printf("[Server] Disconnected: playerId=%d name=%s  (total=%d)%n",
                playerId, playerName, sessions.size());
        broadcastPlayerList();
    }

    /** セッション情報 */
    private record PlayerSession(String name, DataOutputStream out) {}
}
