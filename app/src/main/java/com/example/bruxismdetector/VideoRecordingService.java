package com.example.bruxismdetector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class VideoRecordingService extends Service {

    private static final String TAG = "VideoRecordingService";
    private static final String NOTIFICATION_CHANNEL_ID = "VideoRecordingChannel";
    private static final int NOTIFICATION_ID = 123;

    public static final String ACTION_START_SERVICE = "ACTION_START_SERVICE";
    public static final String ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE";
    public static final String ACTION_TRIGGER_START_SAVE = "ACTION_TRIGGER_START_SAVE";
    public static final String ACTION_TRIGGER_STOP_SAVE = "ACTION_TRIGGER_STOP_SAVE";
    public static final String EXTRA_SESSION_FOLDER_NAME = "EXTRA_SESSION_FOLDER_NAME";
    // NUOVO: Extra per controllare il ritardo dopo il salvataggio
    public static final String EXTRA_NO_DELAY = "EXTRA_NO_DELAY";

    private static final int BUFFER_DURATION_MS = 2 * 60 * 1000; // 2 minutes
    private static final int RETRY_CAMERA_DELAY_MS = 10000; // 10 seconds
    private static final int POST_SAVE_DELAY_MS = 60 * 1000; // 60 seconds

    private File sessionDirectory;
    private File tempFile;

    private volatile HandlerThread backgroundThread;
    private volatile Handler backgroundHandler;
    private volatile CameraDevice cameraDevice;
    private volatile CameraCaptureSession captureSession;
    private volatile CaptureRequest.Builder captureRequestBuilder;
    private volatile MediaRecorder mediaRecorder;

    private volatile boolean isSavingTriggered = false;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerExpiredRunnable = this::handleTimerExpired;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START_SERVICE:
                    cleanupResources();

                    String sessionFolderName = intent.getStringExtra(EXTRA_SESSION_FOLDER_NAME);
                    if (sessionFolderName == null || sessionFolderName.isEmpty()) {
                        stopSelf();
                        return START_NOT_STICKY;
                    }
                    setupDirectories(sessionFolderName);
                    startForeground(NOTIFICATION_ID, createNotification());
                    startBuffering();
                    break;
                case ACTION_STOP_SERVICE:
                    stopServiceAndCleanup();
                    break;
                case ACTION_TRIGGER_START_SAVE:
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    handleStartSaveTrigger(prefs.getBoolean("record_camera_flash", true));
                    break;
                case ACTION_TRIGGER_STOP_SAVE:
                    // MODIFICA: Passa l'intero intent per leggere l'extra
                    handleStopSaveTrigger(intent);
                    break;
            }
        }
        return START_REDELIVER_INTENT;
    }

    private void setupDirectories(String sessionFolderName) {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        sessionDirectory = new File(documentsDir, "RECORDINGS/Camera/" + sessionFolderName);
        if (!sessionDirectory.exists()) sessionDirectory.mkdirs();
        tempFile = new File(sessionDirectory, "recording_temp.mp4");
    }

    private void startBuffering() {
        Log.d(TAG, "Avvio servizio e buffering su file...");
        startBackgroundThread();
        if (backgroundHandler != null) {
            backgroundHandler.post(this::openCamera);
        } else {
            Log.e(TAG, "Impossibile avviare il buffering: backgroundHandler è null.");
        }
    }

    private void stopServiceAndCleanup() {
        Log.d(TAG, "Interruzione servizio...");
        stopForeground(true);
        cleanupResources();
        stopSelf();
        Log.d(TAG, "Servizio fermato.");
    }

    private void cleanupResources() {
        timerHandler.removeCallbacksAndMessages(null);
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join(500);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interruzione durante la pulizia del thread.", e);
            }
        }
        closeCamera();
        if (mediaRecorder != null) {
            try { mediaRecorder.release(); } catch (Exception e) { /* ignora */ }
        }
        backgroundThread = null;
        backgroundHandler = null;
        mediaRecorder = null;
        Log.d(TAG, "Risorse precedenti pulite.");
    }


    private void handleStartSaveTrigger(boolean useTorch) {
        if (isSavingTriggered) return;
        Log.d(TAG, "TRIGGER SALVATAGGIO: Inizio salvataggio.");
        isSavingTriggered = true;
        setTorch(useTorch);
        timerHandler.removeCallbacks(timerExpiredRunnable);
        Log.d(TAG, "Timer del buffer rimosso. Registrazione live in corso.");
    }

    // MODIFICA: Accetta l'intent per leggere l'extra
    private void handleStopSaveTrigger(Intent intent) {
        if (!isSavingTriggered) return;
        Log.d(TAG, "TRIGGER FINE SALVATAGGIO: Finalizzazione.");
        isSavingTriggered = false;
        setTorch(false);

        // Leggi il flag per il ritardo. Il valore predefinito è 'false' (quindi con ritardo).
        boolean noDelay = intent.getBooleanExtra(EXTRA_NO_DELAY, false);

        if (backgroundHandler != null) {
            backgroundHandler.post(() -> stopCurrentRecording(() -> finalizeAndRestart(noDelay)));
        }
    }

    private void handleTimerExpired() {
        Log.d(TAG, "Timer buffer scaduto.");
        if (isSavingTriggered) {
            Log.d(TAG, "Trigger attivo, non riavvio il buffer.");
            return;
        }
        Log.d(TAG, "Riavvio ciclo di buffering...");
        if (backgroundHandler != null) {
            backgroundHandler.post(() -> stopCurrentRecording(this::restartBuffering));
        }
    }

    private void openCamera() {
        if (cameraDevice != null) {
            Log.w(TAG, "openCamera chiamato ma la fotocamera risulta già aperta. Procedo con la chiusura per sicurezza.");
            closeCamera();
        }
        if (backgroundHandler == null) {
            Log.e(TAG, "Impossibile aprire la fotocamera, il background thread non è attivo.");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.i(TAG, "Fotocamera aperta con successo.");
                    cameraDevice = camera;
                    startRecordingToFile();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.w(TAG, "Fotocamera disconnessa dal sistema.");
                    closeCamera();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Errore apertura fotocamera: " + error);
                    closeCamera();

                    if (error == CameraDevice.StateCallback.ERROR_CAMERA_IN_USE) {
                        Log.w(TAG, "Fotocamera in uso. Riprovo tra " + RETRY_CAMERA_DELAY_MS + "ms.");
                        if (backgroundHandler != null) {
                            backgroundHandler.postDelayed(() -> openCamera(), RETRY_CAMERA_DELAY_MS);
                        }
                    }
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Accesso alla fotocamera fallito.", e);
        }
    }

    private void startRecordingToFile() {
        if (cameraDevice == null) {
            Log.e(TAG, "startRecordingToFile chiamato ma cameraDevice è null. Tentativo di riapertura.");
            openCamera();
            return;
        }
        try {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            MediaRecorder newRecorder = new MediaRecorder();
            newRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            newRecorder.setOutputFile(tempFile.getAbsolutePath());
            newRecorder.setVideoEncodingBitRate(500000);
            newRecorder.setVideoFrameRate(15);
            newRecorder.setVideoSize(1280, 720);
            newRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            newRecorder.prepare();
            this.mediaRecorder = newRecorder;

            Surface recorderSurface = mediaRecorder.getSurface();
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureRequestBuilder.addTarget(recorderSurface);

            cameraDevice.createCaptureSession(Collections.singletonList(recorderSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    updateCaptureRequest();
                    try {
                        mediaRecorder.start();
                        Log.d(TAG, "Registrazione avviata su file: " + tempFile.getName());
                        timerHandler.postDelayed(timerExpiredRunnable, BUFFER_DURATION_MS);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "MediaRecorder.start() fallito.", e);
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Configurazione CameraCaptureSession fallita.");
                }
            }, backgroundHandler);
        } catch (IOException | CameraAccessException | IllegalStateException e) {
            Log.e(TAG, "Errore avvio registrazione su file", e);
        }
    }

    private void restartBuffering() {
        Log.d(TAG, "Chiamato restartBuffering.");
        if (backgroundHandler != null) {
            backgroundHandler.post(this::openCamera);
        }
    }

    private void stopCurrentRecording(Runnable onStopComplete) {
        Log.d(TAG, "Fermando la registrazione corrente...");
        timerHandler.removeCallbacks(timerExpiredRunnable);

        try {
            if (captureSession != null) {
                captureSession.close();
            }
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "Eccezione durante stopCurrentRecording (potenzialmente innocua)", e);
        } finally {
            mediaRecorder = null;
            captureSession = null;
            if (onStopComplete != null) {
                onStopComplete.run();
            }
        }
    }

    // MODIFICA: Accetta il flag 'noDelay'
    private void finalizeAndRestart(boolean noDelay) {
        Log.d(TAG, "Finalizzazione e riavvio del buffer.");
        if (tempFile.exists() && tempFile.length() > 0) {
            String timeStamp = new SimpleDateFormat("HH_mm", Locale.getDefault()).format(new Date());
            File finalFile = new File(sessionDirectory, timeStamp + ".mp4");
            if (tempFile.renameTo(finalFile)) {
                Log.i(TAG, "File salvato e spostato in: " + finalFile.getAbsolutePath());
                runOnUiThread(() -> Toast.makeText(VideoRecordingService.this, "Registrazione salvata", Toast.LENGTH_LONG).show());
            } else {
                Log.e(TAG, "Errore: impossibile rinominare/spostare il file finale.");
            }
        } else {
            Log.w(TAG, "Nessun file temporaneo da salvare o file vuoto.");
        }

        if (noDelay) {
            Log.d(TAG, "Riavvio immediato del buffering come richiesto.");
            restartBuffering();
        } else {
            Log.d(TAG, "In attesa di " + POST_SAVE_DELAY_MS + "ms prima di riavviare il buffering.");
            timerHandler.postDelayed(this::restartBuffering, POST_SAVE_DELAY_MS);
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            try { captureSession.close(); } catch (Exception e) { /* ignora */ }
            captureSession = null;
        }
        if (cameraDevice != null) {
            try { cameraDevice.close(); } catch (Exception e) { /* ignora */ }
            cameraDevice = null;
        }
    }

    private void setTorch(boolean enable) {
        if (captureRequestBuilder == null || cameraDevice == null || captureSession == null) return;

        try {
            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, enable ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);

            if (enable && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {

                CameraCharacteristics chars = ((CameraManager) getSystemService(Context.CAMERA_SERVICE)).getCameraCharacteristics(cameraDevice.getId());
                Integer maxLevel = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);

                if (maxLevel != null && maxLevel > 1) {
                    // Clampa la percentuale tra 0 e 100
                    int p = Math.max(0, Math.min(PreferenceManager.getDefaultSharedPreferences(this).getInt("torch_percentage", 100), 100));
                    // Mappa 0-100 sull'intervallo 1-maxLevel
                    int strength = 1 + (p * (maxLevel - 1) / 100);

                    captureRequestBuilder.set(CaptureRequest.FLASH_STRENGTH_LEVEL, strength);
                }
            }
            updateCaptureRequest();
        } catch (Exception e) {
            Log.e(TAG, "Errore torcia", e);
        }
    }

    private void updateCaptureRequest() {
        if (captureSession == null) return;
        try {
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Errore durante l'aggiornamento della richiesta di cattura", e);
        }
    }

    private void runOnUiThread(Runnable task) { new Handler(Looper.getMainLooper()).post(task); }

    private Notification createNotification() {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, CameraTest.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Bruxism Detector Camera")
                .setContentText("Camera is being recorded")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Video recording channel", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void startBackgroundThread() {
        Log.d(TAG, "Creazione di un nuovo background thread.");
        backgroundThread = new HandlerThread("CameraBackground_" + System.currentTimeMillis());
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
}