package com.example.bruxismdetector;

import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.bruxismdetector.bruxism_grapher2.SummaryReader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CalendarViewer extends AppCompatActivity {
    private ViewPager2 viewPager;
    private CalendarPagerAdapter pagerAdapter;
    private Spinner variablePicker;
    private TextView textMonthYear;
    private ImageButton btnPrev, btnNext;
    private String selectedVariable;
    private List<String> spinnerItems;

    public static int selected_variable_tuple_index = 0;

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

        // Inizializza view
        viewPager = findViewById(R.id.calendar_viewpager);
        variablePicker = findViewById(R.id.variable_picker);
        textMonthYear = findViewById(R.id.text_month_year);
        btnPrev = findViewById(R.id.button_prev_month);
        btnNext = findViewById(R.id.button_next_month);

        // Carica dati da SummaryReader
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File recordingsDir = new File(documentsDir, "RECORDINGS");
        File summaryDir = new File(recordingsDir, "Summary");

        SummaryReader.setFilepath(summaryDir.getParent() + "/Summary/Summary.csv");

        SummaryReader reader = SummaryReader.getInstance();
        reader.populateMonthsArray();
        List<SummaryReader.SummaryMonth> months = reader.getSummaryMonths();
        ArrayList<String> filterNames = reader.getFilterNames();
        String[] summaryTitles = reader.getSummaryTitles();

        // Prepara spinner: booleane e analogiche
        spinnerItems = new ArrayList<>(Arrays.asList("Select an item"));
        for (String title : summaryTitles) {
            if (!title.equalsIgnoreCase("Date") && !title.equalsIgnoreCase("Mood") && !title.equalsIgnoreCase("Info")) {
                spinnerItems.add(title);
            }
        }
        selectedVariable = spinnerItems.get(0);

        ArrayAdapter<String> adapterSpinner = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, spinnerItems);
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        variablePicker.setAdapter(adapterSpinner);
        variablePicker.setSelection(0);

        variablePicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedVariable = spinnerItems.get(position);
                selected_variable_tuple_index = position; // Update the index in the Activity

                // Notify the PagerAdapter that the underlying data for the current item *might* have changed.
                // This is useful if the fragment needs to be entirely recreated or re-bound by the adapter.
                // However, for just updating content within an existing fragment, direct communication is often better.
                // pagerAdapter.notifyItemChanged(viewPager.getCurrentItem());

                if (pagerAdapter != null) {
                    pagerAdapter.notifyItemChanged(viewPager.getCurrentItem());
                }


                int currentViewPagerItemPosition = viewPager.getCurrentItem();
                Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + currentViewPagerItemPosition);

                if (currentFragment != null)
                    ((CalendarMonthFragment)currentFragment).calculateInfoCells(position);

            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Optionally handle if nothing is selected, though usually not needed for spinners with a default.
            }
        });

        // Configura ViewPager2 con mesi basati su SummaryMonth
        int currentIndex = 0;
        java.time.YearMonth today = java.time.YearMonth.now();
        for (int i = 0; i < months.size(); i++) {
            SummaryReader.SummaryMonth sm = months.get(i);
            if (sm.getYear() == today.getYear() && sm.getMonth() == today.getMonthValue()) {
                currentIndex = i;
                break;
            }
        }
        pagerAdapter = new CalendarPagerAdapter(this, months);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setCurrentItem(currentIndex, false);

        // Cambia titolo mese al cambio pagina
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateMonthYearLabel(position);

                int currentViewPagerItemPosition = viewPager.getCurrentItem();
                Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + currentViewPagerItemPosition);

                if (currentFragment != null)
                    ((CalendarMonthFragment)currentFragment).calculateInfoCells(getSelectedVariable_TupleIndex());
            }
        });

        // Controlli prev/next
        btnPrev.setOnClickListener(v -> {
            int pos = viewPager.getCurrentItem();
            if (pos > 0) viewPager.setCurrentItem(pos - 1, true);
        });
        btnNext.setOnClickListener(v -> {
            int pos = viewPager.getCurrentItem();
            if (pos < months.size() - 1) viewPager.setCurrentItem(pos + 1, true);
        });

        updateMonthYearLabel(currentIndex);
    }

    private void updateMonthYearLabel(int position) {
        SummaryReader.SummaryMonth sm = SummaryReader.getInstance().getSummaryMonths().get(position);
        String label = java.time.Month.of(sm.month)
                .getDisplayName(java.time.format.TextStyle.FULL,
                        getResources().getConfiguration().getLocales().get(0))
                + " " + sm.getYear();
        textMonthYear.setText(label);
    }

    public String getSelectedVariable() {
        return (selectedVariable);
    }

    // Already adjusted to skip date position index
    public static int getSelectedVariable_TupleIndex() {
        return selected_variable_tuple_index;
    }
}
