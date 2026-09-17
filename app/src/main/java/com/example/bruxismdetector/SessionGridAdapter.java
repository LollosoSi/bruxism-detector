package com.example.bruxismdetector;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bruxismdetector.bruxism_grapher2.CorrelationsCalculator;
import com.example.bruxismdetector.bruxism_grapher2.SummaryReader;
import com.google.android.material.color.MaterialColors;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SessionGridAdapter extends RecyclerView.Adapter<SessionGridAdapter.ViewHolder> {

    private final List<SummaryReader.SummaryEntry> validSessions;
    private int selectedTupleIndex;
    private float globalMin, globalMax;
    private byte effectDirection;
    private boolean hasMetric;
    private final OnSessionClickListener clickListener;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public interface OnSessionClickListener {
        void onSessionClick(SummaryReader.SummaryEntry entry);
    }

    public SessionGridAdapter(List<SummaryReader.SummaryEntry> allSessions, int selectedTupleIndex, OnSessionClickListener listener) {
        this.clickListener = listener;
        this.validSessions = new ArrayList<>();

        for (SummaryReader.SummaryEntry entry : allSessions) {
            if (entry != null && !entry.should_skip) {
                validSessions.add(entry);
            }
        }
        this.validSessions.sort((a, b) -> a.tuple[0].compareTo(b.tuple[0]));

        updateMath(selectedTupleIndex);
    }

    public List<SummaryReader.SummaryEntry> getValidSessions() {
        return validSessions;
    }

    private void updateMath(int newTupleIndex) {
        this.selectedTupleIndex = newTupleIndex;
        SummaryReader sr = SummaryReader.getInstance();
        this.hasMetric = selectedTupleIndex > 0 && selectedTupleIndex < sr.getSummaryTitles().length;

        if (hasMetric) {
            float[] minMax = CalendarHighlighter.getGlobalMinMax(selectedTupleIndex, sr.getSummaryTuplesWithNoSkipItems());
            globalMin = minMax[0];
            globalMax = minMax[1];
            effectDirection = CorrelationsCalculator.isGoingToBetter(1.0, sr.getSummaryTitles()[selectedTupleIndex]);
        }
    }

    /**
     * This is called by the Activity when the picker changes.
     * Passing a Payload ("ANIMATE_CHANGE") tells the adapter to run the color fade.
     */
    public void updateMetricAnimated(int newTupleIndex) {
        updateMath(newTupleIndex);
        notifyItemRangeChanged(0, validSessions.size(), "ANIMATE_CHANGE");
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dense_grid_cell, parent, false);
        ViewHolder vh = new ViewHolder(v);
        // Initialize the base color state
        vh.currentBgColor = MaterialColors.getColor(v, com.google.android.material.R.attr.colorSurface);
        return vh;
    }

    // NEW: Intercept payloads to decide whether to animate or snap instantly
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        boolean shouldAnimate = payloads.contains("ANIMATE_CHANGE");
        bindData(holder, position, shouldAnimate);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        bindData(holder, position, false); // Snap instantly by default
    }

    private void bindData(ViewHolder holder, int position, boolean shouldAnimate) {
        SummaryReader.SummaryEntry entry = validSessions.get(position);

        // 1. Date
        LocalDate date = LocalDate.parse(entry.tuple[0], dateFormatter);
        holder.txtDate.setText(String.format(Locale.getDefault(), "%d/%d", date.getDayOfMonth(), date.getMonthValue()));

        // 2. Tags
        holder.layoutTags.removeAllViews();
        int infoIndex = SummaryReader.getInstance().getInfomationIndex();
        boolean hasTags = false;
        if (entry.tuple[infoIndex] != null) {
            int tagCount = 0;
            for (String tag : entry.tuple[infoIndex].split(",")) {
                if (tag.trim().isEmpty() || tagCount >= 4) continue;
                hasTags = true;
                ImageView icon = new ImageView(holder.itemView.getContext());
                int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, holder.itemView.getResources().getDisplayMetrics());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMargins(1, 0, 1, 0);
                icon.setLayoutParams(lp);
                icon.setImageDrawable(CalendarPalette.getTagIcon(holder.itemView.getContext(), tag));
                holder.layoutTags.addView(icon);
                tagCount++;
            }
        }

        // 3. Metric & Heatmap Coloring
        int colorSurface = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSurface);
        int colorOutline = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOutlineVariant);
        int colorPrimary = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary);

        int targetColor;

        if (!hasMetric) {
            targetColor = hasTags ? MaterialColors.layer(colorSurface, colorPrimary, 0.12f) : colorSurface;

            holder.txtMetric.setVisibility(View.GONE);
            holder.txtDate.setTextColor(MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface));
        } else {
            try {
                float val = Float.parseFloat(entry.tuple[selectedTupleIndex].replace(",", "."));
                targetColor = CalendarHighlighter.getGradientColor(val, globalMin, globalMax, effectDirection);

                boolean isDark = isColorDark(targetColor);
                int textColor = isDark ? Color.WHITE : Color.BLACK;

                holder.txtDate.setTextColor(textColor);
                holder.txtMetric.setTextColor(textColor);
                holder.txtMetric.setText(val >= 100 ? String.format(Locale.US, "%.0f", val) : String.format(Locale.US, "%.1f", val));
                holder.txtMetric.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                targetColor = colorSurface;
                holder.txtMetric.setText("-");
                holder.txtMetric.setVisibility(View.VISIBLE);
            }
        }

        // Execute Color Change
        if (shouldAnimate && holder.currentBgColor != targetColor) {
            animateCellBackground(holder, targetColor, hasMetric ? colorOutline : (hasTags ? colorOutline : Color.TRANSPARENT));
        } else {
            setCellBackground(holder.rootLayout, targetColor, hasMetric ? colorOutline : (hasTags ? colorOutline : Color.TRANSPARENT));
            holder.currentBgColor = targetColor;
        }

        // 4. Click Listener
        holder.itemView.setOnClickListener(v -> clickListener.onSessionClick(entry));
    }

    private void animateCellBackground(ViewHolder holder, int targetColor, int strokeColor) {
        ValueAnimator anim = ValueAnimator.ofArgb(holder.currentBgColor, targetColor);
        anim.setDuration(400); // 400ms smooth transition
        anim.addUpdateListener(a -> {
            int animColor = (int) a.getAnimatedValue();
            setCellBackground(holder.rootLayout, animColor, strokeColor);
        });
        anim.start();
        holder.currentBgColor = targetColor; // Save new state
    }

    private void setCellBackground(View view, int fillColor, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, view.getResources().getDisplayMetrics()));
        gd.setColor(fillColor);
        if (strokeColor != Color.TRANSPARENT) gd.setStroke(3, strokeColor);
        view.setBackground(gd);
    }

    private boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    @Override
    public int getItemCount() {
        return validSessions.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout rootLayout, layoutTags;
        TextView txtDate, txtMetric;

        int currentBgColor; // Tracks color for animation

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            rootLayout = itemView.findViewById(R.id.session_cell_root);
            txtDate = itemView.findViewById(R.id.text_cell_date);
            layoutTags = itemView.findViewById(R.id.layout_cell_tags);
            txtMetric = itemView.findViewById(R.id.text_cell_metric);
        }
    }
}