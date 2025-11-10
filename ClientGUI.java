import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;

/**
 * Simple Remote Desktop Client with GUI.
 * - Enter server IP and port
 * - Click Connect to start receiving frames
 * - Click Disconnect to stop
 */
public class ClientGUI extends JFrame {
    private final JTextField ipField = new JTextField("127.0.0.1", 15);
    private final JTextField portField = new JTextField("5000", 6);
    private final JButton connectButton = new JButton("Connect");
    private final JLabel imageLabel = new JLabel();
    private final JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    // Networking fields
    private volatile Socket socket;
    private volatile DataInputStream dis;
    private ImageReceiverWorker receiverWorker;

    public ClientGUI() {
        super("Remote Desktop Viewer - Client");

        // Top controls
        topPanel.add(new JLabel("Server IP:"));
        topPanel.add(ipField);
        topPanel.add(new JLabel("Port:"));
        topPanel.add(portField);
        topPanel.add(connectButton);

        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);
        imageLabel.setBackground(Color.BLACK);
        imageLabel.setOpaque(true);

        // Layout
        this.setLayout(new BorderLayout());
        this.add(topPanel, BorderLayout.NORTH);
        this.add(new JScrollPane(imageLabel), BorderLayout.CENTER);

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(1000, 700);
        this.setLocationRelativeTo(null);

        // Button action
        connectButton.addActionListener(e -> {
            if (receiverWorker == null || receiverWorker.isDone()) {
                startConnection();
            } else {
                stopConnection();
            }
        });

        // Graceful shutdown
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                stopConnection();
            }
        });
    }

    private void startConnection() {
        String host = ipField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port must be a number", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        connectButton.setEnabled(false);
        connectButton.setText("Connecting...");
        receiverWorker = new ImageReceiverWorker(host, port);
        receiverWorker.execute();
    }

    private void stopConnection() {
        connectButton.setEnabled(false);
        connectButton.setText("Disconnecting...");
        // Close resources and cancel worker
        try {
            if (receiverWorker != null) receiverWorker.cancel(true);
            if (dis != null) dis.close();
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        } finally {
            socket = null;
            dis = null;
            SwingUtilities.invokeLater(() -> {
                connectButton.setText("Connect");
                connectButton.setEnabled(true);
            });
        }
    }

    private class ImageReceiverWorker extends SwingWorker<Void, BufferedImage> {
        private final String host;
        private final int port;

        ImageReceiverWorker(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        protected Void doInBackground() {
            try {
                socket = new Socket(host, port);
                dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                SwingUtilities.invokeLater(() -> {
                    connectButton.setText("Disconnect");
                    connectButton.setEnabled(true);
                });

                while (!isCancelled()) {
                    // Protocol: first an int length, then the bytes
                    int len;
                    try {
                        len = dis.readInt();
                    } catch (EOFException eof) {
                        break; // server closed
                    }

                    if (len <= 0) continue;
                    byte[] bytes = new byte[len];
                    dis.readFully(bytes);

                    // Decode image
                    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                    BufferedImage img = ImageIO.read(bais);
                    if (img != null) publish(img);
                }
            } catch (IOException ex) {
                if (!isCancelled()) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(ClientGUI.this,
                            "Connection error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                }
            } finally {
                // cleanup when finished
                try {
                    if (dis != null) dis.close();
                    if (socket != null) socket.close();
                } catch (IOException ignored) {}
            }
            return null;
        }

        @Override
        protected void process(java.util.List<BufferedImage> chunks) {
            // process latest image only (drop older frames if backlog)
            BufferedImage latest = chunks.get(chunks.size() - 1);
            // Scale image to fit label while preserving aspect ratio
            int lblW = imageLabel.getWidth();
            int lblH = imageLabel.getHeight();
            if (lblW <= 0 || lblH <= 0) {
                // If label not laid out yet, just set directly
                imageLabel.setIcon(new ImageIcon(latest));
            } else {
                Image scaled = getScaledImage(latest, lblW, lblH);
                imageLabel.setIcon(new ImageIcon(scaled));
            }
        }

        @Override
        protected void done() {
            SwingUtilities.invokeLater(() -> {
                connectButton.setText("Connect");
                connectButton.setEnabled(true);
            });
        }
    }

    private static Image getScaledImage(BufferedImage srcImg, int maxW, int maxH) {
        double imgW = srcImg.getWidth();
        double imgH = srcImg.getHeight();

        double scale = Math.min((double) maxW / imgW, (double) maxH / imgH);
        if (scale <= 0) scale = 1.0;

        int newW = (int) Math.round(imgW * scale);
        int newH = (int) Math.round(imgH * scale);
        return srcImg.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientGUI gui = new ClientGUI();
            gui.setVisible(true);
        });
    }
}
