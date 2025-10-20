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


    public static class CorrelationSignificance {

        public static double correlationPValue(double r, int n) {
            double t = r * Math.sqrt((n - 2) / (1 - r * r));
            double p = 2 * (1 - studentTCDF(Math.abs(t), n - 2));
            return p;
        }

        // CDF della distribuzione t (approssimata)
        private static double studentTCDF(double t, int v) {
            double x = v / (v + t * t);
            double a = v / 2.0;
            double b = 0.5;
            double betacdf = regularizedIncompleteBeta(x, a, b);
            return 1 - 0.5 * betacdf;
        }

        // Funzione Beta incompleta regolarizzata (approssimazione)
        private static double regularizedIncompleteBeta(double x, double a, double b) {
            double bt = Math.exp(logGamma(a + b) - logGamma(a) - logGamma(b)
                    + a * Math.log(x) + b * Math.log(1.0 - x));

            boolean symm = x < (a + 1.0) / (a + b + 2.0);
            double result = symm
                    ? bt * betacf(x, a, b) / a
                    : 1.0 - bt * betacf(1 - x, b, a) / b;
            return result;
        }

        // Funzione Beta continua frazionaria
        private static double betacf(double x, double a, double b) {
            int MAXITER = 200;
            double EPS = 3.0e-7;
            double am = 1.0;
            double bm = 1.0;
            double az = 1.0;
            double qab = a + b;
            double qap = a + 1.0;
            double qam = a - 1.0;
            double bz = 1.0 - qab * x / qap;

            for (int m = 1; m <= MAXITER; m++) {
                int m2 = 2 * m;
                double d = m * (b - m) * x / ((qam + m2) * (a + m2));
                double ap = az + d * am;
                double bp = bz + d * bm;
                d = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
                double app = ap + d * az;
                double bpp = bp + d * bz;
                double aold = az;
                am = ap / bpp;
                bm = bp / bpp;
                az = app / bpp;
                bz = 1.0;
                if (Math.abs(az - aold) < (EPS * Math.abs(az))) return az;
            }
            return az;
        }

        // Logaritmo della funzione Gamma (algoritmo di Lanczos)
        private static double logGamma(double x) {
            double[] cof = {
                    76.18009172947146, -86.50532032941677,
                    24.01409824083091, -1.231739572450155,
                    0.1208650973866179e-2, -0.5395239384953e-5
            };
            double y = x;
            double tmp = x + 5.5;
            tmp -= (x + 0.5) * Math.log(tmp);
            double ser = 1.000000000190015;
            for (int j = 0; j < 6; j++) ser += cof[j] / ++y;
            return -tmp + Math.log(2.5066282746310005 * ser / x);
        }


    }


}
