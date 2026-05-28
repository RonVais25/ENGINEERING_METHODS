package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.OrderDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Get-Order screen: enter an order number, see its details and an activity
 * log on the right. Quick-access buttons hard-code three example IDs.
 */
public class GetOrderController {

    @FXML private TextField orderInput;
    @FXML private Button    getBtn;
    @FXML private Label     toast;
    @FXML private HBox      quickBtns;
    @FXML private VBox      rightPanel;

    private final NetworkService network;

    private VBox resultPanel;
    private VBox logBox;

    public GetOrderController(NetworkService network, Session session) {
        this.network = network;
    }

    @FXML
    private void initialize() {
        // Quick access buttons
        for (String n : new String[]{"#1023", "#1055", "#1087"}) {
            Button qb = new Button(n);
            qb.getStyleClass().add("btn-secondary");
            qb.setOnAction(e -> {
                orderInput.setText(n.substring(1));
                onGetOrder();
            });
            quickBtns.getChildren().add(qb);
        }

        // Right panel: result + log
        resultPanel = Widgets.buildResultPanel();
        VBox.setVgrow(resultPanel, Priority.ALWAYS);

        logBox = Widgets.buildLogBox();
        Label logLabel = new Label("ACTIVITY LOG");
        logLabel.getStyleClass().add("section-label-muted");
        VBox logSection = new VBox(6, logLabel, Widgets.wrapLog(logBox));

        rightPanel.getChildren().addAll(resultPanel, logSection);

        // Enter in the input triggers the same action as the button
        orderInput.setOnAction(e -> onGetOrder());
    }

    @FXML
    private void onGetOrder() {
        String raw = orderInput.getText() == null ? "" : orderInput.getText().trim();
        if (raw.isEmpty()) {
            Widgets.showToast(toast, false, "Please enter an order number");
            return;
        }
        int n;
        try { n = Integer.parseInt(raw); }
        catch (NumberFormatException ex) {
            Widgets.showToast(toast, false, "Enter a valid order number");
            return;
        }

        getBtn.setText("Searching…");
        getBtn.setDisable(true);

        network.getOrder(n).thenAccept(res -> {
            getBtn.setText("⊕  Get Order");
            getBtn.setDisable(false);
            if (res.isSuccess()) {
                Widgets.populateResultPanel(resultPanel, (OrderDTO) res.getData());
                Widgets.addLog(logBox, true, "Fetched order #" + n);
            } else {
                Widgets.showToast(toast, false, res.getMessage());
                Widgets.clearResultPanel(resultPanel);
                Widgets.addLog(logBox, false, "Error: " + res.getMessage());
            }
        });
    }
}
