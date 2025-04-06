#pragma once

#include "Notes.h"

// GPIO

static constexpr int analog_pin = A0;  // Set the correct analog input pin
static constexpr int BUTTON = 3;
static constexpr int BUZZER = 5;

// ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~

// TUNES

// Uncomment if you want to start/stop a tune by pressing the button once
//#define TESTING_TONES

uint8_t playtune = 2;            // Which tune you want to start with? (see tunes[] array)
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

// ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~

// SVM Settings

static const int classification_threshold = 68;

static const float weights[] = { 0.00000000, 0.00000000, 0.00000000, 0.00000000, 0.00000000, -0.16666446, 0.14036143, 0.10275731, 0.15018884, 0.17533556, -0.01297485, 0.29788694, -0.10588868, 0.16164895, 0.07309278, -0.06163993, 0.72166246, -0.05186196, 0.05293969, 0.17246698, -0.09546843, 0.25317097, -0.05021467, 0.20567502, 0.12985801, 0.05549219, 0.18976886, -0.10753195, -0.29549880, 0.30059777, 0.24690098, 0.00517570 };
static const float bias = -0.2237529517562951;
static const int weight_length = (sizeof(weights) / sizeof(float));

static const unsigned int elements_size = 1000;  // How many classifications should be collected before batch sending to logger

static_assert(samples / 2 == weight_length, "Error: Weights are not as many as samples/2");

// ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~
