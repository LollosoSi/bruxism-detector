package com.example.bruxismdetector;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import androidx.core.content.ContextCompat;
import java.util.HashMap;
import java.util.Map;

public class CalendarPalette {
    private static final int[] PALETTE = {
            Color.parseColor("#4DB6AC"), // Teal
            Color.parseColor("#FFB74D"), // Amber
            Color.parseColor("#E57373"), // Soft Coral
            Color.parseColor("#81C784"), // Mint Green
            Color.parseColor("#BA68C8"), // Soft Purple
            Color.parseColor("#64B5F6"), // Sky Blue
            Color.parseColor("#A1887F"), // Warm Grey
            Color.parseColor("#FFD54F")  // Soft Gold
    };

    private static final Map<String, Integer> COLOR_CACHE = new HashMap<>();

    public static int getTagColor(String tag) {
        if (tag == null || tag.trim().isEmpty()) return Color.GRAY;
        String clean = tag.trim().toLowerCase();
        if (!COLOR_CACHE.containsKey(clean)) {
            int index = Math.abs(clean.hashCode()) % PALETTE.length;
            COLOR_CACHE.put(clean, PALETTE[index]);
        }
        return COLOR_CACHE.get(clean);
    }

    public static Drawable getTagIcon(Context context, String tag) {
        if (tag == null) return null;
        String t = tag.trim().toLowerCase();
        int resId = R.drawable.ic_calendar_circle; // default

        if (t.contains("alcohol")) resId = R.drawable.ic_calendar_alcohol;
        else if (t.contains("anxious") || t.contains("anxiety")) resId = R.drawable.ic_calendar_anxious;
        else if (t.contains("botox")) resId = R.drawable.ic_calendar_botox;
        else if (t.contains("medication") || t.contains("pill")) resId = R.drawable.ic_calendar_pill;
        else if (t.contains("caffeine") || t.contains("coffee")) resId = R.drawable.ic_calendar_coffee;
        else if (t.contains("hydrated")) resId = R.drawable.ic_calendar_water;
        else if (t.contains("life event")) resId = R.drawable.ic_calendar_star;
        else if (t.contains("pain")) resId = R.drawable.ic_calendar_pain;
        else if (t.contains("stressed")) resId = R.drawable.ic_calendar_stressed;
        else if (t.contains("workout")) resId = R.drawable.ic_calendar_workout;
        else if (t.contains("treatment") || t.contains("mouthguard") || t.contains("night guard")) resId = R.drawable.ic_calendar_mouthguard;
        else if (t.contains("meal") || t.contains("food")) resId = R.drawable.ic_calendar_meal;
        else if (t.contains("medical condition") || t.contains("sick")) resId = R.drawable.ic_calendar_sick;
        else if (t.contains("habit")) resId = R.drawable.ic_calendar_habit;
        else if (t.contains("dietary")) resId = R.drawable.ic_calendar_dietary;
        else if (t.contains("anger") || t.contains("frustration") || t.contains("bad")) resId = R.drawable.ic_calendar_bad;
        else if (t.contains("good")) resId = R.drawable.ic_calendar_good;
        else if (t.contains("tired")) resId = R.drawable.ic_calendar_tired;
        else if (t.contains("concentrated")) resId = R.drawable.ic_calendar_focus;
        else if (t.contains("substance use")) resId = R.drawable.ic_calendar_warning;
        else if (t.contains("dental issue")) resId = R.drawable.ic_calendar_dental;
        
        // Fallback shapes for variety if no specific keyword matched
        else if (t.hashCode() % 5 == 0) resId = R.drawable.ic_calendar_square;
        else if (t.hashCode() % 5 == 1) resId = R.drawable.ic_calendar_triangle;
        else if (t.hashCode() % 5 == 2) resId = R.drawable.ic_calendar_hexagon;
        else if (t.hashCode() % 5 == 3) resId = R.drawable.ic_calendar_heart;
        else if (t.hashCode() % 5 == 4) resId = R.drawable.ic_calendar_x;

        Drawable drawable = ContextCompat.getDrawable(context, resId);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(getTagColor(tag));
        }
        return drawable;
    }

    /**
     * Legacy support or simple background drawable creator.
     */
    public static Drawable getTagDrawable(String tag, float sizePx) {
        // This was previously creating shapes. Now we should preferably use getTagIcon.
        // But for dots in day cells, we might still want a simple version.
        // Let's keep it but simplified.
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(getTagColor(tag));
        return gd;
    }
}