package com.example.bruxismdetector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AppLaunchReceiver extends BroadcastReceiver {
    private static final String TAG = "AppLaunchReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "AppLaunchReceiver received alarm");
        launchApp(context);
    }

    private void launchApp(Context context) {
        context.startActivity(new Intent(context, MainActivity.class));

    }

}