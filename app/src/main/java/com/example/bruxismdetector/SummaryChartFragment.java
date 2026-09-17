package com.example.bruxismdetector;

import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bruxismdetector.bruxism_grapher2.SummaryReader;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.LinkedList;

public class SummaryChartFragment extends Fragment {

    private View root;
    private View bottomDrawerView; // Changed from BottomNavigationView to generic View
    private View topHandle;
    private ViewGroup mainLayout;

    private boolean isExpanded = false;
    private int collapsedHeightDp = 50;

    // Data Storage
    private String[] summaryTitles;
    private ArrayList<String[]> summaryTuples;
    private ArrayList<String> dateLabels;
    private ArrayList<String> filterNames;

    // UI References
    private CheckBox baseDrawCheck;
    private CheckBox separateDrawCheck;
    private CheckBox rollingAverageCheck;
    private LinearLayout leftCol, rightCol, graphsHolder;
    private TextView noResultText;

    private final int[] COLOR_ARRAY = {
            R.color.material_orange_500, R.color.material_green_500,
            R.color.material_blue_500, R.color.material_red_500,
            R.color.material_yellow_500
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_charts, container, false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        root = null; // Prevent memory leak
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;

        // Initialize UI Elements
        baseDrawCheck = view.findViewById(R.id.basedrawcheckbox);
        separateDrawCheck = view.findViewById(R.id.separatedrawcheckbox);
        rollingAverageCheck = view.findViewById(R.id.rollingaveragecheckbox);
        leftCol = view.findViewById(R.id.left_col);
        rightCol = view.findViewById(R.id.right_col);
        graphsHolder = view.findViewById(R.id.graphs_holder);
        noResultText = view.findViewById(R.id.noresultext);

        // Bottom Drawer Setup
        bottomDrawerView = view.findViewById(R.id.bottom_card_view);
        topHandle = view.findViewById(R.id.top_handle);
        mainLayout = view.findViewById(R.id.main);

        bottomDrawerView.post(() -> {
            this.collapsedHeightDp = getCollapsedHeight();
            this.isExpanded = true;
            toggleCardHeight();
        });

        setupDrag();
        setupControlListeners();

        // Load data in background, THEN build UI
        loadDataAndInitializeUI();
    }

    private void setupControlListeners() {
        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            vibrateHaptic();
            recalculateGraphs();
        };

