// Uses debounce by Aaron Kimball
#include <Arduino.h>
#include <debounce.h>
// Uses CMSIS-DSP library (for ARM devices)
#include <arm_math.h>
#include <WiFi.h>
#include <WiFiUdp.h>

// Connect a button to some GPIO pin
static constexpr int BUTTON = 3;
static constexpr int BUZZER = 5;


// FFT Settings
const uint16_t samples = 64;              // Must be a power of 2
const uint16_t samplingFrequency = 1000;  // Adjust as needed
const float freq_bin = samplingFrequency / samples;
float vReal[samples];
float hammingWindow[samples];  // Hamming window coefficients
int network_send_bytes = (samples / 2) * sizeof(float);
arm_rfft_fast_instance_f32 fftInstance;
const int analog_pin = A0;  // Set the correct analog input pin
static const float weights[] = {
  0.00000000,
  0.00000000,
  0.00000000,
  0.00000000,
  0.00000000,
  -0.16666446,
  0.14036143,
  0.10275731,
  0.15018884,
  0.17533556,
  -0.01297485,
  0.29788694,
  -0.10588868,
  0.16164895,
  0.07309278,
  -0.06163993,
  0.72166246,
  -0.05186196,
  0.05293969,
  0.17246698,
  -0.09546843,
  0.25317097,
  -0.05021467,
  0.20567502,
  0.12985801,
  0.05549219,
  0.18976886,
  -0.10753195,
  -0.29549880,
  0.30059777,
  0.24690098,
  0.00517570,
};
static const float bias = -0.2237529517562951;
static const int weight_length = (sizeof(weights) / sizeof(float));

static_assert(samples / 2 == weight_length, "Error: Weights are not as many as samples/2");

bool stream_FFT = false;

const uint8_t CLENCH_START = 0;
const uint8_t CLENCH_STOP = 1;
const uint8_t BUTTON_PRESS = 2;
const uint8_t ALARM_START = 3;
const uint8_t BEEP = 4;
const uint8_t UDP_ALARM_CONFIRMED = 5;
const uint8_t DETECTED = 6;
const uint8_t CONTINUED = 7;
const uint8_t ALARM_STOP = 8;

// Apply packing to ensure there is no padding between struct members
#pragma pack(push, 1)  // Start packing (1-byte alignment)

struct data_element {
  unsigned long timestamp;  // 4 bytes
  bool value;               // 1 byte
};

#pragma pack(pop)  // Restore default packing alignment


static const unsigned int elements_size = 1000;
unsigned int elements_cursor = 0;
data_element data_elements[elements_size];

int classify(float input[]) {
  float sum;
  arm_dot_prod_f32(input, weights, weight_length, &sum);  // SIMD-optimized dot product
  sum += bias;

  if (stream_FFT)
    Serial.println(sum);

  return sum >= 55 ? 1 : 0;  // Classification threshold
}

#ifdef NIGHT_SIMULATION_SERIAL
#include <cstring>
#include <String>

void send_event_serial(uint8_t event, unsigned long ms) {
  Serial.print(ms);
  Serial.print(";");
  Serial.print("simulation");
  Serial.print(";");

  switch (event) {
    default:
      //print("Received unrecognized byte value: ");
      //println((int)event);
      break;

    case CLENCH_START:
      Serial.print("Clenching");
      Serial.print(";");
      Serial.println("STARTED");
      break;

    case CLENCH_STOP:
      Serial.print("Clenching");
      Serial.print(";");
      Serial.println("STOPPED");
      break;

    case BUTTON_PRESS:
      Serial.println("Button");
      break;

    case ALARM_START:
      Serial.print("Alarm");
      Serial.print(";");
      Serial.println("STARTED");
      break;

    case ALARM_STOP:
      Serial.print("Alarm");
      Serial.print(";");
      Serial.println("STOPPED");
      break;

    case BEEP:
      Serial.print("Beep");
      Serial.print(";");
      Serial.println("WARNING BEEP");
      break;

    case DETECTED:
      Serial.print("Clenching");
      Serial.print(";");
      Serial.println("FIRST DETECTION");
      break;

    case CONTINUED:
      Serial.print("Clenching");
      Serial.print(";");
      Serial.println("CONTINUED");
      break;
  }
}

#endif

WiFiUDP udp;                                 // Define UDP object
WiFiUDP read_udp;                            // Define UDP object
IPAddress multicastAddress(239, 255, 0, 1);  // Multicast address
unsigned int multicastPort = 4000;           // Multicast port
unsigned int multicastReadPort = 4001;

const char* ssid = "YOUR SSID";
const char* password = "YOUR PASSWORD";

void send_parameters_udp() {
  udp.beginPacket(multicastAddress, multicastPort);

  // Send samplingFrequency as uint16_t (Little-Endian)
  udp.write(lowByte(samplingFrequency));
  udp.write(highByte(samplingFrequency));

  // Send samples as uint16_t (Little-Endian)
  udp.write(lowByte(samples));
  udp.write(highByte(samples));

  udp.endPacket();
}

