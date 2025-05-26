import processing.serial.*;
import java.net.*;
import java.awt.Toolkit;

String csv_folder_path = "RECORDINGS/";

boolean remote_button_pressed = false;
boolean confirmed_udp_alarm = false;

// Muscle contraction frequency range (in Hz)
int muscleMinFreq = 80;
int muscleMaxFreq = 230;

// Use only UDP | or use serial if available. DEPRECATED, KEEP THIS TRUE.
// Serial is going to be supported for SIMULATIONS
boolean override_use_UDP = true;

Serial myPort;
double[] fftData; // This will store the FFT data
int sampleCount = 128;
long samplingFrequency = 2000;
float maxHeight = 600; // Maximum visible height for the bars
double maxMagnitude = 5000; // To track the maximum magnitude for normalization

// UDP socket for receiving data
MulticastSocket udpSocket;
InetAddress serverAddress;
byte[] udpBuffer = new byte[5096];  // buffer to hold incoming UDP data
String udpReceivedData = "";
int serverPort = 4000;

MulticastSocket sendudpSocket;
InetAddress sendserverAddress;
int sendserverPort = 4001;

import java.text.SimpleDateFormat;
import java.util.Date;

PrintWriter file_out;
PrintWriter file_raw_out;

String formatted_now() {
  // Get the current hour and second
  int h = hour();
  int m = minute();

  // Format the time as a string
  return String.format("%02d:%02d", h, m);
}

void append_csv(String[] data, PrintWriter out) {

  for (int i = 0; i < data.length; i++) {
    if (i!=0)
      out.print(";");
    out.print(data[i]);
  }
  out.println();
  
  out.flush();
}

DisposeHandler dh;

public class DisposeHandler {

  DisposeHandler(PApplet pa)
  {
    pa.registerMethod("dispose", this);
  }

  public void dispose()
  {
    exiting();
  }
}

void exiting() {
  println("Closing sketch");
  // Place here the code you want to execute on exit
  append_csv(new String[]{String.valueOf(millis()), formatted_now(), "End", "Tracking ended"}, file_out);
  file_out.flush(); // Writes the remaining data to the file
  file_out.close(); // Finishes the file

  file_raw_out.flush();
  file_raw_out.close();
}

void send_udp_code(byte c) {
  try {
    byte[] s = {c};
    DatagramPacket sendPacket = new DatagramPacket(s, 1, sendserverAddress, sendserverPort);
    sendudpSocket.send(sendPacket);
    println("Sent: " + c);  // Optional: log the data sent to the console
  }
  catch (Exception e) {
    e.printStackTrace();
  }
}

String get_new_filename(String baseName, String extension, String folder_path) {

  if (!folder_path.endsWith("/")) {
    folder_path += "/";
  }

  String relativePath = folder_path + baseName + extension;
  File file = new File(dataPath(relativePath)); // <<< THIS is the key fix

  int count = 1;
  while (file.exists()) {
    relativePath = folder_path + baseName + " (" + count + ")" + extension;
    file = new File(dataPath(relativePath));  // <<< Fix applied here too
    count++;
  }

  return dataPath(relativePath); // Return full resolved path
}

