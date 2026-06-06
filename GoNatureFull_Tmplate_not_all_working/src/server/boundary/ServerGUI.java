package server.boundary;

import server.net.GoNatureServer;
import server.util.ServerLog;

import javax.swing.*;
import java.awt.*;

public class ServerGUI extends JFrame {
    private JTextField portField = new JTextField("5555");
    private JTextArea logArea = new JTextArea();

    public ServerGUI() {
        setTitle("GoNature Server");
        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JButton startButton = new JButton("Start Server");
        JPanel top = new JPanel(new GridLayout(1, 3, 8, 8));
        top.add(new JLabel("Port:")); top.add(portField); top.add(startButton);
        logArea.setEditable(false);
        add(top, BorderLayout.NORTH); add(new JScrollPane(logArea), BorderLayout.CENTER);
        ServerLog.setListener(msg -> SwingUtilities.invokeLater(() -> logArea.append(msg + "\n")));
        startButton.addActionListener(e -> new Thread(() -> new GoNatureServer(Integer.parseInt(portField.getText())).start()).start());
    }
    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new ServerGUI().setVisible(true)); }
}
