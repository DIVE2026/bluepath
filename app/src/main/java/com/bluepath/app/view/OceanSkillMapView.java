package com.bluepath.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.bluepath.app.util.SkillProfileCatalog;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interactive constellation-style map for Ocean Skill Passport scores.
 */
public class OceanSkillMapView extends View {
    public interface OnSkillSelectedListener {
        void onSkillSelected(String topic);
    }

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, Integer> mastery = new LinkedHashMap<>();
    private final Map<String, Integer> evidence = new LinkedHashMap<>();
    private final Map<String, RectF> hitTargets = new LinkedHashMap<>();
    private OnSkillSelectedListener listener;
    private String pressedTopic = "";

    public OceanSkillMapView(Context context) {
        super(context);
        init();
    }

    public OceanSkillMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        linePaint.setColor(Color.parseColor("#9BD9DE"));
        linePaint.setStrokeWidth(dp(2));
        linePaint.setStyle(Paint.Style.STROKE);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(2));
        ringPaint.setColor(Color.WHITE);

        textPaint.setColor(Color.parseColor("#17324D"));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        scorePaint.setFakeBoldText(true);

        centerPaint.setColor(Color.parseColor("#06223F"));
        centerPaint.setShadowLayer(dp(8), 0, dp(3), Color.parseColor("#3306223F"));

        for (String topic : SkillProfileCatalog.TOPICS) {
            mastery.put(topic, 50);
            evidence.put(topic, 0);
        }
        setContentDescription("오션 스킬 맵. 역량 노드를 선택하면 상세 근거를 확인할 수 있습니다.");
    }

    public void setSkillData(Map<String, Integer> masteryValues, Map<String, Integer> evidenceValues) {
        for (String topic : SkillProfileCatalog.TOPICS) {
            mastery.put(topic, clamp(masteryValues == null ? 50 : masteryValues.getOrDefault(topic, 50)));
            evidence.put(topic, Math.max(0, evidenceValues == null ? 0 : evidenceValues.getOrDefault(topic, 0)));
        }
        invalidate();
    }

    public void setOnSkillSelectedListener(OnSkillSelectedListener value) {
        listener = value;
    }

    /** 1보다 작으면 요약 카드용 축소 렌더링 (노드·글자·간격을 함께 줄이고 하단 안내문 생략). */
    private float compactScale = 1f;

    public void setCompactScale(float value) {
        compactScale = Math.max(0.35f, Math.min(1f, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        hitTargets.clear();

        float width = getWidth();
        float height = getHeight();
        float s = compactScale;
        float cx = width / 2f;
        float cy = height / 2f - dp(5) * s;
        float outerX = Math.max(dp(112) * s, width * 0.37f);
        float outerY = Math.max(dp(100) * s, height * (s < 1f ? 0.33f : 0.27f));
        float nodeRadius = Math.max(dp(31) * s, Math.min(dp(39) * s, width * 0.095f));
        float centerRadius = dp(52) * s;

        float[] xs = new float[SkillProfileCatalog.TOPICS.length];
        float[] ys = new float[SkillProfileCatalog.TOPICS.length];
        for (int i = 0; i < SkillProfileCatalog.TOPICS.length; i++) {
            double angle = -Math.PI / 2d + i * Math.PI * 2d / SkillProfileCatalog.TOPICS.length;
            xs[i] = cx + (float) Math.cos(angle) * outerX;
            ys[i] = cy + (float) Math.sin(angle) * outerY;
        }

        Path constellation = new Path();
        for (int i = 0; i < xs.length; i++) {
            int next = (i + 1) % xs.length;
            constellation.moveTo(xs[i], ys[i]);
            constellation.lineTo(xs[next], ys[next]);
            canvas.drawLine(cx, cy, xs[i], ys[i], linePaint);
        }
        canvas.drawPath(constellation, linePaint);

        int average = SkillProfileCatalog.averageMastery(mastery);
        canvas.drawCircle(cx, cy, centerRadius, centerPaint);
        scorePaint.setTextSize(dp(21) * s);
        canvas.drawText(String.valueOf(average), cx, cy - dp(1) * s, scorePaint);
        scorePaint.setTextSize(dp(10) * s);
        canvas.drawText("종합", cx, cy + dp(18) * s, scorePaint);

        for (int i = 0; i < SkillProfileCatalog.TOPICS.length; i++) {
            String topic = SkillProfileCatalog.TOPICS[i];
            int score = mastery.getOrDefault(topic, 50);
            int count = evidence.getOrDefault(topic, 0);
            float x = xs[i];
            float y = ys[i];
            float radius = nodeRadius + Math.min(dp(5) * s, score / 25f);
            boolean pressed = topic.equals(pressedTopic);

            nodePaint.setColor(nodeColor(score));
            nodePaint.setShadowLayer(pressed ? dp(12) : dp(6), 0, dp(3), Color.parseColor("#3306223F"));
            canvas.drawCircle(x, y, pressed ? radius + dp(3) : radius, nodePaint);
            canvas.drawCircle(x, y, pressed ? radius + dp(3) : radius, ringPaint);

            scorePaint.setTextSize(dp(17) * s);
            canvas.drawText(score + "", x, y - dp(1) * s, scorePaint);
            if (s >= 0.8f) {
                scorePaint.setTextSize(dp(8.5f));
                canvas.drawText("증거 " + count, x, y + dp(14), scorePaint);
            }

            textPaint.setTextSize(dp(10) * s);
            String[] labelLines = splitLabel(topic);
            float labelY = y + radius + dp(15) * s;
            for (int line = 0; line < labelLines.length; line++) {
                canvas.drawText(labelLines[line], x, labelY + line * dp(12) * s, textPaint);
            }

            hitTargets.put(topic, new RectF(
                    x - radius - dp(10), y - radius - dp(10),
                    x + radius + dp(10), y + radius + dp(28)
            ));
        }

        if (s >= 1f) {
            textPaint.setTextSize(dp(10));
            textPaint.setFakeBoldText(false);
            textPaint.setColor(Color.parseColor("#64748B"));
            canvas.drawText("노드를 눌러 점수 근거와 다음 활동을 확인하세요", cx, height - dp(8), textPaint);
            textPaint.setFakeBoldText(true);
            textPaint.setColor(Color.parseColor("#17324D"));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedTopic = findTopic(event.getX(), event.getY());
                invalidate();
                return !pressedTopic.isEmpty();
            case MotionEvent.ACTION_UP:
                String selected = findTopic(event.getX(), event.getY());
                boolean activated = !pressedTopic.isEmpty() && pressedTopic.equals(selected);
                pressedTopic = "";
                invalidate();
                if (activated) {
                    performClick();
                    if (listener != null) listener.onSkillSelected(selected);
                }
                return activated;
            case MotionEvent.ACTION_CANCEL:
                pressedTopic = "";
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private String findTopic(float x, float y) {
        for (Map.Entry<String, RectF> entry : hitTargets.entrySet()) {
            if (entry.getValue().contains(x, y)) return entry.getKey();
        }
        return "";
    }

    private String[] splitLabel(String topic) {
        if (topic.contains("·")) return topic.split("·", 2);
        if (topic.length() >= 5) return new String[]{topic.substring(0, topic.length() / 2), topic.substring(topic.length() / 2)};
        return new String[]{topic};
    }

    private int nodeColor(int score) {
        if (score >= 85) return Color.parseColor("#047857");
        if (score >= 70) return Color.parseColor("#0E7490");
        if (score >= 55) return Color.parseColor("#2563A8");
        return Color.parseColor("#8A5A20");
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
