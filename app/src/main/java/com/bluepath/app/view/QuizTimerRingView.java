package com.bluepath.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 남은 시간을 12시 방향에서 시계 방향으로 줄어드는 원호와 가운데 숫자로 보여 주는 퀴즈 타이머.
 */
public class QuizTimerRingView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private String label = "";
    private float remainingFraction = 1f;
    private int arcColor = Color.parseColor("#0E7490");
    private int trackColor = Color.parseColor("#E2E8F0");
    private int textColor = Color.parseColor("#06223F");

    public QuizTimerRingView(Context context) {
        super(context);
        init();
    }

    public QuizTimerRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setColors(int arcColor, int trackColor, int textColor) {
        this.arcColor = arcColor;
        this.trackColor = trackColor;
        this.textColor = textColor;
        invalidate();
    }

    /**
     * @param label            원 가운데에 표시할 남은 시간 문자열
     * @param remainingFraction 전체 제한 시간 대비 남은 시간 비율(0~1)
     */
    public void setRemaining(String label, float remainingFraction) {
        this.label = label == null ? "" : label;
        this.remainingFraction = Math.max(0f, Math.min(1f, remainingFraction));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0f) return;

        float strokeWidth = Math.max(2f, size * 0.1f);
        float inset = strokeWidth / 2f + size * 0.03f;
        float left = (getWidth() - size) / 2f + inset;
        float top = (getHeight() - size) / 2f + inset;
        arcBounds.set(left, top, left + size - inset * 2f, top + size - inset * 2f);

        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(trackColor);
        canvas.drawArc(arcBounds, 0f, 360f, false, trackPaint);

        if (remainingFraction > 0f) {
            arcPaint.setStrokeWidth(strokeWidth);
            arcPaint.setColor(arcColor);
            canvas.drawArc(arcBounds, -90f, 360f * remainingFraction, false, arcPaint);
        }

        if (label.isEmpty()) return;
        textPaint.setColor(textColor);
        float textSize = size * (label.length() >= 4 ? 0.25f : 0.33f);
        textPaint.setTextSize(textSize);
        float available = arcBounds.width() - strokeWidth * 2f;
        float measured = textPaint.measureText(label);
        if (measured > available && measured > 0f) {
            textPaint.setTextSize(textSize * available / measured);
        }
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = arcBounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(label, arcBounds.centerX(), baseline, textPaint);
    }
}
