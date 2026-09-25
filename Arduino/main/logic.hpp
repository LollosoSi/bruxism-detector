#include <EEPROM.h>

#pragma once


#include "user/WifiSettings.h"
#include "user/Settings.h"
#include "runtime_variables.hpp"

#include "wifi.hpp"


// Custom debounce class
#include "debounce.hpp"
const unsigned long updateInterval = 5;  // Debounce sampling interval: 10ms (100 Hz)
unsigned long lastUpdate = 0;

static void button_long_press(bool pressed, bool released);
static void button_short_press(bool pressed, bool released);

static ButtonDebounce button_input_short(50, button_short_press);
static ButtonDebounce button_input_long(800, button_long_press);

// Variabili di stato
bool eventoInCorso = false;
unsigned long inizioEvento = 0;
unsigned long ultimoPositivo = 0;
int beepCounter = 0;
// moved to runtime_variables unsigned long ultimoBottone = 0;

unsigned long inizioFiltraggio = 0;
unsigned long ultimoCampione = 0;
unsigned long periodoCampione = filtraggioIniziale / campioniFiltraggio;
bool filtraggio[campioniFiltraggio] = { false };
uint8_t indice_campione = campioniFiltraggio - 1;

bool started_sent = false;

bool alarm_running = 0;
bool confirm_android_alarm_stopped = false;
inline void alarm_stoppped_confirmed() {
  confirm_android_alarm_stopped = false;
}

bool do_not_alarm = false;
bool do_not_beep = false;

long last_tone_ms = 0, last_flash = 0;

inline void add_to_elements(bool b, float f) {
  data_elements[elements_cursor++] = { millis(), b, f };

  if (elements_cursor == elements_size) {
    send_elements_batch(data_elements);  // Send all at once
    elements_cursor = 0;
  }
}
unsigned long alarm_start = 0;
inline void trigger_alarm() {
  alarm_running = true;
  beepCounter = numeroMaxBeep;
  eventoInCorso = false;
  alarm_start = millis();
  if (is_using_android) {
    need_alarm_confirmation = true;
  }
}

inline void warning_beep() {
  if (do_not_beep)
    return;
  DEBUG_PRINTLN("Beep!");

  send_event(BEEP);
  if (do_not_beep_if_android && is_using_android) {
    return;
  }

  tone(BUZZER, 2600, warning_beep_duration);
  delay(warning_beep_wait);
  // tone(BUZZER, 2600, warning_beep_duration);
}

inline void loop_alarm() {
  if (alarm_running) {

    if (!is_using_android || alarm_even_with_android) {
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
        confirm_android_alarm_stopped = true;
      }
    }
  } else if (confirm_android_alarm_stopped) {
    send_event(CONFIRM_ANDROID_ALARM_STOPPED);
  }
}

/** This function collects campioniFiltraggio samples and if the positive samples are >= (2 * (campioniFiltraggio / 3)) then the system considers a clenching event started.
 * Adds latency, as little as campioniFiltraggio * 1/samplingFrequency
 * 
*/
void trigger_system(int classificazione, float& result, unsigned long tempoAttuale) {
  add_to_elements(classificazione, result);

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
        DEBUG_PRINT(campioniFiltraggio);
        DEBUG_PRINT(" in ");
        DEBUG_PRINT(tempoAttuale - inizioFiltraggio);
        DEBUG_PRINT("ms\tEsito");
        DEBUG_PRINTLN(esitoFiltraggio);
      }

      inizioFiltraggio = tempoAttuale;
    }
  }

  bool isGraceActive = tempoAttuale - ultimoBottone < periodoGrazia;
  grace_left_seconds = isGraceActive ? (periodoGrazia - (tempoAttuale - ultimoBottone)) / 1000 : 0;

  if (isGraceActive || !filtraggioCompletato || alarm_running) {




    return;
  }


  if (esitoFiltraggio) {  // Se il segnale indica clenching

    if (!eventoInCorso) {
      if (tempoAttuale - ultimoPositivo < periodoAttesa) {
        // Se l'evento torna positivo entro 10s, continua l'evento precedente
        eventoInCorso = true;
        DEBUG_PRINTLN("Evento ripreso!");
        send_event(CONTINUED);


      } else {
        // Nuovo evento
        inizioEvento = tempoAttuale;
        eventoInCorso = true;
        beepCounter = 0;
        DEBUG_PRINTLN("Nuovo evento iniziato");
        send_event(DETECTED);
      }
    }
    ultimoPositivo = tempoAttuale;

    if (tempoAttuale - inizioEvento > attesaFiltraggio) {
      if (tempoAttuale - inizioEvento > attesaPrimoBeep + beepCounter * attesaBeep) {
        // do_not_alarm=true ensures alarm does not fire! So if beeps are enabled the device will continue beeping indefinitely
        if (beepCounter < numeroMaxBeep || do_not_alarm) {
          if (beepCounter == 0) {

            send_event(CLENCH_START);

            started_sent = true;
          }
          warning_beep();  // This will beep only if do_not_beep = false



          beepCounter++;
        } else if (!alarm_running) {
          DEBUG_PRINTLN("Allarme attivato!");

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
        DEBUG_PRINTLN("Evento terminato");
        send_event(CLENCH_STOP);

        started_sent = false;
      }
    }
  }
}

unsigned long last_button_press = 0;
uint8_t press_count = 0;

inline void setup_logic() {
  pinMode(BUTTON, INPUT_PULLUP);
}

inline void loop_logic(unsigned long &now) {
  float result = 0;

  if (new_fft_data) {
    new_fft_data = false;
    trigger_system(classify(vReal, result), result, now);


    if (stream_FFT)
      send_to_udp();
  }

  loop_alarm();


  if (now - lastUpdate >= updateInterval) {
    lastUpdate = now;
    bool btread = !digitalRead(BUTTON);
    button_input_short.update(btread);
    if (millis() < 120000) {
      button_input_long.update(btread);
    }
  }
}

static void button_short_press(bool pressed, bool released) {
  if (pressed) {

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

static void button_long_press(bool pressed, bool released) {
  if (pressed) {
    stream_FFT = !stream_FFT;
    tone(BUZZER, Notes::Ds6, 150);
    delay(100);
    tone(BUZZER, stream_FFT ? 2000 : 2400, 50);
    delay(100);
    tone(BUZZER, !stream_FFT ? 2000 : 2400, 50);
  }
}