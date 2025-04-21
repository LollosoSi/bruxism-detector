#pragma once

#include "user/WifiSettings.h"
#include "user/Settings.h"

// Network Elements

bool stream_FFT = false;

// Apply packing to ensure there is no padding between struct members
#pragma pack(push, 1)  // Start packing (1-byte alignment)
struct data_element {
  unsigned long timestamp;  // 4 bytes
  bool value;               // 1 byte
};
#pragma pack(pop)  // Restore default packing alignment

unsigned int elements_cursor = 0;
data_element data_elements[elements_size];


const uint8_t CLENCH_START = 0;
const uint8_t CLENCH_STOP = 1;
const uint8_t BUTTON_PRESS = 2;
const uint8_t ALARM_START = 3;
const uint8_t BEEP = 4;
const uint8_t UDP_ALARM_CONFIRMED = 5;
const uint8_t DETECTED = 6;
const uint8_t CONTINUED = 7;
const uint8_t ALARM_STOP = 8;
const uint8_t TRACKING_STOP = 9;
const uint8_t USING_ANDROID = 10;
const uint8_t EVALUATION_RESULT = 11;
const uint8_t SET_EVALUATION_THRESHOLD = 12;
const uint8_t DO_NOT_BEEP_ARDUINO = 13;

bool is_using_android = false;
bool do_not_beep_if_android = false;
bool need_alarm_confirmation = false;


// ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~

// FFT elements

const float freq_bin = samplingFrequency / samples;
float vReal[samples];
float hammingWindow[samples];  // Hamming window coefficients
int network_send_bytes = (samples / 2) * sizeof(float);


// ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~

// Tunes elements

uint8_t num_tunes = sizeof(tunes) / sizeof(tune*);
int tone_sel = tunes[playtune]->tone_num - 1;
uint8_t replays = 0;

void reset_tune(bool change = false) {
  if (rotate_tunes || change) {
    playtune = millis() % num_tunes;
  }
  tone_sel = tunes[playtune]->tone_num - 1;
  replays = 0;
}


// ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~