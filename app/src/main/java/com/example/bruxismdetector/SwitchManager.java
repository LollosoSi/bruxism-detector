package com.example.bruxismdetector;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.HashMap;
import java.util.Map;

public class SwitchManager {

        private final View root;
        private final Context context;

        public SwitchManager(View root, Context context) {
            this.root = root;
            this.context = context;

            setupSwitchLabels();
        }


        private void setupSwitchLabels() {
            Map<Integer, String> switchLabelMap = new HashMap<>();
            switchLabelMap.put(R.id.row_workout, "Done workout");
            switchLabelMap.put(R.id.row_hydrated, "Well hydrated");
            switchLabelMap.put(R.id.row_stressed, "Felt stressed");
            switchLabelMap.put(R.id.row_caffeine, "Had caffeine");
            switchLabelMap.put(R.id.row_anxious, "Felt anxious");
            switchLabelMap.put(R.id.row_alcohol, "Had alcohol");
            switchLabelMap.put(R.id.row_late_dinner, "Late dinner or skipped meals");
            switchLabelMap.put(R.id.row_medications, "Took medications");
            switchLabelMap.put(R.id.row_pain, "Felt pain");
            switchLabelMap.put(R.id.row_life_event, "Significant life event");
            switchLabelMap.put(R.id.row_botox, "Recent botox");

            for (Map.Entry<Integer, String> entry : switchLabelMap.entrySet()) {
                View row = root.findViewById(entry.getKey());
                if (row != null) {
                    TextView label = row.findViewById(R.id.switch_label);
                    if (label != null) {
                        label.setText(entry.getValue());
                    }
                    MaterialSwitch materialSwitch = row.findViewById(R.id.switch_item);
                    //materialSwitch.setThumbIconDrawable(AppCompatResources.getDrawable(context, R.drawable.check));
                }
            }
        }
}
