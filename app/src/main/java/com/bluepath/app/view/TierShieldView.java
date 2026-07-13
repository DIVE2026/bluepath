package com.bluepath.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class TierShieldView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String tier = "브론즈";

    public TierShieldView(Context context) { super(context); init(); }
    public TierShieldView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(5f);
        stroke.setColor(Color.WHITE);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setTier(String tier) {
        this.tier = tier == null ? "브론즈" : tier;
        invalidate();
    }

    private int tierColor() {
        switch (tier) {
            case "실버": return Color.parseColor("#94A3B8");
            case "골드": return Color.parseColor("#EAB308");
            case "플래티넘": return Color.parseColor("#22C1C3");
            case "다이아": return Color.parseColor("#60A5FA");
            default: return Color.parseColor("#B7794A");
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float pad = Math.min(w, h) * 0.08f;
        Path shield = new Path();
        shield.moveTo(w / 2f, pad);
        shield.lineTo(w - pad, h * 0.22f);
        shield.lineTo(w * 0.84f, h * 0.72f);
        shield.quadTo(w / 2f, h - pad, w * 0.16f, h * 0.72f);
        shield.lineTo(pad, h * 0.22f);
        shield.close();
        int color = tierColor();
        fill.setShader(new LinearGradient(0, 0, w, h, lighten(color), darken(color), Shader.TileMode.CLAMP));
        fill.setShadowLayer(12f, 0, 5f, Color.argb(90, 0, 0, 0));
        canvas.drawPath(shield, fill);
        canvas.drawPath(shield, stroke);
        String tierLabel = tier == null || tier.trim().isEmpty() ? "브론즈" : tier.trim();
        float availableWidth = w * 0.72f;
        float tierTextSize = Math.max(14f, Math.min(w, h) * 0.16f);
        text.setTextSize(tierTextSize);
        float measuredWidth = text.measureText(tierLabel);
        if (measuredWidth > availableWidth && measuredWidth > 0f) {
            text.setTextSize(tierTextSize * availableWidth / measuredWidth);
        }
        canvas.drawText(tierLabel, w / 2f, h * 0.55f, text);
        text.setTextSize(Math.max(9f, Math.min(w, h) * 0.09f));
        canvas.drawText("TIER", w / 2f, h * 0.70f, text);
    }

    private int lighten(int color) {
        return Color.rgb(Math.min(255, Color.red(color) + 55), Math.min(255, Color.green(color) + 55), Math.min(255, Color.blue(color) + 55));
    }

    private int darken(int color) {
        return Color.rgb(Math.max(0, Color.red(color) - 55), Math.max(0, Color.green(color) - 55), Math.max(0, Color.blue(color) - 55));
    }
}
