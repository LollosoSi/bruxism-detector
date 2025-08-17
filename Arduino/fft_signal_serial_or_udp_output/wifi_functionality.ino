#ifdef WIFI_OUT

void send_parameters_udp() {
  udp.beginPacket(multicastAddress, multicastPort);

  // Send samplingFrequency as uint16_t (Little-Endian)
  udp.write(lowByte(samplingFrequency));
  udp.write(highByte(samplingFrequency));

  // Send samples as uint16_t (Little-Endian)
  udp.write(lowByte(samples));
  udp.write(highByte(samples));

  udp.endPacket();
}

void send_element(data_element* d) {
  // Send data
  udp.beginPacket(multicastAddress, multicastPort);
  udp.write((uint8_t*)(d), 5);
  udp.endPacket();
}

void send_event(uint8_t event) {
  // Send FFT data
  udp.beginPacket(multicastAddress, multicastPort);
  udp.write((uint8_t*)(&event), 1);
  udp.endPacket();
}

uint8_t cc = 200;
void send_to_udp() {

  if (++cc == 0)
    send_parameters_udp();

  // Send FFT data
  udp.beginPacket(multicastAddress, multicastPort);
  udp.write((uint8_t*)(vReal), network_send_bytes);
  udp.endPacket();
}

void read_from_udp() {
  char packetBuffer[255];  // Buffer to store incoming messages
  int packetSize = read_udp.parsePacket();


  int len = 0;
  if (packetSize) {
    len = read_udp.read(packetBuffer, sizeof(packetBuffer) - 1);

    Serial.print("Read ");
    Serial.print((int)len);
    Serial.print(": ");
    Serial.println(packetBuffer[0]);

    if (len > 0) {
      packetBuffer[len] = 0;  // Null-terminate the received string
    }
    if (len == 1) {
      switch (packetBuffer[0]) {
        case 0:
        case 1:
        case 2:
        case 3:
          warning_beep();
          break;

        default:
          trigger_alarm();
          // Confirm alarm action received via UDP by responding with 1 byte of value 2
          send_event(UDP_ALARM_CONFIRMED);
          break;
      }
    }
  }
}

void wifi_setup() {
  Serial.begin(500000);

  Serial.print("Bytes per double: ");
  Serial.println(sizeof(double));
  Serial.print("Bytes to send udp: ");
  Serial.println(network_send_bytes);

  // Wi-Fi code for Arduino Uno R4 WiFi
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(1000);
  }

  String fv = WiFi.firmwareVersion();
  if (fv < WIFI_FIRMWARE_LATEST_VERSION) {
    Serial.println("Please upgrade the firmware");
  }

#if defined(ESP32)
  // Begin UDP on ESP32 (ESP32 does not use `beginMulticast`)
  udp.beginMulticast(multicastAddress, multicastPort);
  read_udp.beginMulticast(multicastAddress, multicastReadPort);
  //udp.beginMulticast(WiFi.localIP(), multicastAddress, multicast
#else
  udp.beginMulticast(multicastAddress, multicastPort);
  read_udp.beginMulticast(multicastAddress, multicastReadPort);
  //udp.beginMulticast(WiFi.localIP(), multicastAddress, multicastPort);
  //read_udp.beginMulticast(WiFi.localIP(), multicastAddress, multicastReadPort);
#endif

  Serial.println("Connected to WiFi");
  printWifiStatus();

  //send_parameters_udp();
}

void printWifiStatus() {
  // print the SSID of the network you're attached to:
  Serial.print("SSID: ");
  Serial.println(WiFi.SSID());

  // print your board's IP address:
  IPAddress ip = WiFi.localIP();
  Serial.print("IP Address: ");
  Serial.println(ip);

  // print the received signal strength:
  long rssi = WiFi.RSSI();
  Serial.print("signal strength (RSSI):");
  Serial.print(rssi);
  Serial.println(" dBm");
}

#endif