package com.example.bruxismdetector;

import android.animation.LayoutTransition;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bruxismdetector.bruxism_grapher2.Correlations;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class CorrelationsFragment extends Fragment {

    View root;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {



        return inflater.inflate(R.layout.fragment_correlations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        root = view;

        new Thread(new Runnable() {
            @Override
            public void run() {
                readSummary();
                prepareCorrelationArrays();
            }
        }).start();


        ((CheckBox)root.findViewById(R.id.seeallcheckbox)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                //prepareCorrelationArrays();
                LinearLayout ll = (LinearLayout) root.findViewById(R.id.correlations_holder);


                LayoutTransition layoutTransition = new LayoutTransition();
                ll.setLayoutTransition(layoutTransition);

                for(CardWithRows a : cards){
                    if(b)
                        a.show();
                    else
                        a.hideIf();
                }

                ll.setLayoutTransition(null);

            }
        });
    }

    double threshold = 0.2;
    boolean[] positiveIfIncreased = {
            false,  // Clenching Rate (per hour) → lower is better
            false,  // Jaw Events → fewer events is better
            false,  // Alarm Triggers → fewer alarms = better
            false,  // Beep Count → fewer beeps = better
            true,   // Button Presses → user actively responded
            true,   // Stopped after beep → good, indicates awareness
            false,  // Avg beeps per event → lower is better (faster response)
            false,  // Alarm % → fewer alarm-level events is better
            true,   // Average clenching event pause → longer gaps between events = better
            false,  // Average clenching duration → shorter clenches = better
            false,  // Total clench time → less overall clenching = better
            false   // Active time (permille) → lower activity during sleep = better
    };

    boolean isGoingToBetter(double correlation, int index) throws IndexOutOfBoundsException {
        if(index < positiveIfIncreased.length){
            return (correlation > 0) == positiveIfIncreased[index];
        }else{
            throw new IndexOutOfBoundsException();
        }
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

    public static class CardWithRows{
        com.google.android.material.card.MaterialCardView card;
        ArrayList<ThresholdRow> rows = new ArrayList<>();
        public CardWithRows(com.google.android.material.card.MaterialCardView cv) {
            card = cv;
        }

        void addToArray(ThresholdRow tr){
            rows.add(tr);
        }
        void hideIf(){
            boolean hid_all = true;
            for(ThresholdRow tr : rows){
                if(tr.hideIfThreshold()){
                    hid_all = false;
                }
            }
            if(hid_all)
                card.setVisibility(View.GONE);
        }
        void show(){
            for(ThresholdRow tr : rows){
                tr.show();
            }
            card.setVisibility(View.VISIBLE);
        }
    };
    public static class ThresholdRow {
        public ThresholdRow(LinearLayout ll, boolean didmeet) {
            this.row = ll;
            this.did_meet_threshold = didmeet;
            row.setAlpha(1f);
        }

        LinearLayout row;
        boolean did_meet_threshold = false;

        public boolean hideIfThreshold() {
            if (!did_meet_threshold) {
                /*row.post(() -> {
                    row.animate()
                            .alpha(0f)
                            .setDuration(100)
                            .withEndAction(() -> row.setVisibility(View.GONE))
                            .start();
                });*/

                row.setVisibility(View.GONE);
            }
            return did_meet_threshold;
        }

        public void show() {
            if (row.getVisibility() != View.VISIBLE) {
                /*row.post(() -> {
                    row.setAlpha(0f);
                    row.setVisibility(View.VISIBLE);
                    row.animate()
                            .alpha(1f)
                            .setDuration(100)
                            .start();
                });*/

                row.setVisibility(View.VISIBLE);
            }
        }
    }

    ArrayList<CardWithRows> cards = new ArrayList<>();
    void prepareCorrelationArrays(){

        // We will create a matrix, where
        //  filter1-contained in tuple1?, filter1-contained in tuple2?, ecc
        //  filter2-contained in tuple1?, filter2-contained in tuple2?, ecc

        // total length - 3 : (skip date, time, info)
        int effectivedatalength = summaryTuples.get(0).length-3;

        double[][] filterstats = new double[filterNames.size()][summaryTuples.size()];
        double[][] entries = new double[effectivedatalength][summaryTuples.size()];

        int[] filterhitcount = new int[filterNames.size()];

        for(int chartelement = 0; chartelement < effectivedatalength; chartelement++) {
            for (int tuple = 0; tuple < summaryTuples.size(); tuple++) {
                entries[chartelement][tuple] = Double.parseDouble(summaryTuples.get(tuple)[chartelement+2].replace(",","."));
            }
        }

        for(int filter = 0; filter < filterNames.size(); filter++){
            for (int tuple = 0; tuple < summaryTuples.size(); tuple++) {
                boolean hit = summaryTuples.get(tuple)[summaryTuples.get(tuple).length - 1].contains(filterNames.get(filter));
                filterstats[filter][tuple] = hit ? 1.0 : 0.0;
                if(hit)
                    filterhitcount[filter]++;
            }
        }

        double[][] correlations = new double[filterNames.size()][effectivedatalength];

        for(int filter = 0; filter < filterNames.size(); filter++) {
            for (int chartelement = 0; chartelement < effectivedatalength; chartelement++) {
                correlations[filter][chartelement] = ((int)(Correlations.pearsonCorrelation(entries[chartelement], filterstats[filter])*100.0))/100.0;
            }
        }

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {


                ((LinearLayout)root.findViewById(R.id.correlations_holder)).removeAllViews();
            }
        });

        for(int filter = 0; filter < filterNames.size(); filter++) {

            int posnegcount = 0;



            com.google.android.material.card.MaterialCardView cv = new com.google.android.material.card.MaterialCardView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(16, 8, 8, 16);
            cv.setLayoutParams(params);
            cv.setRadius(24);
            cv.setPadding(16,40,16,16);
            cv.setElevation(4);

            CardWithRows cwr = new CardWithRows(cv);

            LinearLayout ll1 = new LinearLayout(requireContext());
            ll1.setOrientation(LinearLayout.VERTICAL);

            LayoutTransition transition = new LayoutTransition();
            transition.enableTransitionType(LayoutTransition.CHANGING);
            ll1.setLayoutTransition(transition);


            LinearLayout ll = new LinearLayout(requireContext());
            ll.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(requireContext());
            title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            title.setTextSize(14);
            title.setText(filterNames.get(filter));

            ll.setPadding(16,16,16,16);
            ll1.addView(title);
            ll1.addView(ll);
            for(int i = 0; i < effectivedatalength; i++){

                int rowHeight = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());

                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        rowHeight
                ));
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, 8, 0, 8);  // Vertical spacing

                String finaltext = summaryTitles[i + 2];
                if (finaltext.contains("(")) {
                    finaltext = finaltext.substring(0, finaltext.indexOf("(") - 1);
                }

                TextView label = new TextView(requireContext());
                label.setText(finaltext);
                label.setTextSize(12);
                label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
                label.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                label.setPadding(0, 0, 16, 0);
                label.setMaxLines(3); // Optional: allow wrapping to a few lines
                //label.setEllipsize(TextUtils.TruncateAt.END);

                // Enable text justification if API >= 29
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    //label.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
                }


                double corr = correlations[filter][i];
                double absCorr = Math.min(1f, Math.abs(corr));
                boolean good = isGoingToBetter(corr, i);
                if(Math.abs(correlations[filter][i])>=threshold)
                    posnegcount += good ? 1 : -1;

                LinearLayout barContainer = new LinearLayout(requireContext());
                barContainer.setOrientation(LinearLayout.HORIZONTAL);
                barContainer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2));
                barContainer.setPadding(4, 8, 4, 8); // top/bottom padding for centering
                barContainer.setBackgroundColor(getResources().getColor(R.color.seekbar_track_background, cv.getContext().getTheme()));
                barContainer.setGravity(Gravity.CENTER_VERTICAL);

