package com.example.bruxismdetector;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.elevation.SurfaceColors;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Arrays;
import java.util.List;

public class SwitchEditor extends AppCompatActivity {

    SwitchManager sm;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //EdgeToEdge.enable(this);
        setContentView(R.layout.activity_switch_editor);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getWindow().setStatusBarColor( SurfaceColors.SURFACE_0.getColor(this));

        sm = new SwitchManager(this.findViewById(R.id.main), this, true);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(prefs.getBoolean("editor_tutorial", true)){
            LinearLayout ll = findViewById(R.id.left_col);
            LinearLayout elementcontainer = (LinearLayout) ll.getChildAt(0);
            ImageButton imgbtn = (ImageButton) elementcontainer.getChildAt(0);
            CheckBox cb = (CheckBox) elementcontainer.getChildAt(1);
            TextView label = ((TextView) elementcontainer.findViewById(R.id.switch_label));
            com.google.android.material.materialswitch.MaterialSwitch switchitem = (MaterialSwitch) elementcontainer.findViewById(R.id.switch_item);

            new Handler(Looper.getMainLooper()).post(() -> {



                List<Pair<View, String>> steps = Arrays.asList(

                        new Pair<>(findViewById(R.id.scrollview_editor), "Here are all your current entries."),
                        new Pair<>(ll, "We have pre-selected some items for you."),

                        new Pair<>(elementcontainer, "This is one of your entries.\n\nIt will appear in the main menu."),

                        new Pair<>(label, "When selected, your data will be tagged with this label"),
                        new Pair<>(switchitem, "Show/Hide this entry from your main selection, but don't delete it"),
                        new Pair<>(cb, "When selected, this entry will be enabled as default.\n\nIf the entry is enabled, you will see it in the main menu and will be able to disable it.\n\nIf it is hidden, but this box is checked, the entry will be added to all your next sessions automatically.\n\nUseful if you have a long standing condition or wish to share your data with us."),
                        new Pair<>(imgbtn, "Permanently delete this entry\n\n\n\n\n"),

                        new Pair<>(findViewById(R.id.switch_name_edittext), "Here you can add new entries,\nthe suggested format is Macrocategory: Category.\nThis allows you to find similar items quickly.\n\nForbidden characters: , . ; "),
                        new Pair<>(findViewById(R.id.add_switch), "Add the new entry.\nIf you made a mistake, just press X."),
                        new Pair<>(findViewById(R.id.save_switches), "Save your changes and go back.")

                );

                new TutorialOverlayManager(SwitchEditor.this, steps).start(() -> prefs.edit().putBoolean("editor_tutorial",false).apply());
            });

        }

    }

    public void Save(View v){
        sm.SaveCurrentSelection();
        finish();
    }

    public void addLabel(View v){

        EditText et = ((EditText) findViewById(R.id.switch_name_edittext));
        String label = et.getText().toString().replace(",","").replace(";","").replace(".","");
        et.setText("");
        if(label.isEmpty()) return;
        if(sm.all_switches.contains(label)) return;
        sm.addNewSwitch(label);
    }
}