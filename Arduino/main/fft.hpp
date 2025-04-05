#pragma once

#include "user/WifiSettings.h"
#include "user/Settings.h"
#include "runtime_variables.hpp"

// Uses CMSIS-DSP library (for ARM devices)
#include <arm_math.h>

arm_rfft_fast_instance_f32 fftInstance;

int classify(float input[]) {
  float sum;
  arm_dot_prod_f32(input, weights, weight_length, &sum);  // SIMD-optimized dot product
  sum += bias;

  if (stream_FFT)
    Serial.println(sum);

  return sum >= classification_threshold ? 1 : 0;  // Classification threshold
}

// Collects EMG samples from analog input
void collect_samples() {
  for (uint16_t i = 0; i < samples; i++) {
    vReal[i] = analogRead(analog_pin);
    delayMicroseconds(1000000 / samplingFrequency);
  }
}

inline void setup_fft() {
  // Initialize CMSIS-DSP FFT
  arm_rfft_fast_init_f32(&fftInstance, samples);

  for (int i = 0; i < samples; i++) {
    hammingWindow[i] = 0.54f - 0.46f * arm_cos_f32((2.0f * 3.14159265359f * (float32_t)i) / ((float32_t)samples - 1.0f));
  }
}

inline void loop_fft() {
  // Collect EMG samples
  collect_samples();

  // Apply Hamming window
  arm_mult_f32(vReal, hammingWindow, vReal, samples);
  arm_rfft_fast_f32(&fftInstance, vReal, vReal, 0);
  arm_cmplx_mag_f32(vReal, vReal, samples / 2);

  // Erase up to 60Hz frequency bin
  uint8_t i = 255;
  while (freq_bin * (++i) <= 60)
    vReal[i] = 0;

}