package com.example.bruxismdetector;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

public class MoodSeekbarClass {

    private SeekBar seekBarMood;
    private TextView moodSelectedText;

    private final String[] moodLabels = {"Good", "Neutral", "Bad", "Tired", "Sick"};
    private final int[] moodColors = {
            R.color.material_green_500,  // Good
            R.color.material_blue_500,   // Neutral
            R.color.material_orange_500, // Bad
            R.color.material_yellow_500, // Tired
            R.color.material_red_500     // Ill
    };

    private final View root;
    private final Context context;

    private void updateMoodDisplay(int index) {
        // Update text
        String selectedMood = moodLabels[4-index];
        moodSelectedText.setText("" + selectedMood);

        // Update color
        int color = ContextCompat.getColor(context, moodColors[4-index]);
        moodSelectedText.setTextColor(color);

        // Set color for seekbar track and thumb
        seekBarMood.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        seekBarMood.getThumb().setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    public MoodSeekbarClass(View root, Context context) {
        this.root = root;
        this.context = context;


        seekBarMood = root.findViewById(R.id.seekBar_mood);
        moodSelectedText = root.findViewById(R.id.mood_selected_text);

        updateMoodDisplay(seekBarMood.getProgress()); // Initialize text and color

        seekBarMood.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateMoodDisplay(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {  }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {  }
        });

    }
}
