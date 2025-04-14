# Bruxism Detector
This small suite helps you detect night bruxism and interrupt it.</br>Night bruxism is messing with my sleep anyways, so why not wake up before damage is done.

In case I missed any information, check this [Instructable](https://www.instructables.com/Anti-Bruxism-Device-arduino-Based/)

---------------
Recently a huge clean up took place in the main program.</br>Contributing and navigating should be substantially easier now.</br>**Feel free to [provide feedback here](https://github.com/LollosoSi/bruxism-detector/discussions/1)**

---------------
# Contents
- [What to expect](#what-to-expect)
- [What not to expect](#what-not-to-expect)
- [How it works](#how-it-works)
- [Bill of materials](#items-you-need)
- [Software prerequisites](#software-you-need)
- [Placing electrodes](#electrode-placement)
- [Graph and Summary Utility](#graph-and-summary-utility)
- [Android integration](#android-integration)
- [How to use](#how-to-use)
- [Train your SVM Model](#train-your-svm-model)
- [Tune your SVM Model](#tune-your-svm-model)
- [Changing detection settings](#changing-detection-settings)
- [Changing alarm tunes](#changing-alarm-melody)
- [Other files](#other-files)
---------------

# **SAFETY NOTICE**

To reduce risk of electrocution, __NEVER__ connect electrodes to your body when the circuit is also attached to the wall in some way. Through the charger, a laptop, your desktop, etc.

In simple terms, you should only wear electrodes when your circuit is attached to a battery and not to the wall.</br></br>No, attaching to your power bank while it's charging from the wall also isn't okay.

---------------

## **What to expect**
- Monitor your night sessions: the program logs `Clenching`, `Button`, `Beep` and `Alarm` events with timestamps in csv files. Via Processing or the experimental Android App
- Raw data: all SVM results (clenching/not clenching) are logged with timestamps in a `<date>_RAW.csv` file so you can elaborate them later and make a better detection algorithm.
- A graph if you wish to see one. Use `data/RECORDINGS/generator.jar`.
- A csv summary of your collected data. Use `data/RECORDINGS/generator.jar`.
- Beeps and alarms in case clenching is detected. [Configurable](https://github.com/LollosoSi/bruxism-detector#changing-detection-settings).

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
- The processing sketch (`main_logger`) logs your session of clenching events, beeps, alarms, button presses in a CSV file under `data/RECORDINGS/` Folder
- Inside the `data/RECORDINGS/` folder you will find an utility `generator.jar`. Run it to convert your files to graphs, really dirty graphs but you get the idea.

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

## **Graph and summary utility**
An experimental graphing application is available for download and the source can be found at the [Grapher branch](https://github.com/LollosoSi/bruxism-detector/tree/Grapher).
</br>Run it in your `RECORDINGS` folder to:
 - Convert all your tracked data into graphs. Outputs at `Graphs/` folder.
    - Use the command line to generate graphs with a light theme: `java -jar generator.jar light`
    - Use the command line to generate data only for specific files (and `light` can also be inserted here): `java -jar generator.jar light 2025-03-30.csv`
        - The summary will only be generated from the files you feed in.
    - If available, the data from the RAW folder will be included in your final graph.

<img src="https://github.com/user-attachments/assets/59ba7aaf-2119-428b-aa44-89d48d91f769" width="500">
<img src="https://github.com/user-attachments/assets/fda5c161-26d9-4dd8-a8e8-e60518cfff88" width="500">
 </br> </br>
 
 - Collect all stats from your tracked data into a summary. Will generate in `Summary` folder.
 
 <img src="https://github.com/user-attachments/assets/f42cf81d-608b-49da-8114-d7a90c672f4b" width="1000">
 Provided data is only for demonstrative purposes.

## **Android integration**
An experimental Android App is available for download and source can be found at the [Android branch](https://github.com/LollosoSi/bruxism-detector/tree/Android).
1. Open it and start the tracking service to register all events from Arduino.
2. The app also catches alarms from Arduino and tries to wake you up using the phone. Turn the screen on to dismiss the alarm.
3. In case that fails (you still don't stop the alarm after 10 seconds) we consider tracking failed and the alarm on Arduino will ring.
4. Your tracked data will be available under `Documents/RECORDINGS`. Compatible with the grapher application.

## **How to use**
The following will reference `Arduino/main/main.ino` as the main program.
- Mount the shield, electrodes, buttons, buzzer and everything
- Edit `WifiSettings.h` with your WiFi SSID and password (look for `ssid`, `password` variables)
- Load `main.ino` into your Arduino Uno R4 WiFi. If you wish to use a different MCU, adapt `fft_signal_serial_or_udp_output` but I'm not supporting it in the future (at all actually, but I left the option to use ArduinoFFT instead of the arm specific library)
    </br>
### **Train your SVM model:**
  **Note:** This action is disabled after the first 2 minutes of runtime.
  - Long press the button on your arduino until you hear confirmation beeps. The arduino is now streaming the FFT data
  - Run `fft_recorder_for_training`. read console for keys (c, n, s, any other key to suspend recording)
  - (n) Record data in the non clenching state: do not clench, stay still, move, cough, swallow, etc.
  - (any other key) Suspend recording
  - (c) Record data in the clenching state: begin the recording when you are already clenching. Don't go too hard, but do not let go. Add some variability.
  - (any other key) Suspend recording
  - (s) Save
  - Move your `clenching.csv` and `non-clenching.csv` in the same folder of `data_classification_training.py`. Make sure the content of the files is formatted correctly, it surely isn't. Remove `,` at the end of each line.
  - CMD to that folder, run `python data_classification_training.py`.
  - You will get the C++ code to be pasted in the arduino sketch.</br>It will look like this:
  ```Cpp
  static const float weights[] = { 0.00000000, . . . , 0.00517570, };
  static const float bias = -0.2237529517562951;
  ```
  - Edit `Settings.h` with your weights and bias (look for `weights`, `bias` variables) and load it.
    </br>Keep this file open you're going to edit it again.
    </br>
### **Tune your SVM model:**
  - After the new sketch is loaded, you have three options:
    - **Automatic calibration**
      - The python script will suggest a `classification_threshold` value along with your weights.
      - This value is calculated from your training data, as such you're supposed to test it before actual usage.
    - **Manual calibration:**
      - Long press until you hear confirmation beeps. Now look at the console (`500000 baud rate`).
      - You're seeing the evaluation scores for the FFT. Edit `Settings.h` and replace classification_threshold with the lowest score you see when clenching, and above the highest you see when not clenching.</br>Use plotter or whatever to get the idea.
    - **Assisted Calibration**</br>
       **Note:** This action is disabled after the first 2 minutes of runtime.
       - Long press for 10 seconds, until the device stops beeping (ignore the first beep after long pressing, you will toggle fft output if released here).
       - Follow the instructions given in the console:
         - Relax jaw, press to record. Do some movements except clenching. Press to stop recording
         - Clench jaw (not too hard!) before starting the recording. Press to start recording, press again to stop recording.
       - You'll see in output the average values, the minimum and a suggested threshold.
       - If the non clenching values are higher or too near the clenching values (thus the recommended threshold is below or inside the non clenching result) there is a problem.</br>Reposition your electrodes, use conductive gel, stay away from electromagnetic interference etc. (Phone charging nearby could cause this effect)
       - Edit `Settings.h` and replace classification_threshold with the suggested threshold.
</br>**It is recommended to check all procedures in order to fine tune your model.</br>What you want to achieve here is a value that doesn't trigger easily, but doesn't let you clench very hard before triggering either.</br> Too many false positives are going to be annoying, some false positives don't trigger the alarm, see the next section: `Changing detection settings`**
  
  - You're good to go, upload the sketch one last time. Verify everything works as intended.
  - **IMPORTANT** if anything about the FFT is changed, you obviously should to re-train your model
- Run `main_logger` on your computer to start logging, ensure it doesn't go to sleep.
- Wear the electrodes, power the Arduino and you got a minute to get in bed without beeps.
- Find the best position that wears your electrodes and cables the least: the Arduino can be mounted on the wall above your head, or move the bedside table behind your head. This way the electrode cables are straight from your head to the arduino and you can turn in the bed freely enough
- Short press the button three times to stop logging.</br>**Important: to prevent accidental end of tracking, this action is disabled for 15 seconds after dismissing the alarm.**</br>You'll hear a special beep sequence and the processing sketch on your computer will save and close.</br>Unless the packet is lost, in that case try again:</br>UDP can fail. Usually not a big deal for this application, critical signals like alarm triggers have ACK packets to avoid this inconvenience.
- This application is working in multicast at address `239.255.0.1` with ports `4000` (**arduino** sends events here) and `4001` (**arduino** is listening for control codes here).</br>That means you can receive and send control codes from any device in your network, provided that your networks has multicast enabled (standard home networks are okay, android hotspots might not support this).

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
  int tone_num = 1;          // How many notes in the array?
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
|  `uno_r4_wifi_main_program`  |  Older version of the Arduino program  |
|  `processing_fft_spectrum_sketch`  |  Older version of the processing logger program  |
|  `processing_fft_udp_sender_TESTING`  |  Sends random FFT data via UDP and a button press event through the GUI  |
| `simulation_sendserial` |  Sends the `RAW.csv` data contained in the `input.csv` file (bring one from your `RECORDINGS/` to this folder) through Serial to your Arduino in simulation mode. Appends the Arduino output to `output.csv`, create one if not present. Useful for testing new detection and interruption strategies. Currently the simulation is supported only in `fft_signal_serial_or_udp_output` sketch  |

