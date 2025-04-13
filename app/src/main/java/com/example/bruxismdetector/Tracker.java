package com.example.bruxismdetector;

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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;


public class Tracker extends Service {


    private Vibrator vibrator;
    boolean vibrating = false;
    private void vibrate() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator.hasVibrator()) {
            vibrating = true;
            long[] pattern = {0, 500, 500}; // Vibrate for 500ms, pause for 500ms, repeat
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0); // 0: repeat indefinitely
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(pattern, 0); // 0: repeat indefinitely
            }
        }
    }

    private void dismissVibrator() {
        if (vibrator != null) {
            vibrator.cancel();
            if(vibrating){
                vibrating=false;
                sendUDP(new byte[]{BUTTON_PRESS});
            }
        }
    }

    private NotificationManager notificationManager;

    private static final String CHANNEL_ID = "MulticastChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_STOP_SERVICE = "STOP_MULTICAST_SERVICE";
    private static final String ACTION_BUTTON_SERVICE = "BUTTON_MULTICAST_SERVICE";
    private static final String BEEP_BUTTON_SERVICE = "BEEP_MULTICAST_SERVICE";
    private static final String ALARMOFF_BUTTON_SERVICE = "ALARMOFF_MULTICAST_SERVICE";

    private static final String TAG = "MulticastUDPService";

    private MulticastSocket receiveSocket;
    private DatagramSocket sendSocket;
    private InetAddress multicastAddress;

    private int sendPort;
    private int receivePort;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean running = false;
    private boolean udpSetup = false;

    String csv_folder_path = "RECORDINGS/";

    Context context;


    private AlarmManager alarmManager;
    private PendingIntent alarmPendingIntent;
    public Tracker() {

    }

    PrintWriter createWriter(String filename) {
        try {
            File file = new File(filename);
            return new PrintWriter(file);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Error creating PrintWriter for file: " + filename, e);
            return null; // Or handle the error in another way
        }
    }

    long startmillis = 0;
    long millis(){
        return System.currentTimeMillis() - startmillis;
    }

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UDPService:WakeLock");
        wakeLock.acquire();

        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "UDPService:WifiLock");
        wifiLock.acquire();


        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this);
        }

        Log.d(TAG, "Service created");
        context = this;
        this.notificationManager = (NotificationManager) context.getSystemService(Service.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            createNotificationChannel();
        } else {
            Log.e("Tracker", "NotificationManager is null");
        }

        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent alarmIntent = new Intent(this, AppLaunchReceiver.class);
        alarmPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                alarmIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        //scheduleAlarm();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_STOP_SERVICE);
        filter.addAction(ACTION_BUTTON_SERVICE);
        filter.addAction(BEEP_BUTTON_SERVICE);
        filter.addAction(ALARMOFF_BUTTON_SERVICE);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);



        registerReceiver(stopReceiver, filter, Context.RECEIVER_EXPORTED);


        //notificationManager.notify( NOTIFICATION_ID, buildNotification());

        createRecordingsDirectory();

        Date currentDate = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        String formattedDate = formatter.format(currentDate);

        startmillis = millis();

        String filename1 = getNewFilename(formattedDate, ".csv", csv_folder_path);
        String filename2 = getNewFilename(formattedDate, "_RAW.csv", csv_folder_path+"RAW/");
        file_out = createWriter(filename1);
        append_csv(new String[]{"Millis", "Time", "Event", "Notes", "Duration (seconds)"}, file_out);
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Start", "Tracking started. Date: "+formattedDate}, file_out);

        file_raw_out = createWriter(filename2);
        append_csv(new String[]{"Millis", "Classification"}, file_raw_out);

        Log.d(TAG, "Creating files: " + filename1 + " " + filename2);

        file_out.flush();
        file_raw_out.flush();

        // Register the network change receiver
        //IntentFilter networkFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        //registerReceiver(networkChangeReceiver, networkFilter);

        setupUDP(4001, 4000);

        sendUDP(new byte[]{USING_ANDROID});

        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public void onDestroy() {
        //unregisterReceiver(networkChangeReceiver); // Unregister the receiver

        dismissVibrator();

        unregisterReceiver(stopReceiver);
        closeSockets();
        if (executor != null) {
            executor.shutdownNow(); // Shutdown the executor
        }

        // Place here the code you want to execute on exit
        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "End", "Tracking ended"}, file_out);
        file_out.flush(); // Writes the remaining data to the file
        file_out.close(); // Finishes the file

        file_raw_out.flush();
        file_raw_out.close();

        UDPCheckJob.scheduleJob(context);

        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Tracker Channel";
            String description = "Bruxism notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
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

    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_STOP_SERVICE.equals(intent.getAction())) {
                Log.d(TAG, "Stop action received");
                stopSelf();
            }
            if (ACTION_BUTTON_SERVICE.equals(intent.getAction()) || ALARMOFF_BUTTON_SERVICE.equals(intent.getAction())) {
                Log.d(TAG, "Button received");
                sendUDP(new byte[]{BUTTON_PRESS});
                //processData(new byte[]{BUTTON_PRESS}, 1);
            }
            if (BEEP_BUTTON_SERVICE.equals(intent.getAction())) {
                Log.d(TAG, "Beep button received");
                sendUDP(new byte[]{BEEP});
            }
            if(Intent.ACTION_SCREEN_ON.equals(intent.getAction()) || Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
                Log.d(TAG, "Screen on/off received");
                dismissVibrator();
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
            udpSetup = true;
            Log.d(TAG, "UDP setup complete. Receiving on port " + receivePort + ", sending on port " + sendPort);

        } catch (IOException e) {
            Log.e(TAG, "Error setting up UDP", e);
        }
    }

    public void receiveUDP() {
        byte[] buffer = new byte[10000];

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock2 = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UDPService:WakeLock");
        wakeLock2.acquire(60000*60*10);

        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        WifiManager.MulticastLock multicastLock = wifiManager.createMulticastLock("multicastLock");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();


        WifiManager.WifiLock wifiLock2 = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "UDPService:WifiLock");
        wifiLock2.acquire();

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiveSocket.receive(packet);
                byte[] data = packet.getData();
                int length = packet.getLength();
                //String message = new String(packet.getData(), 0, packet.getLength());
                Log.d(TAG, "Received bytes: " + packet.getLength());

                // Handle the data here, e.g., broadcast, process, etc.
                processData(data, length);
            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "Error receiving UDP packet", e);
                }
            }
        }

        if(multicastLock.isHeld())
            multicastLock.release();

        if (wifiLock2.isHeld()) {
            wifiLock2.release();
        }
