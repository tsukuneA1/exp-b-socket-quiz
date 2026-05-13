package apps;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;
import apps.shared.c2s.ClientMessage;
import apps.shared.c2s.ConnectMessage;
import apps.shared.codec.FrameDecoder;
import apps.shared.codec.FrameEncoder;
import apps.shared.codec.MessageType;
import apps.shared.s2c.ConnectAckMessage;

public class Server {
    public static final int DEFAULT_PORT = 8080;
    private static final AtomicInteger nextPlayerId = new AtomicInteger(1);

    public static void main(String[] args) throws IOException {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        ServerSocket ss = new ServerSocket(port);
        System.out.println("Started: " + ss);
        try {
            Socket socket = ss.accept();
            try {
                System.out.println("Connection accepted: " + socket);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                FrameDecoder.Frame frame = FrameDecoder.readFrame(in);
                ClientMessage msg = FrameDecoder.decodeClient(frame);
                if (msg instanceof ConnectMessage) {
                    int playerId = nextPlayerId.getAndIncrement();
                    ConnectAckMessage ack = new ConnectAckMessage(playerId);
                    FrameEncoder.writeFrame(out, MessageType.CONNECT_ACK, ack.toBytes());
                    System.out.println("Sent CONNECT_ACK, playerId=" + playerId);
                }
            } finally {
                socket.close();
            }
        } finally {
            ss.close();
        }
    }
}
