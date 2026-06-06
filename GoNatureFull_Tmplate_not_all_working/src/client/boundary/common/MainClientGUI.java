package client.boundary.common;

import client.app.Session;
import common.dto.*;

import javax.swing.*;
import java.awt.*;

public class MainClientGUI extends JFrame {
    private JTextField hostField = new JTextField("localhost");
    private JTextField portField = new JTextField("5555");
    private JTextArea output = new JTextArea();

    private JTextField visitorIdField = new JTextField("1");
    private JTextField parkIdField = new JTextField("1");
    private JTextField dateField = new JTextField("2026-06-01");
    private JTextField timeField = new JTextField("10:00:00");
    private JTextField countField = new JTextField("3");
    private JTextField reservationIdField = new JTextField("1");
    private JTextField startDateField = new JTextField("2026-01-01");
    private JTextField endDateField = new JTextField("2026-12-31");

    public MainClientGUI() {
        setTitle("GoNature Client");
        setSize(850, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        output.setEditable(false);
        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildTabs(), BorderLayout.CENTER);
        add(new JScrollPane(output), BorderLayout.SOUTH);
    }

    private JPanel buildTopPanel() {
        JPanel p = new JPanel(new GridLayout(2, 4, 8, 8));
        JButton connect = new JButton("Test Connection");
        p.add(new JLabel("Host:")); p.add(hostField); p.add(new JLabel("Port:")); p.add(portField); p.add(new JLabel()); p.add(connect);
        connect.addActionListener(e -> { Session.NETWORK.connect(hostField.getText(), Integer.parseInt(portField.getText())); sendAndPrint(new ClientRequest(RequestType.PING)); });
        return p;
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Reservation", reservationPanel());
        tabs.add("Entrance", entrancePanel());
        tabs.add("Reports", reportsPanel());
        return tabs;
    }

    private JPanel reservationPanel() {
        JPanel p = new JPanel(new GridLayout(8, 2, 8, 8));
        JButton create = new JButton("Create Reservation");
        JButton get = new JButton("Get Reservation");
        JButton update = new JButton("Update Reservation");
        JButton cancel = new JButton("Cancel Reservation");
        p.add(new JLabel("Visitor ID:")); p.add(visitorIdField);
        p.add(new JLabel("Park ID:")); p.add(parkIdField);
        p.add(new JLabel("Visit Date:")); p.add(dateField);
        p.add(new JLabel("Arrival Time:")); p.add(timeField);
        p.add(new JLabel("Visitors:")); p.add(countField);
        p.add(new JLabel("Reservation ID:")); p.add(reservationIdField);
        p.add(create); p.add(get); p.add(update); p.add(cancel);
        create.addActionListener(e -> createReservation());
        get.addActionListener(e -> requestByReservationId(RequestType.GET_RESERVATION));
        update.addActionListener(e -> updateReservation());
        cancel.addActionListener(e -> requestByReservationId(RequestType.CANCEL_RESERVATION));
        return p;
    }

    private JPanel entrancePanel() {
        JPanel p = new JPanel(new GridLayout(3, 2, 8, 8));
        JButton enter = new JButton("Enter Park"); JButton exit = new JButton("Exit Park");
        p.add(new JLabel("Reservation ID:")); p.add(reservationIdField); p.add(enter); p.add(exit);
        enter.addActionListener(e -> requestByReservationId(RequestType.ENTER_PARK));
        exit.addActionListener(e -> requestByReservationId(RequestType.EXIT_PARK));
        return p;
    }

    private JPanel reportsPanel() {
        JPanel p = new JPanel(new GridLayout(4, 2, 8, 8));
        JButton visits = new JButton("Visits Report");
        p.add(new JLabel("Start Date:")); p.add(startDateField);
        p.add(new JLabel("End Date:")); p.add(endDateField);
        p.add(visits); p.add(new JLabel());
        visits.addActionListener(e -> generateReport(RequestType.GENERATE_VISITS_REPORT));
        return p;
    }

    private void createReservation() {
        ClientRequest req = new ClientRequest(RequestType.CREATE_RESERVATION);
        req.put("visitorId", Integer.parseInt(visitorIdField.getText()));
        req.put("parkId", Integer.parseInt(parkIdField.getText()));
        req.put("date", dateField.getText());
        req.put("time", timeField.getText());
        req.put("numberOfVisitors", Integer.parseInt(countField.getText()));
        req.put("visitorType", "INDIVIDUAL_PREBOOKED");
        ServerResponse res = sendAndPrint(req);
        if (res.isSuccess() && res.getData() instanceof ReservationDTO) reservationIdField.setText(String.valueOf(((ReservationDTO) res.getData()).getReservationId()));
    }

    private void updateReservation() {
        ClientRequest req = new ClientRequest(RequestType.UPDATE_RESERVATION);
        req.put("reservationId", Integer.parseInt(reservationIdField.getText()));
        req.put("date", dateField.getText());
        req.put("numberOfVisitors", Integer.parseInt(countField.getText()));
        sendAndPrint(req);
    }

    private void requestByReservationId(RequestType type) {
        ClientRequest req = new ClientRequest(type);
        req.put("reservationId", Integer.parseInt(reservationIdField.getText()));
        sendAndPrint(req);
    }

    private void generateReport(RequestType type) {
        ClientRequest req = new ClientRequest(type);
        req.put("startDate", startDateField.getText());
        req.put("endDate", endDateField.getText());
        sendAndPrint(req);
    }

    private ServerResponse sendAndPrint(ClientRequest req) {
        ServerResponse res = Session.NETWORK.send(req);
        output.append("\n" + req.getType() + " -> " + res.getMessage() + "\n");
        if (res.getData() != null) output.append(res.getData().toString() + "\n");
        return res;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainClientGUI().setVisible(true));
    }
}