try {
    if (wakeLock2.isHeld()) wakeLock2.release();
} catch (Exception e) {
    throw new RuntimeException(e);
}

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

    private File recordingsDirectory;
    private File rawRecordingsDirectory;

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

    private void closeSockets() {
        running = false;
        //executor.shutdownNow();
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
        udpSetup = false;
    }

    private final BroadcastReceiver networkChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

                if (isConnected) {
                    Log.d(TAG, "Network connected. Re-establishing UDP.");
                    closeSockets(); // Close existing sockets
                    setupUDP(sendPort, receivePort); // Re-establish UDP
                } else {
                    Log.d(TAG, "Network disconnected.");
                    closeSockets();
                }
            }
        }
    };

    PrintWriter file_out;
    PrintWriter file_raw_out;

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

    double[] fftData; // This will store the FFT data
    int sampleCount = 128;
    long samplingFrequency = 2000;
    float maxHeight = 600; // Maximum visible height for the bars
    double maxMagnitude = 5000; // To track the maximum magnitude for normalization

    boolean did_print_sync = false;
    boolean remote_button_pressed = false;
    boolean confirmed_udp_alarm = false;

    long cstart = 0;
    boolean alarmed = false;
    boolean clenching=false;

    final byte CLENCH_START = 0;
    final byte CLENCH_STOP = 1;
    final byte BUTTON_PRESS = 2;
    final byte ALARM_START = 3;
    final byte BEEP = 4;
    final byte UDP_ALARM_CONFIRMED = 5;
    final byte DETECTED = 6;
    final byte CONTINUED = 7;
    final byte ALARM_STOP = 8;
    final byte TRACKING_STOP = 9;
    final byte USING_ANDROID = 10;

    // Central function to process data from both UDP and Serial sources
    void processData(byte[] data, int length) {
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
                        //runAlarm();
                        break;
                    case ALARM_START:
                        if (!alarmed) {
                            append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Alarm", "STARTED"}, file_out);
                            alarmed=true;
                            runAlarm();
                            sendUDP(new byte[]{UDP_ALARM_CONFIRMED});
                        }
                        break;
                    case ALARM_STOP:
                        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Alarm", "STOPPED"}, file_out);
                        alarmed=true;
                        break;
                    case BEEP:
                        append_csv(new String[]{String.valueOf(millis()), formatted_now(), "Beep", "WARNING BEEP"}, file_out);
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

                        //append_csv(new String[]{String.valueOf(millis()), formatted_now(), "ANDROID", "Using android from here"}, file_out);
                        break;

                    case TRACKING_STOP:
                        stopSelf();
                        break;
                }
            } else {

            }
        }
        catch (Exception e) {
            Log.d(TAG, "Error processing data: " + e.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Your service logic here
        return START_STICKY;
    }

    private void reOpenApp() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            startActivity(launchIntent);
        }
    }

    int CHECK_INTERVAL = 5000;
    private void scheduleAlarm() {

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);


        Intent intent = new Intent(context, MainActivity.class);

        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, System.currentTimeMillis() + CHECK_INTERVAL, sender);

       // long triggerAtMillis = SystemClock.elapsedRealtime() + CHECK_INTERVAL;

     //   alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, alarmPendingIntent);
      //  Log.d(TAG, "Alarm scheduled to trigger in " + CHECK_INTERVAL + " ms");
    }


    private void runAlarm() {
        // Launch the app
/*      Intent launchIntent2 = new Intent(context, AlarmActivity.class);
        if (launchIntent2 != null) {
            launchIntent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launchIntent2);
        }*/
        vibrate();
    }
}