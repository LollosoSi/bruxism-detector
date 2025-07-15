package com.example.bruxismdetector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class CrashDialogActivity extends Activity {

    public static final String EXTRA_CRASH_REPORT = "com.example.bruxismdetector.CRASH_REPORT";
    private static final String TAG = "CrashDialogActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Attempt to make window look like a dialog and show over keyguard if possible.
        // Behavior can vary across Android versions and devices.
        try {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | // Required for status bar coloring
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                            WindowManager.LayoutParams.FLAG_FULLSCREEN | // May be overridden by theme
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | // Attempt to show over lock screen
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | // Attempt to dismiss keyguard
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |   // Turn screen on
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON     // Keep screen on
            );
        } catch (Exception e) {
            Log.w(TAG, "Could not set all window flags for CrashDialogActivity", e);
        }


        // Optional: Apply a dialog-like theme to this activity in AndroidManifest.xml
        // android:theme="@android:style/Theme.DeviceDefault.Light.Dialog.Alert"
        // or a custom one:
        // <style name="Theme.TransparentDialog" parent="Theme.AppCompat.Dialog.Alert">
        //     <item name="android:windowIsTranslucent">true</item>
        //     <item name="android:windowBackground">@android:color/transparent</item>
        //     <item name="android:windowContentOverlay">@null</item>
        //     <item name="android:windowNoTitle">true</item>
        //     <item name="android:windowIsFloating">true</item>
        //     <item name="android:backgroundDimEnabled">true</item> <!-- Dims background -->
        // </style>
        // And then in AndroidManifest.xml for this activity:
        // android:theme="@style/Theme.TransparentDialog"

        String crashReport = getIntent().getStringExtra(EXTRA_CRASH_REPORT);
        if (crashReport == null) {
            Log.e(TAG, "No crash report found in intent. Finishing.");
            finishAndKillProcess(); // Exit if no report
            return;
        }

        showCrashDialog(crashReport);
    }

    private void showCrashDialog(String reportDetails) {
        new AlertDialog.Builder(this)
                .setTitle("Application Error")
                .setMessage("Unfortunately, the application has stopped unexpectedly. " +
                        "Would you like to share the error report with the developers to help fix this issue?")
                .setCancelable(false) // User must choose an option
                .setPositiveButton("Share Report", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        shareCrashReport(reportDetails);
                        // The process will be killed by the default uncaught exception handler
                        // after this activity finishes. We call finish to close this dialog activity.
                        finish();
                        // No need to call finishAndKillProcess() here if defaultUEH will handle it.
                        // If defaultUEH is null or you want to be absolutely sure, you can,
                        // but it might interfere with the default handler's process.
                    }
                })
                .setNegativeButton("Close App", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // The process will be killed by the default uncaught exception handler.
                        // We just finish this dialog activity.
                        finish();
                        // finishAndKillProcess(); // See comment above
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        // If the dialog is dismissed by other means (e.g. back button if not cancelable(false))
                        // ensure we still finish the activity.
                        // This is less likely if setCancelable(false) is used.
                        finish();
                        // finishAndKillProcess(); // See comment above
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert) // Optional: add an icon
                .show();
    }

    private void shareCrashReport(String reportDetails) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc822"); // For email clients
        // intent.setType("text/plain"); // More generic, might show more sharing options

        // TODO: Replace with your actual support email address
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"lollosositv@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Crash Report: " + getApplicationName());
        intent.putExtra(Intent.EXTRA_TEXT, reportDetails);

        try {
            startActivity(Intent.createChooser(intent, "Share crash report via..."));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No application found to share the report.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "No activity found to handle sharing intent.", e);
        }
    }

    private String getApplicationName() {
        try {
            return getPackageManager().getApplicationLabel(getApplicationInfo()).toString();
        } catch (Exception e) {
            return "YourApp"; // Fallback
        }
    }

    /**
     * Finishes the activity and ensures the application process is terminated.
     * This should be called if the default uncaught exception handler might not
     * properly terminate the process after this activity.
     */
    private void finishAndKillProcess() {
        finishAffinity(); // Finishes this activity and all activities immediately below it in the current task.
        // The default uncaught exception handler should already be terminating the process.
        // If you are *not* chaining to the defaultUEH, or want to be absolutely sure:
        // android.os.Process.killProcess(android.os.Process.myPid());
        // System.exit(10);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CrashDialogActivity destroyed.");
    }
}