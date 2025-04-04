# Bruxism Detector
This small suite helps you detect night bruxism and interrupt it.</br>Night bruxism is messing with my sleep anyways, so why not wake up before damage is done.

In case I missed any information, check this [Instructable](https://www.instructables.com/Anti-Bruxism-Device-arduino-Based/)

---------------
Recently a huge clean up took place in the main program.</br>Contributing and navigating should be substantially easier now.</br>**Feel free to [provide feedback here](https://github.com/LollosoSi/bruxism-detector/discussions/1)**

---------------

# **SAFETY NOTICE**

To reduce risk of electrocution, __NEVER__ connect electrodes to your body when the circuit is also attached to the wall in some way. Through the charger, a laptop, your desktop, etc.

In simple terms, you should only wear electrodes when your circuit is attached to a battery and not to the wall.</br></br>No, attaching to your power bank while it's charging from the wall also isn't okay.

---------------

## **What to expect**
- Monitor your night sessions: the program logs `Clenching`, `Button`, `Beep` and `Alarm` events with timestamps
- Raw data: all SVM results (clenching/not clenching) are logged with timestamps in a `<date>_RAW.csv` file so you can elaborate them later and make a better detection algorithm
- A really bad graph if you wish to see one. Use `RECORDINGS/generator.jar`
- Beeps and alarms in case clenching is detected. [Configurable](https://github.com/LollosoSi/bruxism-detector#changing-detection-settings)

## What **NOT** to expect
- Magic
- Miracles
- Permanent fixes
- Everything to work flawlessly without a drop of `know-how`
- Holding the author accountable for this work. This software is distributed as-is with no guarantees.

## **How it works**
- Detects jaw clenching / activity through a Machine Learning algorithm. (SVM. You must train it before usage)
- After clenching is detected, arduino or the processing sketch will beep a number of times, then activate an alarm and wake you up.
- The beep count will reset with time, but if the alarm is fired then you need to press the button to turn it off. After pressing the button you get a grace time to reposition yourself in bed.
- The processing sketch (`processing_fft_spectrum_sketch`) logs your session of clenching events, beeps, alarms, button presses in a CSV file under `RECORDINGS/` Folder
- Inside the `RECORDINGS/` folder you will find an utility `generator.jar`. Run it to convert your files to graphs, really dirty graphs but you get the idea.

## **Items you need**
- Arduino Uno R4 WiFi
- Some way to read EMG. I am using [OLIMEX-EKG-EMG-SHIELD](https://www.olimex.com/Products/Duino/Shields/SHIELD-EKG-EMG/open-source-hardware) and the electrodes + pads
- A Passive Buzzer
- A Push Button
- [A way to fix everything in place, I designed a 3D printed model to do so](https://www.printables.com/model/1251532-bruxism-detector-modular-enclosure)
- Conductive gel. Ultrasound gel usually works too, alternatively saliva should work.
- An elastic band to hold the electrodes together and make it reusable

## **Software you need**
- Arduino IDE with libraries "debounce" by Aaron Kimball, "CMSIS-DSP"
- Processing with "Sound" Library
- Python with numpy, pandas, sklearn libraries. Use pip to install these.

## **Electrode Placement**
You must place 3 electrodes in total. One is ground and should be placed away from the muscle you want to monitor, the two others should be placed on the muscle.

In this case we want to monitor the masseter activity, so the most practical placement is probably across the forehead. 

Place:
- The ground electrode around the center of your forehead (away from the masseter)
- The other electrodes can be placed symmetrically near the temples. To find the exact spot: place a finger on your temples, clench your jaw slightly. The perfect position is where you feel the muscles contracting with your fingers. In some cases the muscle can even be seen contracting in that spot.
- **NOTE:** move hair out of the way when placing the electrodes.
- **NOTE:** do not use the electrodes without conductive gel or replacement. It's not going to work well if at all.

## **How to use**
The following will reference `Arduino/main/main.ino` as the main program.
- Mount the shield, electrodes, buttons, buzzer and everything
- Edit `WifiSettings.h` with your WiFi SSID and password (look for `ssid`, `password` variables)
- Load `main.ino` into your Arduino Uno R4 WiFi. If you wish to use a different MCU, adapt `fft_signal_serial_or_udp_output` but I'm not supporting it in the future (at all actually, but I left the option to use ArduinoFFT instead of the arm specific library)
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
  - Edit `Settings.h` with your weights and bias (look for `weights`, `bias` variables) and load it.
    </br>Keep this file open you're going to edit it again.
  - Long press the button after the new sketch is loaded, you'll hear confirmation beeps. Now look at the console (`500000 baud rate`).
  - You're seeing the evaluation scores for the FFT. Edit `Settings.h` and replace classification_threshold with the lowest score you see when clenching, and above the highest you see when not clenching. Use plotter or whatever to get the idea.
  - You're good to go, upload the sketch one last time. Verify everything works as intended.
  - **IMPORTANT** if anything about the FFT is changed, you obviously should to re-train your model
- Run `processing_fft_spectrum_sketch` on your computer to start logging, ensure it doesn't go to sleep.
- Wear the electrodes, power the Arduino and you got a minute to get in bed without beeps.
- Find the best position that wears your electrodes and cables the least: the Arduino can be mounted on the wall above your head, or move the bedside table behind your head. This way the electrode cables are straight from your head to the arduino and you can turn in the bed freely enough

## **Changing detection settings**

Edit the variables under `Settings.h`:
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

## **Changing alarm melody**
A full notes and duration definitions is included for convenience. There is a whole `Notes` section in `Settings.h` and it's pretty much self explanatory.
</br>Look into `Notes.h` for the definitions.
</br>Everything is in the `Notes` namespace, tunes included.
</br>To sum up:
```Cpp

// Select the index of the tune to be played (0 is Other_tune, n-1 is drier) (see tunes[] array)
uint8_t playtune = 0;

struct tune {
  int tone_num = 1;            // How many notes in the array?
  unsigned int tones[];      // Frequencies in Hz
  unsigned int durations[];  // Duration of each note in milliseconds
  unsigned int waits[];      // Delay between notes in milliseconds
};

// Example tune:
namespace Notes{
  tune drier{
    4,
    { G6, A6, B6, C7 },
    { Quarter, Quarter, Quarter, Half },
    { Eighth, Eighth, Eighth, Whole*4 }
  };
}

// Register your tune into the array
tune* tunes[] = {  &Notes::Other_tune, . . . , &Notes::drier };

```

## **Other files**
| File    | Description |
| -------- | ------- |
|  `uno_r4_wifi_main_program`  |  Older version of the program  |
|  `processing_fft_udp_sender_TESTING`  |  Sends random FFT data via UDP and a button press event through the GUI  |
| `simulation_sendserial` |  Sends the `RAW.csv` data contained in the `input.csv` file (bring one from your `RECORDINGS/` to this folder) through Serial to your Arduino in simulation mode. Appends the Arduino output to `output.csv`, create one if not present. Useful for testing new detection and interruption strategies. Currently the simulation is supported only in `fft_signal_serial_or_udp_output` sketch  |

