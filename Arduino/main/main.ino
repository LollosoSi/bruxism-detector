
// Comment this line for the final binary (memory saving)
// #define DEBUG_MODE

#ifdef DEBUG_MODE
#define DEBUG_PRINT(...) Serial.print(__VA_ARGS__)
#define DEBUG_PRINTLN(...) Serial.println(__VA_ARGS__)
#else
#define DEBUG_PRINT(...)
#define DEBUG_PRINTLN(...)
#endif


#include <Arduino.h>

#include "user/WifiSettings.h"
#include "user/Settings.h"

#include "eeprom.hpp"

#include "runtime_variables.hpp"
#include "wifi.hpp"
#include "fft.hpp"
#include "logic.hpp"


void setup() {




  
  if(digitalRead(13)){
    tone(BUZZER, Notes::B4, Notes::DottedEighth / 2);
    delay(Notes::DottedEighth);
    tone(BUZZER, Notes::B4, Notes::DottedEighth / 2);
    delay(Notes::DottedEighth);
    tone(BUZZER, Notes::B4, Notes::DottedEighth / 2);
    delay(Notes::DottedEighth);
    tone(BUZZER, Notes::B4, Notes::DottedEighth / 2);
    delay(Notes::DottedEighth);
    tone(BUZZER, Notes::B4, Notes::DottedEighth / 2);
    delay(Notes::DottedEighth);
  }
  pinMode(13, OUTPUT);
  digitalWrite(13, 0);


  #ifdef DEBUG_MODE
  Serial.begin(500000);
#endif

  tone(BUZZER, Notes::C6, Notes::DottedEighth / 2);
  delay(Notes::DottedEighth);
  tone(BUZZER, Notes::E6, Notes::DottedEighth / 2);
  delay(Notes::DottedEighth);
  tone(BUZZER, Notes::G6, Notes::DottedEighth / 2);
  delay(Notes::Half);

  load_config();

  DEBUG_PRINTLN("\n--- Loading Saved Weights ---");
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

  setup_logic();
  setup_fft();
  setup_wifi();

  reset_tune();

  tone(BUZZER, Notes::C7, Notes::DottedEighth / 2);
}

unsigned long last_wifi_check = 0;
const unsigned long wifi_check_interval = 50;  // Spaced WiFi checks

void loop() {

  //if (!enable_update_poll) {
    unsigned long now = millis();
    loop_fft();
    if (now - last_wifi_check > wifi_check_interval) {
      last_wifi_check = now;
      loop_wifi(now);
    }
    loop_logic(now);

    // Sleep renesas. wakes every 1ms, just in time for 1000hz sampling
    __WFI();
  //} else {
    //ArduinoOTA.poll();
  //}
}
