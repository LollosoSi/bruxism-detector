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
  Serial.println("Saved to EEPROM");

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
    Serial.println("Updating EEPROM");
    
    // 1. Initialize defaults
    eeprom_config.magic_number = VALID_MAGIC_NUMBER;
    strcpy(eeprom_config.ssid, ssid);
    strcpy(eeprom_config.password, password);
    
    eeprom_config.bias = bias;
    eeprom_config.classification_threshold = classification_threshold;
    memcpy(eeprom_config.weights, weights, sizeof(weights));
    
    // 2. Save to EEPROM
    save_config();
  } else {
    Serial.println("EEPROM configuration read.");
  }

  if(!use_eeprom_for_svm){
    eeprom_config.bias = bias;
    eeprom_config.classification_threshold = classification_threshold;
    memcpy(eeprom_config.weights, weights, sizeof(weights));
  }

  if(!use_eeprom_for_wifi){
    strcpy(eeprom_config.ssid, ssid);
    strcpy(eeprom_config.password, password);
  }

}

void save_wifi_ssidpassword(String s_ssid, String s_password){
  strcpy(eeprom_config.ssid, s_ssid.c_str());
  strcpy(eeprom_config.password, s_password.c_str());

  // 2. Save
  save_config();
}

