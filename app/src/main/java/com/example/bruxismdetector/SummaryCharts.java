package com.example.bruxismdetector;

import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bruxismdetector.bruxism_grapher2.Event;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.elevation.SurfaceColors;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SummaryCharts extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_summary_charts);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bottomCardView = findViewById(R.id.bottom_card_view);
        topHandle = findViewById(R.id.top_handle);
        mainLayout = findViewById(R.id.main); // The root layout

        setupDrag(); // Call setupDrag() in onCreate()

        ((CheckBox)findViewById(R.id.basedrawcheckbox)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                vibrateHaptic();
                if(howManyBoxesAreChecked()>0 || b)
                    recalculateGraphs(null);
            }
        });
        ((CheckBox)findViewById(R.id.separatedrawcheckbox)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                vibrateHaptic();
                recalculateGraphs(null);
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                readSummary();
                recalculateGraphs(null);
            }
        }).start();
    }


    void vibrateHaptic(){
        Vibrator vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator.hasVibrator()) {
            VibrationEffect ve = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ve = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);
            }else{
                ve = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE);
            }
            vibrator.vibrate(ve);
        }
    }
    public void recalculateGraphs(View v){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                LinearLayout ll = (LinearLayout) findViewById(R.id.graphs_holder);

                TextView noResultText = (TextView) findViewById(R.id.noresultext);

                if (noResultText.getVisibility() == View.VISIBLE && !((CheckBox)findViewById(R.id.basedrawcheckbox)).isChecked()) {
                    LayoutTransition layoutTransition = new LayoutTransition();
                    ll.setLayoutTransition(layoutTransition);
                } else {
                    // Optionally, if you want to remove any existing transitions when the TextView is not visible
                    ll.setLayoutTransition(null);
                }

                ll.removeAllViews();
                if (switchesbox.isEmpty())
                    createFiltersSwitches();
                addGraphs();
            }
        });
    }

    LineDataSet makeDatasetWithDate(int datatupleindex, String setlabel, boolean usefilter, int filterindex, int c1){
        List<Entry> entries = new ArrayList<>();
        for(String[] data : summaryTuples) {
            if(isTupleCompliantFilter(data, filterindex) || !usefilter)
                entries.add(new Entry(dateLabels.indexOf(data[0]), Float.parseFloat(data[datatupleindex].replace(",","."))));
        }
        LineDataSet dataSet = new LineDataSet(entries, setlabel); // add entries to dataset
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setColor(c1);
        dataSet.setValueTextColor(Color.GREEN); // styling, ...
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(false);
        dataSet.setDrawCircleHole(false);
        dataSet.setDrawCircles(false);

        return dataSet;
    }

    private boolean isTupleCompliantFilter(String[] data, int conditionindex) {
        ArrayList<String> infoextracted = new ArrayList<>(Arrays.asList(data[data.length-1].split(",")));

        if(conditionindex==-1) {
            for (int i = 0; i < switchesbox.size(); i++) {
                if (((CheckBox) switchesbox.get(i).getChildAt(0)).isChecked()) {
                    if (((MaterialSwitch) switchesbox.get(i).getChildAt(1)).isChecked()) {
                        if (!infoextracted.contains(filterNames.get(i))) {
                            return false;
                        }
                    }else {
                        if (infoextracted.contains(filterNames.get(i))) {
                            return false;
                        }
                    }
                }
            }
        }else{
            if (((MaterialSwitch) switchesbox.get(conditionindex).getChildAt(1)).isChecked()) {
                if (!infoextracted.contains(filterNames.get(conditionindex))) {
                    return false;
                }
            }else {
                if (infoextracted.contains(filterNames.get(conditionindex))) {
                    return false;
                }
            }
        }
        return true;
    }

    void addGraphs(){


        createChartWithDateFromIndex(2);
        createChartWithDateFromIndex(3);
        createChartWithDateFromIndex(7);
        createChartWithDateFromIndex(8);
        createChartWithDateFromIndex(9);
        createChartWithDateFromIndex(10);
        createChartWithDateFromIndex(11);
        createChartWithDateFromIndex(12);


    }

    int[] color_array = {R.color.material_orange_500, R.color.material_green_500, R.color.material_blue_500, R.color.material_red_500, R.color.material_yellow_500};
    void createChartWithDateFromIndex(int index){

        boolean doNotAddToView = false;

        String desc = "";

        int howmanychecked = howManyBoxesAreChecked();
        ArrayList<LineDataSet> a = new ArrayList<>();
        if(((CheckBox)findViewById(R.id.basedrawcheckbox)).isChecked())
            a.add(makeDatasetWithDate(index, summaryTitles[index], false, -1, getResources().getColor(R.color.material_blue_500)));
        if(((CheckBox)findViewById(R.id.separatedrawcheckbox)).isChecked()){
            ((TextView)findViewById(R.id.noresultext)).setVisibility(View.GONE);

            for(int i = 0; i < howmanychecked;i++){
                a.add(makeDatasetWithDate(index, (((MaterialSwitch) switchesbox.get(getCheckedBoxIndex(i)).getChildAt(1)).isChecked()? "" : "Not ")+filterNames.get(getCheckedBoxIndex(i)), true, getCheckedBoxIndex(i), getResources().getColor(color_array[i%color_array.length])));
            }
        }else if(howmanychecked>0){
            a.add(makeDatasetWithDate(index, "Filtered", true, -1, getResources().getColor(R.color.material_orange_500)));

            if(a.get(a.size()-1).getEntryCount()==0){
                ((TextView)findViewById(R.id.noresultext)).setVisibility(View.VISIBLE);
                if(!((CheckBox)findViewById(R.id.basedrawcheckbox)).isChecked())
                    doNotAddToView=true;
            }else{
                ((TextView)findViewById(R.id.noresultext)).setVisibility(View.GONE);

            }

            for(int i = 0; i < howmanychecked; i++){
                desc += (i!=0?" + ":"")+(((MaterialSwitch) switchesbox.get(getCheckedBoxIndex(i)).getChildAt(1)).isChecked()? "" : "Not ")+filterNames.get(getCheckedBoxIndex(i));
            }
        }

        if(!doNotAddToView)
            addLineChartToView(getLineChart(a, desc, ((CheckBox)findViewById(R.id.separatedrawcheckbox)).isChecked()), summaryTitles[index]);



    }

    void addLineChartToView(LineChart lc, String title){

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(16, 8, 8, 16);

        com.google.android.material.card.MaterialCardView cv = new com.google.android.material.card.MaterialCardView(this);
        Space space = new Space(this);
        space.setMinimumHeight(20);
        Space space2 = new Space(this);
        space2.setMinimumHeight(20);
        LinearLayout llc = new LinearLayout(this);
        llc.setOrientation(LinearLayout.VERTICAL);
        TextView txtitle = new TextView(this);
        txtitle.setText(title);
        txtitle.setTextSize(14);
        txtitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        llc.addView(space2);
        llc.addView(txtitle);
        llc.addView(space);
        llc.addView(lc);
        cv.setLayoutParams(params);
        cv.setRadius(24);
        cv.setPadding(16,40,16,16);
        cv.setElevation(20);
        cv.addView(llc);

        LinearLayout ll = (LinearLayout) findViewById(R.id.graphs_holder);
        ll.addView(cv);
    }

    private LineChart getLineChart(LineDataSet dataSet, String desc, boolean useLegend) {
        ArrayList<LineDataSet> ds = new ArrayList<>();
        ds.add(dataSet);
        return getLineChart(ds, desc, useLegend);
    }
        @NonNull
    private LineChart getLineChart(ArrayList<LineDataSet> dataSets, String desc, boolean useLegend) {

            LineData lineData = new LineData();
            for(LineDataSet lds : dataSets) {
                lineData.addDataSet(lds);
            }

            ValueFormatter formatter = new ValueFormatter() {
                @Override
                public String getAxisLabel(float value, AxisBase axis) {
                    return dateLabels.get((int) value);
                }
            };


            int textColor = ((CheckBox)findViewById(R.id.basedrawcheckbox)).getCurrentTextColor();

            LineChart lc = new LineChart(this);



            XAxis xAxis = lc.getXAxis();
            xAxis.setGranularity(1f); // minimum axis-step (interval) is 1
            xAxis.setValueFormatter(formatter);
            xAxis.setLabelRotationAngle((float) Math.PI / 4f);

            lc.getAxis(YAxis.AxisDependency.LEFT).setDrawLabels(false);
            lc.getAxis(YAxis.AxisDependency.RIGHT).setDrawLabels(false);

            lc.setDrawGridBackground(false);
            lc.setDrawBorders(false);

            Description d = new Description();
            d.setText(desc);
            d.setYOffset(-10);
            lc.setDescription(d);

            lc.setDrawMarkers(false);

            lc.getLegend().setEnabled(useLegend);

            lc.getRootView().setMinimumHeight(500);

            lc.setData(lineData);

            lc.getDescription().setTextColor(textColor);
            lc.getXAxis().setTextColor(textColor);
            lc.getAxisLeft().setTextColor(textColor);
            lc.getAxisRight().setTextColor(textColor);
            lc.getLegend().setTextColor(textColor);
            lc.getDescription().setTextColor(textColor);

        lc.invalidate();
        return lc;
    }

    ArrayList<String> filterNames = new ArrayList<>();

    void addFilter(String[] names){
        for(String s : names)
            addFilter(s);
    }
    void addFilter(String name){
        if(!filterNames.contains(name))
            filterNames.add(name);
    }

    ArrayList<LinearLayout> switchesbox = new ArrayList<>();

    int howManyBoxesAreChecked(){
        int c = 0;
        for(LinearLayout l : switchesbox){
            if(((CheckBox)l.getChildAt(0)).isChecked())
                c++;
        }
        return c;
    }

    /**
     *
     * @param index from 0 to howManyBoxesAreChecked()-1
     * @return index in switchesbox of the n checkbox that is checked
     */
    int getCheckedBoxIndex(int index){
        int c = 0;
        int i = 0;
        for(LinearLayout l : switchesbox){
            if(((CheckBox)l.getChildAt(0)).isChecked()) {
                if(c==index)
                    return i;
                c++;
            }
            i++;
        }
        return -1;
    }

    LinearLayout createFilterCheckSwitch(String checkboxtext){
        CheckBox cb = new CheckBox(this);
        TextView tw = new TextView(this);
        tw.setTextSize(11);
        //tw.setMaxWidth(250);
        tw.setSingleLine(false);

        //tw.setGravity(Gravity.CENTER);
        tw.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        tw.setPadding(10, 0, 0, 0);
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setChecked(true);
        sw.setEnabled(false);
        tw.setText(checkboxtext);
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                sw.setEnabled(b);
                if(!b)
                    sw.setChecked(true);
                vibrateHaptic();
                recalculateGraphs(null);
            }
        });
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                vibrateHaptic();
                recalculateGraphs(null);
            }
        });

        cb.setChecked(false);

        LinearLayout lll = new LinearLayout(this);
        lll.setGravity(Gravity.FILL_HORIZONTAL);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        lll.addView(cb);
        lll.addView(sw);
        lll.addView(tw);
        return lll;
    }
    void createFiltersSwitches(){

        LinearLayout right = findViewById(R.id.right_col), left=findViewById(R.id.left_col);

        int i = 0;

        for(String e : filterNames){
            LinearLayout currentcol = (i%2==0) ? left : right;
            LinearLayout l=createFilterCheckSwitch(e);
            switchesbox.add(l);
            currentcol.addView(l);
            i++;
        }
    }

    String[] summaryTitles;
    ArrayList<String[]> summaryTuples = new ArrayList<>();
    ArrayList<String> dateLabels = new ArrayList<>();
    void readSummary() {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File recordingsDir = new File(documentsDir, "RECORDINGS");
        File summaryDir = new File(recordingsDir, "Summary");

        ArrayList<String> titles = new ArrayList<>();
        int infoindex=-1;

        try (BufferedReader br = new BufferedReader(new FileReader(summaryDir.getParent() + "/Summary/Summary.csv"))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    summaryTitles = line.split(";");
                    titles = new ArrayList<>(Arrays.asList(summaryTitles));
                    infoindex = titles.indexOf("Info");
                    if(infoindex==-1)
                        throw new Exception("Summary does not have Info!");
                    continue;
                }
                String[] splitline = line.split(";");
                summaryTuples.add(splitline);
                dateLabels.add(splitline[0]);

                if(splitline.length > infoindex)
                    addFilter(splitline[infoindex].split(","));
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + (summaryDir.getParent() + "/Summary/Summary.csv"));
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private com.google.android.material.card.MaterialCardView bottomCardView;
    private View topHandle;
    private ViewGroup mainLayout;
    private void setupDrag() {
        topHandle.setOnTouchListener(new View.OnTouchListener() {
            private float initialY;
            private int initialHeight;
            private boolean isResizing;


            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isResizing = true;
                        initialY = event.getRawY();
                        initialHeight = bottomCardView.getHeight();
                        // Disable parent scrolling, if you have any.
                        if (mainLayout != null) {
                            mainLayout.setMotionEventSplittingEnabled(false);
                        }

                        vibrateHaptic();

                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (isResizing) {
                            float dy = event.getRawY() - initialY;
                            int newHeight = (int) (initialHeight - dy);

                            // Constrain the height
                            int minHeight = dpToPx(100); // Minimum height
                            Integer maxHeight = (mainLayout != null) ? mainLayout.getHeight() : null; // Maximum height

                            if (newHeight < minHeight) {
                                newHeight = minHeight;
                            }
                            if (maxHeight != null && newHeight > maxHeight) {
                                newHeight = maxHeight;
                            }

                            bottomCardView.getLayoutParams().height = newHeight;
                            bottomCardView.requestLayout();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isResizing = false;
                        // Re-enable parent scrolling, if you have any.
                        if (mainLayout != null) {
                            mainLayout.setMotionEventSplittingEnabled(true);
                        }
                        return true;
                    default:
                        return false;
                }
            }

            // Helper method to convert dp to pixels
            private int dpToPx(int dp) {
                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                return (int) (dp * displayMetrics.density + 0.5f);
            }
        });
    }

}