package com.example.bruxismdetector;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class UDPCheckJob extends JobService {

    private static final String TAG = "UDPCheckJob";
    private static final int JOB_ID = 1001;
    private static final String ACTION_CHECK_UDP = "CHECK_UDP";
    private static final int RECEIVE_PORT = 4000;
    private static final int CHECK_INTERVAL = 1 * 1000; // 1 second

    private MulticastSocket receiveSocket;
    private boolean jobCancelled = false;

    public static void scheduleJob(Context context) {
        ComponentName componentName = new ComponentName(context, UDPCheckJob.class);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, componentName)
                .setPersisted(true) // Persist across reboots
                .build();

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        int result = jobScheduler.schedule(jobInfo);
        if (result == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Job scheduled successfully");
        } else {
            Log.d(TAG, "Job scheduling failed");
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Job started");
        doBackgroundWork(params);
        return true; // Indicates that work is being done asynchronously
    }

    private void doBackgroundWork(final JobParameters params) {
        new Thread(() -> {
            try {
                receiveSocket = new MulticastSocket(RECEIVE_PORT);
                receiveSocket.joinGroup(InetAddress.getByName("239.255.0.1"));
                receiveSocket.setReuseAddress(true);
                byte[] buffer = new byte[10000];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiveSocket.setSoTimeout(60000); // Timeout after 60 seconds

                try {
                    receiveSocket.receive(packet);
                    Log.d(TAG, "UDP packet received!");
                    // Start the Tracker service
                    Intent serviceIntent = new Intent(this, Tracker.class);
                    startForegroundService(serviceIntent);
                    jobFinished(params, false); // No need to reschedule
                } catch (IOException e) {
                    Log.d(TAG, "No UDP packet received within timeout");
                    if (!jobCancelled) {
                        scheduleAlarm(this);
                    }
                    jobFinished(params, false); // Reschedule using AlarmManager
                }
            } catch (IOException e) {
                Log.e(TAG, "Error in UDPCheckJob", e);
                jobFinished(params, false);
            } finally {
                if (receiveSocket != null) {
                    receiveSocket.close();
                }
            }
        }).start();
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Job cancelled before completion");
        jobCancelled = true;
        return true; // Reschedule the job
    }

    private void scheduleAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent alarmIntent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 101, alarmIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
        long triggerAtMillis = SystemClock.elapsedRealtime() + CHECK_INTERVAL;

        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
    }
}