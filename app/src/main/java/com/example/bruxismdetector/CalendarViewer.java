package com.example.bruxismdetector;

import android.graphics.Color;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;
import androidx.viewpager2.widget.ViewPager2;

import com.example.bruxismdetector.bruxism_grapher2.SummaryReader;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarViewer extends AppCompatActivity {
    private ViewPager2 viewPager;
    private CalendarPagerAdapter pagerAdapter;
    private AutoCompleteTextView variablePicker;
    private TextView textMonthYear;
    private TextView textStatsContent;
    private ImageButton btnPrev, btnNext;

    private static int selectedVariableTupleIndex = 0;

    private boolean isListView = false;
    private RecyclerView listView;
    private SessionGridAdapter gridAdapter;

    MaterialCardView statistics_section;

    private RecyclerView pickerRecyclerView;
    private MetricPickerAdapter pickerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_calendar);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_calendar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewPager = findViewById(R.id.calendar_viewpager);
        pickerRecyclerView = findViewById(R.id.picker_recycler_view);
        textMonthYear = findViewById(R.id.text_month_year);
        textStatsContent = findViewById(R.id.text_statistics_content);
        btnPrev = findViewById(R.id.button_prev_month);
        btnNext = findViewById(R.id.button_next_month);

        statistics_section = findViewById(R.id.statistics_section);



        SummaryReader reader = SummaryReader.getInstance();
        reader.populateMonthsArray();
        List<SummaryReader.SummaryMonth> months = reader.getSummaryMonths();
        String[] titles = reader.getSummaryTitles();


        ImageButton btnToggleView = findViewById(R.id.button_toggle_view);
        listView = findViewById(R.id.calendar_list_view);
        listView.setLayoutManager(new GridLayoutManager(this, 7));

        // Flatten all months into one continuous list for the RecyclerView
        List<SummaryReader.SummaryEntry> allSessionsFlat = new ArrayList<>();
        for (SummaryReader.SummaryMonth sm : months) {
            allSessionsFlat.addAll(sm.tuples);
        }

        // Initialize the new Grid Adapter and pass the click listener
        gridAdapter = new SessionGridAdapter(allSessionsFlat, selectedVariableTupleIndex, entry -> {
            LocalDate date = LocalDate.parse(entry.tuple[0], DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            showDayDetailSheet(date, entry);
        });listView.setAdapter(gridAdapter);

        ViewGroup rootLayout = findViewById(R.id.root_calendar);
        // --- Toggle View Logic WITH ANIMATION - ---
        btnToggleView.setOnClickListener(v -> {

            // This single line automatically cross-fades and translates UI changes!
            TransitionManager.beginDelayedTransition(rootLayout, new AutoTransition());

            isListView = !isListView;
            if (isListView) {
                // Switch to List View
                viewPager.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
                btnPrev.setVisibility(View.INVISIBLE);
                btnNext.setVisibility(View.INVISIBLE);
                statistics_section.setVisibility(View.GONE);

                textMonthYear.setText("All Tracked Sessions");
                btnToggleView.setImageResource(R.drawable.calendar_month); // Calendar Icon

                // SMART SCROLL: Jump to the month that was currently shown in the ViewPager
                SummaryReader.SummaryMonth currentMonth = months.get(viewPager.getCurrentItem());
                String targetPrefix = String.format(Locale.US, "%04d-%02d", currentMonth.getYear(), currentMonth.getMonth());

                int targetIndex = -1;
                List<SummaryReader.SummaryEntry> validList = gridAdapter.getValidSessions();
                for (int i = 0; i < validList.size(); i++) {
                    if (validList.get(i).tuple[0].startsWith(targetPrefix)) {
                        targetIndex = i;
                        break;
                    }
                }

                if (targetIndex != -1) {
                    // Scroll exactly to the first session of that month
                    listView.scrollToPosition(targetIndex);
                } else if (!validList.isEmpty()) {
                    // Fallback: If that month has no data, scroll to the most recent session at the bottom
                    listView.scrollToPosition(validList.size() - 1);
                }

            } else {
                // Switch to Calendar Grid
                viewPager.setVisibility(View.VISIBLE);
                listView.setVisibility(View.GONE);
                btnPrev.setVisibility(View.VISIBLE);
                btnNext.setVisibility(View.VISIBLE);
                statistics_section.setVisibility(View.VISIBLE);

                updateMonthLabel(viewPager.getCurrentItem()); // Restore month text
                btnToggleView.setImageResource(R.drawable.grid); // List Icon
            }
        });

        // --- Setup the Snapping Metric Picker ---
        List<String> pickerItems = new ArrayList<>();
        pickerItems.add("No Heatmap");
        for (int i = 1; i < titles.length - 2; i++) {
            pickerItems.add(titles[i]);
        }

        LinearLayoutManager pickerLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        pickerRecyclerView.setLayoutManager(pickerLayoutManager);

        pickerAdapter = new MetricPickerAdapter(pickerItems, position -> {
            // Allow users to tap side items to snap them to the center
            pickerRecyclerView.smoothScrollToPosition(position);
        });
        pickerRecyclerView.setAdapter(pickerAdapter);

        // Snap items to the center
        SnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(pickerRecyclerView);

        // Add padding to ends so the first and last items can reach the exact center of the screen
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int padding = screenWidth / 2 - (int)(getResources().getDisplayMetrics().density * 60); // Approx half item width
        pickerRecyclerView.setPadding(padding, 0, padding, 0);

        // Detect when scroll stops to update the actual calendar heatmap
        pickerRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View centerView = snapHelper.findSnapView(pickerLayoutManager);
                    if (centerView != null) {
                        int pos = pickerLayoutManager.getPosition(centerView);
                        if (pos != selectedVariableTupleIndex) {
                            selectedVariableTupleIndex = pos;
                            pickerAdapter.setSelectedPosition(pos);
                            triggerMetricUpdate(pos);
                        }
                    }
                }
            }
        });


        // 2. Setup ViewPager
        int currentMonthIndex = 0;
        YearMonth today = YearMonth.now();
        for (int i = 0; i < months.size(); i++) {
            SummaryReader.SummaryMonth sm = months.get(i);
            if (sm.getYear() == today.getYear() && sm.getMonth() == today.getMonthValue()) {
                currentMonthIndex = i;
                break;
            }
        }

        pagerAdapter = new CalendarPagerAdapter(this, months);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setCurrentItem(currentMonthIndex, false);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateMonthLabel(position);
                // Force the newly scrolled month to apply the current metric
                Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + position);
                if (currentFragment instanceof CalendarMonthFragment) {
                    ((CalendarMonthFragment) currentFragment).updateMetricVisuals(selectedVariableTupleIndex);
                }
            }
        });

        // 3. Navigation
        btnPrev.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() > 0) viewPager.setCurrentItem(viewPager.getCurrentItem() - 1, true);
        });
        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() < months.size() - 1) viewPager.setCurrentItem(viewPager.getCurrentItem() + 1, true);
        });

        updateMonthLabel(currentMonthIndex);

        // FORCE THE INITIAL TILE DRAW
        viewPager.post(() -> {
            Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
            if (currentFragment instanceof CalendarMonthFragment) {
                ((CalendarMonthFragment) currentFragment).updateMetricVisuals(selectedVariableTupleIndex);
            }
        });
    }

    private void updateMonthLabel(int position) {
        List<SummaryReader.SummaryMonth> months = SummaryReader.getInstance().getSummaryMonths();
        if (position < 0 || position >= months.size()) return;
        SummaryReader.SummaryMonth sm = months.get(position);

        String label = Month.of(sm.month).getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + sm.getYear();
        textMonthYear.setText(label.toUpperCase());

        updateMonthlyStats(sm);
    }

    private void updateMonthlyStats(SummaryReader.SummaryMonth sm) {
        int totalDaysInMonth = YearMonth.of(sm.getYear(), sm.getMonth()).lengthOfMonth();
        int sessionsCount = 0;
        double totalHours = 0;
        double totalClenchSec = 0;
        double sumClenchRate = 0;
        int clenchRateCount = 0;
        Map<String, Integer> tagFreq = new HashMap<>();
        int infoIndex = SummaryReader.getInstance().getInfomationIndex();

        for (SummaryReader.SummaryEntry entry : sm.tuples) {
            // Count tags
            if (entry.tuple[infoIndex] != null) {
                for (String tag : entry.tuple[infoIndex].split(",")) {
                    String clean = tag.trim();
                    if (!clean.isEmpty()) tagFreq.put(clean, tagFreq.getOrDefault(clean, 0) + 1);
                }
            }

            if (entry.should_skip) continue;
            sessionsCount++;

            try {
                // tuple indices: 1=Duration, 2=Total Clench Sec, 4=Clench Rate/hr
                totalHours += Double.parseDouble(entry.tuple[1].replace(",", "."));
                totalClenchSec += Double.parseDouble(entry.tuple[2].replace(",", "."));
                float rate = Float.parseFloat(entry.tuple[4].replace(",", "."));
                sumClenchRate += rate;
                clenchRateCount++;
            } catch (Exception ignored) {}
        }

        StringBuilder sb = new StringBuilder();
        double trackPercent = (double) sessionsCount / totalDaysInMonth * 100;
        sb.append(String.format(Locale.US, "Tracked: %d/%d days (%.0f%%)", sessionsCount, totalDaysInMonth, trackPercent));

        if (sessionsCount > 0) {
            sb.append(String.format(Locale.US, "\nAvg session: %.1f hrs  •  Avg Clench: %.0f sec", totalHours / clenchRateCount, totalClenchSec / clenchRateCount));
            if (clenchRateCount > 0) {
                sb.append(String.format(Locale.US, "\nAvg Clenching Rate: %.2f /hr", sumClenchRate / clenchRateCount));
            }
        }

        if (!tagFreq.isEmpty()) {
            String topTag = "";
            int max = 0;
            for (Map.Entry<String, Integer> e : tagFreq.entrySet()) {
                if (e.getValue() > max) {
                    max = e.getValue();
                    topTag = e.getKey();
                }
            }
            sb.append(String.format(Locale.US, "\nTop activity: %s (%dx)", topTag, max));
        }

        if (sessionsCount == 0 && tagFreq.isEmpty()) {
            textStatsContent.setText("No data recorded for this month.");
        } else {
            textStatsContent.setText(sb.toString());
        }
    }

    public static int getSelectedVariable_TupleIndex() {
        return selectedVariableTupleIndex;
    }

    public void showDayDetailSheet(LocalDate date, @Nullable SummaryReader.SummaryEntry entry) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_day_summary, null);

        TextView title = sheet.findViewById(R.id.sheet_date_title);
        TextView statsText = sheet.findViewById(R.id.sheet_stats_summary);
        TextView tagsText = sheet.findViewById(R.id.tags_text);
        ChipGroup chipGroup = sheet.findViewById(R.id.sheet_chip_group);

        // --- 1. CALCULATE DYNAMIC BACKGROUND COLOR ---
        int surfaceColor = MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorSurface);
        int bgColor = surfaceColor; // Default fallback
        int tupleIndex = selectedVariableTupleIndex;

        if (entry != null && !entry.should_skip && tupleIndex > 0) {
            SummaryReader sr = SummaryReader.getInstance();
            if (tupleIndex < sr.getSummaryTitles().length) {
                try {
                    float val = Float.parseFloat(entry.tuple[tupleIndex].replace(",", "."));
                    float[] minMax = CalendarHighlighter.getGlobalMinMax(tupleIndex, sr.getSummaryTuplesWithNoSkipItems());
                    byte direction = com.example.bruxismdetector.bruxism_grapher2.CorrelationsCalculator.isGoingToBetter(1.0, sr.getSummaryTitles()[tupleIndex]);

                    // NEW: Pass surfaceColor so the sheet matches the tile!
                    bgColor = CalendarHighlighter.getGradientColor(val, minMax[0], minMax[1], direction, surfaceColor);
                } catch (Exception ignored) {}
            }
        }

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        float radius = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        gd.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        gd.setColor(bgColor);
        sheet.setBackground(gd);

        // --- 2. CALCULATE CONTRASTING TEXT & OUTLINE COLORS ---
        boolean isDark = isColorDark(bgColor);

        // Text Colors
        int primaryText = isDark ? android.graphics.Color.WHITE : com.google.android.material.color.MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnSurface);
        int secondaryText = isDark ? android.graphics.Color.parseColor("#F5F5F5") : com.google.android.material.color.MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int chipStroke = isDark ? android.graphics.Color.WHITE : android.graphics.Color.DKGRAY;

        // Outline (Halo) Color: Opposite of the text color for maximum readability
        int outlineColor = isDark ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;

        // Apply Text Colors
        title.setTextColor(primaryText);
        statsText.setTextColor(secondaryText);
        tagsText.setTextColor(primaryText);

        // Apply the Outline via a tight Drop Shadow (radius 4f, centered dx=0, dy=0)
        title.setShadowLayer(4f, 0f, 0f, outlineColor);
        statsText.setShadowLayer(4f, 0f, 0f, outlineColor);
        tagsText.setShadowLayer(4f, 0f, 0f, outlineColor);

        // --- 3. POPULATE DATA ---
        String monthName = date.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
        title.setText(String.format(Locale.getDefault(), "%d %s %d", date.getDayOfMonth(), monthName, date.getYear()));

        if (entry == null) {
            statsText.setText("No data recorded for this day.");
            tagsText.setVisibility(View.GONE);
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
                    Chip chip = new Chip(this);
                    chip.setText(tag.trim());

                    // Apply perfect contrast and outline to the Chips
                    chip.setTextColor(primaryText);
                    chip.setShadowLayer(3f, 0f, 0f, outlineColor); // Slightly smaller outline for chip text

                    chip.setChipBackgroundColorResource(android.R.color.transparent);
                    chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(chipStroke));
                    chip.setChipStrokeWidth(1f);

                    chip.setChipIcon(CalendarPalette.getTagIcon(this, tag));
                    chipGroup.addView(chip);
                }
            }
            tagsText.setVisibility(chipGroup.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        }

        dialog.setContentView(sheet);

        // --- 4. PREVENT CORNER CLIPPING ---
        View bottomSheet = (View) sheet.getParent();
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        dialog.show();
    }

    /**
     * Replaces the old spinner update block. Updates all UI without losing state.
     */
    private void triggerMetricUpdate(int position) {


        // Broadcast the update to ALL alive fragments (prevents stale off-screen months)
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof CalendarMonthFragment) {
                ((CalendarMonthFragment) fragment).updateMetricVisuals(position);
            }
        }

        if (gridAdapter != null) {
            gridAdapter.updateMetricAnimated(position);
        }
    }

    private boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }
}
