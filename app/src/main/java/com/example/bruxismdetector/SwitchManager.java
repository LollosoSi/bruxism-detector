package com.example.bruxismdetector;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class SwitchManager {


    private static String TAG = "SwitchManager";
    private final View root;
    private final Context context;

    ArrayList<LinearLayout> switches = new ArrayList<>();

    ArrayList<String> enabled_switches = new ArrayList<>();
    ArrayList<String> all_switches = new ArrayList<>();
    ArrayList<String> selected_switches = new ArrayList<>();


    String default_all = "Alcohol,Anxious,Medication: Muscle Relaxant,Medication: Botox,Caffeine,Hydrated,Life Event,Pain,Stressed,Workout,Treatment: Osteopathy,Treatment: Physiotherapy,Medication: SSRI,Medication: SNRI,Treatment: CPAP,Treatment: Night Guard,Meal: Good,Meal: Late,Meal: Skipped,Medical Condition: Sleep Apnea,Medical Condition: GERD,Medical Condition: ADHD,Medical Condition: Epilepsy,Medical Condition: Parkinson's Disease,Medical Condition: Dementia,Medical Condition: Night Terrors,Habit: Chewing Gum,Habit: Chewing on objects,Dietary: Hard or Chewy Food,Dietary: Sugary Food,Dietary: Acidic Food,Anger,Frustration,Concentrated,Substance Use: Recreational Drugs,Medical Condition: Muscle Imbalance,Medical Condition: Restless Leg Syndrome,Medical Condition: Periodic Limb Movement Disorder,Medical Condition: Down Syndrome,Medical Condition: Rett Syndrome,Medical Condition: Autism,Medical Condition: Fibromyalgia,Medical Condition: OCD,Dental Issue: Misaligned Teeth,Dental Issue: Missing Teeth,Dental Issue: Abnormal Bite";
    String default_enabled = "Alcohol,Anxious,Medication: Botox,Caffeine,Hydrated,Life Event,Pain,Stressed,Workout,Treatment: Night Guard,Meal: Good,Meal: Late,Meal: Skipped,Treatment: CPAP";

    Comparator<String> stringcomparator = new Comparator<String>() {
        @Override
        public int compare(String s, String t1) {
            // The items which do not contain ":" should go to top and all of them should be sorted alphabetically
            if (s.contains(":") && !t1.contains(":"))
                return 1;
            if (!s.contains(":") && t1.contains(":"))
                return -1;
            return s.compareTo(t1);
        }
    };

    public SwitchManager(View root, Context context, boolean editMode) {
        this.root = root;
        this.context = context;

        populate(editMode);
    }

    public void populate(boolean editMode) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String enabled = prefs.getString("enabled_elements_switch_array", default_enabled);
        String all = prefs.getString("all_elements_switch_array", default_all);
        String default_selected = prefs.getString("selected_elements_switch_array", "");


        enabled_switches = new ArrayList<>(Arrays.asList(enabled.split(",")));
        all_switches = new ArrayList<>(Arrays.asList(all.split(",")));

        selected_switches = new ArrayList<>(Arrays.asList(default_selected.split(",")));


        enabled_switches.sort(stringcomparator);
        all_switches.sort(stringcomparator);

        addSwitches(editMode);
    }

    public void ReloadAll() {
        populate(false);
    }

    public String extractInfo() {
        ArrayList<String> elements = new ArrayList<>();

        for (LinearLayout l : switches) {
            if (getSwitchState(l))
                elements.add(getSwitchLabel(l));
        }
        for (String s : selected_switches) {
            // If the enabled switches don't contain this entry, but we said to always select it, this means we should add it.
            // Otherwise, it's been un/checked by the user
            if (!enabled_switches.contains(s))
                elements.add(s);
        }

        return arrayToString(elements.toArray(new String[0]));
    }

    boolean getSwitchState(LinearLayout custom_item) {
        return ((MaterialSwitch) custom_item.findViewById(R.id.switch_item)).isChecked();
    }

    String getSwitchLabel(LinearLayout custom_item) {
        return ((TextView) custom_item.findViewById(R.id.switch_label)).getText().toString();
    }

    LinearLayout createAndAttachSwitchCustom(String textvalue, LinearLayout tr) {

        // Instantiate the custom layout using LayoutInflater
        LayoutInflater inflater = LayoutInflater.from(context);
        LinearLayout switchRowView = (LinearLayout) inflater.inflate(R.layout.switch_row, tr, false);
        ((TextView) switchRowView.findViewById(R.id.switch_label)).setText(textvalue);
        return switchRowView;
    }


    String arrayToString(String[] array){
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for(String s : array){
            if(s.isEmpty())continue;
            if(first)
                first=false;
            else
                sb.append(",");
            sb.append(s);

        }
        return sb.toString();
    }
    public void printDebug(){
        Log.i(TAG, "printDebug:\nSelected: "+arrayToString(selected_switches.toArray(new String[0]))
                +"\nEnabled: "+arrayToString(enabled_switches.toArray(new String[0]))
                +"\nExtracted: "+extractInfo());


    }
    void addSwitches(boolean edit_mode) {

        boolean debug = false;

        LinearLayout right = root.findViewById(R.id.right_col), left = root.findViewById(R.id.left_col);
        right.removeAllViews();
        left.removeAllViews();

        switches.clear();

        int i = 0;

        if(debug){
            Button b = new Button(context);
            b.setText("Print");
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    printDebug();
                }});
            i++;
            left.addView(b);

        }

        for (String e : (edit_mode ? all_switches : enabled_switches)) {
            LinearLayout currentcol = (i % 2 == 0) ? left : right;
            LinearLayout l;
            switches.add(l = createAndAttachSwitchCustom(e, currentcol));

            TextView label = ((TextView) l.findViewById(R.id.switch_label));
            com.google.android.material.materialswitch.MaterialSwitch switchitem = (MaterialSwitch) l.findViewById(R.id.switch_item);

            String fullText = label.getText().toString();
            if (fullText.contains(": ")) {
                SpannableString spannableString = new SpannableString(fullText);

                // Color "some text" in red
                int colorindex = fullText.indexOf(": ") + 2;
                spannableString.setSpan(
                        new ForegroundColorSpan(getThemeColor(context, android.R.attr.colorPrimary)),
                        0,
                        colorindex,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );


                label.setText(spannableString);
            }

            if (edit_mode) {

                ImageButton delete = new ImageButton(context);
                // Set the icon. For a button that's primarily an icon, you might not set text.
                delete.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                // You might want to remove any default padding or set a specific size
                // to make it look more like an icon button if it has no text.
                delete.setPadding(0, 0, 0, 0); // Example, might need adjustment


                android.util.TypedValue outValue = new android.util.TypedValue();
                context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
                delete.setBackgroundResource(outValue.resourceId);


                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                delete.setLayoutParams(params);

                delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        switches.remove(l);
                        l.setVisibility(View.GONE);
                        enabled_switches.remove(label.getText().toString());
                        selected_switches.remove(label.getText().toString());
                        all_switches.remove(label.getText().toString());
                        vibrateHaptic(context);
                    }
                });
                switchitem.setChecked(enabled_switches.contains(label.getText().toString()));
                label.setEnabled(switchitem.isChecked());
                switchitem.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        if (b) {
                            enabled_switches.add(label.getText().toString());
                        } else {
                            enabled_switches.remove(label.getText().toString());
                        }
                        label.setEnabled(b);
                        vibrateHaptic(context);
                    }
                });


                CheckBox default_selected = new CheckBox(context);
                default_selected.setChecked(selected_switches.contains(label.getText().toString()));

                default_selected.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        if (b)
                            selected_switches.add(label.getText().toString());
                        else
                            selected_switches.remove(label.getText().toString());
                        vibrateHaptic(context);
                    }
                });

                l.addView(delete, 0);
                l.addView(default_selected, 1);
            } else {
                switchitem.setChecked(selected_switches.contains(label.getText().toString()));
            }
            currentcol.addView(l);
            i++;
        }

    }

    public void addNewSwitch(String label_text) {
        enabled_switches.add(label_text);
        all_switches.add(label_text);

        LinearLayout right = root.findViewById(R.id.right_col), left = root.findViewById(R.id.left_col);

        LinearLayout currentcol = ((all_switches.size() % 2) != 0) ? left : right;
        LinearLayout l;
        switches.add(l = createAndAttachSwitchCustom(label_text, currentcol));

        TextView label = ((TextView) l.findViewById(R.id.switch_label));
        com.google.android.material.materialswitch.MaterialSwitch switchitem = (MaterialSwitch) l.findViewById(R.id.switch_item);

        String fullText = label.getText().toString();
        if (fullText.contains(": ")) {
            SpannableString spannableString = new SpannableString(fullText);

            // Color "some text" in red
            int colorindex = fullText.indexOf(": ") + 2;
            spannableString.setSpan(
                    new ForegroundColorSpan(getThemeColor(context, android.R.attr.colorPrimary)),
                    0,
                    colorindex,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );


            label.setText(spannableString);
        }

        ImageButton delete = new ImageButton(context);
        // Set the icon. For a button that's primarily an icon, you might not set text.
        delete.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        // You might want to remove any default padding or set a specific size
        // to make it look more like an icon button if it has no text.
        delete.setPadding(0, 0, 0, 0); // Example, might need adjustment


        android.util.TypedValue outValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        delete.setBackgroundResource(outValue.resourceId);


        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        delete.setLayoutParams(params);

        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switches.remove(l);
                l.setVisibility(View.GONE);
                enabled_switches.remove(label.getText().toString());
                selected_switches.remove(label.getText().toString());
                all_switches.remove(label.getText().toString());
                vibrateHaptic(context);
            }
        });
        switchitem.setChecked(enabled_switches.contains(label.getText().toString()));
        label.setEnabled(switchitem.isChecked());
        switchitem.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    enabled_switches.add(label.getText().toString());
                } else {
                    enabled_switches.remove(label.getText().toString());
                }
                label.setEnabled(b);
                vibrateHaptic(context);
            }
        });


        CheckBox default_selected = new CheckBox(context);
        default_selected.setChecked(selected_switches.contains(label.getText().toString()));

        default_selected.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b)
                    selected_switches.add(label.getText().toString());
                else
                    selected_switches.remove(label.getText().toString());
                vibrateHaptic(context);
            }
        });

        l.addView(delete, 0);
        l.addView(default_selected, 1);


        currentcol.addView(l);

    }

    public void SaveCurrentSelection() {
        StringBuilder sb_enabled = new StringBuilder();
        StringBuilder sb_all = new StringBuilder();
        StringBuilder sb_default = new StringBuilder();


        vibrateHaptic(context);

        boolean first_1 = true, first_2 = true, first_3 = true;
        for (String e : all_switches) {
            if(e.isEmpty())continue;
            if (first_1) {
                first_1 = false;
            } else sb_all.append(",");
            sb_all.append(e);
        }
        for (String e : enabled_switches) {
            if(e.isEmpty())continue;
            if (first_2) {
                first_2 = false;
            } else sb_enabled.append(",");
            sb_enabled.append(e);
        }
        for (String e : selected_switches) {
            if(e.isEmpty())continue;
            if (first_3) {
                first_3 = false;
            } else sb_default.append(",");
            sb_default.append(e);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString("enabled_elements_switch_array", sb_enabled.toString()).apply();
        prefs.edit().putString("all_elements_switch_array", sb_all.toString()).apply();
        prefs.edit().putString("selected_elements_switch_array", sb_default.toString()).apply();


    }


    public static void vibrateHaptic(Context ctx) {
        Vibrator vibrator = (Vibrator) ctx.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator.hasVibrator()) {
            VibrationEffect ve = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ve = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);
            } else {
                ve = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE);
            }
            vibrator.vibrate(ve);
        }
    }

    @ColorInt
    public static int getThemeColor(Context context, @AttrRes int attr) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
        try {
            return a.getColor(0, 0);
        } finally {
            a.recycle();

        }
    }

}
