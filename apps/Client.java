package apps;

import java.io.*;
import java.net.*;

import apps.shared.c2s.JoinMessage;
import apps.shared.codec.FrameDecoder;
import apps.shared.codec.FrameEncoder;
import apps.shared.codec.MessageType;
import apps.shared.s2c.*;

/**
 * リアルタイム早押しクイズ - クライアント（動作確認用）
 *
 * 使い方:
 *   java apps.Client <playerName> [port]
 *   例) java apps.Client Alice 8080
 */
public class Client {

    public static void main(String[] args) throws IOException {
        String playerName = (args.length > 0) ? args[0] : "Player1";
        int    port       = (args.length > 1) ? Integer.parseInt(args[1]) : Server.DEFAULT_PORT;

        System.out.println("[Client] Connecting as \"" + playerName + "\" to port " + port);
        Socket socket = new Socket(InetAddress.getByName("localhost"), port);

        try {
            DataInputStream  in  = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // --- JOIN 送信 ---
            JoinMessage join = new JoinMessage(playerName);
            FrameEncoder.writeFrame(out, MessageType.JOIN, join.toBytes());
            System.out.println("[Client] Sent JOIN: name=" + playerName);

            // --- サーバからのメッセージを受信し続ける ---
            while (true) {
                FrameDecoder.Frame frame = FrameDecoder.readFrame(in);
                ServerMessage msg = FrameDecoder.decodeServer(frame);

                switch (msg) {
                    case JoinOkMessage ack -> {
                        System.out.println("[Client] JOIN_OK: playerId=" + ack.playerId()
                                + " name=" + ack.playerName());
                    }
                    case JoinNgMessage ng -> {
                        System.out.println("[Client] JOIN_NG: reason=" + ng.reason());
                        return; // 参加失敗 → 終了
                    }
                    case PlayerListMessage list -> {
                        System.out.println("[Client] PLAYER_LIST: " + list.players());
                    }
                }
            }
        } catch (EOFException e) {
            System.out.println("[Client] Server closed connection.");
        } finally {
            socket.close();
        }
    }
}
