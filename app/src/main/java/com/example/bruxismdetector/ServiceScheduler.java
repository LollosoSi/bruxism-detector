package com.example.bruxismdetector;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

public class ServiceScheduler {

    private static final String TAG = "ServiceScheduler";
    // A single, consistent request code for the UDPCatcher's primary schedule.
    // If you need multiple independent schedules for UDPCatcher simultaneously,
    // you'd need different request codes or a more complex management system.
    public static final int UDP_CATCHER_PRIMARY_REQUEST_CODE = 1001;

    /**
     * Core scheduling function. Cancels any previous alarm with the same PendingIntent
     * and schedules a new one for the UDPCatcher service at the specified millisecond timestamp.
     *
     * @param context       Context to access AlarmManager.
     * @param triggerAtMillis The exact epoch time in milliseconds to trigger the service.
     */
    public static void scheduleUDPCatcherAtTime(Context context, long triggerAtMillis) {
        //cancelUDPCatcherSchedule(context);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager not available.");
            return;
        }

        Intent intent = new Intent(context, UDPCatcher.class);
        // If UDPCatcher needs to know how it was started (e.g., specific action),
        // you might add it here or in the wrapper functions.
        // intent.setAction("YOUR_SPECIFIC_ACTION_IF_NEEDED");

        PendingIntent pendingIntent;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        pendingIntent = PendingIntent.getService(
                context,
                UDP_CATCHER_PRIMARY_REQUEST_CODE, // Consistent request code
                intent,
                flags
        );

        // Cancel any existing alarm with the same PendingIntent (effectively same request code and intent filter)
        // This is implicitly handled by FLAG_UPDATE_CURRENT if the intent itself doesn't change significantly,
        // but an explicit cancel before setting a new one is clearer for overwriting behavior.
        // However, setExactAndAllowWhileIdle with FLAG_UPDATE_CURRENT should update if an alarm with
        // the same PendingIntent (requestCode + Intent filter) exists.
        // For absolute certainty of replacing, you could explicitly cancel:
        // alarmManager.cancel(pendingIntent); // Uncomment if strict replacement is desired before setting

        try {
            // This will update the existing alarm if one with the same PendingIntent exists,
            // or create a new one if it doesn't.
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            Log.i(TAG, "UDPCatcher service scheduled for: " + new java.util.Date(triggerAtMillis).toString());
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException: Missing SCHEDULE_EXACT_ALARM permission? " + se.getMessage());
            // Handle this, e.g., by informing the user or using a less precise alarm.
        }
    }

    // --- Wrapper Functions ---

    /**
     * Schedules UDPCatcher using a Calendar object.
     *
     * @param context  Context.
     * @param calendar Calendar object set to the desired trigger time.
     */
    public static void scheduleUDPCatcherAtTime(Context context, Calendar calendar) {
        scheduleUDPCatcherAtTime(context, calendar.getTimeInMillis());
    }

    /**
     * Schedules UDPCatcher for a specific hour and minute.
     *
     * @param context      Context.
     * @param hourOfDay    The hour (0-23).
     * @param minute       The minute (0-59).
     *
     */
    public static void scheduleUDPCatcherAtTime(Context context, int hourOfDay, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If the calculated time is in the past for today, schedule for the next day
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DATE, 1);
        }
        scheduleUDPCatcherAtTime(context, calendar);
    }

    /**
     * Schedules UDPCatcher to start after a specified number of minutes from the current time.
     *
     * @param context      Context.
     * @param minutesLater Number of minutes from now.
     */
    public static void scheduleUDPCatcherAfterMinutes(Context context, int minutesLater) {
        if (minutesLater < 0) {
            Log.w(TAG, "Cannot schedule in the past. minutesLater must be non-negative.");
            return;
        }
        long triggerAtMillis = System.currentTimeMillis() + (long) minutesLater * 60 * 1000;
        scheduleUDPCatcherAtTime(context, triggerAtMillis);
    }


    /**
     * Cancels the primary scheduled UDPCatcher service alarm.
     * @param context Context to access AlarmManager.
     */
    public static void cancelUDPCatcherSchedule(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager not available for cancellation.");
            return;
        }

        Intent intent = new Intent(context, UDPCatcher.class);
        PendingIntent pendingIntent;

        int flags = PendingIntent.FLAG_NO_CREATE; // Use FLAG_NO_CREATE to check if it exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        // Recreate the *exact same* PendingIntent (requestCode + Intent filter)
        pendingIntent = PendingIntent.getService(
                context,
                UDP_CATCHER_PRIMARY_REQUEST_CODE,
                intent,
                flags
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel(); // Also cancel the PendingIntent itself
            Log.i(TAG, "Cancelled scheduled UDPCatcher service (request code: " + UDP_CATCHER_PRIMARY_REQUEST_CODE + ").");
        } else {
            Log.d(TAG, "No scheduled UDPCatcher service found to cancel with request code: " + UDP_CATCHER_PRIMARY_REQUEST_CODE);
        }
    }
}