package com.example.bruxismdetector;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.Date;

public class SleepAsAndroidExtractor {

    public static class Record {
        public static final String AUTHORITY = "com.urbandroid.sleep.history";
        public static final String RECORDS_TABLE = "records";

        public static class Records implements BaseColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + RECORDS_TABLE);
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.urbandroid.sleep.history";

            public static final String RECORD_ID = "_id";
            public static final String START_TIME = "startTime";
            public static final String LATEST_TO_TIME = "latestToTime";
            public static final String TO_TIME = "toTime";
            public static final String FRAMERATE = "framerate";
            public static final String RATING = "rating";
            public static final String COMMENT = "comment";
            public static final String RECORD_DATA = "recordData";
            public static final String TIMEZONE = "timezone";
            public static final String LEN_ADJUST = "lenAdjust";
            public static final String QUALITY = "quality";
            public static final String SNORE = "snore";
            public static final String CYCLES = "cycles";
            public static final String EVENT_LABELS = "eventLabels";
            public static final String EVENTS = "events";
            public static final String RECORD_FULL_DATA = "recordFullData";
            public static final String RECORD_NOISE_DATA = "recordNoiseData";
            public static final String NOISE_LEVEL = "noiseLevel";
            public static final String FINISHED = "finished";
            public static final String GEO = "geo";
            public static final String LENGTH = "length";
        }
    }

    private static final int REQUEST_SLEEP_PERMISSION = 1001;


    public static void extract(Context ct, Activity a){

        if (ContextCompat.checkSelfPermission(ct, "com.urbandroid.sleep.READ")
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    a,
                    new String[]{"com.urbandroid.sleep.READ"},
                    REQUEST_SLEEP_PERMISSION
            );
            return;
        } else {
            // Permission already granted
            Log.d("SleepPermission", "Permission already granted");
        }

        // Query parameters
        String[] projection = new String[] {
                Record.Records.START_TIME,
                Record.Records.TO_TIME,
                Record.Records.RATING,
                Record.Records.RECORD_DATA,
                Record.Records.RECORD_FULL_DATA
        };

// Define cutoff timestamp (e.g., 1 year ago)
        long yearAgo = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000);

// Selection
        String selection = Record.Records.START_TIME + " > ?";
        String[] selectionArgs = new String[] { String.valueOf(yearAgo) };

// Sort order
        String sortOrder = Record.Records.START_TIME + " ASC";

// Execute query
        Cursor cursor = ct.getContentResolver().query(
                Record.Records.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                sortOrder
        );

// Read results
        if (cursor != null) {
            while (cursor.moveToNext()) {
                long startTime = cursor.getLong(cursor.getColumnIndexOrThrow(Record.Records.START_TIME));
                long endTime = cursor.getLong(cursor.getColumnIndexOrThrow(Record.Records.TO_TIME));
                int rating = cursor.getInt(cursor.getColumnIndexOrThrow(Record.Records.RATING));
                byte[] fullData = cursor.getBlob(cursor.getColumnIndexOrThrow(Record.Records.RECORD_DATA));
                String full = cursor.getString(cursor.getColumnIndexOrThrow(Record.Records.RECORD_FULL_DATA));

                int columnIndex = cursor.getColumnIndexOrThrow(Record.Records.RECORD_FULL_DATA);
                int type = cursor.getType(columnIndex);

                switch (type) {
                    case Cursor.FIELD_TYPE_NULL:
                        Log.d("DATA_TYPE", "NULL");
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        Log.d("DATA_TYPE", "INTEGER");
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        Log.d("DATA_TYPE", "FLOAT");
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        Log.d("DATA_TYPE", "STRING");
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        Log.d("DATA_TYPE", "BLOB");
                        break;
                    default:
                        Log.d("DATA_TYPE", "UNKNOWN");
                        break;
                }


                String[] columnNames = cursor.getColumnNames();
                Log.d("SleepRecord Columns", Arrays.toString(columnNames));

                Log.d("SleepRecord", "Start: " + new Date(startTime) + ", End: " + new Date(endTime) + ", Rating: " + rating + ", Full: " + Arrays.toString(fullData) + ", Full: " + full);
            }
            cursor.close();
        }

    }


}
