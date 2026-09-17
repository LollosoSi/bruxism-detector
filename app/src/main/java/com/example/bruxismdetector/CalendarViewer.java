package com.example.bruxismdetector;

import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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
        gridAdapter = new SessionGridAdapter(allSessionsFlat, selectedVariableTupleIndex, this::showDayDetailSheet);
        listView.setAdapter(gridAdapter);

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

    private void showDayDetailSheet(SummaryReader.SummaryEntry entry) {
        var dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_day_summary, null);

        TextView title = sheet.findViewById(R.id.sheet_date_title);
        TextView statsText = sheet.findViewById(R.id.sheet_stats_summary);
        ChipGroup chipGroup = sheet.findViewById(R.id.sheet_chip_group);

        LocalDate date = LocalDate.parse(entry.tuple[0], DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        title.setText(String.format(Locale.getDefault(), "%d %s %d",
                date.getDayOfMonth(), date.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()), date.getYear()));

        String[] t = entry.tuple;
        statsText.setText(String.format(Locale.US,
                "• Duration: %s hrs\n• Jaw Events: %s\n• Clenching Rate: %s /hr\n• Total Clench: %s sec\n• Alarms: %s (%s%%)",
                t[1], t[9], t[4], t[2], t[11], t[13]));

        int infoIndex = SummaryReader.getInstance().getInfomationIndex();
        if (t[infoIndex] != null && !t[infoIndex].trim().isEmpty()) {
            for (String tag : t[infoIndex].split(",")) {
                if (tag.trim().isEmpty()) continue;
                Chip chip = new Chip(this);
                chip.setText(tag.trim());
                chip.setChipBackgroundColorResource(android.R.color.transparent);
                chip.setChipStrokeColorResource(android.R.color.darker_gray);
                chip.setChipStrokeWidth(1f);
                chip.setChipIcon(CalendarPalette.getTagIcon(this, tag));
                chipGroup.addView(chip);
            }
        }

        dialog.setContentView(sheet);
        dialog.show();
    }

    /**
     * Replaces the old spinner update block. Updates all UI without losing state.
     */
    private void triggerMetricUpdate(int position) {
        if (pagerAdapter != null) {
            pagerAdapter.notifyDataSetChanged();
        }

        Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (currentFragment instanceof CalendarMonthFragment) {
            ((CalendarMonthFragment) currentFragment).updateMetricVisuals(position); // Handles its own animation
        }

        if (gridAdapter != null) {
            gridAdapter.updateMetricAnimated(position); // Tell the RecyclerView to animate!
        }
    }
}
