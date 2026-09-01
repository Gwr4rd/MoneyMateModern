package com.codex.moneymate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class TrendChartView extends View {
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<MoneyDb.Row> rows = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private String rangeStart;
    private String rangeEnd;
    private String scope = "mensual";
    private String selectedKind = "expense";
    private String incomeLabel = "Ingresos";
    private String expenseLabel = "Gastos";
    private int textColor = Color.WHITE;
    private int mutedColor = Color.LTGRAY;
    private int trackColor = Color.DKGRAY;
    private int incomeColor = Color.rgb(73, 209, 139);
    private int expenseColor = Color.rgb(255, 116, 108);

    TrendChartView(Context context) {
        super(context);
        dateFormat.setLenient(false);
    }

    void setRows(List<MoneyDb.Row> values) {
        rows.clear();
        rows.addAll(values);
        invalidate();
    }

    void setRange(String start, String end, String value) {
        rangeStart = start;
        rangeEnd = end;
        scope = value == null ? "mensual" : value;
        invalidate();
    }

    void setKind(String value) {
        selectedKind = value == null ? "expense" : value;
        invalidate();
    }

    void setLegendLabels(String income, String expense) {
        incomeLabel = income;
        expenseLabel = expense;
        invalidate();
    }

    void setThemeColors(int text, int muted, int track, int income, int expense) {
        textColor = text;
        mutedColor = muted;
        trackColor = track;
        incomeColor = income;
        expenseColor = expense;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        ChartData data = aggregate();
        float density = getResources().getDisplayMetrics().density;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        float left = 4f * density;
        float right = getWidth() - 4f * density;
        float top = 34f * density;
        float bottom = getHeight() - 25f * density;
        float chartHeight = Math.max(1f, bottom - top);

        drawLegend(canvas, left, 12f * density, scaledDensity, density);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, density));
        paint.setColor(trackColor);
        canvas.drawLine(left, bottom, right, bottom, paint);

        double max = 1d;
        for (int i = 0; i < data.labels.length; i++) {
            max = Math.max(max, data.income[i]);
            max = Math.max(max, data.expense[i]);
        }

        float groupWidth = (right - left) / data.labels.length;
        float barWidth = Math.min(12f * density, groupWidth * 0.24f);
        float pairGap = Math.max(2f * density, groupWidth * 0.06f);
        float radius = Math.min(6f * density, barWidth / 2f);
        paint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < data.labels.length; i++) {
            float center = left + groupWidth * (i + 0.5f);
            float incomeHeight = (float) (chartHeight * data.income[i] / max);
            float expenseHeight = (float) (chartHeight * data.expense[i] / max);
            float incomeLeft = center - pairGap / 2f - barWidth;
            float expenseLeft = center + pairGap / 2f;

            drawBar(canvas, incomeLeft, bottom, barWidth, incomeHeight, radius, incomeColor, density);
            drawBar(canvas, expenseLeft, bottom, barWidth, expenseHeight, radius, expenseColor, density);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(9f * scaledDensity);
            paint.setColor(mutedColor);
            canvas.drawText(data.labels[i], center, getHeight() - 6f * density, paint);
        }
    }

    private void drawLegend(Canvas canvas, float left, float centerY, float scaledDensity, float density) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(10f * scaledDensity);
        paint.setColor(incomeColor);
        canvas.drawCircle(left + 4f * density, centerY, 4f * density, paint);
        paint.setColor(textColor);
        canvas.drawText(incomeLabel, left + 13f * density, centerY + 3.5f * density, paint);

        float secondStart = left + 96f * density;
        paint.setColor(expenseColor);
        canvas.drawCircle(secondStart + 4f * density, centerY, 4f * density, paint);
        paint.setColor(textColor);
        canvas.drawText(expenseLabel, secondStart + 13f * density, centerY + 3.5f * density, paint);
    }

    private void drawBar(Canvas canvas, float left, float bottom, float width, float height, float radius, int color, float density) {
        float visibleHeight = height <= 0f ? 3f * density : Math.max(5f * density, height);
        paint.setColor(height <= 0f ? trackColor : color);
        RectF rect = new RectF(left, bottom - visibleHeight, left + width, bottom);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    private ChartData aggregate() {
        int slots = "semanal".equals(scope) ? 7 : 6;
        ChartData data = new ChartData(slots);
        if ("diario".equals(scope)) {
            for (int i = 0; i < slots; i++) data.labels[i] = String.format(Locale.US, "%02dh", i * 4);
            for (MoneyDb.Row row : rows) {
                int hour = parseHour(row.time);
                addRow(data, Math.max(0, Math.min(slots - 1, hour / 4)), row);
            }
            return data;
        }

        Date start = parseDate(rangeStart);
        Date end = parseDate(rangeEnd);
        if (start == null || end == null) {
            for (MoneyDb.Row row : rows) {
                Date date = parseDate(row.date);
                if (date == null) continue;
                if (start == null || date.before(start)) start = date;
                if (end == null || date.after(end)) end = date;
            }
        }
        if (start == null) start = new Date();
        if (end == null || end.before(start)) end = start;
        long span = Math.max(DAY_MS, end.getTime() - start.getTime() + DAY_MS);

        for (int i = 0; i < slots; i++) {
            long time = start.getTime() + Math.min(span - 1L, (span * i) / slots);
            data.labels[i] = slotLabel(new Date(time));
        }
        for (MoneyDb.Row row : rows) {
            Date date = parseDate(row.date);
            if (date == null) continue;
            int slot = (int) (((date.getTime() - start.getTime()) * slots) / span);
            addRow(data, Math.max(0, Math.min(slots - 1, slot)), row);
        }
        return data;
    }

    private void addRow(ChartData data, int slot, MoneyDb.Row row) {
        String kind = row.isTransfer() ? selectedKind : row.kind;
        if ("income".equals(kind)) data.income[slot] += row.amount;
        else data.expense[slot] += row.amount;
    }

    private String slotLabel(Date date) {
        String pattern;
        if ("anual".equals(scope) || "semestral".equals(scope)) pattern = "MM";
        else if ("todo".equals(scope)) pattern = "MM/yy";
        else pattern = "dd";
        return new SimpleDateFormat(pattern, Locale.US).format(date);
    }

    private Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return dateFormat.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int parseHour(String value) {
        try {
            return Integer.parseInt(value.substring(0, 2));
        } catch (Exception ignored) {
            return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        }
    }

    private static final class ChartData {
        final double[] income;
        final double[] expense;
        final String[] labels;

        ChartData(int slots) {
            income = new double[slots];
            expense = new double[slots];
            labels = new String[slots];
        }
    }
}
