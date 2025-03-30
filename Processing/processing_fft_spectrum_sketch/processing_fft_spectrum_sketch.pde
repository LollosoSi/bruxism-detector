// Requires Library "Sound"

import processing.serial.*;
import java.net.*;
import java.awt.Toolkit;

import processing.sound.*;


String csv_folder_path = "RECORDINGS\\";

// Use only UDP | or use serial if available
boolean override_use_UDP = true;

boolean remote_button_pressed = false;

PrintWriter file_out;
PrintWriter file_raw_out;

SinOsc osc;
float osc_freq = 2600, osc_amp = 0.5, osc_pos = 0.5;
int clench_start = -10000;
boolean is_clenching = false;
float clench_amp = 0.2;
float clench_amp_start=0.2;


int warning_beep_wait = 4000;
int warning_beep_duration = 100;

int clench_wait_before_alarm_ms = (warning_beep_wait+warning_beep_duration) * 3;

boolean running_thread = false;

void async_play(float a, float b, int c, int d) {
  running_thread = true;
  play_osc(a, b, c);
  delay(d);
  running_thread = false;
}


Serial myPort;
double[] fftData; // This will store the FFT data
int sampleCount = 128;
long samplingFrequency = 2000;
float maxHeight = 600; // Maximum visible height for the bars
double maxMagnitude = 5000; // To track the maximum magnitude for normalization

// Muscle contraction frequency range (in Hz)
int muscleMinFreq = 80;
int muscleMaxFreq = 230;
float contractionThreshold = 1.4; // Threshold for muscle contraction detection (based on energy comparison)
int minimum_energy = 3000;

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

void setup() {

  dh = new DisposeHandler(this);

  size(1280, 720);
  //fullScreen();

  Date currentDate = new Date();
  SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
  String formattedDate = formatter.format(currentDate);

  file_out = createWriter(csv_folder_path+formattedDate+".csv");
  append_csv(new String[]{"Millis", "Time", "Event", "Notes", "Duration (seconds)"}, file_out);
  append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Start", "Tracking started"}, file_out);

  file_raw_out = createWriter(csv_folder_path+formattedDate+"_RAW.csv");
  append_csv(new String[]{"Millis", "Classification"}, file_raw_out);

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

  osc = new SinOsc(this);
  osc.set(osc_freq, osc_amp, osc_pos);
}


int tone_sel = 0;
int tone_num = 4;
int[] tones = {1567, 1760, 1975, 2093};
int[] durations = {200, 200, 200, 500};
int[] waits = {50, 50, 50, 400};

boolean confirmed_udp_alarm = false;

float amp = 0.0;
boolean alarm_triggered = false, alarm_triggered_loop = false;

void trigger_alarm() {
  if (!alarm_triggered_loop)
    alarm_triggered=true;
}
void alarm_loop() {
  if (alarm_triggered) {
    tone_sel = 0;
    confirmed_udp_alarm = false;

    println("Alarm triggered");
    append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Alarm", "STARTED"}, file_out);
    alarm_triggered=false;
    alarm_triggered_loop = true;
  } else if (alarm_triggered_loop) {
    if (!remote_button_pressed) {

      if (!running_thread) {

        if (!confirmed_udp_alarm) {
          send_udp_code(ALARM_START);
        }


        if (amp != 1)
          amp+=0.1;

        Thread t = new Thread(() -> async_play(amp, tones[tone_sel], durations[tone_sel], waits[tone_sel]));

        if (++tone_sel >= tone_num)
          tone_sel=0;

        t.start();
      }
    } else {
      amp = 0;
      remote_button_pressed = false;
      println("Alarm stopped");
      append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Alarm", "STOPPED"}, file_out);
      alarm_triggered=false;
      alarm_triggered_loop=false;
      confirmed_udp_alarm=false;
    }
  }
}

void play_osc(float amp, float tone, int wait) {
  osc.amp(amp);
  osc.freq(tone);

  osc.play();
  delay(wait);
  osc.stop();
}

