// TutorialOverlayManager.java
package com.example.bruxismdetector;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

public class TutorialOverlayManager {

    private final Activity activity;
    private final FrameLayout overlay;
    private final SpotlightView spotlightView;
    private final TextView messageView;
    private final List<Pair<View, String>> steps;
    private int currentStep = 0;

    public TutorialOverlayManager(Activity activity, List<Pair<View, String>> steps) {
        this.activity = activity;
        this.steps = steps;

        overlay = new FrameLayout(activity);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        RectF firstRect = new RectF(0, 0, 100, 100);
        spotlightView = new SpotlightView(activity, firstRect);
        overlay.addView(spotlightView);

        messageView = new TextView(activity);
        messageView.setTextColor(Color.WHITE);
        messageView.setTextSize(16);
        messageView.setPadding(40, 20, 40, 20);
        messageView.setAlpha(0f);

        overlay.addView(messageView);
        overlay.setOnClickListener(v -> nextStep());
    }

    public void start() {
        FrameLayout root = (FrameLayout) activity.getWindow().getDecorView();
        root.addView(overlay);
        nextStep();
    }

    private void nextStep() {
        if (currentStep >= steps.size()) {
            ((ViewGroup) overlay.getParent()).removeView(overlay);
            return;
        }

        View view = steps.get(currentStep).first;
        String message = steps.get(currentStep).second;


        view.post(() -> {
            Rect rect = new Rect();
            view.getGlobalVisibleRect(rect);
            RectF targetRect = new RectF(rect);

            // Start spotlight animation
            spotlightView.animateTo(targetRect);

            // Prepare a temp TextView to measure the future layout
            TextView temp = new TextView(activity);
            temp.setText(message);
            temp.setTextSize(16);
            temp.setPadding(40, 20, 40, 20);
            temp.setMaxWidth(overlay.getWidth() - 80); // leave margins
            temp.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int tempHeight = temp.getMeasuredHeight();

            // Decide new Y position: prefer above, fallback to below if not enough space
            int spacing = 40;
            float targetY;

            if (targetRect.top - spacing - tempHeight >= 0) {
                // Enough room above
                targetY = targetRect.top - tempHeight - spacing;
            } else {
                // Not enough space, place below
                targetY = Math.min(overlay.getHeight() - tempHeight - spacing, targetRect.bottom + spacing);
            }


            // Start coordinated animation: alpha and position
            float startY = messageView.getY();

            ValueAnimator moveAnimator = ValueAnimator.ofFloat(startY, targetY);
            moveAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            moveAnimator.setDuration(600);
            moveAnimator.addUpdateListener(animation -> {
                float value = (float) animation.getAnimatedValue();
                messageView.setY(value);
            });

            // Animate fade out, change content, then fade in
            messageView.animate().alpha(0.2f).setDuration(000).withEndAction(() -> {
                messageView.setText(message);
                messageView.setX(40); // margin from left
                messageView.setY(startY); // start at old Y

                // Start move and fade-in
                moveAnimator.start();
                messageView.animate().alpha(1f).setDuration(300).start();
            }).start();

            currentStep++;
        });
    }

}
