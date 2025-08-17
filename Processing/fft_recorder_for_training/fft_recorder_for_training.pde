import java.net.*;
import java.awt.Toolkit;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.io.*;
PrintWriter clenchingFile, nonClenchingFile;
boolean recordingClenching = false;
boolean recordingNonClenching = false;

// UDP socket for receiving data
MulticastSocket udpSocket;
InetAddress serverAddress;
byte[] udpBuffer = new byte[2048];  // buffer to hold incoming UDP data
String udpReceivedData = "";
int serverPort = 4000;

void setup() {
  size(400, 200);
  clenchingFile = createWriter("clenching.csv");
  nonClenchingFile = createWriter("non_clenching.csv");


  try {
    udpSocket = new MulticastSocket(serverPort); // Same port as the sender
    serverAddress = InetAddress.getByName("239.255.0.1");
    NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost()); // Use the same network interface as the sender
    udpSocket.joinGroup(new InetSocketAddress(serverAddress, 12345), netIf);

    println(String.format("Listening for UDP packets on port %d...", serverPort));
  }
  catch (Exception e) {
    e.printStackTrace();
  }

  println("Recording stopped, press c,n,s for actions");
}

float[] fftData;

void draw() {
  listenForUDP();

  if (recordingClenching) {
    saveData(clenchingFile, fftData);
  } else if (recordingNonClenching) {
    saveData(nonClenchingFile, fftData);
  }

  text("Recording: " + (recordingClenching ? "Clenching": recordingNonClenching ? "Non Clenching" : "Nothing" ), width/2, height/2);
}

void keyPressed() {
  if (key == 'c') {
    recordingClenching = true;
    recordingNonClenching = false;
    println("Recording Clenching...");
  } else if (key == 'n') {
    recordingClenching = false;
    recordingNonClenching = true;
    println("Recording Non Clenching...");
  } else if (key == 's') {
    clenchingFile.flush();
    clenchingFile.close();
    nonClenchingFile.flush();
    nonClenchingFile.close();
    println("Files saved!");
    exit();
  } else {
    recordingClenching = false;
    recordingNonClenching = false;
    println("Recording stopped, press c,n,s for actions");
  }
}

void saveData(PrintWriter file, float[] data) {
  boolean first = true;
  for (float val : data) {
    file.print((first ? "" : ",") + val);
    if (first)first = false;
  }
  file.println();
}

// Handle UDP data
void listenForUDP() {
  DatagramPacket packet = new DatagramPacket(udpBuffer, udpBuffer.length);
  try {
    udpSocket.receive(packet);  // Block until a packet is received

    //println("Bytes: " + packet.getLength());

    // Process the received UDP data as raw byte values
    processData(packet.getData(), packet.getLength());
  }
  catch (Exception e) {
    e.printStackTrace();
  }
}

// Central function to process data from both UDP and Serial sources
void processData(byte[] data, int length) {
  try {
    if (length == 4) {
      // Processing initial parameters (4 bytes: two uint16_t values)
      int samplingFrequency = ((data[0] & 0xFF) | ((data[1] & 0xFF) << 8)); // Little-endian uint16_t
      int sampleCount = ((data[2] & 0xFF) | ((data[3] & 0xFF) << 8));       // Little-endian uint16_t

      // Reinitialize fftData array based on the new sample count
      fftData = new float[sampleCount / 2];  // Adjust the array size properly

      println("Received Parameters: fs=" + samplingFrequency + ", samples=" + sampleCount);

      // Redraw visualization with new parameters
      background(255);
    } else if (length==1 ||(length % 5 == 0) ) {
    } else {
      // Processing FFT data (raw bytes)
      int numBins = min((length)/4, fftData.length);  // Ensure no out-of-bounds errors

      //print("Inbound data:");
      //println("Bins: " + numBins);
      for (int i = 0; i < numBins; i++) {
        // Directly map each byte to the corresponding fftData bin
        fftData[i] = ByteBuffer.wrap(data, (i*4), 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();  // Just store the bytes value as magnitude
      }
      // println();
    }
  }
  catch (Exception e) {
    println("Error processing data: " + e.getMessage());
  }
}
