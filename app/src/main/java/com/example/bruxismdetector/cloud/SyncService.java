package com.example.bruxismdetector.cloud;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncService extends Service {
    private static final String CHANNEL_ID = "CloudSyncChannel";
    private static final int NOTIF_ID = 101;
    private ExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Legge il flag, se non specificato di default è false (solo upload)
        boolean performDownload = intent != null && intent.getBooleanExtra("PERFORM_DOWNLOAD", false);

        String title = performDownload ? "Syncing" : "Saving to Cloud";
        String desc = performDownload ? "Sync in progress..." : "Saving data...";

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(desc)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setOngoing(true)
                .build();

        startForeground(NOTIF_ID, notification);

        executor.execute(() -> {
            try {
                CloudPreferences prefs = new CloudPreferences(getApplicationContext());

                if (prefs.getUUID() != null && prefs.getPassword() != null) {
                    SyncEngine engine = new SyncEngine(getApplicationContext());
                    // Passa il flag all'engine
                    engine.executeSync(performDownload);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                stopForeground(true);
                stopSelf();
            }
        });

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Cloud Synchronization",
                    NotificationManager.IMPORTANCE_LOW // Low = Nessun suono, solo icona visiva
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Non serve il binding per questo task
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}