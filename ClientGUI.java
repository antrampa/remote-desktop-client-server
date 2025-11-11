import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * Remote Desktop Client with GUI and remote control
 * - Connect/disconnect via GUI
 * - Displays live screen stream
 * - Sends mouse + keyboard events to server
 */
public class ClientGUI extends JFrame {
    private final JTextField ipField = new JTextField("127.0.0.1", 15);
    private final JTextField portField = new JTextField("5000", 6);
    private final JButton connectButton = new JButton("Connect");
    private final JLabel imageLabel = new JLabel();
    private final JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    // Networking
    private volatile Socket socket;
    private volatile DataInputStream dis;
    private volatile DataOutputStream dos;
    private ImageReceiverWorker receiverWorker;

    // Screen size of remote machine (assumed same as local for now)
    private Dimension remoteScreenSize = Toolkit.getDefaultToolkit().getScreenSize();

    public ClientGUI() {
        super("Remote Desktop Viewer (Client)");

        // Build GUI
        topPanel.add(new JLabel("Server IP:"));
        topPanel.add(ipField);
        topPanel.add(new JLabel("Port:"));
        topPanel.add(portField);
        topPanel.add(connectButton);

        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);
        imageLabel.setBackground(Color.BLACK);
        imageLabel.setOpaque(true);

        this.setLayout(new BorderLayout());
        this.add(topPanel, BorderLayout.NORTH);
        this.add(new JScrollPane(imageLabel), BorderLayout.CENTER);

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(1000, 700);
        this.setLocationRelativeTo(null);

        // Connect button
        connectButton.addActionListener(e -> {
            if (receiverWorker == null || receiverWorker.isDone()) {
                startConnection();
            } else {
                stopConnection();
            }
        });

        // Send mouse and keyboard events
        setupEventListeners();

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
        try {
            if (receiverWorker != null) receiverWorker.cancel(true);
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        } finally {
            socket = null;
            dis = null;
            dos = null;
            SwingUtilities.invokeLater(() -> {
                connectButton.setText("Connect");
                connectButton.setEnabled(true);
            });
        }
    }

    private void setupEventListeners() {
        imageLabel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                sendMouseMove(e.getX(), e.getY());
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                sendMouseMove(e.getX(), e.getY());
            }
        });

        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                sendMouseClick(e);
            }
        });

        imageLabel.setFocusable(true);
        imageLabel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                sendKeyEvent(3, e.getKeyCode()); // 3 = key press
            }
            @Override
            public void keyReleased(KeyEvent e) {
                sendKeyEvent(4, e.getKeyCode()); // 4 = key release
            }
        });
    }

    private void sendMouseMove(int x, int y) {
        if (dos == null) return;

        try {
            // Convert local imageLabel coordinates to remote screen coordinates
            int lblW = imageLabel.getWidth();
            int lblH = imageLabel.getHeight();
            double scaleX = remoteScreenSize.getWidth() / lblW;
            double scaleY = remoteScreenSize.getHeight() / lblH;
            int remoteX = (int) (x * scaleX);
            int remoteY = (int) (y * scaleY);

            synchronized (dos) {
                dos.writeByte(1); // event type = mouse move
                dos.writeInt(remoteX);
                dos.writeInt(remoteY);
                dos.flush();
            }
        } catch (IOException ignored) {}
    }

    private void sendMouseClick(MouseEvent e) {
        if (dos == null) return;

        int button;
        if (e.getButton() == MouseEvent.BUTTON1)
            button = 1;
        else if (e.getButton() == MouseEvent.BUTTON3)
            button = 2;
        else
            button = 1;

        try {
            synchronized (dos) {
                dos.writeByte(2); // event type = mouse click
                dos.writeInt(button);
                dos.flush();
            }
        } catch (IOException ignored) {}
    }


    private void sendKeyEvent(int type, int keyCode) {
        if (dos == null) return;

        try {
            synchronized (dos) {
                dos.writeByte(type); // 3 = key press, 4 = key release
                dos.writeInt(keyCode);
                dos.flush();
            }
        } catch (IOException ignored) {}
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
                dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                SwingUtilities.invokeLater(() -> {
                    connectButton.setText("Disconnect");
                    connectButton.setEnabled(true);
                    imageLabel.requestFocusInWindow(); // capture keyboard
                });

                while (!isCancelled()) {
                    int len;
                    try {
                        len = dis.readInt();
                    } catch (EOFException eof) {
                        break;
                    }

                    if (len <= 0) continue;
                    byte[] data = new byte[len];
                    dis.readFully(data);

                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                    if (img != null) publish(img);
                }
            } catch (IOException ex) {
                if (!isCancelled()) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(ClientGUI.this,
                            "Connection error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                }
            } finally {
                stopConnection();
            }
            return null;
        }

        @Override
        protected void process(List<BufferedImage> chunks) {
            BufferedImage latest = chunks.get(chunks.size() - 1);
            int lblW = imageLabel.getWidth();
            int lblH = imageLabel.getHeight();
            if (lblW <= 0 || lblH <= 0) {
                imageLabel.setIcon(new ImageIcon(latest));
            } else {
                Image scaled = getScaledImage(latest, lblW, lblH);
                imageLabel.setIcon(new ImageIcon(scaled));
            }
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