// Bar elements
                View negFill = new View(requireContext());
                negFill.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (corr < 0) ? (float) absCorr : 0));
                negFill.setBackgroundColor(getResources().getColor(good ? R.color.material_green_500 : R.color.material_red_500, cv.getContext().getTheme()));

                View centerLine = new View(requireContext());
                LinearLayout.LayoutParams centerParams = new LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT);
                centerLine.setLayoutParams(centerParams);
                centerLine.setBackgroundColor(getResources().getColor(R.color.black, cv.getContext().getTheme()));

                View posFill = new View(requireContext());
                posFill.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (corr > 0) ? (float) absCorr : 0));
                posFill.setBackgroundColor(getResources().getColor(good ? R.color.material_green_500 : R.color.material_red_500, cv.getContext().getTheme()));

                View leftSpacer = new View(requireContext());
                leftSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (corr < 0) ? (float) (1f - absCorr) : 1f));
                View rightSpacer = new View(requireContext());
                rightSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (corr > 0) ? (float) (1f - absCorr) : 1f));

                barContainer.addView(leftSpacer);
                barContainer.addView(negFill);
                barContainer.addView(centerLine);
                barContainer.addView(posFill);
                barContainer.addView(rightSpacer);

                TextView valueLabel = new TextView(requireContext());
                valueLabel.setText((corr > 0 ? "+" : "") + String.format(Locale.US, "%.2f", corr));
                valueLabel.setTextSize(12);
                valueLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.6f));
                valueLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                valueLabel.setPadding(8, 0, 16, 0); // left and right spacing

                    valueLabel.setTextColor(getResources().getColor(
                            Math.abs(correlations[filter][i])>threshold ?
                        good ? R.color.material_green_500 : R.color.material_red_500
                            : R.color.material_blue_500,
                        cv.getContext().getTheme()
                ));

                row.addView(label);
                row.addView(barContainer);
                row.addView(valueLabel);
                ll.addView(row);

                ThresholdRow tr = new ThresholdRow(row, Math.abs(correlations[filter][i])>=threshold);
                tr.hideIfThreshold();
                cwr.addToArray(tr);


            }
            cards.add(cwr);
            cwr.hideIf();

            String[] evals = new String[]{"Negative", "Mostly negative", "Neutral", "Mostly positive", "Positive"};
            int ab = Math.abs(posnegcount);
            boolean positive = posnegcount>0;
            int selected = 2;
            int c = getResources().getColor(R.color.material_blue_500,cv.getContext().getTheme());
            if(ab<2){
                selected = 2;
            }else if (ab < 3){
                selected = positive ? 3 : 1;
                c = positive ? getResources().getColor(R.color.material_green_500,cv.getContext().getTheme()) : getResources().getColor(R.color.material_red_500,cv.getContext().getTheme());

            }else {
                selected = positive ? 4 : 0;
                c = positive ? getResources().getColor(R.color.material_green_500,cv.getContext().getTheme()) : getResources().getColor(R.color.material_red_500,cv.getContext().getTheme());
            }

            TextView tvsel = new TextView(requireContext());
            tvsel.setTextSize(12);
            tvsel.setText(evals[selected]);
            tvsel.setTextColor(c);
            tvsel.setGravity(Gravity.CENTER);
            tvsel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            ll.addView(tvsel);

            TextView tvhit = new TextView(requireContext());
            StringBuilder hittext = new StringBuilder();
            hittext.append("Hits: ").append(filterhitcount[filter]);
            if(filterhitcount[filter]<20){
                hittext.append("\nYou don't have enough data yet");
                tvhit.setTextColor(getResources().getColor(R.color.material_orange_500,cv.getContext().getTheme()));
            }


            tvhit.setTextSize(12);
            tvhit.setText(hittext.toString());
            tvhit.setGravity(Gravity.CENTER);
            tvhit.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            ll.addView(tvhit);



            if(ll.getChildCount() > 2) {
                cv.addView(ll1);
                requireActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        ((LinearLayout) root.findViewById(R.id.correlations_holder)).addView(cv);
                    }
                });
            }

        }
    }

}
