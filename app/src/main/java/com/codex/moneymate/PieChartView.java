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

    void setTextColor(int color) {
        textColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        double total = 0;
        for (MoneyDb.Bar b : data) total += b.value;
        float padding = Math.max(18f, Math.min(getWidth(), getHeight()) * 0.08f);
        float size = Math.max(1f, Math.min(getWidth(), getHeight()) - padding * 2f);
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        RectF rect = new RectF(left, top, left + size, top + size);
        float stroke = Math.max(24f, size * 0.22f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setColor(Color.argb(38, Color.red(textColor), Color.green(textColor), Color.blue(textColor)));
        canvas.drawArc(rect, -90, 360, false, paint);
        if (total <= 0) return;

        float start = -90f;
        for (int i = 0; i < data.size(); i++) {
            float sweep = (float) (360d * data.get(i).value / total);
            paint.setColor(colors[i % colors.length]);
            canvas.drawArc(rect, start, Math.max(0f, sweep - 1.2f), false, paint);
            start += sweep;
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(textColor);
        paint.setTextSize(Math.max(18f, size * 0.075f));
        paint.setFakeBoldText(true);
        canvas.drawText("100%", rect.centerX(), rect.centerY() + paint.getTextSize() * 0.15f, paint);
        paint.setFakeBoldText(false);
    }
}
