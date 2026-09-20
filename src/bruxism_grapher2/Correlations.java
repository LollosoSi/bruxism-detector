package bruxism_grapher2;


import java.util.ArrayList;
import java.util.Comparator;

public class Correlations {

    public static class CorrelationPair {
        public double correlation; // R
        public int delay;
        public double pValue;      // Affidabilità
        public double effectSize;  // E% (R^2)

        public CorrelationPair (double correlation, int delay, double pValue, double effectSize){
            this.correlation = correlation;
            this.delay = delay;
            this.pValue = pValue;
            this.effectSize = effectSize;
        }
    }
    public static double pearsonCorrelation(double[] x, double[] y) {
        return pearsonCorrelation(x,y,0);
    }

    // Fix
    public static double pearsonCorrelation(double[] x, double[] y, int delay) {
        int n = x.length;
        if (n != y.length || n == 0 || Math.abs(delay) >= n) return Double.NaN;

        double sumX = 0, sumY = 0, sumXY = 0;
        double sumX2 = 0, sumY2 = 0;

        // Calcoliamo i campioni effettivi che si sovrappongono
        int effective_n = n - Math.abs(delay);

        // Se delay è positivo: x parte da 0, y parte dal futuro (y avviene DOPO x)
        // Se delay è negativo: x parte dal futuro, y parte da 0 (y avviene PRIMA di x)
        int startX = delay < 0 ? -delay : 0;
        int startY = delay > 0 ? delay : 0;

        for (int i = 0; i < effective_n; i++) {
            double valX = x[startX + i];
            double valY = y[startY + i];

            sumX += valX;
            sumY += valY;
            sumXY += valX * valY;
            sumX2 += valX * valX;
            sumY2 += valY * valY;
        }

        double numerator = effective_n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((effective_n * sumX2 - sumX * sumX) * (effective_n * sumY2 - sumY * sumY));

        return denominator == 0 ? Double.NaN : numerator / denominator;
    }


    // Notice! The delay is calculated from samples! (i * sample time) is the delay in ms
    // This is sorted by highest values
    public static ArrayList<CorrelationPair> calculateDelayedCorrelations(double[] fixed, double[] moving, int startdelay, int enddelay, boolean resizewindowautomatically){
        ArrayList<CorrelationPair> results = new ArrayList<>();

        if(!resizewindowautomatically && enddelay > fixed.length){
            System.out.println("Cannot calculate correlations with these delay values");
            return null;
        }else if(enddelay > fixed.length){
            enddelay = fixed.length-1;
        }

        int total_samples = fixed.length;

        for(int i = -enddelay; i <= enddelay; i++){
            double result = pearsonCorrelation(fixed, moving, i);

            if (!Double.isNaN(result)) {
                // Calcolo N effettivo per questo specifico delay
                int effective_n = total_samples - Math.abs(i);

                // Calcolo p-value usando la tua funzione
                double pVal = CorrelationSignificance.correlationPValue(result, effective_n);

                // Calcolo Effect Size E% (R^2 * 100)
                double effectSizePct = (result * result) * 100.0;

                // Arrotondamenti per pulizia di lettura
                double r_rounded = Math.round(result * 1000.0) / 1000.0;
                double effect_rounded = Math.round(effectSizePct * 100.0) / 100.0;

                results.add(new CorrelationPair(r_rounded, i, pVal, effect_rounded));
            }
        }

        // Ordinamento invariato (per R maggiore)
        results.sort(new Comparator<CorrelationPair>() {
            @Override
            public int compare(CorrelationPair correlationPair, CorrelationPair t1) {
                return Double.compare(Math.abs(t1.correlation), Math.abs(correlationPair.correlation));
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
