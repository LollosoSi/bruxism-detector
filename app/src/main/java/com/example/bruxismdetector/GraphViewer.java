package com.example.bruxismdetector;

import android.animation.ValueAnimator;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.bruxismdetector.bruxism_grapher2.CorrelationsCalculator;
import com.example.bruxismdetector.bruxism_grapher2.SummaryReader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;

public class GraphViewer extends AppCompatActivity {

    private File[] graphFiles;
    private ViewPager2 viewPager;
    private LinearLayout aiEvaluationPanel;
    private LinearLayout barChartContainer; // Container for the bars
    private ImagePagerAdapter adapter;

    private int defaultTextColor;

    // Data for averages
    String[] summaryTitles;
    ArrayList<String[]> summaryTuples;
    float[] averages;
    int[] average_idx_to_column;

    private final HashMap<String, View> activeRows = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ... (il tuo codice di setup della UI non cambia)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decorView = window.getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        EdgeToEdge.enable(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_graph_viewer);

        viewPager = findViewById(R.id.viewPager);
        ImageButton btnLeft = findViewById(R.id.btnLeft);
        ImageButton btnRight = findViewById(R.id.btnRight);

        aiEvaluationPanel = findViewById(R.id.aiEvaluationPanel);
        barChartContainer = findViewById(R.id.barChartContainer); // Get the new container

        graphFiles = getGraphs();
        if (graphFiles == null || graphFiles.length == 0) {
            return;
        }

        adapter = new ImagePagerAdapter(graphFiles, scale -> {
            viewPager.setUserInputEnabled(scale <= 1.2f);
            Log.i("GraphViewer", "Scale: " + scale);
        });

        viewPager.setAdapter(adapter);
        viewPager.setUserInputEnabled(true);

