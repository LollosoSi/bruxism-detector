#pragma once

#include "user/WifiSettings.h"
#include "user/Settings.h"
#include "runtime_variables.hpp"

#include "version.h"

#include <ArduinoBLE.h>


BLEService configService(PROGMEM("12345678-1234-5678-1234-56789abcdef0"));
BLECharacteristic wifiChar(PROGMEM("abcdefab-1234-5678-1234-56789abcdef0"), BLEWrite | BLERead, 80);



#include <WiFi.h>
#include <WiFiUdp.h>

//#include <ArduinoOTA.h>

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

WiFiUDP udp;                                     // Define UDP object
WiFiUDP read_udp;                                // Define UDP object
IPAddress broadcastAddress(255, 255, 255, 255);  // Indirizzo di Broadcast Globale
unsigned int multicastPort = 4000;               // Multicast port
unsigned int multicastReadPort = 4001;


WiFiClient tcpClient;


void send_tcp_bytes(const uint8_t* data, size_t len) {
  if (tcpClient && tcpClient.connected()) {
    tcpClient.write(data, len);
  }
}

void send_udp_bytes(const uint8_t* data, size_t len) {
  udp.beginPacket(broadcastAddress, multicastPort);
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

// Sends wifi strength
void send_wifi_rssi() {
  int8_t rssi_val = (int8_t)WiFi.RSSI();
  uint8_t payload[2];

  payload[0] = RSSI_WIFI;
  payload[1] = (uint8_t)rssi_val;  // Safe 1 byte cast

  send_bytes(payload, sizeof(payload));
}

void send_grace_state() {
  uint8_t payload[2];

  payload[0] = GRACE_ACTIVE;
  payload[1] = grace_left_seconds;

  send_bytes(payload, sizeof(payload));
}

void reset_grace_period() {
  ultimoBottone = millis();
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


// Needs Crypto by Rhys Weatherley
#include <SHA256.h>

void send_device_uuid() {
  uint8_t mac[6];
  WiFi.macAddress(mac);  // Recupera il MAC address

  // Converte il MAC in una stringa formattata
  char macStr[18];
  snprintf(macStr, sizeof(macStr), "%02X:%02X:%02X:%02X:%02X:%02X",
           mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);

  // Inizializza l'algoritmo SHA256
  SHA256 sha256;
  uint8_t hashResult[32];  // SHA-256 produce un output di 32 byte

  sha256.reset();
  sha256.update(macStr, strlen(macStr));
  sha256.finalize(hashResult, sizeof(hashResult));

  // Formattiamo i primi 16 byte dell'hash come stringa esadecimale (32 caratteri = formato UUID senza trattini)
  char uuidHex[33];  // 32 caratteri + terminatore null
  for (int i = 0; i < 16; i++) {
    sprintf(&uuidHex[i * 2], "%02x", hashResult[i]);
  }

  // Costruiamo il payload da inviare ad Android
  // 1 byte (Comando REQUEST_UUID = 23) + 32 byte (Stringa UUID)
  uint8_t payload[33];
  payload[0] = REQUEST_UUID;
  memcpy(payload + 1, uuidHex, 32);

  // Invia i dati usando il metodo di connessione attivo (TCP o UDP)
  send_bytes(payload, sizeof(payload));
}


void received_packet(char* packetBuffer, int len) {
  DEBUG_PRINT("Read ");
  DEBUG_PRINT((int)len);
  DEBUG_PRINT(": ");
  DEBUG_PRINTLN((int)packetBuffer[0]);

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
        DEBUG_PRINTLN("\n--- Received Weights via UDP ---");
        DEBUG_PRINT("Bias: ");
        DEBUG_PRINTLN(eeprom_config.bias, 8);
        DEBUG_PRINT("Threshold: ");
        DEBUG_PRINTLN(eeprom_config.classification_threshold);
        DEBUG_PRINTLN("Weights: ");
        for (int i = 0; i < weight_length; i++) {
          DEBUG_PRINT(eeprom_config.weights[i], 8);
          DEBUG_PRINT(" ");

          if ((i + 1) % 4 == 0) DEBUG_PRINTLN();
        }
        DEBUG_PRINTLN("-----------------------------------\n");

        // 4. Save to EEPROM
        save_config();

        // 5. Restart (optional, but let's do it)
        NVIC_SystemReset();

      } else {
        DEBUG_PRINT("Received SAVE_WEIGHTS, but wrong length. Expected: ");
        DEBUG_PRINT(expected_len);
        DEBUG_PRINT(" Received: ");
        DEBUG_PRINTLN(len);
      }
    } else if (packetBuffer[0] == SAVE_WIFI) {
      // Assicuriamoci che il buffer sia terminato correttamente
      packetBuffer[len] = '\0';
      char* config = &packetBuffer[1];

      // Cerchiamo la posizione del carattere separatore '\"'
      char* sep = strchr(config, '\"');

      if (sep != nullptr) {
        // Sostituiamo temporaneamente le virgolette con il terminatore di stringa '\0'
        // In questo modo 'config' diventa direttamente la stringa SSID pulita!
        *sep = '\0';
        char* newSSID = config;
        char* newPASS = sep + 1;

        // Salviamo in EEPROM usando le funzioni che accettano char* o String temporanee
        save_wifi_ssidpassword(newSSID, newPASS);

        DEBUG_PRINT("New WiFi credentials saved! SSID: ");
        DEBUG_PRINTLN(newSSID);

        NVIC_SystemReset();
      } else {
        DEBUG_PRINTLN("Error: invalid WiFi format.");
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

      case GRACE_ACTIVE:
        reset_grace_period();
        break;

      case REQUEST_UUID:
        // Generate and respond with UUID
        send_device_uuid();
        break;

      case ENABLE_UPDATE:
        tone(BUZZER, Notes::F6, Notes::DottedEighth / 4);
        // 1. Chiudiamo esplicitamente i socket UDP/TCP di normale esercizio per liberare l'hardware di rete dell'ESP32
        udp.stop();
        read_udp.stop();
        if (useTCP && tcpClient) {
          tcpClient.stop();
          tcpServer.end();
        }

        digitalWrite(13, 1);

        // 2. Breve pausa per permettere all'ESP32 di rilasciare i socket
        delay(100);

        // 3. Avviamo l'OTA in modo pulito
        //ArduinoOTA.begin(WiFi.localIP(), "BruxismDetector", "", InternalStorage);

        // 4. Attiviamo il flag che congela tutto il resto nel loop principale
        enable_update_poll = true;

        // 5. Un singolo beep rapido e non bloccante (o saltalo del tutto se dà fastidio)
        DEBUG_PRINTLN("Modalità OTA attivata. In attesa di upload...");
        break;
    }
  }
  if (len == 3) {
    if (packetBuffer[0] == SET_EVALUATION_THRESHOLD) {
      int reception = (uint8_t)packetBuffer[1] | ((uint8_t)packetBuffer[2] << 8);
      if (reception != eeprom_config.classification_threshold) {
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
      const char* config = (const char*)wifiChar.value();
      bool shouldSave = false;

      if (strncmp(config, "!S!", 3) == 0) {
        shouldSave = true;
        config += 3;  // Salta il prefisso
      }

      char configCopy[100];
      strncpy(configCopy, config, sizeof(configCopy) - 1);
      configCopy[sizeof(configCopy) - 1] = '\0';

      char* sep = strchr(configCopy, '\"');
      if (sep != nullptr) {
        *sep = '\0';
        char* newSSID = configCopy;
        char* newPASS = sep + 1;

        if (shouldSave) {
          save_wifi_ssidpassword(newSSID, newPASS);
          NVIC_SystemReset();
        }

        DEBUG_PRINT("Received from BLE: ");
        DEBUG_PRINTLN(config);

        if (sep > 0) {


          if (shouldSave) {
            save_wifi_ssidpassword(newSSID, newPASS);
            DEBUG_PRINTLN("WiFi saved via BLE. Rebooting...");

            NVIC_SystemReset();  // Reset as per UDP
          }

          // If !S! is not present : TCP mode
          connection_comes_from_BLE = true;
          WiFi.disconnect();
          WiFi.begin(newSSID, newPASS);
          count = 1;
          DEBUG_PRINTLN("TCP mode");
        }
      }
    }


    if (count++ == 0) {
      // Reset
      NVIC_SystemReset();
    }
  }





  if (connection_comes_from_BLE) {
    useTCP = true;  // If BLE was used to configure, prefer TCP

    // Wait to have an address assigned
    IPAddress ip;
    do {
      delay(10);
      ip = WiFi.localIP();
    } while (ip[0] == 0);

    // Format IP string
    char ipStr[16];
    snprintf(ipStr, sizeof(ipStr), "%u.%u.%u.%u", ip[0], ip[1], ip[2], ip[3]);

    // Write IP as BLE characteristic
    wifiChar.writeValue(ipStr);
    DEBUG_PRINT("BLE: IP sent: ");
    DEBUG_PRINTLN(ipStr);

    // Wait for android to read and close BLE (max 4 seconds)
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
    DEBUG_PRINTLN("TCP server started on port 9334");
  } else {
    udp.begin(multicastPort);
    read_udp.begin(multicastReadPort);
  }

  send_wifi_rssi();
}


unsigned long last_send_rssi = 10000;           // First send after at least 10 seconds
const unsigned long rssi_send_interval = 3000;  // Periodically send RSSI

inline void loop_wifi(unsigned long& now) {

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

  if (now - last_send_rssi > (grace_left_seconds == 0 ? rssi_send_interval : 1000)) {
    last_send_rssi = now;
    send_wifi_rssi();
    send_grace_state();
  }
}