package apps;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

import apps.shared.codec.FrameDecoder;
import apps.shared.codec.FrameEncoder;
import apps.shared.codec.MessageType;
import apps.shared.c2s.ConnectMessage;
import apps.shared.s2c.ConnectAckMessage;
import apps.shared.s2c.ConnectNgMessage;
import apps.shared.s2c.DisconnectAckMessage;
import apps.shared.s2c.ServerMessage;

public class Client {

    private static volatile boolean running = true;

    public static void main(String[] args) throws IOException {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : Server.DEFAULT_PORT;

        Socket socket = new Socket(InetAddress.getByName("localhost"), port);

        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            System.out.println("=== Socket Quiz Client ===");
            System.out.println("Server: localhost:" + port);
            System.out.println();

            ConnectMessage connect = new ConnectMessage(); //ConnectMessageにplayerNameは渡さない
            FrameEncoder.writeFrame(out, MessageType.CONNECT, connect.toBytes());
            System.out.println("Sent CONNECT");

            FrameDecoder.Frame firstFrame = FrameDecoder.readFrame(in);
            ServerMessage firstMessage = FrameDecoder.decodeServer(firstFrame);

            if (firstMessage instanceof ConnectAckMessage ack) {
                System.out.println("Connected. Your playerId = " + ack.playerId());
                System.out.println("Your playerName = Player" + ack.playerId());
            } else if (firstMessage instanceof ConnectNgMessage ng) {
                System.out.println("Connection failed. reason=" + ng.reason());
                return;
            } else {
                System.out.println("Unexpected first message: " + firstMessage);
                return;
            }

            printHelp();

            Thread receiverThread = new Thread(() -> receiveLoop(in), "client-receiver");
            receiverThread.start();

            inputLoop(out);

            try {
                receiverThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } finally {
            socket.close();
            System.out.println("Socket closed.");
        }
    }

    private static void inputLoop(DataOutputStream out) {
        Scanner scanner = new Scanner(System.in);

        while (running) {
            System.out.print("> ");

            if (!scanner.hasNextLine()) {
                break;
            }

            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+");
            String command = parts[0].toLowerCase();

            try {
                switch (command) {
                    case "answer" -> {
                        if (parts.length != 2) {
                            System.out.println("Usage: answer <number>");
                            break;
                        }

                        int answerIndex = Integer.parseInt(parts[1]);

                        if (answerIndex < 0 || answerIndex > 3) {
                            System.out.println("Answer index must be between 0 and 3.");
                            break;
                        }

                        byte[] body = new byte[] { (byte) answerIndex };
                        FrameEncoder.writeFrame(out, MessageType.ANSWER, body);
                        System.out.println("Sent ANSWER index=" + answerIndex);
                    }

                    case "disconnect", "quit", "exit" -> {
                        FrameEncoder.writeFrame(out, MessageType.DISCONNECT, new byte[0]);
                        System.out.println("Sent DISCONNECT");
                        running = false;
                    }

                    case "help" -> printHelp();

                    default -> {
                        System.out.println("Unknown command: " + command);
                        System.out.println("Type 'help' to show available commands.");
                    }
                }
            } catch (NumberFormatException e) {
                System.out.println("Answer index must be a number.");
            } catch (IOException e) {
                System.out.println("Failed to send message: " + e.getMessage());
                running = false;
            }
        }
    }

    private static void receiveLoop(DataInputStream in) {
        while (running) {
            try {
                FrameDecoder.Frame frame = FrameDecoder.readFrame(in);
                ServerMessage message = FrameDecoder.decodeServer(frame);

                if (message instanceof DisconnectAckMessage) {
                    System.out.println();
                    System.out.println("Received DISCONNECT_ACK");
                    System.out.println("Disconnected from server.");
                    running = false;
                    break;
                } else if (message instanceof ConnectAckMessage ack) {
                    System.out.println();
                    System.out.println("Received CONNECT_ACK, playerId=" + ack.playerId());
                } else if (message instanceof ConnectNgMessage ng) {
                    System.out.println();
                    System.out.println("Received CONNECT_NG, reason=" + ng.reason());
                    running = false;
                    break;
                } else {
                    System.out.println();
                    System.out.println("Received server message: " + message);
                }

            } catch (EOFException e) {
                System.out.println();
                System.out.println("Server closed the connection.");
                running = false;
                break;
            } catch (IOException e) {
                if (running) {
                    System.out.println();
                    System.out.println("Connection error: " + e.getMessage());
                }
                running = false;
                break;
            } catch (RuntimeException e) {
                System.out.println();
                System.out.println("Failed to decode server message: " + e.getMessage());
                running = false;
                break;
            }
        }
    }

    private static void printHelp() {
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  answer <number>  : send answer index to server");
        System.out.println("  disconnect       : disconnect from server");
        System.out.println("  quit / exit      : same as disconnect");
        System.out.println("  help             : show this help");
        System.out.println();
    }
}