package com.example.bruxismdetector;

import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bruxismdetector.bruxism_grapher2.SummaryReader;
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
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SummaryChartFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_charts, container, false);
    }

    View root;
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        root = view;

        bottomCardView = view.findViewById(R.id.bottom_card_view);
        bottomCardView.post(() -> {
            this.collapsedHeightDp = getCollapsedHeight();
            Log.d("CollapsedHeight", "Collapsed height = " + this.collapsedHeightDp);
            this.isExpanded = true;
            toggleCardHeight();
            // Optionally animate to this height
        });


        topHandle = root.findViewById(R.id.top_handle);
        mainLayout = root.findViewById(R.id.main); // The root layout

        setupDrag(); // Call setupDrag() in onCreate()

        ((CheckBox)root.findViewById(R.id.basedrawcheckbox)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                vibrateHaptic();
                if(howManyBoxesAreChecked()>0 || b)
                    recalculateGraphs(null);
            }
        });
        ((CheckBox)root.findViewById(R.id.separatedrawcheckbox)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
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
        Vibrator vibrator = (Vibrator) requireActivity().getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
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
        requireActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                LinearLayout ll = (LinearLayout) root.findViewById(R.id.graphs_holder);

                TextView noResultText = (TextView) root.findViewById(R.id.noresultext);

                if (noResultText.getVisibility() == View.VISIBLE && !((CheckBox)root.findViewById(R.id.basedrawcheckbox)).isChecked()) {
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
                return infoextracted.contains(filterNames.get(conditionindex));
            }else {
                return !infoextracted.contains(filterNames.get(conditionindex));
            }
        }
        return true;
    }

    void addGraphs(){

        for(int i = 1; i < summaryTitles.length-2; i++) {
            createChartWithDateFromIndex(i);
        }

    }

    int[] color_array = {R.color.material_orange_500, R.color.material_green_500, R.color.material_blue_500, R.color.material_red_500, R.color.material_yellow_500};
    void createChartWithDateFromIndex(int index){

        boolean doNotAddToView = false;

        StringBuilder desc = new StringBuilder();

        int howmanychecked = howManyBoxesAreChecked();
        ArrayList<LineDataSet> a = new ArrayList<>();
        if(((CheckBox)root.findViewById(R.id.basedrawcheckbox)).isChecked())
            a.add(makeDatasetWithDate(index, summaryTitles[index], false, -1, getResources().getColor(R.color.material_blue_500)));
        if(((CheckBox)root.findViewById(R.id.separatedrawcheckbox)).isChecked()){
            ((TextView)root.findViewById(R.id.noresultext)).setVisibility(View.GONE);

            for(int i = 0; i < howmanychecked;i++){
                a.add(makeDatasetWithDate(index, (((MaterialSwitch) switchesbox.get(getCheckedBoxIndex(i)).getChildAt(1)).isChecked()? "" : "Not ")+filterNames.get(getCheckedBoxIndex(i)), true, getCheckedBoxIndex(i), getResources().getColor(color_array[i%color_array.length])));
            }
        }else if(howmanychecked>0){
            a.add(makeDatasetWithDate(index, "Filtered", true, -1, getResources().getColor(R.color.material_orange_500)));

            if(a.get(a.size()-1).getEntryCount()==0){
                ((TextView)root.findViewById(R.id.noresultext)).setVisibility(View.VISIBLE);
                if(!((CheckBox)root.findViewById(R.id.basedrawcheckbox)).isChecked())
                    doNotAddToView=true;
            }else{
                ((TextView)root.findViewById(R.id.noresultext)).setVisibility(View.GONE);

            }

            for(int i = 0; i < howmanychecked; i++){
                desc.append(i != 0 ? " + " : "").append(((MaterialSwitch) switchesbox.get(getCheckedBoxIndex(i)).getChildAt(1)).isChecked() ? "" : "Not ").append(filterNames.get(getCheckedBoxIndex(i)));
            }
        }

        if(!doNotAddToView)
            addLineChartToView(getLineChart(a, desc.toString(), ((CheckBox)root.findViewById(R.id.separatedrawcheckbox)).isChecked()), summaryTitles[index]);



    }

    void addLineChartToView(LineChart lc, String title){

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(16, 8, 8, 16);

        com.google.android.material.card.MaterialCardView cv = new com.google.android.material.card.MaterialCardView(requireActivity());
        Space space = new Space(requireActivity());
        space.setMinimumHeight(20);
        Space space2 = new Space(requireActivity());
        space2.setMinimumHeight(20);
        LinearLayout llc = new LinearLayout(requireActivity());
        llc.setOrientation(LinearLayout.VERTICAL);
        TextView txtitle = new TextView(requireActivity());
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
        cv.setElevation(4);
        cv.addView(llc);

        LinearLayout ll = (LinearLayout) root.findViewById(R.id.graphs_holder);
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


        int textColor = ((CheckBox)root.findViewById(R.id.basedrawcheckbox)).getCurrentTextColor();

        LineChart lc = new LineChart(requireActivity());



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
        CheckBox cb = new CheckBox(requireActivity());
        TextView tw = new TextView(requireActivity());
        tw.setTextSize(11);
        //tw.setMaxWidth(250);
        tw.setSingleLine(false);

        //tw.setGravity(Gravity.CENTER);
        tw.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        tw.setPadding(10, 0, 0, 0);
        MaterialSwitch sw = new MaterialSwitch(requireActivity());
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

        LinearLayout lll = new LinearLayout(requireActivity());
        lll.setGravity(Gravity.FILL_HORIZONTAL);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        lll.addView(cb);
        lll.addView(sw);
        lll.addView(tw);
        return lll;
    }
    void createFiltersSwitches(){

        LinearLayout right = root.findViewById(R.id.right_col), left=root.findViewById(R.id.left_col);

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
    ArrayList<String[]> summaryTuples;
    ArrayList<String> dateLabels;
    ArrayList<String> filterNames;
    void readSummary() {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File recordingsDir = new File(documentsDir, "RECORDINGS");
        File summaryDir = new File(recordingsDir, "Summary");

        SummaryReader.setFilepath(summaryDir.getParent() + "/Summary/Summary.csv");


        SummaryReader sr = SummaryReader.getInstance();
        summaryTitles = sr.getSummaryTitles();
        summaryTuples = sr.getSummaryTuplesWithNoSkipItems();
        dateLabels = sr.getDateLabelsWithNoSkipItems();
        filterNames = sr.getFilterNames();
    }


    private boolean isExpanded = false;
    private int collapsedHeightDp = 50;

    private com.google.android.material.bottomnavigation.BottomNavigationView bottomCardView;
    private View topHandle;
    private ViewGroup mainLayout;
    private void setupDrag() {
        final GestureDetector gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                toggleCardHeight();
                return true;
            }
        });

        topHandle.setOnTouchListener(new View.OnTouchListener() {
            private float initialY;
            private int initialHeight;
            private boolean isResizing;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (gestureDetector.onTouchEvent(event)) {
                    return true; // Handle tap
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isResizing = true;
                        initialY = event.getRawY();
                        initialHeight = bottomCardView.getHeight();
                        if (mainLayout != null) {
                            mainLayout.setMotionEventSplittingEnabled(false);
                        }
                        vibrateHaptic();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (isResizing) {
                            float dy = event.getRawY() - initialY;
                            int newHeight = (int) (initialHeight - dy);
                            int minHeight = dpToPx(collapsedHeightDp);
                            Integer maxHeight = (mainLayout != null) ? mainLayout.getHeight() : null;

                            newHeight = Math.max(minHeight, newHeight);
                            if (maxHeight != null) {
                                newHeight = Math.min(maxHeight, newHeight);
                            }

                            bottomCardView.getLayoutParams().height = newHeight;
                            bottomCardView.requestLayout();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isResizing = false;
                        if (mainLayout != null) {
                            mainLayout.setMotionEventSplittingEnabled(true);
                        }
                        return true;
                    default:
                        return false;
                }
            }


        });
    }

    private int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return (int) (dp * displayMetrics.density + 0.5f);
    }

    private int getCollapsedHeight() {
        int totalHeight = 0;


        // 3. Checkbox row
        View checkboxes = bottomCardView.findViewById(R.id.basedrawcheckbox).getParent() instanceof View ?
                (View) bottomCardView.findViewById(R.id.basedrawcheckbox).getParent() : null;
        if (checkboxes != null) totalHeight += checkboxes.getMeasuredHeight() + checkboxes.getPaddingBottom()-20;


        return totalHeight;
    }

    private void toggleCardHeight() {
        int targetHeight = isExpanded ? dpToPx(collapsedHeightDp) : getExpandedHeightCapped();

        ValueAnimator animator = ValueAnimator.ofInt(bottomCardView.getHeight(), targetHeight);
        animator.setDuration(250);
        animator.addUpdateListener(animation -> {
            bottomCardView.getLayoutParams().height = (int) animation.getAnimatedValue();
            bottomCardView.requestLayout();
        });
        animator.start();

        isExpanded = !isExpanded;
    }


    private int getExpandedHeightCapped() {
        // Get content height
        View scrollContent = bottomCardView.findViewById(R.id.scroll_filters); // Replace with your ScrollView ID
        if (scrollContent == null) return dpToPx(300); // fallback

        scrollContent.measure(
                View.MeasureSpec.makeMeasureSpec(bottomCardView.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED
        );
        int contentHeight = scrollContent.getMeasuredHeight()+300;

        // Cap at 80% of screen height
        int maxHeight = (int) (Resources.getSystem().getDisplayMetrics().heightPixels * 0.8);
        return Math.min(contentHeight, maxHeight);
    }



}
