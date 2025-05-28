package com.example.bruxismdetector.bruxism_grapher2;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class CorrelationsCalculator {


    public CorrelationsCalculator(String summary_complete_file_path){
        readSummary(summary_complete_file_path);
    }

    static final byte PositiveCorr = 1, NegativeCorr = 2, NeutralCorr = 0;

    static ArrayList<String> positiveIncreased = new ArrayList<>(Arrays.asList(
            "Stopped after beep %",
            "Average clenching event pause (minutes)",
            "Stopped after beep"
    ));
    static ArrayList<String> negativeIncreased = new ArrayList<>(Arrays.asList(
            "Total clench time (seconds)",
            "Active time (permille)",
            "Clenching Rate (per hour)",
            "Avg beeps per event",
            "Average clenching duration (seconds)",
            "Jaw Events",
            "Beep Count",
            "Alarm Triggers",
            "Alarm %"
    ));
    static byte isGoingToBetter(double correlation, String tablelabel) throws IndexOutOfBoundsException {


        boolean cc1 = (positiveIncreased.contains(tablelabel));
        boolean cc2 = (negativeIncreased.contains(tablelabel));

        boolean c1 = (correlation>0);
        boolean c2 = (correlation<0);

        boolean b1 = c1 && cc1;
        boolean b2 = c2 && cc2;

        byte res = !(cc1||cc2) ? NeutralCorr : (b1 || b2 ? PositiveCorr : NegativeCorr);
        String[] debugres = {"Neutral","Positive","Negative"};

        Log.i("Filter", "Label: " + tablelabel + " Corr: " + correlation + " c1: "+c1+" c2: "+c2 + " cc1: " + cc1 + " cc2: " + cc2 + " b1: " + b1 + " b2: " + b2 + " Result: " + debugres[res]);

        return res;

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
    ArrayList<String> dateLabels = new ArrayList<>();
    int infoindex = -1;
    void readSummary(String filepath) {

        ArrayList<String> titles = new ArrayList<>();

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
                String[] base = new String[infoindex+1];
                Arrays.fill(base, "");
                String[] splitline = line.split(";");
                splitline[1]=String.valueOf(Integer.parseInt(splitline[1].split(":")[0])+(Integer.parseInt(splitline[1].split(":")[0])/60.0));
                for(int i = 0; i < splitline.length; i++)
                    base[i] = splitline[i];

                summaryTuples.add(base);
                dateLabels.add(base[0]);

                if(!base[infoindex].isEmpty())
                    addFilter(base[infoindex].split(","));
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + filepath);
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        filterNames.sort(Comparator.naturalOrder());

    }

    int[] filterhitcount = null;
    ArrayList<String> statNames;
    public ArrayList<String> getFilterNames(){return filterNames;}

    // You must call makeCorrelationMatrix to populate this
    public ArrayList<String> getStatNames(){return statNames;}

    public int[] getFilterhitcount(){return filterhitcount;}

    // The resulting matrix will have filterNames.size() rows and statNames.size() columns
    double[][] makeCorrelationMatrix(){
        // We will create a matrix, where
        //  filter1-contained in tuple1?, filter1-contained in tuple2?, ecc
        //  filter2-contained in tuple1?, filter2-contained in tuple2?, ecc

        // total length - 3 : (skip date, mood, info)
        int effectivedatalength = summaryTuples.get(0).length-3;
        int startcolumn = 1;

        double[][] filterstats = new double[filterNames.size()][summaryTuples.size()];
        double[][] entries = new double[effectivedatalength][summaryTuples.size()];

        filterhitcount = new int[filterNames.size()];

        statNames = new ArrayList<>();

        for(int chartelement = 0; chartelement < effectivedatalength; chartelement++) {
            statNames.add(summaryTitles[startcolumn+chartelement]);

            for (int tuple = 0; tuple < summaryTuples.size(); tuple++) {
                entries[chartelement][tuple] = Double.parseDouble(summaryTuples.get(tuple)[chartelement+startcolumn].replace(",","."));
            }
        }

        for(int filter = 0; filter < filterNames.size(); filter++){
            for (int tuple = 0; tuple < summaryTuples.size(); tuple++) {
                boolean hit = summaryTuples.get(tuple)[infoindex].contains(filterNames.get(filter));
                filterstats[filter][tuple] = hit ? 1.0 : 0.0;
                if(hit)
                    filterhitcount[filter]++;
            }
        }

        double[][] correlations = new double[filterNames.size()][effectivedatalength];

        for(int filter = 0; filter < filterNames.size(); filter++) {
            for (int chartelement = 0; chartelement < effectivedatalength; chartelement++) {
                correlations[filter][chartelement] = ((int)(Correlations.pearsonCorrelation(entries[chartelement], filterstats[filter])*100.0))/100.0;
            }
        }

        return correlations;
    }

}
