package com.bluepath.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ActivityHeatmapView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, Integer> activity = new HashMap<>();
    private final SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
    private final String[] monthLabels = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private int year = Calendar.getInstance(Locale.KOREA).get(Calendar.YEAR);
    private final float density;
    private final float cellSize;
    private final float gap;
    private final float leftPadding;
    private final float topPadding;
    private final float rightPadding;
    private final float bottomPadding;

    public ActivityHeatmapView(Context context) {
        this(context, null);
    }

    public ActivityHeatmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        cellSize = dp(17);
        gap = dp(4);
        leftPadding = dp(34);
        topPadding = dp(28);
        rightPadding = dp(12);
        bottomPadding = dp(10);
        setContentDescription("연도별 활동 잔디");
    }

    public void setActivity(Map<String, Integer> values) {
        activity.clear();
        if (values != null) activity.putAll(values);
        invalidate();
    }

    public void setYear(int selectedYear) {
        year = selectedYear;
        requestLayout();
        invalidate();
    }

    public int getYear() {
        return year;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int weekCount = weekCountForYear(year);
        int desiredWidth = Math.round(leftPadding + weekCount * cellSize + Math.max(0, weekCount - 1) * gap + rightPadding);
        int desiredHeight = Math.round(topPadding + 7 * cellSize + 6 * gap + bottomPadding);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int measuredWidth = desiredWidth;
        if (widthMode == MeasureSpec.EXACTLY) measuredWidth = Math.max(widthSize, desiredWidth);
        else if (widthMode == MeasureSpec.AT_MOST) measuredWidth = Math.min(desiredWidth, widthSize);

        setMeasuredDimension(measuredWidth, resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Calendar gridStart = gridStartForYear(year);
        int weekCount = weekCountForYear(year);
        float gridBottom = topPadding + 7 * cellSize + 6 * gap;

        drawMonthLabelsAndBoundaries(canvas, gridStart, gridBottom);
        drawWeekdayLabels(canvas);

        Calendar day = (Calendar) gridStart.clone();
        float radius = dp(3.5f);
        for (int col = 0; col < weekCount; col++) {
            for (int row = 0; row < 7; row++) {
                boolean inSelectedYear = day.get(Calendar.YEAR) == year;
                int count = inSelectedYear ? activity.getOrDefault(keyFormat.format(day.getTime()), 0) : -1;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(colorFor(count));
                float x = leftPadding + col * (cellSize + gap);
                float y = topPadding + row * (cellSize + gap);
                canvas.drawRoundRect(x, y, x + cellSize, y + cellSize, radius, radius, paint);
                day.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
    }

    private void drawMonthLabelsAndBoundaries(Canvas canvas, Calendar gridStart, float gridBottom) {
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(dp(11));
        paint.setColor(Color.parseColor("#52687A"));
        paint.setStyle(Paint.Style.FILL);

        int previousColumn = -10;
        for (int month = Calendar.JANUARY; month <= Calendar.DECEMBER; month++) {
            Calendar monthStart = Calendar.getInstance(Locale.KOREA);
            monthStart.clear();
            monthStart.set(year, month, 1, 12, 0, 0);
            int column = (int) (daysBetween(gridStart, monthStart) / 7L);
            float x = leftPadding + column * (cellSize + gap);

            if (column - previousColumn >= 2) {
                canvas.drawText(monthLabels[month], x, dp(15), paint);
                previousColumn = column;
            }

            if (month > Calendar.JANUARY) {
                paint.setColor(Color.parseColor("#D8E3E9"));
                paint.setStrokeWidth(dp(1));
                float separatorX = x - gap / 2f;
                canvas.drawLine(separatorX, topPadding - dp(5), separatorX, gridBottom, paint);
                paint.setColor(Color.parseColor("#52687A"));
            }
        }
    }

    private void drawWeekdayLabels(Canvas canvas) {
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(dp(9));
        paint.setColor(Color.parseColor("#718696"));
        paint.setStyle(Paint.Style.FILL);
        String[] labels = {"Mon", "Wed", "Fri"};
        int[] rows = {1, 3, 5};
        for (int i = 0; i < rows.length; i++) {
            float y = topPadding + rows[i] * (cellSize + gap) + cellSize * 0.72f;
            canvas.drawText(labels[i], 0, y, paint);
        }
    }

    private Calendar gridStartForYear(int selectedYear) {
        Calendar start = Calendar.getInstance(Locale.KOREA);
        start.clear();
        start.set(selectedYear, Calendar.JANUARY, 1, 12, 0, 0);
        start.add(Calendar.DAY_OF_YEAR, -(start.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY));
        return start;
    }

    private Calendar gridEndForYear(int selectedYear) {
        Calendar end = Calendar.getInstance(Locale.KOREA);
        end.clear();
        end.set(selectedYear, Calendar.DECEMBER, 31, 12, 0, 0);
        end.add(Calendar.DAY_OF_YEAR, Calendar.SATURDAY - end.get(Calendar.DAY_OF_WEEK));
        return end;
    }

    private int weekCountForYear(int selectedYear) {
        long days = daysBetween(gridStartForYear(selectedYear), gridEndForYear(selectedYear)) + 1L;
        return Math.max(53, (int) (days / 7L));
    }

    private long daysBetween(Calendar start, Calendar end) {
        return TimeUnit.MILLISECONDS.toDays(end.getTimeInMillis() - start.getTimeInMillis());
    }

    private int colorFor(int count) {
        if (count < 0) return Color.parseColor("#F7FAFC");
        if (count == 0) return Color.parseColor("#E7EEF2");
        if (count == 1) return Color.parseColor("#CDEFFF");
        if (count <= 3) return Color.parseColor("#8ED8FA");
        if (count <= 6) return Color.parseColor("#45B8ED");
        return Color.parseColor("#168AC2");
    }

    private float dp(float value) {
        return value * density;
    }
}
