package server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * ServerUI - JavaFX boundary for the server side.
 *
 * Displays:
 *  - DB + port configuration
 *  - Start / Stop buttons
 *  - Table of connected clients (IP, Host, Status)
 *  - Log area
 *
 * This is the ENTRY POINT of the server JAR.
 */
public class ServerUI extends Application implements ServerObserver {

    private TextField portField;
    private TextField dbUrlField;
    private TextField dbUserField;
    private PasswordField dbPassField;
    private Button startBtn;
    private Button stopBtn;
    private TextArea logArea;

    private TableView<ClientRow> clientsTable;
    private final ObservableList<ClientRow> clients = FXCollections.observableArrayList();

    private GoNatureServer server;
    private DBController db;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("GoNature Server");

        // ---- Config grid ----
        GridPane config = new GridPane();
        config.setHgap(8);
        config.setVgap(8);
        config.setPadding(new Insets(10));

        portField   = new TextField("5555");
        dbUrlField  = new TextField("jdbc:mysql://localhost:3306/gonature?serverTimezone=UTC");
        dbUserField = new TextField("root");
        dbPassField = new PasswordField();
        dbPassField.setPromptText("MySQL password");

        config.add(new Label("Port:"),        0, 0); config.add(portField,   1, 0);
        config.add(new Label("DB URL:"),      0, 1); config.add(dbUrlField,  1, 1);
        config.add(new Label("DB User:"),     0, 2); config.add(dbUserField, 1, 2);
        config.add(new Label("DB Password:"), 0, 3); config.add(dbPassField, 1, 3);

        dbUrlField.setPrefColumnCount(40);

        startBtn = new Button("Start server");
        stopBtn  = new Button("Stop server");
        stopBtn.setDisable(true);
        startBtn.setOnAction(e -> startServer());
        stopBtn.setOnAction(e -> stopServer());

        HBox buttons = new HBox(10, startBtn, stopBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(0, 10, 10, 10));

        // ---- Clients table ----
        clientsTable = new TableView<>(clients);
        TableColumn<ClientRow, String> ipCol     = new TableColumn<>("IP address");
        TableColumn<ClientRow, String> hostCol   = new TableColumn<>("Host name");
        TableColumn<ClientRow, String> statusCol = new TableColumn<>("Status");
        ipCol.setCellValueFactory(new PropertyValueFactory<>("ip"));
        hostCol.setCellValueFactory(new PropertyValueFactory<>("host"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        ipCol.setPrefWidth(160);
        hostCol.setPrefWidth(220);
        statusCol.setPrefWidth(120);
        clientsTable.getColumns().addAll(ipCol, hostCol, statusCol);
        clientsTable.setPrefHeight(200);
        clientsTable.setPlaceholder(new Label("No clients connected yet"));

        // ---- Log area ----
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(12);

        VBox root = new VBox(8,
                new Label("Connection settings"),
                config,
                buttons,
                new Label("Connected clients"),
                clientsTable,
                new Label("Server log"),
                logArea
        );
        root.setPadding(new Insets(10));

        stage.setScene(new Scene(root, 720, 680));
        stage.setOnCloseRequest(e -> stopServer());
        stage.show();
    }

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException nfe) {
            alert("Invalid port number");
            return;
        }

        db = new DBController(
                dbUrlField.getText().trim(),
                dbUserField.getText().trim(),
                dbPassField.getText());

        if (!db.connect()) {
            alert("Failed to connect to the database.\nCheck the URL / user / password.");
            return;
        }

        server = new GoNatureServer(port, db, this);
        try {
            server.listen();
            startBtn.setDisable(true);
            stopBtn.setDisable(false);
            portField.setDisable(true);
            dbUrlField.setDisable(true);
            dbUserField.setDisable(true);
            dbPassField.setDisable(true);
        } catch (IOException e) {
            alert("Failed to start server: " + e.getMessage());
            db.disconnect();
        }
    }

    private void stopServer() {
        try {
            if (server != null && server.isListening()) {
                server.stopListening();
                server.close();
            }
        } catch (IOException e) {
            log("!! error stopping server: " + e.getMessage());
        }
        if (db != null) db.disconnect();
        startBtn.setDisable(false);
        stopBtn.setDisable(true);
        portField.setDisable(false);
        dbUrlField.setDisable(false);
        dbUserField.setDisable(false);
        dbPassField.setDisable(false);
    }

    // ---- helpers called by GoNatureServer ----

    @Override
    public void addClient(String ip, String host, String status) {
        Platform.runLater(() -> {
            // Refresh if same IP reconnects
            clients.removeIf(c -> c.getIp().equals(ip));
            clients.add(new ClientRow(ip, host, status));
        });
    }

    @Override
    public void updateClientStatus(String ip, String status) {
        Platform.runLater(() -> {
            for (ClientRow c : clients) {
                if (c.getIp().equals(ip)) {
                    c.setStatus(status);
                    clientsTable.refresh();
                    return;
                }
            }
        });
    }

    @Override
    public void log(String line) {
        String stamped = "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + "] " + line + "\n";
        Platform.runLater(() -> logArea.appendText(stamped));
    }

    private void alert(String text) {
        Alert a = new Alert(Alert.AlertType.ERROR, text);
        a.setHeaderText(null);
        a.showAndWait();
    }

    /**
     * JavaFX-bindable row for the clients table.
     */
    public static class ClientRow {
        private final StringProperty ip;
        private final StringProperty host;
        private final StringProperty status;

        public ClientRow(String ip, String host, String status) {
            this.ip     = new SimpleStringProperty(ip);
            this.host   = new SimpleStringProperty(host);
            this.status = new SimpleStringProperty(status);
        }

        public String getIp()          { return ip.get(); }
        public String getHost()        { return host.get(); }
        public String getStatus()      { return status.get(); }
        public void setStatus(String s){ status.set(s); }

        public StringProperty ipProperty()     { return ip; }
        public StringProperty hostProperty()   { return host; }
        public StringProperty statusProperty() { return status; }
    }
}
