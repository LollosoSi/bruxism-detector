package com.example.bruxismdetector;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.function.BiConsumer;

public class SessionTracker {

    String filename1, filename2;
    public Tracker2 servicereference;
    public static final byte CLENCH_START = 0;
    public static final byte CLENCH_STOP = 1;
    public static final byte BUTTON_PRESS = 2;
    public static final byte ALARM_START = 3;
    public static final byte BEEP = 4;
    public static final byte UDP_ALARM_CONFIRMED = 5;
    public static final byte DETECTED = 6;
    public static final byte CONTINUED = 7;
    public static final byte ALARM_STOP = 8;
    public static final byte TRACKING_STOP = 9;
    public static final byte USING_ANDROID = 10;

    public static final byte EVALUATION_RESULT = 11;
    public static final byte SET_EVALUATION_THRESHOLD = 12;
    public static final byte DO_NOT_BEEP_ARDUINO = 13;


    private static final String TAG = "BruxismTracker:SessionTracker";
    String csv_folder_path = "RECORDINGS/";
    Context ctx;
    static long startmillis = 0;
    long millis(){
        return System.currentTimeMillis() - startmillis;
    }
    void setup(Context ctx){
        this.ctx=ctx;
        startmillis = System.currentTimeMillis();

        createRecordingsDirectory();

        Date currentDate = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        String formattedDate = formatter.format(currentDate);


        filename1 = getNewFilename(formattedDate, ".csv", csv_folder_path);
        filename2 = getNewFilename(formattedDate, "_RAW.csv", csv_folder_path+"RAW/");
        file_out = createWriter(filename1);
        append_csv(new String[]{"Millis", "Time", "Event", "Notes", "Duration (seconds)"}, file_out);
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Start", "Tracking started. Date: "+formattedDate}, file_out);

        file_raw_out = createWriter(filename2);
        append_csv(new String[]{"Millis", "Classification"}, file_raw_out);

        Log.d(TAG, "Creating files: " + filename1 + " " + filename2);

        file_out.flush();
        file_raw_out.flush();

    }

    public void writeDailyLog(DailyLogData dld){

        long ms = millis();
        String time = formatted_now();

            // Append mood - this will always be written regardless of value
            String[] moodLabels = {"Sick", "Tired", "Bad", "Neutral", "Good"};
            if (dld.mood >= 0 && dld.mood < moodLabels.length) {
                append_csv(new String[]{
                        String.valueOf(ms),
                        time,
                        "MOOD",
                        moodLabels[dld.mood]
                }, file_out);
            } else {
                append_csv(new String[]{
                        String.valueOf(ms),
                        time,
                        "MOOD",
                        "Unknown"
                }, file_out);
            }

            // Boolean elements (write only if true)
            if (dld.workout) {
                append_csv(new String[]{
                        String.valueOf(ms),
                        time,
                        "INFO",
                        "Workout"
                }, file_out);
            }
            if (dld.hydrated) {
                append_csv(new String[]{
                        String.valueOf(ms),
                        time,
                        "INFO",
                        "Hydrated"
                }, file_out);
            }
            if (dld.stressed) {
                append_csv(new String[]{
                        String.valueOf(ms),
                        time,
                        "INFO",
                        "Stressed"
                }, file_out);
            }
            if (dld.caffeine) {
                append_csv(new String[]{
                        String.valueOf(ms),
                        time,
                        "INFO",
                        "Caffeine"
                }, file_out);
            }
            if (dld.anxious) {
                append_csv(new String[]{
                        String.valueOf(ms),
                        time,
                        "INFO",
                        "Anxious"
                }, file_out);
            }
            if (dld.alcohol) {
                append_csv(new String[]{
                        String.valueOf(ms),
                        time,
                        "INFO",
                        "Alcohol"
                }, file_out);
            }
            if (dld.lateDinner) {
                append_csv(new String[]{
                        String.valueOf(ms),
                        time,
                        "INFO",
                        "LateDinner"
                }, file_out);
            }
            if (dld.medications) {
                append_csv(new String[]{
                        String.valueOf(ms),
                        time,
                        "INFO",
                        "Medications"
                }, file_out);
            }
            if (dld.pain) {
                append_csv(new String[]{
                        String.valueOf(ms),
                        time,
                        "INFO",
                        "Pain"
                }, file_out);
            }
            if (dld.lifeEvent) {
                append_csv(new String[]{
                        String.valueOf(ms),
                        time,
                        "INFO",
                        "LifeEvent"
                }, file_out);
            }
        }

