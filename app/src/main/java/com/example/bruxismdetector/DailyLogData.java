package com.example.bruxismdetector;

import android.content.Intent;

import androidx.annotation.NonNull;

public class DailyLogData {
    public int mood; // 0 = Ill, ..., 4 = Good

    public boolean workout;
    public boolean hydrated;
    public boolean stressed;
    public boolean caffeine;
    public boolean anxious;
    public boolean alcohol;
    public boolean lateDinner;
    public boolean medications;
    public boolean pain;
    public boolean lifeEvent;

    public DailyLogData(Intent intent) {
        mood = intent.getIntExtra("mood", -1);

        workout = intent.getBooleanExtra("workout", false);
        hydrated = intent.getBooleanExtra("hydrated", false);
        stressed = intent.getBooleanExtra("stressed", false);
        caffeine = intent.getBooleanExtra("caffeine", false);
        anxious = intent.getBooleanExtra("anxious", false);
        alcohol = intent.getBooleanExtra("alcohol", false);
        lateDinner = intent.getBooleanExtra("late_dinner", false);
        medications = intent.getBooleanExtra("medications", false);
        pain = intent.getBooleanExtra("pain", false);
        lifeEvent = intent.getBooleanExtra("life_event", false);
    }

    @NonNull
    @Override
    public String toString() {
        return "Mood=" + mood +
                ", workout=" + workout +
                ", hydrated=" + hydrated +
                ", stressed=" + stressed +
                ", caffeine=" + caffeine +
                ", anxious=" + anxious +
                ", alcohol=" + alcohol +
                ", lateDinner=" + lateDinner +
                ", medications=" + medications +
                ", pain=" + pain +
                ", lifeEvent=" + lifeEvent;
    }
}
