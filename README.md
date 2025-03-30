# Bruxism Detector
This small suite helps you detect night bruxism and interrupt it.</br>This issue is waking my up anyways, so why not wake up before damage is done.

## **What to expect**
- Monitor your night sessions: the program logs `Clenching`, `Button`, `Beep` and `Alarm` events with timestamps
- Raw data: all SVM results are logged with timestamps in a `<date>_RAW.csv` file so you can run through them and make a better detection algorithm
- A really bad graph if you wish to have one, use `RECORDINGS/generator.jar`

## **How it works**
- Detects jaw clenching / activity through an SVM. (You must train it before usage)
- After clenching is detected, arduino or the processing sketch will beep a number of times, then activate an alarm and wake you up.
- The beep count will reset with time, but if the alarm is fired then you need to press the button to turn it off. After pressing the button you get a grace time to reposition yourself in bed.
- The processing sketch (`processing_fft_spectrum_sketch`) logs your session of clenching events, beeps, alarms, button presses in a CSV file under `RECORDINGS/` Folder
- Inside the `RECORDINGS/` folder you will find an utility `generator.jar`. Run it to convert your files to graphs, really dirty graphs but you get the idea.

## **Items you need**
- Arduino Uno R4 WiFi
- Some way to read EMG. I am using [OLIMEX-EKG-EMG-SHIELD](https://www.olimex.com/Products/Duino/Shields/SHIELD-EKG-EMG/open-source-hardware) and the electrodes + pads
- A Passive Buzzer
- A Push Button
- A way to fix everything in place, I designed a 3D printed model to do so
- Conductive gel. Ultrasound gel usually works too, alternatively saliva should work.

## **Software you need**
- Arduino IDE with libraries "debounce" by Aaron Kimball, CMSIS-DSP
- Processing with "Sound" Library
- Python with numpy, pandas, sklearn libraries. Use pip to install these.

## **How to use**
- Mount the shield, electrodes, buttons, buzzer and everything
- Edit `uno_r4_wifi_main_program` with your WiFi SSID and password (look for `ssid`, `password` variables)
- Load `uno_r4_wifi_main_program` into your Arduino Uno R4 WiFi. If you wish to use a different MCU, adapt `fft_signal_serial_or_udp_output` but I'm not supporting it in the future (at all actually, but I left the option to use ArduinoFFT instead of the arm specific library)
- Train and tune your SVM model:
  - Long press the button on your arduino until you hear confirmation beeps. The arduino is now streaming the FFT data
  - Run `fft_recorder_for_training`. read console for keys (c, n, s, any other key to suspend recording)
  - (n) Record data in the non clenching state (do not clench, stay still, move, cough, swallow, etc)
  - (c) Record data in the clenching state (begin the recording when you are already clenching. Don't go too hard, but do not let go. Add some variability)
  - Save (s)
  - Move your `clenching.csv` and `non-clenching.csv` in the same folder of `data_classification_training.py`. Make sure the content of the files is formatted correctly, it surely isn't. Remove `,` at the end of each line.
  - CMD to that folder, run `python data_classification_training.py`.
  - You will get the C++ code to be pasted in the arduino sketch. Will look like this:```static const float weights[] = { 0.00000000, . . . , 0.00517570, };
static const float bias = -0.2237529517562951;```
  - Edit `uno_r4_wifi_main_program` with your weights and bias (look for `weights`, `bias` variables) and load it
  - Long press the button after the new sketch is loaded. Now look at the console (`500000 baud rate`).
  - You're seeing the evaluation scores for the FFT. In the sketch, edit the `classify` function, specifically `return sum >= 55 <--- Edit this number ? 1 : 0;`. Replace 55 with the lowest score you see when clenching, and above the highest you see when not clenching. Use plotter or whatever to get the idea.
  - You're good to go, upload the sketch one last time. Verify everything works as intended.
  - **IMPORTANT** if anything about the FFT is changed, you obviously should to re-train your model

## **Changing detection settings**

Edit the variables:
| Variable    | Description |
| -------- | ------- |
|  `attesaFiltraggio`  |  Wait milliseconds before marking the start of a clenching event  |
| `attesaPrimoBeep` |  Wait milliseconds after the event begun before the first beep    |
| `attesaBeep`    |  Wait milliseconds between beeps   |
|  `numeroMaxBeep`  |  How many beeps before starting the alarm   |
|  `periodoAttesa`  |  Timeout milliseconds before considering a clenching event finished  |
|  `periodoGrazia`  |  Grace time, milliseconds. Clenching events aren't considered during this period   |
|  `filtraggioIniziale`  |  delay between taking samples of from the SVM. You aren't really getting 100ms each sample because sampling can take longer   |
|  `campioniFiltraggio`  |  How many samples to take before evaluating if the jaw is clenched. Positive results if more than 2/3 of the samples are positive  |

Then upload your new code.

## **Other files**
| File    | Description |
| -------- | ------- |
|  `processing_fft_udp_sender_TESTING`  |  Sends random FFT data via UDP and a button press event through the GUI  |
| `simulation_sendserial` |  Sends the `RAW.csv` data contained in the `input.csv` file (bring one from your `RECORDINGS/` to this folder) through Serial to your Arduino in simulation mode. Appends the Arduino output to `output.csv`, create one if not present. Useful for testing new detection and interruption strategies. Currently the simulation is supported only in `fft_signal_serial_or_udp_output` sketch  |

