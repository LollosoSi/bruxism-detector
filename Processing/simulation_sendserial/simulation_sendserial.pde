import processing.serial.*;

Serial myPort;
String csvFile = "input.csv";
String outputFile = "output.csv";
String line;
String[] lines;
int cursor = 0;

void setup() {
  size(400, 400);
  String portName = Serial.list()[1]; // Replace with your serial port name
  myPort = new Serial(this, portName, 500000);
  lines = loadStrings(csvFile);
  println("Connected to serial port: " + portName);
}

void draw() {
  if (myPort.available() > 0) {
    line = myPort.readStringUntil('\n');
    if (line != null) {
      println("Received from Arduino: " + line.replace("\n", ""));
      saveStrings(outputFile, append(loadStrings(outputFile), line.replace("\n", "")));
    }
  }

  if (lines.length > cursor) {
    line = lines[cursor++];
    myPort.write(line+"\n");
    background(255);
    fill(0);
    textSize(10);
    textAlign(CENTER, CENTER);
    text("Status: "+ String.valueOf(100.0*(((float)cursor)/lines.length)) +"%", width/2, height/2);
    //println("Sent to Arduino: " + line);
  }
}

void keyPressed() {
  if (key == 's') {
    saveStrings(outputFile, lines);
    println("Output saved to " + outputFile);
  }
}