        baseDrawCheck.setOnCheckedChangeListener(listener);
        separateDrawCheck.setOnCheckedChangeListener(listener);
        rollingAverageCheck.setOnCheckedChangeListener(listener);
    }

    private void vibrateHaptic() {
        Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    // --- DATA LOADING & PARSING ---

    private void loadDataAndInitializeUI() {
        new Thread(() -> {
            try {
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                String path = documentsDir.getPath() + "/RECORDINGS/Summary/Summary.csv";
                SummaryReader.setFilepath(path);

                SummaryReader sr = SummaryReader.getInstance();
                summaryTitles = sr.getSummaryTitles();
                summaryTuples = sr.getSummaryTuplesWithNoSkipItems();
                dateLabels = sr.getDateLabelsWithNoSkipItems();
                filterNames = sr.getFilterNames();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        buildFilterSwitches();
                        recalculateGraphs();
                    });
                }
            } catch (Exception e) {
                Log.e("SummaryChart", "Error loading data", e);
            }
        }).start();
    }

    // --- UI BUILDERS ---

    private void buildFilterSwitches() {
        leftCol.removeAllViews();
        rightCol.removeAllViews();

        for (int i = 0; i < filterNames.size(); i++) {
            LinearLayout col = (i % 2 == 0) ? leftCol : rightCol;
            col.addView(createFilterSwitchRow(filterNames.get(i)));
        }
    }

    private LinearLayout createFilterSwitchRow(String filterName) {
        Context ctx = requireContext();

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 8);

        // Tag to identify this row later
        row.setTag(filterName);

        CheckBox enableFilterCb = new CheckBox(ctx);
        enableFilterCb.setChecked(false);

        MaterialSwitch includeExcludeSwitch = new MaterialSwitch(ctx);
        includeExcludeSwitch.setChecked(true); // True = Include, False = Exclude
        includeExcludeSwitch.setEnabled(false);

        TextView label = new TextView(ctx);
        label.setTextSize(12);
        label.setText(filterName);
        label.setPadding(12, 0, 0, 0);

        // Listeners
        enableFilterCb.setOnCheckedChangeListener((btn, isChecked) -> {
            includeExcludeSwitch.setEnabled(isChecked);
            if (!isChecked) includeExcludeSwitch.setChecked(true);
            vibrateHaptic();
            recalculateGraphs();
        });

        includeExcludeSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            vibrateHaptic();
            recalculateGraphs();
        });

        row.addView(enableFilterCb);
        row.addView(includeExcludeSwitch);
        row.addView(label);

        return row;
    }

    // --- GRAPH RENDERING LOGIC ---

    public void recalculateGraphs() {
        if (summaryTitles == null) return;

        graphsHolder.removeAllViews();
        List<FilterState> activeFilters = getActiveFilters();

        boolean hasActiveFilters = !activeFilters.isEmpty();
        boolean drawBase = baseDrawCheck.isChecked();

        if (hasActiveFilters || drawBase) {
            noResultText.setVisibility(View.GONE);
            // Ignore the first (Date) and last (Mood, Info) columns
            for (int i = 1; i < summaryTitles.length - 2; i++) {
                renderMetricChart(i, activeFilters);
            }
        } else {
            noResultText.setVisibility(View.VISIBLE);
        }
    }

    private void renderMetricChart(int metricIndex, List<FilterState> activeFilters) {
        String metricName = summaryTitles[metricIndex];
        ArrayList<LineDataSet> datasets = new ArrayList<>();
        boolean isSeparateDraw = separateDrawCheck.isChecked();
        boolean useRollingAvg = rollingAverageCheck.isChecked();
        boolean drawBase = baseDrawCheck.isChecked();

        // 1. Draw Base (All Data)
        if (drawBase) {
            LineDataSet baseSet = extractDataset(metricIndex, metricName, null, getResources().getColor(R.color.material_blue_500, requireContext().getTheme()));
            if (baseSet.getEntryCount() > 0) datasets.add(baseSet);
        }

        // 2. Draw Filtered Data
        if (!activeFilters.isEmpty()) {
            if (isSeparateDraw) {
                // Draw a separate line for EACH active filter
                for (int i = 0; i < activeFilters.size(); i++) {
                    FilterState filter = activeFilters.get(i);
                    String label = (filter.isInclude ? "" : "NOT ") + filter.name;
                    int color = getResources().getColor(COLOR_ARRAY[i % COLOR_ARRAY.length], requireContext().getTheme());

                    LineDataSet filteredSet = extractDataset(metricIndex, label, Arrays.asList(filter), color);
                    if (filteredSet.getEntryCount() > 0) datasets.add(filteredSet);
                }
            } else {
                // Combine all active filters into ONE line
                LineDataSet combinedSet = extractDataset(metricIndex, "Filtered", activeFilters, getResources().getColor(R.color.material_orange_500, requireContext().getTheme()));
                if (combinedSet.getEntryCount() > 0) datasets.add(combinedSet);
            }
        }

        // 3. Apply Rolling Averages if requested
        if (useRollingAvg) {
            ArrayList<LineDataSet> rollingSets = new ArrayList<>();
            for (LineDataSet original : datasets) {
                List<Entry> rollingAvgEntries = calculateRollingAverage(original.getValues(), 14); // 14-day smoothing
                if (!rollingAvgEntries.isEmpty()) {
                    LineDataSet avgSet = new LineDataSet(rollingAvgEntries, original.getLabel() + " (Avg)");
                    avgSet.setColor(original.getColor());
                    avgSet.setLineWidth(1.6f);
                    avgSet.enableDashedLine(10f, 15f, 0f); // Make it dashed
                    avgSet.setDrawCircles(false);
                    avgSet.setDrawValues(false);
                    rollingSets.add(avgSet);
                }
            }
            datasets.addAll(rollingSets);
        }

        // 4. Render to UI if we have data
        if (!datasets.isEmpty()) {
            LineChart chart = buildStyledLineChart(datasets, isSeparateDraw);
            attachChartToCard(chart, metricName);
        }
    }

    // --- DATA EXTRACTION & MATH ---

    private LineDataSet extractDataset(int metricIndex, String label, @Nullable List<FilterState> filters, int color) {
        List<Entry> entries = new ArrayList<>();

        for (String[] row : summaryTuples) {
            if (filters == null || rowCompliesWithFilters(row, filters)) {
                int xIndex = dateLabels.indexOf(row[0]);
                if (xIndex != -1) {
                    try {
                        float value = Float.parseFloat(row[metricIndex].replace(",", "."));
                        entries.add(new Entry(xIndex, value));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(color);
        dataSet.setLineWidth(1f);
        dataSet.setCircleColor(color);
        dataSet.setCircleRadius(1.2f);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawValues(false);
        //dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // Smooth curves
        return dataSet;
    }

    private boolean rowCompliesWithFilters(String[] row, List<FilterState> filters) {
        String infoColumn = row[row.length - 1]; // Info is always last
        List<String> rowTags = Arrays.asList(infoColumn.split(","));

        for (FilterState filter : filters) {
            boolean hasTag = rowTags.contains(filter.name);
            if (filter.isInclude && !hasTag) return false;
            if (!filter.isInclude && hasTag) return false;
        }
        return true;
    }

    private List<Entry> calculateRollingAverage(List<Entry> data, int windowSize) {
        List<Entry> rollingAvgEntries = new ArrayList<>();
        if (data == null || data.isEmpty()) return rollingAvgEntries;

        data.sort((e1, e2) -> Float.compare(e1.getX(), e2.getX()));
        Queue<Float> window = new LinkedList<>();
        float sum = 0.0f;

        for (Entry currentEntry : data) {
            window.add(currentEntry.getY());
            sum += currentEntry.getY();

            if (window.size() > windowSize) {
                sum -= window.poll();
            }

            // Only plot the average if the window has gathered enough data
            if (window.size() >= windowSize / 2) {
                rollingAvgEntries.add(new Entry(currentEntry.getX(), sum / window.size()));
            }
        }
        return rollingAvgEntries;
    }

    // --- CHART STYLING ---

    private LineChart buildStyledLineChart(ArrayList<LineDataSet> dataSets, boolean showLegend) {
        LineChart chart = new LineChart(requireContext());
        chart.setMinimumHeight(600); // Taller charts for better visibility

        LineData lineData = new LineData();
        for (LineDataSet ds : dataSets) lineData.addDataSet(ds);
        chart.setData(lineData);

        // Styling
        int textColor = baseDrawCheck.getCurrentTextColor();

        chart.getDescription().setEnabled(false); // Hide the generic description text
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);

        chart.getLegend().setEnabled(showLegend);
        chart.getLegend().setTextColor(textColor);
        chart.getLegend().setWordWrapEnabled(true);

        // X-Axis (Dates)
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(textColor);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelRotationAngle(45f);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int index = (int) value;
                if (dateLabels != null && index >= 0 && index < dateLabels.size()) {
                    return dateLabels.get(index);
                }
                return "";
            }
        });

        // Y-Axis
        chart.getAxisRight().setEnabled(false); // Hide right axis
        YAxis yAxis = chart.getAxisLeft();
        yAxis.setTextColor(textColor);
        yAxis.setGridColor(Color.parseColor("#40808080")); // Very faint grid line
        yAxis.setAxisMinimum(0f); // Prevent charts from floating above zero

        chart.invalidate(); // Refresh
        return chart;
    }

    private void attachChartToCard(LineChart chart, String title) {
        Context ctx = requireContext();
        MaterialCardView card = new MaterialCardView(ctx);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(16, 16, 16, 24);
        card.setLayoutParams(params);
        card.setRadius(24);
        card.setCardElevation(6);
        card.setContentPadding(24, 32, 24, 24);

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, 24);

        layout.addView(titleView);
        layout.addView(chart);
        card.addView(layout);

        graphsHolder.addView(card);
    }

    // --- HELPER CLASSES & METHODS ---

    /**
     * Reads the UI state to determine which filters the user wants applied.
     */
    private List<FilterState> getActiveFilters() {
        List<FilterState> active = new ArrayList<>();

        // Helper to check a column
        checkColumnForFilters(leftCol, active);
        checkColumnForFilters(rightCol, active);

        return active;
    }

    private void checkColumnForFilters(LinearLayout column, List<FilterState> active) {
        for (int i = 0; i < column.getChildCount(); i++) {
            View row = column.getChildAt(i);
            if (row instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) row;
                CheckBox cb = (CheckBox) ll.getChildAt(0);
                MaterialSwitch sw = (MaterialSwitch) ll.getChildAt(1);

                if (cb.isChecked()) {
                    active.add(new FilterState((String) row.getTag(), sw.isChecked()));
                }
            }
        }
    }

    private static class FilterState {
        String name;
        boolean isInclude;
        FilterState(String name, boolean isInclude) {
            this.name = name;
            this.isInclude = isInclude;
        }
    }

    // --- DRAWER ANIMATION LOGIC (Unchanged math, cleaned up execution) ---

    private void setupDrag() {
        final GestureDetector gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                toggleCardHeight();
                return true;
            }
        });

        topHandle.setOnTouchListener(new View.OnTouchListener() {
            private float initialY;
            private int initialHeight;
            private boolean isResizing;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (gestureDetector.onTouchEvent(event)) return true;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isResizing = true;
                        initialY = event.getRawY();
                        initialHeight = bottomDrawerView.getHeight();
                        if (mainLayout != null) mainLayout.setMotionEventSplittingEnabled(false);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (isResizing) {
                            float dy = event.getRawY() - initialY;
                            int newHeight = Math.max(dpToPx(collapsedHeightDp), (int) (initialHeight - dy));
                            if (mainLayout != null) newHeight = Math.min(mainLayout.getHeight(), newHeight);

                            bottomDrawerView.getLayoutParams().height = newHeight;
                            bottomDrawerView.requestLayout();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isResizing = false;
                        if (mainLayout != null) mainLayout.setMotionEventSplittingEnabled(true);
                        return true;
                }
                return false;
            }
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getCollapsedHeight() {
        View checkboxes = bottomDrawerView.findViewById(R.id.basedrawcheckbox);
        if (checkboxes != null && checkboxes.getParent() instanceof View) {
            View parent = (View) checkboxes.getParent();
            return parent.getMeasuredHeight() + parent.getPaddingBottom() - 20;
        }
        return 50;
    }

    private void toggleCardHeight() {
        int targetHeight = isExpanded ? dpToPx(collapsedHeightDp) : getExpandedHeightCapped();
        ValueAnimator animator = ValueAnimator.ofInt(bottomDrawerView.getHeight(), targetHeight);
        animator.setDuration(250);
        animator.addUpdateListener(animation -> {
            bottomDrawerView.getLayoutParams().height = (int) animation.getAnimatedValue();
            bottomDrawerView.requestLayout();
        });
        animator.start();
        isExpanded = !isExpanded;
    }

    private int getExpandedHeightCapped() {
        View scrollContent = bottomDrawerView.findViewById(R.id.scroll_filters);
        if (scrollContent == null) return dpToPx(300);

        scrollContent.measure(
                View.MeasureSpec.makeMeasureSpec(bottomDrawerView.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED
        );
        int contentHeight = scrollContent.getMeasuredHeight() + dpToPx(100);

        int maxHeight = (int) (Resources.getSystem().getDisplayMetrics().heightPixels * 0.8);
        return Math.min(contentHeight, maxHeight);
    }
}