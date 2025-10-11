package com.example.bruxismdetector.bruxism_grapher2;


import java.io.*;
        import java.util.*;

public class SVMTrainer {

    public interface ProgressCallback{
        public void onProgress(int progress);
    }
    private double[][] X;
    private double[] y;
    private double[] alpha;
    private double[] w;
    private double b;
    private double C = 1.0;
    private double tol = 1e-10;
    private double eps = 1e-10;
    private int maxPasses = 5000;

    public void fit(double[][] X, double[] y, ProgressCallback pcb) {
        pcb.onProgress(0);
        this.X = X;
        this.y = y;
        int m = X.length;
        int n = X[0].length;
        this.alpha = new double[m];
        this.b = 0.0;
        int passes = 0;

        while (passes < maxPasses) {
            pcb.onProgress((int) (100.0*((double) passes /maxPasses)));
            int numChanged = 0;
            for (int i = 0; i < m; i++) {
                double Ei = predictRaw(X[i]) - y[i];
                if ((y[i]*Ei < -tol && alpha[i] < C) || (y[i]*Ei > tol && alpha[i] > 0)) {
                    int j = selectJ(i, m);
                    double Ej = predictRaw(X[j]) - y[j];

                    double alphaIOld = alpha[i];
                    double alphaJOld = alpha[j];

                    double L, H;
                    if (y[i] != y[j]) {
                        L = Math.max(0, alpha[j] - alpha[i]);
                        H = Math.min(C, C + alpha[j] - alpha[i]);
                    } else {
                        L = Math.max(0, alpha[i] + alpha[j] - C);
                        H = Math.min(C, alpha[i] + alpha[j]);
                    }

                    if (L == H) continue;

                    double eta = 2 * dot(X[i], X[j]) - dot(X[i], X[i]) - dot(X[j], X[j]);
                    if (eta >= 0) continue;

                    alpha[j] -= y[j] * (Ei - Ej) / eta;
                    if (alpha[j] > H) alpha[j] = H;
                    else if (alpha[j] < L) alpha[j] = L;

                    if (Math.abs(alpha[j] - alphaJOld) < eps) continue;

                    alpha[i] += y[i] * y[j] * (alphaJOld - alpha[j]);

                    double b1 = b - Ei
                            - y[i]*(alpha[i]-alphaIOld)*dot(X[i], X[i])
                            - y[j]*(alpha[j]-alphaJOld)*dot(X[i], X[j]);
                    double b2 = b - Ej
                            - y[i]*(alpha[i]-alphaIOld)*dot(X[i], X[j])
                            - y[j]*(alpha[j]-alphaJOld)*dot(X[j], X[j]);

                    if (0 < alpha[i] && alpha[i] < C) b = b1;
                    else if (0 < alpha[j] && alpha[j] < C) b = b2;
                    else b = (b1 + b2) / 2.0;

                    numChanged++;
                }
            }
            if (numChanged == 0) passes++;
            else passes = 0;
        }

        pcb.onProgress(99);
        // Calculate w = Σ α_i y_i x_i
        w = new double[n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                w[j] += alpha[i] * y[i] * X[i][j];
            }
        }
    }

    private int selectJ(int i, int m) {
        int j = i;
        while (j == i) j = (int)(Math.random() * m);
        return j;
    }

    private double predictRaw(double[] x) {
        if (alpha == null) return 0;
        double sum = 0.0;
        for (int i = 0; i < X.length; i++) {
            if (alpha[i] > 0)
                sum += alpha[i] * y[i] * dot(X[i], x);
        }
        return sum + b;
    }

    public double predict(double[] x) {
        return Math.signum(predictRaw(x));
    }

    private double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    public double[] getWeights() { return w; }
    public double getBias() { return b; }

    public static void normalize(double[][] X) {
        int m = X.length, n = X[0].length;
        for (int j = 0; j < n; j++) {
            double mean = 0, std = 0;
            for (int i = 0; i < m; i++) mean += X[i][j];
            mean /= m;
            for (int i = 0; i < m; i++) std += Math.pow(X[i][j] - mean, 2);
            std = Math.sqrt(std / m);
            for (int i = 0; i < m; i++) X[i][j] = (X[i][j] - mean) / (std + 1e-8);
        }
    }

    public static double[][] readCSV(String path) throws IOException {
        List<double[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                double[] vals = new double[parts.length];
                for (int i = 0; i < parts.length; i++)
                    vals[i] = Double.parseDouble(parts[i].trim());
                rows.add(vals);
            }
        }
        return rows.toArray(new double[0][]);
    }

    public static String train_for_result(String clenching_file, String nonclenching_file, ProgressCallback pcb) throws Exception {
        double[][] clenching = readCSV(clenching_file);
        double[][] nonClenching = readCSV(nonclenching_file);

        int n1 = clenching.length, n2 = nonClenching.length, nF = clenching[0].length;
        double[][] X = new double[n1 + n2][nF];
        double[] y = new double[n1 + n2];
        for (int i = 0; i < n1; i++) { X[i] = clenching[i]; y[i] = 1; }
        for (int i = 0; i < n2; i++) { X[n1 + i] = nonClenching[i]; y[n1 + i] = -1; }

        normalize(X);

        SVMTrainer svm = new SVMTrainer();
        svm.fit(X, y, pcb);

        double[] w = svm.getWeights();
        double b = svm.getBias();

        StringBuilder result = new StringBuilder();

        result.append("static const float weights[] = { ");
        for (int i = 0; i < w.length; i++) {
            result.append(String.format(Locale.US,"%.8f%s", w[i], (i < w.length - 1) ? ", " : " "));
        }
        result.append("};\n");
        result.append(String.format(Locale.US,"static const float bias = %.8f;%n", b));


        double[] scoresClenching = new double[n1];
        double[] scoresNonClenching = new double[n2];
        for (int i = 0; i < n1; i++)
            scoresClenching[i] = svm.predict(clenching[i]);
        for (int i = 0; i < n2; i++)
            scoresNonClenching[i] = svm.predict(nonClenching[i]);

        double meanC = Arrays.stream(scoresClenching).average().orElse(0);
        double meanN = Arrays.stream(scoresNonClenching).average().orElse(0);
        double threshold = meanC - Math.abs(meanC - meanN) * 0.3;

        result.append(String.format(Locale.US,"static const int classification_threshold = %.0f;%n", threshold));
        return result.toString();
    }
}
