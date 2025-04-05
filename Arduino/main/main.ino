#include <Arduino.h>

#include "user/WifiSettings.h"
#include "user/Settings.h"

#include "runtime_variables.hpp"
#include "wifi.hpp"
#include "fft.hpp"
#include "logic.hpp"


void setup() {

  Serial.begin(500000);

  tone(BUZZER, Notes::C6, Notes::DottedEighth/2);
  delay(Notes::DottedEighth);
  tone(BUZZER, Notes::E6, Notes::DottedEighth/2);
  delay(Notes::DottedEighth);
  tone(BUZZER, Notes::G6, Notes::DottedEighth/2);
  delay(Notes::Half);

  setup_logic();
  setup_fft();
  setup_wifi();

  reset_tune();

  tone(BUZZER, Notes::C7, Notes::DottedEighth/2);
}

void loop() {
  loop_fft();
  loop_wifi();
  loop_logic();
}
