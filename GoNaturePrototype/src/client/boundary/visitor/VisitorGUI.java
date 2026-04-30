package client.boundary.visitor;

import client.net.GoNatureClient;
import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerResponse;

import javax.swing.*;
import java.awt.*;

public class VisitorGUI extends JFrame {

    private GoNatureClient client;

    private JTextField parkField;
    private JTextField dateField;
    private JTextField timeField;
    private JTextField visitorsField;
    private JTextArea outputArea;

    public VisitorGUI() {
        client = new GoNatureClient("localhost",555);

        setTitle("GoNature - Visitor");
        setSize(500, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        parkField = new JTextField("Carmel Forest");
        dateField = new JTextField("2025-05-01");
        timeField = new JTextField("10:00:00");
        visitorsField = new JTextField("3");

        JButton reserveButton = new JButton("Create Reservation");

        outputArea = new JTextArea();
        outputArea.setEditable(false);

        JPanel formPanel = new JPanel(new GridLayout(5, 2, 10, 10));

        formPanel.add(new JLabel("Park Name:"));
        formPanel.add(parkField);

        formPanel.add(new JLabel("Visit Date:"));
        formPanel.add(dateField);

        formPanel.add(new JLabel("Arrival Time:"));
        formPanel.add(timeField);

        formPanel.add(new JLabel("Number of Visitors:"));
        formPanel.add(visitorsField);

        formPanel.add(new JLabel(""));
        formPanel.add(reserveButton);

        reserveButton.addActionListener(e -> createReservation());

        setLayout(new BorderLayout(10, 10));
        add(formPanel, BorderLayout.NORTH);
        add(new JScrollPane(outputArea), BorderLayout.CENTER);
    }

    private void createReservation() {
        try {
            ClientRequest request = new ClientRequest(RequestType.CREATE_RESERVATION);

            request.put("parkName", parkField.getText());
            request.put("date", dateField.getText());
            request.put("time", timeField.getText());
            request.put("visitorsCount", Integer.parseInt(visitorsField.getText()));

            ServerResponse response = client.sendRequest(request);

            outputArea.append("Create Reservation Result:\n");
            outputArea.append("Success: " + response.isSuccess() + "\n");
            outputArea.append("Message: " + response.getMessage() + "\n\n");

        } catch (Exception ex) {
            outputArea.append("Error: " + ex.getMessage() + "\n\n");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VisitorGUI().setVisible(true));
    }
}