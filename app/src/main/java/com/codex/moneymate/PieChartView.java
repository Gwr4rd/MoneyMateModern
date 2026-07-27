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
            Color.rgb(244, 84, 94),
            Color.rgb(245, 152, 83),
            Color.rgb(248, 205, 82),
            Color.rgb(76, 160, 124),
            Color.rgb(86, 132, 210),
            Color.rgb(160, 112, 210)
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
        int size = Math.min(getWidth(), getHeight()) - 92;
        int left = (getWidth() - size) / 2;
        int top = (getHeight() - size) / 2;
        RectF rect = new RectF(left, top, left + size, top + size);
        if (total <= 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(18);
            paint.setColor(Color.rgb(70, 74, 80));
            canvas.drawOval(rect, paint);
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        float start = -90;
        for (int i = 0; i < data.size(); i++) {
            float sweep = (float) (360d * data.get(i).value / total);
            paint.setColor(colors[i % colors.length]);
            canvas.drawArc(rect, start, sweep, true, paint);
            start += sweep;
        }
        drawLabels(canvas, rect, total);
    }

    private void drawLabels(Canvas canvas, RectF rect, double total) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float radius = rect.width() / 2f;
        paint.setStrokeWidth(2);
        paint.setTextSize(28);
        paint.setFakeBoldText(true);
        float start = -90;
        for (int i = 0; i < data.size() && i < 5; i++) {
            MoneyDb.Bar item = data.get(i);
            float sweep = (float) (360d * item.value / total);
            if (sweep < 10) {
                start += sweep;
                continue;
            }
            float angle = (float) Math.toRadians(start + sweep / 2f);
            float x1 = cx + (float) Math.cos(angle) * radius * 0.92f;
            float y1 = cy + (float) Math.sin(angle) * radius * 0.92f;
            float x2 = cx + (float) Math.cos(angle) * radius * 1.16f;
            float y2 = cy + (float) Math.sin(angle) * radius * 1.16f;
            boolean right = Math.cos(angle) >= 0;
            float x3 = right ? Math.min(getWidth() - 8, x2 + 32) : Math.max(8, x2 - 32);
            paint.setColor(colors[i % colors.length]);
            canvas.drawLine(x1, y1, x2, y2, paint);
            canvas.drawLine(x2, y2, x3, y2, paint);
            paint.setColor(textColor);
            paint.setTextAlign(right ? Paint.Align.LEFT : Paint.Align.RIGHT);
            String name = item.label.length() > 14 ? item.label.substring(0, 14) : item.label;
            canvas.drawText(name, x3, y2 - 4, paint);
            paint.setFakeBoldText(false);
            canvas.drawText(Math.round(100d * item.value / total) + "%", x3, y2 + 26, paint);
            paint.setFakeBoldText(true);
            start += sweep;
        }
        paint.setFakeBoldText(false);
    }
}
