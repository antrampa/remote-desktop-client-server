import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;

/**
 * Remote Desktop Server with GUI
 * - Shows local IP & port
 * - Start / Stop sharing
 * - Captures screen & sends to client
 * - Receives mouse + keyboard events and simulates them
 */
public class ServerGUI extends JFrame {
    private JTextField portField = new JTextField("5000", 6);
    private JButton startButton = new JButton("Start");
    private JLabel statusLabel = new JLabel("Status: Stopped");
    private JLabel ipLabel = new JLabel();
    private ServerWorker serverWorker;

    public ServerGUI() {
        super("Remote Desktop Server");

        setLayout(new FlowLayout());
        add(new JLabel("Local IP:"));
        ipLabel.setText(getLocalIPAddress());
        add(ipLabel);

        add(new JLabel("Port:"));
        add(portField);

        add(startButton);
        add(statusLabel);

        startButton.addActionListener(e -> {
            if (serverWorker == null || serverWorker.isDone()) {
                startServer();
            } else {
                stopServer();
            }
        });

        setSize(400, 120);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port must be a number.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        startButton.setEnabled(false);
        startButton.setText("Starting...");
        statusLabel.setText("Status: Starting...");

        serverWorker = new ServerWorker(port);
        serverWorker.execute();
    }

    private void stopServer() {
        startButton.setEnabled(false);
        startButton.setText("Stopping...");
        statusLabel.setText("Status: Stopping...");
        if (serverWorker != null) {
            serverWorker.stopServer();
        }
    }

    private static String getLocalIPAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private class ServerWorker extends SwingWorker<Void, Void> {
        private int port;
        private volatile boolean running = true;
        private ServerSocket serverSocket;
        private Socket client;
        private DataOutputStream dos;
        private DataInputStream dis;
        private Robot robot;

        ServerWorker(int port) {
            this.port = port;
        }

        @Override
        protected Void doInBackground() {
            try {
                robot = new Robot();
                serverSocket = new ServerSocket(port);
                SwingUtilities.invokeLater(() -> {
                    startButton.setText("Stop");
                    startButton.setEnabled(true);
                    statusLabel.setText("Status: Waiting for client...");
                });

                client = serverSocket.accept();
                dos = new DataOutputStream(client.getOutputStream());
                dis = new DataInputStream(client.getInputStream());

                SwingUtilities.invokeLater(() -> statusLabel.setText("Status: Connected - Streaming..."));

                Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

                // Separate thread for handling incoming control events
                Thread inputThread = new Thread(this::handleInputEvents);
                inputThread.start();

                while (running && !client.isClosed()) {
                    BufferedImage screen = robot.createScreenCapture(screenRect);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(screen, "jpg", baos);
                    byte[] bytes = baos.toByteArray();

                    dos.writeInt(bytes.length);
                    dos.write(bytes);
                    dos.flush();

                    Thread.sleep(100); // adjust FPS
                }

            } catch (Exception e) {
                if (running)
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(ServerGUI.this, "Error: " + e.getMessage(),
                                    "Server Error", JOptionPane.ERROR_MESSAGE));
            } finally {
                stopServer();
            }
            return null;
        }

        private void handleInputEvents() {
            try {
                while (running && !client.isClosed()) {
                    // Simple event protocol:
                    // First byte: event type (1=mouse move, 2=mouse click, 3=key press, 4=key release)
                    // Then ints/doubles as needed.
                    int eventType = dis.read();
                    if (eventType == -1) break;

                    switch (eventType) {
                        case 1: // Mouse move
                            int x = dis.readInt();
                            int y = dis.readInt();
                            robot.mouseMove(x, y);
                            break;
                        case 2: // Mouse click
                            int button = dis.readInt(); // 1=left, 2=right
                            robot.mousePress(InputEvent.getMaskForButton(button));
                            robot.mouseRelease(InputEvent.getMaskForButton(button));
                            break;
                        case 3: // Key press
                            int keyCode = dis.readInt();
                            robot.keyPress(keyCode);
                            break;
                        case 4: // Key release
                            int keyCode2 = dis.readInt();
                            robot.keyRelease(keyCode2);
                            break;
                        default:
                            break;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        void stopServer() {
            running = false;
            try {
                if (client != null) client.close();
                if (serverSocket != null) serverSocket.close();
                if (dis != null) dis.close();
                if (dos != null) dos.close();
            } catch (IOException ignored) {
            }

            SwingUtilities.invokeLater(() -> {
                startButton.setText("Start");
                startButton.setEnabled(true);
                statusLabel.setText("Status: Stopped");
            });
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ServerGUI::new);
    }
}
