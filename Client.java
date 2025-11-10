import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;
import javax.swing.*;

public class Client {
    public static void main(String[] args) throws Exception {
        String serverIP = "127.0.0.1"; // change to server's IP
        int port = 5000;

        Socket socket = new Socket(serverIP, port);
        System.out.println("Connected to server: " + serverIP);

        DataInputStream dis = new DataInputStream(socket.getInputStream());

        JFrame frame = new JFrame("Remote Desktop Viewer");
        JLabel label = new JLabel();
        frame.getContentPane().add(label);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);

        while (true) {
            int len = dis.readInt();
            byte[] data = new byte[len];
            dis.readFully(data);

            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            label.setIcon(new ImageIcon(img));
            frame.repaint();
        }
    }
}
