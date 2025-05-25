class ButtonDebounce {
public:
  using Callback = void(*)(bool pressed, bool released);

  ButtonDebounce(unsigned long debounceIntervalMs = 50, Callback cb = nullptr)
    : debounceInterval(debounceIntervalMs), lastStableState(false), lastRawState(false),
      lastDebounceTime(0), callback(cb) {}

  void setDebounceInterval(unsigned long intervalMs) {
    debounceInterval = intervalMs;
  }

  void setCallback(Callback cb) {
    callback = cb;
  }

  // Call this in your main loop at a fixed frequency
  void update(bool rawState) {
    unsigned long now = millis();

    if (rawState != lastRawState) {
      // Input changed; reset debounce timer
      lastDebounceTime = now;
      lastRawState = rawState;
    }

    if ((now - lastDebounceTime) >= debounceInterval) {
      if (lastStableState != rawState) {
        // State has stabilized and changed
        lastStableState = rawState;
        if (callback) {
          bool pressed = rawState;
          bool released = !rawState;
          callback(pressed, released);
        }
      }
    }
  }

private:
  unsigned long debounceInterval;
  bool lastStableState;
  bool lastRawState;
  unsigned long lastDebounceTime;
  Callback callback;
};
