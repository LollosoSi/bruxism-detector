package com.example.bruxismdetector;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SwitchManager {

        private final View root;
        private final Context context;

        ArrayList<LinearLayout> switches = new ArrayList<>();

        public SwitchManager(View root, Context context) {
            this.root = root;
            this.context = context;

            addSwitches();
        }

        public String extractInfo(){
            StringBuilder sb = new StringBuilder();

            boolean first = true;

            for(LinearLayout l : switches){
                if(getSwitchState(l)){
                    if(first){
                        first = false;
                    }else{
                        sb.append(",");
                    }
                    sb.append(getSwitchLabel(l));
                }
            }

            return sb.toString();
        }

        boolean getSwitchState(LinearLayout custom_item){
            return ((MaterialSwitch) custom_item.findViewById(R.id.switch_item)).isChecked();
        }
        String getSwitchLabel(LinearLayout custom_item){
            return ((TextView) custom_item.findViewById(R.id.switch_label)).getText().toString();
        }
        LinearLayout createAndAttachSwitchCustom(String textvalue, LinearLayout tr){
            // Instantiate the custom layout using LayoutInflater
            LayoutInflater inflater = LayoutInflater.from(context);
            LinearLayout switchRowView = (LinearLayout) inflater.inflate(R.layout.switch_row, tr, false);
            ((TextView) switchRowView.findViewById(R.id.switch_label)).setText(textvalue);
            return switchRowView;
        }

    void addSwitches(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String em = prefs.getString("elements_switch_array", "Alcohol,Anxious,Botox,Caffeine,Hydrated,Life Event,Medication,Mouth Guard,Pain,Stressed,Workout,Skipped or late dinner");
        String[] elements = em.split(",");
        LinearLayout right = root.findViewById(R.id.right_col), left=root.findViewById(R.id.left_col);

        int i = 0;

        for(String e : elements){
            LinearLayout currentcol = (i%2==0) ? left : right;
            LinearLayout l;
            switches.add(l=createAndAttachSwitchCustom(e, currentcol));
            currentcol.addView(l);
            i++;
        }

    }
}
