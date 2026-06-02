import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ConnectException;
import java.net.Socket;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import shared.c2s.ConnectMessage;
import shared.codec.FrameDecoder;
import shared.codec.FrameEncoder;
import shared.codec.MessageType;
import shared.s2c.ConnectAckMessage;
import shared.s2c.ConnectNgMessage;
import shared.s2c.DisconnectAckMessage;
import shared.s2c.GameEndMessage;
import shared.s2c.LobbyStatusMessage;
import shared.s2c.QuestionChunkMessage;
import shared.s2c.QuestionOptionsMessage;
import shared.s2c.RoundEndMessage;
import shared.s2c.ScoreMessage;
import shared.s2c.ServerMessage;
import shared.s2c.WrongAnswerMessage;

public class GuiClient {
    private static final Color[] ANSWER_COLORS = {
            new Color(220, 64, 64),
            new Color(64, 132, 220),
            new Color(239, 178, 44),
            new Color(58, 178, 103)
    };

    private final JLabel statusLabel = new JLabel("Status: CONNECTING");
    private final JLabel playerLabel = new JLabel();
    private final JLabel readyLabel = new JLabel("Ready: -/-");

    private final JTextArea questionArea = new JTextArea();
    private final JTextArea logArea = new JTextArea();

    private final JButton readyButton = new JButton("Ready");
    private final JButton disconnectButton = new JButton("Disconnect");
    private final JButton[] answerButtons = new JButton[4];

    private final String playerName;
    private final int port;
    private final JFrame frame = new JFrame("Socket Quiz GUI");

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private volatile boolean running = true;
    private int playerId = -1;
    private int currentRound = 0;

