// TutorialOverlayManager.java
package com.example.bruxismdetector;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

public class TutorialOverlayManager {

    public interface TutorialCompleted {
        void onTutorialCompleted();
    }

    /**
     * @param delayMs Tempo di attesa extra per animazioni/transizioni
     */
    public record TutorialStep(View targetView, String message, Runnable preAction, long delayMs) {
            public TutorialStep(View targetView, String message) {
                this(targetView, message, null, 0);
            }

            public TutorialStep(View targetView, String message, Runnable preAction) {
                this(targetView, message, preAction, 350); // Default di sicurezza se c'è un'azione UI
            }

    }

    private TutorialCompleted completed_callback = null;
    private final Activity activity;
    private final FrameLayout overlay;
    private final SpotlightView spotlightView;
    private final TextView messageView;
    private final List<TutorialStep> steps;
    private int currentStep = 0;

    public TutorialOverlayManager(Activity activity, List<TutorialStep> steps) {
        this.activity = activity;
        this.steps = steps;


        overlay = new FrameLayout(activity);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        overlay.setAlpha(0f); // Inizia trasparente per il fade in iniziale

        RectF firstRect = new RectF(0, 0, 100, 100);
        spotlightView = new SpotlightView(activity, firstRect);
        overlay.addView(spotlightView);

        messageView = new TextView(activity);
        messageView.setTextColor(Color.WHITE);
        messageView.setTextSize(16);
        messageView.setPadding(40, 20, 40, 20);
        messageView.setGravity(Gravity.CENTER_HORIZONTAL);
        messageView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        messageView.setAlpha(0f);

        // Occupa l'intera larghezza con 40px di margine per lato
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        lp.leftMargin = 40;
        lp.rightMargin = 40;
        messageView.setLayoutParams(lp);

        overlay.addView(messageView);
        overlay.setOnClickListener(v -> nextStep());


    }

    public void start(TutorialCompleted callback) {
        completed_callback = callback;
        FrameLayout root = (FrameLayout) activity.getWindow().getDecorView();
        root.addView(overlay);

        // Animazione di dissolvenza in ingresso di tutto il tutorial
        overlay.animate()
                .alpha(1f)
                .setDuration(350)
                .withStartAction(this::nextStep)
                .start();
    }

    private void nextStep() {
        if (currentStep >= steps.size()) {
            // Disattiva ulteriori click per non far ripartire azioni
            overlay.setOnClickListener(null);

            // Fade out dell'intero overlay (spotlight + messaggio insieme)
            overlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        if (overlay.getParent() != null) {
                            ((ViewGroup) overlay.getParent()).removeView(overlay);
                        }
                        if (completed_callback != null) {
                            completed_callback.onTutorialCompleted();
                        }
                    })
                    .start();
            return;
        }

        TutorialStep step = steps.get(currentStep);
        currentStep++;

        if (step.preAction != null) {
            step.preAction.run();
        }

        View view = step.targetView;
        String message = step.message;

        Runnable layoutAndAnimate = () -> {
            // 1. Misura il testo fuori schermo
            TextView measureHelper = new TextView(activity);
            measureHelper.setText(message);
            measureHelper.setTextSize(16);
            measureHelper.setPadding(40, 20, 40, 20);

            int widthSpec = View.MeasureSpec.makeMeasureSpec(overlay.getWidth() - 80, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            measureHelper.measure(widthSpec, heightSpec);

            int tempHeight = measureHelper.getMeasuredHeight();

            // Determina se il testo va su più righe (da esplicito \n o da a capo automatico)
            boolean isMultiline = measureHelper.getLineCount() > 1 || message.contains("\n");

            // Caso 1: View NULL -> Tutto oscurato e testo centrato verticalmente
            if (view == null) {
                spotlightView.animateTo(new RectF(0, 0, 0, 0));
                float targetY = (overlay.getHeight() - tempHeight) / 2f;
                animateMessage(message, targetY, isMultiline);
                return;
            }

            // Caso 2: View valida -> Posizionato sopra o sotto lo spotlight
            view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    Rect rect = new Rect();
                    boolean isVisible = view.getGlobalVisibleRect(rect);

                    if (!isVisible || rect.isEmpty()) {
                        view.post(() -> spotlightView.animateTo(new RectF(rect)));
                        return;
                    }

                    RectF targetRect = new RectF(rect);
                    spotlightView.animateTo(targetRect);

                    int spacing = 100;
                    float targetY;
                    if (targetRect.top - spacing - tempHeight >= 0) {
                        targetY = targetRect.top - tempHeight - spacing;
                    } else {
                        targetY = Math.min(overlay.getHeight() - tempHeight - spacing, targetRect.bottom + spacing);
                    }

                    animateMessage(message, targetY, isMultiline);
                }
            });

            view.requestLayout();
        };

        if (step.delayMs > 0) {
            overlay.postDelayed(layoutAndAnimate, step.delayMs);
        } else {
            overlay.post(layoutAndAnimate);
        }
    }

    private void animateMessage(String newMessage, float targetY, boolean isMultiline) {
        // Al primo step (quando messageView è ancora trasparente)
        if (messageView.getAlpha() == 0f) {
            applyTextAlignment(messageView, isMultiline);
            messageView.setText(newMessage);
            messageView.setY(targetY);
            messageView.animate().alpha(1f).setDuration(300).start();
            return;
        }

        // Negli step successivi: dissolvenza completa prima del cambio contenuto e allineamento
        messageView.animate()
                .alpha(0f)
                .setDuration(100)
                .withEndAction(() -> {
                    applyTextAlignment(messageView, isMultiline);
                    messageView.setText(newMessage);

                    float startY = messageView.getY();
                    ValueAnimator moveAnimator = ValueAnimator.ofFloat(startY, targetY);
                    moveAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                    moveAnimator.setDuration(1000);
                    moveAnimator.addUpdateListener(animation -> {
                        messageView.setY((float) animation.getAnimatedValue());
                    });

                    moveAnimator.start();
                    messageView.animate().alpha(1f).setDuration(1000).start();
                })
                .start();
    }

    private void applyTextAlignment(TextView tv, boolean isMultiline) {
        if (isMultiline) {
            tv.setGravity(Gravity.START);
            tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        } else {
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        }
    }
}