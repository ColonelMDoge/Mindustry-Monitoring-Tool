import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MindustryMonitoringTool extends JFrame {

    // UI Colours
    private static final Color BG = new Color(0x1a1a2e);
    private static final Color CARD_BG = new Color(0x16213e);
    private static final Color CARD_BDR = new Color(0x2a2a4a);
    private static final Color FIELD_BG = new Color(0x0f0f23);
    private static final Color TEXT = new Color(0xeeeeee);
    private static final Color MUTED = new Color(0x888888);
    private static final Color ACCENT = new Color(0x4f46e5);
    private static final Color INUSE = new Color(0xdc2626);
    private static final Color GREEN = new Color(0x22c55e);
    private static final Color GRAY = new Color(0x666666);
    private static final Color DARKGRAY = new Color(0x333333);

    // Private fields
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Path CONFIG = Path.of("monitor.properties");
    private static final Path LOCK_FILE = Path.of("monitor.lock");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private ScheduledExecutorService hostPoller;

    private JTextField usernameField, webhookUrlField, gamePathField, serverIpField, apiKeyField;
    private JCheckBox autoStartBox;
    private JTextArea logArea;
    private JButton startBtn;
    private JLabel statusDot, statusLabel;

    private volatile boolean monitoring, isHost;
    private volatile String token, serverIP;
    private Process gameProcess;

    public MindustryMonitoringTool() {
        super("Mindustry Monitoring Tool");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);
        setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("Icon.png")));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                monitoring = false;
                if (gameProcess != null) gameProcess.destroy();
                saveConfig();
                dispose();
                // If the user is the host, it sends a special exit status code
                // 10 is for the .bat file to automatically upload the save file
                if (isHost) {
                    System.exit(10);
                } else {
                    System.exit(0);
                }
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG);

        content.add(buildHeader());
        content.add(gap());

        content.add(buildTopRow());
        content.add(gap());

        content.add(buildCard("Game path", buildPathInner()));
        content.add(gap());

        content.add(buildButtonRow());

        root.add(content, BorderLayout.NORTH);
        setContentPane(root);
        setSize(700, 675);
        setLocationRelativeTo(null);
        loadConfig();

        if (autoStartBox.isSelected()) {
            SwingUtilities.invokeLater(this::toggleMonitoring);
        }

        setVisible(true);
    }

    private Component gap() {
        return Box.createRigidArea(new Dimension(0, 12));
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        p.setAlignmentX(LEFT_ALIGNMENT);

        JLabel title = new JLabel("Mindustry Monitoring Tool");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setBackground(BG);

        statusDot = new JLabel("●");
        statusDot.setForeground(MUTED);
        statusDot.setFont(statusDot.getFont().deriveFont(10f));

        statusLabel = new JLabel("Idle");
        statusLabel.setForeground(MUTED);
        statusLabel.setFont(statusLabel.getFont().deriveFont(13f));

        right.add(statusDot);
        right.add(statusLabel);

        p.add(title, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private JPanel buildTopRow() {
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setBackground(BG);

        leftCol.add(buildCard("Identity", buildIdentityInner()));
        leftCol.add(Box.createRigidArea(new Dimension(0, 12)));
        leftCol.add(buildCard("Webhook", buildWebhookInner()));
        leftCol.add(Box.createRigidArea(new Dimension(0, 12)));
        leftCol.add(buildCard("Connection", buildServerIpInner()));

        JPanel rightCol = new JPanel(new BorderLayout());
        rightCol.setBackground(BG);
        rightCol.add(buildCard("Log", buildLogInner()), BorderLayout.CENTER);

        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setBackground(BG);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.add(leftCol);
        row.add(rightCol);
        return row;
    }

    private JPanel buildCard(String title, JPanel inner) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BDR, 1, true),
                new EmptyBorder(12, 14, 12, 14)));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel label = new JLabel(title.toUpperCase());
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setBorder(new EmptyBorder(0, 0, 8, 0));

        card.add(label, BorderLayout.NORTH);
        card.add(inner, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildIdentityInner() {
        usernameField = styledField();

        autoStartBox = new JCheckBox("Auto-start on launch");
        autoStartBox.setBackground(CARD_BG);
        autoStartBox.setForeground(MUTED);
        autoStartBox.setFocusPainted(false);

        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(CARD_BG);
        p.add(fieldBlock("Username", usernameField), BorderLayout.NORTH);
        p.add(autoStartBox, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildWebhookInner() {
        webhookUrlField = styledField();
        apiKeyField = styledField();

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(CARD_BG);
        p.add(fieldBlock("Webhook URL", webhookUrlField));
        p.add(Box.createRigidArea(new Dimension(0, 8)));
        p.add(fieldBlock("API Key", apiKeyField));
        return p;
    }

    private JPanel buildServerIpInner() {
        serverIpField = styledField();
        return fieldBlock("Server IP (if you become the host)", serverIpField);
    }

    private JPanel buildPathInner() {
        gamePathField = styledField();
        JButton browseExeBtn = styledBtn("Browse...", CARD_BDR);
        browseExeBtn.setForeground(MUTED);
        browseExeBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Select Mindustry executable");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                gamePathField.setText(fc.getSelectedFile().getAbsolutePath());
        });

        JPanel exeRow = new JPanel(new BorderLayout(8, 0));
        exeRow.setBackground(CARD_BG);
        exeRow.add(gamePathField, BorderLayout.CENTER);
        exeRow.add(browseExeBtn, BorderLayout.EAST);

        JPanel exeBlock = new JPanel(new BorderLayout(0, 4));
        exeBlock.setBackground(CARD_BG);
        JLabel exeLbl = new JLabel("Executable path");
        exeLbl.setForeground(GRAY);
        exeLbl.setFont(exeLbl.getFont().deriveFont(12f));
        exeBlock.add(exeLbl, BorderLayout.NORTH);
        exeBlock.add(exeRow, BorderLayout.CENTER);

        JPanel binBlock = new JPanel(new BorderLayout(0, 4));
        binBlock.setBackground(CARD_BG);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBackground(CARD_BG);
        stack.add(exeBlock);
        stack.add(Box.createRigidArea(new Dimension(0, 10)));
        stack.add(binBlock);
        return stack;
    }

    private JPanel buildLogInner() {
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(FIELD_BG);
        logArea.setForeground(MUTED);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBorder(new EmptyBorder(6, 6, 6, 6));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(0, 180));
        scroll.setMinimumSize(new Dimension(0, 180));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        scroll.setBorder(BorderFactory.createLineBorder(DARKGRAY));
        scroll.getViewport().setBackground(FIELD_BG);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(CARD_BG);
        wrap.add(scroll, BorderLayout.CENTER);
        return wrap;
    }

    private JPanel buildButtonRow() {
        startBtn = styledBtn("▶  Start monitoring", ACCENT);
        startBtn.addActionListener(e -> toggleMonitoring());

        JPanel row = new JPanel(new GridLayout(1, 1, 10, 0));
        row.setBackground(BG);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.add(startBtn);
        return row;
    }

    private JPanel fieldBlock(String label, JTextField field) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(CARD_BG);

        JLabel lbl = new JLabel(label);
        lbl.setForeground(GRAY);
        lbl.setFont(lbl.getFont().deriveFont(12f));

        p.add(lbl, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private JTextField styledField() {
        JTextField tf = new JTextField();
        tf.setBackground(FIELD_BG);
        tf.setForeground(TEXT);
        tf.setCaretColor(TEXT);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DARKGRAY, 1, true),
                new EmptyBorder(6, 8, 6, 8)));
        return tf;
    }

    private JButton styledBtn(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(TEXT);
        btn.setFocusPainted(false);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 13f));
        btn.setBorder(new EmptyBorder(10, 16, 10, 16));
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.putClientProperty("originalColor", bg);
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!btn.isEnabled()) return;
                btn.putClientProperty("originalColor", btn.getBackground());
                btn.setBackground(btn.getBackground().darker());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!btn.isEnabled()) return;
                Color original = (Color) btn.getClientProperty("originalColor");
                if (original != null) btn.setBackground(original);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!btn.isEnabled()) return;
                Color original = (Color) btn.getClientProperty("originalColor");
                if (original != null) btn.setBackground(original);
            }
        });
        return btn;
    }

    private void resetStartBtn() {
        startBtn.setText("▶  Start monitoring");
        startBtn.setBackground(ACCENT);
        startBtn.setEnabled(true);
        startBtn.putClientProperty("originalColor", ACCENT);
    }

    private void setStatus(boolean running) {
        statusDot.setForeground(running ? GREEN : MUTED);
        statusLabel.setText(running ? "Running" : "Idle");
        statusLabel.setForeground(running ? GREEN : MUTED);
    }

    private void showAlert(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Notice", JOptionPane.WARNING_MESSAGE);
    }

    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("api-key", apiKeyField.getText().trim());
    }

    private void toggleMonitoring() {

        // Initial checks before starting Mindustry
        if (usernameField.getText().isBlank()) {
            showAlert("Please set a username!");
            return;
        }

        if (webhookUrlField.getText().isBlank()) {
            showAlert("Please provide the webhook URL!");
            return;
        }

        if (serverIpField.getText().isBlank()) {
            showAlert("Please provide the server IP!");
            return;
        }

        if (monitoring) {
            abortMonitoring("Monitoring stopped.");
            return;
        }

        String path = gamePathField.getText().trim();
        if (path.isBlank()) {
            showAlert("No game path set. Please browse to Mindustry.exe.");
            return;
        }
        File exe = new File(path);
        if (!exe.exists()) {
            showAlert("File not found: " + path);
            return;
        }
        if (!exe.getName().contains("Mindustry.exe")) {
            showAlert("Provided game path is not a Mindustry executable.");
            return;
        }

        startBtn.setText("Connecting...");
        startBtn.setBackground(INUSE);
        startBtn.setEnabled(false);
        startBtn.putClientProperty("originalColor", INUSE);
        saveConfig();

        new Thread(() -> {
            try {
                JsonObject obj = new JsonObject();
                obj.addProperty("user", usernameField.getText());
                obj.addProperty("active", true);
                obj.addProperty("ip", serverIpField.getText());

                HttpRequest req = baseRequest(webhookUrlField.getText().trim() + "/status")
                        .POST(HttpRequest.BodyPublishers.ofString(obj.toString()))
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                SwingUtilities.invokeLater(() -> handleInitialWebhook(res, exe));

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    resetStartBtn();
                    showAlert("Could not reach webhook server:\n" + ex.getMessage());
                    log("Connection error: " + ex.getMessage());
                });
            }
        }).start();
    }

    private void heartBeat() {
        hostPoller = Executors.newSingleThreadScheduledExecutor();
        hostPoller.scheduleAtFixedRate(() -> {
            if (!monitoring) return;
            try {
                JsonObject heartbeatObj = new JsonObject();
                heartbeatObj.addProperty("user", usernameField.getText());
                heartbeatObj.addProperty("token", token);

                HttpRequest heartbeatReq = baseRequest(webhookUrlField.getText().trim() + "/heartbeat")
                        .POST(HttpRequest.BodyPublishers.ofString(heartbeatObj.toString()))
                        .timeout(Duration.ofSeconds(4))
                        .build();

                HTTP.sendAsync(heartbeatReq, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(res -> SwingUtilities.invokeLater(() -> {
                            log("Heartbeat signal sent (HTTP " + res.statusCode() + ")");
                            JsonObject resp = JsonParser.parseString(res.body()).getAsJsonObject();
                            String host = resp.get("host").isJsonNull() ? null : resp.get("host").getAsString();
                            isHost = resp.get("isHost").getAsBoolean();
                            if (host == null && !isHost) {
                                log("⚠ Host has left — shutting down Mindustry.");
                                abortMonitoring("Shut down by host disconnect.");
                            }
                        }))
                        .exceptionally(ex -> {
                            SwingUtilities.invokeLater(() -> log("Heartbeat signal failed: " + ex.getMessage()));
                            return null;
                        });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> log("Poller scheduling error: " + ex.getMessage()));
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void abortMonitoring(String reason) {
        if (!monitoring && gameProcess == null) return;
        monitoring = false;
        if (hostPoller != null) {
            hostPoller.shutdownNow();
            hostPoller = null;
        }
        if (gameProcess != null) {
            gameProcess.destroy();
            gameProcess = null;
        }
        sendOffline(usernameField.getText());
        SwingUtilities.invokeLater(() -> {
            resetStartBtn();
            setStatus(false);
            if (!reason.isBlank()) log(reason);
        });
    }

    private void handleInitialWebhook(HttpResponse<String> res, File exe) {

        if (res.statusCode() == 409) {
            resetStartBtn();
            showAlert("A user with the name \"" + usernameField.getText() + "\" is already online!\nChoose a different username.");
            log("Monitoring aborted — duplicate username.");
            return;
        }

        if (res.statusCode() == 401) {
            resetStartBtn();
            showAlert("Authentication failed. Please provide the correct API key.");
            log("Monitoring aborted — failed to authenticate.");
            return;
        }

        if (res.statusCode() != 200) {
            resetStartBtn();
            showAlert("Webhook failed (HTTP " + res.statusCode() + "). Check your URL.");
            log("Webhook failed (HTTP " + res.statusCode() + ")");
            return;
        }

        JsonObject resp = JsonParser.parseString(res.body()).getAsJsonObject();
        JsonArray onlineUsers = resp.getAsJsonArray("onlineUsers");
        boolean someoneOnline = resp.get("someoneOnline").getAsBoolean();
        String host = resp.get("host").getAsString();
        isHost = usernameField.getText().equals(host);
        token = resp.get("token").getAsString();
        serverIP = resp.get("serverIP").getAsString();

        log("Webhook and API Key OK (HTTP 200)");
        monitoring = true;
        heartBeat();

        if (someoneOnline) {
            StringBuilder names = new StringBuilder();
            for (JsonElement e : onlineUsers) {
                String u = e.getAsString();
                if (!u.equals(usernameField.getText())) {
                    names.append("  • ").append(u);
                    if (u.equals(host)) names.append(" (HOST)");
                    names.append("\n");
                }
            }
            if (!names.isEmpty()) {
                int choice = JOptionPane.showConfirmDialog(
                        this,
                        "There is current activity in the Mindustry game!" +
                                "\n\nOnline:\n" + names +
                                "\nCURRENT SERVER IP: " + serverIP + " (Check logs to copy it)" +
                                "\n\nDo you acknowledge and wish to continue?",
                        "Active Session Detected",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (choice != JOptionPane.YES_OPTION) {
                    abortMonitoring("Monitoring aborted by user.");
                    return;
                }
            }
        }

        if (isHost) {
            log("You are the host.");
        }
        log("The current server IP: " + serverIP);
        launchGame(exe);
    }

    private void launchGame(File exe) {
        startBtn.setText("Monitoring...");
        startBtn.setBackground(INUSE);
        startBtn.setEnabled(false);
        startBtn.putClientProperty("originalColor", INUSE);
        setStatus(true);
        log("✓ Launching Mindustry...");

        Thread t = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(exe.getAbsolutePath());
                pb.directory(exe.getParentFile());
                gameProcess = pb.start();
                gameProcess.waitFor();
                abortMonitoring("");
                SwingUtilities.invokeLater(() -> dispatchEvent(new WindowEvent(MindustryMonitoringTool.this, WindowEvent.WINDOW_CLOSING)));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    log("Error: " + ex.getMessage());
                    abortMonitoring("");
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void sendOffline(String username) {
        new Thread(() -> {
            try {
                JsonObject obj = new JsonObject();
                obj.addProperty("user", username);
                obj.addProperty("active", false);
                obj.addProperty("token", token);

                HttpRequest req = baseRequest(webhookUrlField.getText().trim() + "/status")
                        .POST(HttpRequest.BodyPublishers.ofString(obj.toString()))
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(res -> SwingUtilities.invokeLater(() ->
                                log("Offline signal sent (HTTP " + res.statusCode() + ")")))
                        .exceptionally(ex -> {
                            SwingUtilities.invokeLater(() -> log("Offline signal failed: " + ex.getMessage()));
                            return null;
                        });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> log("Offline signal error: " + ex.getMessage()));
            }
        }).start();
    }

    private void log(String msg) {
        String line = "[" + LocalTime.now().format(TIME_FMT) + "] " + msg + "\n";
        SwingUtilities.invokeLater(() -> {
            logArea.append(line);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // Configs to remember user inputs
    private void saveConfig() {
        try {
            Properties p = new Properties();
            p.setProperty("username", usernameField.getText());
            p.setProperty("webhookUrl", webhookUrlField.getText());
            p.setProperty("apiKey", apiKeyField.getText());
            p.setProperty("gamePath", gamePathField.getText());
            p.setProperty("serverIp", serverIpField.getText());
            p.setProperty("autoStart", String.valueOf(autoStartBox.isSelected()));
            try (OutputStream out = Files.newOutputStream(CONFIG)) {
                p.store(out, null);
            }
        } catch (Exception ignored) {
        }
    }

    private void loadConfig() {
        if (!Files.exists(CONFIG)) return;
        try {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(CONFIG)) {
                p.load(in);
            }
            usernameField.setText(p.getProperty("username", ""));
            webhookUrlField.setText(p.getProperty("webhookUrl", ""));
            apiKeyField.setText(p.getProperty("apiKey", ""));
            gamePathField.setText(p.getProperty("gamePath", ""));
            serverIpField.setText(p.getProperty("serverIp", ""));
            autoStartBox.setSelected(Boolean.parseBoolean(p.getProperty("autoStart", "false")));
        } catch (Exception ignored) {
        }
    }

    // Prevents multiple clients at once
    private static boolean acquireLock() {
        try {
            if (Files.exists(LOCK_FILE)) {
                String pid = Files.readString(LOCK_FILE).trim();
                boolean stillRunning = ProcessHandle.of(Long.parseLong(pid))
                        .map(ProcessHandle::isAlive)
                        .orElse(false);
                if (stillRunning) return false;
                Files.delete(LOCK_FILE);
            }
            Files.writeString(LOCK_FILE, String.valueOf(ProcessHandle.current().pid()),
                    StandardOpenOption.CREATE_NEW);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void releaseLock() {
        try {
            Files.deleteIfExists(LOCK_FILE);
        } catch (IOException ignored) {
        }
    }

    public static void main(String[] args) {
        if (!acquireLock()) {
            JOptionPane.showMessageDialog(null,
                    "Mindustry Monitoring Tool is already running!",
                    "Already Running",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(MindustryMonitoringTool::releaseLock));
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(MindustryMonitoringTool::new);
    }
}