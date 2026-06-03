import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import shared.s2c.ScoreEntry;
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
  private final LobbyIconsPanel lobbyIconsPanel = new LobbyIconsPanel();
  private final JPanel cards = new JPanel(new CardLayout());

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
  private List<ScoreEntry> latestScores = new ArrayList<>();
  private JPanel resultPanel;

  public GuiClient(String playerName, int port) {
    this.playerName = playerName;
    this.port = port;

    playerLabel.setText("Player: " + playerName);
    playerLabel.setForeground(Color.WHITE);
    playerLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
    statusLabel.setForeground(new Color(255, 224, 92));
    statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
    readyLabel.setForeground(new Color(180, 220, 255));
    readyLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
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

    JPanel topPanel = new JPanel(new BorderLayout(12, 0));
    topPanel.setBackground(new Color(11, 17, 43));
    topPanel.add(new PlayerStatusPanel(playerLabel, statusLabel), BorderLayout.WEST);
    topPanel.add(lobbyIconsPanel, BorderLayout.CENTER);

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
    controlPanel.setOpaque(false);
    readyButton.addActionListener(e -> sendReady());
    disconnectButton.addActionListener(e -> disconnect());
    controlPanel.add(readyButton);
    controlPanel.add(disconnectButton);
    topPanel.add(controlPanel, BorderLayout.EAST);

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
    cards.add(mainPanel, "GAME");

    frame.setLayout(new BorderLayout());
    frame.add(topPanel, BorderLayout.NORTH);
    frame.add(cards, BorderLayout.CENTER);

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
              + "接続先: localhost:"
              + port);
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
            lobbyIconsPanel.setCounts(status.totalCount(), status.readyCount());
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
            disableAnswerButtons();
            log("残念、不正解！");
          } else if (message instanceof RoundEndMessage end) {
            disableAnswerButtons();
            if (end.winnerId() == 0) {
              log("全員不正解。正解は選択肢" + end.correctIndex() + "でした。");
            } else {
              log(end.winnerName() + " が正解。正解は選択肢" + end.correctIndex() + "でした。");
            }
          } else if (message instanceof ScoreMessage score) {
            latestScores = new ArrayList<>(score.scores());
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
            showResultScreen();
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

  private void sendRematchReady() {
    resetToLobbyView();
    sendReady();
  }

  private void sendAnswer(int answerIndex) {
    if (out == null) {
      showError("まだサーバーに接続できていません。");
      return;
    }
    try {
      FrameEncoder.writeFrame(out, MessageType.ANSWER, new byte[] {(byte) answerIndex});
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
      System.exit(0);
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

  private void resetToLobbyView() {
    currentRound = 0;
    questionArea.setText("");
    disableAnswerButtons();
    statusLabel.setText("Status: WAITING");
    ((CardLayout) cards.getLayout()).show(cards, "GAME");
  }

  private void showResultScreen() {
    if (resultPanel != null) {
      cards.remove(resultPanel);
    }
    resultPanel = createResultPanel();
    cards.add(resultPanel, "RESULT");
    ((CardLayout) cards.getLayout()).show(cards, "RESULT");
    cards.revalidate();
    cards.repaint();
  }

  private JPanel createResultPanel() {
    List<ScoreEntry> ranking = new ArrayList<>(latestScores);
    ranking.sort(Comparator.comparingInt(ScoreEntry::score).reversed());

    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(new Color(12, 18, 46));

    JLabel title = new JLabel("Final Ranking", JLabel.CENTER);
    title.setForeground(new Color(255, 224, 92));
    title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
    panel.add(title, BorderLayout.NORTH);
    panel.add(new PodiumPanel(ranking), BorderLayout.CENTER);

    JPanel buttons = new JPanel();
    buttons.setBackground(new Color(12, 18, 46));
    JButton rematchButton = new JButton("もう一度対戦する");
    JButton leaveButton = new JButton("部屋を抜ける");
    rematchButton.addActionListener(e -> sendRematchReady());
    leaveButton.addActionListener(e -> disconnect());
    buttons.add(rematchButton);
    buttons.add(leaveButton);
    panel.add(buttons, BorderLayout.SOUTH);

    return panel;
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

  private static class PlayerStatusPanel extends JPanel {
    PlayerStatusPanel(JLabel playerLabel, JLabel statusLabel) {
      setOpaque(false);
      setLayout(new BorderLayout(10, 0));
      setPreferredSize(new Dimension(280, 86));

      JPanel labels = new JPanel(new GridLayout(2, 1));
      labels.setOpaque(false);
      labels.add(playerLabel);
      labels.add(statusLabel);

      add(new PersonIcon(new Color(64, 132, 220), true), BorderLayout.WEST);
      add(labels, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setPaint(
          new GradientPaint(
              0, 0, new Color(24, 39, 96), getWidth(), getHeight(), new Color(19, 78, 122)));
      g2.fillRoundRect(6, 8, getWidth() - 12, getHeight() - 16, 24, 24);
      g2.setColor(new Color(255, 255, 255, 70));
      g2.drawRoundRect(6, 8, getWidth() - 13, getHeight() - 17, 24, 24);
      g2.dispose();
      super.paintComponent(g);
    }
  }

  private static class LobbyIconsPanel extends JPanel {
    private int totalCount = 0;
    private int readyCount = 0;

    LobbyIconsPanel() {
      setOpaque(false);
      setPreferredSize(new Dimension(300, 86));
    }

    void setCounts(int totalCount, int readyCount) {
      this.totalCount = Math.max(0, Math.min(4, totalCount));
      this.readyCount = Math.max(0, Math.min(this.totalCount, readyCount));
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(new Color(220, 235, 255));
      g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
      g2.drawString("Lobby players", 12, 18);

      for (int i = 0; i < 4; i++) {
        int x = 16 + i * 68;
        boolean joined = i < totalCount;
        boolean ready = i < readyCount;
        drawLobbyIcon(g2, x, 28, joined, ready);
      }
      g2.dispose();
    }

    private void drawLobbyIcon(Graphics2D g2, int x, int y, boolean joined, boolean ready) {
      Color body = joined ? new Color(120, 150, 210) : new Color(68, 76, 96);
      g2.setColor(body);
      g2.fillOval(x + 14, y, 26, 26);
      g2.fillRoundRect(x + 6, y + 28, 42, 28, 20, 20);
      g2.setColor(new Color(255, 255, 255, joined ? 85 : 35));
      g2.drawOval(x + 14, y, 26, 26);
      g2.drawRoundRect(x + 6, y + 28, 42, 28, 20, 20);

      if (joined) {
        g2.setColor(ready ? new Color(75, 220, 120) : new Color(245, 178, 60));
        g2.fillOval(x + 38, y - 2, 22, 22);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g2.drawString(ready ? "R" : "...", x + (ready ? 45 : 42), y + 13);
      }
    }
  }

  private static class PersonIcon extends JPanel {
    private final Color color;
    private final boolean crown;

    PersonIcon(Color color, boolean crown) {
      this.color = color;
      this.crown = crown;
      setOpaque(false);
      setPreferredSize(new Dimension(74, 74));
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(color);
      g2.fillOval(22, 12, 30, 30);
      g2.fillRoundRect(12, 42, 50, 30, 24, 24);
      g2.setColor(new Color(255, 255, 255, 95));
      g2.drawOval(22, 12, 30, 30);
      g2.drawRoundRect(12, 42, 50, 30, 24, 24);
      if (crown) {
        g2.setColor(new Color(255, 224, 92));
        g2.fillPolygon(new int[] {24, 32, 39, 47, 54}, new int[] {13, 2, 13, 2, 13}, 5);
      }
      g2.dispose();
    }
  }

  private static class PodiumPanel extends JPanel {
    private final List<ScoreEntry> ranking;

    PodiumPanel(List<ScoreEntry> ranking) {
      this.ranking = ranking;
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth();
      int h = getHeight();
      g2.setPaint(new GradientPaint(0, 0, new Color(28, 38, 92), w, h, new Color(74, 24, 103)));
      g2.fillRect(0, 0, w, h);

      int columnWidth = Math.max(150, w / 5);
      int startX = (w - columnWidth * 4) / 2;
      int baseY = h - 54;

      drawRankGroup(g2, playersAtRank(2), "2nd", startX, columnWidth, baseY, 128);
      drawRankGroup(g2, playersAtRank(1), "1st", startX + columnWidth, columnWidth, baseY, 176);
      drawRankGroup(g2, playersAtRank(3), "3rd", startX + columnWidth * 2, columnWidth, baseY, 104);
      drawRankGroup(g2, playersAtRank(4), "4th", startX + columnWidth * 3, columnWidth, baseY, 0);
      g2.dispose();
    }

    private List<ScoreEntry> playersAtRank(int rank) {
      return ranking.stream().filter(entry -> rankOf(entry) == rank).toList();
    }

    private int rankOf(ScoreEntry entry) {
      int higherPlayers = 0;
      for (ScoreEntry other : ranking) {
        if (other.score() > entry.score()) {
          higherPlayers++;
        }
      }
      return higherPlayers + 1;
    }

    private void drawRankGroup(
        Graphics2D g2,
        List<ScoreEntry> players,
        String rank,
        int x,
        int width,
        int baseY,
        int podiumHeight) {
      if (players.isEmpty()) {
        return;
      }
      if (podiumHeight > 0) {
        drawPodium(g2, x, baseY - podiumHeight, width, podiumHeight);
      }

      int playerWidth = 116;
      int gap = -18;
      int groupWidth = players.size() * playerWidth + (players.size() - 1) * gap;
      int startPlayerX = x + (width - groupWidth) / 2;
      int playerY = baseY - podiumHeight - 118;
      for (int i = 0; i < players.size(); i++) {
        int px = startPlayerX + i * (playerWidth + gap);
        drawPlayer(g2, players.get(i), rank, px, playerY, playerWidth);
      }
    }

    private void drawPlayer(Graphics2D g2, ScoreEntry entry, String rank, int x, int y, int width) {
      int centerX = x + width / 2;
      g2.setColor(new Color(255, 224, 92));
      g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
      drawCentered(g2, rank, centerX, y - 8);

      g2.setColor(new Color(95, 160, 230));
      g2.fillOval(centerX - 32, y, 64, 64);
      g2.fillRoundRect(centerX - 58, y + 56, 116, 64, 32, 32);
      g2.setColor(new Color(255, 255, 255, 95));
      g2.drawOval(centerX - 32, y, 64, 64);
      g2.drawRoundRect(centerX - 58, y + 56, 116, 64, 32, 32);
      g2.setColor(Color.WHITE);
      g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
      drawCentered(g2, fitText(g2, entry.playerName(), 104), centerX, y + 84);
      g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
      drawCentered(g2, entry.score() + " pt", centerX, y + 106);
    }

    private void drawPodium(Graphics2D g2, int x, int y, int width, int height) {
      g2.setColor(new Color(238, 192, 72));
      g2.fillRoundRect(x + 10, y, width - 20, height, 18, 18);
      g2.setColor(new Color(255, 255, 255, 90));
      g2.drawRoundRect(x + 10, y, width - 21, height - 1, 18, 18);
    }

    private void drawCentered(Graphics2D g2, String text, int centerX, int y) {
      int textWidth = g2.getFontMetrics().stringWidth(text);
      g2.drawString(text, centerX - textWidth / 2, y);
    }

    private String fitText(Graphics2D g2, String text, int maxWidth) {
      if (g2.getFontMetrics().stringWidth(text) <= maxWidth) {
        return text;
      }
      String suffix = "...";
      for (int end = text.length(); end > 0; end--) {
        String shortened = text.substring(0, end) + suffix;
        if (g2.getFontMetrics().stringWidth(shortened) <= maxWidth) {
          return shortened;
        }
      }
      return suffix;
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
