package com.example.bruxismdetector;

import static androidx.core.content.ContextCompat.getSystemService;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.util.Calendar;
import android.preference.PreferenceManager;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @SuppressLint("ScheduleExactAlarm")
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && PermissionsActivity.isExactAlarmPermissionGranted(context)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if(prefs.getBoolean("schedule_listener_after_tracker_ends",true)) {
                Log.d(TAG, "Device booted, scheduling UDPCatcher");
                ServiceScheduler.scheduleUDPCatcherAtTime(context,prefs.getInt("ServiceHour", 21), prefs.getInt("ServiceMinute",0));
            }
        }else if (!PermissionsActivity.isExactAlarmPermissionGranted(context)){
            Log.d(TAG, "Permission denied, could not schedule UDPCatcher");

        }
    }
}