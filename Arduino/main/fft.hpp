#pragma once

#include "user/WifiSettings.h"
#include "user/Settings.h"
#include "runtime_variables.hpp"

// Uses CMSIS-DSP library (for ARM devices)
#include <arm_math.h>

uint16_t sample_index = 0;
unsigned long last_sample_time = 0;
bool fft_ready = false;

// Constant: erase up to 60Hz (testing 90)
const uint8_t max_bin_to_erase = (uint8_t)(90.0f / freq_bin);

// Constant: erase from 400Hz up to Nyquist
const uint8_t min_high_noise_bin = (uint8_t)(400.0f / freq_bin);

arm_rfft_fast_instance_f32 fftInstance;

int classify(float input[], float& sum) {
  sum = 0;
  arm_dot_prod_f32(input, eeprom_config.weights, weight_length, &sum);  // SIMD-optimized dot product
  sum += eeprom_config.bias;

  if (stream_FFT){
    Serial.println(sum);
    send_evaluation_result(sum, sum >= eeprom_config.classification_threshold ? 1 : 0);
  }

  return sum >= eeprom_config.classification_threshold ? 1 : 0;  // Classification threshold
}

// Collects EMG samples from analog input
/**void collect_samples() {
  for (uint16_t i = 0; i < samples; i++) {
    vReal[i] = analogRead(analog_pin);
    delayMicroseconds(1000000 / samplingFrequency);
  }
}*/
inline void collect_samples_nonblocking() {
  unsigned long now = micros();
  if (now - last_sample_time >= sample_interval_us) {
    last_sample_time = now;
    vReal[sample_index] = analogRead(analog_pin);
    sample_index++;
    
    if (sample_index >= samples) {
      sample_index = 0;
      fft_ready = true; // Segnala che l'array è pronto per la FFT
    }
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
  //collect_samples();
  collect_samples_nonblocking();

if (fft_ready) {
    fft_ready = false;
  // Apply Hamming window
  arm_mult_f32(vReal, hammingWindow, vReal, samples);
  arm_rfft_fast_f32(&fftInstance, vReal, vReal, 0);
  arm_cmplx_mag_f32(vReal, vReal, samples / 2);

  // Erase up to 60Hz frequency bin
  //uint8_t i = 255;
  //while (freq_bin * (++i) <= 60)
  //  vReal[i] = 0;
  // Faster erase
  memset(vReal, 0, max_bin_to_erase * sizeof(float));

  // Faster erase for high frequency noise (400Hz - 500Hz)
  memset(&vReal[min_high_noise_bin], 0, ((samples / 2) - min_high_noise_bin) * sizeof(float));

  new_fft_data = true;
}

}