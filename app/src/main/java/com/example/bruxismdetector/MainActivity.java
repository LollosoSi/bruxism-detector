package com.example.bruxismdetector;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private MulticastSocket receiveSocket;
    private DatagramSocket sendSocket;
    private InetAddress multicastAddress;
    boolean running = false;
    private int sendPort;
    private int receivePort;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
private static final String TAG = "Main activity";
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    private SeekBar seekBarMood;
    private TextView moodSelectedText;

    private final String[] moodLabels = {"Good", "Neutral", "Bad", "Tired", "Sick"};
    private final int[] moodColors = {
            R.color.material_green_500,  // Good
            R.color.material_blue_500,   // Neutral
            R.color.material_orange_500, // Bad
            R.color.material_yellow_500, // Tired
            R.color.material_red_500     // Ill
    };

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);
        } else {

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            } else {
                // Permission denied, handle accordingly (e.g., show a message)
            }
        }
    }

    boolean is_user_editing_classification_thumb = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

      //  if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requestStoragePermission();
       // } else {

        //}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }

        seekBarMood = findViewById(R.id.seekBar_mood);
        moodSelectedText = findViewById(R.id.mood_selected_text);

        updateMoodDisplay(seekBarMood.getProgress()); // Initialize text and color

        seekBarMood.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateMoodDisplay(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {  }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {  }
        });

        setupSwitchLabels();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);


        SwitchMaterial swsh = (SwitchMaterial)findViewById(R.id.switch_sharedpref).findViewById(R.id.switch_item);
        swsh.setChecked(prefs.getBoolean("start_trainer_after_tracker_ends", false));

        swsh.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("start_trainer_after_tracker_ends", swsh.isChecked()).apply();  // or false when unchecked

            }
        });

        SwitchMaterial swthr = (SwitchMaterial)findViewById(R.id.switch_sharedpref_use_threshold).findViewById(R.id.switch_item);
        swthr.setChecked(prefs.getBoolean("use_threshold", false));

        swthr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("use_threshold", swthr.isChecked()).apply();  // or false when unchecked

            }
        });

        
        SeekBar sbar = (SeekBar)findViewById(R.id.reception);
        sbar.setMax(100);
        sbar.setMin(0);
        sbar.setProgress(50);      // User cursor
        sbar.setSecondaryProgress(30);    // Dynamic underlying bar

        sbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Called when progress is changed

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                is_user_editing_classification_thumb=true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                is_user_editing_classification_thumb=false;
                prefs.edit().putInt("classification_threshold", sbar.getProgress()).apply();
                setSwitchThreshold_sharedpref_text();
                sendUDP(new byte[]{12, (byte)(sbar.getProgress() & 0xFF), (byte)((sbar.getProgress() >> 8) & 0xFF)});
            }
        });


        setSwitchThreshold_sharedpref_text();
        setupUDP(4001, 4000);
    }

    private void updateMoodDisplay(int index) {
        // Update text
        String selectedMood = moodLabels[4-index];
        moodSelectedText.setText("Selected: " + selectedMood);

        // Update color
        int color = ContextCompat.getColor(this, moodColors[4-index]);
        moodSelectedText.setTextColor(color);

        // Set color for seekbar track and thumb
        seekBarMood.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        seekBarMood.getThumb().setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private void setupSwitchLabels() {
        Map<Integer, String> switchLabelMap = new HashMap<>();

        switchLabelMap.put(R.id.row_workout, "Done workout");
        switchLabelMap.put(R.id.row_hydrated, "Well hydrated");
        switchLabelMap.put(R.id.row_stressed, "Felt stressed");
        switchLabelMap.put(R.id.row_caffeine, "Had caffeine");
        switchLabelMap.put(R.id.row_anxious, "Felt anxious");

        switchLabelMap.put(R.id.row_alcohol, "Had alcohol");
        switchLabelMap.put(R.id.row_late_dinner, "Late dinner or skipped meals");
        switchLabelMap.put(R.id.row_medications, "Took medications");
        switchLabelMap.put(R.id.row_pain, "Felt pain");
        switchLabelMap.put(R.id.row_life_event, "Significant life event");
        switchLabelMap.put(R.id.switch_sharedpref, "Start trainer after tracker ends");

        for (Map.Entry<Integer, String> entry : switchLabelMap.entrySet()) {
            View row = findViewById(entry.getKey());
            if (row != null) {
                TextView switchMaterial = row.findViewById(R.id.switch_label);
                if (switchMaterial != null) {
                    switchMaterial.setText(entry.getValue());
                }
            }
        }
    }

    void setSwitchThreshold_sharedpref_text(){
        View row = findViewById(R.id.switch_sharedpref_use_threshold);
        if (row != null) {
            TextView switchMaterial = row.findViewById(R.id.switch_label);
            if (switchMaterial != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                switchMaterial.setText("Use threshold for tracking: " + prefs.getInt("classification_threshold", 0));
            }
        }
    }

    @Override
    protected void onDestroy() {

        Log.d(TAG, "The activity is destroyed");

        closeSockets();
        if (executor != null) {
            executor.shutdownNow(); // Shutdown the executor
        }

        //reOpenApp();
        //if(isServiceRunning(Tracker2.class))
        //    Toast.makeText(this, "You closed the Bruxism app, the alarm will NOT fire!", Toast.LENGTH_LONG).show();
        super.onDestroy();
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);
        for (ActivityManager.RunningServiceInfo service : runningServices) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    private void reOpenApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void startService(View v) {
        Intent intent = new Intent(this, Tracker2.class);

        // Mood SeekBar
        SeekBar moodSeekBar = findViewById(R.id.seekBar_mood);
        int moodValue = moodSeekBar.getProgress(); // 0 = Ill, 4 = Good
        intent.putExtra("mood", moodValue);

        // Map of switch row IDs and their keys
        Map<Integer, String> switchKeyMap = new HashMap<>();
        switchKeyMap.put(R.id.row_workout, "workout");
        switchKeyMap.put(R.id.row_hydrated, "hydrated");
        switchKeyMap.put(R.id.row_stressed, "stressed");
        switchKeyMap.put(R.id.row_caffeine, "caffeine");
        switchKeyMap.put(R.id.row_anxious, "anxious");
        switchKeyMap.put(R.id.row_alcohol, "alcohol");
        switchKeyMap.put(R.id.row_late_dinner, "late_dinner");
        switchKeyMap.put(R.id.row_medications, "medications");
        switchKeyMap.put(R.id.row_pain, "pain");
        switchKeyMap.put(R.id.row_life_event, "life_event");

        // Loop through switch rows and collect values
        for (Map.Entry<Integer, String> entry : switchKeyMap.entrySet()) {
            View row = findViewById(entry.getKey());
            if (row != null) {
                SwitchMaterial sw = row.findViewById(R.id.switch_item);
                if (sw != null) {
                    intent.putExtra(entry.getValue(), sw.isChecked());
                }
            }
        }

        // Start the service
        ContextCompat.startForegroundService(this, intent);
        finish();
    }


    public void sendMyFolder(View v) {
        new Thread(() -> {
            String serverIp = ServerDiscovery.discoverServerIP();
            if (serverIp == null) {
                Log.e("Send", "Server not found.");
                return;
            }

            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File recordingsDir = new File(documentsDir, "RECORDINGS");
            FileSenderClient.sendFolder(recordingsDir, recordingsDir, serverIp, 5000, this);
        }).start();
    }

    public void startTrainer(View v) {
        Intent intent = new Intent(this, RingReceiver.class);
        sendBroadcast(intent);
        //RingReceiver.schedule(this);
        //Toast.makeText(this, "The phone will beep randomly every 30 minutes to 2 hours", Toast.LENGTH_LONG).show();
        showAdviceDialogIfNeeded(this);
    }

    public void stopTrainer(View v) {
        RingReceiver.cancel(this);
        Toast.makeText(this, "Trainer canceled", Toast.LENGTH_SHORT).show();

    }

    private void showAdviceDialogIfNeeded(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean shouldShow = prefs.getBoolean("show_advice", true);

        if (!shouldShow) return;

        new AlertDialog.Builder(context)
                .setTitle("About this trainer")
                .setMessage("Your phone will beep randomly every 30 minutes to 2 hours.\n - When you hear the beep, relax your jaw.\n - To temporarily mute beeping, turn down notifications volume.\n - Beeps end at 19\n\nThis is required for better chances of conditioning night bruxism without waking up.\n - When you eventually relax without thinking about the beep, that's around the time you should see an improvement.")
                .setPositiveButton("Awesome", null)
                .setNegativeButton("Don't show again", (dialog, which) -> {
                    prefs.edit().putBoolean("show_advice", false).apply();
                })
                .setCancelable(false)
                .show();
    }


    public void hideReception(View v){
        running=false;
        closeSockets();
        if (executor != null) {
            executor.shutdownNow(); // Shutdown the executor
        }

        findViewById(R.id.main_container).setVisibility(View.VISIBLE);
        findViewById(R.id.reception_layout).setVisibility(View.GONE);


    }

    public void setupUDP(int sendPort, int receivePort) {
        try {
            this.sendPort = sendPort;
            this.receivePort = receivePort;
            multicastAddress = InetAddress.getByName("239.255.0.1");

            // Set up receiving socket
            receiveSocket = new MulticastSocket(receivePort);
            receiveSocket.joinGroup(multicastAddress);
            receiveSocket.setReuseAddress(true);

            // Set up sending socket
            sendSocket = new DatagramSocket();
            sendSocket.setReuseAddress(true);

            running = true;
            executor.execute(this::receiveUDP);
            Log.d(TAG, "UDP setup complete. Receiving on port " + receivePort + ", sending on port " + sendPort);

        } catch (IOException e) {
            Log.e(TAG, "Error setting up UDP", e);
        }
    }
    private void closeSockets() {
        running = false;
        if (receiveSocket != null && !receiveSocket.isClosed()) {
            try {
                receiveSocket.leaveGroup(multicastAddress);
                receiveSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing receive socket", e);
            }
        }
        if (sendSocket != null && !sendSocket.isClosed()) {
            sendSocket.close();
        }
    }

    int min_result = 0, max_result = 0;

    public void receiveUDP() {
        byte[] buffer = new byte[10000];

        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        WifiManager.MulticastLock multicastLock = wifiManager.createMulticastLock("multicastLock");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();


        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiveSocket.receive(packet);
                byte[] data = packet.getData();
                int length = packet.getLength();
                //String message = new String(packet.getData(), 0, packet.getLength());
                Log.d(TAG, "Received bytes: " + packet.getLength());

                if(length == 11) {
                    if (data[0] == 11 && data[5]==data[10]) {

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                findViewById(R.id.main_container).setVisibility(View.GONE);
                                findViewById(R.id.reception_layout).setVisibility(View.VISIBLE);

                                ByteBuffer bb = ByteBuffer.wrap(new byte[]{data[1], data[2], data[3], data[4]});
                                bb.order(ByteOrder.LITTLE_ENDIAN); // match Arduino's byte order!
                                int classification_result = (int) bb.getFloat();

                                boolean classification = data[5] != 0;


                                ByteBuffer bbb = ByteBuffer.wrap(new byte[]{data[6], data[7], data[8], data[9]});
                                bbb.order(ByteOrder.LITTLE_ENDIAN);
                                int classification_threshold = bbb.getInt();


                                if (classification_result < min_result) min_result = classification_result;
                                if (classification_result > max_result) max_result = classification_result;

                                ((SeekBar) findViewById(R.id.reception)).setMin((int) min_result);
                                ((SeekBar) findViewById(R.id.reception)).setMax((int) max_result);

                                ((SeekBar) findViewById(R.id.reception)).setSecondaryProgress((int) classification_result);

                                if (!is_user_editing_classification_thumb) {
                                    ((SeekBar) findViewById(R.id.reception)).setProgress(classification_threshold);
                                }

                                ((TextView) findViewById(R.id.infotext)).setText("We are receiving data!\nDo you want to set a threshold?\n\nClassification result:\t" + classification_result + "\nValue:\t" + (classification ? "YES" : "NO"));
                                ((TextView) findViewById(R.id.infotext)).setTextColor(classification ? Color.RED : Color.GREEN);
                                ((TextView) findViewById(R.id.mintext)).setText("Min:\n" + min_result);
                                ((TextView) findViewById(R.id.maxtext)).setText("Max:\n" + max_result);
                                ((TextView) findViewById(R.id.curval_text)).setText("Current Threshold:\n" + classification_threshold);

                            }
                        });

                    }
                }
            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "Error receiving UDP packet", e);
                }
            }
        }

        if(multicastLock.isHeld())
            multicastLock.release();

    }
    public void sendUDP(byte[] data) {

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    try {
                        DatagramPacket packet = new DatagramPacket(data, data.length, multicastAddress, sendPort);
                        sendSocket.send(packet);
                        Log.d(TAG, "Sent data to port " + sendPort);
                    } catch (IOException e) {
                        Log.e(TAG, "Error sending UDP packet", e);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();

    }
}