package com.example.bruxismdetector.mibanddbconverter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SleepDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "SleepData.db";
    private static final int DATABASE_VERSION = 1;

    // Table Names
    public static final String TABLE_SESSIONS = "sleep_sessions";
    public static final String TABLE_STAGES = "sleep_stages";
    public static final String TABLE_HR = "heart_rate";
    public static final String TABLE_STRESS = "stress";
    public static final String TABLE_SPO2 = "spo2";

    // Common Column
    public static final String COL_TIMESTAMP = "timestamp";

    // Session Columns
    public static final String COL_WAKEUP_TIME = "wakeup_time";
    public static final String COL_IS_AWAKE = "is_awake";
    public static final String COL_TOTAL_DUR = "total_duration";
    public static final String COL_DEEP_DUR = "deep_sleep_duration";
    public static final String COL_LIGHT_DUR = "light_sleep_duration";
    public static final String COL_REM_DUR = "rem_sleep_duration";
    public static final String COL_AWAKE_DUR = "awake_duration";

    // Value Columns
    public static final String COL_STAGE = "stage";
    public static final String COL_VALUE = "value"; // Generic for HR, Stress, SpO2

    public SleepDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Sessions Table
        db.execSQL("CREATE TABLE " + TABLE_SESSIONS + " (" +
                COL_TIMESTAMP + " INTEGER PRIMARY KEY, " +
                COL_WAKEUP_TIME + " INTEGER, " +
                COL_IS_AWAKE + " INTEGER, " +
                COL_TOTAL_DUR + " INTEGER, " +
                COL_DEEP_DUR + " INTEGER, " +
                COL_LIGHT_DUR + " INTEGER, " +
                COL_REM_DUR + " INTEGER, " +
                COL_AWAKE_DUR + " INTEGER)");

        // Time Series Tables
        db.execSQL("CREATE TABLE " + TABLE_STAGES + " (" + COL_TIMESTAMP + " INTEGER PRIMARY KEY, " + COL_STAGE + " INTEGER)");
        db.execSQL("CREATE TABLE " + TABLE_HR + " (" + COL_TIMESTAMP + " INTEGER PRIMARY KEY, " + COL_VALUE + " INTEGER)");
        db.execSQL("CREATE TABLE " + TABLE_STRESS + " (" + COL_TIMESTAMP + " INTEGER PRIMARY KEY, " + COL_VALUE + " INTEGER)");
        db.execSQL("CREATE TABLE " + TABLE_SPO2 + " (" + COL_TIMESTAMP + " INTEGER PRIMARY KEY, " + COL_VALUE + " INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Removed DROP TABLE to preserve data
    }

    // --- Insertion Methods ---

    /**
     * Inserts a sleep session. Returns false if the timestamp already exists.
     */
    public boolean addSleepSession(long timestamp, long wakeupTime, int isAwake, int totalDur,
                                   int deepDur, int lightDur, int remDur, int awakeDur) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TIMESTAMP, timestamp);
        values.put(COL_WAKEUP_TIME, wakeupTime);
        values.put(COL_IS_AWAKE, isAwake);
        values.put(COL_TOTAL_DUR, totalDur);
        values.put(COL_DEEP_DUR, deepDur);
        values.put(COL_LIGHT_DUR, lightDur);
        values.put(COL_REM_DUR, remDur);
        values.put(COL_AWAKE_DUR, awakeDur);

        // Use CONFLICT_IGNORE: returns -1 if row already exists (duplicate)
        long result = db.insertWithOnConflict(TABLE_SESSIONS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        return result != -1;
    }

    public boolean addStage(long timestamp, int stage) {
        return insertInt(TABLE_STAGES, COL_STAGE, timestamp, stage);
    }

    public boolean addHeartRate(long timestamp, int hr) {
        return insertInt(TABLE_HR, COL_VALUE, timestamp, hr);
    }

    public boolean addStress(long timestamp, int stress) {
        return insertInt(TABLE_STRESS, COL_VALUE, timestamp, stress);
    }

    public boolean addSpO2(long timestamp, int spo2) {
        return insertInt(TABLE_SPO2, COL_VALUE, timestamp, spo2);
    }

    private boolean insertInt(String table, String valCol, long timestamp, int value) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TIMESTAMP, timestamp);
        values.put(valCol, value);

        // Use CONFLICT_IGNORE: returns -1 if row already exists (duplicate)
        long result = db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        return result != -1;
    }

    // --- Export Method ---
    public void exportDatabase(Context context) {
        try {
            File currentDB = context.getDatabasePath(DATABASE_NAME);
            File backupDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "RECORDINGS/Sleep/SQL");

            if (!backupDir.exists()) {
                if (!backupDir.mkdirs()) {
                    Log.e("SleepDB", "Failed to create backup directory");
                    return;
                }
            }

            File backupDB = new File(backupDir, DATABASE_NAME);

            if (currentDB.exists()) {
                try (FileChannel src = new FileInputStream(currentDB).getChannel();
                     FileChannel dst = new FileOutputStream(backupDB).getChannel()) {
                    dst.transferFrom(src, 0, src.size());
                    Log.i("SleepDB", "Database exported to " + backupDB.getAbsolutePath());
                    // Delete the original database file
                    // currentDB.delete();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Export CSV Logic ---

    public void exportDataToCsv(Context context, ProgressReport progressReport) {
        SQLiteDatabase db = this.getReadableDatabase();
        File baseDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "RECORDINGS/Sleep");

        // 1. Get all sessions
        Cursor c = db.query(TABLE_SESSIONS, null, null, null, null, null, COL_TIMESTAMP + " DESC");

        int actioncount_progress = 0;

        while (c.moveToNext()) {
            long startSec = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
            long endSec = c.getLong(c.getColumnIndexOrThrow(COL_WAKEUP_TIME));

            // Generate Folder YYYY-MM-dd based on End Time (Seconds)
            LocalDate date = Instant.ofEpochSecond(endSec).atZone(ZoneId.systemDefault()).toLocalDate();
            File sessionDir = new File(baseDir, date.toString());
            if (!sessionDir.exists()) sessionDir.mkdirs();

            // 2. Export Sleep Data (Use strict session times)
            // If there is an error or it already exists, we're done with exporting
            boolean success = exportSleepSessionCsv(db, c, sessionDir, date.toString(), startSec, endSec);
            if (!success) {
                Log.i("SleepDatabaseHelper", "Exporting was stopped at timestamp " + startSec);
                break;
            }

            // 3. Export Time Series (Use buffer to catch data before sleep start)
            // Subtract 15 minutes (900 seconds) to catch pre-sleep data
            long bufferSeconds = 15 * 60;
            long extendedStart = startSec - bufferSeconds;

            exportTimeSeriesCsv(db, TABLE_HR, "hr", sessionDir, date.toString(), extendedStart, endSec);
            exportTimeSeriesCsv(db, TABLE_SPO2, "spo2", sessionDir, date.toString(), extendedStart, endSec);
            exportTimeSeriesCsv(db, TABLE_STRESS, "stress", sessionDir, date.toString(), extendedStart, endSec);

            if(progressReport != null)
                progressReport.setProgress((int) ((100.0*actioncount_progress++)/c.getCount()));
        }
        c.close();
    }

    private boolean exportSleepSessionCsv(SQLiteDatabase db, Cursor sessionCursor, File dir, String dateStr, long startSec, long endSec) {
        File file = new File(dir, dateStr + "_sleepdata.csv");
        if (file.exists()) return false;

        // Stages are already in Seconds
        List<StageInterval> stages = getStageIntervals(db, startSec, endSec);

        int awakeCount = 0;
        for (StageInterval s : stages) if (s.state == 1) awakeCount++;

        try (FileWriter writer = new FileWriter(file)) {
            // Line 1: Header
            writer.write("Sleep Deep Duration (m);Sleep Light Duration (m);Sleep REM Duration (m);Total Duration (m);Awake Count;Sleep Awake Duration (m)\n");

            // Line 2: Summary Values
            writer.write(String.format(Locale.US, "%d;%d;%d;%d;%d;%d\n",
                    sessionCursor.getInt(sessionCursor.getColumnIndexOrThrow(COL_DEEP_DUR)),
                    sessionCursor.getInt(sessionCursor.getColumnIndexOrThrow(COL_LIGHT_DUR)),
                    sessionCursor.getInt(sessionCursor.getColumnIndexOrThrow(COL_REM_DUR)),
                    sessionCursor.getInt(sessionCursor.getColumnIndexOrThrow(COL_TOTAL_DUR)),
                    awakeCount,
                    sessionCursor.getInt(sessionCursor.getColumnIndexOrThrow(COL_AWAKE_DUR))
            ));

            // Line 3: Stage Header
            writer.write("Start (Unix in seconds or seconds from start);End (Seconds from start);State # 1=Awake, 2=Light Sleep, 3=Deep Sleep, 4=REM Sleep\n");

            // Line 4+: Data
            if (!stages.isEmpty()) {
                long refStartSec = stages.get(0).startSec;

                for (int i = 0; i < stages.size(); i++) {
                    StageInterval st = stages.get(i);
                    // First line: Absolute Start ; Relative End
                    // Other lines: Relative Start ; Relative End
                    String startVal = (i == 0) ? String.valueOf(st.startSec) : String.valueOf(st.startSec - refStartSec);
                    String endVal = String.valueOf(st.endSec - refStartSec);

                    writer.write(startVal + ";" + endVal + ";" + st.state + "\n");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void exportTimeSeriesCsv(SQLiteDatabase db, String tableName, String suffix, File dir, String dateStr, long startSec, long endSec) {
        File file = new File(dir, dateStr + "_" + suffix + ".csv");
        if (file.exists()) return;

        // Query data in range
        String query = "SELECT " + COL_TIMESTAMP + ", " + COL_VALUE + " FROM " + tableName +
                " WHERE " + COL_TIMESTAMP + " >= ? AND " + COL_TIMESTAMP + " <= ? ORDER BY " + COL_TIMESTAMP + " ASC";
        Cursor c = db.rawQuery(query, new String[]{String.valueOf(startSec), String.valueOf(endSec)});

        if (c.getCount() == 0) {
            c.close();
            return;
        }

        try (FileWriter writer = new FileWriter(file)) {
            boolean isFirst = true;
            long firstSecRecorded = 0;

            while (c.moveToNext()) {
                long currentSec = c.getLong(0); // Already Seconds
                int val = c.getInt(1);

                if (isFirst) {
                    firstSecRecorded = currentSec;
                    // Line 1: Absolute Sec ; Value
                    writer.write(firstSecRecorded + ";" + val + "\n");
                    isFirst = false;
                } else {
                    // Line N: Relative Sec ; Value
                    writer.write((currentSec - firstSecRecorded) + ";" + val + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            c.close();
        }
    }

    private List<StageInterval> getStageIntervals(SQLiteDatabase db, long startSec, long endSec) {
        List<StageInterval> intervals = new ArrayList<>();
        String query = "SELECT " + COL_TIMESTAMP + ", " + COL_STAGE + " FROM " + TABLE_STAGES +
                " WHERE " + COL_TIMESTAMP + " >= ? AND " + COL_TIMESTAMP + " <= ? ORDER BY " + COL_TIMESTAMP + " ASC";
        Cursor c = db.rawQuery(query, new String[]{String.valueOf(startSec), String.valueOf(endSec)});

        long prevTimeSec = -1;
        int prevState = -1;

        while (c.moveToNext()) {
            long currTimeSec = c.getLong(0); // Already Seconds
            int currState = c.getInt(1);

            if (prevTimeSec != -1) {
                // Add interval from prev point to current point
                intervals.add(new StageInterval(prevTimeSec, currTimeSec, prevState));
            }
            prevTimeSec = currTimeSec;
            prevState = currState;
        }
        c.close();

        // Close the last interval until Wakeup Time
        if (prevTimeSec != -1 && prevTimeSec < endSec) {
            intervals.add(new StageInterval(prevTimeSec, endSec, prevState));
        }
        return intervals;
    }

    private static class StageInterval {
        long startSec, endSec;
        int state;
        StageInterval(long s, long e, int st) { startSec = s; endSec = e; state = st; }
    }
}