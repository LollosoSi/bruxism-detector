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

    public void importData(Context context, Uri sourceUri, ProgressReport progressReport) {
        // 1. Prepare Destination Helper
        SleepDatabaseHelper destDb = new SleepDatabaseHelper(context);

        // 2. Copy Source DB to Cache (SQLite requires a local file path)
        File cacheFile = new File(context.getCacheDir(), "gb_import_temp.db");
        if (!copyFileToCache(context, sourceUri, cacheFile)) return;

        // 3. Open Source DB
        SQLiteDatabase sourceDb = SQLiteDatabase.openDatabase(
                cacheFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

        try {
            // --- A. Import Sessions (Reverse Order) ---
            Cursor cSession = sourceDb.rawQuery(
                    "SELECT TIMESTAMP, WAKEUP_TIME, IS_AWAKE, TOTAL_DURATION, " +
                            "DEEP_SLEEP_DURATION, LIGHT_SLEEP_DURATION, REM_SLEEP_DURATION, AWAKE_DURATION " +
                            "FROM XIAOMI_SLEEP_TIME_SAMPLE ORDER BY TIMESTAMP DESC", null);

            while (cSession.moveToNext()) {
                boolean success = destDb.addSleepSession(
                        cSession.getLong(0)/1000, // TIMESTAMP
                        cSession.getLong(1)/1000, // WAKEUP_TIME
                        cSession.getInt(2),  // IS_AWAKE
                        cSession.getInt(3),  // TOTAL_DURATION
                        cSession.getInt(4),  // DEEP
                        cSession.getInt(5),  // LIGHT
                        cSession.getInt(6),  // REM
                        cSession.getInt(7)   // AWAKE_DUR
                );
                if (!success) break; // Stop if duplicate found
            }
            cSession.close();

            // --- B. Import Stages (Reverse Order) ---
            Cursor cStage = sourceDb.rawQuery(
                    "SELECT TIMESTAMP, STAGE FROM XIAOMI_SLEEP_STAGE_SAMPLE ORDER BY TIMESTAMP DESC", null);

            int actioncount_progress = 0;

            while (cStage.moveToNext()) {
                int stage = cStage.getInt(1);
                int stage_adapted = stage == 3 ? 2 : stage == 2 ? 3 : stage;

                boolean success = destDb.addStage(
                        cStage.getLong(0)/1000,
                        stage_adapted
                );
                if (!success) break; // Stop if duplicate found
                if(progressReport != null)
                    progressReport.setProgress((int) ((100.0*actioncount_progress++)/cStage.getCount()));
            }
            cStage.close();

            // --- C. Import HR, Stress, SpO2 (Reverse Order) ---
            Cursor cActivity = sourceDb.rawQuery(
                    "SELECT TIMESTAMP, HEART_RATE, STRESS, SPO2 FROM XIAOMI_ACTIVITY_SAMPLE ORDER BY TIMESTAMP DESC", null);

            while (cActivity.moveToNext()) {
                long ts = cActivity.getLong(0);
                int hr = cActivity.getInt(1);
                int stress = cActivity.getInt(2);
                int spo2 = cActivity.getInt(3);

                boolean stop = false;

                if (hr > 0 && hr < 255) {
                    if (!destDb.addHeartRate(ts, hr)) stop = true;
                }
                // Check stop condition after each potential insert to save time
                if (stop) break;

                if (stress > 0) {
                    if (!destDb.addStress(ts, stress)) stop = true;
                }
                if (stop) break;

                if (spo2 > 0) {
                    if (!destDb.addSpO2(ts, spo2)) stop = true;
                }
                if (stop) break;
            }
            cActivity.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sourceDb.close();
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