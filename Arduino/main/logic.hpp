#include <EEPROM.h>

#pragma once

#include "user/WifiSettings.h"
#include "user/Settings.h"
#include "runtime_variables.hpp"

#include "wifi.hpp"

// Uses debounce by Aaron Kimball
#include <debounce.h>

static void button_longer_press(uint8_t btnId, uint8_t btnState);
static void button_long_press(uint8_t btnId, uint8_t btnState);
static void button_short_press(uint8_t btnId, uint8_t btnState);

static Button button_input_short(0, button_short_press);
static Button button_input_long(1, button_long_press);
static Button button_input_longer(2, button_longer_press);

bool is_ema_procedure = false;
bool is_calc_ema = false;
bool is_calc_b = true;
float EMA_A = 0, EMA_B = 0;
float min_b = 0;
const float alpha = 0.1f;
static void emacalc(float &EMA, float x) {
  EMA = (alpha * x) + ((1 - alpha) * EMA);
}

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
unsigned long alarm_start = 0;
void trigger_alarm() {
  alarm_running = true;
  beepCounter = numeroMaxBeep;
  eventoInCorso = false;
  alarm_start = millis();
  if (is_using_android) {
    need_alarm_confirmation = true;
  }
}

void warning_beep() {
  send_event(BEEP);
  if(do_not_beep_if_android && is_using_android){
    return;
  }

  tone(BUZZER, 2600, warning_beep_duration);
  delay(warning_beep_wait);
 // tone(BUZZER, 2600, warning_beep_duration);
}

inline void loop_alarm() {
  if (alarm_running) {

    if (!is_using_android) {
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
    } else {
      if (need_alarm_confirmation)
        send_event(ALARM_START);

      if ((millis() > (alarm_start + android_alarm_timeout))) {
        // Android failed, fallback to device only alarm
        is_using_android = false;
      }
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
          

          beepCounter++;
        } else if (!alarm_running) {
          Serial.println("Allarme attivato!");

          trigger_alarm();
          send_event(ALARM_START);
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

unsigned long last_button_press = 0;
uint8_t press_count = 0;
bool beep_incremental = false;

void setup_logic() {
  pinMode(BUTTON, INPUT_PULLUP);

  button_input_short.setPushDebounceInterval(10);
  button_input_long.setPushDebounceInterval(800);
  button_input_longer.setPushDebounceInterval(9000);
}

inline void loop_logic() {
  float result = 0;
  if (!is_calc_ema)
    trigger_system(classify(vReal, result), millis());
  else {
    classify(vReal, result);

    emacalc(is_calc_b ? EMA_B : EMA_A, result);

    if (is_calc_b) {
      if (result < min_b || min_b == 0)
        min_b = result;
    }
  }

  if (stream_FFT)
    send_to_udp();

  loop_alarm();

  bool btread = digitalRead(BUTTON);
  do {
    button_input_short.update(btread);
    if (millis() < 120000) {
      button_input_long.update(btread);
      button_input_longer.update(btread);

      if (beep_incremental && millis() - last_button_press > 3000) {
        tone(BUZZER, map(millis() - last_button_press, 3000, 10000, 1500, 1700), 30);
        delay(100);
      }
    }
  } while (!(btread = digitalRead(BUTTON)));
}

static void button_short_press(uint8_t btnId, uint8_t btnState) {
  if (btnState == BTN_PRESSED) {

    if (is_ema_procedure) {
      tone(BUZZER, 1000, 100);

      if (is_calc_ema) {
        is_calc_ema = false;

        if (is_calc_b && (EMA_A != 0 && EMA_B != 0)) {
          tone(BUZZER, Notes::E5, 100);
          delay(200);
          tone(BUZZER, Notes::E5, 100);
          delay(150);
          tone(BUZZER, Notes::B6, 250);
          delay(200);

          // Time for results:
          Serial.print("Calibration results:\nAverage NON CLENCHING: ");
          Serial.print(EMA_A);
          Serial.print("\tAverage CLENCHING: ");
          Serial.print(EMA_B);
          Serial.print("\tmin: ");
          Serial.println(min_b);

          float suggested_threshold = EMA_B - (abs(EMA_A - EMA_B) * 0.3f);
          Serial.print("Suggested threshold: ");
          Serial.println(suggested_threshold);
          EMA_A = 0;
          EMA_B = 0;
          min_b = 0;
          is_ema_procedure = false;
          return;
        } else {
          tone(BUZZER, Notes::As5, 250);
          delay(100);
          tone(BUZZER, Notes::Gs5, 250);
        }

        is_calc_b = !is_calc_b;

        if (is_calc_b) {
          Serial.print("Clench and press button once: ");
        }

        return;
      }


      if (is_calc_b) {

        Serial.println("RECORDING NOW, PRESS TO STOP");
        tone(BUZZER, Notes::G5, 50);
        delay(150);
        tone(BUZZER, Notes::G5, 50);
        delay(50);
        tone(BUZZER, Notes::C6, 50);

        is_calc_ema = true;

      } else {
        Serial.println("RECORDING NOW, PRESS TO STOP");

        tone(BUZZER, Notes::G5, 50);
        delay(150);
        tone(BUZZER, Notes::G5, 50);
        delay(50);
        tone(BUZZER, Notes::C6, 50);

        is_calc_ema = true;
      }
      return;
    }

#ifdef TESTING_TONES
    alarm_running = !alarm_running;
#else
    tone(BUZZER, 1000, 100);
    if (alarm_running) {
      alarm_start = millis();
    }
    alarm_running = false;
#endif

    reset_tune();

    eventoInCorso = false;
    beepCounter = 0;
    ultimoBottone = millis();
    started_sent = false;
    need_alarm_confirmation = false;

    send_event(BUTTON_PRESS);

    if ((millis() - alarm_start) > 15000) {
      if (millis() - last_button_press > 3000) {
        press_count = 0;
        last_button_press = millis();
      } else if (++press_count == 2) {
        press_count = 0;
        // STOP TRACKING SEQUENCE
        tone(BUZZER, Notes::Gs6, Notes::DottedEighth);
        delay(Notes::DottedEighth);
        tone(BUZZER, Notes::F6, Notes::DottedEighth);
        delay(Notes::DottedEighth);
        tone(BUZZER, Notes::Cs6, Notes::DottedEighth);
        delay(Notes::DottedEighth);
        tone(BUZZER, Notes::Ds6, Notes::DottedEighth);

        send_event(TRACKING_STOP);
      }
    }
  }
}

static void button_longer_press(uint8_t btnId, uint8_t btnState) {
  if (btnState == BTN_PRESSED) {
    tone(BUZZER, Notes::Ds6, 250);
    delay(100);
    tone(BUZZER, Notes::Gs6, 250);
    is_ema_procedure = true;
    is_calc_ema = false;
    last_button_press = 0;
    is_calc_b = false;
    EMA_A = 0;
    EMA_B = 0;
    min_b = 0;
    Serial.print("Relax and press button once: ");
    beep_incremental = false;
  }
}
static void button_long_press(uint8_t btnId, uint8_t btnState) {
  if (btnState == BTN_OPEN && !is_ema_procedure) {
    stream_FFT = !stream_FFT;
    tone(BUZZER, stream_FFT ? 2000 : 2400, 50);
    delay(100);
    tone(BUZZER, !stream_FFT ? 2000 : 2400, 50);
    beep_incremental = false;
  } else if (btnState == BTN_PRESSED) {
    beep_incremental = true;
    tone(BUZZER, Notes::Ds6, 150);
    delay(100);
  }
}