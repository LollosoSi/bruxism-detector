package com.example.bruxismdetector;

import android.content.Context;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ServerDiscovery {
    public static String discoverServerIP(Context context) {
        final int broadcastPort = 4002;

        // Ascoltiamo direttamente sulla porta 4002 come normale DatagramSocket
        try (DatagramSocket socket = new DatagramSocket(broadcastPort)) {
            socket.setBroadcast(true);
            socket.setSoTimeout(5000); // Aspetta massimo 5 secondi

            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            // Si blocca qui finché non riceve il pacchetto dal PC o scade il tempo
            socket.receive(packet);

            // Estraiamo l'IP reale del PC
            return packet.getAddress().getHostAddress();

        } catch (IOException e) {
            e.printStackTrace();
            return null; // Timeout: il PC non ha inviato nulla in tempo
        }
    }
}