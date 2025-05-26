package com.example.bruxismdetector;

import static androidx.core.app.PendingIntentCompat.getActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
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
import android.preference.PreferenceManager;
import android.provider.Settings;
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
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    SwitchManager switchManager;
    boolean is_user_editing_classification_thumb = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        launchActivityforPermissionsIfNecessary();


        setupSwitchLabels();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);


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
                    AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    alarmManager.cancel(existingIntent);
                    existingIntent.cancel(); // Also cancel the PendingIntent itself
                }else if(swshl.isChecked()){
                    Log.i("Autostart Listener", "Autostart set");
                    Calendar calendar = Calendar.getInstance();
                    calendar.set(Calendar.HOUR_OF_DAY, 21);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);

                    if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                        calendar.add(Calendar.DATE, 1); // Next day if already passed
                    }

                    Intent intent2 = new Intent(MainActivity.this, UDPCatcher.class);
                    PendingIntent pendingIntent2 = PendingIntent.getService(MainActivity.this, 0, intent2, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent2);

                }


            }
        });
        swshl.setChecked(prefs.getBoolean("schedule_listener_after_tracker_ends", true));

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


        MaterialSwitch swonlyalarm = (MaterialSwitch)findViewById(R.id.switch_sharedpref_only_alarm).findViewById(R.id.switch_item);
        swonlyalarm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("only_alarm", swonlyalarm.isChecked()).apply();  // or false when unchecked
                swardubeep.setEnabled(!swonlyalarm.isChecked());
                findViewById(R.id.switch_sharedpref_arduino_beep).setVisibility( swonlyalarm.isChecked() ? View.GONE : View.VISIBLE);

            }
        });
        swonlyalarm.setChecked(prefs.getBoolean("only_alarm", false));

        MaterialSwitch swoalarmondevice = (MaterialSwitch)findViewById(R.id.switch_sharedpref_alarm_on_device).findViewById(R.id.switch_item);
        swoalarmondevice.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                prefs.edit().putBoolean("alarm_on_device", swoalarmondevice.isChecked()).apply();  // or false when unchecked
                ((TextView)findViewById(R.id.switch_sharedpref_alarm_on_device).findViewById(R.id.switch_label)).setText("Alarm on: " + (swoalarmondevice.isChecked()?"Android":"Arduino"));
            }
        });
        swoalarmondevice.setChecked(prefs.getBoolean("alarm_on_device", true));
        // Override - feature not ready
        //((MaterialSwitch)findViewById(R.id.switch_sharedpref_alarm_on_device).findViewById(R.id.switch_item)).setEnabled(false);
        //((MaterialSwitch)findViewById(R.id.switch_sharedpref_alarm_on_device).findViewById(R.id.switch_item)).setChecked(true);


        
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

        switchManager = new SwitchManager(findViewById(android.R.id.content), this);
        new MoodSeekbarClass(findViewById(android.R.id.content), this);

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

        if(prefs.getBoolean("tutorial",true)) {
            prefs.edit().putBoolean("tutorial",false).apply();
            new Handler(Looper.getMainLooper()).post(() -> {
                List<Pair<View, String>> steps = Arrays.asList(
                        new Pair<>(findViewById(R.id.menu_content), "Set the switches that best describe your day"),
                        new Pair<>(findViewById(R.id.sessioncard), "These settings affect your user experience, it's recommended to leave as is."),
                        new Pair<>(findViewById(R.id.switch_sharedpref_use_threshold), "When tracking, use a custom classification threshold.\n\nYou can tune this setting by long pressing the button on the tracker device."),
                        new Pair<>(findViewById(R.id.switch_sharedpref), "Start trainer when tracker ends.\n\nThe trainer will beep around once every hour until 19:00.\nWhen you hear the beep, relax your jaw.\n\nNote that the beeps go into your alarm volume, so you cannot mute them using Media, Call or Notification volumes."),
                        new Pair<>(findViewById(R.id.switch_autostart_listener), "Enable this to start tracking automatically,\nthe app will listen for your arduino starting from 21:00 onwards.\n\nYou'll see a notification and will have the chance to stop or reschedule the service."),


                        new Pair<>(findViewById(R.id.switch_sharedpref_alarm_on_device), "Select which device will ring your alarms.\nAndroid will vibrate, Arduino will beep a melody.\n\nIf Android fails to wake you up, Arduino will ring regardless of this setting."),
                        new Pair<>(findViewById(R.id.switch_sharedpref_only_alarm), "Select this if you only want to have alarms and not beeps."),
                        new Pair<>(findViewById(R.id.switch_sharedpref_arduino_beep), "Select which device will beep.\nBoth Android and Arduino will beep the same way.\n\nYou might prefer Android to tune the volume or connect a headset to avoid disturbing others."),


                        new Pair<>(findViewById(R.id.button), "Tap this button to start tracking"),
                        new Pair<>(findViewById(R.id.button2), "Send all data to the grapher application on your computer."),
                        new Pair<>(findViewById(R.id.button_makegraphs), "Generate and see your graphs."),
                        new Pair<>(findViewById(R.id.button_makecharts), "See your stats and data correlations\n(if any)"),
                        new Pair<>(findViewById(R.id.button_extractdb), "This is an experimental feature.\nExtracts sleep data from a Mi Fitness database."),

                        new Pair<>(findViewById(R.id.button_extractdb), "Have fun!\nRefer to GitHub should you have any issues.")


                );

                new TutorialOverlayManager(MainActivity.this, steps).start();
            });
        }


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
        switchLabelMap.put(R.id.switch_sharedpref_only_alarm, "Only Alarms");
        switchLabelMap.put(R.id.switch_sharedpref_alarm_on_device, "Alarm on device");
        switchLabelMap.put(R.id.switch_autostart_listener, "Autostart Service");


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




}