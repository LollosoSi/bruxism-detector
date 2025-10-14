package com.example.bruxismdetector.bruxism_grapher2;

import android.util.Pair;

import java.util.ArrayList;
import java.util.Comparator;

public class Correlations {

    public static class CorrelationPair{
        public double correlation;
        public int delay;

        public CorrelationPair (double correlation, int delay){
            this.correlation = correlation;
            this.delay = delay;
        }
    }
    public static double pearsonCorrelation(double[] x, double[] y) {
        return pearsonCorrelation(x,y,0);
    }

        public static double pearsonCorrelation(double[] x, double[] y, int start_x) {
        int n = x.length;
        if (n != y.length || n == 0) return Double.NaN;

        double sumX = 0, sumY = 0, sumXY = 0;
        double sumX2 = 0, sumY2 = 0;

        for (int i = 0; i < n-start_x; i++) {
            sumX += x[i+start_x];
            sumY += y[i];
            sumXY += x[i+start_x] * y[i];
            sumX2 += x[i+start_x] * x[i+start_x];
            sumY2 += y[i] * y[i];
        }

        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));

        return denominator == 0 ? Double.NaN : numerator / denominator;
    }


    // Notice! The delay is calculated from samples! (i * sample time) is the delay in ms
    // This is sorted by highest values
    public static ArrayList<CorrelationPair> calculateDelayedCorrelations(double[] fixed, double[] moving, int startdelay, int enddelay, boolean resizewindowautomatically){
        ArrayList<CorrelationPair> results = new ArrayList<>();

        if(!resizewindowautomatically&&(startdelay>fixed.length || enddelay> fixed.length)){

            System.out.println("Cannot calculate correlations with these delay values");
            return null;
        }else if(startdelay>fixed.length || enddelay> fixed.length){
            startdelay = 0;
            enddelay = fixed.length-1;
        }

        for(int i = startdelay; i<enddelay; i++){
            double result = pearsonCorrelation(fixed,moving,i);
            results.add(new CorrelationPair(((int)(result*1000.0))/1000.0,i));
        }

        results.sort(new Comparator<CorrelationPair>() {
            @Override
            public int compare(CorrelationPair correlationPair, CorrelationPair t1) {
                return Double.compare(t1.correlation,correlationPair.correlation);
            }
        });

        return results;

    }



}
