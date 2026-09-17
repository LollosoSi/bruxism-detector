package com.example.bruxismdetector;

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
    private final OnSessionClickListener clickListener;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private int selectedTupleIndex;
    private float globalMin, globalMax;
    private byte effectDirection;
    private boolean hasMetric;

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

        // Call our new update method to initialize the math
        updateMetric(selectedTupleIndex);
    }

    // NEW: Expose the filtered list for the scroll logic
    public List<SummaryReader.SummaryEntry> getValidSessions() {
        return validSessions;
    }

    // NEW: Update metric math without destroying the RecyclerView state
    public void updateMetric(int newTupleIndex) {
        this.selectedTupleIndex = newTupleIndex;
        SummaryReader sr = SummaryReader.getInstance();
        this.hasMetric = selectedTupleIndex > 0 && selectedTupleIndex < sr.getSummaryTitles().length;

        if (hasMetric) {
            float[] minMax = CalendarHighlighter.getGlobalMinMax(selectedTupleIndex, sr.getSummaryTuplesWithNoSkipItems());
            globalMin = minMax[0];
            globalMax = minMax[1];
            effectDirection = CorrelationsCalculator.isGoingToBetter(1.0, sr.getSummaryTitles()[selectedTupleIndex]);
        }

        // Tells the RecyclerView to redraw the tiles without resetting the scroll position!
        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dense_grid_cell, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SummaryReader.SummaryEntry entry = validSessions.get(position);

        // 1. Format Date compactly (e.g. 14/7)
        LocalDate date = LocalDate.parse(entry.tuple[0], dateFormatter);
        holder.txtDate.setText(String.format(Locale.getDefault(), "%d/%d", date.getDayOfMonth(), date.getMonthValue()));

        // 2. Render Tags (Max 4 icons to prevent overflow)
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

        // 3. Render Metric & Heatmap Color
        int colorSurface = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSurface);
        int colorOutline = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOutlineVariant);
        int colorPrimary = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary);

        if (!hasMetric) {
            int bgColor = hasTags ? MaterialColors.layer(colorSurface, colorPrimary, 0.12f) : colorSurface;
            setCellBackground(holder.rootLayout, bgColor, hasTags ? colorOutline : Color.TRANSPARENT);

            holder.txtMetric.setVisibility(View.GONE);
            holder.txtDate.setTextColor(MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface));
        } else {
            try {
                float val = Float.parseFloat(entry.tuple[selectedTupleIndex].replace(",", "."));
                int bgColor = CalendarHighlighter.getGradientColor(val, globalMin, globalMax, effectDirection);
                setCellBackground(holder.rootLayout, bgColor, colorOutline);

                boolean isDark = isColorDark(bgColor);
                int textColor = isDark ? Color.WHITE : Color.BLACK;

                holder.txtDate.setTextColor(textColor);
                holder.txtMetric.setTextColor(textColor);
                holder.txtMetric.setText(val >= 100 ? String.format(Locale.US, "%.0f", val) : String.format(Locale.US, "%.1f", val));
                holder.txtMetric.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                setCellBackground(holder.rootLayout, colorSurface, colorOutline);
                holder.txtMetric.setText("-");
                holder.txtMetric.setVisibility(View.VISIBLE);
            }
        }

        // 4. Click Listener for the Bottom Sheet
        holder.itemView.setOnClickListener(v -> clickListener.onSessionClick(entry));
    }

    private void setCellBackground(View view, int fillColor, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, view.getResources().getDisplayMetrics()));
        gd.setColor(fillColor);
        if (strokeColor != Color.TRANSPARENT) gd.setStroke(3, strokeColor); // 1dp stroke
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

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            rootLayout = itemView.findViewById(R.id.session_cell_root);
            txtDate = itemView.findViewById(R.id.text_cell_date);
            layoutTags = itemView.findViewById(R.id.layout_cell_tags);
            txtMetric = itemView.findViewById(R.id.text_cell_metric);
        }
    }
}