package com.bluepath.app.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * Full-screen animated ocean backdrop used by the welcome and authentication flows.
 * The scene is drawn entirely with Canvas so no large bitmap asset is required.
 */
public class OceanBackgroundView extends View {
    private static final int BUBBLE_COUNT = 22;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random(2407L);
    private final float[] bubbleX = new float[BUBBLE_COUNT];
    private final float[] bubbleY = new float[BUBBLE_COUNT];
    private final float[] bubbleRadius = new float[BUBBLE_COUNT];
    private final float[] bubbleSpeed = new float[BUBBLE_COUNT];

    private ValueAnimator animator;
    private float phase;

    public OceanBackgroundView(Context context) {
        super(context);
        init();
    }

    public OceanBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < BUBBLE_COUNT; i++) {
            bubbleX[i] = random.nextFloat();
            bubbleY[i] = random.nextFloat();
            bubbleRadius[i] = 1.5f + random.nextFloat() * 4f;
            bubbleSpeed[i] = 0.35f + random.nextFloat() * 0.75f;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void startAnimation() {
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(14000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            phase = (float) valueAnimator.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnimation() {
        if (animator == null) return;
        animator.cancel();
        animator = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) return;

        drawBaseGradient(canvas, width, height);
        drawSunGlow(canvas, width, height);
        drawLightRays(canvas, width, height);
        drawHorizonHaze(canvas, width, height);

        drawWave(canvas, width, height, height * 0.48f, height * 0.025f,
                Color.argb(130, 73, 196, 207), 0.65f, 0.2f, false);
        drawWave(canvas, width, height, height * 0.59f, height * 0.035f,
                Color.argb(220, 9, 139, 158), -0.9f, 1.3f, true);
        drawWave(canvas, width, height, height * 0.70f, height * 0.050f,
                Color.rgb(5, 61, 75), 1.25f, 2.1f, true);

        drawBubbles(canvas, width, height);
        drawFish(canvas, width * (0.22f + 0.025f * waveSin(phase * 2f)), height * 0.63f, dp(10), false);
        drawFish(canvas, width * (0.73f - 0.020f * waveSin(phase * 2f)), height * 0.78f, dp(7), true);
        drawReadabilityShade(canvas, width, height);
    }

    private void drawBaseGradient(Canvas canvas, float width, float height) {
        fillPaint.setShader(new LinearGradient(
                0f, 0f, width * 0.2f, height,
                new int[]{
                        Color.rgb(185, 229, 236),
                        Color.rgb(79, 173, 187),
                        Color.rgb(7, 75, 91),
                        Color.rgb(3, 35, 45)
                },
                new float[]{0f, 0.30f, 0.68f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, width, height, fillPaint);
        fillPaint.setShader(null);
    }

    private void drawSunGlow(Canvas canvas, float width, float height) {
        fillPaint.setShader(new RadialGradient(
                width * 0.77f, height * 0.11f, width * 0.55f,
                new int[]{Color.argb(180, 238, 255, 255), Color.argb(25, 204, 249, 250), Color.TRANSPARENT},
                new float[]{0f, 0.35f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, width, height * 0.65f, fillPaint);
        fillPaint.setShader(null);
    }

    private void drawLightRays(Canvas canvas, float width, float height) {
        fillPaint.setColor(Color.argb(18, 255, 255, 255));
        for (int i = 0; i < 4; i++) {
            float startX = width * (0.58f + i * 0.09f);
            path.reset();
            path.moveTo(startX, 0f);
            path.lineTo(startX + width * 0.07f, 0f);
            path.lineTo(startX - width * (0.18f + i * 0.015f), height * 0.72f);
            path.lineTo(startX - width * (0.28f + i * 0.015f), height * 0.72f);
            path.close();
            canvas.drawPath(path, fillPaint);
        }
    }

    private void drawHorizonHaze(Canvas canvas, float width, float height) {
        fillPaint.setShader(new LinearGradient(
                0f, height * 0.38f, 0f, height * 0.58f,
                new int[]{Color.TRANSPARENT, Color.argb(65, 224, 252, 251), Color.TRANSPARENT},
                null,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0f, height * 0.34f, width, height * 0.62f, fillPaint);
        fillPaint.setShader(null);
    }

    private void drawWave(Canvas canvas, float width, float height, float baseY, float amplitude,
                          int color, float speed, float offset, boolean foam) {
        buildWavePath(width, height, baseY, amplitude, speed, offset, true);
        fillPaint.setColor(color);
        fillPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, fillPaint);

        if (foam) {
            buildWavePath(width, height, baseY - dp(1), amplitude, speed, offset, false);
            strokePaint.setColor(Color.argb(95, 226, 255, 255));
            strokePaint.setStrokeWidth(dp(1.4f));
            canvas.drawPath(path, strokePaint);
        }
    }

    private void buildWavePath(float width, float height, float baseY, float amplitude,
                               float speed, float offset, boolean close) {
        path.reset();
        int segments = 42;
        for (int i = 0; i <= segments; i++) {
            float x = width * i / segments;
            float radians = (float) (Math.PI * 2f * i / segments);
            float motion = phase * (float) Math.PI * 2f * speed;
            float y = baseY
                    + (float) Math.sin(radians * 1.15f + motion + offset) * amplitude
                    + (float) Math.sin(radians * 2.25f - motion * 0.55f + offset) * amplitude * 0.24f;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        if (close) {
            path.lineTo(width, height);
            path.lineTo(0f, height);
            path.close();
        }
    }

    private void drawBubbles(Canvas canvas, float width, float height) {
        strokePaint.setStrokeWidth(dp(1f));
        for (int i = 0; i < BUBBLE_COUNT; i++) {
            float progress = (bubbleY[i] - phase * bubbleSpeed[i] + 2f) % 1f;
            float x = bubbleX[i] * width + waveSin(phase * 6f + i) * dp(5);
            float y = height * (0.44f + progress * 0.54f);
            float radius = dp(bubbleRadius[i]);
            int alpha = 35 + (int) (80f * (1f - progress));
            strokePaint.setColor(Color.argb(alpha, 230, 255, 255));
            canvas.drawCircle(x, y, radius, strokePaint);
        }
    }

    private void drawFish(Canvas canvas, float x, float y, float size, boolean flip) {
        float direction = flip ? -1f : 1f;
        fillPaint.setColor(Color.argb(80, 215, 248, 248));
        canvas.save();
        canvas.scale(direction, 1f, x, y);
        canvas.drawOval(x - size, y - size * 0.42f, x + size, y + size * 0.42f, fillPaint);
        path.reset();
        path.moveTo(x - size * 0.85f, y);
        path.lineTo(x - size * 1.65f, y - size * 0.70f);
        path.lineTo(x - size * 1.65f, y + size * 0.70f);
        path.close();
        canvas.drawPath(path, fillPaint);
        canvas.restore();
    }

    private void drawReadabilityShade(Canvas canvas, float width, float height) {
        fillPaint.setShader(new LinearGradient(
                0f, 0f, 0f, height,
                new int[]{Color.argb(25, 0, 27, 35), Color.argb(45, 0, 28, 36), Color.argb(105, 0, 24, 32)},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, width, height, fillPaint);
        fillPaint.setShader(null);
    }

    private float waveSin(float value) {
        return (float) Math.sin(value * Math.PI * 2f);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
