package com.example.bruxismdetector;

import android.animation.LayoutTransition;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import com.example.bruxismdetector.bruxism_grapher2.CorrelationsCalculator;
import com.example.bruxismdetector.bruxism_grapher2.SummaryReader;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class CorrelationsFragment extends Fragment {

    // Threshold for correlation significance
    private final double THRESHOLD = 0.25;

    private View root;
    private ArrayList<CardWithRows> cards = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_correlations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;

        // Setup the "See All" toggle listener
        CheckBox seeAllCheckbox = root.findViewById(R.id.seeallcheckbox);
        seeAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> toggleAllCards(isChecked));

        // Load data in the background to prevent UI freezing
        loadDataInBackground();
    }

    /**
     * Handles the "See All" checkbox toggle with a smooth layout transition.
     */
    private void toggleAllCards(boolean showAll) {
        LinearLayout holder = root.findViewById(R.id.correlations_holder);
        holder.setLayoutTransition(new LayoutTransition());

        for (CardWithRows cardWrapper : cards) {
            if (showAll) {
                cardWrapper.showAll();
            } else {
                cardWrapper.hideIrrelevantRowsAndCard();
            }
        }
        holder.setLayoutTransition(null);
    }

    /**
     * Executes heavy file I/O and mathematical calculations on a background thread.
     */
    private void loadDataInBackground() {
        new Thread(() -> {
            try {
                // 1. Establish file path
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                String path = documentsDir.getPath() + "/RECORDINGS/Summary/Summary.csv";
                SummaryReader.setFilepath(path);

                // 2. Ask the Single Source of Truth (SSOT) for the calculations
                CorrelationsCalculator calc = new CorrelationsCalculator(path);
                CorrelationsCalculator.CorrelationResult[][] results = calc.makeCorrelationMatrix();

                // 3. Extract required metadata
                ArrayList<String> filterNames = calc.getFilterNames();
                ArrayList<String> statNames = calc.getStatNames();
                int[] filterHitCounts = calc.getFilterhitcount();
                int totalSessions = SummaryReader.getInstance().getSummaryTuplesWithNoSkipItems().size();

                // 4. Switch back to Main UI Thread to build the visual elements
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            buildUiForCards(results, filterNames, statNames, filterHitCounts, totalSessions)
                    );
                }

            } catch (Exception e) {
                Log.e("CorrelationsFragment", "Error loading correlation data", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::showErrorState);
                }
            }
        }).start();
    }

    /**
     * Displays an error message on the UI if data loading fails.
     */
    private void showErrorState() {
        TextView errorText = root.findViewById(R.id.errortext);
        errorText.setText("There was an error while interpreting the data.\nAre you up to date?");
        errorText.setTextColor(getResources().getColor(R.color.material_red_500, requireContext().getTheme()));
        errorText.setVisibility(View.VISIBLE);
    }

    /**
     * Builds the entire ScrollView content, iterating through tags and generating cards.
     * MUST be called on the UI thread.
     */
    private void buildUiForCards(CorrelationsCalculator.CorrelationResult[][] results,
                                 ArrayList<String> filterNames,
                                 ArrayList<String> statNames,
                                 int[] filterHitCounts,
                                 int totalSessions) {

        Context ctx = requireContext();
        LinearLayout holder = root.findViewById(R.id.correlations_holder);
        holder.removeAllViews();
        cards.clear();

        // Add overall session count header
        TextView sessionHeader = new TextView(ctx);
        sessionHeader.setText(String.format(Locale.US, "Total sessions analyzed: %d", totalSessions));
        sessionHeader.setTextSize(13);
        sessionHeader.setPadding(0, 0, 0, 16);
        sessionHeader.setGravity(Gravity.CENTER);
        holder.addView(sessionHeader);

        // Iterate through each tag (e.g., Caffeine, Night Guard, etc.)
        for (int filterIdx = 0; filterIdx < filterNames.size(); filterIdx++) {

            // Create Card Container
            MaterialCardView cardView = new MaterialCardView(ctx);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(16, 12, 16, 12);
            cardView.setLayoutParams(cardParams);
            cardView.setRadius(24);
            cardView.setCardElevation(6);
            cardView.setContentPadding(24, 32, 24, 24);

            // Card Inner Layout
            LinearLayout cardInnerLayout = new LinearLayout(ctx);
            cardInnerLayout.setOrientation(LinearLayout.VERTICAL);
            cardInnerLayout.setLayoutTransition(new LayoutTransition());

            // Card Title (The Tag Name)
            TextView title = new TextView(ctx);
            title.setText(filterNames.get(filterIdx));
            title.setTextSize(16);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 24);
            cardInnerLayout.addView(title);

            CardWithRows cardWrapper = new CardWithRows(cardView);
            int posNegScore = 0;

            // Iterate through every metric for this specific tag
            for (int statIdx = 0; statIdx < statNames.size(); statIdx++) {
                CorrelationsCalculator.CorrelationResult cell = results[filterIdx][statIdx];

                // Determine if this row is mathematically significant
                boolean isRelevant = Math.abs(cell.r) >= THRESHOLD && cell.p_value <= 0.05;

                // Tally the overall effect of this tag (for the footer evaluation)
                if (isRelevant) {
                    if (cell.effect == CorrelationsCalculator.PositiveCorr) posNegScore++;
                    else if (cell.effect == CorrelationsCalculator.NegativeCorr) posNegScore--;
                }

                // Build the UI row for this metric
                LinearLayout row = createStatRow(ctx, statNames.get(statIdx), cell, isRelevant);
                cardInnerLayout.addView(row);

                ThresholdRow tr = new ThresholdRow(row, isRelevant);
                tr.hideIfThreshold(); // Hide by default if irrelevant
                cardWrapper.addToArray(tr);
            }

            // Create footer (Evaluation and Hit Count)
            createCardFooter(ctx, cardInnerLayout, posNegScore, filterHitCounts[filterIdx]);

            // Add the inner layout to the card, and the card to the scroll view
            cardView.addView(cardInnerLayout);
            holder.addView(cardView);

            cards.add(cardWrapper);
            cardWrapper.hideIrrelevantRowsAndCard(); // Hide empty cards by default
        }
    }

    /**
     * Creates a single row containing the Metric Name, the Bar Chart, and the Value text.
     */
    private LinearLayout createStatRow(Context ctx, String rawStatName, CorrelationsCalculator.CorrelationResult cell, boolean isRelevant) {
        int rowHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());

        // Row container
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 8);

        // Clean up stat name (remove parenthesis)
        String cleanStatName = rawStatName.contains("(") ? rawStatName.substring(0, rawStatName.indexOf("(") - 1).trim() : rawStatName;

        // 1. Label TextView
        TextView label = new TextView(ctx);
        label.setText(cleanStatName);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        label.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        label.setPadding(0, 0, 16, 0);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(label, 9, 13, 1, TypedValue.COMPLEX_UNIT_SP);

        // 2. Bar Chart Container
        LinearLayout barContainer = new LinearLayout(ctx);
        barContainer.setOrientation(LinearLayout.HORIZONTAL);
        barContainer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.8f));
        barContainer.setPadding(4, 12, 4, 12);
        barContainer.setBackgroundColor(getResources().getColor(R.color.seekbar_track_background, ctx.getTheme()));
        barContainer.setGravity(Gravity.CENTER_VERTICAL);

        // Calculate Bar Proportions
        double absCorr = Math.min(1f, Math.abs(cell.r));
        int colorRes = cell.effect == CorrelationsCalculator.PositiveCorr ? R.color.material_green_500 :
                (cell.effect == CorrelationsCalculator.NegativeCorr ? R.color.material_red_500 : R.color.material_blue_500);
        int barColor = getResources().getColor(colorRes, ctx.getTheme());

        // Build Bar elements
        View leftSpacer = new View(ctx);
        leftSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (cell.r < 0) ? (float) (1f - absCorr) : 1f));

        View negFill = new View(ctx);
        negFill.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (cell.r < 0) ? (float) absCorr : 0));
        negFill.setBackgroundColor(barColor);

        View centerLine = new View(ctx);
        centerLine.setLayoutParams(new LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT));
        centerLine.setBackgroundColor(getResources().getColor(R.color.black, ctx.getTheme()));

        View posFill = new View(ctx);
        posFill.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (cell.r > 0) ? (float) absCorr : 0));
        posFill.setBackgroundColor(barColor);

        View rightSpacer = new View(ctx);
        rightSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (cell.r > 0) ? (float) (1f - absCorr) : 1f));

        barContainer.addView(leftSpacer);
        barContainer.addView(negFill);
        barContainer.addView(centerLine);
        barContainer.addView(posFill);
        barContainer.addView(rightSpacer);

        // 3. Value TextView
        TextView valueLabel = new TextView(ctx);
        String prefix = cell.effect_size > 0 ? "+" : "";
        valueLabel.setText(String.format(Locale.US, "r = %.2f\np = %.3f\nE = %s%.0f%%", cell.r, cell.p_value, prefix, cell.effect_size));
        valueLabel.setTextSize(11);
        valueLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.7f));
        valueLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        valueLabel.setPadding(16, 0, 0, 0);

        // Gray out text if insignificant, otherwise color code it
        if (isRelevant) {
            valueLabel.setTextColor(barColor);
        } else {
            valueLabel.setTextColor(getResources().getColor(R.color.material_blue_grey_400, ctx.getTheme()));
        }

        row.addView(label);
        row.addView(barContainer);
        row.addView(valueLabel);

        return row;
    }

    /**
     * Adds the concluding evaluation and hit counter to the bottom of a card.
     */
    private void createCardFooter(Context ctx, LinearLayout parentLayout, int posNegScore, int hitCount) {
        String[] evals = {"Negative", "Mostly negative", "Neutral", "Mostly positive", "Positive"};

        int absScore = Math.abs(posNegScore);
        boolean isPositive = posNegScore > 0;
        int selectedIndex = 2; // Default Neutral
        int colorRes = R.color.material_blue_500;

        if (absScore >= 2 && absScore < 3) {
            selectedIndex = isPositive ? 3 : 1;
            colorRes = isPositive ? R.color.material_green_500 : R.color.material_red_500;
        } else if (absScore >= 3) {
            selectedIndex = isPositive ? 4 : 0;
            colorRes = isPositive ? R.color.material_green_500 : R.color.material_red_500;
        }

        // Evaluation Text
        TextView evalText = new TextView(ctx);
        evalText.setTextSize(13);
        evalText.setText(evals[selectedIndex]);
        evalText.setTextColor(getResources().getColor(colorRes, ctx.getTheme()));
        evalText.setGravity(Gravity.CENTER);
        evalText.setPadding(0, 16, 0, 4);
        parentLayout.addView(evalText);

        // Hit Count Text (Warning if low data)
        TextView hitText = new TextView(ctx);
        hitText.setTextSize(11);
        hitText.setGravity(Gravity.CENTER);

        if (hitCount < 5) {
            hitText.setText(String.format(Locale.US, "Hits: %d\n(Warning: Not enough data)", hitCount));
            hitText.setTextColor(getResources().getColor(R.color.material_orange_500, ctx.getTheme()));
        } else {
            hitText.setText(String.format(Locale.US, "Hits: %d", hitCount));
            hitText.setTextColor(getResources().getColor(R.color.material_blue_grey_400, ctx.getTheme()));
        }
        parentLayout.addView(hitText);
    }

    // --- Helper Classes for Visibility Toggling ---

    public static class CardWithRows {
        MaterialCardView card;
        ArrayList<ThresholdRow> rows = new ArrayList<>();

        public CardWithRows(MaterialCardView cv) {
            card = cv;
        }

        void addToArray(ThresholdRow tr) {
            rows.add(tr);
        }

        void hideIrrelevantRowsAndCard() {
            boolean isCardEmpty = true;
            for (ThresholdRow tr : rows) {
                if (tr.hideIfThreshold()) {
                    isCardEmpty = false;
                }
            }
            card.setVisibility(isCardEmpty ? View.GONE : View.VISIBLE);
        }

        void showAll() {
            for (ThresholdRow tr : rows) {
                tr.show();
            }
            card.setVisibility(View.VISIBLE);
        }
    }

    public static class ThresholdRow {
        LinearLayout row;
        boolean didMeetThreshold;

        public ThresholdRow(LinearLayout ll, boolean didMeetThreshold) {
            this.row = ll;
            this.didMeetThreshold = didMeetThreshold;
            row.setAlpha(1f);
        }

        public boolean hideIfThreshold() {
            row.setVisibility(didMeetThreshold ? View.VISIBLE : View.GONE);
            return didMeetThreshold;
        }

        public void show() {
            row.setVisibility(View.VISIBLE);
        }
    }
}