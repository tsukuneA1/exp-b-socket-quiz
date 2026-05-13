package apps;
import java.io.*;
import java.net.*;
import apps.shared.codec.FrameDecoder;
import apps.shared.codec.FrameEncoder;
import apps.shared.codec.MessageType;
import apps.shared.s2c.ConnectAckMessage;
import apps.shared.s2c.ServerMessage;
public class Client {
    public static void main(String[] args) throws IOException {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : Server.DEFAULT_PORT;
        Socket socket = new Socket(InetAddress.getByName("localhost"), port);
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            FrameEncoder.writeFrame(out, MessageType.CONNECT, new byte[0]);
            System.out.println("Sent CONNECT");
            FrameDecoder.Frame frame = FrameDecoder.readFrame(in);
            ServerMessage msg = FrameDecoder.decodeServer(frame);
            if (msg instanceof ConnectAckMessage ack) {
                System.out.println("Received CONNECT_ACK, playerId=" + ack.playerId());
            }
        } finally {
            socket.close();
        }
    }
}