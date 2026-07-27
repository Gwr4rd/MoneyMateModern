package com.codex.moneymate;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.InputType;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int IMPORT_BACKUP = 4001;
    private static final int EXPORT_BACKUP = 4002;
    private static final int EXPORT_REPORT = 4003;

    private MoneyDb db;
    private LinearLayout content;
    private String screen = "trans";
    private String transactionMode = "diario";
    private String accountMode = "diario";
    private String statsKind = "expense";
    private String statsDetailCategory;
    private String statsScope = "mensual";
    private String statsAnchorDate;
    private String searchQuery = "";
    private String searchAccount = "";
    private String searchFrom = "";
    private String searchTo = "";
    private String period;
    private long activeAccountId = -1;
    private String pendingExportFormat = "mmbak";
    private String pendingReportStart;
    private String pendingReportEnd;
    private String pendingReportTitle = "Reporte";
    private boolean importInProgress;
    private boolean showHiddenAccounts;
    private SharedPreferences prefs;
    private boolean darkMode;
    private int bg;
    private int surface;
    private int surface2;
    private int textColor;
    private int muted;
    private int accent;
    private int actionColor;
    private int actionSoft;
    private int topSurface;
    private int incomeColor;
    private int expenseColor;
    private int transferColor;
    private int transferSoft;
    private int strokeColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new MoneyDb(this);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        darkMode = prefs.getBoolean("dark_mode", false);
        showHiddenAccounts = prefs.getBoolean("show_hidden_accounts", false);
        applyPalette();
        if (getIntent() != null && getIntent().getData() != null) importBackup(getIntent().getData());
        period = db.latestMonth();
        statsAnchorDate = db.latestDate();
        draw();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == IMPORT_BACKUP) importBackup(data.getData());
        if (requestCode == EXPORT_BACKUP) exportBackup(data.getData());
        if (requestCode == EXPORT_REPORT) exportReport(data.getData());
    }

    private void draw() {
        applyPalette();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(10), dp(8), dp(10), dp(6));

        root.addView(topBar());

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout navShell = new LinearLayout(this);
        navShell.setOrientation(LinearLayout.VERTICAL);
        navShell.setPadding(dp(5), dp(4), dp(5), dp(4));
        navShell.setBackground(rounded(surface, 30, 0, strokeColor));
        navShell.setElevation(0);
        navShell.setLayoutParams(margins(-1, -2, 8, 0));
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.addView(tab("Transacciones", "trans", R.drawable.ic_nav_register), new LinearLayout.LayoutParams(0, dp(52), 1));
        nav.addView(tab("Estado", "stats", R.drawable.ic_nav_status), new LinearLayout.LayoutParams(0, dp(52), 1));
        nav.addView(tab("Cuentas", "accounts", R.drawable.ic_nav_accounts), new LinearLayout.LayoutParams(0, dp(52), 1));
        navShell.addView(nav);
        root.addView(navShell);
        setContentView(root);
        renderScreen();
    }

    private LinearLayout topBar() {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(dp(4), dp(4), dp(2), dp(4));
        top.setBackground(rounded(topSurface, 22, 0, strokeColor));
        top.setElevation(0);
        top.setLayoutParams(margins(-1, dp(62), 0, 10));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_pig);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        top.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));

        String subtitle = titleForScreen() + " · " + ("stats".equals(screen) ? statsRange().label : monthLabel(period));
        TextView title = text("", 18, true, textColor);
        title.setText(appHeaderText("MoneyMate Modern", subtitle));
        title.setGravity(Gravity.CENTER);
        title.setOnClickListener(v -> {
            if ("stats".equals(screen)) statsDateDialog();
            else monthDialog();
        });
        top.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        top.addView(topIcon("⋮", v -> menuDialog(v)), new LinearLayout.LayoutParams(dp(48), dp(48)));
        return top;
    }

    private String titleForScreen() {
        if ("stats".equals(screen)) return "Estadisticas";
        if ("accounts".equals(screen)) return "Cuentas";
        return "Transacciones";
    }

    private SpannableString appHeaderText(String title, String subtitle) {
        String full = title + "\n" + subtitle;
        SpannableString span = new SpannableString(full);
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new RelativeSizeSpan(1.05f), 0, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        int subtitleStart = title.length() + 1;
        span.setSpan(new RelativeSizeSpan(0.62f), subtitleStart, full.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(muted), subtitleStart, full.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    private String displayAccountType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(Locale.US);
        if (value.contains("efectivo") || value.contains("cash")) return "Efectivo";
        return "Cuentas de Banco";
    }

    private void applyPalette() {
        darkMode = prefs != null && prefs.getBoolean("dark_mode", false);
        if (darkMode) {
            bg = Color.rgb(18, 18, 18);
            surface = Color.rgb(28, 28, 30);
            surface2 = Color.rgb(36, 37, 39);
            topSurface = Color.argb(196, 28, 28, 30);
            textColor = Color.rgb(242, 242, 247);
            muted = Color.rgb(166, 168, 173);
            accent = Color.rgb(52, 199, 89);
            actionColor = Color.rgb(79, 86, 96);
            actionSoft = Color.rgb(42, 44, 48);
            incomeColor = Color.rgb(48, 209, 88);
            expenseColor = Color.rgb(255, 69, 58);
            transferColor = Color.rgb(90, 155, 255);
            transferSoft = Color.rgb(25, 46, 72);
            strokeColor = Color.rgb(48, 49, 52);
        } else {
            bg = Color.rgb(242, 242, 247);
            surface = Color.WHITE;
            surface2 = Color.rgb(248, 249, 251);
            topSurface = Color.argb(214, 255, 255, 255);
            textColor = Color.rgb(23, 33, 43);
            muted = Color.rgb(83, 100, 113);
            accent = Color.rgb(52, 199, 89);
            actionColor = Color.rgb(48, 57, 70);
            actionSoft = Color.rgb(230, 234, 239);
            incomeColor = Color.rgb(22, 163, 74);
            expenseColor = Color.rgb(255, 59, 48);
            transferColor = Color.rgb(29, 108, 224);
            transferSoft = Color.rgb(229, 241, 255);
            strokeColor = Color.rgb(226, 232, 228);
        }
        getWindow().setStatusBarColor(darkMode ? bg : Color.rgb(242, 242, 247));
        getWindow().setNavigationBarColor(bg);
    }

    private void toggleTheme() {
        prefs.edit().putBoolean("dark_mode", !darkMode).apply();
        draw();
    }

    private void menuDialog(View anchor) {
        PopupWindow[] holder = new PopupWindow[1];
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(14), dp(12), dp(14), dp(12));
        menu.setBackground(rounded(surface, 24, 0, strokeColor));
        menu.addView(menuItem(R.drawable.ic_menu_period, "Periodo", "Elegir fecha con calendario", v -> closeThen(holder, () -> {
            if ("stats".equals(screen)) statsDateDialog();
            else monthDialog();
        })));
        menu.addView(menuItem(R.drawable.ic_menu_settings, "Configuracion", "Preferencias generales", v -> closeThen(holder, () -> settingsDialog())));
        menu.addView(menuItem(darkMode ? R.drawable.ic_menu_sun : R.drawable.ic_menu_moon_cloud, darkMode ? "Modo claro" : "Modo oscuro", "Cambiar apariencia", v -> closeThen(holder, () -> toggleTheme())));
        menu.addView(menuItem(R.drawable.ic_menu_currency, "Moneda", "Pais y simbolo de importes", v -> closeThen(holder, () -> currencyDialog())));
        menu.addView(menuItem(R.drawable.ic_menu_categories, "Categorias", "Ingresos, gastos y transferencias", v -> closeThen(holder, () -> categoryDialog())));
        menu.addView(menuItem(R.drawable.ic_menu_report, "Generar reporte", "Semanal, mensual, anual o todo", v -> closeThen(holder, this::reportDialog)));
        menu.addView(menuItem(R.drawable.ic_menu_sync, "Sincronizar", "Subir o descargar desde Supabase", v -> closeThen(holder, this::supabaseDialog)));
        menu.addView(menuItem(R.drawable.ic_menu_import, "Importar datos", "MMBAK, CSV, JSON o XLSX", v -> closeThen(holder, () -> openImport())));
        menu.addView(menuItem(R.drawable.ic_menu_export, "Exportar respaldo", "MMBAK, CSV, JSON o XLSX", v -> closeThen(holder, () -> openExport())));
        holder[0] = new PopupWindow(menu, dp(304), LinearLayout.LayoutParams.WRAP_CONTENT, true);
        holder[0].setOutsideTouchable(true);
        holder[0].setBackgroundDrawable(rounded(surface, 24, 0, strokeColor));
        holder[0].setElevation(0);
        holder[0].showAsDropDown(anchor, -dp(248), dp(8));
    }

    private void closeThen(AlertDialog[] holder, Runnable action) {
        if (holder[0] != null) holder[0].dismiss();
        action.run();
    }

    private void closeThen(PopupWindow[] holder, Runnable action) {
        if (holder[0] != null) holder[0].dismiss();
        action.run();
    }

    private View menuItem(int drawableRes, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(10), dp(8), dp(10), dp(8));
        item.setBackground(rounded(surface2, 8, 0, strokeColor));
        item.setLayoutParams(margins(-1, -2, 0, 8));
        Drawable drawable = getResources().getDrawable(drawableRes).mutate();
        drawable.setTint(actionColor);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(drawable);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(6), dp(6), dp(6), dp(6));
        item.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView label = text(title + "\n" + subtitle, 14, true, textColor);
        label.setPadding(dp(10), 0, 0, 0);
        item.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        item.setOnClickListener(listener);
        return item;
    }

    private void renderScreen() {
        content.removeAllViews();
        if ("stats".equals(screen)) renderStats();
        else if ("accounts".equals(screen)) renderAccounts();
        else renderTransactions();
        animateContent();
    }

    private void renderTransactions() {
        String start = "total".equals(transactionMode) ? null : period + "-01";
        String end = "total".equals(transactionMode) ? null : period + "-31";
        if ("buscar".equals(transactionMode)) {
            start = searchFrom.isEmpty() ? null : searchFrom;
            end = searchTo.isEmpty() ? null : searchTo;
            if (start == null || end == null) {
                start = null;
                end = null;
            }
        }
        List<MoneyDb.Row> rows = db.transactionsForDisplay(start, end);
        if ("buscar".equals(transactionMode)) rows = filterTransactions(rows);
        MoneyDb.Summary s = summaryForRows(rows);
        LinearLayout summary = panel();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(kpi("Ingresos", money(s.income), incomeColor), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(kpi("Gastos", money(s.expense), expenseColor), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(kpi("Balance", money(s.balance), textColor), new LinearLayout.LayoutParams(0, -2, 1));
        summary.addView(row);
        content.addView(summary);
        content.addView(dataStatusStrip(s));
        content.addView(topAction("Nuevo movimiento", v -> movementDialog(null)));

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.addView(modeButton("Diario", "diario"), pillParams(false));
        modes.addView(modeButton("Mensual", "mensual"), pillParams(false));
        modes.addView(modeButton("Total", "total"), pillParams(false));
        modes.addView(searchModeButton(), pillParams(true));
        content.addView(modes);

        if ("buscar".equals(transactionMode)) content.addView(activeSearchStrip(rows.size()));
        if ("mensual".equals(transactionMode)) renderMonthly(rows);
        else renderTransactionRows(rows, false);
    }

    private MoneyDb.Summary summaryForRows(List<MoneyDb.Row> rows) {
        double income = 0;
        double expense = 0;
        for (MoneyDb.Row row : rows) {
            if (row.isTransfer()) continue;
            if ("income".equals(row.kind)) income += row.amount;
            else if ("expense".equals(row.kind)) expense += row.amount;
        }
        return new MoneyDb.Summary(income, expense, income - expense, rows.size());
    }

    private List<MoneyDb.Row> filterTransactions(List<MoneyDb.Row> rows) {
        List<MoneyDb.Row> out = new ArrayList<>();
        String query = searchQuery.trim().toLowerCase(Locale.US);
        for (MoneyDb.Row row : rows) {
            if (!searchAccount.isEmpty()) {
                boolean accountMatch = row.isTransfer()
                        ? searchAccount.equals(row.transferFrom) || searchAccount.equals(row.transferTo)
                        : searchAccount.equals(row.account);
                if (!accountMatch) continue;
            }
            if (!query.isEmpty()) {
                String type = row.isTransfer() ? "transferencia" : ("income".equals(row.kind) ? "ingreso" : "gasto");
                String searchable = (row.date + " " + row.time + " " + row.account + " " + row.category + " "
                        + row.transferFrom + " " + row.transferTo + " " + row.note + " " + row.description + " "
                        + type + " " + String.format(Locale.US, "%.2f", row.amount)).toLowerCase(Locale.US);
                if (!searchable.contains(query)) continue;
            }
            out.add(row);
        }
        return out;
    }

    private View activeSearchStrip(int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(6), dp(7));
        row.setBackground(rounded(actionSoft, 8, 0, strokeColor));
        row.setLayoutParams(margins(-1, -2, 0, 8));
        String filters = searchQuery.isEmpty() ? "Busqueda activa" : "“" + searchQuery + "”";
        TextView label = text(filters + " · " + count + " resultados", 12, true, textColor);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Button clear = smallButton("Limpiar", v -> clearSearch());
        row.addView(clear, new LinearLayout.LayoutParams(dp(74), dp(34)));
        return row;
    }

    private void clearSearch() {
        searchQuery = "";
        searchAccount = "";
        searchFrom = "";
        searchTo = "";
        transactionMode = "diario";
        renderScreen();
    }

    private View dataStatusStrip(MoneyDb.Summary summary) {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setBackground(rounded(softAccent(), 12, 0, strokeColor));
        strip.setPadding(dp(10), dp(8), dp(10), dp(8));
        strip.setLayoutParams(margins(-1, -2, 0, 8));
        String source = prefs.getString("last_import_name", "datos locales");
        String label = importInProgress ? "Cargando datos..." : summary.count + " movimientos";
        TextView left = text(label, 13, true, actionColor);
        strip.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        TextView right = text(source, 12, false, muted);
        right.setGravity(Gravity.RIGHT);
        strip.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        return strip;
    }

    private void renderMonthly(List<MoneyDb.Row> rows) {
        Map<String, double[]> byDay = new LinkedHashMap<>();
        for (MoneyDb.Row r : rows) {
            if (r.isTransfer()) continue;
            double[] vals = byDay.get(r.date);
            if (vals == null) {
                vals = new double[]{0, 0};
                byDay.put(r.date, vals);
            }
            if ("income".equals(r.kind)) vals[0] += r.amount;
            else vals[1] += r.amount;
        }
        if (byDay.isEmpty()) content.addView(empty("No hay movimientos en este periodo."));
        for (Map.Entry<String, double[]> e : byDay.entrySet()) {
            double balance = e.getValue()[0] - e.getValue()[1];
            content.addView(simpleLine(e.getKey(), "Ingreso " + money(e.getValue()[0]) + "   Gasto " + money(e.getValue()[1]) + "   Balance " + money(balance)));
        }
    }

    private void renderTransactionRows(List<MoneyDb.Row> rows, boolean onlyNotes) {
        String lastDate = "";
        int shown = 0;
        for (MoneyDb.Row r : rows) {
            if (onlyNotes && r.note.trim().isEmpty() && r.description.trim().isEmpty()) continue;
            if (!r.date.equals(lastDate)) {
                lastDate = r.date;
                content.addView(section(r.date));
            }
            content.addView(transactionRow(r));
            shown++;
        }
        if (shown == 0) content.addView(empty("No hay movimientos para mostrar."));
    }

    private void renderStats() {
        DateRange range = statsRange();
        MoneyDb.Summary s = db.summaryBetween(range.start, range.end);
        if (statsDetailCategory != null) {
            renderStatsDetail();
            return;
        }
        List<MoneyDb.Bar> bars = db.categoryTotalsBetween(statsKind, range.start, range.end);

        content.addView(statsPeriodHeader());
        content.addView(statsTotalsHeader(s));

        PieChartView pie = new PieChartView(this);
        pie.setData(bars);
        pie.setTextColor(textColor);

        LinearLayout pieBox = flatSection();
        if (bars.isEmpty()) {
            pieBox.addView(empty("Sin datos en este periodo."));
        } else {
            pieBox.addView(pie, new LinearLayout.LayoutParams(-1, dp(300)));
            animateChart(pie);
        }
        content.addView(pieBox);

        double total = 0;
        for (MoneyDb.Bar b : bars) total += b.value;
        for (int i = 0; i < bars.size(); i++) {
            content.addView(statCategoryRow(bars.get(i), total, i));
        }
    }

    private void renderStatsDetail() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(4), 0, dp(8));
        head.addView(iconButton(R.drawable.ic_action_back, v -> {
            statsDetailCategory = null;
            renderScreen();
        }), new LinearLayout.LayoutParams(dp(42), dp(40)));
        TextView title = text(statsDetailCategory, 18, true, textColor);
        title.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView month = text(statsRange().label, 14, true, muted);
        month.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        head.addView(month, new LinearLayout.LayoutParams(dp(116), dp(44)));
        content.addView(head);

        List<MoneyDb.Row> rows = filteredCategoryRows();
        double total = 0;
        for (MoneyDb.Row row : rows) total += row.amount;
        LinearLayout balance = compactPanel();
        balance.addView(kpi("Balance total", money(total), "income".equals(statsKind) ? incomeColor : expenseColor));
        content.addView(balance);

        TrendChartView trend = new TrendChartView(this);
        trend.setRows(rows);
        LinearLayout chart = flatSection();
        chart.addView(text("Movimiento mensual", 14, true, textColor));
        chart.addView(trend, new LinearLayout.LayoutParams(-1, dp(168)));
        content.addView(chart);
        animateChart(trend);

        renderTransactionRows(rows, false);
    }

    private List<MoneyDb.Row> filteredCategoryRows() {
        List<MoneyDb.Row> out = new ArrayList<>();
        DateRange range = statsRange();
        for (MoneyDb.Row row : db.transactionsForDisplay(range.start, range.end)) {
            if (row.category.equals(statsDetailCategory) && row.kind.equals(statsKind)) out.add(row);
        }
        return out;
    }

    private LinearLayout statsPeriodHeader() {
        DateRange range = statsRange();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(8));
        row.setLayoutParams(margins(-1, -2, 0, 4));
        TextView previous = navText("‹", v -> shiftStatsPeriod(-1));
        row.addView(previous, new LinearLayout.LayoutParams(dp(38), dp(42)));
        TextView month = text(range.label, 17, true, textColor);
        month.setGravity(Gravity.CENTER_VERTICAL);
        month.setOnClickListener(v -> statsDateDialog());
        row.addView(month, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView next = navText("›", v -> shiftStatsPeriod(1));
        row.addView(next, new LinearLayout.LayoutParams(dp(38), dp(42)));
        LinearLayout scope = statsScopeControl();
        LinearLayout.LayoutParams scopeParams = new LinearLayout.LayoutParams(dp(112), dp(42));
        scopeParams.setMargins(dp(8), 0, 0, 0);
        row.addView(scope, scopeParams);
        return row;
    }

    private LinearLayout statsScopeControl() {
        LinearLayout control = new LinearLayout(this);
        control.setOrientation(LinearLayout.HORIZONTAL);
        control.setGravity(Gravity.CENTER);
        control.setBackground(rounded(controlSurface(), 8, 1, strokeColor));
        control.setPadding(dp(8), 0, dp(8), 0);
        Drawable drawable = getResources().getDrawable(R.drawable.ic_action_filter).mutate();
        drawable.setTint(actionColor);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(drawable);
        control.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView label = text(scopeLabel(statsScope), 12, true, textColor);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(5), 0, 0, 0);
        control.addView(label, new LinearLayout.LayoutParams(0, dp(40), 1));
        control.setOnClickListener(v -> statsScopeDialog());
        return control;
    }

    private LinearLayout statsTotalsHeader(MoneyDb.Summary s) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(margins(-1, -2, 0, 10));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        boolean incomeSelected = "income".equals(statsKind);
        TextView income = text("Ingresos  " + money(s.income), 14, true, incomeSelected ? textColor : muted);
        income.setGravity(Gravity.CENTER);
        income.setOnClickListener(v -> {
            statsKind = "income";
            statsDetailCategory = null;
            renderScreen();
        });
        row.addView(income, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView expense = text("Gastos  " + money(s.expense), 14, true, incomeSelected ? muted : textColor);
        expense.setGravity(Gravity.CENTER);
        expense.setOnClickListener(v -> {
            statsKind = "expense";
            statsDetailCategory = null;
            renderScreen();
        });
        row.addView(expense, new LinearLayout.LayoutParams(0, dp(42), 1));
        box.addView(row);

        LinearLayout underline = new LinearLayout(this);
        underline.setOrientation(LinearLayout.HORIZONTAL);
        View incomeLine = new View(this);
        incomeLine.setBackgroundColor(incomeSelected ? incomeColor : Color.TRANSPARENT);
        underline.addView(incomeLine, new LinearLayout.LayoutParams(0, dp(3), 1));
        View expenseLine = new View(this);
        expenseLine.setBackgroundColor(incomeSelected ? Color.TRANSPARENT : expenseColor);
        underline.addView(expenseLine, new LinearLayout.LayoutParams(0, dp(3), 1));
        box.addView(underline);
        return box;
    }

    private TextView navText(String value, View.OnClickListener listener) {
        TextView v = text(value, 30, false, textColor);
        v.setGravity(Gravity.CENTER);
        v.setOnClickListener(listener);
        return v;
    }

    private LinearLayout flatSection() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, dp(6));
        box.setLayoutParams(margins(-1, -2, 0, 4));
        return box;
    }

    private View statCategoryRow(MoneyDb.Bar bar, double total, int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(rounded(surface, 0, 0, strokeColor));
        row.setPadding(dp(10), dp(9), dp(10), dp(9));
        row.setLayoutParams(margins(-1, -2, 0, 1));

        int color = statColor(index);
        int pct = total <= 0 ? 0 : (int) Math.round(100d * bar.value / total);
        TextView badge = text(pct + "%", 12, true, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(color, 6, 0, color));
        row.addView(badge, new LinearLayout.LayoutParams(dp(52), dp(32)));

        TextView label = text(bar.label, 14, true, textColor);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(12), 0, dp(8), 0);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(44), 1));

        TextView amount = text(money(bar.value), 14, true, textColor);
        amount.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(amount, new LinearLayout.LayoutParams(dp(126), dp(44)));
        row.setOnClickListener(v -> {
            statsDetailCategory = bar.label;
            renderScreen();
        });
        return row;
    }

    private int statColor(int index) {
        int[] colors = new int[]{
                Color.rgb(244, 84, 94),
                Color.rgb(245, 152, 83),
                Color.rgb(248, 205, 82),
                Color.rgb(76, 160, 124),
                Color.rgb(86, 132, 210),
                Color.rgb(160, 112, 210)
        };
        return colors[index % colors.length];
    }

    private void renderAccounts() {
        List<MoneyDb.AccountTotal> accounts = db.accountTotals();
        if (activeAccountId >= 0) {
            MoneyDb.AccountTotal active = findAccount(accounts, activeAccountId);
            if (active != null) {
                renderAccountDetail(active);
                return;
            }
            activeAccountId = -1;
        }
        double capital = 0;
        double debt = 0;
        for (MoneyDb.AccountTotal a : accounts) {
            if (!a.includeTotal || a.hidden) continue;
            if (a.balance >= 0) capital += a.balance;
            else debt += Math.abs(a.balance);
        }
        LinearLayout summary = compactPanel();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(kpi("Capital", money(capital), incomeColor), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(kpi("A deber", money(debt), expenseColor), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(kpi("Balance", money(capital - debt), textColor), new LinearLayout.LayoutParams(0, -2, 1));
        summary.addView(row);
        content.addView(summary);

        content.addView(accountActions());

        List<MoneyDb.AccountTotal> visible = new ArrayList<>();
        List<MoneyDb.AccountTotal> hidden = new ArrayList<>();
        for (MoneyDb.AccountTotal a : accounts) {
            if (a.hidden) hidden.add(a);
            else visible.add(a);
        }
        sortAccounts(visible);
        sortAccounts(hidden);

        Map<String, List<MoneyDb.AccountTotal>> groups = new LinkedHashMap<>();
        groups.put("Efectivo", new ArrayList<>());
        groups.put("Cuentas de Banco", new ArrayList<>());
        for (MoneyDb.AccountTotal a : visible) {
            String type = displayAccountType(a.type);
            List<MoneyDb.AccountTotal> list = groups.get(type);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(type, list);
            }
            list.add(a);
        }
        for (Map.Entry<String, List<MoneyDb.AccountTotal>> group : groups.entrySet()) {
            if (group.getValue().isEmpty()) continue;
            content.addView(accountGroupHeader(group.getKey(), groupTotal(group.getValue())));
            for (MoneyDb.AccountTotal a : group.getValue()) {
                content.addView(accountRow(a));
            }
        }
        if (showHiddenAccounts && !hidden.isEmpty()) {
            content.addView(accountGroupHeader("Ocultas", 0));
            for (MoneyDb.AccountTotal a : hidden) content.addView(accountRow(a));
        }
        if (!hidden.isEmpty()) content.addView(hiddenAccountsToggle());
    }

    private View insightPanel(String label, String title, String value, int color) {
        LinearLayout box = compactPanel();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = text(label + "\n" + title, 13, false, textColor);
        left.setText(highlightedMeta(label, title, ""));
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        TextView amount = text(value, 14, true, color);
        amount.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        amount.setBackground(rounded(color == expenseColor ? softExpense() : softIncome(), 12, 0, strokeColor));
        amount.setPadding(dp(10), dp(6), dp(10), dp(6));
        row.addView(amount, new LinearLayout.LayoutParams(dp(132), -2));
        box.addView(row);
        return box;
    }

    private void renderAccountDetail(MoneyDb.AccountTotal account) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(8));
        ImageButton back = iconButton(R.drawable.ic_action_back, v -> {
            activeAccountId = -1;
            renderScreen();
        });
        head.addView(back, new LinearLayout.LayoutParams(dp(42), dp(40)));
        TextView name = text(account.name + "\n" + displayAccountType(account.type), 15, true, textColor);
        name.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(name, new LinearLayout.LayoutParams(0, dp(44), 1));
        head.addView(iconButton(R.drawable.ic_action_edit, v -> accountDialog(account)), new LinearLayout.LayoutParams(dp(42), dp(40)));
        content.addView(head);
        content.addView(topAction("Nuevo movimiento", v -> movementDialog(null)));

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.addView(accountModeButton("Diario", "diario"), pillParams(false));
        modes.addView(accountModeButton("Mensual", "mensual"), pillParams(false));
        modes.addView(accountModeButton("Anual", "anual"), pillParams(true));
        content.addView(modes);

        String start = "anual".equals(accountMode) ? period.substring(0, 4) + "-01-01" : period + "-01";
        String end = "anual".equals(accountMode) ? period.substring(0, 4) + "-12-31" : period + "-31";
        List<MoneyDb.Row> rows = db.transactionsForDisplayAccount(account.name, start, end);
        double deposits = 0;
        double withdrawals = 0;
        for (MoneyDb.Row r : rows) {
            if (r.isTransfer()) {
                if (account.name.equals(r.transferTo)) deposits += r.amount;
                else withdrawals += r.amount;
            } else if ("income".equals(r.kind)) {
                deposits += r.amount;
            } else {
                withdrawals += r.amount;
            }
        }
        LinearLayout summary = compactPanel();
        TextView range = text(("anual".equals(accountMode) ? period.substring(0, 4) : "1. " + monthLabel(period)) + "     " + account.currency, 12, true, muted);
        range.setGravity(Gravity.CENTER);
        summary.addView(range);
        LinearLayout numbers = new LinearLayout(this);
        numbers.setOrientation(LinearLayout.HORIZONTAL);
        numbers.addView(kpi("Depositos", money(deposits), incomeColor), new LinearLayout.LayoutParams(0, -2, 1));
        numbers.addView(kpi("Retiros", money(withdrawals), expenseColor), new LinearLayout.LayoutParams(0, -2, 1));
        numbers.addView(kpi("Balance", money(deposits - withdrawals), textColor), new LinearLayout.LayoutParams(0, -2, 1));
        numbers.addView(kpi("Saldo", money(Math.abs(account.balance)), account.balance >= 0 ? incomeColor : expenseColor), new LinearLayout.LayoutParams(0, -2, 1));
        summary.addView(numbers);
        content.addView(summary);

        if ("anual".equals(accountMode)) {
            TrendChartView trend = new TrendChartView(this);
            trend.setRows(rows);
            LinearLayout chart = compactPanel();
            chart.addView(text("Movimiento anual", 14, true, textColor));
            chart.addView(trend, new LinearLayout.LayoutParams(-1, dp(180)));
            content.addView(chart);
        } else if ("mensual".equals(accountMode)) {
            renderMonthly(rows);
        } else {
            renderTransactionRows(rows, false);
        }
    }

    private MoneyDb.AccountTotal findAccount(List<MoneyDb.AccountTotal> accounts, long id) {
        for (MoneyDb.AccountTotal account : accounts) {
            if (account.id == id) return account;
        }
        return null;
    }

    private double groupTotal(List<MoneyDb.AccountTotal> accounts) {
        double total = 0;
        for (MoneyDb.AccountTotal account : accounts) {
            if (account.includeTotal && !account.hidden) total += account.balance;
        }
        return total;
    }

    private void sortAccounts(List<MoneyDb.AccountTotal> accounts) {
        Collections.sort(accounts, new Comparator<MoneyDb.AccountTotal>() {
            @Override
            public int compare(MoneyDb.AccountTotal left, MoneyDb.AccountTotal right) {
                int leftEmpty = Math.abs(left.balance) < 0.005 ? 1 : 0;
                int rightEmpty = Math.abs(right.balance) < 0.005 ? 1 : 0;
                if (leftEmpty != rightEmpty) return leftEmpty - rightEmpty;
                return left.name.compareToIgnoreCase(right.name);
            }
        });
    }

    private LinearLayout accountActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(margins(-1, -2, 0, 8));
        row.addView(topActionButton("Nueva cuenta", v -> accountDialog()), new LinearLayout.LayoutParams(0, dp(44), 1));
        return row;
    }

    private TextView hiddenAccountsToggle() {
        TextView v = text(showHiddenAccounts ? "Ocultar cuentas ocultas" : "Mostrar cuentas ocultas", 12, false, muted);
        v.setGravity(Gravity.CENTER);
        v.setAlpha(0.62f);
        v.setPadding(0, dp(14), 0, dp(18));
        v.setOnClickListener(view -> toggleHiddenAccounts());
        return v;
    }

    private TextView topAction(String label, View.OnClickListener listener) {
        TextView v = text(label, 14, true, Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setBackground(rounded(actionColor, 16, 0, actionColor));
        v.setPadding(dp(12), 0, dp(12), 0);
        v.setLayoutParams(margins(-1, dp(44), 0, 8));
        v.setOnClickListener(listener);
        return v;
    }

    private Button topActionButton(String label, View.OnClickListener listener) {
        Button b = smallButton(label, listener);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(actionColor, 16, 0, actionColor));
        return b;
    }

    private void toggleHiddenAccounts() {
        showHiddenAccounts = !showHiddenAccounts;
        prefs.edit().putBoolean("show_hidden_accounts", showHiddenAccounts).apply();
        renderScreen();
    }

    private void animateContent() {
        content.setAlpha(0f);
        content.setTranslationY(dp(8));
        content.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private LinearLayout accountGroupHeader(String title, double total) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(14), dp(2), dp(6));
        TextView label = text(title, 12, true, muted);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(money(total), 12, true, muted);
        value.setGravity(Gravity.RIGHT);
        row.addView(value, new LinearLayout.LayoutParams(dp(132), -2));
        return row;
    }

    private LinearLayout fabRow(String label, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.RIGHT);
        row.setPadding(0, dp(8), 0, dp(10));
        Button add = smallButton(label, listener);
        add.setTextSize(24);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setTextColor(Color.WHITE);
        add.setBackground(rounded(actionColor, 28, 0, actionColor));
        row.addView(add, new LinearLayout.LayoutParams(dp(56), dp(56)));
        return row;
    }

    private Button accountModeButton(String label, String target) {
        Button b = smallButton(label, v -> {
            accountMode = target;
            renderScreen();
        });
        b.setTextColor(accountMode.equals(target) ? Color.WHITE : muted);
        b.setBackground(rounded(accountMode.equals(target) ? actionColor : surface, 14, 0, strokeColor));
        return b;
    }

    private void renderBars(List<MoneyDb.Bar> bars, int color) {
        double total = 0;
        for (MoneyDb.Bar b : bars) total += b.value;
        if (bars.isEmpty()) {
            content.addView(empty("Sin datos todavia."));
            return;
        }
        for (MoneyDb.Bar b : bars) {
            LinearLayout box = panel();
            int pct = total <= 0 ? 0 : (int) Math.round(b.value * 100 / total);
            box.addView(text(pct + "%  " + b.label + "  " + money(b.value), 14, true, textColor));
            View bar = new View(this);
            bar.setBackgroundColor(color);
            int width = Math.max(dp(24), (int) (dp(300) * (total <= 0 ? 0 : b.value / total)));
            box.addView(bar, new LinearLayout.LayoutParams(width, dp(8)));
            content.addView(box);
        }
    }

    private void movementDialog(MoneyDb.Row copyFrom) {
        movementDialog(copyFrom, false, null);
    }

    private void editMovementDialog(MoneyDb.Row row) {
        movementDialog(row, true, null);
    }

    private void movementDialog(MoneyDb.Row copyFrom, boolean editMode) {
        movementDialog(copyFrom, editMode, null);
    }

    private void movementDialog(MoneyDb.Row copyFrom, boolean editMode, String forcedDate) {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(16), dp(8), dp(16), dp(16));
        form.setOrientation(LinearLayout.VERTICAL);

        Spinner type = spinner(labels("Gasto", "Ingreso", "Transferencia"));
        EditText date = input("Fecha AAAA-MM-DD");
        date.setText(forcedDate != null ? forcedDate : (copyFrom == null ? today() : copyFrom.date));
        date.setFocusable(false);
        date.setInputType(0);
        date.setOnClickListener(v -> pickDate(date));
        EditText time = input("Hora HH:MM");
        time.setText(copyFrom == null ? nowTime() : copyFrom.time);
        EditText amount = input("Importe");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (copyFrom != null) amount.setText(String.format(Locale.US, "%.2f", copyFrom.amount));
        boolean includeHiddenAccounts = showHiddenAccounts || copyFrom != null;
        Spinner account = spinner(db.accounts(includeHiddenAccounts));
        Spinner toAccount = spinner(db.accounts(includeHiddenAccounts));
        Spinner category = spinner(db.categories("expense"));
        EditText note = input("Nota");
        EditText description = input("Descripcion");
        if (copyFrom != null) {
            note.setText(copyFrom.note);
            description.setText(copyFrom.description);
        }

        form.addView(label("Tipo"));
        form.addView(type);
        form.addView(date);
        form.addView(smallButton("Elegir fecha en calendario", v -> pickDate(date)), new LinearLayout.LayoutParams(-1, dp(46)));
        form.addView(time);
        form.addView(amount);
        form.addView(label("Cuenta"));
        form.addView(account);
        form.addView(label("Cuenta destino"));
        form.addView(toAccount);
        form.addView(label("Categoria"));
        form.addView(category);
        form.addView(note);
        form.addView(description);

        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean transfer = position == 2;
                toAccount.setEnabled(transfer);
                category.setEnabled(!transfer);
                if (!transfer) {
                    String kind = position == 1 ? "income" : "expense";
                    category.setAdapter(stringAdapter(db.categories(kind)));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (copyFrom != null) {
            int mode = copyFrom.isTransfer() ? 2 : ("income".equals(copyFrom.kind) ? 1 : 0);
            type.setSelection(mode);
            if (mode != 2) {
                category.setAdapter(stringAdapter(db.categories(mode == 1 ? "income" : "expense")));
                setSpinnerSelection(account, copyFrom.account);
                setSpinnerSelection(category, copyFrom.category);
            } else {
                setSpinnerSelection(account, copyFrom.transferFrom.isEmpty() ? copyFrom.account : copyFrom.transferFrom);
                setSpinnerSelection(toAccount, copyFrom.transferTo);
            }
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(6), dp(8), dp(6));
        header.setBackgroundColor(topSurface);
        header.addView(iconButton(R.drawable.ic_action_back, v -> dialog.dismiss()), new LinearLayout.LayoutParams(dp(48), dp(48)));
        String editorTitle = editMode ? "Editar movimiento" : (copyFrom == null ? "Nuevo movimiento" : "Copiar movimiento");
        TextView title = text(editorTitle, 18, true, textColor);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        header.addView(new View(this), new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(64)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(form, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(16), dp(8), dp(16), dp(12));
        Button save = actionWide("Guardar", v -> {
            if (saveMovement(type, date, time, amount, account, toAccount, category, note, description, editMode ? copyFrom : null)) {
                dialog.dismiss();
                renderScreen();
            }
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(52), editMode ? 1 : 3));
        if (!editMode) {
            Button keepGoing = smallButton("Continuar", v -> {
                if (saveMovement(type, date, time, amount, account, toAccount, category, note, description, null)) {
                    amount.setText("");
                    note.setText("");
                    description.setText("");
                    renderScreen();
                }
            });
            LinearLayout.LayoutParams keepParams = new LinearLayout.LayoutParams(0, dp(52), 1);
            keepParams.setMargins(dp(8), 0, 0, 0);
            actions.addView(keepGoing, keepParams);
        }
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(72)));
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(rounded(bg, 0, 0, strokeColor));
            window.setStatusBarColor(topSurface);
            window.setNavigationBarColor(bg);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private boolean saveMovement(Spinner type, EditText date, EditText time, EditText amount, Spinner account, Spinner toAccount, Spinner category, EditText note, EditText description, MoneyDb.Row editSource) {
        double value = parse(amount.getText().toString());
        if (value <= 0) {
            toast("Ingresa un importe valido.");
            return false;
        }
        int selected = type.getSelectedItemPosition();
        String d = date.getText().toString().trim();
        String t = time.getText().toString().trim();
        String n = note.getText().toString();
        String desc = description.getText().toString();
        if (selected == 2) {
            String from = account.getSelectedItem().toString();
            String to = toAccount.getSelectedItem().toString();
            if (from.equals(to)) {
                toast("Elige cuentas distintas.");
                return false;
            }
            if (editSource != null) {
                if (editSource.isTransfer()) {
                    db.updateTransfer(editSource, d, t, from, to, value, n, desc);
                } else {
                    db.deleteMovement(editSource);
                    db.addTransfer(d, t, from, to, value, n, desc);
                }
            } else {
                db.addTransfer(d, t, from, to, value, n, desc);
            }
        } else {
            String kind = selected == 1 ? "income" : "expense";
            if (editSource != null) {
                if (editSource.isTransfer()) {
                    db.deleteMovement(editSource);
                    db.addTransaction(d, t, account.getSelectedItem().toString(), category.getSelectedItem().toString(), kind, value, n, desc);
                } else {
                    db.updateTransaction(editSource.id, d, t, account.getSelectedItem().toString(), category.getSelectedItem().toString(), kind, value, n, desc);
                }
            } else {
                db.addTransaction(d, t, account.getSelectedItem().toString(), category.getSelectedItem().toString(), kind, value, n, desc);
            }
        }
        period = d.length() >= 7 ? d.substring(0, 7) : period;
        return true;
    }

    private void transactionDetail(MoneyDb.Row r) {
        String body;
        if (r.isTransfer()) {
            String route = (r.transferFrom.isEmpty() ? r.account : r.transferFrom) + " → " + (r.transferTo.isEmpty() ? "Cuenta destino" : r.transferTo);
            body = r.date + " " + r.time + "\nTransferencia\n" + route + "\n" + money(r.amount);
        } else {
            body = r.date + " " + r.time + "\n" + r.account + "\n" + r.category + "\n" + money(r.amount);
        }
        if (!r.note.isEmpty()) body += "\n\nNota: " + r.note;
        if (!r.description.isEmpty()) body += "\nDescripcion: " + r.description;
        AlertDialog[] holder = new AlertDialog[1];
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(4));
        box.addView(text(body, 14, false, textColor));
        box.addView(actionWide("Editar", v -> closeThen(holder, () -> editMovementDialog(r))));
        box.addView(actionWide("Copiar", v -> closeThen(holder, () -> copyMovementDialog(r))));
        box.addView(smallButton("Eliminar", v -> closeThen(holder, () -> confirmDeleteMovement(r))), new LinearLayout.LayoutParams(-1, dp(46)));
        holder[0] = new AlertDialog.Builder(this)
                .setTitle("Detalle")
                .setView(box)
                .setPositiveButton("Cerrar", null)
                .create();
        holder[0].show();
        styleDialog(holder[0]);
    }

    private void copyMovementDialog(MoneyDb.Row row) {
        String[] options = {"Copia con fecha de hoy", "Copia con fecha original (" + row.date + ")"};
        int[] selected = {0};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Copiar movimiento")
                .setSingleChoiceItems(options, 0, (d, which) -> selected[0] = which)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Copiar", (d, w) -> movementDialog(row, false, selected[0] == 0 ? today() : row.date))
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void confirmDeleteMovement(MoneyDb.Row row) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Eliminar movimiento")
                .setMessage("¿Seguro que deseas borrar esta información?")
                .setNegativeButton("No", null)
                .setPositiveButton("Sí, eliminar", (d, w) -> {
                    db.deleteMovement(row);
                    renderScreen();
                })
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void accountDialog() {
        accountDialog(null);
    }

    private void accountDialog(MoneyDb.AccountTotal edit) {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        form.setOrientation(LinearLayout.VERTICAL);
        Spinner type = spinner(labels("Efectivo", "Cuentas de Banco"));
        EditText name = input("Nombre");
        EditText balance = input("Dinero inicial");
        balance.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText currency = input("Moneda");
        EditText description = input("Descripcion");
        CheckBox includeTotal = checkbox("Incluir en total", true);
        CheckBox hidden = checkbox("Ocultar esta cuenta", false);
        if (edit == null) {
            currency.setText(currentCurrencyCode());
        } else {
            setSpinnerSelection(type, displayAccountType(edit.type));
            name.setText(edit.name);
            balance.setText(String.format(Locale.US, "%.2f", edit.balance - edit.income + edit.expense));
            currency.setText(edit.currency);
            description.setText(edit.description);
            includeTotal.setChecked(edit.includeTotal);
            hidden.setChecked(edit.hidden);
        }
        form.addView(label("Tipo"));
        form.addView(type);
        form.addView(name);
        form.addView(balance);
        form.addView(currency);
        form.addView(description);
        form.addView(includeTotal);
        form.addView(hidden);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(edit == null ? "Nueva cuenta" : "Editar cuenta")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d, w) -> {
                    if (name.getText().toString().trim().isEmpty()) return;
                    if (edit == null) {
                        db.addAccount(name.getText().toString(), currency.getText().toString(), type.getSelectedItem().toString(), parse(balance.getText().toString()), description.getText().toString(), includeTotal.isChecked(), hidden.isChecked());
                    } else {
                        db.updateAccount(edit.id, edit.name, name.getText().toString(), currency.getText().toString(), type.getSelectedItem().toString(), parse(balance.getText().toString()), description.getText().toString(), includeTotal.isChecked(), hidden.isChecked());
                    }
                    renderScreen();
                });
        if (edit != null) {
            builder.setNeutralButton("Eliminar", (d, w) -> {
                if (!db.deleteAccount(edit.id, edit.name)) {
                    toast("No se puede eliminar una cuenta con movimientos.");
                }
                renderScreen();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        styleDialog(dialog);
    }

    private void categoryDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        form.setOrientation(LinearLayout.VERTICAL);
        EditText name = input("Nombre");
        Spinner kind = spinner(labels("Gasto", "Ingreso"));
        form.addView(name);
        form.addView(label("Tipo"));
        form.addView(kind);
        form.addView(label("Categorias de gasto"));
        for (MoneyDb.CategoryOption c : db.categoryOptions("expense")) form.addView(categoryLine(c));
        form.addView(label("Categorias de ingreso"));
        for (MoneyDb.CategoryOption c : db.categoryOptions("income")) form.addView(categoryLine(c));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Categorias")
                .setView(scroll)
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Agregar", (d, w) -> {
                    if (name.getText().toString().trim().isEmpty()) return;
                    db.addCategory(name.getText().toString(), categoryKind(kind));
                    renderScreen();
                })
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private TextView categoryLine(MoneyDb.CategoryOption category) {
        TextView v = text(category.name + "  ·  " + ("income".equals(category.kind) ? "Ingreso" : "Gasto"), 14, false, textColor);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setBackground(rounded(surface2, 14, 0, strokeColor));
        v.setLayoutParams(margins(-1, -2, 0, 6));
        v.setOnClickListener(view -> categoryEditDialog(category));
        return v;
    }

    private void categoryEditDialog(MoneyDb.CategoryOption category) {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        form.setOrientation(LinearLayout.VERTICAL);
        EditText name = input("Nombre");
        name.setText(category.name);
        Spinner kind = spinner(labels("Gasto", "Ingreso"));
        kind.setSelection("income".equals(category.kind) ? 1 : 0);
        form.addView(name);
        form.addView(label("Tipo"));
        form.addView(kind);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Editar categoria")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d, w) -> {
                    if (name.getText().toString().trim().isEmpty()) return;
                    db.updateCategory(category.id, category.name, name.getText().toString(), categoryKind(kind));
                    renderScreen();
                })
                .setNeutralButton("Eliminar", (d, w) -> {
                    if (!db.deleteCategory(category.id, category.name)) {
                        toast("No se puede eliminar una categoria con movimientos.");
                    }
                    renderScreen();
                })
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void settingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(8), dp(14), dp(8));
        box.addView(setting("Ajustes de moneda principal", currentCurrencyCode(), v -> currencyDialog()));
        box.addView(setting("Pantalla de inicio", titleForScreen(), v -> toast("Usa la barra inferior para cambiar.")));
        box.addView(setting("Dia de inicio del mes", "Cada 1", v -> toast("Pendiente de personalizacion.")));
        box.addView(setting("Dia de inicio de la semana", "Domingo", v -> toast("Pendiente de personalizacion.")));
        box.addView(setting("Ajustes del balance", "Calculado por cuenta", v -> screenTo("accounts")));
        box.addView(setting("Entrada de tiempo", "Fecha y hora en movimiento", v -> movementDialog(null)));
        box.addView(setting("Mostrar descripcion", "Activar", v -> toast("La descripcion ya se guarda.")));
        box.addView(setting("Respaldo local", "Importar/exportar MMBAK, CSV, JSON o XLSX", v -> openImport()));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Configuracion").setView(scroll).setPositiveButton("Cerrar", null).create();
        dialog.show();
        styleDialog(dialog);
    }

    private TextView setting(String name, String value, View.OnClickListener listener) {
        TextView v = text(name + "\n" + value, 14, false, textColor);
        v.setPadding(0, dp(10), 0, dp(10));
        v.setOnClickListener(listener);
        return v;
    }

    private void currencyDialog() {
        String[] labels = new String[]{
                "Peru - Sol peruano (S/)",
                "Estados Unidos - Dolar ($)",
                "Union Europea - Euro (EUR)",
                "Mexico - Peso mexicano (MX$)",
                "Colombia - Peso colombiano (COL$)",
                "Chile - Peso chileno (CLP$)",
                "Argentina - Peso argentino (AR$)",
                "Brasil - Real (R$)"
        };
        String[] codes = new String[]{"PEN", "USD", "EUR", "MXN", "COP", "CLP", "ARS", "BRL"};
        int checked = 0;
        String current = currentCurrencyCode();
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(current)) checked = i;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Moneda por pais")
                .setSingleChoiceItems(labels, checked, (dlg, which) -> {
                    prefs.edit().putString("currency_code", codes[which]).apply();
                    dlg.dismiss();
                    renderScreen();
                    toast("Moneda: " + labels[which]);
                })
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void supabaseDialog() {
        String url = prefs.getString("supabase_url", "").trim();
        String key = prefs.getString("supabase_key", "").trim();
        if (url.isEmpty() || key.isEmpty()) {
            supabaseConfigurationDialog();
        } else {
            supabaseAccountDialog();
        }
    }

    private void supabaseConfigurationDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        TextView status = text("Supabase no configurado\nGuarda primero la conexión del proyecto.", 13, true, transferColor);
        status.setBackground(rounded(surface2, 10, 0, strokeColor));
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        form.addView(status, margins(-1, -2, 0, 12));
        EditText url = input("https://tu-proyecto.supabase.co");
        url.setText(prefs.getString("supabase_url", ""));
        EditText key = input("Clave publica de Supabase");
        key.setText(prefs.getString("supabase_key", ""));
        form.addView(url);
        form.addView(key);
        form.addView(text("La clave debe ser la publica (publishable o anon). Nunca uses service_role.", 12, false, muted));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Configurar Supabase")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar conexión", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String projectUrl = url.getText().toString().trim();
                String publicKey = key.getText().toString().trim();
                if (!projectUrl.startsWith("https://") || publicKey.isEmpty()) {
                    toast("Ingresa una URL https y la clave publica.");
                    return;
                }
                prefs.edit()
                        .putString("supabase_url", projectUrl)
                        .putString("supabase_key", publicKey)
                        .apply();
                dialog.dismiss();
                toast("Conexión de Supabase guardada.");
                supabaseAccountDialog();
            });
        });
        dialog.show();
        styleDialog(dialog);
    }

    private void supabaseAccountDialog() {
        String projectUrl = prefs.getString("supabase_url", "").trim();
        String publicKey = prefs.getString("supabase_key", "").trim();
        if (projectUrl.isEmpty() || publicKey.isEmpty()) {
            supabaseConfigurationDialog();
            return;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        TextView status = text("Sin cuenta conectada\nConfiguración de Supabase guardada.", 13, true, transferColor);
        status.setBackground(rounded(surface2, 10, 0, strokeColor));
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        form.addView(status, margins(-1, -2, 0, 12));

        boolean[] createMode = {false};
        LinearLayout tabs = new LinearLayout(this);
        Button loginTab = smallButton("Iniciar sesión", null);
        Button createTab = smallButton("Crear cuenta", null);
        tabs.addView(loginTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        createParams.setMargins(dp(6), 0, 0, 0);
        tabs.addView(createTab, createParams);
        form.addView(tabs, margins(-1, dp(42), 0, 10));

        EditText email = input("Correo");
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setText(prefs.getString("supabase_email", ""));
        EditText password = input("Contrasena");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(email);
        form.addView(password);

        Button accountAction = actionWide("Entrar y sincronizar", null);
        form.addView(accountAction);
        Button changeConnection = smallButton("Cambiar conexión de Supabase", null);
        form.addView(changeConnection, new LinearLayout.LayoutParams(-1, dp(44)));

        Runnable refreshMode = () -> {
            loginTab.setTextColor(createMode[0] ? muted : Color.WHITE);
            createTab.setTextColor(createMode[0] ? Color.WHITE : muted);
            loginTab.setBackground(rounded(createMode[0] ? surface2 : actionColor, 12, 0, strokeColor));
            createTab.setBackground(rounded(createMode[0] ? actionColor : surface2, 12, 0, strokeColor));
            accountAction.setText(createMode[0] ? "Crear cuenta" : "Entrar y sincronizar");
        };
        loginTab.setOnClickListener(v -> {
            createMode[0] = false;
            refreshMode.run();
        });
        createTab.setOnClickListener(v -> {
            createMode[0] = true;
            refreshMode.run();
        });
        refreshMode.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Cuenta y sincronización")
                .setView(form)
                .setNegativeButton("Cerrar", null)
                .create();
        accountAction.setOnClickListener(v -> {
            String userEmail = email.getText().toString().trim();
            String userPassword = password.getText().toString();
            if (userEmail.isEmpty() || userPassword.isEmpty()) {
                toast("Completa el correo y la contraseña.");
                return;
            }
            prefs.edit().putString("supabase_email", userEmail).apply();
            dialog.dismiss();
            verifySupabaseAccount(projectUrl, publicKey, userEmail, userPassword, createMode[0]);
        });
        changeConnection.setOnClickListener(v -> {
            dialog.dismiss();
            supabaseConfigurationDialog();
        });
        dialog.show();
        styleDialog(dialog);
    }

    private void verifySupabaseAccount(String url, String key, String email, String password, boolean createAccount) {
        toast(createAccount ? "Creando cuenta..." : "Comprobando cuenta...");
        new Thread(() -> {
            try {
                SupabaseSync.Session session = createAccount
                        ? SupabaseSync.signUp(url, key, email, password)
                        : SupabaseSync.signIn(url, key, email, password);
                runOnUiThread(() -> {
                    if (createAccount && session.accessToken.isEmpty()) {
                        toast("Cuenta creada. Revisa tu correo para confirmarla.");
                        supabaseAccountDialog();
                    } else {
                        toast(createAccount ? "Cuenta creada y conectada." : "Cuenta conectada.");
                        supabaseActionsDialog(url, key, email, password);
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    toast("No se pudo conectar: " + ex.getMessage());
                    supabaseAccountDialog();
                });
            }
        }).start();
    }

    private void supabaseActionsDialog(String url, String key, String email, String password) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        TextView status = text("Cuenta conectada\n" + email, 13, true, incomeColor);
        status.setBackground(rounded(surface2, 10, 0, strokeColor));
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        form.addView(status, margins(-1, -2, 0, 12));
        AlertDialog[] holder = new AlertDialog[1];
        form.addView(actionWide("Subir copia local", v -> {
            holder[0].dismiss();
            runSupabaseSync(true, url, key, email, password);
        }));
        form.addView(smallButton("Descargar copia de la nube", v -> {
            holder[0].dismiss();
            confirmSupabaseDownload(url, key, email, password);
        }), new LinearLayout.LayoutParams(-1, dp(48)));
        form.addView(smallButton("Cambiar conexión de Supabase", v -> {
            holder[0].dismiss();
            supabaseConfigurationDialog();
        }), new LinearLayout.LayoutParams(-1, dp(44)));
        holder[0] = new AlertDialog.Builder(this)
                .setTitle("Sincronización")
                .setView(form)
                .setPositiveButton("Cerrar", null)
                .create();
        holder[0].show();
        styleDialog(holder[0]);
    }

    private void confirmSupabaseDownload(String url, String key, String email, String password) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Reemplazar datos locales")
                .setMessage("La copia de Supabase reemplazara las cuentas, categorias y transacciones guardadas en este dispositivo.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Descargar", (d, w) -> runSupabaseSync(false, url, key, email, password))
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void runSupabaseSync(boolean upload, String url, String key, String email, String password) {
        toast(upload ? "Subiendo datos..." : "Descargando datos...");
        new Thread(() -> {
            try {
                SupabaseSync.Session session = SupabaseSync.signIn(url.trim(), key.trim(), email.trim(), password);
                if (upload) {
                    SupabaseSync.upload(url.trim(), key.trim(), session, cloudSnapshotJson());
                } else {
                    SupabaseSync.RemoteSnapshot remote = SupabaseSync.download(url.trim(), key.trim(), session);
                    replaceFromCloudSnapshot(remote.data);
                }
                runOnUiThread(() -> {
                    prefs.edit().putLong("last_supabase_sync", System.currentTimeMillis()).apply();
                    period = db.latestMonth();
                    statsAnchorDate = db.latestDate();
                    toast(upload ? "Copia subida a Supabase." : "Datos descargados desde Supabase.");
                    draw();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> toast("No se pudo sincronizar: " + ex.getMessage()));
            }
        }).start();
    }

    private void monthDialog() {
        Calendar cal = calendarFrom(period + "-01");
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            period = String.format(Locale.US, "%04d-%02d", year, month + 1);
            draw();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.show();
        styleDialog(dialog);
    }

    private void shiftMonth(int delta) {
        Calendar cal = Calendar.getInstance();
        try {
            String[] parts = period.split("-");
            cal.set(Calendar.YEAR, Integer.parseInt(parts[0]));
            cal.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
            cal.set(Calendar.DAY_OF_MONTH, 1);
        } catch (Exception ignored) {
        }
        cal.add(Calendar.MONTH, delta);
        period = new SimpleDateFormat("yyyy-MM", Locale.US).format(cal.getTime());
        draw();
    }

    private void statsScopeDialog() {
        String[] labels = {"Anual", "Mensual", "Semanal", "Diario", "Todo"};
        String[] values = {"anual", "mensual", "semanal", "diario", "todo"};
        int checked = 1;
        for (int i = 0; i < values.length; i++) if (values[i].equals(statsScope)) checked = i;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Filtrar estado")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    statsScope = values[which];
                    statsDetailCategory = null;
                    d.dismiss();
                    renderScreen();
                })
                .setNeutralButton("Elegir fecha", (d, w) -> statsDateDialog())
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void statsDateDialog() {
        Calendar cal = calendarFrom(statsAnchorDate == null ? db.latestDate() : statsAnchorDate);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            statsAnchorDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            period = statsAnchorDate.substring(0, 7);
            statsDetailCategory = null;
            renderScreen();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.show();
        styleDialog(dialog);
    }

    private void shiftStatsPeriod(int direction) {
        if ("todo".equals(statsScope)) return;
        Calendar cal = calendarFrom(statsAnchorDate == null ? db.latestDate() : statsAnchorDate);
        if ("diario".equals(statsScope)) cal.add(Calendar.DATE, direction);
        else if ("semanal".equals(statsScope)) cal.add(Calendar.DATE, direction * 7);
        else if ("anual".equals(statsScope)) cal.add(Calendar.YEAR, direction);
        else cal.add(Calendar.MONTH, direction);
        statsAnchorDate = isoDate(cal);
        period = statsAnchorDate.substring(0, 7);
        statsDetailCategory = null;
        renderScreen();
    }

    private DateRange statsRange() {
        return rangeForScope(statsScope, statsAnchorDate == null ? db.latestDate() : statsAnchorDate);
    }

    private DateRange rangeForScope(String scope, String anchorDate) {
        if ("todo".equals(scope)) return new DateRange(null, null, "Todo");
        Calendar anchor = calendarFrom(anchorDate == null ? db.latestDate() : anchorDate);
        Calendar start = (Calendar) anchor.clone();
        Calendar end = (Calendar) anchor.clone();
        String label;
        if ("anual".equals(scope)) {
            start.set(Calendar.MONTH, Calendar.JANUARY);
            start.set(Calendar.DAY_OF_MONTH, 1);
            end.set(Calendar.MONTH, Calendar.DECEMBER);
            end.set(Calendar.DAY_OF_MONTH, 31);
            label = String.valueOf(anchor.get(Calendar.YEAR));
        } else if ("semanal".equals(scope)) {
            int day = start.get(Calendar.DAY_OF_WEEK);
            int backToMonday = day == Calendar.SUNDAY ? 6 : day - Calendar.MONDAY;
            start.add(Calendar.DATE, -backToMonday);
            end = (Calendar) start.clone();
            end.add(Calendar.DATE, 6);
            label = shortDate(start) + " - " + shortDate(end);
        } else if ("diario".equals(scope)) {
            label = longDate(anchor);
        } else {
            start.set(Calendar.DAY_OF_MONTH, 1);
            end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
            label = monthLabel(isoDate(anchor).substring(0, 7));
        }
        return new DateRange(isoDate(start), isoDate(end), label);
    }

    private String scopeLabel(String scope) {
        if ("anual".equals(scope)) return "Anual";
        if ("semanal".equals(scope)) return "Semanal";
        if ("diario".equals(scope)) return "Diario";
        if ("todo".equals(scope)) return "Todo";
        return "Mensual";
    }

    private String isoDate(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private String shortDate(Calendar calendar) {
        return new SimpleDateFormat("dd MMM", new Locale("es", "PE")).format(calendar.getTime());
    }

    private String longDate(Calendar calendar) {
        return new SimpleDateFormat("dd MMM yyyy", new Locale("es", "PE")).format(calendar.getTime());
    }

    private void animateChart(View chart) {
        chart.setAlpha(0f);
        chart.setScaleX(0.94f);
        chart.setScaleY(0.94f);
        chart.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void openImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, IMPORT_BACKUP);
    }

    private void openExport() {
        String[] formats = new String[]{"MMBAK", "CSV", "JSON", "XLSX"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Exportar datos")
                .setItems(formats, (d, which) -> createExport(formats[which].toLowerCase(Locale.US)))
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void reportDialog() {
        String[] labels = {"Semanal", "Mensual", "Anual", "Todo"};
        String[] values = {"semanal", "mensual", "anual", "todo"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Periodo del reporte")
                .setItems(labels, (d, which) -> {
                    String scope = values[which];
                    if ("todo".equals(scope)) {
                        prepareReport(scope, db.latestDate());
                    } else {
                        reportDateDialog(scope);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void reportDateDialog(String scope) {
        Calendar cal = calendarFrom(statsAnchorDate == null ? db.latestDate() : statsAnchorDate);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String anchor = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            prepareReport(scope, anchor);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.setTitle("Fecha del reporte");
        dialog.show();
        styleDialog(dialog);
    }

    private void prepareReport(String scope, String anchor) {
        DateRange range = rangeForScope(scope, anchor);
        pendingReportStart = range.start;
        pendingReportEnd = range.end;
        pendingReportTitle = "Reporte " + scopeLabel(scope) + " - " + range.label;
        String[] formats = {"XLSX moderno", "XLS compatible"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(pendingReportTitle)
                .setItems(formats, (d, which) -> createReportExport(which == 0 ? "xlsx" : "xls"))
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void createReportExport(String format) {
        pendingExportFormat = format;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(exportMime(format));
        String scope = pendingReportTitle.toLowerCase(Locale.US).replace(" ", "_").replace("-", "");
        intent.putExtra(Intent.EXTRA_TITLE, "moneymate_" + scope + "." + format);
        startActivityForResult(intent, EXPORT_REPORT);
    }

    private void createExport(String format) {
        pendingExportFormat = format;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(exportMime(format));
        intent.putExtra(Intent.EXTRA_TITLE, "moneymate_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + "." + format);
        startActivityForResult(intent, EXPORT_BACKUP);
    }

    private void importBackup(Uri uri) {
        final String fileName = displayName(uri);
        importInProgress = true;
        if (content != null) renderScreen();
        toast("Cargando datos...");
        new Thread(() -> {
            try {
                String name = fileName.toLowerCase(Locale.US);
                ImportResult result;
                if (name.endsWith(".csv")) result = importCsv(uri);
                else if (name.endsWith(".json")) result = importJson(uri);
                else if (name.endsWith(".xlsx")) result = SpreadsheetExchange.importXlsx(this, uri, db, currentCurrencyCode());
                else {
                    MmbakImporter importer = new MmbakImporter(this);
                    result = importer.importInto(uri, db);
                }
                runOnUiThread(() -> {
                    if (!prefs.contains("currency_code")) prefs.edit().putString("currency_code", db.primaryCurrency()).apply();
                    prefs.edit().putString("last_import_name", fileName).putInt("last_import_count", result.transactions).apply();
                    period = db.latestMonth();
                    statsAnchorDate = db.latestDate();
                    importInProgress = false;
                    toast("Importado: " + result.transactions + " movimientos");
                    if (content != null) draw();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    importInProgress = false;
                    toast("No se pudo importar: " + ex.getMessage());
                    if (content != null) renderScreen();
                });
            }
        }).start();
    }

    private void exportBackup(Uri uri) {
        try {
            if ("csv".equals(pendingExportFormat)) {
                exportCsv(uri);
            } else if ("json".equals(pendingExportFormat)) {
                exportJson(uri);
            } else if ("xlsx".equals(pendingExportFormat)) {
                SpreadsheetExchange.exportXlsx(this, uri, db, currentCurrencyCode());
            } else {
                File temp = File.createTempFile("moneymate-", ".mmbak", getCacheDir());
                db.copyDatabaseTo(temp, this);
                try (FileInputStream in = new FileInputStream(temp); OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalArgumentException("No se pudo guardar el archivo.");
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
                temp.delete();
            }
            toast("Datos exportados en " + pendingExportFormat.toUpperCase(Locale.US));
        } catch (Exception ex) {
            toast("No se pudo exportar: " + ex.getMessage());
        }
    }

    private void exportReport(Uri uri) {
        try {
            List<MoneyDb.Row> rows = db.transactionsForDisplay(pendingReportStart, pendingReportEnd);
            if ("xls".equals(pendingExportFormat)) {
                SpreadsheetExchange.exportXls(this, uri, rows, currentCurrencyCode(), pendingReportTitle);
            } else {
                SpreadsheetExchange.exportXlsx(this, uri, rows, currentCurrencyCode(), pendingReportTitle);
            }
            toast("Reporte generado: " + rows.size() + " transacciones");
        } catch (Exception ex) {
            toast("No se pudo generar el reporte: " + ex.getMessage());
        }
    }

    private String exportMime(String format) {
        if ("csv".equals(format)) return "text/csv";
        if ("json".equals(format)) return "application/json";
        if ("xls".equals(format)) return "application/vnd.ms-excel";
        if ("xlsx".equals(format)) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }

    private ImportResult importCsv(Uri uri) throws Exception {
        List<ImportedAccount> accounts = new ArrayList<>();
        List<ImportedCategory> categories = new ArrayList<>();
        List<ImportedTxn> txns = new ArrayList<>();
        try (InputStream in = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String header = reader.readLine();
            if (header == null) throw new IllegalArgumentException("CSV vacio.");
            List<String> headers = parseCsvLine(header);
            Map<String, Integer> index = csvIndex(headers);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> cols = parseCsvLine(line);
                String rawDate = csvValue(cols, index, "date", "fecha", "datetime");
                String date = SpreadsheetExchange.normalizeDateForImport(rawDate);
                String time = csvValue(cols, index, "time", "hora");
                if (time.trim().isEmpty()) time = SpreadsheetExchange.normalizeTimeForImport(rawDate);
                String account = csvValue(cols, index, "account", "cuenta");
                String category = csvValue(cols, index, "category", "categoria", "categoría");
                String rawKind = csvValue(cols, index, "kind", "tipo", "ingreso/gasto", "ingresogasto");
                double amount = SpreadsheetExchange.parseAmount(csvValue(cols, index, "amount", "importe", "monto", currentCurrencyCode().toLowerCase(Locale.US)));
                String note = csvValue(cols, index, "note", "nota");
                String description = csvValue(cols, index, "description", "descripcion", "descripción");
                if (date.trim().isEmpty() || amount <= 0) continue;
                if (SpreadsheetExchange.isTransferKind(rawKind)) {
                    txns.add(new ImportedTxn(date, time, account, "Transferencia", "expense", amount, note, description));
                    if (!category.trim().isEmpty() && !category.equals(account)) {
                        txns.add(new ImportedTxn(date, time, category, "Transferencia", "income", amount, note, description));
                    }
                } else {
                    txns.add(new ImportedTxn(date, time, account, category, SpreadsheetExchange.normalizeKind(rawKind), amount, note, description));
                }
            }
        }
        completeImportLists(accounts, categories, txns);
        db.replaceFromImport(accounts, categories, txns);
        return new ImportResult(accounts.size(), categories.size(), txns.size());
    }

    private void exportCsv(Uri uri) throws Exception {
        try (OutputStream out = getContentResolver().openOutputStream(uri);
             Writer writer = new OutputStreamWriter(out)) {
            writer.write("date,time,account,category,kind,amount,note,description\n");
            for (MoneyDb.Row r : db.allTransactions()) {
                writer.write(csvCell(r.date) + "," + csvCell(r.time) + "," + csvCell(r.account) + "," +
                        csvCell(r.category) + "," + csvCell(r.kind) + "," + csvCell(String.format(Locale.US, "%.2f", r.amount)) + "," +
                        csvCell(r.note) + "," + csvCell(r.description) + "\n");
            }
        }
    }

    private ImportResult importJson(Uri uri) throws Exception {
        Object raw = new org.json.JSONTokener(readText(uri)).nextValue();
        List<ImportedAccount> accounts = new ArrayList<>();
        List<ImportedCategory> categories = new ArrayList<>();
        List<ImportedTxn> txns = new ArrayList<>();
        JSONArray txnArray;
        if (raw instanceof JSONArray) {
            txnArray = (JSONArray) raw;
        } else {
            JSONObject root = (JSONObject) raw;
            JSONArray accountArray = root.optJSONArray("accounts");
            if (accountArray != null) {
                for (int i = 0; i < accountArray.length(); i++) {
                    JSONObject a = accountArray.getJSONObject(i);
                    accounts.add(new ImportedAccount(a.optString("name", a.optString("nombre", "Cuenta")),
                            a.optDouble("balance", a.optDouble("saldo", 0)),
                            a.optString("currency", a.optString("moneda", currentCurrencyCode())),
                            a.optString("type", a.optString("tipo", "")),
                            a.optString("description", a.optString("descripcion", "")),
                            a.optBoolean("includeTotal", true),
                            a.optBoolean("hidden", false)));
                }
            }
            JSONArray categoryArray = root.optJSONArray("categories");
            if (categoryArray != null) {
                for (int i = 0; i < categoryArray.length(); i++) {
                    JSONObject c = categoryArray.getJSONObject(i);
                    categories.add(new ImportedCategory(c.optString("name", c.optString("nombre", "Sin categoria")),
                            normalizeKind(c.optString("kind", c.optString("tipo", "expense"))),
                            c.optString("color", "")));
                }
            }
            txnArray = root.optJSONArray("transactions");
            if (txnArray == null) txnArray = root.optJSONArray("movements");
            if (txnArray == null) txnArray = new JSONArray();
        }
        for (int i = 0; i < txnArray.length(); i++) {
            JSONObject t = txnArray.getJSONObject(i);
            txns.add(new ImportedTxn(t.optString("date", t.optString("fecha", today())),
                    t.optString("time", t.optString("hora", "00:00")),
                    t.optString("account", t.optString("cuenta", "Efectivo")),
                    t.optString("category", t.optString("categoria", "Sin categoria")),
                    normalizeKind(t.optString("kind", t.optString("tipo", "expense"))),
                    t.optDouble("amount", t.optDouble("importe", t.optDouble("monto", 0))),
                    t.optString("note", t.optString("nota", "")),
                    t.optString("description", t.optString("descripcion", ""))));
        }
        completeImportLists(accounts, categories, txns);
        db.replaceFromImport(accounts, categories, txns);
        return new ImportResult(accounts.size(), categories.size(), txns.size());
    }

    private void exportJson(Uri uri) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("currency", currentCurrencyCode());
        JSONArray accounts = new JSONArray();
        for (MoneyDb.AccountTotal a : db.accountTotals()) {
            JSONObject item = new JSONObject();
            item.put("name", a.name);
            item.put("currency", a.currency);
            item.put("type", a.type);
            item.put("balance", a.balance - a.income + a.expense);
            item.put("description", a.description);
            item.put("includeTotal", a.includeTotal);
            item.put("hidden", a.hidden);
            accounts.put(item);
        }
        root.put("accounts", accounts);
        JSONArray categories = new JSONArray();
        for (MoneyDb.CategoryOption c : db.categoryOptions("expense")) categories.put(categoryJson(c));
        for (MoneyDb.CategoryOption c : db.categoryOptions("income")) categories.put(categoryJson(c));
        root.put("categories", categories);
        JSONArray txns = new JSONArray();
        for (MoneyDb.Row r : db.allTransactions()) {
            JSONObject item = new JSONObject();
            item.put("date", r.date);
            item.put("time", r.time);
            item.put("account", r.account);
            item.put("category", r.category);
            item.put("kind", r.kind);
            item.put("amount", r.amount);
            item.put("note", r.note);
            item.put("description", r.description);
            txns.put(item);
        }
        root.put("transactions", txns);
        try (OutputStream out = getContentResolver().openOutputStream(uri);
             Writer writer = new OutputStreamWriter(out)) {
            writer.write(root.toString(2));
        }
    }

    private JSONObject cloudSnapshotJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 2);
        root.put("currency", currentCurrencyCode());
        JSONArray accounts = new JSONArray();
        for (MoneyDb.AccountTotal account : db.accountTotals()) {
            JSONObject item = new JSONObject();
            item.put("name", account.name);
            item.put("currency", account.currency);
            item.put("type", account.type);
            item.put("balance", account.balance - account.income + account.expense);
            item.put("description", account.description);
            item.put("includeTotal", account.includeTotal);
            item.put("hidden", account.hidden);
            accounts.put(item);
        }
        root.put("accounts", accounts);
        JSONArray categories = new JSONArray();
        for (MoneyDb.CategoryOption category : db.categoryOptions("expense")) categories.put(categoryJson(category));
        for (MoneyDb.CategoryOption category : db.categoryOptions("income")) categories.put(categoryJson(category));
        root.put("categories", categories);
        JSONArray transactions = new JSONArray();
        for (MoneyDb.Row row : db.transactionsForDisplay(null, null)) {
            JSONObject item = new JSONObject();
            item.put("date", row.date);
            item.put("time", row.time);
            item.put("kind", row.isTransfer() ? "transfer" : row.kind);
            item.put("account", row.isTransfer() ? row.transferFrom : row.account);
            item.put("toAccount", row.isTransfer() ? row.transferTo : "");
            item.put("category", row.isTransfer() ? "Transferencia" : row.category);
            item.put("amount", row.amount);
            item.put("note", row.note);
            item.put("description", row.description);
            transactions.put(item);
        }
        root.put("transactions", transactions);
        return root;
    }

    private ImportResult replaceFromCloudSnapshot(JSONObject root) throws Exception {
        List<ImportedAccount> accounts = new ArrayList<>();
        List<ImportedCategory> categories = new ArrayList<>();
        List<ImportedTxn> txns = new ArrayList<>();
        String currency = root.optString("currency", currentCurrencyCode());
        JSONArray accountArray = root.optJSONArray("accounts");
        if (accountArray != null) {
            for (int i = 0; i < accountArray.length(); i++) {
                JSONObject account = accountArray.getJSONObject(i);
                accounts.add(new ImportedAccount(
                        account.optString("name", "Cuenta"),
                        account.optDouble("balance", 0),
                        account.optString("currency", currency),
                        account.optString("type", "Cuentas de Banco"),
                        account.optString("description", ""),
                        account.optBoolean("includeTotal", true),
                        account.optBoolean("hidden", false)
                ));
            }
        }
        JSONArray categoryArray = root.optJSONArray("categories");
        if (categoryArray != null) {
            for (int i = 0; i < categoryArray.length(); i++) {
                JSONObject category = categoryArray.getJSONObject(i);
                categories.add(new ImportedCategory(
                        category.optString("name", "Sin categoria"),
                        normalizeKind(category.optString("kind", "expense")),
                        category.optString("color", "")
                ));
            }
        }
        JSONArray transactionArray = root.optJSONArray("transactions");
        if (transactionArray != null) {
            for (int i = 0; i < transactionArray.length(); i++) {
                JSONObject transaction = transactionArray.getJSONObject(i);
                String kind = transaction.optString("kind", "expense");
                String date = SpreadsheetExchange.normalizeDateForImport(transaction.optString("date", today()));
                String time = transaction.optString("time", "00:00");
                String account = transaction.optString("account", "Cuenta");
                double amount = transaction.optDouble("amount", 0);
                String note = transaction.optString("note", "");
                String description = transaction.optString("description", "");
                if ("transfer".equalsIgnoreCase(kind) || SpreadsheetExchange.isTransferKind(kind)) {
                    String toAccount = transaction.optString("toAccount", "");
                    if (toAccount.isEmpty() || account.equals(toAccount)) continue;
                    txns.add(new ImportedTxn(date, time, account, "Transferencia", "expense", amount, note, description));
                    txns.add(new ImportedTxn(date, time, toAccount, "Transferencia", "income", amount, note, description));
                } else {
                    txns.add(new ImportedTxn(
                            date,
                            time,
                            account,
                            transaction.optString("category", "Sin categoria"),
                            normalizeKind(kind),
                            amount,
                            note,
                            description
                    ));
                }
            }
        }
        completeImportLists(accounts, categories, txns);
        db.replaceFromImport(accounts, categories, txns);
        return new ImportResult(accounts.size(), categories.size(), txns.size());
    }

    private JSONObject categoryJson(MoneyDb.CategoryOption c) throws Exception {
        JSONObject item = new JSONObject();
        item.put("name", c.name);
        item.put("kind", c.kind);
        return item;
    }

    private void completeImportLists(List<ImportedAccount> accounts, List<ImportedCategory> categories, List<ImportedTxn> txns) {
        Map<String, Boolean> accountNames = new LinkedHashMap<>();
        for (ImportedAccount a : accounts) accountNames.put(a.name, true);
        for (ImportedTxn t : txns) {
            if (!accountNames.containsKey(t.account)) {
                accounts.add(new ImportedAccount(t.account, 0, currentCurrencyCode()));
                accountNames.put(t.account, true);
            }
        }
        Map<String, Boolean> categoryNames = new LinkedHashMap<>();
        for (ImportedCategory c : categories) categoryNames.put(c.kind + "|" + c.name, true);
        for (ImportedTxn t : txns) {
            String key = t.kind + "|" + t.category;
            if (!categoryNames.containsKey(key)) {
                categories.add(new ImportedCategory(t.category, t.kind, ""));
                categoryNames.put(key, true);
            }
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return c.getString(index);
            }
        } catch (Exception ignored) {
        }
        String path = uri.getLastPathSegment();
        return path == null || path.trim().isEmpty() ? "archivo" : path;
    }

    private String readText(Uri uri) throws Exception {
        StringBuilder out = new StringBuilder();
        try (InputStream in = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        return out.toString();
    }

    private Map<String, Integer> csvIndex(List<String> headers) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String raw = headers.get(i).trim().toLowerCase(Locale.US);
            if (!index.containsKey(raw)) index.put(raw, i);
            String normalized = SpreadsheetExchange.key(headers.get(i));
            if (!normalized.isEmpty() && !index.containsKey(normalized)) index.put(normalized, i);
        }
        return index;
    }

    private String csvValue(List<String> cols, Map<String, Integer> index, String... keys) {
        for (String key : keys) {
            Integer i = index.get(key);
            if (i != null && i < cols.size()) return cols.get(i);
        }
        return "";
    }

    private String normalizeKind(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (lower.contains("ingreso") || lower.contains("income") || lower.contains("deposit")) return "income";
        return "expense";
    }

    private String categoryKind(Spinner spinner) {
        return spinner.getSelectedItemPosition() == 1 ? "income" : "expense";
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                out.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        out.add(cell.toString());
        return out;
    }

    private void shareApp() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, "MoneyMate Modern: finanzas personales sin anuncios.");
        startActivity(Intent.createChooser(send, "Compartir"));
    }

    private View transactionRow(MoneyDb.Row r) {
        boolean transfer = r.isTransfer();
        boolean income = "income".equals(r.kind);
        int amountColor = transfer ? transferColor : (income ? incomeColor : expenseColor);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(rounded(surface, 8, 0, strokeColor));
        row.setPadding(dp(10), dp(9), dp(10), dp(9));
        row.setLayoutParams(margins(-1, -2, 0, 2));

        TextView left = text("", 13, false, textColor);
        String primary = transfer ? "Transferencia" : r.category;
        String secondary = transfer
                ? r.time + " · " + (r.transferFrom.isEmpty() ? r.account : r.transferFrom) + " → " + (r.transferTo.isEmpty() ? "Cuenta destino" : r.transferTo)
                : r.time + " · " + r.account;
        SpannableString meta = highlightedMeta(primary, secondary, noteSuffixPlain(r));
        if (transfer) meta.setSpan(new ForegroundColorSpan(transferColor), 0, primary.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        left.setText(meta);
        left.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        TextView amount = text(money(r.amount), 13, true, amountColor);
        amount.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        amount.setBackground(rounded(transfer ? Color.TRANSPARENT : (income ? softIncome() : softExpense()), 8, 0, strokeColor));
        amount.setPadding(dp(8), dp(5), dp(8), dp(5));
        row.addView(amount, new LinearLayout.LayoutParams(dp(124), -2));
        row.setOnClickListener(view -> transactionDetail(r));
        return row;
    }

    private String noteSuffix(MoneyDb.Row r) {
        if (!r.note.isEmpty()) return "\n" + r.note;
        if (!r.description.isEmpty()) return "\n" + r.description;
        return "";
    }

    private String noteSuffixPlain(MoneyDb.Row r) {
        if (!r.note.isEmpty()) return r.note;
        if (!r.description.isEmpty()) return r.description;
        return "";
    }

    private View accountRow(MoneyDb.AccountTotal a) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(rounded(a.hidden ? surface2 : surface, 10, 0, strokeColor));
        row.setPadding(dp(10), dp(9), dp(10), dp(9));
        row.setLayoutParams(margins(-1, -2, 0, 1));

        TextView name = text(a.name + (a.hidden ? "\nOculta" : "\n" + displayAccountType(a.type)), 13, false, a.hidden ? muted : textColor);
        name.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(42), 1));

        TextView balance = text(money(Math.abs(a.balance)), 13, true, a.balance >= 0 ? incomeColor : expenseColor);
        balance.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(balance, new LinearLayout.LayoutParams(dp(132), dp(42)));
        row.setOnClickListener(view -> {
            activeAccountId = a.id;
            accountMode = "diario";
            renderScreen();
        });
        return row;
    }

    private LinearLayout panel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded(surface, 18, 0, strokeColor));
        box.setElevation(0);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setLayoutParams(margins(-1, -2, 0, 8));
        return box;
    }

    private LinearLayout compactPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded(surface, 14, 0, strokeColor));
        box.setPadding(dp(8), dp(10), dp(8), dp(10));
        box.setLayoutParams(margins(-1, -2, 0, 8));
        return box;
    }

    private TextView kpi(String label, String value, int color) {
        TextView v = text("", 13, false, color);
        String full = label + "\n" + value;
        SpannableString span = new SpannableString(full);
        span.setSpan(new ForegroundColorSpan(muted), 0, label.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(color), label.length() + 1, full.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(Typeface.BOLD), label.length() + 1, full.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        v.setText(span);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private TextView simpleLine(String title, String subtitle) {
        TextView v = text(title + "\n" + subtitle, 14, false, textColor);
        v.setBackground(rounded(surface, 12, 0, strokeColor));
        v.setElevation(0);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setLayoutParams(margins(-1, -2, 0, 6));
        return v;
    }

    private TextView empty(String value) {
        TextView v = text(value, 14, false, muted);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, dp(28), 0, dp(28));
        return v;
    }

    private TextView section(String value) {
        TextView v = text(value, 16, true, textColor);
        v.setPadding(0, dp(14), 0, dp(8));
        return v;
    }

    private Button modeButton(String label, String target) {
        Button b = smallButton(label, v -> {
            transactionMode = target;
            renderScreen();
        });
        b.setTextColor(transactionMode.equals(target) ? Color.WHITE : muted);
        b.setTextSize(11);
        b.setBackground(rounded(transactionMode.equals(target) ? actionColor : surface, 14, 0, strokeColor));
        return b;
    }

    private Button searchModeButton() {
        Button button = smallButton("Buscar", v -> searchDialog());
        boolean selected = "buscar".equals(transactionMode);
        Drawable icon = getResources().getDrawable(R.drawable.ic_action_search).mutate();
        icon.setTint(selected ? Color.WHITE : muted);
        icon.setBounds(0, 0, dp(16), dp(16));
        button.setCompoundDrawables(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(3));
        button.setTextColor(selected ? Color.WHITE : muted);
        button.setTextSize(11);
        button.setBackground(rounded(selected ? actionColor : surface, 14, 0, strokeColor));
        return button;
    }

    private void searchDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        EditText query = input("Cuenta, nota, categoria, fecha, importe...");
        query.setText(searchQuery);
        List<String> accounts = new ArrayList<>();
        accounts.add("Todas las cuentas");
        accounts.addAll(db.accounts(true));
        Spinner account = spinner(accounts);
        setSpinnerSelection(account, searchAccount.isEmpty() ? "Todas las cuentas" : searchAccount);
        EditText from = input("Desde");
        from.setText(searchFrom);
        from.setFocusable(false);
        from.setOnClickListener(v -> pickDate(from));
        EditText to = input("Hasta");
        to.setText(searchTo);
        to.setFocusable(false);
        to.setOnClickListener(v -> pickDate(to));
        form.addView(query);
        form.addView(label("Cuenta"));
        form.addView(account);
        LinearLayout dates = new LinearLayout(this);
        dates.setOrientation(LinearLayout.HORIZONTAL);
        dates.addView(from, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams toParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        toParams.setMargins(dp(8), 0, 0, 0);
        dates.addView(to, toParams);
        form.addView(label("Rango de fechas opcional"));
        form.addView(dates);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Buscar transacciones")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Limpiar", null)
                .setPositiveButton("Buscar", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                searchQuery = query.getText().toString().trim();
                String selected = account.getSelectedItem().toString();
                searchAccount = "Todas las cuentas".equals(selected) ? "" : selected;
                searchFrom = from.getText().toString().trim();
                searchTo = to.getText().toString().trim();
                if ((!searchFrom.isEmpty() && searchTo.isEmpty()) || (searchFrom.isEmpty() && !searchTo.isEmpty())) {
                    toast("Elige ambas fechas o deja las dos vacias.");
                    return;
                }
                transactionMode = "buscar";
                dialog.dismiss();
                renderScreen();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                clearSearch();
            });
        });
        dialog.show();
        styleDialog(dialog);
    }

    private LinearLayout.LayoutParams pillParams(boolean last) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(34), 1);
        p.setMargins(0, 0, last ? 0 : dp(6), dp(8));
        return p;
    }

    private View tab(String label, String target, int iconRes) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setClickable(true);
        tab.setFocusable(true);
        tab.setPadding(0, dp(2), 0, dp(1));
        tab.setOnClickListener(v -> screenTo(target));
        boolean selected = screen.equals(target);
        if (selected) {
            tab.setBackground(new InsetDrawable(rounded(actionSoft, 17, 0, strokeColor), dp(6), dp(4), dp(6), dp(4)));
        } else {
            tab.setBackground(rounded(Color.TRANSPARENT, 18, 0, strokeColor));
        }

        Drawable icon = getResources().getDrawable(iconRes).mutate();
        icon.setTint(selected ? actionColor : muted);
        ImageView image = new ImageView(this);
        image.setImageDrawable(icon);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        tab.addView(image, new LinearLayout.LayoutParams(dp(23), dp(23)));

        TextView text = text(label, 9, selected, selected ? actionColor : muted);
        text.setGravity(Gravity.CENTER);
        text.setIncludeFontPadding(false);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, dp(13));
        textParams.setMargins(0, 0, 0, 0);
        tab.addView(text, textParams);
        return tab;
    }

    private void screenTo(String target) {
        screen = target;
        if (!"accounts".equals(target)) activeAccountId = -1;
        if (!"stats".equals(target)) statsDetailCategory = null;
        draw();
    }

    private Button smallButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(textColor);
        b.setBackground(rounded(actionSoft, 18, 0, strokeColor));
        b.setOnClickListener(listener);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setIncludeFontPadding(false);
        return b;
    }

    private Button topIcon(String label, View.OnClickListener listener) {
        Button b = smallButton(label, listener);
        b.setTextSize("⋮".equals(label) ? 30 : 20);
        b.setTextColor(textColor);
        b.setBackground(rounded(Color.TRANSPARENT, 20, 0, strokeColor));
        return b;
    }

    private ImageButton iconButton(int drawableRes, View.OnClickListener listener) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawableRes);
        button.setColorFilter(textColor);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setOnClickListener(listener);
        return button;
    }

    private Button actionWide(String label, View.OnClickListener listener) {
        Button b = smallButton(label, listener);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(actionColor, 20, 0, actionColor));
        b.setLayoutParams(margins(-1, dp(52), 0, 8));
        return b;
    }

    private void styleDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(rounded(surface, 26, 1, strokeColor));
            tintTextTree(window.getDecorView());
        }
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL));
    }

    private void styleDialogButton(Button button) {
        if (button == null) return;
        button.setAllCaps(false);
        button.setTextColor(actionColor);
        button.setTextSize(14);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
    }

    private void tintTextTree(View view) {
        if (view instanceof TextView && !(view instanceof Button)) {
            ((TextView) view).setTextColor(textColor);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) tintTextTree(group.getChildAt(i));
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private int softAccent() {
        return actionSoft;
    }

    private int softIncome() {
        return darkMode ? Color.rgb(18, 58, 33) : Color.rgb(230, 249, 235);
    }

    private int softExpense() {
        return darkMode ? Color.rgb(62, 28, 28) : Color.rgb(255, 235, 234);
    }

    private SpannableString highlightedMeta(String primary, String secondary, String note) {
        String text = primary + "\n" + secondary + (note == null || note.trim().isEmpty() ? "" : "\n" + note);
        SpannableString span = new SpannableString(text);
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, primary.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(textColor), 0, primary.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        int secondaryStart = primary.length() + 1;
        span.setSpan(new ForegroundColorSpan(muted), secondaryStart, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setLineSpacing(dp(2), 1);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(textColor);
        e.setHintTextColor(muted);
        e.setTextSize(14);
        e.setBackground(rounded(controlSurface(), 16, 1, strokeColor));
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setLayoutParams(margins(-1, dp(48), 0, 8));
        return e;
    }

    private TextView label(String value) {
        TextView v = text(value, 12, true, muted);
        v.setPadding(0, dp(8), 0, 0);
        return v;
    }

    private CheckBox checkbox(String label, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setTextColor(textColor);
        c.setTextSize(13);
        c.setButtonTintList(android.content.res.ColorStateList.valueOf(actionColor));
        c.setChecked(checked);
        c.setPadding(0, dp(6), 0, dp(6));
        return c;
    }

    private Spinner spinner(List<String> values) {
        if (values.isEmpty()) values.add("Sin datos");
        Spinner s = new Spinner(this);
        s.setAdapter(stringAdapter(values));
        s.setBackground(rounded(controlSurface(), 16, 1, strokeColor));
        s.setPadding(dp(8), 0, dp(8), 0);
        s.setLayoutParams(margins(-1, dp(48), 0, 8));
        return s;
    }

    private ArrayAdapter<String> stringAdapter(List<String> values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getView(position, convertView, parent);
                styleSpinnerText(item, false);
                return item;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getDropDownView(position, convertView, parent);
                styleSpinnerText(item, true);
                return item;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void styleSpinnerText(TextView item, boolean dropdown) {
        item.setTextColor(textColor);
        item.setTextSize(14);
        item.setSingleLine(true);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), dropdown ? dp(12) : 0, dp(12), dropdown ? dp(12) : 0);
        if (dropdown) item.setBackgroundColor(surface);
    }

    private int controlSurface() {
        return darkMode ? Color.rgb(22, 34, 25) : Color.rgb(248, 255, 250);
    }

    private List<String> labels(String... values) {
        List<String> out = new ArrayList<>();
        for (String v : values) out.add(v);
        return out;
    }

    private LinearLayout.LayoutParams margins(int w, int h, int side, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(side), 0, dp(side), dp(bottom));
        return p;
    }

    private static final class DateRange {
        final String start;
        final String end;
        final String label;

        DateRange(String start, String end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void pickDate(EditText target) {
        Calendar cal = calendarFrom(target.getText().toString());
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            target.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.show();
        styleDialog(dialog);
    }

    private Calendar calendarFrom(String value) {
        Calendar cal = Calendar.getInstance();
        try {
            String[] parts = value.split("-");
            cal.set(Calendar.YEAR, Integer.parseInt(parts[0]));
            cal.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
            cal.set(Calendar.DAY_OF_MONTH, parts.length > 2 ? Integer.parseInt(parts[2]) : 1);
        } catch (Exception ignored) {
        }
        return cal;
    }

    private String nowTime() {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
    }

    private String monthLabel(String value) {
        try {
            String[] parts = value.split("-");
            String[] months = {"ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.", "ago.", "sep.", "oct.", "nov.", "dic."};
            int month = Integer.parseInt(parts[1]) - 1;
            return months[month] + " " + parts[0];
        } catch (Exception ignored) {
            return value;
        }
    }

    private String money(double value) {
        return String.format(Locale.US, "%s%,.2f", currencySymbol(currentCurrencyCode()), value);
    }

    private String currentCurrencyCode() {
        String saved = prefs == null ? null : prefs.getString("currency_code", null);
        if (saved != null && !saved.trim().isEmpty()) return saved;
        return db == null ? "USD" : db.primaryCurrency();
    }

    private String currencySymbol(String code) {
        if ("PEN".equals(code)) return "S/ ";
        if ("EUR".equals(code)) return "EUR ";
        if ("MXN".equals(code)) return "MX$ ";
        if ("COP".equals(code)) return "COL$ ";
        if ("CLP".equals(code)) return "CLP$ ";
        if ("ARS".equals(code)) return "AR$ ";
        if ("BRL".equals(code)) return "R$ ";
        return "$";
    }

    private double parse(String value) {
        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            Object item = spinner.getItemAtPosition(i);
            if (item != null && item.toString().equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }
}
