package com.example.bruxismdetector;



// CalendarHighlighter.java
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;

public class CalendarHighlighter {
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Evidenzia una cella giorno colorandola in base a variabile booleana o analogica.
     * @param cell      View root della cella (item_day_cell)
     * @param date      data del giorno
     * @param dateLabels lista di date ("dd-MM-yyyy")
     * @param tuples    liste di valori in corrispondenza di date
     * @param titles    nomi delle colonne
     * @param filterNames nome di variabili boolean filtrabili ("Info")
     * @param infoIndex indice colonna "Info"
     * @param variableName nome variabile corrente da evidenziare
     * @param isBoolean  true se variabile è booleana (in filterNames)
     */
    public static void highlightDayCell(View cell,
                                        LocalDate date,
                                        ArrayList<String> dateLabels,
                                        ArrayList<String[]> tuples,
                                        String[] titles,
                                        ArrayList<String> filterNames,
                                        int infoIndex,
                                        String variableName,
                                        boolean isBoolean) {
        String key = date.format(DTF);
        int idx = dateLabels.indexOf(key);
        if (idx < 0) {
            // no sessione: fondo trasparente
            cell.setBackgroundColor(Color.BLUE);
            return;
        }
        String[] row = tuples.get(idx);
        if (isBoolean) {
            int filterIdx = filterNames.indexOf(variableName);
            boolean hit = row[infoIndex].contains(filterNames.get(filterIdx));
            if (hit) {
                cell.setBackgroundColor(Color.GREEN);
            } else {
                cell.setBackgroundColor(Color.TRANSPARENT);
            }
        } else {
            // analogico: trovare indice in titles
            int col = Arrays.asList(titles).indexOf(variableName);
            if (col < 0) return;
            String valueStr = row[col];
            try {
                float value = Float.parseFloat(valueStr);
                // mappa valore in scala 0..1 (min-max su quel mese)
                float min = readerGetMin(variableName, tuples, titles, dateLabels, date);
                float max = readerGetMax(variableName, tuples, titles, dateLabels, date);
                float norm = (value - min) / (max - min);
                int color = interpolateColor(Color.RED, Color.GREEN, norm);
                cell.setBackgroundColor(color);
            } catch (Exception e) {
                cell.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    private static float readerGetMin(String var, ArrayList<String[]> tuples,
                                      String[] titles, ArrayList<String> labels, LocalDate date) {
        int col = Arrays.asList(titles).indexOf(var);
        float min = Float.MAX_VALUE;
        for (int i = 0; i < labels.size(); i++) {
            float v = Float.parseFloat(tuples.get(i)[col]);
            min = Math.min(min, v);
        }
        return min;
    }
    private static float readerGetMax(String var, ArrayList<String[]> tuples,
                                      String[] titles, ArrayList<String> labels, LocalDate date) {
        int col = Arrays.asList(titles).indexOf(var);
        float max = Float.MIN_VALUE;
        for (int i = 0; i < labels.size(); i++) {
            float v = Float.parseFloat(tuples.get(i)[col]);
            max = Math.max(max, v);
        }
        return max;
    }

    private static int interpolateColor(int colorStart, int colorEnd, float fraction) {
        int r = (int) ((Color.red(colorEnd) - Color.red(colorStart)) * fraction + Color.red(colorStart));
        int g = (int) ((Color.green(colorEnd) - Color.green(colorStart)) * fraction + Color.green(colorStart));
        int b = (int) ((Color.blue(colorEnd) - Color.blue(colorStart)) * fraction + Color.blue(colorStart));
        return Color.rgb(r, g, b);
    }
}
