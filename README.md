# bruxism-detector
This small suite helps you detect night bruxism and interrupt it.</br>This issue is waking my up anyways, so why not wake up before damage is done.


What you need:
- Arduino Uno R4 WiFi
- Some way to read EMG. I am using [OLIMEX-EKG-EMG-SHIELD](https://www.olimex.com/Products/Duino/Shields/SHIELD-EKG-EMG/open-source-hardware) and the electrodes + pads
- A Passive Buzzer
- A Push Button
- A way to fix everything in place, I designed a 3D printed model to do so, but it's not perfect
- Conductive gel. Ultrasound gel usually works too, alternatively saliva should work.

Software you need:
- Arduino IDE with libraries "debounce" by Aaron Kimball
- Processing with "Sound" Library
- Python with numpy, pandas, sklearn libraries. Use pip to install these.