void send_element(data_element* d) {
  // Send data
  udp.beginPacket(multicastAddress, multicastPort);
  udp.write((uint8_t*)(d), 5);
  udp.endPacket();
}

void send_event(uint8_t event) {
  // Send FFT data
  udp.beginPacket(multicastAddress, multicastPort);
  udp.write((uint8_t*)(&event), 1);
  udp.endPacket();
}

uint8_t cc = 200;
void send_to_udp() {

  if (++cc == 0)
    send_parameters_udp();

  // Send FFT data
  udp.beginPacket(multicastAddress, multicastPort);
  udp.write((uint8_t*)(vReal), network_send_bytes);
  udp.endPacket();
}

void read_from_udp() {
  char packetBuffer[255];  // Buffer to store incoming messages
  int packetSize = read_udp.parsePacket();


  int len = 0;
  if (packetSize) {
    len = read_udp.read(packetBuffer, sizeof(packetBuffer) - 1);

    Serial.print("Read ");
    Serial.print((int)len);
    Serial.print(": ");
    Serial.println(packetBuffer[0]);

    if (len > 0) {
      packetBuffer[len] = 0;  // Null-terminate the received string
    }
    if (len == 1) {
      switch (packetBuffer[0]) {
        case 0:
        case 1:
        case 2:
        case 3:
          warning_beep();
          break;

        default:
          trigger_alarm();
          // Confirm alarm action received via UDP by responding with 1 byte of value 2
          send_event(UDP_ALARM_CONFIRMED);
          break;
      }
    }
  }
}

// Collects EMG samples from analog input
void collect_samples() {
  for (uint16_t i = 0; i < samples; i++) {
    vReal[i] = analogRead(analog_pin);
    delayMicroseconds(1000000 / samplingFrequency);
  }
}

// Sends FFT magnitudes over Serial (or UDP)
void send_fft_data() {
  Serial.print("FFT: ");
  for (uint16_t i = 0; i < samples / 2; i++) {
    if (i != 0)
      Serial.print(", ");
    Serial.print(vReal[i], 2);
  }
  Serial.println();
}

inline void setup_fft() {
  // Initialize CMSIS-DSP FFT
  arm_rfft_fast_init_f32(&fftInstance, samples);

  for (int i = 0; i < samples; i++) {
    hammingWindow[i] = 0.54f - 0.46f * arm_cos_f32((2.0f * 3.14159265359f * (float32_t)i) / ((float32_t)samples - 1.0f));
  }
}

bool alarm_running = 0;

long last_tone_ms = 0, last_flash = 0;

int tone_num = 4;
int tone_sel = tone_num - 1;
unsigned int tones[] = { 1567, 1760, 1975, 2093 };
unsigned int durations[] = { 200, 200, 200, 500 };
unsigned int waits[] = { 170, 170, 170, 4500 };

int warning_beep_duration = 100;
int warning_beep_wait = 50;

inline void loop_alarm() {
  if (alarm_running) {
    if (millis() - last_tone_ms >= waits[tone_sel]) {
      if (++tone_sel >= tone_num)
        tone_sel = 0;
      tone(BUZZER, tones[tone_sel], durations[tone_sel]);
      last_tone_ms = millis();
    }
  }
}

void trigger_alarm() {
  alarm_running = true;
}

void warning_beep() {
  tone(BUZZER, 2600, warning_beep_duration);
  delay(warning_beep_wait);
  tone(BUZZER, 2600, warning_beep_duration);
}

static void button_long_press(uint8_t btnId, uint8_t btnState);
static void button_short_press(uint8_t btnId, uint8_t btnState);

static Button button_input_short(0, button_short_press);
static Button button_input_long(1, button_long_press);

// Function to send elements in smaller packets
void send_elements_batch(data_element* d) {
  udp.beginPacket(multicastAddress, multicastPort);
  udp.write((uint8_t*)d, sizeof(data_element) * elements_size);  // Send the chunk of data
  udp.endPacket();
}

void add_to_elements(bool b) {
  data_elements[elements_cursor++] = { millis(), b };

  if (elements_cursor == elements_size) {
    send_elements_batch(data_elements);  // Send all at once
    elements_cursor = 0;
  }
}

// Variabili configurabili
const unsigned long attesaFiltraggio = 1000;  // 1000ms per ignorare falsi positivi
const unsigned long attesaPrimoBeep = 1000;   // 1s prima del primo beep
const unsigned long attesaBeep = 3000;        // 3s tra un beep e l'altro
const int numeroMaxBeep = 3;                  // Numero massimo di beep prima dell'allarme
const unsigned long periodoAttesa = 8000;     // 2s per considerare interrotto l'evento
const unsigned long periodoGrazia = 60000;    // 60s di grazia per dare tempo di mettersi a letto

const unsigned long filtraggioIniziale = 100;
const uint8_t campioniFiltraggio = 6;

// Variabili di stato
bool eventoInCorso = false;
unsigned long inizioEvento = 0;
unsigned long ultimoPositivo = 0;
int beepCounter = 0;
unsigned long ultimoBottone = 0;

