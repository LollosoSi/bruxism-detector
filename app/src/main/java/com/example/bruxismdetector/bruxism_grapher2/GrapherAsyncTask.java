package com.example.bruxismdetector.bruxism_grapher2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;

import com.example.bruxismdetector.MainActivity;
import com.example.bruxismdetector.ProgressingDialog;
import com.example.bruxismdetector.TrackFilesMerger;
import com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces.AndroidTaskRunner;
import com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces.GrapherAndroid;
import com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces.IconManagerAndroid;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.UiModeManager;

public class GrapherAsyncTask extends AsyncTask<Void, Void, Void> {
    public interface GraphTaskCallback {
        void onGraphTaskCompleted();
    }

    ProgressingDialog asyncDialog;
    String typeStatus;

    GraphTaskCallback taskcallback = null;
    public void setTaskCallback(GraphTaskCallback callback) {taskcallback=callback;}
    public GrapherAsyncTask(MainActivity context){
        ctx = context;
    }

    static String TAG = "Async";
    private MainActivity ctx;



    public boolean isDarkModeActive(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        int currentMode = uiModeManager.getNightMode();
        return currentMode == UiModeManager.MODE_NIGHT_YES;
    }

    @Override
    protected void onPreExecute() {

        ctx.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                asyncDialog = new ProgressingDialog();
                //set message of the dialog
                asyncDialog.setMessage("Creating Graphs..");

                asyncDialog.updateProgress(0);

                //show dialog
                asyncDialog.show(ctx.getSupportFragmentManager(), "ProgressingDialog");
                asyncDialog.setMessage("Creating Graphs..");
                asyncDialog.setCancelable(false);
            }
        });


        super.onPreExecute();
    }

    boolean isFinished = false;

    int count = 0;
    @Override
    protected Void doInBackground(Void... arg0) {


        new TrackFilesMerger().findAndMergeFiles();




        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File recordingsDir = new File(documentsDir, "RECORDINGS");
        File rawRecordingsDirectory = new File(recordingsDir, "RAW");
        File graphRecordingsDirectory = new File(recordingsDir, "Graphs");


        if (!recordingsDir.mkdirs()) {
            Log.e(TAG, "Failed to create RECORDINGS directory");
        }

        if (!rawRecordingsDirectory.mkdirs()) {
            //Log.e(TAG, "Failed to create RAW directory");
        }


        if (!graphRecordingsDirectory.mkdirs()) {
            //Log.e(TAG, "Failed to create Graphs directory");
        }

        ArrayList<StatData> sda = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<StatData>> futures = new ArrayList<>();

        if (recordingsDir.exists() && recordingsDir.isDirectory()) {
            File[] files = recordingsDir.listFiles();

            if (files != null && files.length > 0) {

                Arrays.sort(files, (file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified()));

                AtomicInteger processedCount = new AtomicInteger(0);

                for (File f : files) {
                    if (!f.isDirectory() && f.getName().contains(".csv")) {
                        futures.add(executor.submit(() -> {
                            int currentCount = processedCount.getAndIncrement();

                            // Update progress on UI thread
                            ctx.runOnUiThread(() -> {
                                int percent = (int) ((currentCount / (float) files.length) * 100.0f);
                                asyncDialog.updateProgress(percent);
                            });

                            try {
                                Log.i("Async", "Processing: " + f.getName());
                                boolean skipRedraw = new File(f.getParent() + "/Graphs/" + f.getName().replace(".csv", ".png")).exists() && currentCount != 0;
                                StatData sd = makeGraph(f, skipRedraw);
                                return sd;
                            } catch (Exception e) {
                                e.printStackTrace();
                                return null;
                            }
                        }));
                    }
                }

                executor.shutdown();

                for (Future<StatData> future : futures) {
                    try {
                        StatData sd = future.get();  // Wait for each result
                        if (sd != null) {
                            sda.add(sd);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (sda.size() > 2) {
                    sda.sort(Comparator.naturalOrder());

                    File summaryDir = new File(recordingsDir, "Summary");
                    summaryDir.mkdirs();

                    try (PrintWriter pw = new PrintWriter(new File(summaryDir, "Summary.csv"))) {
                        pw.println(sda.get(0).produce_csv_header());
                        for (StatData sd : sda) {
                            pw.println(sd.produce_csv_line());
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }





        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        //hide the dialog
        ctx.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                asyncDialog.dismiss();
            }
        });
        isFinished = true;
        super.onPostExecute(result);

        if(taskcallback!=null)
            taskcallback.onGraphTaskCompleted();
    }

    public boolean taskFinished(){return isFinished;}


    public File getMostRecentFile() {
        // Get the directory path
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File recordingsDir = new File(documentsDir, "RECORDINGS");

        // Check if the directory exists
        if (recordingsDir.exists() && recordingsDir.isDirectory()) {
            // Get all files in the directory
            File[] files = recordingsDir.listFiles();

            if (files != null && files.length > 0) {
                // Sort the files by last modified timestamp in descending order
                Arrays.sort(files, new Comparator<File>() {
                    @Override
                    public int compare(File file1, File file2) {
                        return Long.compare(file2.lastModified(), file1.lastModified());
                    }
                });

                // Return the most recent file
                int i = 0;
                while(files[i].isDirectory()){i++;}
                return files[i];
            }
        }
        return null; // Return null if no files found or directory doesn't exist
    }


    StatData makeGraph(File file, boolean onlyStats){

        // We could use the display size if we wanted to, but the image doesn't scale properly and should be zoomed
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ctx.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;


        String path = file.getPath();
        File rawfile = new File(String.valueOf(new File(file.getParent() + "/RAW/" + file.getName().replace(".csv", "_RAW.csv") )));

        Grapher<Bitmap, Color, Typeface> gg = new Grapher<>(FileEventReader.readCSV(file.getAbsolutePath()), file.getName(), 1280,720);

        gg.setSleepData(FileSleepReader.readCSV(file.getParent()+"/Sleep/" +file.getName().replace(".csv", "")+"/"+ file.getName().replace(".csv", "_sleepdata.csv")));


        Log.i(TAG, "Path: " + path + " raw: " + rawfile.getAbsolutePath());
        if(!onlyStats) {
            if(rawfile.exists()){
                ArrayList<RawEvent> rawevents = FileRawEventReader.readCSV(rawfile.getAbsolutePath());
                Log.i(TAG, "Rawevents size " + rawevents.size());
                gg.addRawData(rawevents);
            }
            gg.setPlatformSpecificAbstractions(new GrapherAndroid(gg.graph_width, gg.graph_height), new IconManagerAndroid(ctx), new AndroidTaskRunner());
            gg.writeImage(gg.generateGraph(isDarkModeActive(ctx)), file.getParent() + "/Graphs/" + file.getName());
        }

        return gg.getStats();
    }

}
