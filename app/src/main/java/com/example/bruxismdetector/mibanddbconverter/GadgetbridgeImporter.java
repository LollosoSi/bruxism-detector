package com.example.bruxismdetector.mibanddbconverter;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class GadgetbridgeImporter {

    public void importData(Context context, Uri sourceUri) {
        // 1. Prepare Destination Helper
        SleepDatabaseHelper destDb = new SleepDatabaseHelper(context);

        // 2. Copy Source DB to Cache (SQLite requires a local file path)
        File cacheFile = new File(context.getCacheDir(), "gb_import_temp.db");
        if (!copyFileToCache(context, sourceUri, cacheFile)) return;

        // 3. Open Source DB
        SQLiteDatabase sourceDb = SQLiteDatabase.openDatabase(
                cacheFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

        try {
            // --- A. Import Sessions ---
            Cursor cSession = sourceDb.rawQuery(
                    "SELECT TIMESTAMP, WAKEUP_TIME, IS_AWAKE, TOTAL_DURATION, " +
                            "DEEP_SLEEP_DURATION, LIGHT_SLEEP_DURATION, REM_SLEEP_DURATION, AWAKE_DURATION " +
                            "FROM XIAOMI_SLEEP_TIME_SAMPLE", null);

            while (cSession.moveToNext()) {
                destDb.addSleepSession(
                        cSession.getLong(0)/1000, // TIMESTAMP
                        cSession.getLong(1)/1000, // WAKEUP_TIME
                        cSession.getInt(2),  // IS_AWAKE
                        cSession.getInt(3),  // TOTAL_DURATION
                        cSession.getInt(4),  // DEEP
                        cSession.getInt(5),  // LIGHT
                        cSession.getInt(6),  // REM
                        cSession.getInt(7)   // AWAKE_DUR
                );
            }
            cSession.close();

            // --- B. Import Stages ---
            Cursor cStage = sourceDb.rawQuery(
                    "SELECT TIMESTAMP, STAGE FROM XIAOMI_SLEEP_STAGE_SAMPLE", null);

            while (cStage.moveToNext()) {
                destDb.addStage(
                        cStage.getLong(0)/1000,
                        cStage.getInt(1)
                );
            }
            cStage.close();

            // --- C. Import HR, Stress, SpO2 ---
            // We iterate once over the main activity table to save time
            Cursor cActivity = sourceDb.rawQuery(
                    "SELECT TIMESTAMP, HEART_RATE, STRESS, SPO2 FROM XIAOMI_ACTIVITY_SAMPLE", null);

            while (cActivity.moveToNext()) {
                long ts = cActivity.getLong(0);
                int hr = cActivity.getInt(1);
                int stress = cActivity.getInt(2);
                int spo2 = cActivity.getInt(3);

                if (hr > 0 && hr < 255) destDb.addHeartRate(ts, hr);
                if (stress > 0) destDb.addStress(ts, stress);
                if (spo2 > 0) destDb.addSpO2(ts, spo2);
            }
            cActivity.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sourceDb.close();
            // Optional: cacheFile.delete();
            cacheFile.delete();
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