    public void close(){
        // Place here the code you want to execute on exit
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "End", "Tracking ended"}, file_out);
        file_out.flush(); // Writes the remaining data to the file
        file_out.close(); // Finishes the file

        file_raw_out.flush();
        file_raw_out.close();
    }


    PrintWriter file_out;
    PrintWriter file_raw_out;

    private File recordingsDirectory;
    private File rawRecordingsDirectory;

    PrintWriter createWriter(String filename) {
        try {
            File file = new File(filename);
            return new PrintWriter(file);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Error creating PrintWriter for file: " + filename, e);
            return null; // Or handle the error in another way
        }
    }


    double[] fftData; // This will store the FFT data
    int sampleCount = 128;
    long samplingFrequency = 2000;
    double maxMagnitude = 5000; // To track the maximum magnitude for normalization
    boolean did_print_sync = false;
    boolean remote_button_pressed = false;
    boolean confirmed_udp_alarm = false;

    long cstart = 0;
    boolean alarmed = false;
    boolean clenching=false;


    String formatted_now() {
        // Get the current time
        Calendar calendar = Calendar.getInstance();

        // Format the time as a string
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    void append_csv(String[] data, PrintWriter out) {

        for (int i = 0; i < data.length; i++) {
            if (i!=0)
                out.print(";");
            out.print(data[i]);
        }
        out.println();

        out.flush();
    }



    private void createRecordingsDirectory() {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        recordingsDirectory = new File(documentsDir, "RECORDINGS");
        rawRecordingsDirectory = new File(recordingsDirectory, "RAW");

        if (!recordingsDirectory.exists()) {
            if (!recordingsDirectory.mkdirs()) {
                Log.e(TAG, "Failed to create RECORDINGS directory");
            }
        }
        if (!rawRecordingsDirectory.exists()) {
            if (!rawRecordingsDirectory.mkdirs()) {
                Log.e(TAG, "Failed to create RAW directory");
            }
        }
    }

    public String getNewFilename(String baseName, String extension, String folderName) {
        File storageDir;
        if (folderName.equals("RECORDINGS/")) {
            storageDir = recordingsDirectory;
        } else if (folderName.equals("RECORDINGS/RAW/")) {
            storageDir = rawRecordingsDirectory;
        } else {
            Log.e("FileHelper", "Invalid folder name");
            return null;
        }

        if (storageDir == null) {
            Log.e("FileHelper", "External storage directory is null");
            return null;
        }

        // Ensure the folder exists
        if (!storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                Log.e("FileHelper", "Failed to create directory: " + storageDir.getAbsolutePath());
                return null;
            }
        }

        String relativePath = baseName + extension;
        File file = new File(storageDir, relativePath);

        int count = 1;
        while (file.exists()) {
            relativePath = baseName + " (" + count + ")" + extension;
            file = new File(storageDir, relativePath);
            count++;
        }

        return file.getAbsolutePath();
    }

    // Central function to process data from both UDP and Serial sources
    public void processData(byte[] data, int length) {
        Log.d(TAG, "Packlen: " + length);
        try {
            if (length == 4) {
                // Processing initial parameters (4 bytes: two uint16_t values)
                samplingFrequency = ((data[0] & 0xFF) | ((data[1] & 0xFF) << 8)); // Little-endian uint16_t
                sampleCount = ((data[2] & 0xFF) | ((data[3] & 0xFF) << 8));       // Little-endian uint16_t

                // Reinitialize fftData array based on the new sample count
                fftData = new double[sampleCount / 2];  // Adjust the array size properly
                maxMagnitude = 0;

                Log.d(TAG, "Received Parameters: fs=" + samplingFrequency + ", samples=" + sampleCount);


            } else if (length % 5 == 0) {  // Ensure it's a multiple of 5
                long millisforsync = millis();

                int count = length / 5;   // Number of elements in the packet

                for (int i = 0; i < count; i++) {
                    int offset = i * 5;

                    long timestamp = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
                    boolean value = (data[offset + 4] != 0);

                    append_csv(new String[]{String.valueOf(timestamp), String.valueOf(value)}, file_raw_out);
                    Log.d(TAG, "Received RAW: " + timestamp + ", " + value);
                }

                if (!did_print_sync) {
                    did_print_sync = true;
                    append_csv(new String[]{String.valueOf(millisforsync), formatted_now(), "Sync", String.valueOf(ByteBuffer.wrap(data, (count-1) * 5, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL)}, file_out);
                }
            } else if (length==1) {
                int val = (data[0] & 0xFF);

                switch(val) {
                    default:
                        Log.d(TAG, "Received unrecognized byte value: "+val);
                        break;
                    case CLENCH_START:
                        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Clenching", "STARTED"}, file_out);
                        cstart=millis();
                        clenching=true;
                        break;
                    case CLENCH_STOP:
                        clenching=false;
                        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Clenching", "STOPPED", String.valueOf((millis()-cstart)/1000.0)}, file_out);
                        break;
                    case BUTTON_PRESS:
                        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Button"}, file_out);
                        remote_button_pressed = true;
                        if (alarmed)
                            append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Alarm", "STOPPED"}, file_out);
                        if (clenching) {
                            clenching=false;
                            append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Clenching", "STOPPED", String.valueOf((millis()-cstart)/1000.0)}, file_out);
                        }
                        alarmed=false;
                        //servicereference.runAlarm();
                        break;
                    case ALARM_START:
                        if (!alarmed) {
                            append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Alarm", "STARTED"}, file_out);
                            alarmed=true;
                            servicereference.runAlarm();
                            servicereference.sendUDP(new byte[]{UDP_ALARM_CONFIRMED});
                        }
                        break;
                    case ALARM_STOP:
                        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Alarm", "STOPPED"}, file_out);
                        alarmed=true;
                        break;
                    case BEEP:
                        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Beep", "WARNING BEEP"}, file_out);
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
                        if(!prefs.getBoolean("arduino_beep", true)){
                            Intent intent = new Intent(ctx, RingReceiver.class);
                            intent.setAction(RingReceiver.beep_once); // Use the constant here
                            ctx.sendBroadcast(intent);
                        }

                            break;

                    case UDP_ALARM_CONFIRMED:
                        confirmed_udp_alarm = true;
                        Log.d(TAG, "Confirmed alarm via UDP");
                        break;

                    case DETECTED:
                        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "CLENCHING", "FIRST DETECTION"}, file_out);
                        break;

                    case CONTINUED:
                        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "CLENCHING", "CONTINUED"}, file_out);
                        break;

                    case USING_ANDROID:

                        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "ANDROID", "Using android from here"}, file_out);
                        break;

                    case TRACKING_STOP:
                        servicereference.exit();
                        break;
                }
            } else {

            }
        }
        catch (Exception e) {
            Log.d(TAG, "Error processing data: " + e.getMessage());
        }
    }

    public void makeGraph(){

    }

}
