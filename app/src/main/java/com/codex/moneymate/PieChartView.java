package com.codex.moneymate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

final class PieChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<MoneyDb.Bar> data = new ArrayList<>();
    private int textColor = Color.WHITE;
    private int mutedColor = Color.LTGRAY;
    private int trackColor = Color.DKGRAY;
    private String centerTitle = "";
    private String centerSubtitle = "";
    private final int[] colors = new int[]{
            Color.rgb(255, 59, 92),
            Color.rgb(255, 138, 52),
            Color.rgb(255, 202, 40),
            Color.rgb(0, 184, 148),
            Color.rgb(0, 168, 232),
            Color.rgb(61, 90, 254),
            Color.rgb(168, 85, 247),
            Color.rgb(236, 72, 153)
    };

    PieChartView(Context context) {
        super(context);
    }

    void setData(List<MoneyDb.Bar> values) {
        data.clear();
        data.addAll(values);
        invalidate();
    }

    void setThemeColors(int text, int muted, int track) {
        textColor = text;
        mutedColor = muted;
        trackColor = track;
        invalidate();
    }

    void setCenterText(String title, String subtitle) {
        centerTitle = title == null ? "" : title;
        centerSubtitle = subtitle == null ? "" : subtitle;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        double total = 0;
        for (MoneyDb.Bar b : data) total += b.value;
        float density = getResources().getDisplayMetrics().density;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        float available = Math.max(1f, Math.min(getWidth(), getHeight()) - 20f * density);
        float outerSize = Math.min(available * 0.82f, 164f * density);
        float stroke = Math.max(15f * density, outerSize * 0.12f);
        float arcSize = Math.max(1f, outerSize - stroke);
        float left = (getWidth() - arcSize) / 2f;
        float top = (getHeight() - arcSize) / 2f;
        RectF rect = new RectF(left, top, left + arcSize, top + arcSize);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(trackColor);
        canvas.drawArc(rect, -90, 360, false, paint);
        if (total <= 0) return;

        float start = -90f;
        float gap = data.size() <= 1 ? 3.5f : Math.max(1.8f, 4.2f - data.size() * 0.22f);
        for (int i = 0; i < data.size(); i++) {
            float sweep = (float) (360d * data.get(i).value / total);
            paint.setColor(colors[i % colors.length]);
            canvas.drawArc(rect, start + gap / 2f, Math.max(0f, sweep - gap), false, paint);
            start += sweep;
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(textColor);
        paint.setTextSize(22f * scaledDensity);
        paint.setFakeBoldText(true);
        canvas.drawText(centerTitle, rect.centerX(), rect.centerY() - 2f * density, paint);
        paint.setFakeBoldText(false);
        paint.setTextSize(11f * scaledDensity);
        paint.setColor(mutedColor);
        canvas.drawText(centerSubtitle, rect.centerX(), rect.centerY() + 18f * density, paint);
    }
}
