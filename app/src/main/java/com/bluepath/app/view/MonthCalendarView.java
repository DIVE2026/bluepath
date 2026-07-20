package com.bluepath.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MonthCalendarView extends View {

    public interface OnDaySelectedListener {
        void onDaySelected(String isoDate);
    }

    private static final String[] WEEKDAYS = {"일", "월", "화", "수", "목", "금", "토"};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Set<String> markedDays = new HashSet<>();
    private final float density;

    private final int textColor = Color.parseColor("#17324D");
    private final int mutedColor = Color.parseColor("#64748B");
    private final int oceanColor = Color.parseColor("#0E7490");
    private final int selectedFill = Color.parseColor("#0E7490");
    private final int todayStroke = Color.parseColor("#18D6D2");

    private int year;
    private int month; 
    private String selectedDay = "";
    private OnDaySelectedListener listener;

    public MonthCalendarView(Context context) {
        this(context, null);
    }

    public MonthCalendarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        Calendar now = Calendar.getInstance(Locale.KOREA);
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH);
    }

    public void setMonth(int year, int month) {
        this.year = year;
        this.month = month;
        requestLayout();
        invalidate();
    }

    public void setMarkedDays(Set<String> isoDates) {
        markedDays.clear();
        if (isoDates != null) markedDays.addAll(isoDates);
        invalidate();
    }

    public void setSelectedDay(String isoDate) {
        selectedDay = isoDate == null ? "" : isoDate;
        invalidate();
    }

    public void setOnDaySelectedListener(OnDaySelectedListener listener) {
        this.listener = listener;
    }

    private int daysInMonth() {
        Calendar calendar = Calendar.getInstance(Locale.KOREA);
        calendar.clear();
        calendar.set(year, month, 1);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private int firstWeekdayOffset() {
        Calendar calendar = Calendar.getInstance(Locale.KOREA);
        calendar.clear();
        calendar.set(year, month, 1);
        return calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
    }

    private int rowCount() {
        return (int) Math.ceil((firstWeekdayOffset() + daysInMonth()) / 7.0);
    }

    private String isoDate(int day) {
        return String.format(Locale.KOREA, "%04d-%02d-%02d", year, month + 1, day);
    }

    private float headerHeight() {
        return dp(26);
    }

    private float cellHeight() {
        return dp(44);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (headerHeight() + rowCount() * cellHeight() + dp(6));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cellWidth = getWidth() / 7f;

        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(12));
        for (int i = 0; i < 7; i++) {
            paint.setColor(i == 0 ? Color.parseColor("#B42318") : mutedColor);
            canvas.drawText(WEEKDAYS[i], cellWidth * i + cellWidth / 2f, dp(16), paint);
        }

        Calendar now = Calendar.getInstance(Locale.KOREA);
        String todayIso = String.format(Locale.KOREA, "%04d-%02d-%02d",
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));

        int offset = firstWeekdayOffset();
        int days = daysInMonth();
        for (int day = 1; day <= days; day++) {
            int cellIndex = offset + day - 1;
            int rowIndex = cellIndex / 7;
            int columnIndex = cellIndex % 7;
            float centerX = cellWidth * columnIndex + cellWidth / 2f;
            float top = headerHeight() + rowIndex * cellHeight();
            float centerY = top + cellHeight() / 2f - dp(4);
            String iso = isoDate(day);
            boolean selected = iso.equals(selectedDay);

            if (selected) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(selectedFill);
                canvas.drawCircle(centerX, centerY, dp(15), paint);
            } else if (iso.equals(todayIso)) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(todayStroke);
                canvas.drawCircle(centerX, centerY, dp(15), paint);
                paint.setStyle(Paint.Style.FILL);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            paint.setTextSize(dp(14));
            paint.setColor(selected ? Color.WHITE : (columnIndex == 0 ? Color.parseColor("#B42318") : textColor));
            canvas.drawText(String.valueOf(day), centerX, centerY + dp(5), paint);

            if (markedDays.contains(iso)) {
                paint.setColor(selected ? Color.WHITE : oceanColor);
                canvas.drawCircle(centerX, centerY + dp(13), dp(2.5f), paint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float cellWidth = getWidth() / 7f;
            int columnIndex = (int) (event.getX() / cellWidth);
            int rowIndex = (int) ((event.getY() - headerHeight()) / cellHeight());
            if (columnIndex >= 0 && columnIndex < 7 && rowIndex >= 0 && rowIndex < rowCount()) {
                int day = rowIndex * 7 + columnIndex - firstWeekdayOffset() + 1;
                if (day >= 1 && day <= daysInMonth()) {
                    selectedDay = isoDate(day);
                    invalidate();
                    if (listener != null) listener.onDaySelected(selectedDay);
                    performClick();
                }
            }
            return true;
        }
        return event.getAction() == MotionEvent.ACTION_DOWN || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float dp(float value) {
        return value * density;
    }
}
