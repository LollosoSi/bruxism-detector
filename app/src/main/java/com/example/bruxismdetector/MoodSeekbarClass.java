package com.example.bruxismdetector;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.bruxismdetector.bruxism_grapher2.grapher_interfaces.IconManagerAndroid;

public class MoodSeekbarClass {

    private SeekBar seekBarMood;
    private TextView moodSelectedText;

    private final String[] moodLabels = {"Good", "Neutral", "Bad", "Tired", "Sick"};
    private Drawable[] icons = {null, null, null, null, null};
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
        if(icons[4-index]!=null) {
            moodSelectedText.setCompoundDrawables(icons[4-index], null, null, null); // Left, Top, Right, Bottom
            moodSelectedText.setCompoundDrawablePadding(8); // Optional padding between icon and text
        }else{
            moodSelectedText.setCompoundDrawables(null, null, null, null); // Left, Top, Right, Bottom
            moodSelectedText.setCompoundDrawablePadding(0); // Optional padding between icon and text
        }

        // Update color
        int color = ContextCompat.getColor(context, moodColors[4-index]);
        moodSelectedText.setTextColor(color);

        // Set color for seekbar track and thumb
        seekBarMood.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        seekBarMood.getThumb().setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private void moveLabelUnderThumb(final SeekBar seekBar, final TextView label) {
        label.post(() -> {
            int thumbOffset = seekBar.getThumbOffset();
            int max = seekBar.getMax();

            // Total usable width of the seekbar track
            int seekBarWidth = seekBar.getWidth() - 2 * thumbOffset;

            // Compute spacing between steps
            float spacing = seekBarWidth / (float) max;

            // Target X, adjusted to center the label under the thumb
            float targetX = thumbOffset + (spacing * (seekBar.getProgress()-2)) - (label.getMeasuredWidth() / 2f);

            // Animate label movement
            ObjectAnimator animator = ObjectAnimator.ofFloat(label, "translationX", targetX);
            animator.setDuration(150); // Slightly longer for better smoothness
            animator.start();
        });
    }




    public MoodSeekbarClass(View root, Context context) {
        this.root = root;
        this.context = context;

        IconManagerAndroid icm = new IconManagerAndroid(context);

        Drawable good = ContextCompat.getDrawable(context, R.drawable.good);
        good.setBounds(0, 0, good.getIntrinsicWidth(), good.getIntrinsicHeight());

        Drawable bad = ContextCompat.getDrawable(context, R.drawable.bad);
        bad.setBounds(0, 0, bad.getIntrinsicWidth(), bad.getIntrinsicHeight());

        Drawable tired = ContextCompat.getDrawable(context, R.drawable.tired);
        tired.setBounds(0, 0, bad.getIntrinsicWidth(), bad.getIntrinsicHeight());

        Drawable sick = ContextCompat.getDrawable(context, R.drawable.sick);
        sick.setBounds(0, 0, sick.getIntrinsicWidth(), sick.getIntrinsicHeight());

        icons[0] = good;
        icons[2] = bad;
        icons[3] = tired;
        icons[4] = sick;





        seekBarMood = root.findViewById(R.id.seekBar_mood);
        moodSelectedText = root.findViewById(R.id.mood_selected_text);


        seekBarMood.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateMoodDisplay(progress);
                moveLabelUnderThumb(seekBar, moodSelectedText);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {  }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {  }
        });

        updateMoodDisplay(seekBarMood.getProgress()); // Initialize text and color

        moveLabelUnderThumb(seekBarMood, moodSelectedText);


    }
}