unsigned long inizioFiltraggio = 0;
unsigned long ultimoCampione = 0;
unsigned long periodoCampione = filtraggioIniziale / campioniFiltraggio;
bool filtraggio[campioniFiltraggio] = { false };
uint8_t indice_campione = campioniFiltraggio - 1;

bool started_sent = false;

void trigger_system(int classificazione, unsigned long tempoAttuale) {
  add_to_elements(classificazione);

  bool esitoFiltraggio = false;
  bool filtraggioCompletato = false;

  if (tempoAttuale - ultimoCampione >= periodoCampione) {
    ultimoCampione = tempoAttuale;
    filtraggio[indice_campione++] = classificazione;
    if (indice_campione == campioniFiltraggio) {
      filtraggioCompletato = true;
      uint8_t count = 0;
      for (uint8_t i = 0; i < campioniFiltraggio; i++)
        if (filtraggio[i])
          count++;
      esitoFiltraggio = count >= (2 * (campioniFiltraggio / 3));
      indice_campione = 0;
      if (stream_FFT) {
        Serial.print(campioniFiltraggio);
        Serial.print(" in ");
        Serial.print(tempoAttuale - inizioFiltraggio);
        Serial.print("ms\tEsito");
        Serial.println(esitoFiltraggio);
      }

      inizioFiltraggio = tempoAttuale;
    }
  }

  if (tempoAttuale - ultimoBottone < periodoGrazia || !filtraggioCompletato) {
    return;
  }


  if (esitoFiltraggio) {  // Se il segnale indica clenching

    if (!eventoInCorso) {
      if (tempoAttuale - ultimoPositivo < periodoAttesa) {
        // Se l'evento torna positivo entro 10s, continua l'evento precedente
        eventoInCorso = true;
        Serial.println("Evento ripreso!");
        send_event(CONTINUED);


      } else {
        // Nuovo evento
        inizioEvento = tempoAttuale;
        eventoInCorso = true;
        beepCounter = 0;
        Serial.println("Nuovo evento iniziato");
        send_event(DETECTED);

      }
    }
    ultimoPositivo = tempoAttuale;

    if (tempoAttuale - inizioEvento > attesaFiltraggio) {
      if (tempoAttuale - inizioEvento > attesaPrimoBeep + beepCounter * attesaBeep) {
        if (beepCounter < numeroMaxBeep) {
          if (beepCounter == 0) {

            send_event(CLENCH_START);

            started_sent = true;
          }
          warning_beep();
          Serial.println("Beep!");
          send_event(BEEP);

          beepCounter++;
        } else if (!alarm_running) {
          Serial.println("Allarme attivato!");
          send_event(ALARM_START);

          trigger_alarm();
        }
      }
    }

  } else {
    // Se il segnale torna negativo
    if (eventoInCorso && (tempoAttuale - ultimoPositivo > periodoAttesa)) {
      eventoInCorso = false;
      if (started_sent) {
        Serial.println("Evento terminato");
        send_event(CLENCH_STOP);

        started_sent = false;
      }
    }
  }
}


inline void loop_logic() {
  trigger_system(classify(vReal), millis());

  if (stream_FFT)
    send_to_udp();

  loop_alarm();
  bool btread = digitalRead(BUTTON);
  button_input_long.update(btread);
  button_input_short.update(btread);
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

  // Send magnitudes over Serial
  // send_fft_data();
}

void setup_wifi() {

  Serial.print("Bytes per double: ");
  Serial.println(sizeof(double));
  Serial.print("Bytes to send udp: ");
  Serial.println(network_send_bytes);

  // Wi-Fi code for Arduino Uno R4 WiFi
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(100);
  }

  udp.beginMulticast(multicastAddress, multicastPort);
  read_udp.beginMulticast(multicastAddress, multicastReadPort);
}

inline void loop_wifi() {
  read_from_udp();
}

void setup() {

  Serial.begin(500000);

  tone(BUZZER, 1500, warning_beep_duration);

  pinMode(BUTTON, INPUT_PULLUP);

  button_input_short.setPushDebounceInterval(10);
  button_input_long.setPushDebounceInterval(800);

  setup_fft();
  setup_wifi();

  tone(BUZZER, 1800, 100);
}

void loop() {
  loop_fft();
  loop_wifi();
  loop_logic();
}

static void button_short_press(uint8_t btnId, uint8_t btnState) {
  if (btnState == BTN_PRESSED) {
    tone(BUZZER, 1000, 100);
    alarm_running = false;
    tone_sel = tone_num - 1;

    eventoInCorso = false;
    beepCounter = 0;
    ultimoBottone = millis();

    send_event(BUTTON_PRESS);
  }
}

static void button_long_press(uint8_t btnId, uint8_t btnState) {
  if (btnState == BTN_PRESSED) {
    stream_FFT = !stream_FFT;
    tone(BUZZER, stream_FFT ? 2000 : 2400, 50);
    delay(100);
    tone(BUZZER, !stream_FFT ? 2000 : 2400, 50);
  }
}
