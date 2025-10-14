package com.example.bruxismdetector.mibanddbconverter;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;

import com.example.bruxismdetector.ProgressingDialog;
import com.example.bruxismdetector.bruxism_grapher2.SleepData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MiBandDBConverter {

    public interface ProgressReport{
        public void setProgress(int progress);
    }

    Context context;


    public static boolean tryRoot(Context ct, ProgressReport pr) {
        if(hasRootAccess()){
            try {
                if(tryfolders(ct)){
                    new MiBandDBConverter().convert(ct, null, pr);
                    return true;
                }else{return false;}
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }else{
            return false;
        }
    }

    // Check root access
    private static boolean hasRootAccess() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            DataInputStream is = new DataInputStream(process.getInputStream());

            os.writeBytes("id\n");
            os.flush();
            os.writeBytes("exit\n");
            os.flush();

            String output = new BufferedReader(new InputStreamReader(is)).readLine();
            process.waitFor();

            return output != null && output.contains("uid=0"); // root UID is 0
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean rootExists(String path) throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "ls " + path});
        int exitCode = p.waitFor();
        return (exitCode == 0);
    }


    public static boolean tryfolders(Context context) throws IOException, InterruptedException {
        File internalCopy = new File(context.getCacheDir(), "temp_db_copy.db");
        String[] candidateRoots = {
                "/data/data/",
                "/data/user/0/",
                "/data/user_de/0/",
                "/data_mirror/data_ce/null/0/"
        };

        String packageName = "com.xiaomi.wearable";

        for (String root : candidateRoots) {
            String path = root + packageName + "/databases/";
            if (rootExists(path)) { // Use root shell 'ls' command to verify
                if(extractFitnessDbToCache(context, path))
                    return true;
            }
        }
        return false;
    }
    public static boolean extractFitnessDbToCache(Context context, String basepath) {


        try {


            // Step 1: Find full path of the fitness_data file using root shell
            String[] command = {
                    "su", "-c", "find "+basepath+" -type f -name '*fitness_data*'"
            };

            Log.i("FitnessDbExtractor", "basepath: "+basepath);


            Process findProcess = Runtime.getRuntime().exec(command);

            BufferedReader stdout = new BufferedReader(new InputStreamReader(findProcess.getInputStream()));
            BufferedReader stderr = new BufferedReader(new InputStreamReader(findProcess.getErrorStream()));
            String fitnessDbPath = stdout.readLine();  // grab first match
            findProcess.waitFor();

            // Print stdout
            if (fitnessDbPath != null) {
                Log.i("FitnessDbExtractor", "Found fitness DB path: " + fitnessDbPath);
            }

            // Print any errors
            String errLine;
            while ((errLine = stderr.readLine()) != null) {
                Log.e("FitnessDbExtractor", "stderr: " + errLine);
            }

            if (fitnessDbPath == null || fitnessDbPath.trim().isEmpty()) {
                Log.e("FitnessDbExtractor", "Fitness DB not found.");
                return false;
            }

            File internalCopy = new File(context.getCacheDir(), "temp_db_copy.db");
            // Step 2: Copy the file to internal cache
            String internalPath = internalCopy.getAbsolutePath();
            String copyCmd = "cp \"" + fitnessDbPath + "\" \"" + internalPath + "\" && chmod 777 \"" + internalPath + "\"";
            Process copyProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", copyCmd});
            int result = copyProcess.waitFor();

            if (result != 0) {
                Log.e("FitnessDbExtractor", "Failed to copy file. Exit code: " + result);
                return false;
            }

            Log.i("FitnessDbExtractor", "Database copied to cache: " + internalPath);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public class SleepStage {
        public long startUnix;
        public long endUnix;
        public int state;

        public SleepStage(long startUnix, long endUnix, int state) {
            this.startUnix = startUnix;
            this.endUnix = endUnix;
            this.state = state;
        }

        public String toCsvString(long referenceStart) {
            // First row: write startUnix as is, others: relative to referenceStart
            String startStr = startUnix == referenceStart
                    ? String.valueOf(startUnix)
                    : String.valueOf(startUnix - referenceStart);
            String endStr = String.valueOf(endUnix - referenceStart);
            return startStr + ";" + endStr + ";" + state;
        }
    }

    private void exportTimeSeries(SQLiteDatabase db, File outputDir, LocalDate sessionDate,
                                  long sessionStart, long sessionEnd,
                                  String tableName, String valueKey, String fileSuffix) {

        File outFileCheck = new File(outputDir, sessionDate + "_" + fileSuffix + ".csv");

        if(outFileCheck.exists()) {
            //Log.i("FitnessDbExtractor", "File already exists " + outFileCheck.getAbsolutePath());
            return;
        }

        List<Pair<Long, Integer>> entries = new ArrayList<>();

        Cursor cursor = db.query(false, tableName, new String[]{"value"}, null, null, null, null, null, null);
        while (cursor.moveToNext()) {
            String jsonString = cursor.getString(0);
            try {
                JSONObject json = new JSONObject(jsonString);
                long unixTime = json.optLong("time", -1);
                if (unixTime <= 0) continue;

                int value = json.optInt(valueKey, -1);
                if (value != -1 && unixTime >= sessionStart && unixTime <= sessionEnd) {
                    entries.add(new Pair<>(unixTime, value));
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        cursor.close();

        if (entries.isEmpty()){
            //Log.i("FitnessDbExtractor", "No entries found for " + outFileCheck.getAbsoluteFile());
            return;
        }

        entries.sort(Comparator.comparingLong(p -> p.first));

        File outFile = new File(outputDir, sessionDate + "_" + fileSuffix + ".csv");
        try (FileWriter writer = new FileWriter(outFile)) {
            long referenceTime = entries.get(0).first;
            writer.write(referenceTime + ";" + entries.get(0).second + "\n");

            for (int i = 1; i < entries.size(); i++) {
                long offset = entries.get(i).first - referenceTime;
                writer.write(offset + ";" + entries.get(i).second + "\n");
            }
        } catch (IOException e) {
            Log.e("FitnessDbExtractor", "Problem writing file " + outFile.getAbsolutePath() + " : " + e.getLocalizedMessage());
        }
    }

    public void convert(Context context, Uri uri, ProgressReport pr) {
        this.context = context;

        pr.setProgress(0);

        File internalCopy = new File(context.getCacheDir(), "temp_db_copy.db");
        if(uri!=null) {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(internalCopy)) {

                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

            int cores = Runtime.getRuntime().availableProcessors();
            int runthreads = 4*cores; // This operation is I/O bound, we can afford more threads
            ExecutorService executor2 = Executors.newFixedThreadPool(runthreads);


        SQLiteDatabase db = SQLiteDatabase.openDatabase(internalCopy.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

            Cursor cursor = db.query("sleep_segment", new String[]{"value"}, null, null, null, null, null);
            Map<LocalDate, List<JSONObject>> groupedSegments = new HashMap<>();

            while (cursor.moveToNext()) {

                    String jsonString = cursor.getString(0);
                    try {
                        JSONObject segment = new JSONObject(jsonString);
                        long wakeupTime = Math.max(
                                segment.optLong("wake_up_time", 0),
                                Math.max(
                                        segment.optLong("device_wake_up_time", 0),
                                        segment.optLong("protoTime", 0)
                                )
                        );

                        if (wakeupTime > 0) {
                            LocalDate utcDate = Instant.ofEpochSecond(wakeupTime).atZone(ZoneOffset.UTC).toLocalDate();
                            groupedSegments.computeIfAbsent(utcDate, k -> new ArrayList<>()).add(segment);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }



            }
            cursor.close();



        File baseDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "RECORDINGS/Sleep");
        AtomicInteger count = new AtomicInteger(0);
            int maxsize = groupedSegments.size();
            for (Map.Entry<LocalDate, List<JSONObject>> entry : groupedSegments.entrySet()) {
                executor2.submit(() -> {

                    processEntry(db, count, maxsize, pr, baseDir, entry);

                });
            }

        // Shutdown and wait for all tasks to finish
        executor2.shutdown();
        try {
            executor2.awaitTermination(1, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        db.close();
        }


        public void processEntry(SQLiteDatabase db, AtomicInteger count, int maxsize, ProgressReport pr, File baseDir, Map.Entry<LocalDate, List<JSONObject>> entry){
            LocalDate date = entry.getKey();
            List<JSONObject> sessions = entry.getValue();


            pr.setProgress((int) (((double) (count.get()) / (double) maxsize) * 100.0));
            count.set(count.get() + 1);

            File dddateDir = new File(baseDir, date.toString());
            if (!dddateDir.exists()) dddateDir.mkdirs();

            String testfileName = date + "_sleepdata.csv";
            File testoutFile = new File(dddateDir, testfileName);

            // Skip entire session if output file already exists
            if (testoutFile.exists()) {
                // Optionally log or print skipping message
                 System.out.println("Skipping existing session file: " + testoutFile.getAbsolutePath());
                return;  // Skip to next session
            }

            // Sort sessions by bedtime to keep consistency
            sessions.sort(Comparator.comparingLong(s -> s.optLong("bedtime", Long.MAX_VALUE)));

            for (int i = 0; i < sessions.size(); i++) {
                JSONObject session = sessions.get(i);

                long sessionStart = Math.min(session.optLong("bedtime", Long.MAX_VALUE), session.optLong("device_bedtime", Long.MAX_VALUE));
                long sessionEnd = Math.max(session.optLong("wake_up_time", 0), session.optLong("device_wake_up_time", 0));

                File dateDir = new File(baseDir, date.toString());
                if (!dateDir.exists()) dateDir.mkdirs();


                double sleepDeep = 0, sleepLight = 0, sleepRem = 0, totalDuration = 0, awakeDuration = 0;
                int awakeCount = 0;
                List<Double> avgHrValues = new ArrayList<>();
                List<Double> breathQualityValues = new ArrayList<>();
                List<SleepStage> sleepStages = new ArrayList<>();


                List<JSONObject> segments = entry.getValue();

                // Sort segments by earliest of bedtime or device_bedtime
                segments.sort(Comparator.comparingLong(s -> {
                    return Math.min(
                            s.optLong("bedtime", Long.MAX_VALUE),
                            s.optLong("device_bedtime", Long.MAX_VALUE)
                    );
                }));

                for (int j = 1; j < segments.size(); j++) {
                    JSONObject prev = segments.get(j - 1);
                    JSONObject curr = segments.get(j);

                    long prevEnd = Math.max(
                            prev.optLong("wake_up_time", 0),
                            prev.optLong("device_wake_up_time", 0)
                    );
                    long currStart = Math.min(
                            curr.optLong("bedtime", Long.MAX_VALUE),
                            curr.optLong("device_bedtime", Long.MAX_VALUE)
                    );
                    if (currStart > prevEnd) {
                        sleepStages.add(new SleepStage(prevEnd, currStart, 1));  // 1 = Awake
                    }
                }


                sleepStages.removeIf(stage -> stage.startUnix <= 0 || stage.endUnix <= 0);
                sleepStages.sort(Comparator.comparingLong(s -> s.startUnix));

// Extract values and stages from all segments in this session
                for (JSONObject segment : segments) {
                    sleepDeep += segment.optDouble("sleep_deep_duration", 0);
                    sleepLight += segment.optDouble("sleep_light_duration", 0);
                    sleepRem += segment.optDouble("sleep_rem_duration", 0);
                    totalDuration += segment.optDouble("duration", 0);
                    awakeCount += segment.optInt("awake_count", 0);
                    awakeDuration += segment.optDouble("sleep_awake_duration", 0);

                    if (segment.has("avg_hr")) {
                        avgHrValues.add(segment.optDouble("avg_hr"));
                    }
                    if (segment.has("breath_quality")) {
                        breathQualityValues.add(segment.optDouble("breath_quality"));
                    }

                    JSONArray items = segment.optJSONArray("items");
                    if (items != null) {
                        for (int j = 0; j < items.length(); j++) {
                            JSONObject item = items.optJSONObject(j);  // <-- use j here
                            if (item == null) continue;
                            long start = item.optLong("start_time", -1);
                            long end = item.optLong("end_time", -1);
                            int state = item.optInt("state", 0);
                            if (start > 0 && end > 0) {
                                sleepStages.add(new SleepStage(start, end, state));
                            }
                        }
                    }

                }

                if (sleepStages.isEmpty())
                    continue;

                String fileName = sessions.size() == 1 ? date + "_sleepdata.csv" : "_sleepdata_" + i + ".csv";
                File outFile = new File(dateDir, fileName);

                try (FileWriter writer = new FileWriter(outFile)) {
                    // Write CSV header line 1 (summary columns)
                    writer.write("Sleep Deep Duration (m);Sleep Light Duration (m);Sleep REM Duration (m);Total Duration (m);Awake Count;Sleep Awake Duration (m)");

// Add optional columns if data exists
                    if (!avgHrValues.isEmpty()) writer.write(";Average Heart Rate");
                    if (!breathQualityValues.isEmpty()) writer.write(";Breath Quality");
                    writer.write("\n");

// Write CSV summary values or "N/A" if no data
                    writer.write(String.format(Locale.US,
                            "%.0f;%.0f;%.0f;%.0f;%d;%.0f",
                            sleepDeep, sleepLight, sleepRem, totalDuration, awakeCount, awakeDuration
                    ));

                    if (!avgHrValues.isEmpty()) {
                        double meanHr = avgHrValues.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
                        writer.write(";" + (Double.isNaN(meanHr) ? "N/A" : String.format(Locale.US, "%.1f", meanHr)));
                    }

                    if (!breathQualityValues.isEmpty()) {
                        double meanBq = breathQualityValues.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
                        writer.write(";" + (Double.isNaN(meanBq) ? "N/A" : String.format(Locale.US, "%.1f", meanBq)));
                    }
                    writer.write("\n");

// Write CSV header line 2 for sleep stages
                    writer.write("Start (Unix in seconds or seconds from start);End (Seconds from start);State # 1=Awake, 2=Light Sleep, 3=Deep Sleep, 4=REM Sleep\n");

                    long referenceStart = sleepStages.get(0).startUnix;

                    // Write all sleep stages using toCsvString(referenceStart)
                    for (SleepStage stage : sleepStages) {
                        writer.write(stage.toCsvString(referenceStart) + "\n");
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }


                // Export HR, SpO2, and stress time series
                exportTimeSeries(db, dateDir, date, sessionStart-60, sessionEnd, "hr_record", "bpm", "hr");
                exportTimeSeries(db, dateDir, date, sessionStart-60, sessionEnd, "stress_record", "stress", "stress");
                exportTimeSeries(db, dateDir, date, sessionStart-60, sessionEnd, "spo2_record", "spo2", "spo2");

            }
        }

    }
