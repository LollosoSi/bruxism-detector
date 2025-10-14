package com.example.bruxismdetector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.PrintWriter;
import java.util.Objects;

public class MovementDetectorService extends Service implements SensorEventListener {

    private static final String CHANNEL_ID = "AccelRecorder";
    private static final int NOTIFICATION_ID = 3;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isMoving = false;
    private long lastStillTime = 0;

    private static final float MOVEMENT_THRESHOLD = 0.1f;  // m/s²
    private static final int STILL_TIMEOUT_MS = 5000;

    NotificationManager notificationManager = null;

    public static String ACTION_STOP = "com.example.bruxismdetector.STOP";

    @Override
    public void onCreate() {
        super.onCreate();

        this.notificationManager = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            createNotificationChannel();
        } else {
            Log.e("Tracker", "NotificationManager is null");
        }

        // Launch the foreground service notification (silenced and reduced) that the audio is being recorded
        Notification n = buildNotification();
        n.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        startForeground(NOTIFICATION_ID, n);

    }

    @Override
    public void onDestroy() {
        sensorManager.unregisterListener(this);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        CharSequence name = "Accelerometer Channel";
        String description = "Bruxism notifications";
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bruxism Tracker Service")
                .setContentText("Is recording accelerometer")
                .setSmallIcon(android.R.drawable.zoom_plate)
                .setOngoing(true)
                .build();
    }


    PrintWriter output = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Intent contains filename
        if(intent != null) {

            if (intent.hasExtra("filename4")) {
                output=SessionTracker.createWriter(intent.getStringExtra("filename4"));

                sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (accelerometer != null) {
                    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                }

            }

            if (Objects.equals(intent.getAction(), ACTION_STOP)) {
                sensorManager.unregisterListener(this);

                output.flush();
                output.close();

                stopSelf();
            }
        }

        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        double magnitude = Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

        if (Math.abs(magnitude) > MOVEMENT_THRESHOLD) {
            if (!isMoving) {
                isMoving = true;
                Log.d("MovementService", "User is moving");
                // Notify listeners / update shared state
            }
            //Log.d("MovementService", "MAG: " + Math.abs(magnitude));
            SessionTracker.append_csv(new String[]{String.valueOf(SessionTracker.millis()), String.valueOf(((int)(Math.abs(magnitude)*100.0))/100.0)}, output);

            lastStillTime = System.currentTimeMillis();
        } else {
            if (System.currentTimeMillis() - lastStillTime > STILL_TIMEOUT_MS && isMoving) {
                isMoving = false;
                Log.d("MovementService", "User is still");
                // Notify listeners / update shared state
            }
        }


    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}