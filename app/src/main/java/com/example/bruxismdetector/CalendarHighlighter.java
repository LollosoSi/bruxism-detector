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
     * Interpolates colors based on whether the metric is inherently Good, Bad, or Neutral.
     */
    public static int getGradientColor(float value, float min, float max, byte effectDirection) {
        float norm = (value - min) / (max - min);
        norm = Math.max(0f, Math.min(1f, norm)); // Clamp 0-1

        int colorLow;
        int colorHigh;

        if (effectDirection == CorrelationsCalculator.PositiveCorr) {
            // High is GOOD: Soft Red -> Soft Green
            colorLow = Color.parseColor("#FFCDD2");  // Pastel Red
            colorHigh = Color.parseColor("#E8F5E9"); // Pastel Green
        }
        else if (effectDirection == CorrelationsCalculator.NegativeCorr) {
            // High is BAD: Soft Green -> Soft Red
            colorLow = Color.parseColor("#E8F5E9");  // Pastel Green
            colorHigh = Color.parseColor("#FFCDD2"); // Pastel Red
        }
        else {
            // High is NEUTRAL: Icy Blue -> Sky Blue
            colorLow = Color.parseColor("#E1F5FE");  // Very light Blue
            colorHigh = Color.parseColor("#29B6F6"); // Bright Sky Blue
        }

        return interpolateColor(colorLow, colorHigh, norm);
    }

    private static int interpolateColor(int colorStart, int colorEnd, float fraction) {
        int r = (int) ((Color.red(colorEnd) - Color.red(colorStart)) * fraction + Color.red(colorStart));
        int g = (int) ((Color.green(colorEnd) - Color.green(colorStart)) * fraction + Color.green(colorStart));
        int b = (int) ((Color.blue(colorEnd) - Color.blue(colorStart)) * fraction + Color.blue(colorStart));
        return Color.rgb(r, g, b);
    }
}