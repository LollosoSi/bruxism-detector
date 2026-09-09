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
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
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

import com.example.bruxismdetector.bruxism_grapher2.TunePlayer;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class RingReceiver extends BroadcastReceiver {
    private static final int REQUEST_CODE = 1001;
    //private static final String CHANNEL_ID = "tone_channel";
    private static final String CHANNEL_SILENT_ID = "tone_channel_silent";
    private static final String CHANNEL_SOUND_ID = "tone_channel_sound";


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

    private static final String ACTION_YES = "com.example.bruxismdetector.ACTION_YES";
    private static final String ACTION_NO = "com.example.bruxismdetector.ACTION_NO";
    private static final String ACTION_IGNORE = "com.example.bruxismdetector.ACTION_IGNORE";
    private static final String EXTRA_BEEP_ID = "extra_beep_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandlerSharer(context));
        UncaughtExceptionHandlerSharer.setErrorDisplayMode(UncaughtExceptionHandlerSharer.ErrorDisplayMode.NOTIFICATION);

        String action = intent.getAction();
        if (action != null) {
            if (action.equals(cancel_action_notif)) {
                cancel(context);
                NotificationManagerCompat.from(context).cancel(REQUEST_CODE);
                return;
            }
            // Handle user response actions
            if (action.equals(ACTION_YES) || action.equals(ACTION_NO) || action.equals(ACTION_IGNORE)) {
                long id = intent.getLongExtra(EXTRA_BEEP_ID, -1);
                if (id != -1) {
                    int responseValue = 0;
                    if (action.equals(ACTION_YES)) responseValue = 1;
                    else if (action.equals(ACTION_NO)) responseValue = 2;
                    else if (action.equals(ACTION_IGNORE)) responseValue = 3;

                    BeepDatabaseHelper dbHelper = new BeepDatabaseHelper(context);
                    dbHelper.updateResponse(id, responseValue);
                }
                NotificationManagerCompat.from(context).cancel(REQUEST_CODE);
                return;
            }
            if (action.equals(beep_once)) {
                play2600Hz(context);
                return;
            }
        }

        // Main training logic: 50% chance of beeping
        boolean shouldBeep = Math.random() < 0.5;
        BeepDatabaseHelper dbHelper = new BeepDatabaseHelper(context);
        long id = dbHelper.insertEvent(shouldBeep);

        if (shouldBeep) {
            play2600Hz(context);
        }

        // Show updated notification
        showNotification(context, id, shouldBeep);

        // Schedule next beep
        schedule(context);
    }

    private void showNotification(Context context, long beepId, boolean beeped) {
        createNotificationChannels(context);

        // Prepare actions
        PendingIntent yesPI = createActionPI(context, ACTION_YES, beepId, 0);
        PendingIntent noPI = createActionPI(context, ACTION_NO, beepId, 1);
        PendingIntent ignorePI = createActionPI(context, ACTION_IGNORE, beepId, 2);

        Intent stopIntent = new Intent(context, RingReceiver.class);
        stopIntent.setAction(cancel_action_notif);
        PendingIntent stopPI = PendingIntent.getBroadcast(
                context, 1002, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        String toneText = beeped ? "Tone was played" : "Tone was not played";

        String targetChannel = beeped ? CHANNEL_SILENT_ID : CHANNEL_SOUND_ID;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, targetChannel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Were you bruxing?")
                .setContentText(toneText + ". Beeps end at 19:00")
                .setPriority(beeped ? NotificationCompat.PRIORITY_LOW : NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(false)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_input_add, "Yes", yesPI)
                .addAction(android.R.drawable.ic_delete, "No", noPI)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Ignore", ignorePI)
                .addAction(android.R.drawable.ic_lock_power_off, "Stop", stopPI);

        // If there was no beep, enable default sound and vibration for android < 8.0
        if (!beeped) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL);
        }

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(REQUEST_CODE, builder.build());
        }
    }

    private PendingIntent createActionPI(Context context, String action, long beepId, int offset) {
        Intent intent = new Intent(context, RingReceiver.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_BEEP_ID, beepId);
        // Unique request code for each action/beep combination
        int requestCode = (int) (beepId % 10000) * 10 + offset;
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager == null) return;

            // 1. Silent channel (when we beep)
            NotificationChannel silentChannel = new NotificationChannel(
                    CHANNEL_SILENT_ID,
                    "Beep Training (Silent)",
                    NotificationManager.IMPORTANCE_LOW
            );
            silentChannel.setDescription("Feedback notifications (with beep)");
            silentChannel.setSound(null, null);
            silentChannel.enableVibration(false);
            notificationManager.createNotificationChannel(silentChannel);

            // 2. Noisy channel (when we don't beep but want a standard notification)
            NotificationChannel soundChannel = new NotificationChannel(
                    CHANNEL_SOUND_ID,
                    "Beep Training (Alert)",
                    NotificationManager.IMPORTANCE_HIGH
            );
            soundChannel.setDescription("Feedback notifications (no beep)");
            notificationManager.createNotificationChannel(soundChannel);
        }
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

        // Respect alarm volume (detached from notifications/media)
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int volume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        if (volume == 0) return;

        SoundState.getInstance().setRinging(true);

        // Play sound using modern AudioTrack Builder with AudioAttributes
        AudioTrack audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(generatedSound.length)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();

        // Avoid playing through speaker if headphones are connected (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : outputDevices) {
                if (device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    audioTrack.setPreferredDevice(device);
                    break;
                }
            }
        }

        audioTrack.write(generatedSound, 0, generatedSound.length);
        audioTrack.play();

        // Auto release after playing
        new Handler(Looper.getMainLooper()).postDelayed(()->{
                    SoundState.getInstance().setRinging(false);
                    audioTrack.release();
                },
                (long) (durationSeconds * 1000));
    }


    public static void playTone(Context context, int frequency, int durationMs) {
        int sampleRate = 44100;
        int numSamples = (int) ((durationMs/1000.0) * sampleRate);
        double[] sample = new double[numSamples];
        byte[] generatedSound = new byte[2 * numSamples];

        // Generate sine wave
        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i * frequency / sampleRate);
        }

        // Convert to 16-bit PCM
        int idx = 0;
        for (double dVal : sample) {
            short val = (short) ((dVal * 32767));
            generatedSound[idx++] = (byte) (val & 0x00ff);
            generatedSound[idx++] = (byte) ((val & 0xff00) >>> 8);
        }

        // Respect alarm volume
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int volume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        if (volume == 0) return;

        SoundState.getInstance().setRinging(true);

        // Play sound using modern AudioTrack Builder
        AudioTrack audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(generatedSound.length)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();

        // Routing to headphones if connected
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : outputDevices) {
                if (device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    audioTrack.setPreferredDevice(device);
                    break;
                }
            }
        }

        audioTrack.write(generatedSound, 0, generatedSound.length);
        audioTrack.play();

        // Auto release after playing
        new Handler(Looper.getMainLooper()).postDelayed(()->{
                    SoundState.getInstance().setRinging(false);
                    audioTrack.release();
                },
                (long) (durationMs));
    }



    // In RingReceiver.java, update the schedule method
    @SuppressLint("ScheduleExactAlarm")
    public static void schedule(Context context) {
        cancel(context);

        testVolumeAndReportUser(context);

        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        if (hour >= 19) return; // Don't schedule after 19:00

        // Random delay (using the existing 10min to 1h range or similar)
        long minDelay = TimeUnit.MINUTES.toMillis(30);
        long maxDelay = TimeUnit.HOURS.toMillis(1);
        long delay = minDelay + (long)(Math.random() * (maxDelay - minDelay));

        long triggerAt = System.currentTimeMillis() + delay;
        Calendar triggerCal = Calendar.getInstance();
        triggerCal.setTimeInMillis(triggerAt);

        // Ensure it starts at least at 8:00 AM
        if (triggerCal.get(Calendar.HOUR_OF_DAY) < 8) {
            triggerCal.set(Calendar.HOUR_OF_DAY, 8);
            triggerCal.set(Calendar.MINUTE, (int)(Math.random() * 30)); // starting randomly between 8:00 and 8:30
            triggerCal.set(Calendar.SECOND, 0);
            triggerCal.set(Calendar.MILLISECOND, 0);
            triggerAt = triggerCal.getTimeInMillis();
        }

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