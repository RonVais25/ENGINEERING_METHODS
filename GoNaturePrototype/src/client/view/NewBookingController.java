package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.OrderDTO;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;

/**
 * Booking form: visit date + visitor count. On success replaces the form with
 * a confirmation panel showing the new order number.
 */
public class NewBookingController {

    @FXML private StackPane outer;
    @FXML private VBox       card;
    @FXML private DatePicker datePicker;
    @FXML private TextField  visitField;
    @FXML private Button     confirmBtn;
    @FXML private Label      toast;

    private final NetworkService network;
    private final Session session;

    public NewBookingController(NetworkService network, Session session) {
        this.network = network;
        this.session = session;
    }

    @FXML
    private void onConfirm() {
        if (datePicker.getValue() == null || visitField.getText().trim().isEmpty()) {
            Widgets.showToast(toast, false, "Please fill in all fields");
            return;
        }
        int visitors;
        try {
            visitors = Integer.parseInt(visitField.getText().trim());
            if (visitors < 1 || visitors > 50) {
                Widgets.showToast(toast, false, "Visitors must be between 1 and 50");
                return;
            }
        } catch (NumberFormatException ex) {
            Widgets.showToast(toast, false, "Enter a valid number of visitors");
            return;
        }

        String visitDate = datePicker.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE);
        confirmBtn.setText("Submitting…");
        confirmBtn.setDisable(true);

        network.insertOrder(visitDate, visitors, session.getSubscriberId()).thenAccept(res -> {
            if (!res.isSuccess()) {
                confirmBtn.setText("+  Confirm Booking");
                confirmBtn.setDisable(false);
                Widgets.showToast(toast, false, res.getMessage());
                return;
            }
            showSuccessScreen((OrderDTO) res.getData(), visitors);
        });
    }

    private void showSuccessScreen(OrderDTO created, int visitors) {
        String dateStr = datePicker.getValue().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));

        Label check = new Label("✓");
        check.getStyleClass().add("success-check");
        StackPane checkCircle = new StackPane(check);
        checkCircle.getStyleClass().add("success-check-circle");

        Label title = new Label("Booking Confirmed!");
        title.getStyleClass().add("success-title");

        Label msg = new Label(
            "Order #" + created.getOrderNumber() + " saved.\n" +
            "Visit date: " + dateStr + " · " + visitors + " visitors\n" +
            "Confirmation code: " + created.getConfirmationCode());
        msg.getStyleClass().add("success-msg");
        msg.setWrapText(true);
        msg.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox success = new VBox(16, checkCircle, title, msg);
        success.setAlignment(Pos.CENTER);
        success.setMaxWidth(420);

        outer.getChildren().setAll(success);
    }
}
