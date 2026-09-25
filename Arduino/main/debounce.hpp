#pragma once

struct ButtonDebounce {
  using Callback = void(*)(bool pressed, bool released);

  unsigned long debounceInterval;
  bool lastStableState;
  bool lastRawState;
  unsigned long lastDebounceTime;
  Callback callback;

  ButtonDebounce(unsigned long intervalMs, Callback cb)
    : debounceInterval(intervalMs), lastStableState(false), lastRawState(false),
      lastDebounceTime(0), callback(cb) {}

  inline void update(bool rawState) {
    unsigned long now = millis();

    if (rawState != lastRawState) {
      lastDebounceTime = now;
      lastRawState = rawState;
    }

    if ((now - lastDebounceTime) >= debounceInterval) {
      if (lastStableState != rawState) {
        lastStableState = rawState;
        if (callback) {
          callback(rawState, !rawState);
        }
      }
    }
  }
};