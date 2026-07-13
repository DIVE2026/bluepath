package com.bluepath.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ActivityHeatmapView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, Integer> activity = new HashMap<>();
    private final SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);

    public ActivityHeatmapView(Context context) { super(context); }
    public ActivityHeatmapView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setActivity(Map<String, Integer> values) {
        activity.clear();
        if (values != null) activity.putAll(values);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desired = Math.max(150, (int) (width * 0.29f));
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int cols = 53;
        int rows = 7;
        float gap = Math.max(2f, getWidth() / 190f);
        float cell = Math.min((getWidth() - gap * (cols - 1)) / cols, (getHeight() - gap * (rows - 1)) / rows);
        float gridWidth = cell * cols + gap * (cols - 1);
        float left = Math.max(0f, (getWidth() - gridWidth) / 2f);
        Calendar day = Calendar.getInstance(Locale.KOREA);
        int weekday = day.get(Calendar.DAY_OF_WEEK);
        day.add(Calendar.DAY_OF_YEAR, -(52 * 7 + weekday - Calendar.SUNDAY));
        float radius = Math.max(1.5f, cell * 0.22f);
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                int count = activity.getOrDefault(keyFormat.format(day.getTime()), 0);
                paint.setColor(colorFor(count));
                float x = left + col * (cell + gap);
                float y = row * (cell + gap);
                canvas.drawRoundRect(x, y, x + cell, y + cell, radius, radius, paint);
                day.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
    }

    private int colorFor(int count) {
        if (count <= 0) return Color.parseColor("#E7EEF2");
        if (count == 1) return Color.parseColor("#CDEFFF");
        if (count <= 3) return Color.parseColor("#8ED8FA");
        if (count <= 6) return Color.parseColor("#45B8ED");
        return Color.parseColor("#168AC2");
    }
}
