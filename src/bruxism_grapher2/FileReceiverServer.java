package bruxism_grapher2;

import java.io.*;
import java.net.*;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileReceiverServer {
    private static final int TCP_PORT = 5000;
    private static final String SAVE_DIR = ".";
    private static final int MULTICAST_PORT = 4002;

    static boolean running = true;

    public static void main(String[] args) {
        new File(SAVE_DIR).mkdirs();

        requestWindowsFirewallUnlock();

        new Thread(() -> sendMulticast()).start();

        // Thread pool per gestire fino a 10 flussi di ricezione in parallelo
        ExecutorService executor = Executors.newFixedThreadPool(10);

        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.println("Server listening on port " + TCP_PORT);

            while (running) {
                try {
                    // Accetta la connessione e la passa a un thread, tornando subito in ascolto
                    Socket clientSocket = serverSocket.accept();
                    executor.submit(() -> handleClientConnection(clientSocket));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        executor.shutdown();
    }

 // NUOVO METODO PER FILE PICCOLI: Decompressione Streaming al Volo
    private static void handleClientConnection(Socket clientSocket) {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new BufferedInputStream(clientSocket.getInputStream()))) {
            java.util.zip.ZipEntry entry;
            
            // Finché ci sono file nello zip in arrivo dalla rete...
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(SAVE_DIR, entry.getName());

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    // Crea la struttura delle cartelle se non esiste
                    outFile.getParentFile().mkdirs();
                    
                    // Scrive il file su disco
                    try (FileOutputStream fos = new FileOutputStream(outFile);
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        
                        byte[] buffer = new byte[65536];
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, read);
                        }
                    }
                    System.out.println("Received & Saved: " + entry.getName());
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            System.out.println("Connessione interrotta o conclusa.");
        } finally {
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    private static void requestWindowsFirewallUnlock() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                System.out.println("Richiesta autorizzazioni Firewall in corso...");
                String cmd = "powershell.exe -Command \"Start-Process cmd -ArgumentList '/c netsh advfirewall firewall add rule name=\"BruxismGrapher UDP\" dir=out action=allow protocol=UDP localport=4002 & netsh advfirewall firewall add rule name=\"BruxismGrapher TCP\" dir=in action=allow protocol=TCP localport=5000' -Verb RunAs\"";
                Runtime.getRuntime().exec(cmd);
            } catch (Exception e) {
                System.out.println("Impossibile richiedere lo sblocco del firewall in automatico.");
            }
        }
    }

    private static void sendMulticast() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            byte[] message = "BRUXISM_PC_SERVER".getBytes();

            while (running) {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    if (networkInterface.isLoopback() || !networkInterface.isUp()) continue;

                    for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                        InetAddress broadcast = interfaceAddress.getBroadcast();
                        if (broadcast == null) continue;

                        try {
                            DatagramPacket packet = new DatagramPacket(message, message.length, broadcast, MULTICAST_PORT);
                            socket.send(packet);
                        } catch (Exception ignored) {}
                    }
                }
                
                try {
                    DatagramPacket packet = new DatagramPacket(message, message.length, InetAddress.getByName("255.255.255.255"), MULTICAST_PORT);
                    socket.send(packet);
                } catch (Exception ignored) {}

                Thread.sleep(2000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}