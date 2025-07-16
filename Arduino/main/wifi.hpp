#pragma once

#include "user/WifiSettings.h"
#include "user/Settings.h"
#include "runtime_variables.hpp"

#include "version.h"

#include <ArduinoBLE.h>

BLEService configService("12345678-1234-5678-1234-56789abcdef0");
BLECharacteristic wifiChar("abcdefab-1234-5678-1234-56789abcdef0", BLEWrite | BLERead, 100);



#include <WiFi.h>
#include <WiFiUdp.h>

bool bleActive = false;
bool useTCP = false;
const int tcpServerPort = 9334;
WiFiServer tcpServer(tcpServerPort);  // Port number for incoming connections

extern void trigger_alarm();
extern void warning_beep();
extern void alarm_stoppped_confirmed();
static void button_short_press(bool pressed, bool released);

extern bool do_not_alarm;
extern bool do_not_beep;

WiFiUDP udp;                                 // Define UDP object
WiFiUDP read_udp;                            // Define UDP object
IPAddress multicastAddress(239, 255, 0, 1);  // Multicast address
unsigned int multicastPort = 4000;           // Multicast port
unsigned int multicastReadPort = 4001;


WiFiClient tcpClient;


void send_tcp_bytes(const uint8_t* data, size_t len) {
  if (tcpClient && tcpClient.connected()) {
    tcpClient.write(data, len);
  }
}

void send_udp_bytes(const uint8_t* data, size_t len) {
  udp.beginPacket(multicastAddress, multicastPort);
  udp.write(data, len);
  udp.endPacket();
}

void send_bytes(const uint8_t* data, size_t len) {
  if (useTCP) {
    send_tcp_bytes(data, len);
  } else {
    send_udp_bytes(data, len);
  }
}


void send_elements_batch(data_element* d) {
  send_bytes((uint8_t*)d, sizeof(data_element) * elements_size);
}

void send_parameters_udp() {
  uint8_t payload[4];

  payload[0] = lowByte(samplingFrequency);
  payload[1] = highByte(samplingFrequency);
  payload[2] = lowByte(samples);
  payload[3] = highByte(samples);

  send_bytes(payload, sizeof(payload));
}

// 11 bytes: 1 + 4 + 1 + 4 + 1
void send_evaluation_result(float result, bool classification) {
  uint8_t payload[11];

  payload[0] = EVALUATION_RESULT;

  memcpy(payload + 1, &result, 4);  // float
  payload[5] = classification;

  memcpy(payload + 6, &classification_threshold, 4);  // float
  payload[10] = classification;

  send_bytes(payload, sizeof(payload));
}

void send_element(data_element* d) {
  send_bytes((uint8_t*)(d), 5);
}

void send_event(uint8_t event) {
  send_bytes(&event, 1);
}

uint8_t cc = 200;
void send_to_udp() {
  if (++cc == 0)
    send_parameters_udp();

  send_bytes((uint8_t*)(vReal), network_send_bytes);
}

void send_version() {
  uint8_t payload[3];
  payload[0] = CHECK_VERSION;
  payload[1] = lowByte(VersionIncremental);
  payload[2] = highByte(VersionIncremental);

  send_bytes(payload, sizeof(payload));
}


