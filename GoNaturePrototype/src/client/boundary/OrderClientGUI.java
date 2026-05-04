package client.boundary;

import client.net.ClientConnection;
import common.dto.*;

import javax.swing.*;
import java.awt.*;

public class OrderClientGUI extends JFrame {

    private ClientConnection client;
    private JTextField orderField, dateField, visitorsField;
    private JTextArea output;

    public OrderClientGUI() {

        client = new ClientConnection("localhost", 5555);

        setTitle("Order Client");
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        orderField = new JTextField();
        dateField = new JTextField("2026-06-01");
        visitorsField = new JTextField("5");

        JButton getBtn = new JButton("Get Order");
        JButton updateBtn = new JButton("Update Order");

        output = new JTextArea();

        JPanel panel = new JPanel(new GridLayout(6,2));

        panel.add(new JLabel("Order Number"));
        panel.add(orderField);

        panel.add(new JLabel("New Date"));
        panel.add(dateField);

        panel.add(new JLabel("New Visitors"));
        panel.add(visitorsField);

        panel.add(getBtn);
        panel.add(updateBtn);

        add(panel, BorderLayout.NORTH);
        add(new JScrollPane(output), BorderLayout.CENTER);

        getBtn.addActionListener(e -> getOrder());
        updateBtn.addActionListener(e -> updateOrder());
    }

    private void getOrder() {
        int order = Integer.parseInt(orderField.getText());

        ClientRequest req = new ClientRequest(RequestType.GET_ORDER);
        req.put("orderNumber", order);

        ServerResponse res = client.sendRequest(req);

        output.append("GET:\n" + res.getMessage() + "\n");

        if (res.isSuccess()) {
            OrderDTO dto = (OrderDTO) res.getData();
            output.append("Date: " + dto.getOrderDate() + "\n");
            output.append("Visitors: " + dto.getNumberOfVisitors() + "\n\n");
        }
    }

    private void updateOrder() {
        int order = Integer.parseInt(orderField.getText());

        ClientRequest req = new ClientRequest(RequestType.UPDATE_ORDER);
        req.put("orderNumber", order);
        req.put("newDate", dateField.getText());
        req.put("newVisitors", Integer.parseInt(visitorsField.getText()));

        ServerResponse res = client.sendRequest(req);

        output.append("UPDATE:\n" + res.getMessage() + "\n\n");
    }

    public static void main(String[] args) {
        new OrderClientGUI().setVisible(true);
    }
}