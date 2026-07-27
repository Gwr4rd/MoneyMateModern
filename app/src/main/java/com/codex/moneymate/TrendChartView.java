package com.codex.moneymate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

final class TrendChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<MoneyDb.Row> rows = new ArrayList<>();

    TrendChartView(Context context) {
        super(context);
    }

    void setRows(List<MoneyDb.Row> values) {
        rows.clear();
        rows.addAll(values);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        paint.setStrokeWidth(2);
        paint.setColor(Color.argb(34, 120, 132, 128));
        for (int i = 1; i < 5; i++) {
            float y = h * i / 5f;
            canvas.drawLine(0, y, w, y, paint);
        }

        double[] income = new double[6];
        double[] expense = new double[6];
        for (MoneyDb.Row r : rows) {
            int slot = Math.max(0, Math.min(5, daySlot(r.date)));
            if ("income".equals(r.kind)) income[slot] += r.amount;
            else expense[slot] += r.amount;
        }
        double max = 1;
        for (int i = 0; i < 6; i++) {
            max = Math.max(max, income[i]);
            max = Math.max(max, expense[i]);
        }
        float group = w / 6f;
        for (int i = 0; i < 6; i++) {
            float base = h - 16;
            float x = group * i + group * 0.25f;
            float incH = (float) ((h - 32) * income[i] / max);
            float expH = (float) ((h - 32) * expense[i] / max);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(52, 199, 89));
            canvas.drawRect(x, base - incH, x + group * 0.18f, base, paint);
            paint.setColor(Color.rgb(218, 74, 74));
            canvas.drawRect(x + group * 0.24f, base - expH, x + group * 0.42f, base, paint);
        }
    }

    private int daySlot(String date) {
        try {
            int day = Integer.parseInt(date.substring(8, 10));
            return (day - 1) / 5;
        } catch (Exception ignored) {
            return 0;
        }
    }
}
