package com.example.bruxismdetector;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class ServerDiscovery {
    public static String discoverServerIP() {
        final String multicastAddress = "239.255.0.1";
        final int multicastPort = 4002;

        try (MulticastSocket socket = new MulticastSocket(multicastPort)) {
            InetAddress group = InetAddress.getByName(multicastAddress);
            socket.joinGroup(group);

            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.setSoTimeout(5000);

            socket.receive(packet);
            socket.leaveGroup(group);

            return new String(packet.getData(), 0, packet.getLength());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}