        btnLeft.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current > 0) {
                viewPager.setCurrentItem(current - 1, true);
            }
        });

        btnRight.setOnClickListener(v -> {
            int current = viewPager.getCurrentItem();
            if (current < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(current + 1, true);
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                btnLeft.setVisibility(position != 0 ? View.VISIBLE : View.INVISIBLE);
                btnRight.setVisibility(position != graphFiles.length - 1 ? View.VISIBLE : View.INVISIBLE);
                updateAiPanelVisibility(position);
                Log.d("GraphViewer", "Page selected: " + position);
            }
        });

        // Calculate averages on creation
        try {
            readSummary();
        } catch (Exception e) {
            Log.e("GraphViewer", "Failed to read summary", e);
        }

        viewPager.setCurrentItem(graphFiles.length - 1, true);
    }

    private File[] getGraphs() {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File recordingsDir = new File(documentsDir, "RECORDINGS");
        File graphDir = new File(recordingsDir, "Graphs");

        File[] files = graphDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null) return null;

        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }

    private void updateAiPanelVisibility(int position) {
        LinearLayout.LayoutParams viewPagerParams = (LinearLayout.LayoutParams) viewPager.getLayoutParams();
        LinearLayout.LayoutParams aiPanelParams = (LinearLayout.LayoutParams) aiEvaluationPanel.getLayoutParams();

        if (position == (graphFiles.length - 1)) {
            // Show the AI panel for the last item
            aiEvaluationPanel.setVisibility(View.VISIBLE);
            viewPagerParams.weight = 7.0f;
            aiPanelParams.weight = 3.0f;

            // Display the bar chart instead of text


        } else {
            // Hide the AI panel for all other items
            //aiEvaluationPanel.setVisibility(View.GONE);
            viewPagerParams.weight = 7.0f;
            aiPanelParams.weight = 3.0f;
        }

        displayAveragesChart(position);
        viewPager.setLayoutParams(viewPagerParams);
        aiEvaluationPanel.setLayoutParams(aiPanelParams);
    }

    // Sostituisci il tuo metodo displayAveragesChart con questo
    private void displayAveragesChart(int wanted_pos) {
        if (summaryTuples == null || summaryTuples.isEmpty() || averages == null) {
            return;
        }

        // Calcola l'indice corretto per la sessione desiderata
        // L'array summaryTuples è ordinato dal più vecchio al più nuovo.
        // L'adapter del ViewPager è ordinato per nome file (dal più vecchio al più nuovo).
        // Quindi, la posizione 'wanted_pos' corrisponde direttamente all'indice in summaryTuples.
        int sessionIndex = wanted_pos;
        if (sessionIndex < 0 || sessionIndex >= summaryTuples.size()) {
            Log.e("GraphViewer", "Invalid session index: " + sessionIndex);
            return;
        }
        String[] sessionData = summaryTuples.get(sessionIndex);

        // Set per tenere traccia delle metriche rilevanti in questa sessione
        HashSet<String> relevantMetrics = new HashSet<>();

        for (int i = 0; i < averages.length; i++) {
            int colIdx = average_idx_to_column[i];
            String title = summaryTitles[colIdx];
            float lastValue = Float.parseFloat(sessionData[colIdx]);
            float avgValue = averages[i];

            float deviation = (avgValue > 0) ? (lastValue - avgValue) / avgValue : 0;

            byte trend = CorrelationsCalculator.isGoingToBetter(deviation, title);

            // CONDIZIONI DI RILEVANZA: trend non neutrale E deviazione > 20%
            if (trend != CorrelationsCalculator.NeutralCorr && Math.abs(deviation) >= 0.20) {
                relevantMetrics.add(title);

                View row = activeRows.get(title);
                if (row == null) {
                    // La riga non esiste, creala e aggiungila
                    row = createMetricRow(title, deviation, trend);
                    activeRows.put(title, row);
                    barChartContainer.addView(row);
                    // Animazione di fade-in per la nuova riga
                    row.setAlpha(0f);
                    row.animate().alpha(1f).setDuration(300).start();
                } else {
                    // La riga esiste già, aggiornala
                    updateMetricRow(row, deviation, trend);
                }
            }
        }

        // Rimuovi le righe che non sono più rilevanti
        Iterator<HashMap.Entry<String, View>> iterator = activeRows.entrySet().iterator();
        while (iterator.hasNext()) {
            HashMap.Entry<String, View> entry = iterator.next();
            if (!relevantMetrics.contains(entry.getKey())) {
                View rowToRemove = entry.getValue();
                iterator.remove();
                // Animazione di fade-out prima della rimozione
                rowToRemove.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    barChartContainer.removeView(rowToRemove);
                }).start();
            }
        }
    }

    // Metodo helper per creare una nuova riga (migliora la leggibilità)
    private View createMetricRow(String title, float deviation, byte trend) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 80));
        row.setGravity(Gravity.CENTER_VERTICAL);

        // 1. Label
        TextView label = new TextView(this);
        label.setId(R.id.metric_label); // Usa ID per trovarlo dopo
        label.setText(title);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        label.setPadding(8, 0, 8, 0);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);

        // 2. Bar Container
        LinearLayout barContainer = createBarContainer();
        row.setTag(barContainer); // Salva il bar container nel tag della riga per un facile accesso

        // 3. Value Label
        TextView valueLabel = new TextView(this);
        valueLabel.setId(R.id.metric_value); // Usa ID per trovarlo dopo
        valueLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f));
        valueLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        valueLabel.setPadding(16, 0, 8, 0);

        row.addView(label);
        row.addView(barContainer);
        row.addView(valueLabel);

        updateMetricRow(row, deviation, trend); // Applica i valori iniziali e avvia l'animazione

        return row;
    }

    // Metodo helper per creare la struttura della barra (evita duplicazione)
    private LinearLayout createBarContainer() {
        LinearLayout barContainer = new LinearLayout(this);
        barContainer.setOrientation(LinearLayout.HORIZONTAL);
        barContainer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f));
        barContainer.setPadding(4, 20, 4, 20);
        barContainer.setGravity(Gravity.CENTER_VERTICAL);

        View leftSpacer = new View(this); leftSpacer.setId(R.id.spacer_left);
        View negFill = new View(this); negFill.setId(R.id.fill_neg);
        View centerLine = new View(this);
        View posFill = new View(this); posFill.setId(R.id.fill_pos);
        View rightSpacer = new View(this); rightSpacer.setId(R.id.spacer_right);

        centerLine.setLayoutParams(new LinearLayout.LayoutParams(4, LinearLayout.LayoutParams.MATCH_PARENT));
        centerLine.setBackgroundColor(Color.WHITE); // Changed to white as requested

        // --- CORRECTED INITIAL WEIGHTS ---
        // Each side (left and right) has a total weight of 1.
        // Initially, the spacers take up all the space.
        leftSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        negFill.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0f));
        posFill.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0f));
        rightSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        barContainer.addView(leftSpacer);
        barContainer.addView(negFill);
        barContainer.addView(centerLine);
        barContainer.addView(posFill);
        barContainer.addView(rightSpacer);
        return barContainer;
    }


    // Metodo per aggiornare una riga esistente
    private void updateMetricRow(View row, float deviation, byte trend) {
        LinearLayout barContainer = (LinearLayout) row.getTag();
        TextView valueLabel = row.findViewById(R.id.metric_value);

        int goodColor = getColor(R.color.material_green_500);
        int badColor = getColor(R.color.material_red_500);
        int barColor = (trend == CorrelationsCalculator.PositiveCorr) ? goodColor : badColor;

        // Aggiorna colore e testo
        valueLabel.setTextColor(barColor);
        animateTextValue(valueLabel, deviation * 100);

        // Aggiorna animazione barra
        animateBar(barContainer, deviation, barColor);
    }

    // Anima la barra di una riga specifica
    // In GraphViewer.java, add this new, corrected animateBar method

    private void animateBar(LinearLayout barContainer, float deviation, int barColor) {
        View leftSpacer = barContainer.findViewById(R.id.spacer_left);
        View negFill = barContainer.findViewById(R.id.fill_neg);
        View posFill = barContainer.findViewById(R.id.fill_pos);
        View rightSpacer = barContainer.findViewById(R.id.spacer_right);

        negFill.setBackgroundColor(barColor);
        posFill.setBackgroundColor(barColor);

        // Normalize deviation to be between -1 and 1 for weight calculation
        float absNormalizedDev = Math.min(Math.abs(deviation), 1.0f);

        // Determine target weights
        float targetNegWeight = (deviation < 0) ? absNormalizedDev : 0;
        float targetPosWeight = (deviation > 0) ? absNormalizedDev : 0;

        // Get current weights to animate from
        float startNegWeight = ((LinearLayout.LayoutParams) negFill.getLayoutParams()).weight;
        float startPosWeight = ((LinearLayout.LayoutParams) posFill.getLayoutParams()).weight;

        // Use a single animator to handle both cases
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(500);
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();

            // Interpolate weights from start to target
            float currentNegWeight = startNegWeight + fraction * (targetNegWeight - startNegWeight);
            float currentPosWeight = startPosWeight + fraction * (targetPosWeight - startPosWeight);

            // Get params
            LinearLayout.LayoutParams negParams = (LinearLayout.LayoutParams) negFill.getLayoutParams();
            LinearLayout.LayoutParams posParams = (LinearLayout.LayoutParams) posFill.getLayoutParams();
            LinearLayout.LayoutParams leftSpacerParams = (LinearLayout.LayoutParams) leftSpacer.getLayoutParams();
            LinearLayout.LayoutParams rightSpacerParams = (LinearLayout.LayoutParams) rightSpacer.getLayoutParams();

            // Set weights for both sides simultaneously
            negParams.weight = currentNegWeight;
            leftSpacerParams.weight = 1f - currentNegWeight;

            posParams.weight = currentPosWeight;
            rightSpacerParams.weight = 1f - currentPosWeight;

            // Request a layout update for the whole container
            barContainer.requestLayout();
        });
        animator.start();
    }

    // Anima il valore di testo (percentuale)
    private void animateTextValue(TextView textView, float finalPercentage) {
        // Estrai il valore numerico corrente dal testo, se possibile
        float startPercentage = 0;
        try {
            String currentText = textView.getText().toString().replace("%", "").replace("+", "");
            if (!currentText.isEmpty()) {
                startPercentage = Float.parseFloat(currentText);
            }
        } catch (NumberFormatException e) {
            // Ignora se il parsing fallisce, inizia da 0
        }

        ValueAnimator textAnimator = ValueAnimator.ofFloat(startPercentage, finalPercentage);
        textAnimator.setDuration(500);
        textAnimator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            textView.setText(String.format(Locale.US, "%+.0f%%", animatedValue));
        });
        textAnimator.start();
    }

    void readSummary() {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File recordingsDir = new File(documentsDir, "RECORDINGS");
        File summaryDir = new File(recordingsDir, "Summary");

        SummaryReader.setFilepath(summaryDir.getParent() + "/Summary/Summary.csv");

        SummaryReader sr = SummaryReader.getInstance();
        summaryTitles = sr.getSummaryTitles();
        summaryTuples = sr.getSummaryTuplesWithNoSkipItems();

        if (summaryTuples == null || summaryTuples.isEmpty()) {
            Log.w("GraphViewer", "Summary tuples are empty, cannot calculate averages.");
            return;
        }

        ArrayList<Integer> numericColumnIndices = new ArrayList<>();
        int columnIndex = 0;
        for (String value : summaryTuples.get(0)) {
            try {
                Float.parseFloat(value);
                numericColumnIndices.add(columnIndex);
            } catch (NumberFormatException e) {
                // Not a numeric column
            }
            columnIndex++;
        }

        int numNumericColumns = numericColumnIndices.size();
        averages = new float[numNumericColumns];
        average_idx_to_column = new int[numNumericColumns];

        for (int i = 0; i < numNumericColumns; i++) {
            average_idx_to_column[i] = numericColumnIndices.get(i);
        }

        for (String[] tuple : summaryTuples) {
            for (int i = 0; i < numNumericColumns; i++) {
                int colIdx = average_idx_to_column[i];
                try {
                    averages[i] += Float.parseFloat(tuple[colIdx]);
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    Log.e("GraphViewer", "Error parsing float in row for column " + colIdx, e);
                }
            }
        }

        int totalRows = summaryTuples.size();
        if (totalRows > 0) {
            Log.i("GraphViewer", "Averages across " + totalRows + " rows:");
            for (int i = 0; i < numNumericColumns; i++) {
                averages[i] /= totalRows;
                Log.i("GraphViewer", summaryTitles[average_idx_to_column[i]] + ": " + averages[i]);
            }
        }
    }
}
