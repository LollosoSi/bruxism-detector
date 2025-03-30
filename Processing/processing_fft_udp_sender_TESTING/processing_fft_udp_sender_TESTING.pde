import java.net.*;

MulticastSocket udpSocket;
InetAddress serverAddress;
int serverPort = 4000; // Same port as the receiver sketch
int sampleCount = 128; // Same sample count as the receiver sketch
long samplingFrequency = 2000; // Same sampling frequency as the receiver sketch


int rectX, rectY;      // Position of square button
int circleX, circleY;  // Position of circle button
int rectSize = 90;     // Diameter of rect
int circleSize = 93;   // Diameter of circle
color rectColor, circleColor, baseColor;
color rectHighlight, circleHighlight;
color currentColor;
boolean rectOver = false;
boolean circleOver = false;

void setup() {
  size(500, 500);
  try {
    udpSocket = new MulticastSocket();
    serverAddress = InetAddress.getByName("239.255.0.1");
    NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost()); // You can specify a specific network interface if needed
    udpSocket.joinGroup(new InetSocketAddress(serverAddress, serverPort), netIf);

    // Start sending simulated data
    println("Sending UDP data to: " + serverAddress + ":" + serverPort);
    sendInitialParameters();
    frameRate(5);  // Set low frame rate to simulate slow data sending
  }
  catch (Exception e) {
    e.printStackTrace();
  }


  rectColor = color(0);
  rectHighlight = color(51);
  circleColor = color(255);
  circleHighlight = color(204);
  baseColor = color(102);
  currentColor = baseColor;
  circleX = width/2+circleSize/2+10;
  circleY = height/2;
  rectX = width/2-rectSize-10;
  rectY = height/2-rectSize/2;
  ellipseMode(CENTER);
}

void draw() {
  // Simulate sending FFT data every frame
  sendFFTData();

  update(mouseX, mouseY);
  background(currentColor);

  if (rectOver) {
    fill(rectHighlight);
  } else {
    fill(rectColor);
  }
  stroke(255);
  rect(rectX, rectY, rectSize, rectSize);

  if (circleOver) {
    fill(circleHighlight);
  } else {
    fill(circleColor);
  }
  stroke(0);
  ellipse(circleX, circleY, circleSize, circleSize);
}

void sendInitialParameters() {
  // Send the initial parameters (sampling frequency and sample count)
  String params = samplingFrequency + "," + sampleCount + "\n";
  sendUDPData(params);
}

void sendFFTData() {
  // Generate simulated FFT data
  String fftData = "";

  // Create a simulated FFT data string in the format "index:value"
  for (int i = 0; i < sampleCount; i++) {
    float magnitude = random(0, 200);  // Simulate FFT magnitudes
    fftData += i + ":" + magnitude + " ";
  }

  sendUDPData(fftData.trim() + "\n");
}

void sendUDPData(String data) {
  try {
    byte[] sendData = data.getBytes();
    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
    udpSocket.send(sendPacket);
   // println("Sent: " + data);  // Optional: log the data sent to the console
  }
  catch (Exception e) {
    e.printStackTrace();
  }
}




void update(int x, int y) {
  if ( overCircle(circleX, circleY, circleSize) ) {
    circleOver = true;
    rectOver = false;
  } else if ( overRect(rectX, rectY, rectSize, rectSize) ) {
    rectOver = true;
    circleOver = false;
  } else {
    circleOver = rectOver = false;
  }
}

void mousePressed() {
  if (circleOver) {
    currentColor = circleColor;
  }
  if (rectOver) {
    currentColor = rectColor;
    try {
      byte[] dt = {1};
      DatagramPacket sendPacket = new DatagramPacket(dt, 1, serverAddress, serverPort);
      udpSocket.send(sendPacket);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    try {
      byte[] dt = {0};
      DatagramPacket sendPacket = new DatagramPacket(dt, 1, serverAddress, serverPort);
      udpSocket.send(sendPacket);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}

boolean overRect(int x, int y, int width, int height) {
  if (mouseX >= x && mouseX <= x+width &&
    mouseY >= y && mouseY <= y+height) {
    return true;
  } else {
    return false;
  }
}

boolean overCircle(int x, int y, int diameter) {
  float disX = x - mouseX;
  float disY = y - mouseY;
  if (sqrt(sq(disX) + sq(disY)) < diameter/2 ) {
    return true;
  } else {
    return false;
  }
}
