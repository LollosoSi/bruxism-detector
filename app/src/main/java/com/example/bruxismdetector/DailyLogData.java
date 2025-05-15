package com.example.bruxismdetector;

import android.content.Intent;

import androidx.annotation.NonNull;

public class DailyLogData {
    public int mood; // 0 = Ill, ..., 4 = Good

    public String info;

    public DailyLogData(Intent intent) {
        mood = intent.getIntExtra("mood", -1);

        info = intent.getStringExtra("info");

    }

    @NonNull
    @Override
    public String toString() {

        return "Mood=" + mood +
                ", info: " + (info!=null?info:"") ;
    }
}
