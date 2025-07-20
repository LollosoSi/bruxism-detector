package com.example.bruxismdetector.bruxism_grapher2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class SummaryReader {


    public static String filepath = "";

    public static void setFilepath(String f){
        filepath = f;
        reset();
        getInstance();
    }
    public static void reset(){
        instance = null;
    }
    private static SummaryReader instance;
    public static SummaryReader getInstance() {
        if (instance == null) {
            instance = new SummaryReader();
            if(filepath.isEmpty()){
                throw new RuntimeException("You need to set the filepath for summary");
            }
            instance.readSummary(filepath);
        }
        return instance;
    }

    private SummaryReader() {
        // Constructor
    }

    ArrayList<String> filterNames = new ArrayList<>();

    void addFilter(String[] names){
        for(String s : names)
            addFilter(s);
    }
    void addFilter(String name){
        if(!filterNames.contains(name))
            filterNames.add(name);
    }

    String[] summaryTitles;
    ArrayList<String[]> summaryTuples = new ArrayList<>();
    ArrayList<String[]> noSkipSummaryTuples = new ArrayList<>();
    ArrayList<String> dateLabels = new ArrayList<>();
    ArrayList<String> noSkipDateLabels = new ArrayList<>();

    int global_infoindex=-1;

    public ArrayList<String> getFilterNames(){return filterNames;}
    public String[] getSummaryTitles(){return summaryTitles;}
    public ArrayList<String[]> getSummaryTuples(){return summaryTuples;}

    // Since for some calculations you only need to use items with date, just get them!
    public ArrayList<String[]> getSummaryTuplesWithNoSkipItems(){return noSkipSummaryTuples;}
    public ArrayList<String> getDateLabelsWithNoSkipItems(){return noSkipDateLabels;}

    public ArrayList<String> getDateLabels(){return dateLabels;}

    public ArrayList<Boolean> skiplist = new ArrayList<>(); // If true, the session only contains Info elements. Skip calculations on it!
    int true_skips = 0;
    public ArrayList<Boolean> getSkiplist(){return skiplist;}
    public int skiplist_true_size(){return true_skips;}

    public int getInfomationIndex(){return global_infoindex;}
    void readSummary(String filepath) {


        ArrayList<String> titles = new ArrayList<>();

        int infoindex=-1;
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    summaryTitles = line.split(";");
                    titles = new ArrayList<>(Arrays.asList(summaryTitles));
                    infoindex = titles.indexOf("Info");
                    if(infoindex==-1)
                        throw new Exception("Summary does not have Info!");
                    continue;
                }
                String[] splitline = line.split(";");
                boolean shouldskip = splitline[1].equals("0:0");
                splitline[1]=String.valueOf(Integer.parseInt(splitline[1].split(":")[0])+(Integer.parseInt(splitline[1].split(":")[0])/60.0));
                summaryTuples.add(splitline);
                dateLabels.add(splitline[0]);


                if(shouldskip)
                    true_skips++;
                else {
                    noSkipSummaryTuples.add(splitline);
                    noSkipDateLabels.add(splitline[0]);
                }
                skiplist.add(shouldskip);


                if(splitline.length > infoindex)
                    addFilter(splitline[infoindex].split(","));
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + filepath);
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        global_infoindex = infoindex;
        filterNames.sort(Comparator.naturalOrder());

    }

    ArrayList<SummaryMonth> months = new ArrayList<>();
    public ArrayList<SummaryMonth> getSummaryMonths(){return months;}
    public void populateMonthsArray(){
        months = new ArrayList<>();

        SummaryMonth previous_month = null;

        for(int i = 0; i < summaryTuples.size(); i++){
            String[] tuple = summaryTuples.get(i);
            // First, find date. Do it in SummaryEntry, I don't care
            SummaryEntry entry = new SummaryEntry(tuple);
            entry.should_skip = skiplist.get(i);

            // If available, find the SummaryMonth with same month and year, store it for the next iteration
            if(previous_month != null && previous_month.month == entry.month && previous_month.year == entry.year){

            }else {
                previous_month = (months.stream()
                        .filter(sm -> sm.month == entry.month && sm.year == entry.year)
                        .findFirst()
                        .orElse(null));

                if(previous_month==null){
                    previous_month = new SummaryMonth(entry.month, entry.year);
                    months.add(previous_month);
                }

            }

            previous_month.tuples.add(entry);

        }

    }

    public class SummaryEntry {

        public SummaryEntry(String[] t){
            tuple = t;

            String[] date = tuple[0].split("-");

            day = Integer.parseInt(date[2]);
            month = Integer.parseInt(date[1]);
            year = Integer.parseInt(date[0]);


        }
        public String[] tuple;

        public boolean should_skip = false;
        public int day = 0, month = 0, year = 0;
    }

    public class SummaryMonth {
        public int month = 0, year = 0;
        public ArrayList<SummaryEntry> tuples = new ArrayList<>();

        public SummaryMonth(int month, int year) {
            this.month = month;
            this.year = year;
        }

        public int getMonth(){return month;}
        public int getYear(){return year;}
    }

}
