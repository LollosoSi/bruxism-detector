package com.example.bruxismdetector;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.icu.util.Calendar;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.se.omapi.Session;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.FragmentManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Tracker2 extends Service {

    private static final String TAG = "BruxismTracker:TrackerService";

    private NotificationManager notificationManager;

    private static final String CHANNEL_ID = "MulticastChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_STOP_SERVICE = "STOP_MULTICAST_SERVICE";
    private static final String ACTION_BUTTON_SERVICE = "BUTTON_MULTICAST_SERVICE";
    private static final String BEEP_BUTTON_SERVICE = "BEEP_MULTICAST_SERVICE";
    private static final String ALARMOFF_BUTTON_SERVICE = "ALARMOFF_MULTICAST_SERVICE";

    private MulticastSocket receiveSocket;
    private DatagramSocket sendSocket;
    private InetAddress multicastAddress;

    private int sendPort;
    private int receivePort;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean running = false;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    private SessionTracker sessionTracker;

    public Tracker2() {
    }

    //AudioLogger audioLogger = null;

    @Override
    public void onCreate() {
        super.onCreate();

        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandlerSharer(this));


        sessionTracker = new SessionTracker();
        sessionTracker.servicereference = this;

        Log.d(TAG, "Service created");
        this.notificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            createNotificationChannel();
        } else {
            Log.e("Tracker", "NotificationManager is null");
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_STOP_SERVICE);
        filter.addAction(ACTION_BUTTON_SERVICE);
        filter.addAction(BEEP_BUTTON_SERVICE);
        filter.addAction(ALARMOFF_BUTTON_SERVICE);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        registerReceiver(notification_and_screen_receiver, filter, Context.RECEIVER_EXPORTED);

        sessionTracker.setup(this);

        setupUDP(4001, 4000);

        sendUDP(new byte[]{SessionTracker.USING_ANDROID});
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(prefs.getBoolean("use_threshold", false)) {
            sendUDP(new byte[]{SessionTracker.SET_EVALUATION_THRESHOLD, (byte)(prefs.getInt("classification_threshold", 0) & 0xFF), (byte)((prefs.getInt("classification_threshold", 0) >> 8) & 0xFF)});
            sendUDP(new byte[]{SessionTracker.SET_EVALUATION_THRESHOLD, (byte)(prefs.getInt("classification_threshold", 0) & 0xFF), (byte)((prefs.getInt("classification_threshold", 0) >> 8) & 0xFF)});
            sendUDP(new byte[]{SessionTracker.SET_EVALUATION_THRESHOLD, (byte)(prefs.getInt("classification_threshold", 0) & 0xFF), (byte)((prefs.getInt("classification_threshold", 0) >> 8) & 0xFF)});
            sendUDP(new byte[]{SessionTracker.SET_EVALUATION_THRESHOLD, (byte)(prefs.getInt("classification_threshold", 0) & 0xFF), (byte)((prefs.getInt("classification_threshold", 0) >> 8) & 0xFF)});

        }

        if(!prefs.getBoolean("arduino_beep", true)){
            sendUDP(new byte[]{SessionTracker.DO_NOT_BEEP_ARDUINO});
            Intent intent = new Intent(this, RingReceiver.class);
            intent.setAction(RingReceiver.beep_once); // Use the constant here
            sendBroadcast(intent);
        }

        if(!prefs.getBoolean("alarm_on_device", true)){
            sendUDP(new byte[]{SessionTracker.ALARM_ARDUINO_EVEN_WITH_ANDROID});
        }

        if(prefs.getBoolean("do_not_beep", false)){
            sendUDP(new byte[]{SessionTracker.DO_NOT_BEEP});
        }
        if(prefs.getBoolean("do_not_alarm", false)){
            sendUDP(new byte[]{SessionTracker.DO_NOT_ALARM});
        }


        Notification n = buildNotification();
        n.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        startForeground(NOTIFICATION_ID, n);


        if(PermissionsActivity.isMicrophonePermissionGranted(this) && prefs.getBoolean("record_noise", false)) {
            //audioLogger = new Tracker2.AudioLogger();
            String filename3 = sessionTracker.getNewFilename(sessionTracker.formattedDate, "_NOISE.csv", sessionTracker.csv_folder_path + "NOISE/");
            Intent startRecorderIntent = new Intent(this, Recorder.class);
            startRecorderIntent.putExtra("filename3", filename3);
            startService(startRecorderIntent);
        }

        if(PermissionsActivity.isBackgroundLocationGranted(this) && prefs.getBoolean("record_accel", false)) {
            String filename4 = sessionTracker.getNewFilename(sessionTracker.formattedDate, "_ACCEL.csv", sessionTracker.csv_folder_path + "ACCEL/");
            Intent startRecorderIntent = new Intent(this, MovementDetectorService.class);
            startRecorderIntent.putExtra("filename4", filename4);
            startService(startRecorderIntent);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent!=null){
            DailyLogData logData = new DailyLogData(intent);
            // Now you can easily pass this object around:
            Log.d("Tracker2", logData.toString());
            // Example: pass to a logger, writer, or processor
            SessionTracker.writeDailyLog(logData, sessionTracker.file_out, this);
        }


        return START_STICKY;
    }

    @SuppressLint("ScheduleExactAlarm")
    @Override
    public void onDestroy() {

        dismissVibrator();
        unregisterReceiver(notification_and_screen_receiver);
        closeSockets();
        if (executor != null) {
            executor.shutdownNow(); // Shutdown the executor
        }

        sessionTracker.close();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(prefs.getBoolean("start_trainer_after_tracker_ends", false)){
            Intent intent = new Intent(this, RingReceiver.class);
            sendBroadcast(intent);
        }

        if(prefs.getBoolean("schedule_listener_after_tracker_ends",true)) {
            ServiceScheduler.scheduleUDPCatcherAtTime(this, prefs.getInt("ServiceHour", 21), prefs.getInt("ServiceMinute",0));
        }

        if(prefs.getBoolean("collect_data_on_end",false)) {

            try {
                Intent intent2 = new Intent(this, DialogHostActivity.class);

                intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Required from a Service
                Log.d(TAG, "Starting DialogHostActivity");
                startActivity(intent2);
                prefs.edit().putBoolean("collect_data_on_end", false).apply();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start DialogHostActivity", e);
            }
        }else{

            Intent myIntent = new Intent(Tracker2.this, MainActivity.class);
            myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Required from a Service
            myIntent.setAction(MainActivity.LAUNCH_GRAPHER);
            this.startActivity(myIntent);

        }


        if(PermissionsActivity.isMicrophonePermissionGranted(this) && prefs.getBoolean("record_noise", false)){
            // Intent with action stop
            Intent stopRecorderIntent = new Intent(this, Recorder.class);
            stopRecorderIntent.setAction(Recorder.ACTION_STOP);
            startService(stopRecorderIntent);
        }

        if(PermissionsActivity.isBackgroundLocationGranted(this) && prefs.getBoolean("record_accel", false)) {
            Intent stopRecorderIntent = new Intent(this, MovementDetectorService.class);
            stopRecorderIntent.setAction(MovementDetectorService.ACTION_STOP);
            startService(stopRecorderIntent);
        }



            super.onDestroy();
    }

    private void createNotificationChannel() {

        CharSequence name = "Tracker Channel";
        String description = "Bruxism notifications";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        notificationManager.createNotificationChannel(channel);

    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent buttonIntent = new Intent(ACTION_BUTTON_SERVICE);
        PendingIntent buttonPendingIntent = PendingIntent.getBroadcast(
                this, 1, buttonIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent beepIntent = new Intent(BEEP_BUTTON_SERVICE);
        PendingIntent beepPendingIntent = PendingIntent.getBroadcast(
                this, 2, beepIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );



        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bruxism Tracker Service Running")
                .setContentText("Logging & running alarms")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.ic_media_pause,
                        "Stop tracking",
                        stopPendingIntent
                ))
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.btn_star,
                        "Button press",
                        buttonPendingIntent
                ))
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.btn_star,
                        "BEEP",
                        beepPendingIntent
                ))
                .setOngoing(true)

                .build();
    }

    private Vibrator vibrator = null;
    boolean vibrating = false;
    private void vibrate() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator.hasVibrator()) {

            vibrating=true;

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

    public void dismissVibrator() {
        if (vibrator != null && vibrating) {
            vibrating=false;
            vibrator.cancel();
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


public void exit(){
        stopSelf();
}
    private final BroadcastReceiver notification_and_screen_receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_STOP_SERVICE.equals(intent.getAction())) {
                Log.d(TAG, "Stop action received");
                stopSelf();

            }
            if (ACTION_BUTTON_SERVICE.equals(intent.getAction()) || ALARMOFF_BUTTON_SERVICE.equals(intent.getAction())) {
                Log.d(TAG, "Button received");
                sendUDP(new byte[]{SessionTracker.BUTTON_PRESS});
                //processData(new byte[]{BUTTON_PRESS}, 1);
            }
            if (BEEP_BUTTON_SERVICE.equals(intent.getAction())) {
                Log.d(TAG, "Beep button received");
                sendUDP(new byte[]{SessionTracker.BEEP});
            }
            if(Intent.ACTION_SCREEN_ON.equals(intent.getAction()) || Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
                Log.d(TAG, "Screen on/off received");
                if(vibrating) {
                    sendUDP(new byte[]{SessionTracker.BUTTON_PRESS});
                }

            }
        }
    };
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

    long last_system_broadcast_info = 0;
    public void receiveUDP() {
        byte[] buffer = new byte[10000];

        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        WifiManager.MulticastLock multicastLock = wifiManager.createMulticastLock("multicastLock");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();


        while (running) {
            if(sessionTracker.millis()-last_system_broadcast_info > (1000*60*30)){
                last_system_broadcast_info = sessionTracker.millis();
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                if(prefs.getBoolean("use_threshold", false)) {
                    sendUDP(new byte[]{SessionTracker.SET_EVALUATION_THRESHOLD, (byte)(prefs.getInt("classification_threshold", 0) & 0xFF), (byte)((prefs.getInt("classification_threshold", 0) >> 8) & 0xFF)});
                    sendUDP(new byte[]{SessionTracker.SET_EVALUATION_THRESHOLD, (byte)(prefs.getInt("classification_threshold", 0) & 0xFF), (byte)((prefs.getInt("classification_threshold", 0) >> 8) & 0xFF)});
                }

                sendUDP(new byte[]{SessionTracker.USING_ANDROID});

                if(!prefs.getBoolean("arduino_beep", true)){
                    sendUDP(new byte[]{SessionTracker.DO_NOT_BEEP_ARDUINO});
                }
                if(!prefs.getBoolean("alarm_on_device", true)){
                    sendUDP(new byte[]{SessionTracker.ALARM_ARDUINO_EVEN_WITH_ANDROID});
                }

                if(prefs.getBoolean("do_not_beep", false)){
                    sendUDP(new byte[]{SessionTracker.DO_NOT_BEEP});
                }
                if(prefs.getBoolean("do_not_alarm", false)){
                    sendUDP(new byte[]{SessionTracker.DO_NOT_ALARM});
                }

            }

            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiveSocket.receive(packet);
                byte[] data = packet.getData();
                int length = packet.getLength();
                //String message = new String(packet.getData(), 0, packet.getLength());
                Log.d(TAG, "Received bytes: " + packet.getLength());

                // Handle the data here, e.g., broadcast, process, etc.
                sessionTracker.processData(data, length);
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

    public void runAlarm() {
        vibrate();
    }



}