void setup() {

  dh = new DisposeHandler(this);

  size(1280, 720);
  //fullScreen();

  Date currentDate = new Date();
  SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
  String formattedDate = formatter.format(currentDate);

  String filename1 = get_new_filename(formattedDate, ".csv", csv_folder_path);
  String filename2 = get_new_filename(formattedDate, "_RAW.csv", csv_folder_path+"RAW/");
  file_out = createWriter(filename1);
  append_csv(new String[]{"Millis", "Time", "Event", "Notes", "Duration (seconds)"}, file_out);
  append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Start", "Tracking started. Date: "+formattedDate}, file_out);

  file_raw_out = createWriter(filename2);
  append_csv(new String[]{"Millis", "Classification", "Classification int"}, file_raw_out);

  println("Creating files: " + filename1 + " " + filename2);

  file_out.flush();
  file_raw_out.flush();

  // Check if the second serial port is available
  if (Serial.list().length > 1 && !override_use_UDP) {
    // Use Serial if the second port is available
    String portName = Serial.list()[1]; // Choose the second available port
    myPort = new Serial(this, portName, 500000);
    println("Using Serial Port: " + portName);
  } else {
    // Fallback to UDP if the second port is not available
    try {
      udpSocket = new MulticastSocket(serverPort); // Same port as the sender
      serverAddress = InetAddress.getByName("239.255.0.1");
      NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost()); // Use the same network interface as the sender
      udpSocket.joinGroup(new InetSocketAddress(serverAddress, 12345), netIf);

      sendudpSocket = new MulticastSocket();
      sendserverAddress = InetAddress.getByName("239.255.0.1");
      NetworkInterface sendnetIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost()); // You can specify a specific network interface if needed
      sendudpSocket.joinGroup(new InetSocketAddress(sendserverAddress, sendserverPort), sendnetIf);

      println(String.format("Listening for UDP packets on port %d...", serverPort));
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  // Initialize fftData array with default sampleCount
  fftData = new double[sampleCount];

  // Clear the screen initially
  background(255);
}

// Function to draw the scale on the sides (including decibel scale)
void drawScale() {

  float scaleStep = 30; // Change this to adjust scale line spacing
  int scaleSteps = 5;

  // Draw scale lines on the left side
  stroke(0);
  line(50, 60, 50, height - 60); // Draw vertical scale line

  for (int i = 0; i <= scaleSteps; i++) {
    float yPos = map(i, 0, scaleSteps, 60, height - 60); // Y position for scale lines
    line(60, yPos, width - 60, yPos); // Horizontal scale line across the width
    textSize(10);
    fill(0);
    textAlign(CENTER, CENTER);
    text(String.format("%.1f", i * scaleStep), 30, yPos); // Display the scale values
  }

  // Draw the decibel scale on the side
  for (int i = 0; i <= 5; i++) {
    float yPos = map(i, 0, 5, height - 60, 60);
    float dbValue = 20 * log(i * maxHeight / 5 + 1);  // Convert to decibels
    textSize(10);
    fill(0);
    textAlign(CENTER, CENTER);
    text(String.format("%.1f dB", dbValue), 20, yPos);
  }
}

// Function to draw the FFT bars
void drawFFTBars() {
  background(255); // Redraw background only to clear previous bars

  // Draw the FFT spectrum bars
  float binWidth = samplingFrequency / (sampleCount);

  // Display the FFT bars
  for (int i = 0; i < (sampleCount / 2); i++) {
    float magnitude = (float)fftData[i]; // Use the fixed maxHeight for scaling
    float x = map(i, 0, sampleCount / 2, 60, width - 60); // X position
    float y = height - 60 - magnitude; // Y position based on magnitude

    // Check if this frequency is associated with muscle contraction
    if (i * binWidth >= muscleMinFreq && i * binWidth <= muscleMaxFreq) {
      fill(255, 0, 0); // Red color for muscle contraction frequencies
    } else {
      fill(100); // Normal color for non-muscle frequencies
    }

    rect(x, y, 6, magnitude); // Draw the bar

    // Display frequency labels vertically below each bar
    long frequency = (long)(i * binWidth);
    pushMatrix();
    translate(x + 3, height - 40); // Position under the bar
    rotate(HALF_PI);
    textSize(10);
    textAlign(CENTER, CENTER);
    text(String.format("%d Hz", frequency), 0, 0);
    popMatrix();
  }
}