void received_packet(char* packetBuffer, int len) {
  Serial.print("Read ");
  Serial.print((int)len);
  Serial.print(": ");
  Serial.println((int)packetBuffer[0]);

  if (len > 0) {
    packetBuffer[len] = 0;  // Null-terminate the received string
  }
  if (len == 1) {
    switch (packetBuffer[0]) {
      case BEEP:
        warning_beep();
        break;

      case ALARM_START:
        trigger_alarm();
        // Confirm alarm action received via UDP by responding with 1 byte of value 2
        send_event(UDP_ALARM_CONFIRMED);
        break;

      case USING_ANDROID:
        if (!is_using_android) {
          tone(BUZZER, Notes::E6, Notes::DottedEighth / 2);
          send_event(USING_ANDROID);
        }
        is_using_android = true;
        break;

      case UDP_ALARM_CONFIRMED:
        need_alarm_confirmation = false;
        break;

      case BUTTON_PRESS:
        button_short_press(true, false);
        break;

      case DO_NOT_BEEP_ARDUINO:
        do_not_beep_if_android = true;
        break;

      case DO_NOT_BEEP:
        do_not_beep = true;
        send_event(DO_NOT_BEEP);
        break;

      case DO_NOT_ALARM:
        do_not_alarm = true;
        send_event(DO_NOT_ALARM);
        break;

      case ALARM_ARDUINO_EVEN_WITH_ANDROID:
        if (!alarm_even_with_android) {
          tone(BUZZER, Notes::C6, Notes::DottedEighth / 4);
          delay(50);
          tone(BUZZER, Notes::D6, Notes::DottedEighth / 4);
          delay(50);
          tone(BUZZER, Notes::E6, Notes::DottedEighth / 4);
          delay(50);
          tone(BUZZER, Notes::F6, Notes::DottedEighth / 4);
        }
        alarm_even_with_android = true;
        break;

      case CHECK_VERSION:
        send_version();
        break;

      case CONFIRM_ANDROID_ALARM_STOPPED:
        alarm_stoppped_confirmed();
        break;
    }
  }
  if (len == 3) {
    if (packetBuffer[0] == SET_EVALUATION_THRESHOLD) {
      classification_threshold = (uint8_t)packetBuffer[1] | ((uint8_t)packetBuffer[2] << 8);
    }
  }
}

void setup_wifi() {
  BLE.begin();

  configService.addCharacteristic(wifiChar);
  BLE.setLocalName("BruxismDetector");
  BLE.setAdvertisedService(configService);
  BLE.addService(configService);
  BLE.advertise();
  bleActive = true;

  bool connection_comes_from_BLE = false;

  WiFi.begin(ssid, password);
  uint8_t count = 1;

  while (WiFi.status() != WL_CONNECTED) {
    delay(100);
    BLE.poll();

    if (wifiChar.written()) {
      String config = String((const char*)wifiChar.value());

      int sep = config.indexOf('\"');

      Serial.print("Received from BLE: ");
      Serial.println(config);


      if (sep > 0) {
        String newSSID = config.substring(0, sep);
        String newPASS = config.substring(sep + 1);

        connection_comes_from_BLE = true;

        WiFi.disconnect();
        WiFi.begin(newSSID.c_str(), newPASS.c_str());
        count = 1;
        Serial.println("Received new WiFi credentials via BLE");
      }
    }

    if (count++ == 0) {
      NVIC_SystemReset();
    }
  }





  if (connection_comes_from_BLE) {
    useTCP = true;  // If BLE was used to configure, prefer TCP

    // Wait for a valid IP address
    IPAddress ip;
    do {
      delay(10);
      ip = WiFi.localIP();
    } while (ip[0] == 0);

    // Format IP address string
    char ipStr[16];  // 15 characters max for IPv4 + 1 for null terminator
    snprintf(ipStr, sizeof(ipStr), "%u.%u.%u.%u", ip[0], ip[1], ip[2], ip[3]);

    // Send IP back via BLE
    wifiChar.writeValue(ipStr);
    delay(500);
  }


  if (bleActive) {
    BLE.stopAdvertise();
    BLE.disconnect();
    BLE.end();
    bleActive = false;
  }

  if (useTCP) {
    tcpServer.begin();


  } else {
    udp.beginMulticast(multicastAddress, multicastPort);
    read_udp.beginMulticast(multicastAddress, multicastReadPort);
  }
}


inline void loop_wifi() {

  char packetBuffer[255];  // Buffer to store incoming messages
  int len = 0;

  if (useTCP) {


    // Accept new connection if needed
    if (!tcpClient || !tcpClient.connected()) {
      tcpClient = tcpServer.available();
    }

    if (tcpClient && tcpClient.connected() && tcpClient.available()) {
      int len = tcpClient.read((uint8_t*)packetBuffer, sizeof(packetBuffer));
      if (len > 0) {
        // Store it into a ring buffer or process minimal header
        received_packet(packetBuffer, len);  // fast handling
      }
    }

  } else {


    int packetSize = read_udp.parsePacket();
    if (packetSize) {
      len = read_udp.read(packetBuffer, sizeof(packetBuffer) - 1);
      received_packet(packetBuffer, len);
    }
  }
}