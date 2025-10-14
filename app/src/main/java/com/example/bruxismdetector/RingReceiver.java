package com.example.bruxismdetector;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class RingReceiver extends BroadcastReceiver {
    private static final int REQUEST_CODE = 1001;
    private static final String CHANNEL_ID = "tone_channel";
    private static final String cancel_action_notif = "cancel_action_notif";
    public static final String beep_once = "beep_once";

    public static class SoundState {
        private static final SoundState instance = new SoundState();
        private volatile boolean isRinging = false;

        private SoundState() {}

        public static SoundState getInstance() {
            return instance;
        }

        public void setRinging(boolean ringing) {
            isRinging = ringing;
        }

        public boolean isRinging() {
            return isRinging;
        }
    }

    public static void testVolumeAndReportUser(Context context){
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int volume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        Log.d("RingReceiver", "Volume: " + volume);
        if (volume <= 1 || volume >= 20) {
            // Show volume picker if volume too low or too high
            Intent volumeIntent = new Intent(Settings.ACTION_SOUND_SETTINGS);
            volumeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(volumeIntent);
            if (volume <= 1)
                Toast.makeText(context, "Raise alarm volume to hear the next beeps", Toast.LENGTH_LONG).show();
            else
                Toast.makeText(context, "Your alarm volume might be too high for the next beeps", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction()!=null) {
            if (intent.getAction().equals(cancel_action_notif)) {
                cancel(context);

                // Cancel the notification
                NotificationManagerCompat manager = NotificationManagerCompat.from(context);
                manager.cancel(REQUEST_CODE);  // Same ID used in notify()

                return;
            }

        }

        play2600Hz(context);

        if(intent.getAction()!=null)
            if(intent.getAction().equals(beep_once)){
                return;
            }

        // Show notification
        showNotification(context);

        // Schedule next
        schedule(context);
    }

    private void showNotification(Context context) {
        createNotificationChannel(context);

        Intent stopIntent = new Intent(context, RingReceiver.class);
        stopIntent.setAction(cancel_action_notif);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                context, 1002, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Did you hear the tone?")
                .setContentText("Dismiss this notification or cancel the trainer. Beeps will stop automatically at 19")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_delete, "Stop trainer", stopPendingIntent);

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        manager.notify(REQUEST_CODE, builder.build());
    }

    private void createNotificationChannel(Context context) {
        CharSequence name = "Beep Notification";
        String description = "Notifications for Beep events";
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null)
            notificationManager.createNotificationChannel(channel);
    }


    public static void play2600Hz(Context context) {
        int sampleRate = 44100;
        double durationSeconds = 0.2; // 200 ms
        int numSamples = (int) (durationSeconds * sampleRate);
        double[] sample = new double[numSamples];
        byte[] generatedSound = new byte[2 * numSamples];

        // Generate sine wave
        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i * 2600 / sampleRate);
        }

        // Convert to 16-bit PCM
        int idx = 0;
        for (double dVal : sample) {
            short val = (short) ((dVal * 32767));
            generatedSound[idx++] = (byte) (val & 0x00ff);
            generatedSound[idx++] = (byte) ((val & 0xff00) >>> 8);
        }

        // Respect notification volume
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int volume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        if (volume == 0) return;

        SoundState.getInstance().setRinging(true);

        // Play sound
        AudioTrack audioTrack = new AudioTrack(
                AudioManager.STREAM_ALARM,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                generatedSound.length,
                AudioTrack.MODE_STATIC
        );

        audioTrack.write(generatedSound, 0, generatedSound.length);
        audioTrack.play();

        // Auto release after playing
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            SoundState.getInstance().setRinging(false);
            audioTrack.release();
            },
                (long) (durationSeconds * 1000));
    }


    @SuppressLint("ScheduleExactAlarm")
    public static void schedule(Context context) {
        cancel(context);

        testVolumeAndReportUser(context);

        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        if (hour >= 19) return; // Don't schedule after 19:00

        // Random delay between 30min and 2h
        long minDelay = TimeUnit.MINUTES.toMillis(1);
        long maxDelay = TimeUnit.HOURS.toMillis(2);
        long delay = minDelay + (long)(Math.random() * (maxDelay - minDelay));

        long triggerAt = System.currentTimeMillis() + delay;
        Calendar triggerCal = Calendar.getInstance();
        triggerCal.setTimeInMillis(triggerAt);
        if (triggerCal.get(Calendar.HOUR_OF_DAY) >= 19) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = getPendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = getPendingIntent(context, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            //Toast.makeText(context, "Trainer canceled", Toast.LENGTH_SHORT).show();
        }
    }

    private static PendingIntent getPendingIntent(Context context, int flags) {
        Intent intent = new Intent(context, RingReceiver.class);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }
}