// Function to find and display the top 3 highest frequencies
void displayTopFrequencies() {
  // Create an array to store the magnitude and index as pairs
  ArrayList<int[]> topFrequencies = new ArrayList<int[]>();

  // Find the top 3 frequencies with the highest magnitudes
  for (int i = 0; i < sampleCount / 2; i++) {
    topFrequencies.add(new int[]{i, (int)(fftData[i])});
  }

  // Sort the array based on the magnitude values
  topFrequencies.sort((a, b) -> Integer.compare(b[1], a[1])); // Sort by magnitude in descending order

  // Display the top 3 highest frequencies
  fill(0);
  textSize(12);
  text("Top 3 Frequencies:", width/2, 65);
  long avg = 0;
  for (int i = 0; i < 3; i++) {
    int index = topFrequencies.get(i)[0];
    float frequency = index * (samplingFrequency / sampleCount);
    float magnitude = (float)fftData[index]; // Adjust magnitude to maxHeight
    text(String.format("Freq: %.1f Hz, Mag: %.2f", frequency, magnitude), width/2, 60 + 25 + i * 15);
    avg += frequency;
  }

  avg /= 3;
  text(String.format("Avg high freq: %d Hz", avg), width/2, 60 + 75);
}

void draw() {

  // Draw the scale first, then the bars
  drawScale();
  drawFFTBars();

  text(String.format("Sampling Frequency: %d Hz, Samples: %d", samplingFrequency, sampleCount), width/2, 45);

  // Display the top 3 highest frequencies and their magnitudes
  displayTopFrequencies();

  // Listen for UDP packets if using UDP
  if (udpSocket != null) {
    listenForUDP();
  }
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

boolean firstread=true;

// Handle incoming serial event (unmaintained, sorry)
void serialEvent(Serial myPort) {
  int availableBytes = myPort.available();
  println("Available bytes: " + availableBytes);

  if (availableBytes == (firstread?(4):(sampleCount/2))) {
    firstread=false;
    byte[] serialData = new byte[availableBytes];
    myPort.readBytes(serialData);  // Read the available bytes



    processData(serialData, serialData.length);
  }
}

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

boolean did_print_sync = false;

long cstart = 0;
boolean alarmed = false;
boolean clenching=false;

final byte CLENCH_START = 0;
final byte CLENCH_STOP = 1;
final byte BUTTON_PRESS = 2;
final byte ALARM_START = 3;
final byte BEEP = 4;
final byte UDP_ALARM_CONFIRMED = 5;
final byte DETECTED = 6;
final byte CONTINUED = 7;
final byte ALARM_STOP = 8;
final byte TRACKING_STOP = 9;
final byte USING_ANDROID = 10;
final byte EVALUATION_RESULT = 11;
final byte SET_EVALUATION_THRESHOLD = 12;
final byte DO_NOT_BEEP_ARDUINO = 13;
final byte ALARM_ARDUINO_EVEN_WITH_ANDROID = 14;

final int dataelementbytes = 9;
long last_millis_raw = 0, sync_millis = 0, local_sync_millis = 0;
long timestamp_difference_add = 0;
// Central function to process data from both UDP and Serial sources
void processData(byte[] data, int length) {
  println("Packlen: " + length);
  try {
    if (length == 4) {
      // Processing initial parameters (4 bytes: two uint16_t values)
      samplingFrequency = ((data[0] & 0xFF) | ((data[1] & 0xFF) << 8)); // Little-endian uint16_t
      sampleCount = ((data[2] & 0xFF) | ((data[3] & 0xFF) << 8));       // Little-endian uint16_t

      // Reinitialize fftData array based on the new sample count
      fftData = new double[sampleCount / 2];  // Adjust the array size properly
      maxMagnitude = 0;

      println("Received Parameters: fs=" + samplingFrequency + ", samples=" + sampleCount);

      // Redraw visualization with new parameters
      background(255);
    } else if (length % dataelementbytes == 0) {  // Ensure it's a multiple of dataelementbytes
      long millisforsync = millis();

                int count = length / dataelementbytes;   // Number of elements in the packet

                for (int i = 0; i < count; i++) {
                    int offset = i * dataelementbytes;

                    long timestamp = (ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL) + timestamp_difference_add;
                    boolean value = (data[offset + 4] != 0);
                    float fvalue = ByteBuffer.wrap(data, offset + 5, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();

                    if(timestamp < last_millis_raw){
                        long offstart = (sync_millis+(last_millis_raw-local_sync_millis));
                        long offtime = millisforsync-offstart;
                        timestamp_difference_add = (last_millis_raw)+(offtime);
                        append_csv(new String[]{String.valueOf(millisforsync), formatted_now(), "ResetDetected", "Looks like arduino was reset here. It was down for approximately "+offtime+"ms."}, file_out);
                        append_csv(new String[]{String.valueOf(millisforsync), formatted_now(), "ResetDetectedStartMs", String.valueOf(offstart)}, file_out);
                        append_csv(new String[]{String.valueOf(millisforsync), formatted_now(), "ResetDetectedEndMs", String.valueOf(offstart+offtime)}, file_out);
                        timestamp += timestamp_difference_add;
                    }

                    if(i==count-1){
                        last_millis_raw = timestamp;
                    }

                    append_csv(new String[]{String.valueOf(timestamp), String.valueOf(value), String.valueOf((int)fvalue)}, file_raw_out);
                    System.out.println("Received RAW:\t" + timestamp + "\t" + value + "\t" + ((int)fvalue));
                }

                if (!did_print_sync) {
                    did_print_sync = true;
                    sync_millis=millisforsync;
                    local_sync_millis = ByteBuffer.wrap(data, (count-1) * dataelementbytes, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
                    append_csv(new String[]{String.valueOf(millisforsync), formatted_now(), "Sync", String.valueOf(local_sync_millis)}, file_out);
                }
    } else if (length==1) {
      int val = (data[0] & 0xFF);

      switch(val) {
      default:
        println("Received unrecognized byte value: "+val);
        break;
      case CLENCH_START:
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Clenching", "STARTED"}, file_out);
        cstart=millis();
        clenching=true;
        break;
      case CLENCH_STOP:
        clenching=false;
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Clenching", "STOPPED", String.valueOf((millis()-cstart)/1000.0)}, file_out);
        break;
      case BUTTON_PRESS:
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Button"}, file_out);
        remote_button_pressed = true;
        if (alarmed)
          append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Alarm", "STOPPED"}, file_out);
        if (clenching) {
          clenching=false;
          append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Clenching", "STOPPED", String.valueOf((millis()-cstart)/1000.0)}, file_out);
        }
        alarmed=false;
        break;
      case ALARM_START:
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Alarm", "STARTED"}, file_out);
        alarmed=true;
        break;
      case ALARM_STOP:
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Alarm", "STOPPED"}, file_out);
        alarmed=true;
        break;
      case BEEP:
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Beep", "WARNING BEEP"}, file_out);
        break;

      case UDP_ALARM_CONFIRMED:
        confirmed_udp_alarm = true;
        println("Confirmed alarm via UDP");
        break;

      case DETECTED:
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "CLENCHING", "FIRST DETECTION"}, file_out);
        break;

      case CONTINUED:
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "CLENCHING", "CONTINUED"}, file_out);
        break;

      case USING_ANDROID:
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "ANDROID", "Using android from here"}, file_out);
        break;

      case TRACKING_STOP:
        exit();
        break;
      }
    } else {
      // Processing FFT data (raw bytes)
      int numBins = min((length)/4, fftData.length);  // Ensure no out-of-bounds errors

      //print("Inbound data:");
      //println("Bins: " + numBins);
      for (int i = 0; i < numBins; i++) {
        // Directly map each byte to the corresponding fftData bin
        fftData[i] = ByteBuffer.wrap(data, (i*4), 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();  // Just store the bytes value as magnitude
        maxMagnitude = max((int)maxMagnitude, (int)fftData[i]);  // Track max magnitude
        //  print(" " + i + ":" + fftData[i]);
      }
      // println();
    }
  }
  catch (Exception e) {
    println("Error processing data: " + e.getMessage());
  }
}
