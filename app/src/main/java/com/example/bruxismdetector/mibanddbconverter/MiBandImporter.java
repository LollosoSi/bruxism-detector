package com.example.bruxismdetector.mibanddbconverter;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MiBandImporter {

    public void importData(Context context, Uri sourceUri) {
        SleepDatabaseHelper destDb = new SleepDatabaseHelper(context);

        // 1. Copy source DB to cache
        File cacheFile = new File(context.getCacheDir(), "miband_import_temp.db");
        if (!copyFileToCache(context, sourceUri, cacheFile)) return;

        // 2. Open Source DB
        SQLiteDatabase sourceDb = SQLiteDatabase.openDatabase(
                cacheFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

        try {
            // --- A. Import Sessions & Stages (from sleep_segment) ---
            Cursor cSession = sourceDb.query("sleep_segment", new String[]{"value"}, null, null, null, null, null);
            while (cSession.moveToNext()) {
                try {
                    JSONObject json = new JSONObject(cSession.getString(0));

                    // Timestamps in Xiaomi DB are SECONDS, convert to MILLIS for Helper consistency
                    long wakeupTimeSec = json.optLong("wake_up_time", 0);
                    long bedtimeSec = json.optLong("bedtime", 0);

                    if (wakeupTimeSec <= 0 || bedtimeSec <= 0) continue;

                    // Insert Session
                    destDb.addSleepSession(
                            bedtimeSec * 1000L,        // Timestamp (Start)
                            wakeupTimeSec * 1000L,     // Wakeup Time
                            0,                         // isAwake (0 for sleep session)
                            json.optInt("duration", 0), // Duration (usually minutes in Xiaomi JSON, check usage)
                            json.optInt("sleep_deep_duration", 0),
                            json.optInt("sleep_light_duration", 0),
                            json.optInt("sleep_rem_duration", 0),
                            json.optInt("sleep_awake_duration", 0)
                    );

                    // Insert Stages from 'items' array
                    JSONArray items = json.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item != null) {
                                long start = item.optLong("start_time", 0);
                                int state = item.optInt("state", 0);
                                if (start > 0) {
                                    destDb.addStage(start * 1000L, state);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            cSession.close();

            // --- B. Import Time Series (HR, Stress, SpO2) ---
            importTimeSeries(sourceDb, destDb, "hr_record", "bpm", 1);
            importTimeSeries(sourceDb, destDb, "stress_record", "stress", 2);
            importTimeSeries(sourceDb, destDb, "spo2_record", "spo2", 3);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sourceDb.close();
            // cacheFile.delete(); // Optional
        }
    }

    private void importTimeSeries(SQLiteDatabase src, SleepDatabaseHelper dest, String table, String key, int type) {
        try {
            // Check if table exists first (some DB versions might differ)
            Cursor check = src.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{table});
            if (check.getCount() == 0) {
                check.close();
                return;
            }
            check.close();

            Cursor c = src.query(table, new String[]{"value"}, null, null, null, null, null);
            while (c.moveToNext()) {
                try {
                    JSONObject json = new JSONObject(c.getString(0));
                    long timeSec = json.optLong("time", 0);
                    int val = json.optInt(key, -1);

                    if (timeSec > 0 && val != -1) {
                        long timeMillis = timeSec * 1000L;
                        switch (type) {
                            case 1: dest.addHeartRate(timeMillis, val); break;
                            case 2: dest.addStress(timeMillis, val); break;
                            case 3: dest.addSpO2(timeMillis, val); break;
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
            c.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean copyFileToCache(Context context, Uri uri, File destFile) {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}