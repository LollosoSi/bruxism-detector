package com.example.bruxismdetector;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.util.List;

public class MetricPickerAdapter extends RecyclerView.Adapter<MetricPickerAdapter.ViewHolder> {

    private final List<String> metrics;
    private int selectedPosition = 0;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClicked(int position);
    }

    public MetricPickerAdapter(List<String> metrics, OnItemClickListener listener) {
        this.metrics = metrics;
        this.listener = listener;
    }

    public void setSelectedPosition(int position) {
        int oldPos = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(oldPos);
        notifyItemChanged(selectedPosition);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_picker_metric, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.textView.setText(metrics.get(position));

        if (position == selectedPosition) {
            // Highlight centered item
            holder.textView.setTextColor(MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary));
            holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            holder.textView.setAlpha(1.0f);
        } else {
            // Dim side items
            holder.textView.setTextColor(MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant));
            holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            holder.textView.setAlpha(0.5f);
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClicked(position));
    }

    @Override
    public int getItemCount() {
        return metrics.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.text_metric_name);
        }
    }
}