package com.example.bruxismdetector;

import static androidx.core.app.PendingIntentCompat.getActivity;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.icu.util.Calendar;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.transition.TransitionManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bruxismdetector.bruxism_grapher2.GrapherAsyncTask;
import com.example.bruxismdetector.mibanddbconverter.MiBandDBConverter;
import com.google.android.material.elevation.SurfaceColors;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    public final static String LAUNCH_GRAPHER = "Launch_Grapher_Please";
    private MulticastSocket receiveSocket;
    private DatagramSocket sendSocket;
    private InetAddress multicastAddress;
    boolean running = false;
    private int sendPort;
    private int receivePort;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String TAG = "Main activity";

    Thread bleThread = null;
    SwitchManager switchManager;
    boolean is_user_editing_classification_thumb = false;

    BLEWifiSender bleWifiSender = null;

    BLEWifiSender.BLECallback blc = new BLEWifiSender.BLECallback() {
        @Override
        public void onIPReceived(String ip) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((MaterialSwitch)findViewById(R.id.switch_tcp).findViewById(R.id.switch_item)).setChecked(true);

                }
            });


        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandlerSharer(this));

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
         //       | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        //getWindow().getDecorView().setSystemUiVisibility(flags);



        //EdgeToEdge.enable(this);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //SleepAsAndroidExtractor.extract(this, this);

        getWindow().setStatusBarColor( SurfaceColors.SURFACE_0.getColor(this));

        if(!launchActivityforPermissionsIfNecessary()){
            checkAndRequestBluetoothPermissions();


        }





        setupUDP(4001, 4000);

        Intent launchintent = getIntent();
        if(launchintent!=null){
            if(launchintent.getAction()!=null){
            switch (launchintent.getAction()){
                case LAUNCH_GRAPHER:
                    tryGraphing(null);
                    break;
                default:
                    break;
            }
        }
            }


        initialSetup();
    }

    public void initialSetup(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        setupSwitchLabels();
        setupSessionToggle();


        MaterialSwitch swsh = (MaterialSwitch)findViewById(R.id.switch_sharedpref).findViewById(R.id.switch_item);
        swsh.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("start_trainer_after_tracker_ends", swsh.isChecked()).apply();  // or false when unchecked
                findViewById(R.id.button_start_trainer).setVisibility(swsh.isChecked() ? View.GONE : View.VISIBLE);

            }
        });
        swsh.setChecked(prefs.getBoolean("start_trainer_after_tracker_ends", false));

        MaterialSwitch swshl = (MaterialSwitch)findViewById(R.id.switch_autostart_listener).findViewById(R.id.switch_item);
        swshl.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @SuppressLint("ScheduleExactAlarm")
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("schedule_listener_after_tracker_ends", swshl.isChecked()).apply();  // or false when unchecked

                Intent intent = new Intent(MainActivity.this, UDPCatcher.class);

                // Try to retrieve the existing PendingIntent (without creating it)
                PendingIntent existingIntent = PendingIntent.getService(
                        MainActivity.this, 0, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                );

                // If it exists, cancel it
                if (existingIntent != null && !swshl.isChecked()) {
                    Log.i("Autostart Listener", "Autostart cancelled");
                    ServiceScheduler.cancelUDPCatcherSchedule(MainActivity.this);
                }else if(swshl.isChecked()){
                    Log.i("Autostart Listener", "Autostart set");
                    ServiceScheduler.scheduleUDPCatcherAtTime(MainActivity.this, prefs.getInt("ServiceHour", 21), prefs.getInt("ServiceMinute",0));

                }


            }
        });
        swshl.setChecked(prefs.getBoolean("schedule_listener_after_tracker_ends", true));

        View autostartListenerRow = findViewById(R.id.switch_autostart_listener);
        autostartListenerRow.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showAutostartTimePicker();
                return true; // Consume the long click
            }
        });


        MaterialSwitch swthr = (MaterialSwitch)findViewById(R.id.switch_sharedpref_use_threshold).findViewById(R.id.switch_item);
        swthr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("use_threshold", swthr.isChecked()).apply();  // or false when unchecked

            }
        });
        swthr.setChecked(prefs.getBoolean("use_threshold", false));

        MaterialSwitch swardubeep = (MaterialSwitch)findViewById(R.id.switch_sharedpref_arduino_beep).findViewById(R.id.switch_item);
        swardubeep.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("arduino_beep", swardubeep.isChecked()).apply();  // or false when unchecked

            }
        });
        swardubeep.setChecked(prefs.getBoolean("arduino_beep", true));

        MaterialSwitch swoalarmondevice = (MaterialSwitch)findViewById(R.id.switch_sharedpref_alarm_on_device).findViewById(R.id.switch_item);
        swoalarmondevice.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("alarm_on_device", swoalarmondevice.isChecked()).apply();  // or false when unchecked
                ((TextView)findViewById(R.id.switch_sharedpref_alarm_on_device).findViewById(R.id.switch_label)).setText("Alarm on: " + (swoalarmondevice.isChecked()?"Android":"Arduino"));
            }
        });
        swoalarmondevice.setChecked(prefs.getBoolean("alarm_on_device", true));



        MaterialSwitch swrecordnoise = (MaterialSwitch)findViewById(R.id.switch_recordnoise).findViewById(R.id.switch_item);
        swrecordnoise.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("record_noise", swrecordnoise.isChecked()).apply();  // or false when unchecked
            }
        });
        swrecordnoise.setChecked(prefs.getBoolean("record_noise", false));


        MaterialSwitch swrecordaccel = (MaterialSwitch)findViewById(R.id.switch_recordaccel).findViewById(R.id.switch_item);
        swrecordaccel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("record_accel", swrecordaccel.isChecked()).apply();  // or false when unchecked
            }
        });
        swrecordaccel.setChecked(prefs.getBoolean("record_accel", false));


        MaterialSwitch sw_notbeep = (MaterialSwitch)findViewById(R.id.switch_do_not_beep).findViewById(R.id.switch_item);
        sw_notbeep.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("do_not_beep", sw_notbeep.isChecked()).apply();  // or false when unchecked

                if(sw_notbeep.isChecked()){
                    findViewById(R.id.switch_sharedpref_arduino_beep).setVisibility(View.GONE);
                }else{
                    findViewById(R.id.switch_sharedpref_arduino_beep).setVisibility(View.VISIBLE);
                }
            }
        });
        sw_notbeep.setChecked(prefs.getBoolean("do_not_beep", false));

        MaterialSwitch sw_notalarm = (MaterialSwitch)findViewById(R.id.switch_do_not_alarm).findViewById(R.id.switch_item);
        sw_notalarm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("do_not_alarm", sw_notalarm.isChecked()).apply();  // or false when unchecked

                if(sw_notalarm.isChecked()){
                    findViewById(R.id.switch_sharedpref_alarm_on_device).setVisibility(View.GONE);
                }else{
                    findViewById(R.id.switch_sharedpref_alarm_on_device).setVisibility(View.VISIBLE);
                }
            }
        });
        sw_notalarm.setChecked(prefs.getBoolean("do_not_alarm", false));

        MaterialSwitch swtcp = (MaterialSwitch)findViewById(R.id.switch_tcp).findViewById(R.id.switch_item);
        swtcp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("use_tcp", swtcp.isChecked()).apply();  // or false when unchecked
                String ip = prefs.getString("tcp_address", "");
                ((TextView)findViewById(R.id.switch_tcp).findViewById(R.id.switch_label)).setText("TCP" + (ip.isEmpty() ? "" : ": ") + ip);
                swtcp.setEnabled(!ip.isEmpty());
            }
        });
        swtcp.setChecked(prefs.getBoolean("use_tcp", false));
        String ip = prefs.getString("tcp_address", "");
        ((TextView)findViewById(R.id.switch_tcp).findViewById(R.id.switch_label)).setText("TCP" + (ip.isEmpty() ? "" : ": ") + ip);
        swtcp.setEnabled(!ip.isEmpty());


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

        View roww = findViewById(R.id.switch_sharedpref_arduino_beep);
        if (roww != null) {
            TextView materialSwitch = roww.findViewById(R.id.switch_label);
            if (materialSwitch != null) {
                materialSwitch.setText("Arduino beeps");
            }
        }

        setSwitchThreshold_sharedpref_text();

        switchManager = new SwitchManager(findViewById(android.R.id.content), this, false);
        new MoodSeekbarClass(findViewById(android.R.id.content), this);

        if(prefs.getBoolean("tutorial",true)) {
            playTutorial();

        }

    }

    private void showAutostartTimePicker() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int currentHour = prefs.getInt("ServiceHour", 21);
        int currentMinute = prefs.getInt("ServiceMinute", 0);

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                MainActivity.this,
                // R.style.YourCustomTimePickerTheme, // Optional: Apply a custom theme
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        // Save the new time to SharedPreferences
                        prefs.edit()
                                .putInt("ServiceHour", hourOfDay)
                                .putInt("ServiceMinute", minute)
                                .apply();

                        Log.i("Autostart Listener", "New time selected: " + hourOfDay + ":" + minute);

                        // If the main switch is currently checked, reschedule with the new time
                        MaterialSwitch swshl = findViewById(R.id.switch_autostart_listener).findViewById(R.id.switch_item);
                        if (swshl.isChecked()) {
                            ServiceScheduler.scheduleUDPCatcherAtTime(MainActivity.this, hourOfDay, minute);
                            Log.i("Autostart Listener", "Rescheduled with new time.");
                        }
                    }
                },
                currentHour,
                currentMinute,
                true // true for 24-hour view, false for 12-hour AM/PM view
        );
        timePickerDialog.setTitle("Set Autostart Time"); // Optional: Set a title
        timePickerDialog.show();


    }
    public void playTutorial(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        new Handler(Looper.getMainLooper()).post(() -> {
            List<Pair<View, String>> steps = Arrays.asList(
                    new Pair<>(findViewById(R.id.menu_content), "Set the switches that best describe your day"),
                    new Pair<>(findViewById(R.id.sessioncard), "These settings affect your user experience, it's recommended to leave as is."),
                    new Pair<>(findViewById(R.id.session_settings_textview_handle), "Tap here to expand/collapse the session settings.\n\nLong press to replay this tutorial."),

                    new Pair<>(findViewById(R.id.switch_sharedpref_use_threshold), "When tracking, use a custom classification threshold.\n\nYou can tune this setting by long pressing the button on the tracker device."),
                    new Pair<>(findViewById(R.id.switch_sharedpref), "Start trainer when tracker ends.\n\nThe trainer will beep around once every hour until 19:00.\nWhen you hear the beep, relax your jaw.\n\nNote that the beeps go into your alarm volume, so you cannot mute them using Media, Call or Notification volumes."),
                    new Pair<>(findViewById(R.id.switch_autostart_listener), "Enable this to start tracking automatically,\nthe app will listen for your arduino starting from 21:00 onwards.\n\nYou'll see a notification and will have the chance to stop or reschedule the service.\n\nLONG PRESS this switch to change the start listening time."),

                    new Pair<>(findViewById(R.id.switch_do_not_beep), "Don't fire and record beeps during the session."),
                    new Pair<>(findViewById(R.id.switch_do_not_alarm), "Don't fire and record alarms during the session."),

                    new Pair<>(findViewById(R.id.switch_sharedpref_arduino_beep), "Select which device will beep.\nBoth Android and Arduino will beep the same way.\n\nYou might prefer Android to tune the volume or connect a headset to avoid disturbing others."),
                    new Pair<>(findViewById(R.id.switch_sharedpref_alarm_on_device), "Select which device will ring your alarms.\nAndroid will vibrate, Arduino will beep a melody.\n\nIf Android fails to wake you up, Arduino will ring regardless of this setting."),




                    new Pair<>(findViewById(R.id.button), "Tap this button to start tracking"),
                    new Pair<>(findViewById(R.id.button2), "Send all data to the grapher application on your computer."),
                    new Pair<>(findViewById(R.id.button_makegraphs), "Generate and see your graphs."),
                    new Pair<>(findViewById(R.id.button_makecharts), "See your stats and data correlations\n(if any)"),
                    new Pair<>(findViewById(R.id.button_extractdb), "This is an experimental feature.\nExtracts sleep data from a Mi Fitness database."),
                    new Pair<>(findViewById(R.id.button_tageditor), "Edit your session tags"),

                    new Pair<>(findViewById(R.id.button_tageditor), "Have fun!\nRefer to GitHub should you have any issues.")

            );


            new TutorialOverlayManager(MainActivity.this, steps).start(() -> prefs.edit().putBoolean("tutorial", false).apply());
        });
    }
    public void launchcameratest(View v){
        Intent intent = new Intent(this, CameraTest.class);
        startActivity(intent);

    }

    private boolean launchActivityforPermissionsIfNecessary() {
        if (!PermissionsActivity.hasAllPermissions(this)) {
            Intent intent = new Intent(this, PermissionsActivity.class);
            startActivity(intent);
            return true;
        }
        return false;
    }


    private static final int PICK_FILE_REQUEST_CODE = 1;

    public void openFilePicker(View v) {
        ProgressingDialog ad = showProgressDialog(MainActivity.this, "Handling your database");
        ad.setMessage("Converting your database");

        new Thread(new Runnable() {
            @Override
            public void run() {

                MiBandDBConverter.ProgressReport pr = new MiBandDBConverter.ProgressReport() {
                    @Override
                    public void setProgress(int progress) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ad.updateProgress(progress);
                            }
                        });
                        }
                    };


                if(MiBandDBConverter.tryRoot(MainActivity.this, pr)){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ad.dismiss();
                        }
                    });

                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ad.dismiss();
                    }
                });

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*"); // You can restrict this to specific MIME types like "text/plain", "application/json", etc.

                        startActivityForResult(intent, PICK_FILE_REQUEST_CODE);
                    }
                });

            }
        }).start();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();


                if(uri!=null) {
                    ProgressingDialog ad = showProgressDialog(this, "Converting your database");
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );

                            MiBandDBConverter.ProgressReport pr = new MiBandDBConverter.ProgressReport() {
                                @Override
                                public void setProgress(int progress) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            ad.updateProgress(progress);
                                        }
                                    });
                                }
                            };

                            // Use the Uri to read the file
                            Log.d("FilePicker", "Selected file: " + uri.getPath());
                            // You can now open the stream: getContentResolver().openInputStream(uri)
                            MiBandDBConverter mbdbc = new MiBandDBConverter();
                            mbdbc.convert(MainActivity.this, uri, pr);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ad.dismiss();
                                }
                            });
                        }
                    });

                    t.start();
                }
            }
        }
    }

    public ProgressingDialog showProgressDialog(Activity context, String message) {
        ProgressingDialog asyncDialog = new ProgressingDialog();
        //set message of the dialog


        asyncDialog.updateProgress(0);

        //show dialog
        asyncDialog.show(getSupportFragmentManager(), "ProgressingDialogDatabase");
        asyncDialog.setCancelable(false);
        asyncDialog.setMessage(message);

        return asyncDialog;
    }



    private void setupSwitchLabels() {
        Map<Integer, String> switchLabelMap = new HashMap<>();

        switchLabelMap.put(R.id.switch_sharedpref, "Autostart Trainer");
        switchLabelMap.put(R.id.switch_sharedpref_alarm_on_device, "Alarm on device");
        switchLabelMap.put(R.id.switch_autostart_listener, "Autostart Service");
        switchLabelMap.put(R.id.switch_recordnoise, "Record noise");
        switchLabelMap.put(R.id.switch_recordaccel, "Record movement");
        switchLabelMap.put(R.id.switch_do_not_alarm, "Do not alarm");
        switchLabelMap.put(R.id.switch_do_not_beep, "Do not beep");



        for (Map.Entry<Integer, String> entry : switchLabelMap.entrySet()) {
            View row = findViewById(entry.getKey());
            if (row != null) {
                TextView materialSwitch = row.findViewById(R.id.switch_label);
                if (materialSwitch != null) {
                    materialSwitch.setText(entry.getValue());
                }
            }
        }
    }

    public void launchChartActivity(View v){
        makeGraphs(this, new GrapherAsyncTask.GraphTaskCallback() {
            @Override
            public void onGraphTaskCompleted() {
                Intent intent = new Intent(MainActivity.this, SummaryCharts.class);
                startActivity(intent);
            }
        });

    }

    void setSwitchThreshold_sharedpref_text(){
        View row = findViewById(R.id.switch_sharedpref_use_threshold);
        if (row != null) {
            TextView materialSwitch = row.findViewById(R.id.switch_label);
            if (materialSwitch != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                materialSwitch.setText("Threshold: " + prefs.getInt("classification_threshold", 0));
            }
        }
    }

    // In your Activity, before creating BLEWifiSender or starting a scan:
    private static final int BLEREQUEST_CODE = 123;

    private void checkAndRequestBluetoothPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else { // Below Android 12
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH);
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_ADMIN);
            }
        }
        // Location permission is always needed for scanning on API 23+ (unless using companion device pairing)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }


        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), BLEREQUEST_CODE);
        } else {
            // Permissions are already granted, proceed with BLE operations
            bleThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    bleWifiSender = new BLEWifiSender(MainActivity.this, blc);

                }
            });
            bleThread.start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLEREQUEST_CODE) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                // All permissions granted, proceed
                bleThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        bleWifiSender = new BLEWifiSender(MainActivity.this, blc);

                    }
                });
                bleThread.start();
            } else {
                Toast.makeText(this, "Permissions denied. BLE scanning will not work.", Toast.LENGTH_LONG).show();
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

        if(bleWifiSender!=null)
             bleWifiSender.stop();

        try {
            bleThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

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
        intent.putExtra("info", this.switchManager.extractInfo());

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

    private void fetchLatestVersionFromGitHub(Consumer<Integer> callback) {
        new Thread(() -> {
            try {
                URL url = new URL("https://raw.githubusercontent.com/LollosoSi/bruxism-detector/main/Arduino/main/version.h");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                InputStream in = new BufferedInputStream(conn.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                int version = -1;

                while ((line = reader.readLine()) != null) {
                    if (line.contains("VersionIncremental")) {
                        // Example: const static uint16_t VersionIncremental = 123;
                        Pattern pattern = Pattern.compile("VersionIncremental\\s*=\\s*(\\d+)");
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            version = Integer.parseInt(matcher.group(1));
                            break;
                        }
                    }
                }

                reader.close();
                in.close();
                conn.disconnect();

                int finalVersion = version;
                new Handler(Looper.getMainLooper()).post(() -> callback.accept(finalVersion));

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> callback.accept(-1));
            }
        }).start();
    }

    int min_result = 0, max_result = 0;

    public void receiveUDP() {
        byte[] buffer = new byte[10000];

        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        WifiManager.MulticastLock multicastLock = wifiManager.createMulticastLock("multicastLock");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();

        boolean check_version = true;
        sendUDP(new byte[]{(byte)15});
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiveSocket.receive(packet);
                byte[] data = packet.getData();
                int length = packet.getLength();
                //String message = new String(packet.getData(), 0, packet.getLength());
                Log.d(TAG, "Received bytes: " + packet.getLength());

                if(check_version){
                    sendUDP(new byte[]{(byte)15});
                }
                if(length == 3){

                    if(data[0] == 15){
                        check_version=false;
                        ByteBuffer bb = ByteBuffer.wrap(new byte[]{data[1], data[2]});
                        bb.order(ByteOrder.LITTLE_ENDIAN); // match Arduino's byte order!
                        int versionincremental =  bb.getShort();

                        // Now compare:
                        fetchLatestVersionFromGitHub(latestVersion -> {
                            if (latestVersion == -1) {
                                Log.e("VersionCheck", "Failed to fetch version from GitHub.");
                            } else {
                                if (versionincremental < latestVersion) {
                                    Log.i("VersionCheck", "Update available! Arduino=" + versionincremental + ", GitHub=" + latestVersion);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {

                                            new AlertDialog.Builder(MainActivity.this)
                                                    .setTitle("Update Available")
                                                    .setMessage("A new firmware version is available.\n\n" +
                                                            "Please update your Arduino device.")
                                                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                                                    .setCancelable(true)
                                                    .show();
                                        }
                                    });

                                } else {
                                    Log.i("VersionCheck", "Arduino is up to date.");
                                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                                    if(prefs.getInt("lastversionarduino", 0) != versionincremental){
                                        prefs.edit().putInt("lastversionarduino", versionincremental).apply();
                                        ((TextView)findViewById(R.id.updatedtextnotification)).setVisibility(View.VISIBLE);
                                    }
                                }
                            }
                        });

                    }
                }

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

                                ((TextView) findViewById(R.id.infotext)).setText("\nClassification result:\t" + classification_result + "\nValue:\t" + (classification ? "YES" : "NO"));
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


    @SuppressLint("ScheduleExactAlarm")
    public void startListener(View v){

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND,2);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DATE, 1); // Next day if already passed
        }

        Intent intent = new Intent(this, UDPCatcher.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);

    }






    public void tryGraphing(View v){

        makeGraphs(this, new GrapherAsyncTask.GraphTaskCallback() {
            @Override
            public void onGraphTaskCompleted() {
                startActivity(new Intent(MainActivity.this, GraphViewer.class));
            }
        });
    }

    public static void makeGraphs(MainActivity ctx, GrapherAsyncTask.GraphTaskCallback callback){

        // Acquire a WakeLock to keep the screen on
        PowerManager powerManager = (PowerManager) ctx.getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "MyApp:MyWakeLockTag");
        wakeLock.acquire();

        GrapherAsyncTask task = new GrapherAsyncTask(ctx);

        // Set a completion listener
        task.setTaskCallback(callback);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);


        // Release the WakeLock when the task is done
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }

    }


    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator.hasVibrator()) {

            Random random = new Random();
            List<Long> timings = new ArrayList<>();
            List<Integer> amplitudes = new ArrayList<>();

            // Initial delay
            timings.add(0L);
            amplitudes.add(0);

            long vibrationDuration = 50; // Starting vibration duration
            long pauseDuration = 50;     // Starting pause duration

            // Generate many short irregular vibrations
            for (int i = 0; i < 20; i++) {
                // Randomize vibration and pause durations with gradual increase
                vibrationDuration += random.nextInt(10);  // increase gradually
                pauseDuration += random.nextInt(10);

                timings.add(vibrationDuration);
                amplitudes.add(VibrationEffect.DEFAULT_AMPLITUDE); // Full power

                timings.add(pauseDuration);
                amplitudes.add(0); // Pause (no vibration)
            }

            // Final long vibration (5 seconds)
            timings.add(5000L);
            amplitudes.add(VibrationEffect.DEFAULT_AMPLITUDE);

            // Convert to arrays
            long[] timingArray = new long[timings.size()];
            int[] amplitudeArray = new int[amplitudes.size()];
            for (int i = 0; i < timings.size(); i++) {
                timingArray[i] = timings.get(i);
                amplitudeArray[i] = amplitudes.get(i);
            }

            VibrationEffect effect = VibrationEffect.createWaveform(timingArray, amplitudeArray, 0);
            vibrator.vibrate(effect);

        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        initialSetup();
        switchManager.ReloadAll();

    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // New configuration, window might have resized.
        // You can get new dimensions here if needed.
        int newWidth = newConfig.screenWidthDp;
        int newHeight = newConfig.screenHeightDp;
        Log.d("Resize", "onConfigurationChanged: New DpWidth=" + newWidth + ", New DpHeight=" + newHeight);

        initialSetup();
        switchManager.ReloadAll();
    }


    public void LaunchSwitchEditor(View v){
        startActivity(new Intent(MainActivity.this, SwitchEditor.class));

    }



    private boolean isSessionExpanded = true;
    private ConstraintLayout rootLayout;
    private ConstraintSet expandedSet = new ConstraintSet();
    private ConstraintSet collapsedSet = new ConstraintSet();

    private void setupSessionToggle() {
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        rootLayout = findViewById(R.id.session_card_root); // the root ConstraintLayout inside your CardView

        // Clone current layout as "expanded" state
        expandedSet.clone(rootLayout);

        // Define collapsed layout
        collapsedSet.clone(rootLayout);


        // Hide other buttons (optional)
        collapsedSet.setVisibility(R.id.button_extractdb, View.GONE);
        collapsedSet.setVisibility(R.id.button_tageditor, View.GONE);
        collapsedSet.setVisibility(R.id.right_column_switches, View.GONE);
        collapsedSet.setVisibility(R.id.left_column_switches, View.GONE);
        collapsedSet.setVisibility(R.id.button2, View.GONE);
        collapsedSet.setVisibility(R.id.button_start_trainer, View.GONE);

        View toggleHandle = findViewById(R.id.session_settings_textview_handle);

        toggleHandle.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {

                playTutorial();

                return true;
            }
        });

        toggleHandle.setOnClickListener(v -> {
            isSessionExpanded = !isSessionExpanded;

            //TransitionManager.beginDelayedTransition(rootLayout);

            if (isSessionExpanded) {
                expandedSet.applyTo(rootLayout);
            } else {
                collapsedSet.applyTo(rootLayout);
            }
            prefs.edit().putBoolean("session_collapsed", !isSessionExpanded).apply();
            vibrateHaptic();

        });

        if(prefs.getBoolean("session_collapsed", false)) {
            collapsedSet.applyTo(rootLayout);
        } else {
            expandedSet.applyTo(rootLayout);
        }
    }


    void vibrateHaptic(){
        Vibrator vibrator = (Vibrator) this.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator.hasVibrator()) {
            VibrationEffect ve = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ve = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);
            }else{
                ve = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE);
            }
            vibrator.vibrate(ve);
        }
    }

    public void scheduleUDPCatcher(View v){
        ServiceScheduler.scheduleUDPCatcherAtTime(this, System.currentTimeMillis()+10000);
    }

    public void startCalendar(View v){
        // Launch Calendar activity
        Intent intent = new Intent(this, CalendarViewer.class);
        startActivity(intent);
    }


}