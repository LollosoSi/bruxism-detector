package com.example.bruxismdetector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;

public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder> {
    private final File[] imageFiles;

    public ImagePagerAdapter(File[] files) {
        this.imageFiles = files;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        Bitmap bitmap = BitmapFactory.decodeFile(imageFiles[position].getAbsolutePath());
        holder.bind(bitmap);
    }

    @Override
    public int getItemCount() {
        return imageFiles.length;
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        private final ZoomableImageView imageView;
        private final Matrix matrix = new Matrix();
        private float scale = 1f;
        private float lastX, lastY;
        private boolean isDragging = false;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view);

            ScaleGestureDetector scaleDetector = new ScaleGestureDetector(itemView.getContext(),
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override
                        public boolean onScale(ScaleGestureDetector detector) {
                            //scale *= detector.getScaleFactor();
                            //scale = Math.max(0.5f, Math.min(scale, 5.0f));
                            //matrix.setScale(scale, scale, detector.getFocusX(), detector.getFocusY());
                            //imageView.setImageMatrix(matrix);
                            return true;
                        }
                    });

            imageView.setOnTouchListener((v, event) -> {
                scaleDetector.onTouchEvent(event);
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getX();
                        lastY = event.getY();
                        isDragging = true;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (isDragging) {
                            float dx = event.getX() - lastX;
                            float dy = event.getY() - lastY;
                            //matrix.postTranslate(dx, dy);
                            //imageView.setImageMatrix(matrix);
                            lastX = event.getX();
                            lastY = event.getY();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        isDragging = false;
                        break;
                }
                return true;
            });
        }

        public void bind(Bitmap bitmap) {
            imageView.setImageAndCenter(bitmap);
        }

    }
}
