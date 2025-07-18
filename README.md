# Bruxism Detector
This small device + software suite helps you detect bruxism and interrupt it, while trying to track down its triggers.</br>

## Overview
Following this readme, you will build an Arduino based device that is able to detect bruxism.</br><sub><sup>Basic knowledge about electronics is advisable.</sup></sub></br></br>
Bruxism detection is done through a Machine Learning algorithm and 3 electrodes placed on the forehead.
</br>Bruxing events are interrupted in two ways:
1. A warning beep to interrupt it **without waking up** the user ([conditioning](https://en.wikipedia.org/wiki/Classical_conditioning))
2. An alarm that is supposed to wake up the user.</br>
   The alarm can be:
   - A melody played by the Arduino device
   - A vibration if you are using the Android app

In case I missed any information, check this [Instructable](https://www.instructables.com/Anti-Bruxism-Device-arduino-Based/)
</br>
<sub>Night bruxism is messing with my sleep anyways, so why not wake up before damage is done.</sub>
<hr></hr>
<div hspace="20">
<img align="left" src="https://github.com/user-attachments/assets/f6ab8171-b3ae-428b-a4ee-0c84f47bf78b" height="350">
   </br>

<p>
A full assembly of the modular device, featuring:
</br>- The core module
</br>- Wall mount
</br>- Phone mount
</br>- Battery module.
</p>
<hr></hr>

<h3>Let's keep in touch!</h3>

Use [discussions](https://github.com/LollosoSi/bruxism-detector/discussions) for support and feature development

Use [issues](https://github.com/LollosoSi/bruxism-detector/issues) for problems within the code.
</div>
</br>
<hr></hr>


# Contents
- [What to expect](#what-to-expect)
- [What not to expect](#what-not-to-expect)
- [How it works](#how-it-works)
- [Daytime training](#daytime-training)
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
- Raw data: all SVM results (clenching/not clenching and confidence) are logged with timestamps in a `<date>_RAW.csv` file so you can elaborate them later and make a better detection algorithm.
- Match your data with sleep data from your sleep tracker*
- A graph if you wish to see one. Use `generator.jar` or the Android app.
- A summary of your collected data. Use `generator.jar` or the Android app.
- Beeps and alarms in case clenching is detected. [Configurable](https://github.com/LollosoSi/bruxism-detector#changing-detection-settings).
- Detect correlations between your tagged sessions <sub>(Android)</sub>

<sub>*You will need to export the tracker data to a compatible format</sub>

## What **NOT** to expect
- Magic
- Holding the author accountable for this work. This software is distributed with no guarantees.

## **How it works**
- Detects jaw clenching / activity through a Machine Learning algorithm. (SVM. You must train it before usage)
- After clenching is detected, arduino or the processing sketch will beep a number of times, then activate an alarm and wake you up.
- The beep count will reset with time, but if the alarm is fired then you need to press the button to turn it off. After pressing the button you get a grace time to reposition yourself in bed.
- The processing sketch (`main_logger`) logs your session of clenching events, beeps, alarms, button presses in a CSV file under `data/RECORDINGS/` Folder
- Inside the `data/RECORDINGS/` folder you will find an utility `generator.jar`. Run it to convert your files to graphs.

## **Daytime Training**
Using the Android app, you can enable the "trainer", which works as follows:
 - You will randomly hear the `warning beep` once every 30 minutes to 2 hours until 19:00, the same tone will be played at night when clenching is detected.
 - During the day, relax your jaw when you hear the tone.
 - Have the app start this trainer automatically once tracking ends, so you don't need to remember to launch it every day.
 - When you start relaxing the jaw without thinking about the beep, conditioning should start to show its effects.
For more information, [see here](https://en.wikipedia.org/wiki/Classical_conditioning)

### **Alternative daytime usage**
[A belt adapter is available.](https://www.printables.com/model/1259715-belt-addon-for-modular-bruxism-detector-enclosure)
</br>The tracking device can be worn on your belt, so it can help you become more aware of daytime clenching.
</br>Mostly for home usage.

## **Items you need**
- Arduino Uno R4 WiFi
- Some way to read EMG. I am using [OLIMEX-EKG-EMG-SHIELD](https://www.olimex.com/Products/Duino/Shields/SHIELD-EKG-EMG/open-source-hardware) and the electrodes + pads
- A Passive Buzzer (12mm diameter). Connect the positive lead to Arduino Pin 5, negative lead to GND.
- A Push Button (12x12mm base). Connect one lead to Arduino Pin 3, the other end to GND.
- [A way to fix everything in place, I designed a 3D printed model to do so](https://www.printables.com/model/1251532-bruxism-detector-modular-enclosure)
- Conductive gel. Ultrasound gel usually works too, alternatively saliva should work.
- An elastic band to hold the electrodes together and make it reusable. [Edit this model to the right size to hold your band together](https://www.thingiverse.com/thing:4264338).
- (optional) a sleep tracker

## **Software you need**
- Arduino IDE with "CMSIS-DSP" Library
- Processing with "Sound" Library
- Python with numpy, pandas, sklearn, openpyxl libraries. Use pip to install these.

## **Electrode Placement**
You must place 3 electrodes in total. One is ground and should be placed away from the muscle you want to monitor, the two others should be placed on the muscle.

In this case we want to monitor activity of the masticatory muscles. the most accessable of these muscles is the temporalis. This also leads to the most practical electrode placement - across the forehead. 

Place:
- The ground electrode around the center of your forehead (away from the temples)
- The other electrodes can be placed symmetrically near the temples. To find the exact spot: place a finger on your temples, clench your jaw slightly. The perfect position is where you feel the muscles contracting with your fingers. In some cases the muscle can even be seen contracting in that spot.
- **NOTE:** move hair out of the way when placing the electrodes.
- **NOTE:** do not use the electrodes without conductive gel or replacement. It's not going to work well if at all.

## **Graph and summary utility**
An experimental graphing application is available for download and the source can be found at the [Grapher branch](https://github.com/LollosoSi/bruxism-detector/tree/Grapher).
</br>Run it in your `RECORDINGS` folder to:
 - Download all your tracked data from Android (Click "Receive from android" and in the Android app tap "send to pc")
 - Convert all your tracked data into graphs. Outputs at `Graphs/` folder.
    - Use the command line to generate graphs with a light theme: `java -jar generator.jar light`
    - Use the command line to generate data only for specific files (and `light` can also be inserted here): `java -jar generator.jar light 2025-03-30.csv`
        - The summary will only be generated from the files you feed in.
    - If available, the data from the RAW folder will be included in your final graph.
    - If available, your sleep data (sleep phases, heart rate, stress, SpO2) will be included in your final graph.

<img src="https://github.com/user-attachments/assets/5aa7a13f-8b68-4de8-a1e0-e659ea20a1b8" width="800">

 </br> </br>
 
 - Collect all stats from your tracked data into a summary. Will generate in `Summary` folder.
 
 <img src="https://github.com/user-attachments/assets/f42cf81d-608b-49da-8114-d7a90c672f4b" width="1000">
 Provided data is only for demonstrative purposes.

## **Android integration**
An experimental Android App is available for download and source can be found at the [Android branch](https://github.com/LollosoSi/bruxism-detector/tree/Android).
1. If `Autostart Service` is enabled, you will need to follow this only once.
   - When tracking ends, you will still be asked about your day.
   - The app will always use your last `Session Settings` 
3. Open it, set the pertinent switches about your day and tap `Start` to register all events from Arduino.
4. The app will catch beeps from Arduino: you can configure your phone to beep with a certain volume or into headphones to avoid disturbing others' sleep
5. The app will also catch alarms: it will try to wake you up more gently by vibrating.</br>Turning the screen on/off counts as button press and dismisses the alarm.
   - In case the vibration alarm fails to wake you up in 10 seconds, we consider something failed and the Arduino will ring instead.
   - Ensure a stable wifi connection.</br>If your phone disconnects you will miss events in the final logs.
   - Say the vibration alarm failed or there are network issues.</br>Arduino will operate without the smartphone until the app registers itself again. Happens once around every 15 minutes.
6. Your tracked data will be available under `Documents/RECORDINGS`.
   - To see your graphs on the app, tap `Sessions`
   - To see your summary, tap `Summary`
   - Tap `PC` to send your data to the Grapher program on your computer (which will be listening after clicking `Receive from android`)
   - Tap `Smartband DB` if you are using a Mi Band to track your sleep with Mi Fitness, and you have either a rooted device or the database (`fitness_data`) file
7. Spot correlations with tagged sessions in `Summary -> Correlations`. You will get an overview.


<div style="display: flex; justify-content: space-between;">
<img src="https://github.com/user-attachments/assets/6cb34f1d-6877-4013-b543-780975eb5b38" width="22%">
<img src="https://github.com/user-attachments/assets/6540fea7-7fa0-43f4-8ec4-44cf723825cd" width="22%">
<img src="https://github.com/user-attachments/assets/4aea7393-ac43-4a46-8ecf-63a68c1f5d58" width="22%">

<img src="https://github.com/user-attachments/assets/4eb218aa-4276-425f-a224-c61f6c4f5fdd" width="22%">
<img src="https://github.com/user-attachments/assets/a203d53f-90f2-40be-b3c0-0d642c10d711" width="22%">

<img src="https://github.com/user-attachments/assets/50bf9dab-e19e-45f1-a366-73962ad19c13" width="22%">

</div>



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
  - After the new sketch is loaded, you have four options:
    - **Android calibration**
      - Start your Arduino with the trained SVM model
      - Long press the button to enable FFT streaming
      - The app will detect the data from the main screen
      - You will see the classification data in realtime
      - Tune the slider to the perfect value
      - Press "Awesome" button
      - Check "Use threshold for tracking: *your value*"
      - The app will set the threshold periodically
      - (UNTESTED) You might still detect clenching without training the model if you set your custom threshold.
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

