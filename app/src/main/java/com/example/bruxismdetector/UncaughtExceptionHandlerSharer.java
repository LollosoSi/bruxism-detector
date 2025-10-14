package com.example.bruxismdetector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;

public class UncaughtExceptionHandlerSharer implements UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private final Context applicationContext;
    private final UncaughtExceptionHandler defaultUEH;

    // Constructor
    public UncaughtExceptionHandlerSharer(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler(); // Keep the original handler
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        Log.e(TAG, "Uncaught exception caught by custom handler", ex);

        // Prepare crash report details
        StringWriter stackTrace = new StringWriter();
        ex.printStackTrace(new PrintWriter(stackTrace));
        String reportDetails = buildCrashReport(ex, stackTrace.toString());

        // Start an activity to show the dialog and handle sharing
        // This is crucial because we can't reliably show a dialog from here.
        Intent intent = new Intent(applicationContext, CrashDialogActivity.class);
        intent.putExtra(CrashDialogActivity.EXTRA_CRASH_REPORT, reportDetails);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        applicationContext.startActivity(intent);

        // Important: Call the original default handler.
        // This ensures the system still processes the crash as usual (e.g., logs to system, terminates).
        if (defaultUEH != null) {
            defaultUEH.uncaughtException(thread, ex);
        } else {
            // Fallback if there was no default handler (highly unlikely for Android apps)
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }

    private String buildCrashReport(Throwable ex, String stackTrace) {
        StringBuilder report = new StringBuilder();
        report.append("CRASH REPORT\n");
        report.append("------------------------------\n\n");

        // Device Information
        report.append("Device Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        report.append("Device Model: ").append(Build.MODEL).append("\n");
        report.append("Android Version: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");

        // App Information
        try {
            PackageInfo pInfo = applicationContext.getPackageManager().getPackageInfo(applicationContext.getPackageName(), 0);
            report.append("App Version Name: ").append(pInfo.versionName).append("\n");
            report.append("App Version Code: ").append(pInfo.versionCode).append("\n");
        } catch (PackageManager.NameNotFoundException e) {
            report.append("App Version: Not available\n");
        }
        report.append("\n");

        // Exception Details
        report.append("Exception Type: ").append(ex.getClass().getName()).append("\n");
        report.append("Exception Message: ").append(ex.getMessage()).append("\n\n");
        report.append("Stack Trace:\n");
        report.append(stackTrace).append("\n");
        report.append("------------------------------\n");
        report.append("Please provide any additional details about what you were doing when the crash occurred.\n");

        return report.toString();
    }

    /**
     * Call this method from your Application's onCreate() to register the handler.
     */
    public static void init(Context context) {
        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof UncaughtExceptionHandlerSharer)) {
            Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandlerSharer(context));
            Log.i(TAG, "UncaughtExceptionHandlerSharer initialized.");
        }
    }
}