#pragma once

#include "user/WifiSettings.h"
#include "user/Settings.h"
#include "runtime_variables.hpp"

// ==============================================================================
// 1. SELEZIONE DEL MOTORE FFT
// ==============================================================================
#define FFT_USE_CMSIS_DSP   1  
#define FFT_USE_ARDUINO_FFT 2  
#define FFT_USE_NATIVE      3  

#define FFT_ENGINE FFT_USE_NATIVE

#if FFT_ENGINE == FFT_USE_CMSIS_DSP
  #include <arm_math.h>
  arm_rfft_fast_instance_f32 fftInstance;
#elif FFT_ENGINE == FFT_USE_ARDUINO_FFT
  #include <arduinoFFT.h>
  float vImag[samples];
  ArduinoFFT<float> FFT = ArduinoFFT<float>(vReal, vImag, samples, samplingFrequency);
#elif FFT_ENGINE == FFT_USE_NATIVE
  #include <math.h>
  float vImag[samples];
#endif

uint16_t sample_index = 0;
unsigned long last_sample_time = 0;
bool fft_ready = false;

const uint8_t max_bin_to_erase = (uint8_t)(90.0f / freq_bin);
const uint8_t min_high_noise_bin = (uint8_t)(400.0f / freq_bin);

// ==============================================================================
// CLASSIFICAZIONE (DOT PRODUCT)
// ==============================================================================
int classify(float input[], float& sum) {
  sum = 0;

#if FFT_ENGINE == FFT_USE_CMSIS_DSP
  arm_dot_prod_f32(input, (float*)eeprom_config.weights, weight_length, &sum);
#else
  for(int i = 0; i < weight_length; i++) {
    sum += input[i] * eeprom_config.weights[i];
  }
#endif

  sum += eeprom_config.bias;

  if (stream_FFT){
    DEBUG_PRINTLN(sum);
    send_evaluation_result(sum, sum >= eeprom_config.classification_threshold ? 1 : 0);
  }

  return sum >= eeprom_config.classification_threshold ? 1 : 0;
}

// ==============================================================================
// CAMPIONAMENTO NON BLOCCANTE
// ==============================================================================
inline void collect_samples_nonblocking() {
  unsigned long now = micros();
  if (now - last_sample_time >= sample_interval_us) {
    last_sample_time = now;
    vReal[sample_index] = analogRead(analog_pin);
    sample_index++;
    
    if (sample_index >= samples) {
      sample_index = 0;
      fft_ready = true;
    }
  }
}

// ==============================================================================
// REAL FFT OTTIMIZZATA (N campioni reali -> FFT complessa a N/2 punti)
// ==============================================================================
#if FFT_ENGINE == FFT_USE_NATIVE
void compute_native_real_fft() {
    int half = samples / 2; // 32 punti per 64 campioni reali

    // 1. Pack: unisce i campioni reali in un array complesso di dimensione N/2 (32)
    for (int i = 0; i < half; i++) {
        vReal[i] = vReal[2 * i];
        vImag[i] = vReal[2 * i + 1];
    }

    // 2. Esegue la FFT complessa a 32 punti (Cooley-Tukey Radix-2)
    uint16_t j = 0;
    for (uint16_t i = 0; i < half - 1; i++) {
        if (i < j) {
            float tempR = vReal[i]; vReal[i] = vReal[j]; vReal[j] = tempR;
            float tempI = vImag[i]; vImag[i] = vImag[j]; vImag[j] = tempI;
        }
        uint16_t k = half >> 1;
        while (k <= j) { j -= k; k >>= 1; }
        j += k;
    }

    for (uint16_t step = 1; step < half; step <<= 1) {
        float theta = -PI / step;
        float wtemp = sin(0.5f * theta);
        float wpr = -2.0f * wtemp * wtemp;
        float wpi = sin(theta);
        float wr = 1.0f;
        float wi = 0.0f;
        for (uint16_t m = 0; m < step; m++) {
            for (uint16_t i = m; i < half; i += 2 * step) {
                uint16_t l = i + step;
                float tr = wr * vReal[l] - wi * vImag[l];
                float ti = wr * vImag[l] + wi * vReal[l];
                vReal[l] = vReal[i] - tr;
                vImag[l] = vImag[i] - ti;
                vReal[i] += tr;
                vImag[i] += ti;
            }
            wtemp = wr;
            wr += wr * wpr - wi * wpi;
            wi += wi * wpr + wtemp * wpi;
        }
    }

    // 3. Unpack e Post-processing per estrarre le magnitudini finali dello spettro reale (32 bin)
    float temp_real[half];
    float temp_imag[half];
    
    for (int k = 0; k < half; k++) {
        int k_rev = (half - k) % half;
        float r1 = 0.5f * (vReal[k] + vReal[k_rev]);
        float i1 = 0.5f * (vImag[k] - vImag[k_rev]);
        float r2 = 0.5f * (vImag[k] + vImag[k_rev]);
        float i2 = 0.5f * (vReal[k_rev] - vReal[k]);

        float angle = -PI * k / half;
        float wr = cos(angle);
        float wi = sin(angle);

        float tw_r = r2 * wr - i2 * wi;
        float tw_i = r2 * wi + i2 * wr;

        temp_real[k] = r1 + tw_r;
        temp_imag[k] = i1 + tw_i;
    }

    // Calcolo della magnitudo finale memorizzata direttamente in vReal[0..31]
    for (int k = 0; k < half; k++) {
        vReal[k] = sqrt(temp_real[k] * temp_real[k] + temp_imag[k] * temp_imag[k]);
    }
}
#endif

inline void setup_fft() {
  for (int i = 0; i < samples; i++) {
    hammingWindow[i] = 0.54f - 0.46f * cos((2.0f * PI * (float)i) / ((float)samples - 1.0f));
  }
}

// ==============================================================================
// LOOP FFT & ESECUZIONE
// ==============================================================================
inline void loop_fft() {
  collect_samples_nonblocking();

  if (fft_ready) {
    fft_ready = false;

    // 1. Applica la finestra di Hamming sui 64 campioni reali
    for (uint16_t i = 0; i < samples; i++) {
        vReal[i] = vReal[i] * hammingWindow[i];
    }

    // 2. Esegue la trasformata ottimizzata
    #if FFT_ENGINE == FFT_USE_ARDUINO_FFT
        FFT.compute(FFTDirection::Forward);
        FFT.complexToMagnitude();
    #elif FFT_ENGINE == FFT_USE_NATIVE
        compute_native_real_fft();
    #endif

    // 3. Pulizia del rumore a bassa e alta frequenza sui 32 bin risultanti
    memset(vReal, 0, max_bin_to_erase * sizeof(float));
    memset(&vReal[min_high_noise_bin], 0, ((samples / 2) - min_high_noise_bin) * sizeof(float)); // Adattato ai bin corretti

    new_fft_data = true;
  }
}