    public GuiClient(String playerName, int port) {
        this.playerName = playerName;
        this.port = port;

        playerLabel.setText("Player: " + playerName);
        readyButton.setEnabled(false);
        disconnectButton.setEnabled(false);

        questionArea.setEditable(false);
        questionArea.setOpaque(false);
        questionArea.setForeground(Color.WHITE);
        questionArea.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        logArea.setEditable(false);
        logArea.setBackground(new Color(18, 18, 18));
        logArea.setForeground(new Color(99, 255, 126));
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        logArea.setRows(6);

        JPanel topPanel = new JPanel(new GridLayout(4, 1));
        topPanel.add(playerLabel);
        topPanel.add(statusLabel);
        topPanel.add(readyLabel);

        JPanel answerPanel = new JPanel(new GridLayout(2, 2));
        answerPanel.setOpaque(false);
        for (int i = 0; i < answerButtons.length; i++) {
            int answerIndex = i;
            answerButtons[i] = new AnswerButton("-");
            answerButtons[i].setBackground(ANSWER_COLORS[i]);
            answerButtons[i].setEnabled(false);
            answerButtons[i].addActionListener(e -> sendAnswer(answerIndex));
            answerPanel.add(answerButtons[i]);
        }

        JPanel controlPanel = new JPanel();
        readyButton.addActionListener(e -> sendReady());
        disconnectButton.addActionListener(e -> disconnect());
        controlPanel.add(readyButton);
        controlPanel.add(disconnectButton);
        topPanel.add(controlPanel);

        JScrollPane questionScroll = new JScrollPane(questionArea);
        questionScroll.setOpaque(false);
        questionScroll.getViewport().setOpaque(false);
        questionScroll.setBorder(null);

        JPanel leftPanel = new QuizStagePanel();
        leftPanel.setLayout(new BorderLayout(0, 12));
        leftPanel.add(questionScroll, BorderLayout.CENTER);
        leftPanel.add(answerPanel, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new JLabel("Terminal Log"), BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel mainPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        mainPanel.add(leftPanel);
        mainPanel.add(rightPanel);

        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(mainPanel, BorderLayout.CENTER);

        frame.setSize(900, 560);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public static void main(String[] args) {
        String playerName = (args.length > 0) ? args[0] : "Player1";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : Server.DEFAULT_PORT;

        SwingUtilities.invokeLater(
                () -> {
                    GuiClient client = new GuiClient(playerName, port);
                    client.show();
                    new Thread(client::connectAndReceive, "gui-client-receiver").start();
                });
    }

    public void show() {
        frame.setVisible(true);
    }

    private void connectAndReceive() {
        try {
            socket = new Socket(InetAddress.getByName("localhost"), port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            ConnectMessage connect = new ConnectMessage(playerName);
            FrameEncoder.writeFrame(out, MessageType.CONNECT, connect.toBytes());
            logOnUi("CONNECT sent: " + playerName);

            while (running) {
                FrameDecoder.Frame receivedFrame = FrameDecoder.readFrame(in);
                ServerMessage message = FrameDecoder.decodeServer(receivedFrame);
                handleMessage(message);
            }
        } catch (EOFException e) {
            logOnUi("Server closed the connection.");
        } catch (ConnectException e) {
            showErrorOnUi(
                    "サーバーに接続できませんでした。先に Server を起動してください。\n"
                            + "起動例: mvn exec:java -Dexec.mainClass=Server\n"
                            + "接続先: localhost:" + port);
        } catch (IOException e) {
            showErrorOnUi("通信エラー: " + e.getMessage());
        } catch (RuntimeException e) {
            showErrorOnUi("メッセージ処理エラー: " + e.getMessage());
        } finally {
            running = false;
            closeSocket();
            markDisconnectedOnUi();
        }
    }

    private void handleMessage(ServerMessage message) {
        SwingUtilities.invokeLater(
                () -> {
                    if (message instanceof ConnectAckMessage ack) {
                        playerId = ack.playerId();
                        statusLabel.setText("Status: WAITING");
                        playerLabel.setText("Player: " + playerName + " (id=" + playerId + ")");
                        readyButton.setEnabled(true);
                        disconnectButton.setEnabled(true);
                        log("Connected. playerId=" + playerId);
                    } else if (message instanceof ConnectNgMessage ng) {
                        statusLabel.setText("Status: REJECTED");
                        readyButton.setEnabled(false);
                        disconnectButton.setEnabled(false);
                        log("Connection failed: " + ng.reason());
                        running = false;
                    } else if (message instanceof LobbyStatusMessage status) {
                        readyLabel.setText("Ready: " + status.readyCount() + "/" + status.totalCount());
                        log("Ready: " + status.readyCount() + "/" + status.totalCount());
                    } else if (message instanceof QuestionOptionsMessage options) {
                        currentRound++;
                        statusLabel.setText("Status: PLAYING");
                        questionArea.setText("第" + currentRound + "問\n");
                        for (int i = 0; i < answerButtons.length; i++) {
                            if (i < options.options().size()) {
                                answerButtons[i].setText(i + ": " + options.options().get(i));
                                answerButtons[i].setEnabled(true);
                            } else {
                                answerButtons[i].setText("-");
                                answerButtons[i].setEnabled(false);
                            }
                        }
                    } else if (message instanceof QuestionChunkMessage chunk) {
                        questionArea.append(chunk.chunk());
                    } else if (message instanceof WrongAnswerMessage) {
                        log("不正解。もう一度考えてください。");
                    } else if (message instanceof RoundEndMessage end) {
                        disableAnswerButtons();
                        if (end.winnerId() == 0) {
                            log("全員不正解。正解は選択肢" + end.correctIndex() + "でした。");
                        } else {
                            log(end.winnerName() + " が正解。正解は選択肢" + end.correctIndex() + "でした。");
                        }
                    } else if (message instanceof ScoreMessage score) {
                        log("=== スコア ===");
                        score
                                .scores()
                                .forEach(
                                        entry -> {
                                            String marker = entry.playerId() == playerId ? " <- you" : "";
                                            log(entry.playerName() + ": " + entry.score() + "点" + marker);
                                        });
                    } else if (message instanceof GameEndMessage end) {
                        statusLabel.setText("Status: FINISHED");
                        disableAnswerButtons();
                        readyButton.setEnabled(false);
                        disconnectButton.setEnabled(false);
                        if (end.winnerId() == 0) {
                            log("ゲーム終了: 引き分けです。");
                        } else if (end.winnerId() == playerId) {
                            log("ゲーム終了: あなたの勝ちです！");
                        } else {
                            log("ゲーム終了: 勝者は " + end.winnerName() + " です。");
                        }
                        running = false;
                    } else if (message instanceof DisconnectAckMessage) {
                        log("Disconnected from server.");
                        readyButton.setEnabled(false);
                        disconnectButton.setEnabled(false);
                        running = false;
                    }
                });
    }

    private void sendReady() {
        if (out == null) {
            showError("まだサーバーに接続できていません。");
            return;
        }
        try {
            FrameEncoder.writeFrame(out, MessageType.READY, new byte[0]);
            statusLabel.setText("Status: READY");
            readyButton.setEnabled(false);
            log("READY sent.");
        } catch (IOException e) {
            showError("READY送信に失敗しました: " + e.getMessage());
        }
    }

    private void sendAnswer(int answerIndex) {
        if (out == null) {
            showError("まだサーバーに接続できていません。");
            return;
        }
        try {
            FrameEncoder.writeFrame(out, MessageType.ANSWER, new byte[] { (byte) answerIndex });
            log("ANSWER sent: " + answerIndex);
        } catch (IOException e) {
            showError("回答送信に失敗しました: " + e.getMessage());
        }
    }

    private void disconnect() {
        try {
            running = false;
            if (out != null) {
                FrameEncoder.writeFrame(out, MessageType.DISCONNECT, new byte[0]);
            }
        } catch (IOException e) {
            showError("切断に失敗しました: " + e.getMessage());
        } finally {
            closeSocket();
            frame.dispose();
        }
    }

    private void disableAnswerButtons() {
        for (JButton button : answerButtons) {
            button.setEnabled(false);
        }
    }

    private void log(String message) {
        logArea.append(message + "\n");
    }

    private void logOnUi(String message) {
        SwingUtilities.invokeLater(() -> log(message));
    }

    private void setStatusOnUi(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("Status: " + status));
    }

    private void markDisconnectedOnUi() {
        SwingUtilities.invokeLater(
                () -> {
                    statusLabel.setText("Status: DISCONNECTED");
                    readyButton.setEnabled(false);
                    disconnectButton.setEnabled(false);
                    disableAnswerButtons();
                });
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
        log(message);
    }

    private void showErrorOnUi(String message) {
        SwingUtilities.invokeLater(() -> showError(message));
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static class QuizStagePanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, new Color(12, 23, 82), w, h, new Color(72, 15, 116)));
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(255, 255, 255, 36));
            g2.fillOval(-w / 4, -h / 5, w / 2, h / 2);
            g2.fillOval(w * 2 / 3, h / 8, w / 2, h / 2);
            g2.setColor(new Color(255, 224, 92, 70));
            for (int x = 24; x < w; x += 56) {
                g2.fillOval(x, 18, 18, 18);
            }
            g2.dispose();
        }
    }

    private static class AnswerButton extends JButton {
        AnswerButton(String text) {
            super(text);
            setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setPreferredSize(new Dimension(150, 70));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = isEnabled() ? getBackground() : new Color(110, 110, 110);
            g2.setColor(base);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 48, 48);
            g2.setColor(new Color(255, 255, 255, 90));
            g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 44, 44);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
