// CalendarMonthFragment.java
package com.example.bruxismdetector;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bruxismdetector.bruxism_grapher2.CorrelationsCalculator;
import com.example.bruxismdetector.bruxism_grapher2.SummaryReader;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class CalendarMonthFragment extends Fragment {
    private static final String ARG_YEAR = "arg_year";
    private static final String ARG_MONTH = "arg_month";
    private int year;
    private int month;

    public class CellWithInfo{

        public CellWithInfo(View c, String[] data, boolean meaningful){
            this.data = data;
            cell = c;
            this.meaningful = meaningful;
        }
        public View cell;
        public String[] data;
        public boolean meaningful = false;

        float getFloat(int index){
            try {
                return Float.parseFloat(data[index]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return -1;
        }
    }
    public static CalendarMonthFragment newInstance(int year, int month) {
        Bundle args = new Bundle();
        args.putInt(ARG_YEAR, year);
        args.putInt(ARG_MONTH, month);
        CalendarMonthFragment fragment = new CalendarMonthFragment();
        fragment.setArguments(args);
        return fragment;
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
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar_month, container, false);
    }

    ArrayList<CellWithInfo> infocells = new ArrayList<>();

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        GridLayout grid = view.findViewById(R.id.grid_days);
        LinearLayout legendContainer = view.findViewById(R.id.legend_container);
        grid.removeAllViews();
        legendContainer.removeAllViews();

        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();
        LocalDate firstDayOfMonth = ym.atDay(1);
        // DayOfWeek restituisce un valore da 1 (LUN) a 7 (DOM).
        int dayOfWeekOfFirstDay = firstDayOfMonth.getDayOfWeek().getValue();

        SummaryReader reader = SummaryReader.getInstance();
        reader.populateMonthsArray();
        SummaryReader.SummaryMonth sm = reader.getSummaryMonths().stream()
                .filter(m -> m.getYear() == year && m.getMonth() == month)
                .findFirst().orElse(null);
        List<SummaryReader.SummaryEntry> entries = sm != null ? sm.tuples : new ArrayList<>();

        int infoIndex = reader.getInfomationIndex();
        int selected_tuple_index = ((CalendarViewer) getActivity()).getSelectedVariable_TupleIndex();

        // Build frequency and assign colors for badge legend
        Map<String, Integer> freq = new HashMap<>();
        for (SummaryReader.SummaryEntry entry : entries) {
            String[] infos = entry.tuple[infoIndex].split(",");
            for (String info : infos) {
                if(info.isEmpty())
                    continue;
                freq.put(info, freq.getOrDefault(info, 0) + 1);
            }
        }
        List<String> sortedKeys = freq.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        Map<String, Integer> colorMap = new HashMap<>();
        Random rnd = new Random(year * 31 + month);
        for (String key : sortedKeys) {
            int color = 0xff000000 | rnd.nextInt(0x00ffffff);
            colorMap.put(key, color);
        }


        String[] dayLabels = new String[7];
        // DayOfWeek.MONDAY è il primo giorno della settimana secondo lo standard ISO.
        java.time.DayOfWeek firstDay = java.time.DayOfWeek.MONDAY;
        for (int i = 0; i < 7; i++) {
            java.time.DayOfWeek dayOfWeek = firstDay.plus(i);
            // TextStyle.SHORT_STANDALONE fornisce un'abbreviazione (es. "Mon", "Lun").
            // Locale.getDefault() usa la lingua corrente del dispositivo.
            dayLabels[i] = dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, java.util.Locale.getDefault());
        }

        for (String dayLabel : dayLabels) {
            TextView header = new TextView(getContext());
            header.setText(dayLabel);
            header.setGravity(Gravity.CENTER);
            header.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
            header.setTextColor(Color.GRAY);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); // Distribuisci equamente
            header.setLayoutParams(params);
            grid.addView(header);
        }

        // 2. Aggiungi spazi vuoti per i giorni prima dell'inizio del mese
        // (dayOfWeekOfFirstDay - 1) perché se il mese inizia di lunedì (valore 1), non servono spazi.
        for (int i = 0; i < dayOfWeekOfFirstDay - 1; i++) {
            View emptyCell = new View(getContext());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = 0; // Invisibile ma occupa spazio nella griglia
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            emptyCell.setLayoutParams(params);
            grid.addView(emptyCell);
        }

        infocells = new ArrayList<>();

        // 3. Popola i giorni del mese (logica quasi invariata)
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = ym.atDay(day);
            View cell = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_day_cell, grid, false);

            // Imposta i parametri di layout per ogni cella
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0; // Larghezza gestita dal peso
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); // Distribuisci equamente
            cell.setLayoutParams(params);

            TextView txtDay = cell.findViewById(R.id.text_day_number);
            LinearLayout charLayout = cell.findViewById(R.id.layout_characteristics);
            txtDay.setText(String.valueOf(day));

            SummaryReader.SummaryEntry s_entry = null;

            // Add badges and then highlight cell
            for (SummaryReader.SummaryEntry entry : entries) {
                if (entry.day == day) {
                    s_entry = entry;
                    String[] infos = entry.tuple[infoIndex].split(",");
                    for (String info : infos) {
                        if(info.isEmpty())
                            continue;
                        TextView label = new TextView(getContext());
                        label.setText(info.contains(": ") ? info.split(": ")[1] : info);
                        label.setTextSize(12);
                        label.setPadding(6, 2, 6, 2);
                        label.setBackgroundResource(R.drawable.rounded_label_bg);
                        label.setBackgroundColor(colorMap.get(info));
                        label.setTextColor(0xffffffff);
                        charLayout.addView(label);
                    }
                    break;
                }
            }

            try {
                if(s_entry!=null)
                    infocells.add(new CellWithInfo(cell, s_entry.tuple, !s_entry.should_skip));

            }catch (Exception e){
                e.printStackTrace();
            }

            cell.setOnClickListener(v -> {
                // TODO: dettaglio giorno
            });
            grid.addView(cell);


        }

        calculateInfoCells(selected_tuple_index);

        // Build legend table
        int columns = Math.min(3, sortedKeys.size());
        if(columns == 0)
            return;
        TableLayout table = new TableLayout(getContext());
        int rows = (sortedKeys.size() + columns - 1) / columns;
        for (int r = 0; r < rows; r++) {
            TableRow row = new TableRow(getContext());
            for (int c = 0; c < columns; c++) {
                int idx = r + c * rows;
                if (idx < sortedKeys.size()) {
                    String key = sortedKeys.get(idx);
                    String showKey = key.contains(": ") ? key.split(": ")[1] : key;
                    LinearLayout cellLegend = new LinearLayout(getContext());
                    cellLegend.setOrientation(LinearLayout.HORIZONTAL);
                    cellLegend.setGravity(Gravity.CENTER_VERTICAL);
                    TextView colorBox = new TextView(getContext());
                    colorBox.setWidth(24);
                    colorBox.setHeight(24);
                    colorBox.setBackgroundColor(colorMap.get(key));
                    TextView label = new TextView(getContext());
                    label.setText(showKey + " (" + freq.get(key) + ")");
                    label.setPadding(8, 0, 16, 0);
                    cellLegend.addView(colorBox);
                    cellLegend.addView(label);
                    row.addView(cellLegend);
                }
            }
            table.addView(row);
        }
        legendContainer.addView(table);
    }

