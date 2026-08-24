#include <Arduino.h>

#include "user/WifiSettings.h"
#include "user/Settings.h"

#include "eeprom.hpp"

#include "runtime_variables.hpp"
#include "wifi.hpp"
#include "fft.hpp"
#include "logic.hpp"


void setup() {

  Serial.begin(500000);

  

  tone(BUZZER, Notes::C6, Notes::DottedEighth / 2);
  delay(Notes::DottedEighth);
  tone(BUZZER, Notes::E6, Notes::DottedEighth / 2);
  delay(Notes::DottedEighth);
  tone(BUZZER, Notes::G6, Notes::DottedEighth / 2);
  delay(Notes::Half);

  load_config();

  Serial.println("\n--- Loading Saved Weights ---");
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

  setup_logic();
  setup_fft();
  setup_wifi();

  reset_tune();

  tone(BUZZER, Notes::C7, Notes::DottedEighth / 2);
}

unsigned long last_wifi_check = 0;
const unsigned long wifi_check_interval = 50; // Spaced WiFi checks

void loop() {
  unsigned long now = millis();

  loop_fft();
  if (now - last_wifi_check > wifi_check_interval){
    last_wifi_check = now;
    loop_wifi();
  }
  loop_logic();

  // Sleep renesas. wakes every 1ms, just in time for 1000hz sampling
  __WFI();
}
