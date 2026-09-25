#pragma once

#include <EEPROM.h>
#include <string.h>

struct AppConfig {
  uint16_t magic_number;       // Signature to find out if EEPROM is formatted
  
  // WiFi
  char ssid[32];
  char password[64];

  // SVM Model
  float bias;
  int classification_threshold;
  float weights[weight_length];
};

// Global config
AppConfig eeprom_config;

void save_config() {
  // EEPROM.put saves the entire struct
  // on UNO R4, only changed bytes are written to memory, preserving the lifespan
  EEPROM.put(0, eeprom_config);
  DEBUG_PRINTLN("Saved to EEPROM");

  tone(BUZZER, Notes::G5, Notes::DottedEighth / 2);
  delay(Notes::DottedEighth/2);
  tone(BUZZER, Notes::B5, Notes::DottedEighth / 2);
  delay(Notes::DottedEighth/2);
  tone(BUZZER, Notes::B5, Notes::DottedEighth / 2);
  delay(Notes::DottedEighth/2);
  tone(BUZZER, Notes::D6, Notes::DottedEighth / 2);
}

const uint16_t VALID_MAGIC_NUMBER = 0xA1B2; // Any hex

void load_config(bool force_update = false) {
  // EEPROM.get reads the entire memory block and fills the struct
  EEPROM.get(0, eeprom_config);

  // Check for first power on or corrupted data
  if (eeprom_config.magic_number != VALID_MAGIC_NUMBER || force_update) {
    DEBUG_PRINTLN("Updating EEPROM");
    
    // 1. Initialize defaults
    eeprom_config.magic_number = VALID_MAGIC_NUMBER;
    strncpy(eeprom_config.ssid, ssid, sizeof(eeprom_config.ssid) - 1);
    eeprom_config.ssid[sizeof(eeprom_config.ssid) - 1] = '\0';
    
    strncpy(eeprom_config.password, password, sizeof(eeprom_config.password) - 1);
    eeprom_config.password[sizeof(eeprom_config.password) - 1] = '\0';
    
    eeprom_config.bias = bias;
    eeprom_config.classification_threshold = classification_threshold;
    memcpy(eeprom_config.weights, weights, sizeof(weights));
    
    // 2. Save to EEPROM
    save_config();
  } else {
    DEBUG_PRINTLN("EEPROM configuration read.");
  }

  if(!use_eeprom_for_svm){
    eeprom_config.bias = bias;
    eeprom_config.classification_threshold = classification_threshold;
    memcpy(eeprom_config.weights, weights, sizeof(weights));
  }

  if(!use_eeprom_for_wifi){
    strncpy(eeprom_config.ssid, ssid, sizeof(eeprom_config.ssid) - 1);
    eeprom_config.ssid[sizeof(eeprom_config.ssid) - 1] = '\0';
    
    strncpy(eeprom_config.password, password, sizeof(eeprom_config.password) - 1);
    eeprom_config.password[sizeof(eeprom_config.password) - 1] = '\0';
  }
}

// Aggiornato per accettare const char* ed evitare allocazioni con la classe String
void save_wifi_ssidpassword(const char* s_ssid, const char* s_password) {
  strncpy(eeprom_config.ssid, s_ssid, sizeof(eeprom_config.ssid) - 1);
  eeprom_config.ssid[sizeof(eeprom_config.ssid) - 1] = '\0';

  strncpy(eeprom_config.password, s_password, sizeof(eeprom_config.password) - 1);
  eeprom_config.password[sizeof(eeprom_config.password) - 1] = '\0';

  // Salva in EEPROM
  save_config();
}