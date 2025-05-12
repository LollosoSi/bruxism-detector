package com.example.bruxismdetector;

import android.icu.util.Calendar;
import android.os.Environment;
import android.util.Log;

import com.example.bruxismdetector.bruxism_grapher2.Event;
import com.example.bruxismdetector.bruxism_grapher2.FileEventReader;
import com.example.bruxismdetector.bruxism_grapher2.FileRawEventReader;
import com.example.bruxismdetector.bruxism_grapher2.RawEvent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

class SaveDetails{
    ArrayList<Event> events;
    ArrayList<RawEvent> rawevents = new ArrayList<>();
    public SaveDetails(File origin, File rawOrigin){
        this.origin = origin;
        this.rawOrigin = rawOrigin;


        events = FileEventReader.readCSV(origin.getAbsolutePath());

        // Date is inside Start, notes
        // there is a SYNC element with sync_time,millis_sync_track,millis_sync_raw
        // sync_time = date with the time of the sync
        for(Event ev : events){
            if(ev.type.equals("Start")) {
                // It is formatted YYYY-MM-dd
                String[] stringDate = ev.notes.split(" ")[3].split("-");
                String[] stringTime = ev.time.split(":");
                date = Calendar.getInstance();
                date.set(Calendar.YEAR, Integer.parseInt(stringDate[0]));
                date.set(Calendar.MONTH, Integer.parseInt(stringDate[1]));
                date.set(Calendar.DAY_OF_MONTH, Integer.parseInt(stringDate[2]));
                date.set(Calendar.HOUR_OF_DAY, Integer.parseInt(stringTime[0]));
                date.set(Calendar.MINUTE, Integer.parseInt(stringTime[1]));

                start_millis = ev.millis;

            }

            if(ev.type.equals("Sync")){

                sync_time = Calendar.getInstance();
                sync_time.setTimeInMillis(date.getTimeInMillis()+(ev.millis-start_millis));

                millis_sync_track = ev.millis;
                millis_sync_raw = Long.parseLong(ev.notes);
                break;
            }
        }
    }

    public void readRaw(){
        if(rawevents.isEmpty())
         if(rawOrigin.exists())
              rawevents = FileRawEventReader.readCSV(rawOrigin.getAbsolutePath());

    }

    Calendar date;
    Calendar sync_time;
    long millis_sync_track, millis_sync_raw, start_millis;

    File origin, rawOrigin;
};



public class TrackFilesMerger {

    boolean compareSaveDetails(SaveDetails sd1, SaveDetails sd2){
        try {
            // Check if sync_time1 + (millis_sync_raw2-millis_sync_raw1) produces sync_time2
            // If yes, then the latest file needs to be updated with the time difference and the RAW item too

            // First of all sd1 should always be the earlier one
            if (sd1.millis_sync_raw > sd2.millis_sync_raw) {
                SaveDetails temp = sd1;
                sd1 = sd2;
                sd2 = temp;
            }

            long timeDifference = sd2.millis_sync_raw - sd1.millis_sync_raw;
            long syncTime1 = sd1.sync_time.getTimeInMillis();
            long syncTime2 = sd2.sync_time.getTimeInMillis();

            // Comparing millis would require high accuracy, what we want to compare is sync_time1 + timeDifference equals sync_time2 with 5 minutes of tolerance
            return (Math.abs(syncTime2 - (syncTime1 + timeDifference)) < 5 * 60 * 1000);
        }catch (Exception e){return false;}
    }

    SaveDetails mergeFiles(SaveDetails master, SaveDetails delete){
        // First of all sd1 should always be the earlier one
        if(master.millis_sync_raw > delete.millis_sync_raw){
            SaveDetails temp = master;
            master = delete;
            delete = temp;
        }
        master.readRaw();
        delete.readRaw();

        long timeDifference = delete.millis_sync_raw - master.millis_sync_raw;

        try {
            String new_mergedfilename=master.origin.getAbsolutePath(),
                    new_mergedfilename_raw=master.rawOrigin.getAbsolutePath();


            PrintWriter writer = new PrintWriter(new_mergedfilename);
            writer.println(FileEventReader.getFirstLine(master.origin.getAbsolutePath()));

            PrintWriter writer_raw = new PrintWriter(new_mergedfilename_raw);
            writer_raw.println(FileEventReader.getFirstLine(master.rawOrigin.getAbsolutePath()));

            for(Event ev : master.events){
                if(ev.type.equals("End"))
                    continue;
                SessionTracker.append_csv(new String[]{String.valueOf(ev.millis), String.valueOf(ev.time), String.valueOf(ev.type), String.valueOf(ev.notes), String.valueOf(ev.duration)}, writer);
            }

            for(Event ev : delete.events){
                if(ev.type.equals("Sync") || ev.type.equals("MOOD") || ev.type.equals("Start"))
                    continue;
                SessionTracker.append_csv(new String[]{String.valueOf(ev.millis+timeDifference), String.valueOf(ev.time), String.valueOf(ev.type), String.valueOf(ev.notes), String.valueOf(ev.duration)}, writer);
            }

            for(RawEvent ev : master.rawevents){
                SessionTracker.append_csv(new String[]{String.valueOf(ev.millis), String.valueOf(ev.value), String.valueOf(ev.fvalue)}, writer_raw);
            }

            for(RawEvent ev : delete.rawevents){
                SessionTracker.append_csv(new String[]{String.valueOf(ev.millis), String.valueOf(ev.value), String.valueOf(ev.fvalue)}, writer_raw);
            }

            writer.close();
            writer_raw.close();

            // Delete "delete" file from memory
            delete.origin.delete();
            delete.rawOrigin.delete();

            return new SaveDetails(new File(new_mergedfilename),new File(new_mergedfilename_raw));

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

    }




    public void findAndMergeFiles(){
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File recordingsDir = new File(documentsDir, "RECORDINGS");
        File rawRecordingsDirectory = new File(recordingsDir, "RAW");


        // Check if the directory exists
        if (recordingsDir.exists() && recordingsDir.isDirectory()) {
            // Get all files in the directory
            File[] files = recordingsDir.listFiles();

            if (files != null && files.length > 0) {

                Arrays.sort(files, new Comparator<File>() {
                    @Override
                    public int compare(File file1, File file2) {
                        return Long.compare(file2.lastModified(), file1.lastModified());
                    }
                });
                ArrayList<SaveDetails> sdlist = new ArrayList<>();
               for(File f : files) {
                   if(f.isDirectory())
                       continue;
                   File rawfile = new File(String.valueOf(new File(f.getParent() + "/RAW/" + f.getName().replace(".csv", "_RAW.csv") )));
                   sdlist.add(new SaveDetails(f, rawfile));
               }
               if(sdlist.isEmpty())
                   return;
               // Compare the most recent file with the second-most recent file through compareSaveDetails(), if we merge it using mergeFiles, then compare the merged file returned from the function with the third-most recent file and so on
                SaveDetails comparing_now = sdlist.get(0);
                for(int i = 1; i <sdlist.size(); i++){
                    SaveDetails comparing_next = sdlist.get(i);
                    Log.i("Comparing", comparing_now.origin.getAbsolutePath() + " with " + comparing_next.origin.getAbsolutePath()+ " Result: "+compareSaveDetails(comparing_next, comparing_now));
                    if(compareSaveDetails(comparing_next, comparing_now)){
                        comparing_now = mergeFiles(comparing_next, comparing_now);
                    }else
                        comparing_now = comparing_next;
                }
            }
        }

    }
}
