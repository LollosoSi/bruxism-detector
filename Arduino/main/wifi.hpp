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

  memcpy(payload + 6, &eeprom_config.classification_threshold, 4);  // float
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
    packetBuffer[len] = 0;

    if (packetBuffer[0] == SAVE_WEIGHTS) {
      // Calcola la lunghezza esatta attesa in modo dinamico
      size_t expected_len = 1 + sizeof(eeprom_config.bias) + sizeof(eeprom_config.classification_threshold) + sizeof(eeprom_config.weights);
      
      if (len == expected_len) {
        
        // 1. Extract bias
        memcpy(&eeprom_config.bias, &packetBuffer[1], sizeof(eeprom_config.bias));
        
        // 2. Extract threshold (1 + 4 bytes)
        memcpy(&eeprom_config.classification_threshold, &packetBuffer[1 + sizeof(eeprom_config.bias)], sizeof(eeprom_config.classification_threshold));
        
        // 3. Extract weights (1 + 4 bias + 4 threshold)
        memcpy(eeprom_config.weights, &packetBuffer[1 + sizeof(eeprom_config.bias) + sizeof(eeprom_config.classification_threshold)], sizeof(eeprom_config.weights));

        // Print
        Serial.println("\n--- Received Weights via UDP ---");
        Serial.print("Bias: ");
        Serial.println(eeprom_config.bias, 8);
        Serial.print("Threshold: ");
        Serial.println(eeprom_config.classification_threshold);
        Serial.println("Weights: ");
        for(int i = 0; i < weight_length; i++) {
          Serial.print(eeprom_config.weights[i], 8);
          Serial.print(" ");

          if ((i + 1) % 4 == 0) Serial.println(); 
        }
        Serial.println("-----------------------------------\n");

        // 4. Save to EEPROM
        save_config();

        // 5. Restart (optional, but let's do it)
        NVIC_SystemReset();
        
      } else {
        Serial.print("Received SAVE_WEIGHTS, but wrong length. Expected: ");
        Serial.print(expected_len);
        Serial.print(" Received: ");
        Serial.println(len);
      }
    } else if (packetBuffer[0] == SAVE_WIFI) {
      
      // Estrapoliamo la stringa partendo dal secondo byte (indice 1)
      String config = String(&packetBuffer[1]);
      
      int sep = config.indexOf('\"');

      if (sep > 0) {
        String newSSID = config.substring(0, sep);
        String newPASS = config.substring(sep + 1);

        // Chiamiamo la funzione che salva permanentemente in EEPROM
        save_wifi_ssidpassword(newSSID, newPASS);
        
        Serial.print("New WiFi credentials saved! SSID: ");
        Serial.println(newSSID);

        NVIC_SystemReset();
      } else {
        Serial.println("Error: invalid WiFi format.");
      }
    }
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
      int reception = (uint8_t)packetBuffer[1] | ((uint8_t)packetBuffer[2] << 8);
      if(reception != eeprom_config.classification_threshold){
        eeprom_config.classification_threshold = reception;

        // Save to EEPROM
        save_config();
      }
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

  WiFi.begin(eeprom_config.ssid, eeprom_config.password);
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

        // Not sure if I want to save credentials for a one-time TCP session. I'd rather reset it as tcp-udp packet.
        //save_wifi_ssidpassword(newSSID, newPASS);

      }
    }

    if (count++ == 0) {
      // Reset
      NVIC_SystemReset();
    }
  }





if (connection_comes_from_BLE) {
    useTCP = true;  // If BLE was used to configure, prefer TCP

    // Attendi l'assegnazione dell'indirizzo IP
    IPAddress ip;
    do {
      delay(10);
      ip = WiFi.localIP();
    } while (ip[0] == 0);

    // Formatta la stringa IP
    char ipStr[16];
    snprintf(ipStr, sizeof(ipStr), "%u.%u.%u.%u", ip[0], ip[1], ip[2], ip[3]);

    // Scrivi l'IP nella caratteristica BLE
    wifiChar.writeValue(ipStr);
    Serial.print("BLE: IP caricato sulla caratteristica: ");
    Serial.println(ipStr);

    // Attendi che l'app Android legga l'IP e chiuda la connessione BLE (max 4 secondi)
    unsigned long bleStartWait = millis();
    while (BLE.connected() && (millis() - bleStartWait < 4000)) {
      BLE.poll();
      delay(50);
    }
  }

  if (bleActive) {
    BLE.stopAdvertise();
    BLE.disconnect();
    BLE.end();
    bleActive = false;
  }

  if (useTCP) {
    tcpServer.begin();
    Serial.println("Server TCP avviato sulla porta 9334");
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