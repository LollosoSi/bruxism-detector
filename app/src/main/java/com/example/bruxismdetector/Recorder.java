package com.example.bruxismdetector;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

public class Recorder extends Service {


    private static final String CHANNEL_ID = "AudioRecorder";
    private static final int NOTIFICATION_ID = 2;

    public static String ACTION_STOP = "ACTION_STOP";

    NotificationManager notificationManager = null;

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

    SessionTracker sessionTracker = null;
    public void setSessionTrackerInstance(SessionTracker st){
        sessionTracker = st;
    }

    private void createNotificationChannel() {
        CharSequence name = "Recorder Channel";
        String description = "Bruxism notifications";
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bruxism Tracker Service")
                .setContentText("Is recording audio")
                .setSmallIcon(android.R.drawable.presence_audio_online)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Intent contains filename
        if(intent != null) {

            if (intent.hasExtra("filename3")) {
                startRecording(SessionTracker.createWriter(intent.getStringExtra("filename3")), this);
            }

            if (Objects.equals(intent.getAction(), ACTION_STOP)) {
                stopRecording();
                stopSelf();
            }
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // --- Variables for Running Average ---
    private static final int AVERAGE_WINDOW_SIZE = 50; // Number of dB samples to average (tune this)
    private static final double DB_THRESHOLD_ABOVE_AVERAGE = 6.0; // Log if current dB is X dB above average (tune this)
    private Queue<Double> dbWindow = new LinkedList<>();
    private double currentDbSum = 0.0;
    private double runningAverageDb = -120.0; // Initialize to a very low value (silence)
    private static final String TAG = "AudioLogger";
    private static final int DEFAULT_SAMPLE_RATE = 44100; // Or 16000, etc.
    private static final String WAKE_LOCK_TAG = "BruxismDetector:AudioLogger";

    private AudioRecord audioRecord;
    private Thread recordThread;
    private volatile boolean isRunning = false; // volatile for thread visibility

    // Ensure this method is called only after RECORD_AUDIO permission is confirmed to be granted.
    @SuppressLint("MissingPermission") // Suppress warning as permission is handled externally
    public void startRecording(PrintWriter noise_out, Context ctx) {
        if (isRunning) {
            Log.w(TAG, "Recording is already in progress.");
            return;
        }

        // Optional: Double-check permission, though you state it's handled.
        // This is good practice if the guarantee isn't absolute throughout your app's lifecycle.
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted. Cannot start recording.");
            // If this happens, your external permission handling has a flaw.
            return;
        }

        int bufferSize = AudioRecord.getMinBufferSize(DEFAULT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid AudioRecord parameters or unable to get buffer size.");
            return;
        }

        // Release previous instance if it somehow exists and wasn't cleaned up
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing previous AudioRecord instance", e);
            }
            audioRecord = null;
        }

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    DEFAULT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to create AudioRecord instance: " + e.getMessage());
            return;
        }


        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized. Check MIC availability or other audio conflicts.");
            if (audioRecord != null) { // Attempt to release if created but not initialized
                audioRecord.release();
                audioRecord = null;
            }
            return;
        }

        dbWindow.clear();
        currentDbSum = 0.0;
        runningAverageDb = -120.0;

        final short[] buffer = new short[bufferSize];

        try {
            audioRecord.startRecording();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to start recording: " + e.getMessage());
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }
            return;
        }

        isRunning = true;
        recordThread = new Thread(() -> {
            Log.d(TAG, "Recording thread started.");
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            PowerManager.WakeLock wakeLock = null;

            try {
                // --- Acquire WakeLock ---
                PowerManager powerManager = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
                    wakeLock.setReferenceCounted(false); // Optional: false if you manage acquire/release strictly
                    try {
                        wakeLock.acquire(); // You can also specify a timeout: wakeLock.acquire(timeoutMillis);
                        Log.i(TAG, "WakeLock acquired.");
                    } catch (SecurityException se) {
                        Log.e(TAG, "Failed to acquire WakeLock due to SecurityException. Check WAKE_LOCK permission.", se);
                        // Potentially stop here or proceed without wakelock if that's acceptable
                        wakeLock = null; // Ensure wakelock is null if acquire failed
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to acquire WakeLock.", e);
                        wakeLock = null;
                    }
                } else {
                    Log.w(TAG, "PowerManager not available, cannot acquire WakeLock.");
                }

                while (isRunning) {
                    if (audioRecord == null || audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                        Log.w(TAG, "AudioRecord is null or not recording. Stopping thread.");
                        break;
                    }



                    int read = audioRecord.read(buffer, 0, buffer.length);
                    // Avoid recording when the ringing or the alarm is enabled
                    if (read > 0 && !RingReceiver.SoundState.getInstance().isRinging() && (sessionTracker == null || !sessionTracker.alarmed)) {
                        double sumSquare = 0;
                        for (int i = 0; i < read; i++) {
                            sumSquare += buffer[i] * buffer[i];
                        }
                        double rms = Math.sqrt(sumSquare / read);
                        double currentDb;

                        if (rms > 0) {
                            currentDb = 20 * Math.log10(rms / (double) Short.MAX_VALUE);
                        } else {
                            currentDb = -120.0; // Effective silence
                        }

                        // --- Update Running Average ---
                        if (dbWindow.size() >= AVERAGE_WINDOW_SIZE) {
                            currentDbSum -= dbWindow.poll(); // Remove oldest value from sum and window
                        }
                        dbWindow.offer(currentDb); // Add current dB to window
                        currentDbSum += currentDb;   // Add current dB to sum

                        if (!dbWindow.isEmpty()) {
                            runningAverageDb = currentDbSum / dbWindow.size();
                        } else {
                            runningAverageDb = -120.0; // Should not happen if window always gets values
                        }
                        // --- End Update Running Average ---

                        // --- Log based on threshold above average ---
                        // Also ensure it's not absolute silence before comparing to average
                        if (currentDb > -119.0 && currentDb > (runningAverageDb + DB_THRESHOLD_ABOVE_AVERAGE)) {
                            Log.d(TAG, String.format("dB: %.2f (Avg: %.2f) -> LOGGING", currentDb, runningAverageDb));
                            if (noise_out != null) {
                                SessionTracker.append_csv(new String[]{String.valueOf(SessionTracker.millis()), String.valueOf(((int)(currentDb*100.0))/100.0)}, noise_out);
                            }
                        } else if (currentDb > -119.0) {
                            // Optional: Log values that are not silent but below the average threshold for debugging
                            //Log.v(TAG, String.format("dB: %.2f (Avg: %.2f) -> Below threshold", currentDb, runningAverageDb));
                        }
                        // --- End Log based on threshold ---

                    } else if (read < 0) { // Handle errors from read
                        Log.e(TAG, "AudioRecord read error: " + read);
                        break; // Exit loop on read error
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }finally {
                Log.d(TAG, "Recording thread finishing and cleaning up.");
                cleanupAudioRecord(noise_out);
                if (wakeLock != null && wakeLock.isHeld())
                    wakeLock.release();
            }
        }, "AudioProcessingThread");

        recordThread.start();
        Log.i(TAG, "Recording started successfully.");
    }

    private void cleanupAudioRecord(PrintWriter noise_out) {
        if (audioRecord != null) {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    audioRecord.stop();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error stopping AudioRecord: " + e.getMessage());
                }
            }
            try {
                audioRecord.release();
            } catch (Exception e) { // Catch generic Exception as release can sometimes throw unexpected things
                Log.e(TAG, "Error releasing AudioRecord: " + e.getMessage());
            }
            audioRecord = null;
        }
        if (noise_out != null) {
            try {
                noise_out.flush();
                noise_out.close();
            } catch (Exception e) {
                Log.e(TAG, "Error flushing/closing PrintWriter: " + e.getMessage());
            }
        }
        Log.d(TAG, "AudioRecord cleaned up.");
    }

    public void stopRecording() {
        if (!isRunning) {
            Log.w(TAG, "Recording is not currently running.");
            return;
        }
        Log.i(TAG, "Attempting to stop recording...");
        isRunning = false; // Signal the recording thread to stop its loop

        if (recordThread != null) {
            try {
                // Wait for the recording thread to finish its execution.
                // The recording thread is responsible for calling cleanupAudioRecord().
                // A timeout is important to prevent blocking indefinitely.
                recordThread.join(1000); // Wait up to 1 second for the thread to complete

                if (recordThread.isAlive()) {
                    Log.w(TAG, "Recording thread did not finish in the allocated time. It might still be running.");
                    // Consider if any further action is needed here, though typically the thread
                    // should exit based on the 'isRunning' flag.
                    // Forcing an interrupt could be an option but can lead to inconsistent states
                    // if not handled carefully within the thread.
                    // recordThread.interrupt(); // Use with caution
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for recording thread to finish.", e);
                // Restore the interrupted status as per best practice
                Thread.currentThread().interrupt();
            } finally {
                // Ensure recordThread reference is cleared if it's no longer alive
                // or if we're done trying to manage it from this method.
                if (recordThread != null && !recordThread.isAlive()) {
                    recordThread = null;
                }
            }
        } else {
            Log.w(TAG, "recordThread was null, but isRunning was true. This indicates an inconsistent state.");
            // If recordThread is null, but isRunning was true, it implies something went wrong
            // during startRecording or the thread terminated unexpectedly without resetting isRunning.
            // You might want to directly call cleanup here if you have access to the PrintWriter,
            // assuming it's safe to do so.
            // if (this.currentNoiseOutputWriter != null) { // Assuming currentNoiseOutputWriter is a member
            //     Log.d(TAG, "Calling cleanup directly as recordThread is null.");
            //     cleanupAudioRecord(this.currentNoiseOutputWriter);
            // }
        }
        Log.i(TAG, "stopRecording method finished.");
    }
}
