#pragma once

#include "user/WifiSettings.h"
#include "user/Settings.h"
#include "runtime_variables.hpp"

#include "wifi.hpp"

// Uses debounce by Aaron Kimball
#include <debounce.h>


static void button_long_press(uint8_t btnId, uint8_t btnState);
static void button_short_press(uint8_t btnId, uint8_t btnState);

static Button button_input_short(0, button_short_press);
static Button button_input_long(1, button_long_press);


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

bool alarm_running = 0;

long last_tone_ms = 0, last_flash = 0;

void add_to_elements(bool b) {
  data_elements[elements_cursor++] = { millis(), b };

  if (elements_cursor == elements_size) {
    send_elements_batch(data_elements);  // Send all at once
    elements_cursor = 0;
  }
}

void trigger_alarm() {
  alarm_running = true;
  beepCounter = numeroMaxBeep;
  eventoInCorso = false;
}

void warning_beep() {
  tone(BUZZER, 2600, warning_beep_duration);
  delay(warning_beep_wait);
  tone(BUZZER, 2600, warning_beep_duration);
}

inline void loop_alarm() {
  if (alarm_running) {
    if (millis() - last_tone_ms >= tunes[playtune]->waits[tone_sel]) {
      if (++tone_sel >= tunes[playtune]->tone_num) {
        tone_sel = 0;

        if ((++replays) % max_replays == 0) {
          replays = 0;
          reset_tune(true);
        }
      }
      tone(BUZZER, tunes[playtune]->tones[tone_sel], tunes[playtune]->durations[tone_sel]);
      last_tone_ms = millis();
    }
  }
}

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

  if (tempoAttuale - ultimoBottone < periodoGrazia || !filtraggioCompletato || alarm_running) {
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

void setup_logic() {
  pinMode(BUTTON, INPUT_PULLUP);

  button_input_short.setPushDebounceInterval(10);
  button_input_long.setPushDebounceInterval(800);
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

static void button_short_press(uint8_t btnId, uint8_t btnState) {
  if (btnState == BTN_PRESSED) {

#ifdef TESTING_TONES
    alarm_running = !alarm_running;
#else
    tone(BUZZER, 1000, 100);
    alarm_running = false;
#endif

    reset_tune();

    eventoInCorso = false;
    beepCounter = 0;
    ultimoBottone = millis();
    started_sent = false;

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