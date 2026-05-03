import java.io.*;
import java.net.*;

public class Server {
    public static final int DEFAULT_PORT = 8080;
    public static void main(String[] args)
    throws IOException {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        ServerSocket s = new ServerSocket(port);
        System.out.println("Started: " + s);
        try {
            Socket socket = s.accept();
            try {
                System.out.println(
                    "Connection accepted: " + socket
                );
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                while (true) {
                    String str = in.readLine();
                    if(str.equals("END")) break;
                    System.out.println("Echoing : ");
                    out.println(str);
                }
            } finally {
                System.out.println("closing...");
                socket.close();
            }
        } finally {
            s.close();
        }
    }
}