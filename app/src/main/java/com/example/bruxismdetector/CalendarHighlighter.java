package com.example.bruxismdetector;

import android.graphics.Color;
import com.example.bruxismdetector.bruxism_grapher2.CorrelationsCalculator;
import java.util.ArrayList;

public class CalendarHighlighter {

    /**
     * Calculates the absolute minimum and maximum for a given metric across ALL historical data.
     */
    public static float[] getGlobalMinMax(int tupleIndex, ArrayList<String[]> tuples) {
        if (tuples == null || tuples.isEmpty()) return new float[]{0f, 1f};

        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;

        for (String[] row : tuples) {
            try {
                float val = Float.parseFloat(row[tupleIndex].replace(",", "."));
                min = Math.min(min, val);
                max = Math.max(max, val);
            } catch (Exception ignored) { }
        }

        if (min == Float.MAX_VALUE) return new float[]{0f, 1f}; // Fallback
        if (min == max) return new float[]{min, max + 1f};

        return new float[]{min, max};
    }

    /**
     * Interpolates colors based on Light/Dark Mode and whether the metric is Good, Bad, or Neutral.
     */
    public static int getGradientColor(float value, float min, float max, byte effectDirection, int surfaceColor) {
        float norm = (max == min) ? 0f : (value - min) / (max - min);
        norm = Math.max(0f, Math.min(1f, norm)); // Clamp 0-1

        boolean isDarkMode = isColorDark(surfaceColor);

        if (!isDarkMode) {
            // LIGHT MODE: As is (Pastel 2-point gradient)
            int colorLow;
            int colorHigh;

            if (effectDirection == CorrelationsCalculator.PositiveCorr) {
                colorLow = Color.parseColor("#FFCDD2");  // Pastel Red
                colorHigh = Color.parseColor("#E8F5E9"); // Pastel Green
            }
            else if (effectDirection == CorrelationsCalculator.NegativeCorr) {
                colorLow = Color.parseColor("#E8F5E9");  // Pastel Green
                colorHigh = Color.parseColor("#FFCDD2"); // Pastel Red
            }
            else {
                colorLow = Color.parseColor("#E1F5FE");  // Very light Blue
                colorHigh = Color.parseColor("#29B6F6"); // Bright Sky Blue
            }
            return interpolateColor(colorLow, colorHigh, norm);

        } else {
            // DARK MODE: 3-Point Gradient passing through the exact Surface Color (Dark Gray/Black)
            int colorBad = Color.parseColor("#C62828");  // Deep Red
            int colorGood = Color.parseColor("#2E7D32"); // Deep Green
            int colorNeutral = Color.parseColor("#0277BD"); // Deep Blue

            if (effectDirection == CorrelationsCalculator.PositiveCorr) {
                // Low is Bad, Middle is Surface, High is Good
                if (norm < 0.5f) return interpolateColor(colorBad, surfaceColor, norm * 2f);
                else return interpolateColor(surfaceColor, colorGood, (norm - 0.5f) * 2f);
            }
            else if (effectDirection == CorrelationsCalculator.NegativeCorr) {
                // Low is Good, Middle is Surface, High is Bad
                if (norm < 0.5f) return interpolateColor(colorGood, surfaceColor, norm * 2f);
                else return interpolateColor(surfaceColor, colorBad, (norm - 0.5f) * 2f);
            }
            else {
                // Neutral: From Surface to Deep Blue
                return interpolateColor(surfaceColor, colorNeutral, norm);
            }
        }
    }

    private static int interpolateColor(int colorStart, int colorEnd, float fraction) {
        int r = (int) ((Color.red(colorEnd) - Color.red(colorStart)) * fraction + Color.red(colorStart));
        int g = (int) ((Color.green(colorEnd) - Color.green(colorStart)) * fraction + Color.green(colorStart));
        int b = (int) ((Color.blue(colorEnd) - Color.blue(colorStart)) * fraction + Color.blue(colorStart));
        return Color.rgb(r, g, b);
    }

    private static boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }
}