package client.boundary.staff;

import client.net.GoNatureClient;
import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerResponse;
import common.dto.ReservationDTO;

import javax.swing.*;
import java.awt.*;

public class StaffGUI extends JFrame {

    private GoNatureClient client;

    private JTextField reservationIdField;
    private JTextField newDateField;
    private JTextField newVisitorsField;

    private JTextArea outputArea;

    public StaffGUI() {
        client = new GoNatureClient("localhost",555);

        setTitle("GoNature - Staff / Manager");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        reservationIdField = new JTextField();
        newDateField = new JTextField("2025-06-01");
        newVisitorsField = new JTextField("5");

        JButton getButton = new JButton("Get Reservation");
        JButton updateButton = new JButton("Update Reservation");

        JButton enterButton = new JButton("Register Entry");
        JButton exitButton = new JButton("Register Exit");
        JButton reportButton = new JButton("Generate Visits Report");

        outputArea = new JTextArea();
        outputArea.setEditable(false);

        JPanel topPanel = new JPanel(new GridLayout(6, 2, 10, 10));

        topPanel.add(new JLabel("Reservation ID:"));
        topPanel.add(reservationIdField);

        topPanel.add(new JLabel("New Visit Date:"));
        topPanel.add(newDateField);

        topPanel.add(new JLabel("New Number of Visitors:"));
        topPanel.add(newVisitorsField);

        topPanel.add(getButton);
        topPanel.add(updateButton);

        topPanel.add(enterButton);
        topPanel.add(exitButton);

        topPanel.add(reportButton);
        topPanel.add(new JLabel(""));

        getButton.addActionListener(e -> getReservation());
        updateButton.addActionListener(e -> updateReservation());

        enterButton.addActionListener(e -> registerEntry());
        exitButton.addActionListener(e -> registerExit());
        reportButton.addActionListener(e -> generateReport());

        setLayout(new BorderLayout(10, 10));
        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(outputArea), BorderLayout.CENTER);
    }

    private void getReservation() {
        String reservationId = reservationIdField.getText();

        if (reservationId.isEmpty()) {
            outputArea.append("Please enter Reservation ID.\n\n");
            return;
        }

        ClientRequest request = new ClientRequest(RequestType.GET_RESERVATION);
        request.put("reservationId", reservationId);

        ServerResponse response = client.sendRequest(request);

        outputArea.append("=== GET RESERVATION ===\n");
        outputArea.append("Success: " + response.isSuccess() + "\n");
        outputArea.append("Message: " + response.getMessage() + "\n");

        if (response.isSuccess() && response.getData() != null) {
            ReservationDTO dto = (ReservationDTO) response.getData();

            outputArea.append("Reservation ID: " + dto.getReservationId() + "\n");
            outputArea.append("Visit Date: " + dto.getVisitDate() + "\n");
            outputArea.append("Arrival Time: " + dto.getArrivalTime() + "\n");
            outputArea.append("Visitors: " + dto.getNumberOfVisitors() + "\n");
            outputArea.append("Status: " + dto.getStatus() + "\n");
        }

        outputArea.append("\n");
    }

    private void updateReservation() {
        String reservationId = reservationIdField.getText();

        if (reservationId.isEmpty()) {
            outputArea.append("Please enter Reservation ID.\n\n");
            return;
        }

        ClientRequest request = new ClientRequest(RequestType.UPDATE_RESERVATION);
        request.put("reservationId", reservationId);
        request.put("newDate", newDateField.getText());
        request.put("newVisitors", Integer.parseInt(newVisitorsField.getText()));

        ServerResponse response = client.sendRequest(request);

        outputArea.append("=== UPDATE RESERVATION ===\n");
        outputArea.append("Success: " + response.isSuccess() + "\n");
        outputArea.append("Message: " + response.getMessage() + "\n\n");
    }

    private void registerEntry() {
        String reservationId = reservationIdField.getText();

        if (reservationId.isEmpty()) {
            outputArea.append("Please enter Reservation ID.\n\n");
            return;
        }

        ClientRequest request = new ClientRequest(RequestType.ENTER_PARK);
        request.put("reservationId", reservationId);

        ServerResponse response = client.sendRequest(request);

        outputArea.append("=== ENTRY ===\n");
        outputArea.append("Success: " + response.isSuccess() + "\n");
        outputArea.append("Message: " + response.getMessage() + "\n\n");
    }

    private void registerExit() {
        String reservationId = reservationIdField.getText();

        if (reservationId.isEmpty()) {
            outputArea.append("Please enter Reservation ID.\n\n");
            return;
        }

        ClientRequest request = new ClientRequest(RequestType.EXIT_PARK);
        request.put("reservationId", reservationId);

        ServerResponse response = client.sendRequest(request);

        outputArea.append("=== EXIT ===\n");
        outputArea.append("Success: " + response.isSuccess() + "\n");
        outputArea.append("Message: " + response.getMessage() + "\n\n");
    }

    private void generateReport() {
        ClientRequest request = new ClientRequest(RequestType.GENERATE_VISITS_REPORT);

        ServerResponse response = client.sendRequest(request);

        outputArea.append("=== REPORT ===\n");
        outputArea.append("Success: " + response.isSuccess() + "\n");
        outputArea.append("Message: " + response.getMessage() + "\n");
        outputArea.append("Check server console for full report.\n\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StaffGUI().setVisible(true));
    }
}