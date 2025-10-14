package com.example.bruxismdetector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.animation.Animator;
import androidx.core.animation.ValueAnimator;

public class SpotlightView extends View {

    private final Paint backgroundPaint;
    private final Paint clearPaint;
    private final RectF currentRect;
    private final RectF targetRect;
    private final ValueAnimator animator;

    public SpotlightView(Context context, RectF initialRect) {
        super(context);

        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.parseColor("#B3000000")); // semi-transparent black

        clearPaint = new Paint();
        clearPaint.setAntiAlias(true);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        currentRect = new RectF(initialRect);
        targetRect = new RectF(initialRect);

        setLayerType(LAYER_TYPE_HARDWARE, null);

        animator = ValueAnimator.ofFloat(0f, 1f); // this makes getAnimatedValue() work
        animator.setDuration(800);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull Animator animation) {
                float progress = (Float) animator.getAnimatedValue();
                interpolate(currentRect, targetRect, progress);
                invalidate();
            }
        });


    }

    private void interpolate(RectF out, RectF to, float t) {
        out.left   += (to.left   - out.left)   * t;
        out.top    += (to.top    - out.top)    * t;
        out.right  += (to.right  - out.right)  * t;
        out.bottom += (to.bottom - out.bottom) * t;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        canvas.drawRoundRect(currentRect, 32f, 32f, clearPaint);
    }

    public void animateTo(RectF newRect) {
        targetRect.set(newRect);
        animator.start();
    }
}
