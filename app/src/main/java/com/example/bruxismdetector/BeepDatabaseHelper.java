package com.example.bruxismdetector;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;

import java.io.File;
import java.util.Calendar;
import java.util.Random;

public class BeepDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "beeps.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_BEEPS = "beeps";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TIMESTAMP_EVENT = "timestamp_event";
    public static final String COLUMN_TIMESTAMP_RESPONSE = "timestamp_response";
    public static final String COLUMN_BEEP = "beep";
    public static final String COLUMN_RESPONSE = "response";

    public BeepDatabaseHelper(Context context) {
        super(context, getDatabasePath(), null, DATABASE_VERSION);
    }

    private static String getDatabasePath() {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File recordingsDir = new File(documentsDir, "RECORDINGS");
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs();
        }
        return new File(recordingsDir, DATABASE_NAME).getAbsolutePath();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_BEEPS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_TIMESTAMP_EVENT + " INTEGER, " +
                COLUMN_TIMESTAMP_RESPONSE + " INTEGER DEFAULT NULL, " +
                COLUMN_BEEP + " INTEGER, " +
                COLUMN_RESPONSE + " INTEGER DEFAULT 0)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BEEPS);
        onCreate(db);
    }

    public long insertEvent(boolean beeped) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TIMESTAMP_EVENT, System.currentTimeMillis());
        values.put(COLUMN_BEEP, beeped ? 1 : 0);
        values.put(COLUMN_RESPONSE, 0); // Default: Ignored/None
        return db.insert(TABLE_BEEPS, null, values);
    }

    public void updateResponse(long id, int response) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TIMESTAMP_RESPONSE, System.currentTimeMillis());
        values.put(COLUMN_RESPONSE, response);
        db.update(TABLE_BEEPS, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public Cursor getAllBeeps() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_BEEPS, null, null, null, null, null, COLUMN_TIMESTAMP_EVENT + " ASC");
    }

    /**
     * Generates realistic sample data for the last 60 days to test charts.
     */
    public void generateSampleData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            long sixtyDaysMillis = 60L * 24 * 60 * 60 * 1000;
            long startTime = now - sixtyDaysMillis;

            Random random = new Random();

            for (int day = 0; day < 60; day++) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(startTime + (day * 24L * 60 * 60 * 1000));

                // Set to 8:00 AM of that day
                cal.set(Calendar.HOUR_OF_DAY, 8);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                long currentDayTime = cal.getTimeInMillis();
                cal.set(Calendar.HOUR_OF_DAY, 19);
                long endDayTime = cal.getTimeInMillis();

                while (currentDayTime < endDayTime) {
                    // Random interval between 30 and 120 minutes
                    currentDayTime += (30 + random.nextInt(91)) * 60 * 1000;
                    if (currentDayTime >= endDayTime) break;

                    boolean beeped = random.nextBoolean(); // 50% chance

                    int response = 0; // Unanswered
                    long responseTime = 0;

                    // Realistic response behavior: 80% response rate with beep, 40% without
                    double respondChance = beeped ? 0.8 : 0.4;
                    if (random.nextDouble() < respondChance) {
                        // Responded after 5-60 seconds
                        responseTime = currentDayTime + (5 + random.nextInt(56)) * 1000;

                        double typeRoll = random.nextDouble();
                        if (typeRoll < 0.6) response = 1; // Yes
                        else if (typeRoll < 0.9) response = 2; // No
                        else response = 3; // Ignored
                    }

                    ContentValues values = new ContentValues();
                    values.put(COLUMN_TIMESTAMP_EVENT, currentDayTime);
                    values.put(COLUMN_BEEP, beeped ? 1 : 0);
                    values.put(COLUMN_RESPONSE, response);
                    if (responseTime > 0) {
                        values.put(COLUMN_TIMESTAMP_RESPONSE, responseTime);
                    } else {
                        values.putNull(COLUMN_TIMESTAMP_RESPONSE);
                    }
                    db.insert(TABLE_BEEPS, null, values);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}