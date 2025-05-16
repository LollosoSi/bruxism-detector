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
            Log.i("FitnessDbExtractor", "No entries found");
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
            e.printStackTrace();
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

        // Now open it safely
        SQLiteDatabase db = SQLiteDatabase.openDatabase(internalCopy.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

        Log.i("DBParser", "Parsing objects");

        Map<LocalDate, List<JSONObject>> groupedSegments = new HashMap<>();

        Cursor cursor = db.query(false, "sleep_segment", new String[]{"value"}, null, null, null, null, null, null);
        while (cursor.moveToNext()) {
            String jsonString = cursor.getString(0);
            try {
                JSONObject dataJson = new JSONObject(jsonString);
                long earliestTime = Math.min(
                        dataJson.optLong("bedtime", Long.MAX_VALUE),
                        Math.min(
                                dataJson.optLong("device_bedtime", Long.MAX_VALUE),
                                Math.min(
                                        dataJson.optLong("device_wake_up_time", Long.MAX_VALUE),
                                        Math.min(
                                                dataJson.optLong("wake_up_time", Long.MAX_VALUE),
                                                dataJson.optLong("protoTime", Long.MAX_VALUE)
                                        )
                                )
                        )
                );

                if (earliestTime != Long.MAX_VALUE) {
                    LocalDate utcDate = Instant.ofEpochSecond(earliestTime).atZone(ZoneOffset.UTC).toLocalDate();
                    groupedSegments.computeIfAbsent(utcDate, k -> new ArrayList<>()).add(dataJson);
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        cursor.close();

        Log.i("DBParser", "Parsing sessions");

        int cur = 0, max = groupedSegments.entrySet().size();
        for (Map.Entry<LocalDate, List<JSONObject>> entry : groupedSegments.entrySet()) {

            pr.setProgress((int) (((float)cur++/(float)max)*100));


            LocalDate sessionDate = entry.getKey();
            List<JSONObject> segments = entry.getValue();

            // Sort segments by earliest_time
            segments.sort(Comparator.comparingLong(s -> {
                return Math.min(
                        s.optLong("bedtime", Long.MAX_VALUE),
                        s.optLong("device_bedtime", Long.MAX_VALUE)
                );
            }));

            // Initialize accumulators
            double sleepDeep = 0, sleepLight = 0, sleepRem = 0, totalDuration = 0, awakeDuration = 0;
            int awakeCount = 0;
            List<Double> avgHrValues = new ArrayList<>();
            List<Double> breathQualityValues = new ArrayList<>();
            List<SleepStage> sleepStages = new ArrayList<>();

            for (JSONObject segment : segments) {
                sleepDeep += segment.optDouble("sleep_deep_duration", 0);
                sleepLight += segment.optDouble("sleep_light_duration", 0);
                sleepRem += segment.optDouble("sleep_rem_duration", 0);
                totalDuration += segment.optDouble("duration", 0);
                awakeCount += segment.optInt("awake_count", 0) + 1;
                awakeDuration += segment.optDouble("sleep_awake_duration", 0);

                if (segment.has("avg_hr")) {
                    avgHrValues.add(segment.optDouble("avg_hr"));
                }
                if (segment.has("breath_quality")) {
                    breathQualityValues.add(segment.optDouble("breath_quality"));
                }

                JSONArray items = segment.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        long start = item.optLong("start_time", -1);
                        long end = item.optLong("end_time", -1);
                        int state = item.optInt("state", 0);
                        if (start > 0 && end > 0) {
                            sleepStages.add(new SleepStage(start, end, state));
                        }
                    }
                }
            }

            // Add awake segments between parts
            for (int i = 1; i < segments.size(); i++) {
                JSONObject prev = segments.get(i - 1);
                JSONObject curr = segments.get(i);

                long prevEnd = Math.max(
                        prev.optLong("wake_up_time", 0),
                        prev.optLong("device_wake_up_time", 0)
                );
                long currStart = Math.min(
                        curr.optLong("bedtime", Long.MAX_VALUE),
                        curr.optLong("device_bedtime", Long.MAX_VALUE)
                );
                if (currStart > prevEnd) {
                    sleepStages.add(new SleepStage(prevEnd, currStart, 1));
                }
            }

            sleepStages.removeIf(stage -> stage.startUnix <= 0 || stage.endUnix <= 0);
            sleepStages.sort(Comparator.comparingLong(s -> s.startUnix));

            if (sleepStages.isEmpty()) continue;

            long referenceStart = sleepStages.get(0).startUnix;
            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File recordingsDir = new File(documentsDir, "RECORDINGS");


            File outputDir = new File(recordingsDir.getParent()+"/RECORDINGS/Sleep/" + sessionDate.toString());
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            File csvFile = new File(outputDir, sessionDate + "_sleepdata.csv");


                Log.i("DBParser", "Writing " + csvFile.getAbsolutePath());
                if(!csvFile.exists())
                    try (FileWriter writer = new FileWriter(csvFile)) {
                        // Write summary
                        writer.write("Sleep Deep Duration (m);Sleep Light Duration (m);Sleep REM Duration (m);Total Duration (m);Awake Count;Sleep Awake Duration (m)");
                        if (!avgHrValues.isEmpty()) writer.write(";Average Heart Rate");
                        if (!breathQualityValues.isEmpty()) writer.write(";Breath Quality");
                        writer.write("\n");

                        writer.write(String.format(Locale.US, "%.1f;%.1f;%.1f;%.1f;%d;%.1f",
                                sleepDeep, sleepLight, sleepRem, totalDuration, awakeCount, awakeDuration
                        ));

                        if (!avgHrValues.isEmpty()) {
                            double meanHr = avgHrValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                            writer.write(";" + String.format(Locale.US, "%.1f", meanHr));
                        }

                        if (!breathQualityValues.isEmpty()) {
                            double meanBq = breathQualityValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                            writer.write(";" + String.format(Locale.US, "%.1f", meanBq));
                        }
                        writer.write("\n");

                        // Write sleep stages
                        writer.write("Start (Unix in seconds or seconds from start);End (Seconds from start);State # 1=Awake, 2=Light Sleep, 3=Deep Sleep, 4=REM Sleep\n");
                        for (SleepStage stage : sleepStages) {
                            writer.write(stage.toCsvString(referenceStart) + "\n");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }


            // Determine session start and end (±1 day buffer)
            List<Long> startTimes = segments.stream().map(s ->
                    Math.min(s.optLong("bedtime", Long.MAX_VALUE), s.optLong("device_bedtime", Long.MAX_VALUE))
            ).collect(Collectors.toList());

            List<Long> endTimes = segments.stream().map(s ->
                    Math.max(s.optLong("wake_up_time", 0), s.optLong("device_wake_up_time", 0))
            ).collect(Collectors.toList());

            if (!startTimes.isEmpty() && !endTimes.isEmpty()) {
                long sessionStart = Collections.min(startTimes) - 86400;
                long sessionEnd = Collections.max(endTimes);

                exportTimeSeries(db, outputDir, sessionDate, sessionStart, sessionEnd, "hr_record", "bpm", "hr");
                exportTimeSeries(db, outputDir, sessionDate, sessionStart, sessionEnd, "stress_record", "stress", "stress");
                exportTimeSeries(db, outputDir, sessionDate, sessionStart, sessionEnd, "spo2_record", "spo2", "spo2");
            }

        }




        db.close();
    }

}
