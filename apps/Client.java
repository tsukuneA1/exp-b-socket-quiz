package apps;

import java.io.*;
import java.net.*;
import apps.shared.codec.FrameDecoder;
import apps.shared.codec.FrameEncoder;
import apps.shared.codec.MessageType;
import apps.shared.c2s.ConnectMessage;
import apps.shared.s2c.ConnectAckMessage;
import apps.shared.s2c.ConnectNgMessage;
import apps.shared.s2c.ServerMessage;

public class Client {
    public static void main(String[] args) throws IOException {
        String playerName = (args.length > 0) ? args[0] : "Player1";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : Server.DEFAULT_PORT;
        Socket socket = new Socket(InetAddress.getByName("localhost"), port);
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            ConnectMessage connect = new ConnectMessage(playerName);
            FrameEncoder.writeFrame(out, MessageType.CONNECT, connect.toBytes());
            System.out.println("Sent CONNECT name=" + playerName);

            FrameDecoder.Frame frame = FrameDecoder.readFrame(in);
            ServerMessage msg = FrameDecoder.decodeServer(frame);
            if (msg instanceof ConnectAckMessage ack) {
                System.out.println("Received CONNECT_ACK, playerId=" + ack.playerId());
            } else if (msg instanceof ConnectNgMessage ng) {
                System.out.println("Received CONNECT_NG, reason=" + ng.reason());
            }
        } finally {
            socket.close();
        }
    }
}
