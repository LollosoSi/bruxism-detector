package com.example.bruxismdetector.bruxism_grapher2;

public class Correlations {


        public static double pearsonCorrelation(double[] x, double[] y) {
        int n = x.length;
        if (n != y.length || n == 0) return Double.NaN;

        double sumX = 0, sumY = 0, sumXY = 0;
        double sumX2 = 0, sumY2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
            sumY2 += y[i] * y[i];
        }

        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));

        return denominator == 0 ? Double.NaN : numerator / denominator;
    }


}
