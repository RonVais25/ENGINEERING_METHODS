package server.boundary;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import server.db.DBConnection;
import server.net.OrderServer;
import server.net.ServerListener;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.sql.Connection;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerGUI extends Application implements ServerListener {

    // Design tokens — mirror the client palette
    private static final String G800 = "#1a3320";
    private static final String G700 = "#254d2e";
    private static final String G600 = "#2e6638";
    private static final String G500 = "#3d8c4a";
    private static final String G400 = "#55b363";
    private static final String G300 = "#80cb8b";
    private static final String G200 = "#c2e5c8";
    private static final String G100 = "#e5f3e8";
    private static final String G50  = "#f2f9f3";
    private static final String RED  = "#c94040";
    private static final String AMBER = "#d4a847";

    private OrderServer server;

    // Header status
    private Circle statusDot;
    private Label  statusLabel;

    // Controls
    private TextField     portField;
    private PasswordField passwordField;
    private Button        startBtn;
    private Button        stopBtn;

    // Reachable-at info (populated on start)
    private VBox  reachableBox;
    private Label reachableTitle;

    // Clients table
    private final ObservableList<ClientRow> clients = FXCollections.observableArrayList();
    private final Map<String, ClientRow> clientByIp = new HashMap<>();

    // Log
    private VBox logBox;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + G50 + ";");
        root.setTop(buildTopbar());
        root.setCenter(buildBody());

        Scene scene = new Scene(root, 980, 640);
        stage.setScene(scene);
        stage.setTitle("GoNature Server");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setOnCloseRequest(e -> { if (server != null) server.stop(); });
        stage.show();
    }

    public static void main(String[] args) { launch(args); }

    // ─── Topbar ───────────────────────────────────────────────────────────────

    private HBox buildTopbar() {
        StackPane iconTile = new StackPane();
        iconTile.setPrefSize(38, 38);
        iconTile.setMinSize(38, 38);
        iconTile.setStyle("-fx-background-color:" + G500 + "; -fx-background-radius:11;");
        SVGPath leaf = new SVGPath();
        leaf.setContent("M11 2C6.5 2 3 6 3 10.5c0 3 2 6.5 8 9.5 6-3 8-6.5 8-9.5C19 6 15.5 2 11 2z");
        leaf.setFill(Color.WHITE);
        leaf.setScaleX(0.8);
        leaf.setScaleY(0.8);
        iconTile.getChildren().add(leaf);

        Label title = new Label("GoNature");
        title.setStyle("-fx-font-size:16; -fx-font-weight:bold; -fx-text-fill:white;");
        Label sub = new Label("SERVER");
        sub.setStyle("-fx-font-size:10; -fx-font-weight:bold; -fx-text-fill:" + G300 + ";");
        VBox titleBox = new VBox(1, title, sub);

        HBox left = new HBox(10, iconTile, titleBox);
        left.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusDot   = new Circle(5, Color.web("#d4a847"));
        statusLabel = new Label("Stopped");
        statusLabel.setStyle("-fx-font-size:13; -fx-font-weight:bold; -fx-text-fill:" + G200 + ";");

        HBox statusPill = new HBox(8, statusDot, statusLabel);
        statusPill.setAlignment(Pos.CENTER_LEFT);
        statusPill.setPadding(new Insets(6, 14, 6, 14));
        statusPill.setStyle("-fx-background-color:rgba(255,255,255,0.08); -fx-background-radius:100;");

        HBox bar = new HBox(left, spacer, statusPill);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14, 22, 14, 18));
        bar.setStyle("-fx-background-color:" + G800 + ";");
        return bar;
    }

    // ─── Body ─────────────────────────────────────────────────────────────────

    private SplitPane buildBody() {
        VBox leftCol  = new VBox(16, buildControlCard());
        leftCol.setPadding(new Insets(22, 14, 22, 22));
        leftCol.setMinWidth(320);

        VBox rightCol = new VBox(16, buildClientsCard(), buildLogCard());
        rightCol.setPadding(new Insets(22, 22, 22, 14));
        VBox.setVgrow(rightCol.getChildren().get(0), Priority.ALWAYS);

        SplitPane split = new SplitPane(leftCol, rightCol);
        split.setDividerPositions(0.34);
        split.setStyle("-fx-background-color:" + G50 + ";");
        return split;
    }

    private VBox buildCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(22, 24, 22, 24));
        card.setStyle("-fx-background-color:white; -fx-background-radius:14;" +
                      " -fx-border-color:" + G200 + "; -fx-border-radius:14; -fx-border-width:1;");
        return card;
    }

    private VBox buildControlCard() {
        VBox card = buildCard();

        Label title = new Label("SERVER CONTROLS");
        title.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:" + G500 + ";");

        Label portLbl = new Label("Port");
        portLbl.setStyle(fieldLabelStyle());
        portField = new TextField("5555");
        portField.setStyle(inputStyle());

        Label pwLbl = new Label("Database Password");
        pwLbl.setStyle(fieldLabelStyle());
        passwordField = new PasswordField();
        passwordField.setPromptText("MySQL root password");
        passwordField.setStyle(inputStyle());
        String envPw = System.getenv("DB_PASSWORD");
        if (envPw != null) passwordField.setText(envPw);

        startBtn = buildPrimaryButton("Start Server", "▶");
        stopBtn  = buildSecondaryButton("Stop");
        stopBtn.setDisable(true);

        HBox btnRow = new HBox(10, startBtn, stopBtn);
        HBox.setHgrow(startBtn, Priority.ALWAYS);
        startBtn.setMaxWidth(Double.MAX_VALUE);

        startBtn.setOnAction(e -> startServer());
        stopBtn.setOnAction(e -> stopServer());

        reachableTitle = new Label("REACHABLE AT");
        reachableTitle.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:" + G500 +
                                "; -fx-padding:10 0 0 0;");

        reachableBox = new VBox(3);
        populateReachable();

        portField.textProperty().addListener((obs, oldV, newV) -> populateReachable());

        card.getChildren().addAll(title, portLbl, portField, pwLbl, passwordField,
                                  btnRow, reachableTitle, reachableBox);
        return card;
    }

    private void populateReachable() {
        String port = portField.getText().trim();
        if (port.isEmpty()) port = "5555";

        List<String> ips = getLanIPv4();
        reachableBox.getChildren().clear();

        if (ips.isEmpty()) {
            Label none = new Label("localhost:" + port + "  (no LAN interface found)");
            none.setStyle("-fx-font-size:12; -fx-font-family:monospace; -fx-text-fill:" + G700 + ";");
            reachableBox.getChildren().add(none);
            return;
        }

        for (String ip : ips) {
            Label row = new Label(ip + ":" + port);
            row.setStyle("-fx-font-size:13; -fx-font-weight:bold; -fx-font-family:monospace;" +
                         " -fx-text-fill:" + G700 + ";");
            reachableBox.getChildren().add(row);
        }
    }

    private static List<String> getLanIPv4() {
        List<String> result = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        result.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private VBox buildClientsCard() {
        VBox card = buildCard();
        VBox.setVgrow(card, Priority.ALWAYS);

        Label title = new Label("CONNECTED CLIENTS");
        title.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:" + G500 + ";");

        TableView<ClientRow> table = new TableView<>(clients);
        table.setPlaceholder(new Label("No clients yet"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<ClientRow, String> ipCol = new TableColumn<>("IP Address");
        ipCol.setCellValueFactory(new PropertyValueFactory<>("ip"));

        TableColumn<ClientRow, String> hostCol = new TableColumn<>("Host Name");
        hostCol.setCellValueFactory(new PropertyValueFactory<>("host"));

        TableColumn<ClientRow, String> timeCol = new TableColumn<>("Connected At");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("connectedAt"));

        TableColumn<ClientRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                String fg = s.equals("Connected") ? G600 : "#888";
                setStyle("-fx-font-weight:bold; -fx-text-fill:" + fg + ";");
            }
        });

        table.getColumns().setAll(java.util.List.of(ipCol, hostCol, timeCol, statusCol));

        card.getChildren().addAll(title, table);
        return card;
    }

    private VBox buildLogCard() {
        VBox card = buildCard();
        card.setPrefHeight(180);
        card.setMinHeight(180);

        Label title = new Label("ACTIVITY LOG");
        title.setStyle("-fx-font-size:11; -fx-font-weight:bold; -fx-text-fill:" + G500 + ";");

        logBox = new VBox(2);
        Label empty = new Label("No activity yet…");
        empty.setStyle("-fx-font-size:11; -fx-font-style:italic; -fx-text-fill:" + G300 + ";");
        logBox.getChildren().add(empty);

        ScrollPane scroll = new ScrollPane(logBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:" + G50 + "; -fx-background-color:" + G50 + ";" +
                        " -fx-border-color:" + G200 + "; -fx-border-radius:10; -fx-border-width:1;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        card.getChildren().addAll(title, scroll);
        return card;
    }

    // ─── Server lifecycle ─────────────────────────────────────────────────────

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            appendLog(false, "Invalid port number");
            return;
        }

        DBConnection.setPassword(passwordField.getText());

        // Probe DB before opening the socket so the user sees the failure here.
        try (Connection c = DBConnection.getConnection()) {
            appendLog(true, "Database connection OK");
        } catch (Exception ex) {
            appendLog(false, "Database connection failed: " + ex.getMessage());
            return;
        }

        server = new OrderServer(port, this);
        server.start();

        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        portField.setDisable(true);
        passwordField.setDisable(true);
    }

    private void stopServer() {
        if (server != null) server.stop();
    }

    // ─── ServerListener (called from background threads) ──────────────────────

    @Override public void onStarted(int port) {
        Platform.runLater(() -> {
            statusDot.setFill(Color.web("#64c864"));
            statusLabel.setText("Running on port " + port);
            appendLog(true, "Server started on port " + port);
            for (String ip : getLanIPv4()) {
                appendLog(true, "Reachable at " + ip + ":" + port);
            }
        });
    }

    @Override public void onStopped() {
        Platform.runLater(() -> {
            statusDot.setFill(Color.web(AMBER));
            statusLabel.setText("Stopped");
            startBtn.setDisable(false);
            stopBtn.setDisable(true);
            portField.setDisable(false);
            passwordField.setDisable(false);
            appendLog(true, "Server stopped");
        });
    }

    @Override public void onClientConnected(String ip, String host) {
        Platform.runLater(() -> {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            ClientRow existing = clientByIp.get(ip);
            if (existing != null) {
                existing.setStatus("Connected");
                existing.setConnectedAt(time);
            } else {
                ClientRow row = new ClientRow(ip, host, time, "Connected");
                clientByIp.put(ip, row);
                clients.add(0, row);
            }
            appendLog(true, "Client connected: " + ip + " (" + host + ")");
        });
    }

    @Override public void onClientDisconnected(String ip, String host) {
        Platform.runLater(() -> {
            ClientRow row = clientByIp.get(ip);
            if (row != null) row.setStatus("Disconnected");
            appendLog(true, "Client disconnected: " + ip);
        });
    }

    @Override public void onLog(String message) {
        Platform.runLater(() -> appendLog(true, message));
    }

    @Override public void onError(String message) {
        Platform.runLater(() -> appendLog(false, message));
    }

    // ─── Log helpers ──────────────────────────────────────────────────────────

    private void appendLog(boolean ok, String message) {
        if (!logBox.getChildren().isEmpty()) {
            javafx.scene.Node first = logBox.getChildren().get(0);
            if (first instanceof Label l && l.getStyle().contains("italic"))
                logBox.getChildren().clear();
        }
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Label entry = new Label(time + "  " + message);
        entry.setStyle("-fx-font-size:11; -fx-font-family:monospace; -fx-text-fill:" +
                       (ok ? G600 : RED) + ";");
        entry.setWrapText(true);
        logBox.getChildren().add(0, entry);
        while (logBox.getChildren().size() > 200) {
            logBox.getChildren().remove(logBox.getChildren().size() - 1);
        }
    }

    // ─── Style helpers ────────────────────────────────────────────────────────

    private Button buildPrimaryButton(String text, String icon) {
        Button btn = new Button(icon + "  " + text);
        String base = "-fx-font-size:14; -fx-font-weight:bold; -fx-text-fill:white;" +
                      " -fx-background-radius:10; -fx-padding:11 18 11 18; -fx-cursor:hand;";
        btn.setStyle(base + "-fx-background-color:" + G500 + ";");
        btn.setOnMouseEntered(e -> btn.setStyle(base + "-fx-background-color:" + G600 + ";"));
        btn.setOnMouseExited(e  -> btn.setStyle(base + "-fx-background-color:" + G500 + ";"));
        return btn;
    }

    private Button buildSecondaryButton(String text) {
        Button btn = new Button(text);
        String base = "-fx-font-size:14; -fx-font-weight:bold; -fx-text-fill:" + G700 +
                      "; -fx-background-radius:10; -fx-border-radius:10; -fx-border-width:1.5;" +
                      " -fx-padding:11 18 11 18; -fx-cursor:hand;";
        btn.setStyle(base + "-fx-background-color:" + G100 + "; -fx-border-color:" + G200 + ";");
        btn.setOnMouseEntered(e -> btn.setStyle(base + "-fx-background-color:" + G200 + "; -fx-border-color:" + G200 + ";"));
        btn.setOnMouseExited(e  -> btn.setStyle(base + "-fx-background-color:" + G100 + "; -fx-border-color:" + G200 + ";"));
        return btn;
    }

    private String fieldLabelStyle() {
        return "-fx-font-size:12; -fx-font-weight:bold; -fx-text-fill:" + G600 + ";";
    }

    private String inputStyle() {
        return "-fx-font-size:14; -fx-background-color:" + G50 + "; -fx-border-color:" + G200 +
               "; -fx-border-width:1.5; -fx-border-radius:10; -fx-background-radius:10;" +
               " -fx-padding:10 14 10 14;";
    }

    // ─── Client row model ─────────────────────────────────────────────────────

    public static class ClientRow {
        private final javafx.beans.property.SimpleStringProperty ip;
        private final javafx.beans.property.SimpleStringProperty host;
        private final javafx.beans.property.SimpleStringProperty connectedAt;
        private final javafx.beans.property.SimpleStringProperty status;

        public ClientRow(String ip, String host, String connectedAt, String status) {
            this.ip          = new javafx.beans.property.SimpleStringProperty(ip);
            this.host        = new javafx.beans.property.SimpleStringProperty(host);
            this.connectedAt = new javafx.beans.property.SimpleStringProperty(connectedAt);
            this.status      = new javafx.beans.property.SimpleStringProperty(status);
        }

        public String getIp()          { return ip.get(); }
        public String getHost()        { return host.get(); }
        public String getConnectedAt() { return connectedAt.get(); }
        public String getStatus()      { return status.get(); }

        public void setStatus(String s)      { status.set(s); }
        public void setConnectedAt(String t) { connectedAt.set(t); }
    }
}
