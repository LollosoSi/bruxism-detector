package com.example.bruxismdetector;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class NoSessionNotificationReceiver extends BroadcastReceiver {

    public static final String action_nosession_cancel = "com.example.bruxismdetector.ACTION_TEST_NO_SESSION_NOTIFICATION";
    public static final String action_nosession_record = "com.example.bruxismdetector.ACTION_TEST_NO_SESSION_RECORD";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);



        if(intent.hasExtra("del_notification")) {
            sendNoSessionNotification(context, true);
            return;
        }


        if(((System.currentTimeMillis() - prefs.getLong("last_tracker_start_ms", 0)) / 1000) > (18*60*60)){
            // Tracker was not started tonight!
            sendNoSessionNotification(context,false);
        }

        NoSessionNotificationReceiver.reschedulenotification(context);


    }

    public static void reschedulenotification(Context ctx){
        DailyNotificationScheduler.scheduleNotificationAtTime(ctx, 8, 30);
    }

    private static final String CHANNEL_ID = "NoSessionNotificationChannel";

    private static void createNotificationChannel(NotificationManager notificationManager) {

        CharSequence name = "Missed Sessions Channel";
        String description = "Add tags to night you did not track";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        notificationManager.createNotificationChannel(channel);

    }
    public static void sendNoSessionNotification(Context context, boolean cancel) {

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Service.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            createNotificationChannel(notificationManager);
        } else {
            Log.e("Nosession", "NotificationManager is null");
            return;
        }


        if(cancel){

            notificationManager.cancel(6535);

            return;
        }



        // Use explicit intent to target the receiver class directly
        Intent intentrecord = new Intent(context, DialogHostActivity.class);
        //intentrecord.setAction(action_nosession_record);
        // Ensure package set to avoid implicit resolution issues
        intentrecord.setPackage(context.getPackageName());
        intentrecord.putExtra("hasSession",false);

        int flagss = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flagss |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntentrecord = PendingIntent.getActivity(
                context,
                6582, // unique request code for this alarm
                intentrecord,
                flagss
        );

        // Use explicit intent to target the receiver class directly
        Intent intentcancel = new Intent(context, NoSessionNotificationReceiver.class);
        intentcancel.setAction(action_nosession_cancel);
        // Ensure package set to avoid implicit resolution issues
        intentcancel.setPackage(context.getPackageName());
        intentcancel.putExtra("del_notification",true);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntentCancel = PendingIntent.getBroadcast(
                context,
                6582, // unique request code for this alarm
                intentcancel,
                flags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.tagging)
                .setContentTitle("Tag your night!")
                .setContentText("Click to add tags.\nYou did not track your session today.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntentrecord)
                .addAction(R.drawable.bad,"Ignore", pendingIntentCancel)
                .setAutoCancel(true)
                .setOngoing(true);


        notificationManager.notify(6535, builder.build());
    }

    public static class DailyNotificationScheduler {

        private static final String TAG = "DailyNotificationScheduler";
        // A single, consistent request code for the UDPCatcher's primary schedule.
        // If you need multiple independent schedules for UDPCatcher simultaneously,
        // you'd need different request codes or a more complex management system.
        public static final int DailyNotificationScheduler_PRIMARY_REQUEST_CODE = 99293;


        /**
         * Schedule a broadcast to the NoSessionNotificationReceiver at the specified time.
         * The receiver listens for ACTION_TEST_NO_SESSION_NOTIFICATION.
         * @param context application context
         * @param triggerAtMillis UTC time in millis when the broadcast should fire
         */
        public static void scheduleNotificationAtTime(Context context, long triggerAtMillis) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager not available.");
                return;
            }
            // Use explicit intent to target the receiver class directly
            Intent intent = new Intent(context, NoSessionNotificationReceiver.class);
            intent.setAction("com.example.bruxismdetector.ACTION_TEST_NO_SESSION_NOTIFICATION");
            // Ensure package set to avoid implicit resolution issues
            intent.setPackage(context.getPackageName());

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    DailyNotificationScheduler_PRIMARY_REQUEST_CODE, // unique request code for this alarm
                    intent,
                    flags
            );
            // Cancel any existing alarm and set a new one
            alarmManager.cancel(pendingIntent);
            try {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                );
                Log.i(TAG, "Scheduled broadcast for: " + new java.util.Date(triggerAtMillis));
            } catch (SecurityException se) {
                Log.e(TAG, "SecurityException scheduling alarm: " + se.getMessage());
            }
        }

        // --- Wrapper Functions ---

        /**
         * Schedules Notification using a Calendar object.
         *
         * @param context  Context.
         * @param calendar Calendar object set to the desired trigger time.
         */
        public static void scheduleNotificationAtTime(Context context, Calendar calendar) {
            scheduleNotificationAtTime(context, calendar.getTimeInMillis());
        }

        /**
         * Schedules Notification for a specific hour and minute.
         *
         * @param context      Context.
         * @param hourOfDay    The hour (0-23).
         * @param minute       The minute (0-59).
         *
         */
        public static void scheduleNotificationAtTime(Context context, int hourOfDay, int minute) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            // If the calculated time is in the past for today, schedule for the next day
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DATE, 1);
            }
            scheduleNotificationAtTime(context, calendar);
        }

        /**
         * Schedules Notification to start after a specified number of minutes from the current time.
         *
         * @param context      Context.
         * @param minutesLater Number of minutes from now.
         */
        public static void scheduleNotificationAfterMinutes(Context context, int minutesLater) {
            if (minutesLater < 0) {
                Log.w(TAG, "Cannot schedule in the past. minutesLater must be non-negative.");
                return;
            }
            long triggerAtMillis = System.currentTimeMillis() + (long) minutesLater * 60 * 1000;
            scheduleNotificationAtTime(context, triggerAtMillis);
        }


        /**
         * Cancels the primary scheduled Notification service alarm.
         * @param context Context to access AlarmManager.
         */
        public static void cancelNotificationSchedule(Context context) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager not available for cancellation.");
                return;
            }

            Intent intent = new Intent(context, NoSessionNotificationReceiver.class);
            PendingIntent pendingIntent;

            int flags = PendingIntent.FLAG_NO_CREATE; // Use FLAG_NO_CREATE to check if it exists
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            // Recreate the *exact same* PendingIntent (requestCode + Intent filter)
            pendingIntent = PendingIntent.getService(
                    context,
                    DailyNotificationScheduler_PRIMARY_REQUEST_CODE,
                    intent,
                    flags
            );

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel(); // Also cancel the PendingIntent itself
                Log.i(TAG, "Cancelled scheduled DailyNotificationScheduler service (request code: " + DailyNotificationScheduler_PRIMARY_REQUEST_CODE + ").");
            } else {
                Log.d(TAG, "No scheduled DailyNotificationScheduler service found to cancel with request code: " + DailyNotificationScheduler_PRIMARY_REQUEST_CODE);
            }
        }
    }
}