void draw() {
  alarm_loop();

  // Draw the scale first, then the bars
  drawScale();
  drawFFTBars();

  text(String.format("Sampling Frequency: %d Hz, Samples: %d", samplingFrequency, sampleCount), width/2, 45);

  // Display the top 3 highest frequencies and their magnitudes
  displayTopFrequencies();

  // Check and display if muscle is contracted
  checkMuscleContraction();

  // Listen for UDP packets if using UDP
  if (udpSocket != null) {
    listenForUDP();
  }
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

long last_contraption = -10000;
boolean muscle_contracted = false;

// Function to check muscle contraction based on energy comparison
void checkMuscleContraction() {
  float muscleEnergy = 0;
  float totalEnergy = 0;

  // Calculate energy in the 50-150 Hz range (muscle contraction range)
  for (int i = 0; i < sampleCount / 2; i++) {
    float frequency = i * (samplingFrequency / sampleCount);
    float magnitude = (float)fftData[i];
    if (frequency >= muscleMinFreq && frequency <= muscleMaxFreq) {
      muscleEnergy += magnitude * magnitude; // Energy is magnitude squared
    }
    totalEnergy += magnitude * magnitude; // Total energy of the spectrum
  }

  boolean temp_muscle_contracted = ((muscleEnergy > (totalEnergy - muscleEnergy) * contractionThreshold) && (muscleEnergy>minimum_energy));
  if (temp_muscle_contracted)
    last_contraption = millis();



  if (temp_muscle_contracted && !muscle_contracted) {
  } else if (!temp_muscle_contracted && muscle_contracted) {
  } else {
  }

  /*
  if(is_clenching){
   
   }else{
   fill(0);
   textSize(20);
   textAlign(CENTER, CENTER);
   text("Muscle Relaxed", width / 2, 20);
   
   if(muscle_contracted){
   
   }
   }*/

  // If the energy in the 50-150 Hz range is greater than the rest of the spectrum, muscle is contracted
  if (((muscleEnergy > (totalEnergy - muscleEnergy) * contractionThreshold) && (muscleEnergy>minimum_energy))) {
    fill(255, 0, 0);
    textSize(20);
    textAlign(CENTER, CENTER);
    text("Muscle Contracted!", width / 2, 20);


    if (is_clenching) {
      if ((millis()-clench_start > clench_wait_before_alarm_ms) && !alarm_triggered_loop)
        trigger_alarm();
      else if (!running_thread) {
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Beep", "WARNING BEEP"}, file_out);

        send_udp_code(BEEP);

        if (clench_amp < 1)
          clench_amp+=0.5;
        if (clench_amp > 1)
          clench_amp=1;
        Thread t = new Thread(() -> async_play(clench_amp, osc_freq, warning_beep_duration, warning_beep_wait));
        t.start();
        //play_osc(clench_amp, osc_freq, 300);
        //delay(500);
      }
    } else if (millis()-clench_start < 10000) {
      is_clenching=true;
    } else {
      clench_amp = clench_amp_start;
      is_clenching=true;
      clench_start = millis();
      append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Clenching", "STARTED"}, file_out);
    }
  } else {
    fill(0);
    textSize(20);
    textAlign(CENTER, CENTER);
    text("Muscle Relaxed", width / 2, 20);


    if (is_clenching && (millis()-clench_start) > 5000) {
      is_clenching=false;

      append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Clenching", "STOPPED", String.valueOf((millis()-clench_start)/1000.0)}, file_out);
    }
  }
  text("Muscle Energy: "+ muscleEnergy + " Ratio: " + ((muscleEnergy/(totalEnergy - muscleEnergy))*100.0) + "%\nTotal Energy: " + totalEnergy + "\nDifference"+ (totalEnergy - muscleEnergy), 3*(width / 4), 60);
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

// Handle incoming serial event (with 4-byte packet for parameters)
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
    } else if (length % 5 == 0) {  // Ensure it's a multiple of 5
      int count = length / 5;   // Number of elements in the packet

      for (int i = 0; i < count; i++) {
        int offset = i * 5;

        long timestamp = ByteBuffer.wrap(data, offset, 4)
          .order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
        boolean value = (data[offset + 4] != 0);

        append_csv(new String[]{String.valueOf(timestamp), String.valueOf(value)}, file_raw_out);
        println("Received RAW: " + timestamp + ", " + value);
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
