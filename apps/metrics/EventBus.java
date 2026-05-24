package metrics;

import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {

  private static final CopyOnWriteArrayList<PrintWriter> observers = new CopyOnWriteArrayList<>();

  public static void start(int port) {
    Thread t =
        new Thread(
            () -> {
              try (ServerSocket ss = new ServerSocket(port)) {
                System.out.println("EventBus listening on :" + port);
                while (true) {
                  Socket s = ss.accept();
                  PrintWriter w =
                      new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
                  observers.add(w);
                  System.out.println("Observer connected: " + s.getRemoteSocketAddress());
                  w.println("{\"event\":\"connected\"}");
                }
              } catch (IOException e) {
                System.err.println("EventBus error: " + e.getMessage());
              }
            },
            "event-bus");
    t.setDaemon(true);
    t.start();
  }

  public static void emit(String event, String json) {
    String line = "{\"event\":\"" + event + "\"," + json.substring(1);
    for (PrintWriter w : observers) {
      if (w.checkError()) {
        observers.remove(w);
      } else {
        w.println(line);
      }
    }
  }
}
