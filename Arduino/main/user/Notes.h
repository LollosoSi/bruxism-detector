#pragma once

namespace Notes {

enum {
  C = 0, Cs, D, Ds, E, F, Fs, G, Gs, A, As, B
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

// Constant for ln(2)
constexpr double LN2 = 0.6931471805599453;

// A constexpr exponential function using Taylor series expansion.
// Increase the number of terms (default 20) if you need more precision.
constexpr double taylor_exp(double x, int terms = 20) {
    double sum = 1.0;
    double term = 1.0;
    for (int i = 1; i <= terms; ++i) {
        term *= x / i;
        sum += term;
    }
    return sum;
}

// Compute 2^x as exp(x * ln2)
constexpr double constexpr_pow2(double x) {
    return taylor_exp(x * LN2);
}

// Now the frequency is defined as:
//  f = 440.0 * 2^(((octave+1)*12 + note - 69) / 12.0)
constexpr float getFrequency(int note, int octave) {
  return 440.0f * static_cast<float>(constexpr_pow2(((octave + 1) * 12 + note - 69) / 12.0));
}

// Define constants from C4 to C8
constexpr float C4  = getFrequency(C, 4);
constexpr float Cs4 = getFrequency(Cs, 4);
constexpr float D4  = getFrequency(D, 4);
constexpr float Ds4 = getFrequency(Ds, 4);
constexpr float E4  = getFrequency(E, 4);
constexpr float F4  = getFrequency(F, 4);
constexpr float Fs4 = getFrequency(Fs, 4);
constexpr float G4  = getFrequency(G, 4);
constexpr float Gs4 = getFrequency(Gs, 4);
constexpr float A4  = getFrequency(A, 4);
constexpr float As4 = getFrequency(As, 4);
constexpr float B4  = getFrequency(B, 4);

constexpr float C5  = getFrequency(C, 5);
constexpr float Cs5 = getFrequency(Cs, 5);
constexpr float D5  = getFrequency(D, 5);
constexpr float Ds5 = getFrequency(Ds, 5);
constexpr float E5  = getFrequency(E, 5);
constexpr float F5  = getFrequency(F, 5);
constexpr float Fs5 = getFrequency(Fs, 5);
constexpr float G5  = getFrequency(G, 5);
constexpr float Gs5 = getFrequency(Gs, 5);
constexpr float A5  = getFrequency(A, 5);
constexpr float As5 = getFrequency(As, 5);
constexpr float B5  = getFrequency(B, 5);

constexpr float C6  = getFrequency(C, 6);
constexpr float Cs6 = getFrequency(Cs, 6);
constexpr float D6  = getFrequency(D, 6);
constexpr float Ds6 = getFrequency(Ds, 6);
constexpr float E6  = getFrequency(E, 6);
constexpr float F6  = getFrequency(F, 6);
constexpr float Fs6 = getFrequency(Fs, 6);
constexpr float G6  = getFrequency(G, 6);
constexpr float Gs6 = getFrequency(Gs, 6);
constexpr float A6  = getFrequency(A, 6);
constexpr float As6 = getFrequency(As, 6);
constexpr float B6  = getFrequency(B, 6);

constexpr float C7  = getFrequency(C, 7);
constexpr float Cs7 = getFrequency(Cs, 7);
constexpr float D7  = getFrequency(D, 7);
constexpr float Ds7 = getFrequency(Ds, 7);
constexpr float E7  = getFrequency(E, 7);
constexpr float F7  = getFrequency(F, 7);
constexpr float Fs7 = getFrequency(Fs, 7);
constexpr float G7  = getFrequency(G, 7);
constexpr float Gs7 = getFrequency(Gs, 7);
constexpr float A7  = getFrequency(A, 7);
constexpr float As7 = getFrequency(As, 7);
constexpr float B7  = getFrequency(B, 7);

constexpr float C8  = getFrequency(C, 8);
constexpr float Cs8 = getFrequency(Cs, 8);
constexpr float D8  = getFrequency(D, 8);
constexpr float Ds8 = getFrequency(Ds, 8);
constexpr float E8  = getFrequency(E, 8);
constexpr float F8  = getFrequency(F, 8);
constexpr float Fs8 = getFrequency(Fs, 8);
constexpr float G8  = getFrequency(G, 8);
constexpr float Gs8 = getFrequency(Gs, 8);
constexpr float A8  = getFrequency(A, 8);
constexpr float As8 = getFrequency(As, 8);
constexpr float B8  = getFrequency(B, 8);


}