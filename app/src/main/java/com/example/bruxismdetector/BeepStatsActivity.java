package com.example.bruxismdetector;

import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.color.DynamicColors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

public class BeepStatsActivity extends AppCompatActivity {

    private BeepDatabaseHelper dbHelper;
    private TextView textResponsesSummary, textResponseRates, textWithBeep, textWithoutBeep, textAvgDelay;
    private LineChart chartTimelineResponseRates, chartTimelineYes, chartTimelineNo;
    private BarChart chartHourlyYesAll, chartHourlyYesTone, chartHourlyYesNoTone;

    // Risolviamo i colori dal tema Material You
    int colorAll, colorTone, colorNoTone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivitiesIfAvailable(this.getApplication());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beep_stats);


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_scrollview), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        dbHelper = new BeepDatabaseHelper(this);

        textResponsesSummary = findViewById(R.id.text_responses_summary);
        textResponseRates = findViewById(R.id.text_response_rates);
        textWithBeep = findViewById(R.id.text_with_beep);
        textWithoutBeep = findViewById(R.id.text_without_beep);
        textAvgDelay = findViewById(R.id.text_avg_delay);

        chartTimelineResponseRates = findViewById(R.id.chart_timeline_response_rates);
        chartTimelineYes = findViewById(R.id.chart_timeline_yes);
        chartTimelineNo = findViewById(R.id.chart_timeline_no);
        chartHourlyYesAll = findViewById(R.id.chart_hourly_yes_all);
        chartHourlyYesTone = findViewById(R.id.chart_hourly_yes_tone);
        chartHourlyYesNoTone = findViewById(R.id.chart_hourly_yes_notone);

        // Risolviamo i colori dal tema Material You
        colorAll = getTextColor(); // Colore neutro (testo)
        colorTone = getThemeColor(com.google.android.material.R.attr.colorPrimary);
        colorNoTone = getThemeColor(com.google.android.material.R.attr.colorTertiary);

        loadDataAndPopulate();
    }

    private void loadDataAndPopulate() {
        Cursor cursor = dbHelper.getAllBeeps();
        if (cursor == null) return;

        int totalEvents = 0, totalResponded = 0;
        int withBeepTotal = 0, withBeepYes = 0, withBeepNo = 0, withBeepIgnored = 0, withBeepUnanswered = 0;
        int noBeepTotal = 0, noBeepYes = 0, noBeepNo = 0, noBeepIgnored = 0, noBeepUnanswered = 0;
        long totalDelayMs = 0;
        int respondedWithDelayCount = 0;

        TreeMap<String, DayStats> dailyStats = new TreeMap<>();
        HourlyStats[] hourlyStats = new HourlyStats[24];
        for (int i = 0; i < 24; i++) hourlyStats[i] = new HourlyStats();

        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();

        while (cursor.moveToNext()) {
            totalEvents++;
            long tsEvent = cursor.getLong(cursor.getColumnIndexOrThrow(BeepDatabaseHelper.COLUMN_TIMESTAMP_EVENT));
            long tsResponse = cursor.getLong(cursor.getColumnIndexOrThrow(BeepDatabaseHelper.COLUMN_TIMESTAMP_RESPONSE));
            boolean beep = cursor.getInt(cursor.getColumnIndexOrThrow(BeepDatabaseHelper.COLUMN_BEEP)) == 1;
            int response = cursor.getInt(cursor.getColumnIndexOrThrow(BeepDatabaseHelper.COLUMN_RESPONSE));

            String dayKey = dayFormat.format(new Date(tsEvent));
            DayStats dStats = dailyStats.computeIfAbsent(dayKey, k -> new DayStats());

            cal.setTimeInMillis(tsEvent);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            HourlyStats hStats = hourlyStats[hour];

            boolean responded = (response != 0);
            if (responded) {
                totalResponded++;
                if (tsResponse > 0) {
                    totalDelayMs += (tsResponse - tsEvent);
                    respondedWithDelayCount++;
                }
            }

            if (beep) {
                withBeepTotal++; dStats.beepTotal++; hStats.beepTotal++;
                if (responded) { dStats.beepResponded++; hStats.beepResponded++; }
                switch (response) {
                    case 0: withBeepUnanswered++; break;
                    case 1: withBeepYes++; dStats.beepYes++; hStats.beepYes++; hStats.allYes++; break;
                    case 2: withBeepNo++; dStats.beepNo++; hStats.beepNo++; break;
                    case 3: withBeepIgnored++; break;
                }
            } else {
                noBeepTotal++; dStats.noBeepTotal++; hStats.noBeepTotal++;
                if (responded) { dStats.noBeepResponded++; hStats.noBeepResponded++; }
                switch (response) {
                    case 0: noBeepUnanswered++; break;
                    case 1: noBeepYes++; dStats.noBeepYes++; hStats.noBeepYes++; hStats.allYes++; break;
                    case 2: noBeepNo++; dStats.noBeepNo++; hStats.noBeepNo++; break;
                    case 3: noBeepIgnored++; break;
                }
            }
            hStats.allTotal++;
        }
        cursor.close();

        updateSection1(totalResponded, totalEvents, withBeepTotal, withBeepUnanswered, noBeepTotal, noBeepUnanswered, withBeepYes, withBeepNo, withBeepIgnored, noBeepYes, noBeepNo, noBeepIgnored, respondedWithDelayCount, totalDelayMs);
        setupTimelineCharts(dailyStats);
        setupHourlyChart(hourlyStats);
    }

    private void updateSection1(int totalResponded, int totalEvents, int withBeepTotal, int withBeepUnanswered, int noBeepTotal, int noBeepUnanswered, int withBeepYes, int withBeepNo, int withBeepIgnored, int noBeepYes, int noBeepNo, int noBeepIgnored, int respondedWithDelayCount, long totalDelayMs) {
        double respPerc = totalEvents > 0 ? (totalResponded * 100.0 / totalEvents) : 0;
        textResponsesSummary.setText(String.format(Locale.US, "Responses: %d/%d (%.1f%%)", totalResponded, totalEvents, respPerc));
        double rateWithTone = withBeepTotal > 0 ? ((withBeepTotal - withBeepUnanswered) * 100.0 / withBeepTotal) : 0;
        double rateNoTone = noBeepTotal > 0 ? ((noBeepTotal - noBeepUnanswered) * 100.0 / noBeepTotal) : 0;
        textResponseRates.setText(String.format(Locale.US, "Response rate with tone: %.1f%%\nResponse rate without tone: %.1f%%", rateWithTone, rateNoTone));
        textWithBeep.setText(String.format(Locale.US, "With Beep:\nYes: %d, No: %d, Ignored: %d, Unanswered: %d", withBeepYes, withBeepNo, withBeepIgnored, withBeepUnanswered));
        textWithoutBeep.setText(String.format(Locale.US, "Without Beep:\nYes: %d, No: %d, Ignored: %d, Unanswered: %d", noBeepYes, noBeepNo, noBeepIgnored, noBeepUnanswered));
        double avgDelay = respondedWithDelayCount > 0 ? (totalDelayMs / 1000.0 / respondedWithDelayCount) : 0;
        textAvgDelay.setText(String.format(Locale.US, "Average response delay: %.1fs", avgDelay));
    }

    private void setupTimelineCharts(TreeMap<String, DayStats> dailyStats) {
        List<String> days = new ArrayList<>(dailyStats.keySet());
        List<Entry> rateWithBeepEntries = new ArrayList<>(), rateNoBeepEntries = new ArrayList<>();
        List<Entry> yesWithBeepEntries = new ArrayList<>(), yesNoBeepEntries = new ArrayList<>();
        List<Entry> noWithBeepEntries = new ArrayList<>(), noNoBeepEntries = new ArrayList<>();

        for (int i = 0; i < days.size(); i++) {
            DayStats s = dailyStats.get(days.get(i));
            if (s.beepTotal > 0) {
                rateWithBeepEntries.add(new Entry(i, (float) (s.beepResponded * 100.0 / s.beepTotal)));
                yesWithBeepEntries.add(new Entry(i, (float) (s.beepYes * 100.0 / s.beepTotal)));
                noWithBeepEntries.add(new Entry(i, (float) (s.beepNo * 100.0 / s.beepTotal)));
            }
            if (s.noBeepTotal > 0) {
                rateNoBeepEntries.add(new Entry(i, (float) (s.noBeepResponded * 100.0 / s.noBeepTotal)));
                yesNoBeepEntries.add(new Entry(i, (float) (s.noBeepYes * 100.0 / s.noBeepTotal)));
                noNoBeepEntries.add(new Entry(i, (float) (s.noBeepNo * 100.0 / s.noBeepTotal)));
            }
        }
        configureLineChart(chartTimelineResponseRates, days, "With Tone", rateWithBeepEntries, "No Tone", rateNoBeepEntries);
        configureLineChart(chartTimelineYes, days, "Yes (Tone)", yesWithBeepEntries, "Yes (No Tone)", yesNoBeepEntries);
        configureLineChart(chartTimelineNo, days, "No (Tone)", noWithBeepEntries, "No (No Tone)", noNoBeepEntries);
    }

    private void configureLineChart(LineChart chart, List<String> xLabels, String label1, List<Entry> entries1, String label2, List<Entry> entries2) {
        LineDataSet ds1 = new LineDataSet(entries1, label1); ds1.setColor(colorTone); ds1.setCircleColor(colorTone);
        LineDataSet ds2 = new LineDataSet(entries2, label2); ds2.setColor(colorNoTone); ds2.setCircleColor(colorNoTone);

        // Disable labels
        ds1.setDrawValues(false);
        ds2.setDrawValues(false);

        ds1.setValueTextColor(getTextColor());
        ds2.setValueTextColor(getTextColor());

        chart.setData(new LineData(ds1, ds2));
        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));
        xAxis.setTextColor(getTextColor());
        xAxis.setGranularity(1f); xAxis.setPosition(XAxis.XAxisPosition.BOTTOM); xAxis.setLabelRotationAngle(-45);
        chart.getAxisRight().setEnabled(false); chart.getDescription().setEnabled(false);

        chart.setNoDataTextColor(getTextColor());
        chart.getAxisLeft().setTextColor(getTextColor());
        chart.getLegend().setTextColor(getTextColor());

        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(100f); // Un po' di padding extra per le etichette sopra le barre

        chart.invalidate();
    }

    private void setupHourlyChart(HourlyStats[] stats) {
        List<BarEntry> allEntries = new ArrayList<>(), beepEntries = new ArrayList<>(), noBeepEntries = new ArrayList<>();
        for (int h = 8; h <= 19; h++) {
            HourlyStats s = stats[h];
            float x = h;
            allEntries.add(new BarEntry(x, s.allTotal > 0 ? (float)(s.allYes * 100.0 / s.allTotal) : 0));
            beepEntries.add(new BarEntry(x, s.beepTotal > 0 ? (float)(s.beepYes * 100.0 / s.beepTotal) : 0));
            noBeepEntries.add(new BarEntry(x, s.noBeepTotal > 0 ? (float)(s.noBeepYes * 100.0 / s.noBeepTotal) : 0));
        }

        configureBarChart(chartHourlyYesAll, allEntries, "Avg Yes (All)", colorAll);
        configureBarChart(chartHourlyYesTone, beepEntries, "Avg Yes (Tone)", colorTone);
        configureBarChart(chartHourlyYesNoTone, noBeepEntries, "Avg Yes (No Tone)", colorNoTone);
    }

    void configureBarChart(BarChart bc, List<BarEntry> entries, String title, int color){
        BarDataSet ds = new BarDataSet(entries, title);
        ds.setColor(color);
        ds.setValueTextColor(getTextColor());
        ds.setValueTextSize(7f);

        // Formattatore per mostrare la percentuale sopra le barre
        ds.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value > 0 ? String.format(Locale.US, "%.0f%%", value) : "";
            }
        });

        BarData data = new BarData(ds);
        data.setBarWidth(0.6f); // Barre più larghe per riempire meglio lo spazio
        bc.setData(data);

        // Configurazione XAxis
        XAxis xAxis = bc.getXAxis();
        xAxis.setGranularity(1f);
        xAxis.setCenterAxisLabels(false);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setAxisMinimum(7.5f);
        xAxis.setAxisMaximum(19.5f);
        xAxis.setTextColor(getTextColor());
        xAxis.setDrawGridLines(false); // Pulizia visiva
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int)value); //+ ":00";
            }
        });

        // Configurazione YAxis (Sinistra) - Fissa 0-100%
        bc.getAxisLeft().setAxisMinimum(0f);
        bc.getAxisLeft().setAxisMaximum(115f); // Un po' di padding extra per le etichette sopra le barre
        bc.getAxisLeft().setTextColor(getTextColor());
        bc.getAxisLeft().setDrawGridLines(true);
        bc.getAxisLeft().setGridColor(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)));
        bc.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return (int)value + "%";
            }
        });

        bc.getAxisRight().setEnabled(false);
        bc.getDescription().setEnabled(false);
        bc.setNoDataTextColor(getTextColor());
        bc.getLegend().setTextColor(getTextColor());
        bc.setExtraOffsets(0, 0, 0, 10); // Spazio per le label in basso

        bc.animateY(1000); // Aggiungiamo una piccola animazione all'apertura
        bc.invalidate();
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    private int getTextColor(){
        return textResponsesSummary.getCurrentTextColor();
    }

    private static class DayStats { int beepTotal=0, noBeepTotal=0, beepResponded=0, noBeepResponded=0, beepYes=0, noBeepYes=0, beepNo=0, noBeepNo=0; }
    private static class HourlyStats { int allTotal=0, allYes=0, beepTotal=0, beepResponded=0, beepYes=0, noBeepTotal=0, noBeepResponded=0, noBeepYes=0, beepNo=0, noBeepNo=0; }
}