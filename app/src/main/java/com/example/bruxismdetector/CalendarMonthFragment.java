package com.example.bruxismdetector;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bruxismdetector.bruxism_grapher2.CorrelationsCalculator;
import com.example.bruxismdetector.bruxism_grapher2.SummaryReader;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class CalendarMonthFragment extends Fragment {
    private static final String ARG_YEAR = "arg_year";
    private static final String ARG_MONTH = "arg_month";

    private int year;
    private int month;
    private final List<DayCellBinding> dayBindings = new ArrayList<>();

    private int colorOnSurface, colorOnSurfaceVariant, colorSurface, colorOutline, colorPrimary;

    public static CalendarMonthFragment newInstance(int year, int month) {
        Bundle args = new Bundle();
        args.putInt(ARG_YEAR, year);
        args.putInt(ARG_MONTH, month);
        CalendarMonthFragment fragment = new CalendarMonthFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private static class DayCellBinding {
        View cellView;
        TextView dayNumText;
        TextView metricValueText;
        SummaryReader.SummaryEntry entry;
        boolean isTrackingSession;
        boolean hasTags;

        DayCellBinding(View cellView, TextView dayNumText, TextView metricValueText, SummaryReader.SummaryEntry entry, boolean isTrackingSession, boolean hasTags) {
            this.cellView = cellView;
            this.dayNumText = dayNumText;
            this.metricValueText = metricValueText;
            this.entry = entry;
            this.isTrackingSession = isTrackingSession;
            this.hasTags = hasTags;
        }

        float getMetric(int tupleIndex) {
            if (entry == null || entry.tuple == null || tupleIndex < 0 || tupleIndex >= entry.tuple.length) return Float.NaN;
            try { return Float.parseFloat(entry.tuple[tupleIndex].replace(",", ".")); }
            catch (Exception e) { return Float.NaN; }
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            year = getArguments().getInt(ARG_YEAR);
            month = getArguments().getInt(ARG_MONTH);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar_month, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        colorOnSurface = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface);
        colorOnSurfaceVariant = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant);
        colorSurface = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface);
        colorOutline = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOutlineVariant);
        colorPrimary = MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimary);

        GridLayout grid = view.findViewById(R.id.grid_days);
        ChipGroup legendGroup = view.findViewById(R.id.legend_chip_group);
        grid.removeAllViews();
        legendGroup.removeAllViews();
        dayBindings.clear();

        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();
        int dayOfWeekFirst = ym.atDay(1).getDayOfWeek().getValue();

        SummaryReader reader = SummaryReader.getInstance();
        SummaryReader.SummaryMonth sm = reader.getSummaryMonths().stream()
                .filter(m -> m.getYear() == year && m.getMonth() == month)
                .findFirst().orElse(null);
        List<SummaryReader.SummaryEntry> entries = sm != null ? sm.tuples : new ArrayList<>();
        int infoIndex = reader.getInfomationIndex();

        // 1. Build Legend Data
        Map<String, Integer> freq = new HashMap<>();
        for (SummaryReader.SummaryEntry entry : entries) {
            if (entry.tuple[infoIndex] == null) continue;
            for (String info : entry.tuple[infoIndex].split(",")) {
                if (!info.trim().isEmpty()) freq.put(info.trim(), freq.getOrDefault(info.trim(), 0) + 1);
            }
        }
        List<String> sortedTags = freq.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 2. Weekday Headers
        java.time.DayOfWeek startDay = java.time.DayOfWeek.MONDAY;
        for (int i = 0; i < 7; i++) {
            String dayName = startDay.plus(i).getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase();
            TextView header = new TextView(getContext());
            header.setText(dayName);
            header.setGravity(Gravity.CENTER);
            header.setTextSize(10);
            header.setTextColor(colorOnSurfaceVariant);
            header.setAlpha(0.6f);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams(GridLayout.spec(0), GridLayout.spec(i, 1f));
            p.width = 0;
            p.setMargins(0, 0, 0, 8);
            header.setLayoutParams(p);
            grid.addView(header);
        }

        // 3. Fillers (Day 1 offset)
        int offset = dayOfWeekFirst - 1; 
        for (int i = 0; i < offset; i++) {
            View filler = new View(getContext());
            GridLayout.LayoutParams p = new GridLayout.LayoutParams(GridLayout.spec(1), GridLayout.spec(i, 1f));
            p.width = 0; p.height = 0;
            filler.setLayoutParams(p);
            grid.addView(filler);
        }

        // 4. Days
        for (int d = 1; d <= daysInMonth; d++) {
            final int day = d;
            int row = (d + offset - 1) / 7 + 1;
            int col = (d + offset - 1) % 7;

            View cell = LayoutInflater.from(getContext()).inflate(R.layout.item_day_cell, grid, false);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(row), GridLayout.spec(col, 1f));
            lp.width = 0;
            lp.height = dpToPx(64);
            lp.setMargins(2, 2, 2, 2);
            cell.setLayoutParams(lp);

            TextView txtDay = cell.findViewById(R.id.text_day_number);
            TextView txtMetric = cell.findViewById(R.id.text_metric_value);
            LinearLayout dotsContainer = cell.findViewById(R.id.layout_tag_dots);

            txtDay.setText(String.valueOf(day));
            SummaryReader.SummaryEntry matched = entries.stream().filter(e -> e.day == day).findFirst().orElse(null);
            boolean isTracking = matched != null && !matched.should_skip;
            boolean hasTags = false;

            if (matched != null && matched.tuple[infoIndex] != null) {
                String[] tags = matched.tuple[infoIndex].split(",");
                int tagCount = 0;
                for (String t : tags) {
                    if (t.trim().isEmpty() || tagCount >= 5) continue;
                    hasTags = true;
                    ImageView icon = new ImageView(getContext());
                    int size = dpToPx(10);
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(size, size);
                    iconLp.setMargins(1, 0, 1, 0);
                    icon.setLayoutParams(iconLp);
                    icon.setImageDrawable(CalendarPalette.getTagIcon(requireContext(), t));
                    dotsContainer.addView(icon);
                    tagCount++;
                }
            }

            dayBindings.add(new DayCellBinding(cell, txtDay, txtMetric, matched, isTracking, hasTags));
            final SummaryReader.SummaryEntry finalEntry = matched;
            cell.setOnClickListener(v -> showDayDetailSheet(day, finalEntry));
            grid.addView(cell);
        }

        updateMetricVisuals(CalendarViewer.getSelectedVariable_TupleIndex());

        // 5. Legend
        for (String tag : sortedTags) {
            String displayName = tag.contains(": ") ? tag.split(": ")[1] : tag;
            Chip chip = new Chip(requireContext());
            chip.setText(String.format(Locale.US, "%s (%d)", displayName, freq.get(tag)));
            chip.setChipIcon(CalendarPalette.getTagIcon(requireContext(), tag));
            chip.setChipIconVisible(true);
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setTextSize(9);
            chip.setChipMinHeight(dpToPx(24));
            legendGroup.addView(chip);
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    public void updateMetricVisuals(int selectedTupleIndex) {
        if (dayBindings.isEmpty() || getContext() == null) return;

        SummaryReader sr = SummaryReader.getInstance();
        boolean hasMetric = selectedTupleIndex > 0 && selectedTupleIndex < sr.getSummaryTitles().length;

        float globalMin = 0, globalMax = 1;
        byte direction = CorrelationsCalculator.NeutralCorr; // Default to neutral

        if (hasMetric) {
            float[] minMax = CalendarHighlighter.getGlobalMinMax(selectedTupleIndex, sr.getSummaryTuplesWithNoSkipItems());
            globalMin = minMax[0];
            globalMax = minMax[1];

            // Ask the SSOT what kind of metric this is
            direction = CorrelationsCalculator.isGoingToBetter(1.0, sr.getSummaryTitles()[selectedTupleIndex]);
        }

        for (DayCellBinding b : dayBindings) {
            if (!b.isTrackingSession) {
                int bgColor = b.hasTags ? MaterialColors.layer(colorSurface, colorPrimary, 0.12f) : Color.TRANSPARENT;
                setCellTile(b.cellView, bgColor, b.hasTags ? colorOutline : Color.TRANSPARENT);
                b.dayNumText.setTextColor(colorOnSurface);
                b.dayNumText.setAlpha(b.hasTags ? 0.9f : 0.25f);
                b.metricValueText.setVisibility(View.GONE);
                continue;
            }

            b.dayNumText.setAlpha(1.0f);
            if (!hasMetric) {
                setCellTile(b.cellView, colorSurface, colorOutline);
                b.dayNumText.setTextColor(colorOnSurface);
                b.metricValueText.setVisibility(View.GONE);
            } else {
                float val = b.getMetric(selectedTupleIndex);
                if (Float.isNaN(val)) {
                    setCellTile(b.cellView, colorSurface, colorOutline);
                    b.metricValueText.setText("-");
                    b.metricValueText.setVisibility(View.VISIBLE);
                    b.dayNumText.setTextColor(colorOnSurface);
                } else {

                    // Pass the 'direction' byte instead of a boolean
                    int bgColor = CalendarHighlighter.getGradientColor(val, globalMin, globalMax, direction);

                    setCellTile(b.cellView, bgColor, colorOutline);

                    if (isColorDark(bgColor)) {
                        b.dayNumText.setTextColor(Color.WHITE);
                        b.metricValueText.setTextColor(Color.WHITE);
                    } else {
                        b.dayNumText.setTextColor(Color.BLACK);
                        b.metricValueText.setTextColor(Color.BLACK);
                    }

                    b.metricValueText.setText(val >= 100 ? String.format(Locale.US, "%.0f", val) : String.format(Locale.US, "%.1f", val));
                    b.metricValueText.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    private void setCellTile(View cell, int fillColor, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dpToPx(12));
        gd.setColor(fillColor);
        if (strokeColor != Color.TRANSPARENT) gd.setStroke(dpToPx(1), strokeColor);
        cell.setBackground(gd);
    }

    private void showDayDetailSheet(int day, @Nullable SummaryReader.SummaryEntry entry) {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(getContext()).inflate(R.layout.dialog_day_summary, null);

        TextView title = sheet.findViewById(R.id.sheet_date_title);
        TextView statsText = sheet.findViewById(R.id.sheet_stats_summary);
        ChipGroup chipGroup = sheet.findViewById(R.id.sheet_chip_group);

        String monthName = YearMonth.of(year, month).getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
        title.setText(String.format(Locale.getDefault(), "%d %s %d", day, monthName, year));

        if (entry == null) {
            statsText.setText("No data recorded for this day.");
        } else {
            if (entry.should_skip) {
                statsText.setText("No monitoring session recorded.");
            } else {
                String[] t = entry.tuple;
                statsText.setText(String.format(Locale.US,
                        "• Duration: %s hrs\n• Jaw Events: %s\n• Clenching Rate: %s /hr\n• Total Clench: %s sec\n• Alarms: %s (%s%%)",
                        t[1], t[9], t[4], t[2], t[11], t[13]));
            }

            int infoIndex = SummaryReader.getInstance().getInfomationIndex();
            if (entry.tuple[infoIndex] != null && !entry.tuple[infoIndex].trim().isEmpty()) {
                for (String tag : entry.tuple[infoIndex].split(",")) {
                    if (tag.trim().isEmpty()) continue;
                    Chip chip = new Chip(requireContext());
                    chip.setText(tag.trim());

                    chip.setChipBackgroundColorResource(android.R.color.transparent);
                    chip.setChipStrokeColorResource(android.R.color.darker_gray);
                    chip.setChipStrokeWidth(1f);
                    chip.setChipIcon(CalendarPalette.getTagIcon(requireContext(), tag));
                    chipGroup.addView(chip);
                }
            }
        }
        dialog.setContentView(sheet);
        dialog.show();
    }
}
