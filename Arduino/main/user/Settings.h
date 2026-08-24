#pragma once

#include "Notes.h"

// ANDROID

static constexpr long android_alarm_timeout = 10000;

// ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~

// GPIO

static constexpr int analog_pin = A0;  // Set the correct analog input pin
static constexpr int BUTTON = 3;
static constexpr int BUZZER = 5;

// ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~

// TUNES

// Uncomment if you want to start/stop a tune by pressing the button once
//#define TESTING_TONES

uint8_t playtune = 0;            // Which tune you want to start with? (see tunes[] array)
bool rotate_tunes = false;       // Randomly pick a tune (will rotate anyway after max_replays loops, the tune failed at waking you up!)
const uint8_t max_replays = 10;  // How many loops of the same tune before rotating

struct tune {
  int tone_num = 1;            // How many notes in the array?
  unsigned int tones[30];      // Frequencies in Hz
  unsigned int durations[30];  // Duration of each note in milliseconds
  unsigned int waits[30];      // Delay between notes in milliseconds
};

namespace Notes {

// NOTICE: All tunes are unofficial and intended to be used as examples. Written by ear
tune drier{
  4,
  { G6, A6, B6, C7 },
  { Quarter, Quarter, Quarter, Half },
  { Eighth, Eighth, Eighth, Whole*4 }
};

tune samsung{
  5,
  { C5, G5, C6, B5, G5 },
  { Quarter, Quarter, Quarter, Quarter, Quarter },
  { DottedQuarter, Eighth, Eighth, DottedQuarter, Half }
};

tune apple{
  13,
  { 1568, 1568, 1865, 1047, 1047, 1865, 1568, 1047, 1397, 1047, 1865, 1047, 1568 },  // Frequencies in Hz
  { 300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300 },               // Duration of each note in milliseconds
  { 400, 200, 200, 200, 300, 200, 100, 100, 200, 200, 200, 200, 2000 }               // Delay between notes in milliseconds
};

tune zerb{
  20,
  { F6, Gs6, F6, Gs6, F6, As6, Cs7, As6, Cs7, As6, F6, Gs6, F6, Gs6, F6, F6, Gs6, As6, C7, F6 }, // F6, G#6, F6, G#6, F6, A#6, C#7, A#6, C#7, A#6
  { Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter, Quarter },
  { Quarter, Quarter, Quarter, Quarter, Half, Quarter, Quarter, Quarter, Quarter, Half, Quarter, Quarter, Quarter, Quarter, Half, Quarter, Quarter, Quarter, Quarter, Whole }
};

// End of Notes namespace
}

tune* tunes[] = { &Notes::drier, &Notes::samsung, &Notes::apple, &Notes::zerb };

// ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~

// Beeps and alarms

const unsigned long attesaFiltraggio = 1000;  // 1000ms per ignorare falsi positivi
const unsigned long attesaPrimoBeep = 500;    // 1s prima del primo beep
const unsigned long attesaBeep = 2000;        // 3s tra un beep e l'altro
const int numeroMaxBeep = 4;                  // Numero massimo di beep prima dell'allarme
const unsigned long periodoAttesa = 8000;     // 2s per considerare interrotto l'evento
const unsigned long periodoGrazia = 60000;    // 60s di grazia per dare tempo di mettersi a letto

const unsigned long filtraggioIniziale = 100;
const uint8_t campioniFiltraggio = 6;

int warning_beep_duration = 100;
int warning_beep_wait = 50;

// ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~

// FFT Settings

const uint16_t samples = 64;              // Must be a power of 2
const uint16_t samplingFrequency = 1000;  // Adjust as needed
const unsigned long sample_interval_us = 1000000 / samplingFrequency;

// ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~

// SVM Settings (now in EEPROM)

bool use_eeprom_for_svm = true; // Set to false to set classification_threshold, bias and weights manually here

int classification_threshold = 37;

static const float weights[] = { 
  0.00000000, 0.00000000, 0.00000000, 0.00000000, 
  0.47980666, -0.31826749, 0.05809363, -0.03985905, 
  0.00901466, 0.04364503, 0.11737717, 0.07428109, 
  -0.03230778, 0.03184345, 0.24386819, 0.26248544, 
  0.38641945, 0.25450867, 0.22663249, 0.03980024, 
  0.00449978, 0.17955838, 0.06384432, 0.00154516, 
  0.13665916, 0.02439742, 0.04932829, -0.18201523, 
  0.24929525, 0.20383461, 0.00252797, 0.00000000
};
static const float bias = 0.05218686;

static const unsigned int elements_size = 150;  // How many classifications should be collected before batch sending to logger. NOTE: More than 1400 bytes will segment the packet and reception will fail.

static const int weight_length = (sizeof(weights) / sizeof(float));
static_assert(samples / 2 == weight_length, "Error: Weights are not as many as samples/2");

// ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
