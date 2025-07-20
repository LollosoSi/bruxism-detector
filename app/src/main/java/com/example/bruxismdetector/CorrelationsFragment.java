package com.example.bruxismdetector;

import android.animation.LayoutTransition;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
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
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import com.example.bruxismdetector.bruxism_grapher2.Correlations;
import com.example.bruxismdetector.bruxism_grapher2.SummaryReader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class CorrelationsFragment extends Fragment {
    boolean show_error = false;

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
                if(show_error){
                    ((TextView)root.findViewById(R.id.errortext)).setText("There was an error while interpreting the data.\nAre you up to date?");
                    ((TextView)root.findViewById(R.id.errortext)).setTextColor(getResources().getColor(R.color.material_red_500, requireContext().getTheme()));
                }
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
    static final byte PositiveCorr = 1, NegativeCorr = 2, NeutralCorr = 0;

    ArrayList<String> positiveIncreased = new ArrayList<>(Arrays.asList(
            "Stopped after beep %",
            "Average clenching event pause (minutes)",
            "Stopped after beep"
    ));
    ArrayList<String> negativeIncreased = new ArrayList<>(Arrays.asList(
            "Total clench time (seconds)",
            "Active time (permille)",
            "Clenching Rate (per hour)",
            "Avg beeps per event",
            "Average clenching duration (seconds)",
            "Jaw Events",
            "Beep Count",
            "Alarm Triggers",
            "Alarm %"
    ));
    byte isGoingToBetter(double correlation, String tablelabel) throws IndexOutOfBoundsException {


            boolean cc1 = (positiveIncreased.contains(tablelabel));
            boolean cc2 = (negativeIncreased.contains(tablelabel));

            boolean c1 = (correlation>0);
            boolean c2 = (correlation<0);

            boolean b1 = c1 && cc1;
            boolean b2 = c2 && cc2;

            byte res = !(cc1||cc2) ? NeutralCorr : (b1 || b2 ? PositiveCorr : NegativeCorr);
            String[] debugres = {"Neutral","Positive","Negative"};

            Log.i("Filter", "Label: " + tablelabel + " Corr: " + correlation + " c1: "+c1+" c2: "+c2 + " cc1: " + cc1 + " cc2: " + cc2 + " b1: " + b1 + " b2: " + b2 + " Result: " + debugres[res]);



            return res;

    }

    int infoindex = -1;
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
        infoindex = sr.getInfomationIndex();
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

        // total length - 3 : (skip date, mood, info)
        int effectivedatalength = summaryTuples.get(0).length-3;
        int startcolumn = 1;

        double[][] filterstats = new double[filterNames.size()][summaryTuples.size()];
        double[][] entries = new double[effectivedatalength][summaryTuples.size()];

        int[] filterhitcount = new int[filterNames.size()];

        for(int chartelement = 0; chartelement < effectivedatalength; chartelement++) {
            for (int tuple = 0; tuple < summaryTuples.size(); tuple++) {
                entries[chartelement][tuple] = Double.parseDouble(summaryTuples.get(tuple)[chartelement+startcolumn].replace(",","."));
            }
        }

        for(int filter = 0; filter < filterNames.size(); filter++){
            for (int tuple = 0; tuple < summaryTuples.size(); tuple++) {
                boolean hit = summaryTuples.get(tuple)[infoindex].contains(filterNames.get(filter));
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

                String finaltext = summaryTitles[i+startcolumn];
                if (finaltext.contains("(")) {
                    finaltext = finaltext.substring(0, finaltext.indexOf("(") - 1);
                }

                TextView label = new TextView(requireContext());
                label.setText(finaltext);
                label.setTextSize(11);
                label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
                label.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                label.setPadding(0, 0, 16, 0);

                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                        label,
                        8,   // min size in SP
                        14,  // max size in SP
                        1,   // step size in SP
                        TypedValue.COMPLEX_UNIT_SP
                );


                double corr = correlations[filter][i];
                double absCorr = Math.min(1f, Math.abs(corr));
                //Log.i("filter", "F: "+i);
                byte good = isGoingToBetter(corr, summaryTitles[i+startcolumn]);
                //Log.i("filter", summaryTitles[i]);
                if(Math.abs(correlations[filter][i])>=threshold)
                    posnegcount += good == PositiveCorr ? 1 : (good == NegativeCorr ? -1 : 0);

                LinearLayout barContainer = new LinearLayout(requireContext());
                barContainer.setOrientation(LinearLayout.HORIZONTAL);
                barContainer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2));
                barContainer.setPadding(4, 8, 4, 8); // top/bottom padding for centering
                barContainer.setBackgroundColor(getResources().getColor(R.color.seekbar_track_background, cv.getContext().getTheme()));
                barContainer.setGravity(Gravity.CENTER_VERTICAL);

// Bar elements
                View negFill = new View(requireContext());
                negFill.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (corr < 0) ? (float) absCorr : 0));
                negFill.setBackgroundColor(getResources().getColor(good == PositiveCorr ? R.color.material_green_500 : (good == NegativeCorr ?  R.color.material_red_500 : R.color.material_blue_500), cv.getContext().getTheme()));

                View centerLine = new View(requireContext());
                LinearLayout.LayoutParams centerParams = new LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT);
                centerLine.setLayoutParams(centerParams);
                centerLine.setBackgroundColor(getResources().getColor(R.color.black, cv.getContext().getTheme()));

                View posFill = new View(requireContext());
                posFill.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (corr > 0) ? (float) absCorr : 0));
                posFill.setBackgroundColor(getResources().getColor(good == PositiveCorr ? R.color.material_green_500 : (good == NegativeCorr ?  R.color.material_red_500 : R.color.material_blue_500), cv.getContext().getTheme()));

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
                                    (good == PositiveCorr ? R.color.material_green_500 : (good == NegativeCorr ? R.color.material_red_500 : R.color.material_blue_500))
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
