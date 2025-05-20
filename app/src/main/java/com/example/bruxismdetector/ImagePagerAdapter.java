package com.example.bruxismdetector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.chrisbanes.photoview.PhotoView;

import java.io.File;

public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.PhotoViewHolder> {

    public interface OnScaleChangedListener {
        void onScaleChanged(float scale);
    }

    private final File[] imageFiles;
    private final OnScaleChangedListener scaleChangedListener;

    public ImagePagerAdapter(File[] files, OnScaleChangedListener listener) {
        this.imageFiles = files;
        this.scaleChangedListener = listener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photoview, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        Bitmap bitmap = BitmapFactory.decodeFile(imageFiles[position].getAbsolutePath());
        holder.photoView.setImageBitmap(bitmap);

        // Set scale change listener on the PhotoView
        holder.photoView.setOnScaleChangeListener((scaleFactor, focusX, focusY) -> {
            if (scaleChangedListener != null) {
                float currentScale = holder.photoView.getScale();
                scaleChangedListener.onScaleChanged(currentScale);
            }
        });

        // Also notify the initial scale (usually 1f)
        if (scaleChangedListener != null) {
            scaleChangedListener.onScaleChanged(holder.photoView.getScale());
        }
    }

    @Override
    public int getItemCount() {
        return imageFiles.length;
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        PhotoView photoView;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            photoView = itemView.findViewById(R.id.photo_view);
        }
    }
}
