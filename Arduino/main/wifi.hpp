#pragma once

#include "user/WifiSettings.h"
#include "user/Settings.h"
#include "runtime_variables.hpp"

#include <WiFi.h>
#include <WiFiUdp.h>

extern void trigger_alarm();
extern void warning_beep();

WiFiUDP udp;                                 // Define UDP object
WiFiUDP read_udp;                            // Define UDP object
IPAddress multicastAddress(239, 255, 0, 1);  // Multicast address
unsigned int multicastPort = 4000;           // Multicast port
unsigned int multicastReadPort = 4001;

void send_elements_batch(data_element* d) {
  udp.beginPacket(multicastAddress, multicastPort);
  udp.write((uint8_t*)d, sizeof(data_element) * elements_size);  // Send the chunk of data
  udp.endPacket();
}

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
      }
    }
  }
}

void setup_wifi() {

  // Wi-Fi code for Arduino Uno R4 WiFi
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(100);
  }

  udp.beginMulticast(multicastAddress, multicastPort);
  read_udp.beginMulticast(multicastAddress, multicastReadPort);
}

inline void loop_wifi() {
  read_from_udp();
}