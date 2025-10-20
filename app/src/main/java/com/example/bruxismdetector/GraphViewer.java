package com.example.bruxismdetector;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.animation.ArgbEvaluator;
import androidx.core.animation.ObjectAnimator;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;

import com.example.bruxismdetector.bruxism_grapher2.CorrelationsCalculator;
import com.example.bruxismdetector.bruxism_grapher2.GrapherAsyncTask;
import com.example.bruxismdetector.bruxism_grapher2.SummaryReader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GraphViewer extends AppCompatActivity {

    private File[] graphFiles;
    private ViewPager2 viewPager;
    private LinearLayout aiEvaluationPanel;
    private LinearLayout barChartContainer;
    private ImageView dragHandle;
    private ValueAnimator weightAnimator;
    private float startX;
    private static final int CLICK_ACTION_THRESHOLD = 20;

    private ImagePagerAdapter adapter;

    private static final float DEFAULT_VIEWPAGER_WEIGHT = 6.8f;
    private static final float DEFAULT_AI_PANEL_WEIGHT = 3.2f;

    private boolean isAiPanelOpen = true;

    // Data for averages
    String[] summaryTitles;
    ArrayList<String[]> summaryTuples;
    float[] averages;
    int[] average_idx_to_column;

    int generated_upto = 0;

    private ExecutorService executorService;

    private TextView comparisonModeTextView;
    private enum ComparisonMode {
        VS_AVERAGE,
        VS_PREVIOUS
    }
    private ComparisonMode currentComparisonMode = ComparisonMode.VS_AVERAGE;

    private final HashMap<String, View> activeRows = new HashMap<>();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        int coreCount = Runtime.getRuntime().availableProcessors();
        executorService = Executors.newFixedThreadPool(coreCount);

        viewPager = findViewById(R.id.viewPager);
        ImageButton btnLeft = findViewById(R.id.btnLeft);
        ImageButton btnRight = findViewById(R.id.btnRight);

        aiEvaluationPanel = findViewById(R.id.aiEvaluationPanel);
        barChartContainer = findViewById(R.id.barChartContainer); // Get the new container

        comparisonModeTextView = findViewById(R.id.comparisonModeTextView);
        barChartContainer.setOnClickListener(v -> {
            // Cambia la modalità di confronto
            if (currentComparisonMode == ComparisonMode.VS_AVERAGE) {
                currentComparisonMode = ComparisonMode.VS_PREVIOUS;
            } else {
                currentComparisonMode = ComparisonMode.VS_AVERAGE;
            }
            // Forza l'aggiornamento del grafico con la nuova modalità
            displayAveragesChart(viewPager.getCurrentItem());
        });

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

        GrapherAsyncTask gat = new GrapherAsyncTask(this);
        generated_upto = graphFiles.length;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                btnLeft.setVisibility(position != 0 ? View.VISIBLE : View.INVISIBLE);
                btnRight.setVisibility(position != graphFiles.length - 1 ? View.VISIBLE : View.INVISIBLE);
                updateAiPanelVisibility(position);
                Log.d("GraphViewer", "Page selected: " + position);


                if(prefs.getBoolean("regen_graph_scroll", false)){
                for (int i = 0; i < 5; i++) {
                    final int currentPosition = position - i;
                    if (currentPosition >= 0 && currentPosition < generated_upto) {
                        generated_upto = currentPosition;
                        File csv = checkCorrespondingCsv(currentPosition);
                        if (csv != null) {
                            // Invia il task al thread pool invece di creare un nuovo thread
                            executorService.submit(() -> {
                                try {
                                    gat.makeGraph(csv, false);
                                    Log.i("GraphViewer", "Graph regenerated in background for: " + csv.getName());
                                } catch (Exception e) {
                                    Log.e("GraphViewer", "Error regenerating graph for " + csv.getName(), e);
                                }
                            });
                        }
                    }}
                }
            }
        });



        dragHandle = findViewById(R.id.drag_handle);
        aiEvaluationPanel.setVisibility(View.VISIBLE);
        dragHandle.setVisibility(View.VISIBLE);
        setupDragHandle();
        animateWeights(DEFAULT_VIEWPAGER_WEIGHT, DEFAULT_AI_PANEL_WEIGHT);


        // Calculate averages on creation
        try {
            readSummary();
        } catch (Exception e) {
            Log.e("GraphViewer", "Failed to read summary", e);
        }

        viewPager.setCurrentItem(graphFiles.length - 1, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            Log.i("GraphViewer", "Shutting down thread pool.");
            executorService.shutdown();
        }
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
        displayAveragesChart(position);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDragHandle() {
        dragHandle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (weightAnimator != null && weightAnimator.isRunning()) {
                        weightAnimator.cancel();
                    }
                    startX = event.getX();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    // La logica del DRAG rimane invariata
                    float totalWidth = findViewById(R.id.main_container).getWidth();
                    if (totalWidth == 0) return true; // Evita divisione per zero

                    float newAiPanelWidth = totalWidth - event.getRawX();
                    float minWidth = totalWidth * 0.10f;
                    float maxWidth = totalWidth * 0.70f;
                    newAiPanelWidth = Math.max(minWidth, Math.min(newAiPanelWidth, maxWidth));
                    float aiPanelWeight = (newAiPanelWidth / totalWidth) * 10.0f;
                    float viewPagerWeight = 10.0f - aiPanelWeight;

                    setWeights(viewPagerWeight, aiPanelWeight);
                    // Mentre trasciniamo, consideriamo il pannello "aperto"
                    isAiPanelOpen = true;
                    return true;

                case MotionEvent.ACTION_UP:
                    float endX = event.getX();
                    if (Math.abs(endX - startX) < CLICK_ACTION_THRESHOLD) {
                        // È un TAP
                        if (isAiPanelOpen) {
                            // Se è aperto, chiudilo
                            animateWeights(10.0f, 0f);
                            isAiPanelOpen = false;
                        } else {
                            // Se è chiuso, aprilo alla dimensione di default
                            animateWeights(DEFAULT_VIEWPAGER_WEIGHT, DEFAULT_AI_PANEL_WEIGHT);
                            isAiPanelOpen = true;
                        }
                    } else {
                        // È un DRAG (rilascio)
                        // Anima il ritorno alla posizione di default se è aperto
                        if (isAiPanelOpen) {
                            animateWeights(DEFAULT_VIEWPAGER_WEIGHT, DEFAULT_AI_PANEL_WEIGHT);
                        }
                        // Se fosse stato chiuso, un drag non è possibile, quindi non serve un else.
                    }
                    return true;
            }
            return false;
        });
    }

    private void setWeights(float viewPagerWeight, float aiPanelWeight) {
        LinearLayout.LayoutParams viewPagerParams = (LinearLayout.LayoutParams) viewPager.getLayoutParams();
        LinearLayout.LayoutParams aiPanelParams = (LinearLayout.LayoutParams) aiEvaluationPanel.getLayoutParams();

        viewPagerParams.weight = viewPagerWeight;
        aiPanelParams.weight = aiPanelWeight;

        // Applica le modifiche
        viewPager.setLayoutParams(viewPagerParams);
        aiEvaluationPanel.setLayoutParams(aiPanelParams);
    }


    // Sostituisci il tuo metodo displayAveragesChart con questo
    private void displayAveragesChart(int wanted_pos) {
        if (summaryTuples == null || summaryTuples.isEmpty()) {
            return;
        }

        int sessionIndex = wanted_pos;
        if (sessionIndex < 0 || sessionIndex >= summaryTuples.size()) {
            Log.e("GraphViewer", "Invalid session index: " + sessionIndex);
            return;
        }

        // --- LOGICA DI SELEZIONE MODALITÀ ---
        boolean isVsPreviousMode = (currentComparisonMode == ComparisonMode.VS_PREVIOUS && sessionIndex > 0);

        if (isVsPreviousMode) {
            comparisonModeTextView.setText("Session vs Previous");
        } else {
            // Se la modalità è VS_PREVIOUS ma siamo all'indice 0, torna a VS_AVERAGE
            currentComparisonMode = ComparisonMode.VS_AVERAGE;
            comparisonModeTextView.setText("Session vs Average");
        }
        // Abilita/disabilita il click se non si può cambiare modalità
        barChartContainer.setClickable(sessionIndex > 0);


        String[] currentSessionData = summaryTuples.get(sessionIndex);
        String[] comparisonData = isVsPreviousMode ? summaryTuples.get(sessionIndex - 1) : null;

        HashSet<String> relevantMetrics = new HashSet<>();

        for (int i = 0; i < average_idx_to_column.length; i++) {
            int colIdx = average_idx_to_column[i];
            String title = summaryTitles[colIdx];
            float lastValue = Float.parseFloat(currentSessionData[colIdx]);

            float comparisonValue;
            if (isVsPreviousMode) {
                comparisonValue = Float.parseFloat(comparisonData[colIdx]);
            } else {
                comparisonValue = averages[i];
            }

            float deviation = (comparisonValue > 0) ? (lastValue - comparisonValue) / comparisonValue : 0;
            byte trend = CorrelationsCalculator.isGoingToBetter(deviation, title);

            if (trend != CorrelationsCalculator.NeutralCorr && Math.abs(deviation) >= 0.20) {
                relevantMetrics.add(title);
                View row = activeRows.get(title);
                if (row == null) {
                    // --- MODIFICA PER LA CREAZIONE ---
                    // 1. Crea la riga (che ora è trasparente e con la barra a zero di default)
                    row = createMetricRow(title);
                    activeRows.put(title, row);
                    barChartContainer.addView(row);

                    // 2. Avvia l'animazione di fade-in. Al termine, avvia l'animazione della barra.
                    final View finalRow = row; // Necessario per la lambda
                    row.animate()
                            .alpha(1f)
                            .setDuration(300) // Durata del fade-in
                            .withEndAction(() -> {
                                // Questa parte viene eseguita DOPO il fade-in
                                updateMetricRow(finalRow, deviation, trend);
                            })
                            .start();

                } else {
                    // --- MODIFICA PER L'AGGIORNAMENTO ---
                    // Se la riga esiste già, aggiorna direttamente la barra (l'animazione è in updateMetricRow)
                    updateMetricRow(row, deviation, trend);
                }
            }
        }


        Iterator<HashMap.Entry<String, View>> iterator = activeRows.entrySet().iterator();
        while (iterator.hasNext()) {
            HashMap.Entry<String, View> entry = iterator.next();
            if (!relevantMetrics.contains(entry.getKey())) {
                View rowToRemove = entry.getValue();
                iterator.remove();

                // ==================== CORREZIONE CHIAVE ====================
                // Recupera il barContainer dal tag della riga prima di passarlo ad animateBar.
                LinearLayout barContainer = (LinearLayout) rowToRemove.getTag();
                //if (barContainer != null) {
                //    animateBar(barContainer, 0, 0); // Anima la barra a zero
                //}
                // =========================================================

                // 2. Al termine dell'animazione della barra, avvia il fade-out e rimuovi la vista.
                //rowToRemove.animate()
                //        //.setStartDelay(200) // Attendi che l'animazione della barra finisca
                //        .alpha(0f)
                //        .setDuration(300)
                //        .withEndAction(() -> barChartContainer.removeView(rowToRemove))
                //        .start();
                barChartContainer.removeView(rowToRemove);
            }
        }
    }

    // Metodo helper per creare una nuova riga (migliora la leggibilità)
    private View createMetricRow(String title) { // Rimuovi i parametri deviation e trend da qui
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 80));
        row.setGravity(Gravity.CENTER_VERTICAL);

        // --- IMPOSTAZIONE INIZIALE ---
        // La riga viene creata trasparente, pronta per il fade-in.
        row.setAlpha(0f);

        // 1. Label
        TextView label = new TextView(this);
        label.setId(R.id.metric_label);
        label.setText(title);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        label.setPadding(8, 0, 8, 0);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);

        // 2. Bar Container
        LinearLayout barContainer = createBarContainer();
        row.setTag(barContainer);

        // 3. Value Label
        TextView valueLabel = new TextView(this);
        valueLabel.setId(R.id.metric_value);
        valueLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f));
        valueLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        valueLabel.setPadding(16, 0, 8, 0);
        valueLabel.setText(""); // Inizia vuoto

        row.addView(label);
        row.addView(barContainer);
        row.addView(valueLabel);

        // NON chiamare updateMetricRow qui. Verrà chiamato dopo il fade-in.

        return row;
    }

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


        animateBarColor(negFill, barColor);
        animateBarColor(posFill, barColor);

        //negFill.setBackgroundColor(barColor);
        //posFill.setBackgroundColor(barColor);

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

        // ==================== NUOVA LOGICA ====================
        // 1. Crea un'istanza dell'OvershootInterpolator.
        //    Il parametro nel costruttore controlla la "tensione" dell'overshoot.
        //    Un valore tra 1.0 e 2.0 è solitamente un buon punto di partenza.
        OvershootInterpolator overshootInterpolator = new OvershootInterpolator(1.5f);

        // 2. Applica l'interpolatore all'animatore.
        animator.setInterpolator(overshootInterpolator);
        // ======================================================

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
        animator.setStartDelay(500);
        animator.start();
    }

    /**
     * Anima il colore di sfondo di una View da quello attuale a un colore di destinazione.
     * @param view La View di cui animare il colore (es. negFill o posFill).
     * @param targetColor Il colore finale desiderato.
     */
    private void animateBarColor(View view, int targetColor) {
        int startColor = Color.TRANSPARENT;
        if (view.getBackground() instanceof ColorDrawable) {
            startColor = ((ColorDrawable) view.getBackground()).getColor();
        }
        // Se il colore di partenza è trasparente (prima animazione), non animare, imposta direttamente.
        if (startColor == Color.TRANSPARENT) {
            view.setBackgroundColor(targetColor);
            return;
        }

        // Usa ObjectAnimator per animare la proprietà "backgroundColor"
        ObjectAnimator colorAnimator = ObjectAnimator.ofObject(
                view,
                "backgroundColor",
                ArgbEvaluator.getInstance(),
                startColor,
                targetColor
        );
        colorAnimator.setDuration(500); // Stessa durata dell'animazione della dimensione
        colorAnimator.setStartDelay(500);
        colorAnimator.start();
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

    private void animateWeights(float targetViewPagerWeight, float targetAiPanelWeight) {
        // Ottieni i pesi di partenza
        LinearLayout.LayoutParams viewPagerParams = (LinearLayout.LayoutParams) viewPager.getLayoutParams();
        LinearLayout.LayoutParams aiPanelParams = (LinearLayout.LayoutParams) aiEvaluationPanel.getLayoutParams();
        final float startViewPagerWeight = viewPagerParams.weight;
        final float startAiPanelWeight = aiPanelParams.weight;

        // Se c'è un'animazione precedente, cancellala
        if (weightAnimator != null && weightAnimator.isRunning()) {
            weightAnimator.cancel();
        }

        // Crea un animatore che va da 0 a 1
        weightAnimator = ValueAnimator.ofFloat(0f, 1f);
        weightAnimator.setDuration(400); // Durata dell'animazione in ms
        weightAnimator.setInterpolator(new android.view.animation.OvershootInterpolator(1.0f)); // Aggiunge l'effetto "overshoot"

        weightAnimator.addUpdateListener(animation -> {
            // Calcola il valore interpolato per ogni frame
            float fraction = animation.getAnimatedFraction();
            float currentViewPagerWeight = startViewPagerWeight + (targetViewPagerWeight - startViewPagerWeight) * fraction;
            float currentAiPanelWeight = startAiPanelWeight + (targetAiPanelWeight - startAiPanelWeight) * fraction;

            // Applica i nuovi pesi
            setWeights(currentViewPagerWeight, currentAiPanelWeight);
        });

        weightAnimator.start();
    }

    private File checkCorrespondingCsv(int position) {
        if (graphFiles == null || position >= graphFiles.length) {
            return null;
        }

        File pngFile = graphFiles[position];
        String csvFileName = pngFile.getName().replace(".png", ".csv");

        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File recordingsDir = new File(documentsDir, "RECORDINGS");
        File csvFile = new File(recordingsDir, csvFileName);


        if (csvFile.exists()) {
            Log.i("GraphViewer", "Corresponding CSV file found: " + csvFile.getName());
            return csvFile;
        } else {
            Log.w("GraphViewer", "Corresponding CSV file NOT found: " + csvFile.getName());
            return null;
        }
    }
}
