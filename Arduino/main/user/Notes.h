#pragma once

namespace Notes {

typedef enum {
  C4 = 261,
  Cd4 = 277,
  D4 = 293,
  Dd4 = 311,
  E4 = 329,
  F4 = 349,
  Fd4 = 370,
  G4 = 392,
  Gd4 = 415,
  A4 = 440,
  Ad4 = 466,
  B4 = 493,
  C5 = 523,
  Cd5 = 554,
  D5 = 587,
  Dd5 = 622,
  E5 = 659,
  F5 = 698,
  Fd5 = 740,
  G5 = 784,
  Gd5 = 831,
  A5 = 880,
  Ad5 = 932,
  B5 = 987,
  C6 = 1047,
  Cd6 = 1109,
  D6 = 1175,
  Dd6 = 1244,
  E6 = 1319,
  F6 = 1397,
  Fd6 = 1480,
  G6 = 1568,
  Gd6 = 1661,
  A6 = 1760,
  Ad6 = 1865,
  B6 = 1976,
  C7 = 2093,
  Cd7 = 2217
} Note;

// Enum for note durations and wait times (using the same values)
typedef enum {
  Whole = 1200,         // Whole note - 1200ms
  Half = 600,           // Half note - 600ms
  Quarter = 300,        // Quarter note - 300ms
  Eighth = 150,         // Eighth note - 150ms
  Sixteenth = 75,       // Sixteenth note - 75ms
  DottedQuarter = 450,  // Dotted quarter note - 450ms
  DottedEighth = 225,   // Dotted eighth note - 225ms
} NoteLength;

}