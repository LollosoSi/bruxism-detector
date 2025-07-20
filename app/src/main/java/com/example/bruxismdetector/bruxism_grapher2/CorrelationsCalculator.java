package com.example.bruxismdetector.bruxism_grapher2;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

public class CorrelationsCalculator {


    public CorrelationsCalculator(String summary_complete_file_path){
        readSummary(summary_complete_file_path);
    }

    public static final byte PositiveCorr = 1, NegativeCorr = 2, NeutralCorr = 0;

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
    public static byte isGoingToBetter(double correlation, String tablelabel) throws IndexOutOfBoundsException {


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

    int infoindex = -1;
    String[] summaryTitles;
    ArrayList<String[]> summaryTuples;
    ArrayList<String> dateLabels;
    ArrayList<String> filterNames;
    void readSummary(String summary_complete_file_path) {

        SummaryReader.setFilepath(summary_complete_file_path);
        SummaryReader sr = SummaryReader.getInstance();

        summaryTitles = sr.getSummaryTitles();
        summaryTuples = sr.getSummaryTuplesWithNoSkipItems();
        dateLabels = sr.getDateLabelsWithNoSkipItems();
        filterNames = sr.getFilterNames();
        infoindex = sr.getInfomationIndex();
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
