#pragma once
// Uses debounce by Aaron Kimball
#include <debounce.h>
#include "user/WifiSettings.h"
#include "user/Settings.h"
#include "runtime_variables.hpp"

#include <WiFi.h>
#include <WiFiUdp.h>

extern void trigger_alarm();
extern void warning_beep();
static void button_short_press(uint8_t btnId, uint8_t btnState);

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

// Does not conflict as it's not multiple of 5 bytes and does not have reserved sizes (1, 4, n%5) bytes (total 11 bytes)
void send_evaluation_result(float result, bool classification){
  udp.beginPacket(multicastAddress, multicastPort);
  udp.write(EVALUATION_RESULT);
  // Send result as float (Little-Endian)
  byte* floatBytes = (byte*) &result;
  for (int i = 0; i < 4; i++) {
     udp.write(floatBytes[i]);
  }

  udp.write(classification);

  byte* intBytes = (byte*) &classification_threshold;
  for (int i = 0; i < 4; i++) {
      udp.write(intBytes[i]); // sends in little-endian order
  }

  udp.write(classification);

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

        case USING_ANDROID:
          is_using_android = true;
          tone(BUZZER, Notes::E6, Notes::DottedEighth / 2);
          send_event(USING_ANDROID);
          break;

        case UDP_ALARM_CONFIRMED:
          need_alarm_confirmation = false;
          break;

        case BUTTON_PRESS:
          button_short_press(0, BTN_PRESSED);
          break;
      }
    }
    if(len==3){
      if(packetBuffer[0]==SET_EVALUATION_THRESHOLD){
        classification_threshold = (uint8_t)packetBuffer[1] | ((uint8_t)packetBuffer[2] << 8);
      }
    }

  }
}

void setup_wifi() {

  // Wi-Fi code for Arduino Uno R4 WiFi
  WiFi.begin(ssid, password);

  uint8_t count = 1;

  while (WiFi.status() != WL_CONNECTED) {
    delay(30);
    if (count++ == 0) {
      NVIC_SystemReset();
    }
  }

  udp.beginMulticast(multicastAddress, multicastPort);
  read_udp.beginMulticast(multicastAddress, multicastReadPort);
}

inline void loop_wifi() {
  read_from_udp();
}