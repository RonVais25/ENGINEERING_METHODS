package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.EventOp;
import common.dto.OrderDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Update-Order screen: a two-step flow (find → edit) with a step indicator
 * at the top. The result panel and activity log on the right mirror the
 * Get-Order screen.
 *
 * <p>This controller participates in the realtime push channel via
 * {@link BaseController}: as soon as a search succeeds, it subscribes to
 * {@code ("order", orderNumber)} so an edit committed by another client
 * refreshes the local fields without polling. The subscription is
 * dropped on back-to-step-0, on re-search, and on screen navigation away.
 */
public class UpdateOrderController extends BaseController {

    @FXML private VBox      leftPanel;
    @FXML private HBox      stepIndicatorSlot;
    @FXML private VBox      step0;
    @FXML private TextField s0input;
    @FXML private Button    findBtn;
    @FXML private Label     s0toast;
    @FXML private VBox      step1;
    @FXML private Label     s1title;
    @FXML private DatePicker datePicker;
    @FXML private Spinner<Integer> visitorsSpinner;
    @FXML private Button    backBtn;
    @FXML private Button    applyBtn;
    @FXML private Label     s1toast;
    @FXML private VBox      rightPanel;

    private VBox resultPanel;
    private VBox logBox;
    private int  currentOrderNumber = -1;

    public UpdateOrderController(NetworkService network, Session session) {
        super(network);
    }

    @FXML
    private void initialize() {
        visitorsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 5));

        // Initial step indicator
        setStepIndicator(0);

        // Right panel: result + log
        resultPanel = Widgets.buildResultPanel();
        VBox.setVgrow(resultPanel, Priority.ALWAYS);
        logBox = Widgets.buildLogBox();

        Label logLabel = new Label("ACTIVITY LOG");
        logLabel.getStyleClass().add("section-label-muted");
        VBox logSection = new VBox(6, logLabel, Widgets.wrapLog(logBox));

        rightPanel.getChildren().addAll(resultPanel, logSection);

        s0input.setOnAction(e -> onFind());
    }

    @FXML
    private void onFind() {
        String raw = s0input.getText() == null ? "" : s0input.getText().trim();
        if (raw.isEmpty()) { Widgets.showToast(s0toast, false, "Please enter an order number"); return; }
        int n;
        try { n = Integer.parseInt(raw); }
        catch (NumberFormatException ex) {
            Widgets.showToast(s0toast, false, "Enter a valid order number");
            return;
        }

        findBtn.setText("Searching…");
        findBtn.setDisable(true);

        network.getOrder(n).thenAccept(res -> {
            findBtn.setText("⊕  Find Order");
            findBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(s0toast, false, res.getMessage());
                Widgets.addLog(logBox, false, "Error: " + res.getMessage());
                return;
            }
            OrderDTO dto = (OrderDTO) res.getData();
            currentOrderNumber = n;
            Widgets.populateResultPanel(resultPanel, dto);
            Widgets.addLog(logBox, true, "Loaded order #" + n);
            s1title.setText("Edit Order #" + n);
            try { datePicker.setValue(LocalDate.parse(dto.getOrderDate())); }
            catch (Exception ignored) { datePicker.setValue(LocalDate.now().plusDays(7)); }
            visitorsSpinner.getValueFactory().setValue(dto.getNumberOfVisitors());

            setStepIndicator(1);
            step0.setVisible(false); step0.setManaged(false);
            step1.setVisible(true);  step1.setManaged(true);

            // Drop any previous order's subscription before attaching a new
            // one — the user can re-search a different id without navigating
            // away, so onHide() alone wouldn't catch this case.
            unsubscribeAll();
            subscribe("order", n, ev -> {
                if (ev.getOp() == EventOp.UPDATED && ev.getPayload() instanceof OrderDTO updated) {
                    applyOrderToFields(updated);
                }
            });
        });
    }

    @FXML
    private void onBack() {
        unsubscribeAll();
        setStepIndicator(0);
        step0.setVisible(true);  step0.setManaged(true);
        step1.setVisible(false); step1.setManaged(false);
        Widgets.clearResultPanel(resultPanel);
        currentOrderNumber = -1;
    }

    @FXML
    private void onApply() {
        if (datePicker.getValue() == null) { Widgets.showToast(s1toast, false, "Please select a date"); return; }

        // Commit any text the user typed into the editable spinner.
        try {
            int typed = Integer.parseInt(visitorsSpinner.getEditor().getText().trim());
            if (typed >= 1 && typed <= 50) visitorsSpinner.getValueFactory().setValue(typed);
        } catch (NumberFormatException ignored) {}

        String newDate  = datePicker.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE);
        int    newVisit = visitorsSpinner.getValue();
        int    n        = currentOrderNumber;

        applyBtn.setText("Updating…");
        applyBtn.setDisable(true);

        network.updateOrder(n, newDate, newVisit).thenAccept(res -> {
            applyBtn.setText("✎  Apply Changes");
            applyBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(s1toast, false, res.getMessage());
                Widgets.addLog(logBox, false, "Update failed: " + res.getMessage());
                return;
            }
            Widgets.showToast(s1toast, true, "Order updated successfully");
            Widgets.addLog(logBox, true, "Updated order #" + n);
            setStepIndicator(2);

            // The server's UPDATED event will land here too (we're subscribed
            // to our own change), so the result panel will refresh via
            // applyOrderToFields. The explicit re-fetch below is kept as a
            // belt-and-suspenders in case the event is lost in flight.
            network.getOrder(n).thenAccept(r2 -> {
                if (r2.isSuccess()) Widgets.populateResultPanel(resultPanel, (OrderDTO) r2.getData());
            });
        });
    }

    /**
     * Pushes a server-confirmed order snapshot into the visible form fields
     * and the result panel. Called from the EventBus callback registered in
     * {@link #onFind}.
     */
    private void applyOrderToFields(OrderDTO updated) {
        // Defensive: if an event somehow fires before @FXML injection
        // completes (shouldn't happen via the Navigator path), bail out.
        if (datePicker == null) return;

        try { datePicker.setValue(LocalDate.parse(updated.getOrderDate())); }
        catch (Exception ignored) {}
        visitorsSpinner.getValueFactory().setValue(updated.getNumberOfVisitors());
        Widgets.populateResultPanel(resultPanel, updated);
        Widgets.addLog(logBox, true, "Order updated remotely");
        System.out.println("[ui] applied remote update for order #" + updated.getOrderNumber());
    }

    private void setStepIndicator(int active) {
        HBox indicator = Widgets.buildStepIndicator(active);
        stepIndicatorSlot.getChildren().setAll(indicator.getChildren());
    }
}