public void calculateInfoCells(int selected_tuple_index){

    if(selected_tuple_index != 0) {
        float min = 99999, max = 0;
        for (CellWithInfo icell : infocells) {
            if (icell.meaningful) {
                min = Math.min(min, icell.getFloat(selected_tuple_index));
                max = Math.max(max, icell.getFloat(selected_tuple_index));
            }
        }

        for (CellWithInfo icell : infocells) {
            if (icell.meaningful) {

                float value = icell.getFloat(selected_tuple_index);
                byte direction = CorrelationsCalculator.isGoingToBetter(value, SummaryReader.getInstance().getSummaryTitles()[selected_tuple_index]);
                float norm = (value - min) / (max - min);
                int color = direction == CorrelationsCalculator.NeutralCorr ? interpolateColor(Color.TRANSPARENT, Color.parseColor("#1E88E5"), norm) : direction == CorrelationsCalculator.PositiveCorr ? interpolateColor(Color.parseColor("#EF5350"), Color.parseColor("#00E676"), norm) : interpolateColor(Color.parseColor("#00E676"), Color.parseColor("#EF5350"), norm);
                icell.cell.setBackgroundColor(color);
            }else
                icell.cell.setBackgroundColor(Color.TRANSPARENT);

        }

    }else{
        for (CellWithInfo icell : infocells) {
            icell.cell.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}
    private static int interpolateColor(int colorStart, int colorEnd, float fraction) {
        int r = (int) ((Color.red(colorEnd) - Color.red(colorStart)) * fraction + Color.red(colorStart));
        int g = (int) ((Color.green(colorEnd) - Color.green(colorStart)) * fraction + Color.green(colorStart));
        int b = (int) ((Color.blue(colorEnd) - Color.blue(colorStart)) * fraction + Color.blue(colorStart));
        return Color.rgb(r, g, b);
    }
}
