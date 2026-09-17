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
            Color.parseColor("#F57F17")  // Dark Yellow/Orange
    };

    private static final int[] FALLBACK_SHAPES = {
            R.drawable.ic_calendar_square,
            R.drawable.ic_calendar_triangle,
            R.drawable.ic_calendar_hexagon,
            R.drawable.ic_calendar_heart,
            R.drawable.ic_calendar_x,
            R.drawable.ic_calendar_star
    };

    private static final Map<String, Integer> COLOR_CACHE = new HashMap<>();
    private static final Map<String, Integer> ICON_CACHE = new HashMap<>();
    private static int nextFallbackShapeIndex = 0;

    public static int getTagColor(String tag) {
        if (tag == null || tag.trim().isEmpty()) return Color.GRAY;
        String clean = tag.trim().toLowerCase();

        if (!COLOR_CACHE.containsKey(clean)) {
            COLOR_CACHE.put(clean, assignSemanticColor(clean));
        }
        return COLOR_CACHE.get(clean);
    }

    private static int assignSemanticColor(String t) {

        if (t.contains("hydrated") || t.contains("water") || t.contains("concentrated") || t.contains("focus")) return Color.parseColor("#64B5F6");
        if (t.contains("caffeine") || t.contains("coffee")) return Color.parseColor("#F57F17");
        if (t.contains("treatment") || t.contains("mouthguard") || t.contains("night guard") || t.contains("dental") || t.contains("medical") || t.contains("sick")) return Color.parseColor("#4DB6AC");
        if (t.contains("meal") || t.contains("food") || t.contains("dietary")) return Color.parseColor("#FFB74D");
        if (t.contains("anxious") || t.contains("anxiety") || t.contains("stressed") || t.contains("anger") || t.contains("frustration") || t.contains("bad") || t.contains("pain")) return Color.parseColor("#E57373");
        if (t.contains("workout") || t.contains("good")) return Color.parseColor("#81C784");
        if (t.contains("alcohol") || t.contains("medication") || t.contains("pill") || t.contains("botox") || t.contains("substance")) return Color.parseColor("#BA68C8");
        if (t.contains("life event") || t.contains("milestone")) return Color.parseColor("#F57F17");
        if (t.contains("tired") || t.contains("habit")) return Color.parseColor("#A1887F");

        int index = Math.abs(t.hashCode()) % PALETTE.length;
        return PALETTE[index];
    }

    public static Drawable getTagIcon(Context context, String tag) {
        if (tag == null) return null;
        String t = tag.trim().toLowerCase();

        // 1. If we already assigned a shape to this tag, reuse it instantly
        if (!ICON_CACHE.containsKey(t)) {

            int resId = -1;

            // 2. Check known keywords first
            if (t.contains("alcohol")) resId = R.drawable.ic_calendar_alcohol;
            else if (t.contains("anxious") || t.contains("anxiety")) resId = R.drawable.ic_calendar_anxious;
            else if (t.contains("botox")) resId = R.drawable.ic_calendar_botox;
            else if (t.contains("medication") || t.contains("pill")) resId = R.drawable.ic_calendar_pill;
            else if (t.contains("caffeine") || t.contains("coffee")) resId = R.drawable.ic_calendar_coffee;
            else if (t.contains("hydrated")) resId = R.drawable.ic_calendar_water;
            else if (t.contains("life event")) resId = R.drawable.ic_calendar_life_event;
            else if (t.contains("pain")) resId = R.drawable.ic_calendar_pain;
            else if (t.contains("stressed")) resId = R.drawable.ic_calendar_stressed;
            else if (t.contains("workout")) resId = R.drawable.ic_calendar_workout;
            else if (t.contains("treatment") || t.contains("mouthguard") || t.contains("night guard")) resId = R.drawable.ic_calendar_mouthguard;
            else if (t.contains("meal") || t.contains("food") || t.contains("dietary")) resId = R.drawable.ic_calendar_dietary;
            else if (t.contains("medical condition") || t.contains("sick")) resId = R.drawable.ic_calendar_sick;
            else if (t.contains("habit")) resId = R.drawable.ic_calendar_habit;
            else if (t.contains("anger") || t.contains("frustration") || t.contains("bad")) resId = R.drawable.ic_calendar_bad;
            else if (t.contains("good")) resId = R.drawable.ic_calendar_good;
            else if (t.contains("tired")) resId = R.drawable.ic_calendar_tired;
            else if (t.contains("concentrated")) resId = R.drawable.ic_calendar_focus;
            else if (t.contains("substance use")) resId = R.drawable.ic_calendar_warning;
            else if (t.contains("dental issue")) resId = R.drawable.ic_calendar_dental;

            // 3. Assign sequential fallback shape if no keyword matches
            if (resId == -1) {
                resId = FALLBACK_SHAPES[nextFallbackShapeIndex % FALLBACK_SHAPES.length];
                nextFallbackShapeIndex++;
            }

            // 4. Save the assigned ID to the cache
            ICON_CACHE.put(t, resId);
        }

        // 5. Retrieve from cache and tint
        int finalResId = ICON_CACHE.get(t);
        Drawable drawable = ContextCompat.getDrawable(context, finalResId);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(getTagColor(tag));
        }
        return drawable;
    }

    public static Drawable getTagDrawable(String tag, float sizePx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(getTagColor(tag));
        return gd;
    }
}