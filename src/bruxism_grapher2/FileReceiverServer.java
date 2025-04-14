package bruxism_grapher2;

import java.io.*;
import java.net.*;

public class FileReceiverServer {
    private static final int TCP_PORT = 5000;
    private static final String SAVE_DIR = ".";
    private static final String MULTICAST_ADDRESS = "239.255.0.1";
    private static final int MULTICAST_PORT = 4002;

    static boolean running = true;
    
    public void main(String[] args) {
        new File(SAVE_DIR).mkdirs();

        new Thread(() -> sendMulticast()).start();

        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.println("Server listening on port " + TCP_PORT);

            
                try (Socket clientSocket = serverSocket.accept();
                     DataInputStream dis = new DataInputStream(clientSocket.getInputStream())) {

                    int numFiles = dis.readInt();  // Total number of files
                    for (int i = 0; i < numFiles; i++) {
                        String relativePath = dis.readUTF(); // Relative path from root
                        long fileSize = dis.readLong();

                        File outFile = new File(SAVE_DIR, relativePath);
                        outFile.getParentFile().mkdirs();

                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            byte[] buffer = new byte[4096];
                            long totalRead = 0;
                            int read;

                            while (totalRead < fileSize &&
                                    (read = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) > 0) {
                                fos.write(buffer, 0, read);
                                totalRead += read;
                            }
                        }

                        System.out.println("Received: " + relativePath);
                    }
                }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        running = false;
    }

    private static void sendMulticast() {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            String ip = InetAddress.getLocalHost().getHostAddress();
            byte[] message = ip.getBytes();

            while (running) {
                DatagramPacket packet = new DatagramPacket(message, message.length, group, MULTICAST_PORT);
                socket.send(packet);
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

