package com.example.bruxismdetector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public class ZoomableImageView extends androidx.appcompat.widget.AppCompatImageView {

    private Matrix matrix = new Matrix();
    private float[] lastTouch = new float[2];
    private float scale = 1f;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float minScale = 1f, maxScale = 4f;
    private boolean isDragging = false;

    public ZoomableImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scale *= detector.getScaleFactor();
                scale = Math.max(minScale, Math.min(scale, maxScale));
                matrix.postScale(detector.getScaleFactor(), detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                setImageMatrix(matrix);
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        setOnTouchListener((v, event) -> {
            float x = event.getX();
            float y = event.getY();
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouch[0] = x;
                    lastTouch[1] = y;

                    // If touch is in center 80% of width, request parent to NOT intercept
                    int width = getWidth();
                    if (x > width * 0.1 && x < width * 0.9) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = x - lastTouch[0];
                    float dy = y - lastTouch[1];
                    matrix.postTranslate(dx, dy);
                    setImageMatrix(matrix);
                    lastTouch[0] = x;
                    lastTouch[1] = y;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return true;
        });
    }

    public void setImageAndCenter(Bitmap bitmap) {
        setImageBitmap(bitmap);
        post(() -> {
            float viewWidth = getWidth();
            float viewHeight = getHeight();
            float imageWidth = bitmap.getWidth();
            float imageHeight = bitmap.getHeight();

            float scaleX = viewWidth / imageWidth;
            float scaleY = viewHeight / imageHeight;
            scale = Math.min(scaleX, scaleY);

            float dx = (viewWidth - imageWidth * scale) / 2;
            float dy = (viewHeight - imageHeight * scale) / 2;

            matrix.reset();
            matrix.postScale(scale, scale);
            matrix.postTranslate(dx, dy);
            setImageMatrix(matrix);
        });
    }
}
