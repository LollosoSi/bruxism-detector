package com.example.bruxismdetector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;

public class UncaughtExceptionHandlerSharer implements UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static final String CRASH_NOTIFICATION_CHANNEL_ID = "CrashNotificationChannel";
    private final Context applicationContext;
    private final UncaughtExceptionHandler defaultUEH;

    public enum ErrorDisplayMode {
        DIALOG,
        NOTIFICATION
    }

    private static volatile ErrorDisplayMode currentMode = ErrorDisplayMode.DIALOG;

    public UncaughtExceptionHandlerSharer(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void setErrorDisplayMode(ErrorDisplayMode mode) {
        Log.i(TAG, "Impostazione della modalità di gestione dei crash a: " + mode);
        currentMode = mode;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        Log.e(TAG, "Uncaught exception caught by custom handler", ex);

        StringWriter stackTrace = new StringWriter();
        ex.printStackTrace(new PrintWriter(stackTrace));
        String reportDetails = buildCrashReport(ex, stackTrace.toString());

        if (currentMode == ErrorDisplayMode.DIALOG) {
            startCrashDialogActivity(reportDetails);
        } else {
            showCrashNotification(reportDetails);
        }

        if (defaultUEH != null) {
            defaultUEH.uncaughtException(thread, ex);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }

    private void startCrashDialogActivity(String reportDetails) {
        Intent intent = new Intent(applicationContext, CrashDialogActivity.class);
        intent.putExtra(CrashDialogActivity.EXTRA_CRASH_REPORT, reportDetails);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        applicationContext.startActivity(intent);
    }

    private void showCrashNotification(String reportDetails) {
        NotificationManager notificationManager = (NotificationManager) applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CRASH_NOTIFICATION_CHANNEL_ID,
                    "Crash Reports",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        // Intent per l'azione "Share" (opzionale, per il tap sulla notifica)
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Bruxism Detector Crash Report");
        shareIntent.putExtra(Intent.EXTRA_TEXT, reportDetails);
        PendingIntent sharePendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                Intent.createChooser(shareIntent, "Share Crash Report"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // MODIFICA: Intent specifico per l'azione "Send via Email"
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:")); // Solo le app di posta elettronica dovrebbero gestire questo
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"lollosositv+crashreports@gmail.com"});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Bruxism Detector Crash Report");
        emailIntent.putExtra(Intent.EXTRA_TEXT, reportDetails);
        PendingIntent emailPendingIntent = PendingIntent.getActivity(
                applicationContext,
                1, // Usa un requestCode diverso per evitare conflitti
                emailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );


        Notification notification = new NotificationCompat.Builder(applicationContext, CRASH_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Bruxism Detector has crashed")
                .setContentText("Tap to share the crash report.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText("An unexpected error occurred while running in the background. Tap to share the crash report."))
                .setContentIntent(sharePendingIntent) // Tap sulla notifica apre il selettore di condivisione
                .setAutoCancel(true)
                .addAction(android.R.drawable.sym_action_email, "Send via Email", emailPendingIntent)
                .build();

        notificationManager.notify(1, notification);
    }

    private String buildCrashReport(Throwable ex, String stackTrace) {
        StringBuilder report = new StringBuilder();
        report.append("CRASH REPORT\n");
        report.append("------------------------------\n\n");
        report.append("Device Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        report.append("Device Model: ").append(Build.MODEL).append("\n");
        report.append("Android Version: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        try {
            PackageInfo pInfo = applicationContext.getPackageManager().getPackageInfo(applicationContext.getPackageName(), 0);
            report.append("App Version Name: ").append(pInfo.versionName).append("\n");
            report.append("App Version Code: ").append(pInfo.versionCode).append("\n");
        } catch (PackageManager.NameNotFoundException e) {
            report.append("App Version: Not available\n");
        }
        report.append("\n");
        report.append("Exception Type: ").append(ex.getClass().getName()).append("\n");
        report.append("Exception Message: ").append(ex.getMessage()).append("\n\n");
        report.append("Stack Trace:\n");
        report.append(stackTrace).append("\n");
        report.append("------------------------------\n");
        report.append("Please provide any additional details about what you were doing when the crash occurred.\n");
        return report.toString();
    }

    public static void init(Context context) {
        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof UncaughtExceptionHandlerSharer)) {
            Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandlerSharer(context));
            Log.i(TAG, "UncaughtExceptionHandlerSharer initialized.");
        }
    }
}