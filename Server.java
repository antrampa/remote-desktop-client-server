import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;

public class Server {
    public static void main(String[] args) throws Exception {
        int port = 5000; // you can change this
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);

        Socket client = serverSocket.accept();
        System.out.println("Client connected: " + client.getInetAddress());

        DataOutputStream dos = new DataOutputStream(client.getOutputStream());
        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

        while (true) {
            BufferedImage screen = robot.createScreenCapture(screenRect);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(screen, "jpg", baos);
            byte[] bytes = baos.toByteArray();

            dos.writeInt(bytes.length);
            dos.write(bytes);
            dos.flush();

            Thread.sleep(100); // ~10 FPS
            //Thread.sleep(1000); // ~10 FPS
        }
    }
}
