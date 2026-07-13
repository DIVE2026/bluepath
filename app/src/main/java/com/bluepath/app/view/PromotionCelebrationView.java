package com.bluepath.app.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Random;

public class PromotionCelebrationView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float[] px = new float[80];
    private final float[] py = new float[80];
    private final float[] speed = new float[80];
    private String tier = "실버";
    private float progress;
    private Runnable onFinished;

    public PromotionCelebrationView(Context context) {
        super(context);
        setBackgroundColor(Color.argb(235, 2, 22, 45));
        for (int i = 0; i < px.length; i++) {
            px[i] = random.nextFloat();
            py[i] = random.nextFloat();
            speed[i] = 0.35f + random.nextFloat() * 0.9f;
        }
    }

    public void play(String tier, Runnable onFinished) {
        this.tier = tier == null ? "실버" : tier;
        this.onFinished = onFinished;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(3600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (PromotionCelebrationView.this.onFinished != null) PromotionCelebrationView.this.onFinished.run();
            }
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        for (int i = 0; i < px.length; i++) {
            float y = ((py[i] + progress * speed[i]) % 1f) * h;
            paint.setColor(i % 3 == 0 ? Color.WHITE : tierColor());
            paint.setAlpha((int) (180 * (1f - Math.max(0f, progress - 0.75f) * 4f)));
            canvas.drawCircle(px[i] * w, y, 3f + (i % 5), paint);
        }
        float pulse = 0.82f + 0.18f * (float) Math.sin(progress * Math.PI * 7);
        float shieldW = Math.min(w * 0.5f, h * 0.28f) * pulse;
        float shieldH = shieldW * 1.15f;
        float cx = w / 2f;
        float cy = h * 0.43f;
        Path path = new Path();
        path.moveTo(cx, cy - shieldH / 2f);
        path.lineTo(cx + shieldW / 2f, cy - shieldH * 0.25f);
        path.lineTo(cx + shieldW * 0.4f, cy + shieldH * 0.3f);
        path.quadTo(cx, cy + shieldH / 2f, cx - shieldW * 0.4f, cy + shieldH * 0.3f);
        path.lineTo(cx - shieldW / 2f, cy - shieldH * 0.25f);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tierColor());
        paint.setShadowLayer(28f, 0, 0, Color.WHITE);
        canvas.drawPath(path, paint);
        paint.clearShadowLayer();
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(shieldW * 0.2f);
        canvas.drawText(tier, cx, cy + 10f, paint);
        paint.setTextSize(Math.min(w, h) * 0.065f);
        canvas.drawText("TIER UP!", cx, h * 0.72f, paint);
        paint.setTextSize(Math.min(w, h) * 0.032f);
        canvas.drawText("새로운 해양 항로가 열렸습니다", cx, h * 0.79f, paint);
    }

    private int tierColor() {
        switch (tier) {
            case "실버": return Color.parseColor("#A8B3C4");
            case "골드": return Color.parseColor("#FACC15");
            case "플래티넘": return Color.parseColor("#2DD4BF");
            case "다이아": return Color.parseColor("#60A5FA");
            default: return Color.parseColor("#B7794A");
        }
    }
}
