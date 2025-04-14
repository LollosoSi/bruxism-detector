package com.example.bruxismdetector;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
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
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        setupSwitchLabels();

        //ContextCompat.startForegroundService(this, new Intent(this, Tracker2.class));

        //finish();
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

        switchLabelMap.put(R.id.row_workout, "Done workout today");
        switchLabelMap.put(R.id.row_hydrated, "Well hydrated");
        switchLabelMap.put(R.id.row_stressed, "Felt stressed today");
        switchLabelMap.put(R.id.row_caffeine, "Had caffeine");
        switchLabelMap.put(R.id.row_anxious, "Felt anxious");

        switchLabelMap.put(R.id.row_alcohol, "Had alcohol");
        switchLabelMap.put(R.id.row_late_dinner, "Late dinner or skipped meals");
        switchLabelMap.put(R.id.row_medications, "Took medications");
        switchLabelMap.put(R.id.row_pain, "Felt pain today");
        switchLabelMap.put(R.id.row_life_event, "Significant life event");

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


    @Override
    protected void onDestroy() {

        Log.d(TAG, "The activity is destroyed");
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

}