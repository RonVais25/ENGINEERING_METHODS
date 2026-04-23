package client;

import common.Order;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * ClientUI - JavaFX boundary for the client side.
 *
 * Screen 1 (on start): connection form - host/port + Connect.
 * Screen 2 (after connect): orders table + edit fields + Load/Update.
 *
 * This is the ENTRY POINT of the client JAR.
 */
public class ClientUI extends Application implements ClientController.OrdersListener {

    private final ClientController controller = new ClientController();

    private Stage primaryStage;

    // Connect screen
    private TextField hostField;
    private TextField portField;

    // Orders screen widgets
    private TableView<Order> ordersTable;
    private final ObservableList<Order> ordersList = FXCollections.observableArrayList();
    private TextField orderNumberField;
    private DatePicker newOrderDate;
    private TextField newVisitorsField;
    private Label statusLabel;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        controller.setListener(this);
        stage.setTitle("GoNature Client");
        stage.setOnCloseRequest(e -> controller.disconnect());
        showConnectScene();
        stage.show();
    }

    // =====================================================================
    // Scene 1 - connect
    // =====================================================================
    private void showConnectScene() {
        hostField = new TextField("localhost");
        portField = new TextField("5555");

        Button connectBtn = new Button("Connect");
        connectBtn.setOnAction(e -> doConnect());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Server host:"), 0, 0); grid.add(hostField, 1, 0);
        grid.add(new Label("Server port:"), 0, 1); grid.add(portField, 1, 1);
        grid.add(connectBtn,                1, 2);

        VBox root = new VBox(new Label("GoNature - connect to server"), grid);
        root.setSpacing(8);
        root.setPadding(new Insets(20));

        primaryStage.setScene(new Scene(root, 360, 200));
    }

    private void doConnect() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException nfe) {
            alert("Invalid port");
            return;
        }
        try {
            controller.connect(host, port);
            showOrdersScene();
        } catch (IOException ex) {
            alert("Could not connect: " + ex.getMessage());
        }
    }

    // =====================================================================
    // Scene 2 - orders
    // =====================================================================
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void showOrdersScene() {
        ordersTable = new TableView<>(ordersList);
        TableColumn<Order, Number> c1 = new TableColumn<>("Order #");
        TableColumn<Order, Date>   c2 = new TableColumn<>("Order date");
        TableColumn<Order, Number> c3 = new TableColumn<>("Visitors");
        TableColumn<Order, Number> c4 = new TableColumn<>("Conf. code");
        TableColumn<Order, Number> c5 = new TableColumn<>("Subscriber");
        TableColumn<Order, Date>   c6 = new TableColumn<>("Placed on");

        c1.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getOrderNumber()));
        c2.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getOrderDate()));
        c3.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getNumberOfVisitors()));
        c4.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getConfirmationCode()));
        c5.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getSubscriberId()));
        c6.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getDateOfPlacingOrder()));

        ordersTable.getColumns().addAll(c1, c2, c3, c4, c5, c6);
        ordersTable.setPrefHeight(260);
        ordersTable.setPlaceholder(new Label("Click 'Load orders' to fetch data"));

        // clicking a row copies its values into the edit fields
        ordersTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                orderNumberField.setText(String.valueOf(sel.getOrderNumber()));
                if (sel.getOrderDate() != null) {
                    newOrderDate.setValue(sel.getOrderDate().toLocalDate());
                }
                newVisitorsField.setText(String.valueOf(sel.getNumberOfVisitors()));
            }
        });

        Button loadBtn   = new Button("Load orders");
        Button updateBtn = new Button("Update selected");
        loadBtn.setOnAction(e -> controller.requestOrders());
        updateBtn.setOnAction(e -> submitUpdate());

        HBox actions = new HBox(10, loadBtn, updateBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        // Edit form
        orderNumberField = new TextField();
        orderNumberField.setEditable(false);
        orderNumberField.setPromptText("(pick a row)");
        newOrderDate     = new DatePicker();
        newVisitorsField = new TextField();

        GridPane edit = new GridPane();
        edit.setHgap(8);
        edit.setVgap(8);
        edit.setPadding(new Insets(10, 0, 10, 0));
        edit.add(new Label("Order #:"),         0, 0); edit.add(orderNumberField, 1, 0);
        edit.add(new Label("New order date:"),  0, 1); edit.add(newOrderDate,     1, 1);
        edit.add(new Label("New # of visitors:"), 0, 2); edit.add(newVisitorsField, 1, 2);

        statusLabel = new Label("Connected. Click 'Load orders' to begin.");

        VBox root = new VBox(10,
                new Label("Orders in the database"),
                ordersTable,
                actions,
                new Label("Update (order_date, number_of_visitors)"),
                edit,
                statusLabel
        );
        root.setPadding(new Insets(15));

        primaryStage.setScene(new Scene(root, 820, 620));
    }

    private void submitUpdate() {
        Order selected = ordersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { alert("Select a row first"); return; }

        LocalDate newDate = newOrderDate.getValue();
        if (newDate == null) { alert("Pick a date"); return; }

        int visitors;
        try {
            visitors = Integer.parseInt(newVisitorsField.getText().trim());
            if (visitors <= 0) throw new NumberFormatException();
        } catch (NumberFormatException nfe) {
            alert("Number of visitors must be a positive integer");
            return;
        }

        Order updated = new Order(
                selected.getOrderNumber(),
                Date.valueOf(newDate),
                visitors,
                selected.getConfirmationCode(),
                selected.getSubscriberId(),
                selected.getDateOfPlacingOrder()
        );
        controller.updateOrder(updated);
    }

    // =====================================================================
    // Listener callbacks (called from network thread - marshal to JavaFX!)
    // =====================================================================
    @Override
    public void onOrdersReceived(List<Order> orders) {
        Platform.runLater(() -> {
            ordersList.setAll(orders);
            statusLabel.setText("Loaded " + orders.size() + " orders.");
        });
    }

    @Override
    public void onUpdateResult(boolean success) {
        Platform.runLater(() -> {
            if (success) {
                statusLabel.setText("Update OK - reloading...");
                controller.requestOrders();
            } else {
                statusLabel.setText("Update failed.");
                alert("Update failed on the server");
            }
        });
    }

    @Override
    public void onError(String message) {
        Platform.runLater(() -> {
            statusLabel.setText("Error: " + message);
            alert(message);
        });
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            statusLabel.setText("Disconnected from server.");
            alert("Disconnected from server");
        });
    }

    // --- small helper ---
    private void alert(String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, text);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
