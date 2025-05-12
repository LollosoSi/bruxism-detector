package com.example.bruxismdetector;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPCatcher extends Service {
    public UDPCatcher() {

    }

    public static final String ACTION_STOP = "STOPLISTENER";
    public static final String ACTION_RESCHEDULE_30 = "RESCHEDULE_30";
    public static final String ACTION_RESCHEDULE_60 = "RESCHEDULE_60";

    private MulticastSocket receiveSocket;
    private DatagramSocket sendSocket;
    private InetAddress multicastAddress;

    private int sendPort;
    private int receivePort;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleAction(intent);
        startForeground(1, buildNotification());
        setupUDP(4001, 4000);

        return START_NOT_STICKY;
    }

    private void handleAction(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        switch (intent.getAction()) {
            case ACTION_STOP:
                stopSelf();
                break;
            case ACTION_RESCHEDULE_30:
                scheduleSelfAfterMinutes(30);
                stopSelf();
                break;
            case ACTION_RESCHEDULE_60:
                scheduleSelfAfterMinutes(60);
                stopSelf();
                break;
        }
    }

    private Notification buildNotification() {
        PendingIntent stopIntent = buildServicePendingIntent(ACTION_STOP);
        PendingIntent resched30 = buildServicePendingIntent(ACTION_RESCHEDULE_30);
        PendingIntent resched60 = buildServicePendingIntent(ACTION_RESCHEDULE_60);

        return new NotificationCompat.Builder(this, "listener_channel")
                .setContentTitle("")
                .setContentText("Waiting for your Arduino to come online")
                .setSmallIcon(android.R.drawable.ic_input_get)
                .addAction(android.R.drawable.presence_offline, "Stop", stopIntent)
                .addAction(android.R.drawable.star_off, "30m", resched30)
                .addAction(android.R.drawable.star_off, "1h", resched60)
                .setOngoing(true)
                .build();
    }

    private PendingIntent buildServicePendingIntent(String action) {
        Intent intent = new Intent(this, UDPCatcher.class);
        intent.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressLint("ScheduleExactAlarm")
    private void scheduleSelfAfterMinutes(int minutes) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, UDPCatcher.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAtMillis = System.currentTimeMillis() + minutes * 60 * 1000;
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("listener_channel", "Listener Channel", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("UDP Listener Service");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    // ---- Your existing logic here ----
    private void setupUDP(int sendPort, int receivePort) {
        try {
            this.sendPort = sendPort;
            this.receivePort = receivePort;
            multicastAddress = InetAddress.getByName("239.255.0.1");

            // Set up receiving socket
            receiveSocket = new MulticastSocket(receivePort);
            receiveSocket.joinGroup(multicastAddress);
            receiveSocket.setReuseAddress(true);

            running = true;
            executor.execute(this::receiveUDP);
            Log.d("UDPCatcher", "UDP setup complete. Receiving on port " + receivePort + ", sending on port " + sendPort);

        } catch (IOException e) {
            Log.e("UDPCatcher", "Error setting up UDP", e);
        }
    }

    private void receiveUDP() {
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
                Log.d("UDPCatcher", "Received bytes: " + packet.getLength());

                if(length%SessionTracker.dataelementbytes == 0){

                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    prefs.edit().putBoolean("collect_data_on_end", true).apply();

                    // Special packet detected!
                    startService(new Intent(this, Tracker2.class));
                    stopSelf();
                }
            } catch (IOException e) {
                if (running) {
                    Log.e("UDPCatcher", "Error receiving UDP packet", e);
                }
            }
        }

        if(multicastLock.isHeld())
            multicastLock.release();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        if (receiveSocket != null && !receiveSocket.isClosed()) {
            receiveSocket.close();
        }
        executor.shutdownNow();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}