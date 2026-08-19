package com.codex.moneymate;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.os.Handler;
import android.os.Looper;
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
import android.widget.AutoCompleteTextView;
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

public class MoneyMateActivity extends Activity {
    private static final int IMPORT_BACKUP = 4001;
    private static final int EXPORT_BACKUP = 4002;
    private static final int EXPORT_REPORT = 4003;
    private static final String IMPORT_PREVIEW_DB = "moneymate-import-preview.sqlite";
    private static final String IMPORT_RECOVERY_FILE = "moneymate-before-last-import.mmbak";

    private MoneyDb db;
    private LinearLayout content;
    private String screen = "trans";
    private String transactionMode = "diario";
    private String accountMode = "diario";
    private String statsKind = "expense";
    private String statsDetailAccount;
    private String statsScope = "mensual";
    private String statsAnchorDate;
    private String transactionAnchorDate;
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
    private boolean pendingStatusReport;
    private boolean importInProgress;
    private boolean showHiddenAccounts;
    private SharedPreferences prefs;
    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private final Object syncLock = new Object();
    private boolean syncRunning;
    private volatile boolean activityResumed;
    private final Runnable pendingAutoSync = this::runPendingAutoSync;
    private final Runnable cloudPoll = new Runnable() {
        @Override
        public void run() {
            checkForCloudUpdates();
            if (activityResumed) syncHandler.postDelayed(this, 30000);
        }
    };
    private boolean darkMode;
    private String language = "es";
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
        language = prefs.getString("language", "es");
        showHiddenAccounts = prefs.getBoolean("show_hidden_accounts", false);
        applyPalette();
        if (getIntent() != null && getIntent().getData() != null) importBackup(getIntent().getData());
        period = db.latestMonth();
        statsAnchorDate = db.latestDate();
        transactionAnchorDate = db.latestDate();
        draw();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (prefs != null) {
            if (prefs.getBoolean("supabase_pending_upload", false)) scheduleAutoSync();
            syncHandler.removeCallbacks(cloudPoll);
            syncHandler.postDelayed(cloudPoll, 2500);
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        syncHandler.removeCallbacks(cloudPoll);
        syncHandler.removeCallbacks(pendingAutoSync);
        super.onPause();
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
        top.setPadding(dp(6), dp(4), dp(4), dp(4));
        top.setBackground(rounded(topSurface, 16, 0, strokeColor));
        top.setElevation(0);
        top.setLayoutParams(margins(-1, dp(64), 0, 10));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_pig);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        top.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));

        String subtitle;
        if ("stats".equals(screen)) subtitle = titleForScreen() + " · " + statsRange().label;
        else if ("trans".equals(screen)) subtitle = titleForScreen() + " · " + transactionRangeLabel();
        else subtitle = titleForScreen() + " · " + monthLabel(period);
        TextView title = text("", 19, true, textColor);
        title.setText(appHeaderText("Control Financiero", ui(subtitle)));
        title.setGravity(Gravity.CENTER);
        title.setOnClickListener(v -> {
            if ("stats".equals(screen)) statsDateDialog();
            else if ("trans".equals(screen)) transactionDateDialog();
            else monthDialog();
        });
        top.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        top.addView(topIcon("⋮", v -> menuDialog(v)), new LinearLayout.LayoutParams(dp(50), dp(50)));
        return top;
    }

    private String titleForScreen() {
        if ("stats".equals(screen)) return ui("Estadisticas");
        if ("accounts".equals(screen)) return ui("Cuentas");
        return ui("Transacciones");
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
        String value = type == null ? "" : type.trim();
        String lower = value.toLowerCase(Locale.US);
        if (lower.equals("efectivo") || lower.equals("cash")) return "Efectivo";
        if (value.isEmpty() || lower.equals("cuentas") || lower.equals("banco") || lower.equals("bank")) return "Cuentas de Banco";
        return value;
    }

    private void applyPalette() {
        darkMode = prefs != null && prefs.getBoolean("dark_mode", false);
        if (darkMode) {
            bg = Color.rgb(21, 23, 22);
            surface = Color.rgb(32, 35, 34);
            surface2 = Color.rgb(39, 42, 41);
            topSurface = Color.argb(224, 32, 35, 34);
            textColor = Color.rgb(241, 244, 242);
            muted = Color.rgb(165, 172, 168);
            accent = Color.rgb(67, 201, 139);
            actionColor = Color.rgb(67, 201, 139);
            actionSoft = Color.rgb(32, 61, 48);
            incomeColor = Color.rgb(67, 201, 139);
            expenseColor = Color.rgb(255, 107, 100);
            transferColor = Color.rgb(105, 169, 255);
            transferSoft = Color.rgb(32, 54, 79);
            strokeColor = Color.rgb(52, 57, 55);
        } else {
            bg = Color.rgb(244, 247, 245);
            surface = Color.WHITE;
            surface2 = Color.rgb(248, 250, 249);
            topSurface = Color.argb(232, 255, 255, 255);
            textColor = Color.rgb(24, 34, 46);
            muted = Color.rgb(102, 112, 133);
            accent = Color.rgb(19, 138, 97);
            actionColor = Color.rgb(19, 138, 97);
            actionSoft = Color.rgb(229, 244, 237);
            incomeColor = Color.rgb(19, 138, 97);
            expenseColor = Color.rgb(228, 61, 55);
            transferColor = Color.rgb(29, 111, 218);
            transferSoft = Color.rgb(231, 241, 255);
            strokeColor = Color.rgb(225, 231, 227);
        }
        Window window = getWindow();
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(bg);
        window.setStatusBarContrastEnforced(false);
        window.setNavigationBarContrastEnforced(false);
        int flags = window.getDecorView().getSystemUiVisibility();
        int lightSystemBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        flags = darkMode ? flags & ~lightSystemBars : flags | lightSystemBars;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void toggleTheme() {
        prefs.edit().putBoolean("dark_mode", !darkMode).apply();
        draw();
    }

    private void menuDialog(View anchor) {
        PopupWindow[] holder = new PopupWindow[1];
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(10), dp(8), dp(10), dp(8));
        menu.setBackground(rounded(surface, 16, 0, strokeColor));
        menu.addView(menuItem(R.drawable.ic_menu_period, "Periodo", "Elegir fecha con calendario", v -> closeThen(holder, () -> {
            if ("stats".equals(screen)) statsDateDialog();
            else monthDialog();
        })));
        menu.addView(menuItem(R.drawable.ic_menu_settings, "Preferencias", "Idioma, moneda y apariencia", v -> closeThen(holder, this::preferencesDialog)));
        menu.addView(menuItem(R.drawable.ic_menu_categories, "Categorias", "Ingresos, gastos y transferencias", v -> closeThen(holder, () -> categoryDialog())));
        menu.addView(menuItem(R.drawable.ic_menu_report, "Generar reporte", "Semanal, mensual, anual o todo", v -> closeThen(holder, this::reportDialog)));
        menu.addView(menuItem(R.drawable.ic_menu_sync, "Sincronizar", "Subir o descargar desde Supabase", v -> closeThen(holder, this::supabaseDialog)));
        menu.addView(menuItem(R.drawable.ic_menu_import, "Datos y respaldos", "Importar o exportar archivos", v -> closeThen(holder, this::dataBackupDialog)));
        menu.addView(menuItem(R.drawable.ic_menu_settings, "Acerca de", "Nombre, version y desarrollador", v -> closeThen(holder, this::aboutDialog)));
        ScrollView menuScroll = new ScrollView(this);
        menuScroll.setFillViewport(false);
        menuScroll.addView(menu);
        int popupHeight = Math.min(dp(420), getResources().getDisplayMetrics().heightPixels - dp(120));
        holder[0] = new PopupWindow(menuScroll, dp(296), popupHeight, true);
        holder[0].setOutsideTouchable(true);
        holder[0].setBackgroundDrawable(rounded(surface, 16, 0, strokeColor));
        holder[0].setElevation(0);
        holder[0].showAsDropDown(anchor, -dp(242), dp(4));
    }

    private void preferencesDialog() {
        AlertDialog[] holder = new AlertDialog[1];
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(6), dp(12), dp(6));
        box.addView(iconSmallButton("Idioma", R.drawable.ic_menu_settings, v -> closeThen(holder, this::languageDialog)), margins(-1, dp(50), 0, 6));
        box.addView(iconSmallButton("Moneda", R.drawable.ic_menu_currency, v -> closeThen(holder, this::currencyDialog)), margins(-1, dp(50), 0, 6));
        box.addView(iconSmallButton(darkMode ? "Modo claro" : "Modo oscuro", darkMode ? R.drawable.ic_menu_sun : R.drawable.ic_menu_moon_cloud, v -> closeThen(holder, this::toggleTheme)), margins(-1, dp(50), 0, 0));
        holder[0] = new AlertDialog.Builder(this)
                .setTitle(ui("Preferencias"))
                .setView(box)
                .setNegativeButton(ui("Cerrar"), null)
                .create();
        holder[0].show();
        styleDialog(holder[0]);
    }

    private void dataBackupDialog() {
        AlertDialog[] holder = new AlertDialog[1];
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(6), dp(12), dp(6));
        box.addView(iconSmallButton("Importar datos", R.drawable.ic_menu_import, v -> closeThen(holder, this::openImport)), margins(-1, dp(50), 0, 6));
        box.addView(iconSmallButton("Exportar respaldo", R.drawable.ic_menu_export, v -> closeThen(holder, this::openExport)), margins(-1, dp(50), 0, 6));
        box.addView(iconSmallButton("Revisar integridad", R.drawable.ic_menu_settings, v -> closeThen(holder, this::integrityDialog)), margins(-1, dp(50), 0, 6));
        File recovery = importRecoveryFile();
        if (recovery.isFile()) {
            box.addView(iconSmallButton("Deshacer última importación", R.drawable.ic_action_back, v -> closeThen(holder, this::confirmUndoLastImport)), margins(-1, dp(50), 0, 0));
        }
        holder[0] = new AlertDialog.Builder(this)
                .setTitle(ui("Datos y respaldos"))
                .setView(box)
                .setNegativeButton(ui("Cerrar"), null)
                .create();
        holder[0].show();
        styleDialog(holder[0]);
    }

    private void integrityDialog() {
        IntegrityReport report = ImportSafety.audit(db);
        String message = ui(report.getHealthy() ? "Datos en buen estado" : "Se encontraron observaciones")
                + "\n\n" + ui("Base de datos") + ": " + ui(report.getDatabaseOk() ? "Correcta" : "Requiere atención")
                + "\n" + ui("Movimientos duplicados") + ": " + report.getDuplicateMovements()
                + "\n" + ui("Movimientos sin cuenta") + ": " + report.getOrphanMovements()
                + "\n" + ui("Fechas o importes inválidos") + ": " + report.getInvalidMovements()
                + "\n" + ui("Transferencias incompletas") + ": " + report.getUnpairedTransfers();
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Integridad de los datos")
                .setMessage(message)
                .setNegativeButton("Cerrar", null);
        if (report.getRepairableIssues() > 0) {
            builder.setPositiveButton("Reparar problemas seguros", (d, w) -> {
                db.repairSafeIntegrityIssues();
                markLocalDataChanged();
                draw();
                integrityDialog();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        styleDialog(dialog);
    }

    private void closeThen(AlertDialog[] holder, Runnable action) {
        if (holder[0] != null) holder[0].dismiss();
        action.run();
    }

    private void closeThen(PopupWindow[] holder, Runnable action) {
        if (holder[0] != null) holder[0].dismiss();
        action.run();
    }

    private void languageDialog() {
        String[] labels = {"Español", "English", "Português", "Français"};
        String[] values = {"es", "en", "pt", "fr"};
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(language)) checked = i;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(ui("Idioma"))
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    language = values[which];
                    prefs.edit().putString("language", language).apply();
                    d.dismiss();
                    draw();
                })
                .setNegativeButton(ui("Cancelar"), null)
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void aboutDialog() {
        String body = "Control Financiero\n" + ui("Version") + ": " + appVersion()
                + "\n" + ui("Desarrollador") + ": Gwr4rd"
                + "\nhttps://github.com/Gwr4rd";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(ui("Acerca de"))
                .setMessage(body)
                .setNeutralButton(ui("Ver repositorios"), (d, w) -> {
                    Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Gwr4rd"));
                    startActivity(browser);
                })
                .setPositiveButton(ui("Cerrar"), null)
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "2.1.0";
        }
    }

    private View menuItem(int drawableRes, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(8), dp(5), dp(8), dp(5));
        item.setBackground(rounded(surface2, 8, 0, strokeColor));
        item.setLayoutParams(margins(-1, -2, 0, 4));
        Drawable drawable = getResources().getDrawable(drawableRes).mutate();
        drawable.setTint(actionColor);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(drawable);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(6), dp(6), dp(6), dp(6));
        item.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView label = text(title + "\n" + subtitle, 13, true, textColor);
        label.setPadding(dp(8), 0, 0, 0);
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
        TransactionRange selectedRange = TransactionScopes.range(
                transactionMode,
                transactionAnchorDate,
                db.latestDate(),
                language
        );
        String start = selectedRange.start;
        String end = selectedRange.end;
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

        Button search = searchModeButton();
        search.setText(ui("Buscar cuentas, notas, fechas o importes"));
        content.addView(search, margins(-1, dp(40), 0, 7));

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.addView(modeButton("Anual", "anual"), pillParams(false));
        modes.addView(modeButton("Mensual", "mensual"), pillParams(false));
        modes.addView(modeButton("Semanal", "semanal"), pillParams(false));
        modes.addView(modeButton("Diario", "diario"), pillParams(false));
        modes.addView(modeButton("Total", "todo"), pillParams(true));
        content.addView(modes);

        if ("buscar".equals(transactionMode)) content.addView(activeSearchStrip(rows.size()));
        renderTransactionRows(rows, false);
    }

    private String transactionRangeLabel() {
        if ("buscar".equals(transactionMode)) return "Busqueda";
        return TransactionScopes.range(transactionMode, transactionAnchorDate, db.latestDate(), language).label;
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
        strip.setBackground(rounded(softAccent(), 6, 1, strokeColor));
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
        if (statsDetailAccount != null) {
            renderStatsDetail();
            return;
        }
        List<MoneyDb.Bar> bars = db.accountFlowTotalsBetween(statsKind, range.start, range.end);

        content.addView(statsPeriodHeader());
        content.addView(statsTotalsHeader(s));
        content.addView(statusExportAction());

        PieChartView pie = new PieChartView(this);
        pie.setData(bars);
        pie.setTextColor(textColor);
        pie.setCenterText(String.valueOf(bars.size()), ui(bars.size() == 1 ? "Cuenta" : "Cuentas"));
        pie.setContentDescription(ui("Distribución por cuenta"));

        LinearLayout pieBox = flatSection();
        pieBox.addView(text("Distribución por cuenta", 12, true, muted));
        if (bars.isEmpty()) {
            pieBox.addView(empty("Sin datos en este periodo."));
        } else {
            pieBox.addView(pie, new LinearLayout.LayoutParams(-1, dp(206)));
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
            statsDetailAccount = null;
            renderScreen();
        }), new LinearLayout.LayoutParams(dp(42), dp(40)));
        TextView title = text(statsDetailAccount, 18, true, textColor);
        title.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView month = text(statsRange().label, 14, true, muted);
        month.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        head.addView(month, new LinearLayout.LayoutParams(dp(116), dp(44)));
        content.addView(head);

        List<MoneyDb.Row> rows = filteredAccountRows();
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

    private List<MoneyDb.Row> filteredAccountRows() {
        List<MoneyDb.Row> out = new ArrayList<>();
        DateRange range = statsRange();
        for (MoneyDb.Row row : db.transactionsForDisplay(range.start, range.end)) {
            if (row.isTransfer()) {
                String account = "income".equals(statsKind) ? row.transferTo : row.transferFrom;
                if (statsDetailAccount.equals(account)) out.add(row);
            } else if (row.account.equals(statsDetailAccount) && row.kind.equals(statsKind)) {
                out.add(row);
            }
        }
        return out;
    }

    private View statusExportAction() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(6));
        Button export = smallButton("Exportar estado XLSX", v -> prepareStatusReport());
        export.setTextSize(12);
        export.setTextColor(actionColor);
        export.setBackground(rounded(controlSurface(), 7, 1, strokeColor));
        row.addView(export, new LinearLayout.LayoutParams(dp(176), dp(38)));
        return row;
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
        LinearLayout.LayoutParams scopeParams = new LinearLayout.LayoutParams(dp(126), dp(42));
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
            statsDetailAccount = null;
            renderScreen();
        });
        row.addView(income, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView expense = text("Gastos  " + money(s.expense), 14, true, incomeSelected ? muted : textColor);
        expense.setGravity(Gravity.CENTER);
        expense.setOnClickListener(v -> {
            statsKind = "expense";
            statsDetailAccount = null;
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
        label.setMaxLines(2);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1));

        TextView amount = text(money(bar.value), 13, true, textColor);
        amount.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(amount, new LinearLayout.LayoutParams(dp(112), dp(48)));
        row.setOnClickListener(v -> {
            statsDetailAccount = bar.label;
            renderScreen();
        });
        return row;
    }

    private int statColor(int index) {
        int[] colors = new int[]{
                Color.rgb(255, 59, 92),
                Color.rgb(255, 138, 52),
                Color.rgb(255, 202, 40),
                Color.rgb(0, 184, 148),
                Color.rgb(0, 168, 232),
                Color.rgb(61, 90, 254),
                Color.rgb(168, 85, 247),
                Color.rgb(236, 72, 153)
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
            if (isHiddenAccount(a)) hidden.add(a);
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

    private boolean hasZeroBalance(MoneyDb.AccountTotal account) {
        return Math.abs(account.balance) < 0.005;
    }

    private boolean isHiddenAccount(MoneyDb.AccountTotal account) {
        return account.hidden || hasZeroBalance(account);
    }

    private LinearLayout accountActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(margins(-1, -2, 0, 8));
        row.addView(topActionButton("Nueva cuenta", v -> accountDialog()), new LinearLayout.LayoutParams(0, dp(44), 1));
        Button organize = topActionButton("Tipos y categorias", v -> accountMetadataDialog());
        organize.setTextColor(textColor);
        organize.setBackground(rounded(controlSurface(), 6, 1, strokeColor));
        LinearLayout.LayoutParams organizeParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        organizeParams.setMargins(dp(8), 0, 0, 0);
        row.addView(organize, organizeParams);
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
        v.setBackground(rounded(actionColor, 6, 0, actionColor));
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
        b.setBackground(rounded(actionColor, 6, 0, actionColor));
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
        movementDialog(copyFrom, false, null, false);
    }

    private void editMovementDialog(MoneyDb.Row row) {
        movementDialog(row, true, null, false);
    }

    private void movementDialog(MoneyDb.Row copyFrom, boolean editMode) {
        movementDialog(copyFrom, editMode, null, false);
    }

    private void movementDialog(MoneyDb.Row copyFrom, boolean editMode, String forcedDate) {
        movementDialog(copyFrom, editMode, forcedDate, false);
    }

    private void movementDialog(MoneyDb.Row copyFrom, boolean editMode, String forcedDate, boolean useCurrentTime) {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(16), dp(12), dp(16), dp(20));
        form.setOrientation(LinearLayout.VERTICAL);

        Spinner type = spinner(labels("Gasto", "Ingreso", "Transferencia"));
        EditText date = input("Fecha AAAA-MM-DD");
        date.setText(forcedDate != null ? forcedDate : (copyFrom == null ? today() : copyFrom.date));
        date.setFocusable(false);
        date.setInputType(0);
        date.setOnClickListener(v -> pickDate(date));
        EditText time = input("Hora HH:MM");
        time.setText(copyFrom == null || useCurrentTime ? nowTime() : copyFrom.time);
        time.setFocusable(false);
        time.setInputType(0);
        time.setOnClickListener(v -> pickTime(time));
        EditText amount = input("Importe");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (copyFrom != null) amount.setText(String.format(Locale.US, "%.2f", copyFrom.amount));
        List<AccountSelectionOption> accountChoices = movementAccountChoices();
        List<String> accountTypes = db.accountTypes();
        if (accountTypes.isEmpty()) accountTypes.add("Cuentas de Banco");
        String initialAccountName = copyFrom == null ? "" : (copyFrom.isTransfer() && !copyFrom.transferFrom.isEmpty() ? copyFrom.transferFrom : copyFrom.account);
        String initialToAccountName = copyFrom == null ? "" : copyFrom.transferTo;
        String initialAccountType = accountTypeForName(accountChoices, initialAccountName, accountTypes.get(0));
        String initialToAccountType = accountTypeForName(accountChoices, initialToAccountName, accountTypes.get(0));
        Spinner accountType = spinner(new ArrayList<>(accountTypes));
        Spinner toAccountType = spinner(new ArrayList<>(accountTypes));
        setSpinnerSelection(accountType, initialAccountType);
        setSpinnerSelection(toAccountType, initialToAccountType);
        Spinner account = accountSpinner(accountChoicesForType(accountChoices, initialAccountType));
        Spinner toAccount = accountSpinner(accountChoicesForType(accountChoices, initialToAccountType));
        setSpinnerSelection(account, initialAccountName);
        setSpinnerSelection(toAccount, initialToAccountName);
        if (copyFrom == null && toAccount.getCount() > 1) toAccount.setSelection(1);
        Spinner category = spinner(db.categories("expense"));
        AutoCompleteTextView note = noteInput(db.recentNotes());
        EditText description = input("Descripcion");
        if (copyFrom != null) {
            note.setText(copyFrom.note);
            description.setText(copyFrom.description);
        }

        form.addView(label("Tipo de movimiento"));
        LinearLayout typeSwitch = new LinearLayout(this);
        typeSwitch.setOrientation(LinearLayout.HORIZONTAL);
        Button incomeType = smallButton("Ingreso", null);
        Button expenseType = smallButton("Gasto", null);
        Button transferType = smallButton("Transferencia", null);
        Button[] typeButtons = {expenseType, incomeType, transferType};
        typeSwitch.addView(incomeType, movementTypeParams(false));
        typeSwitch.addView(expenseType, movementTypeParams(false));
        typeSwitch.addView(transferType, movementTypeParams(true));
        form.addView(typeSwitch);
        type.setVisibility(View.GONE);
        form.addView(type, new LinearLayout.LayoutParams(1, 1));

        form.addView(label("Fecha y hora"));
        LinearLayout dateTime = new LinearLayout(this);
        dateTime.setOrientation(LinearLayout.HORIZONTAL);
        dateTime.addView(date, new LinearLayout.LayoutParams(0, dp(48), 3));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0, dp(48), 2);
        timeParams.setMargins(dp(8), 0, 0, 0);
        dateTime.addView(time, timeParams);
        form.addView(dateTime);

        form.addView(label("Importe"));
        form.addView(amount);
        TextView accountLabel = label("Cuenta");
        TextView accountTypeLabel = label("Tipo de cuenta");
        TextView toAccountTypeLabel = label("Tipo de destino");
        TextView toAccountLabel = label("Cuenta destino");
        TextView categoryLabel = label("Categoria");
        form.addView(categoryLabel);
        form.addView(category);
        form.addView(accountTypeLabel);
        form.addView(accountType);
        form.addView(accountLabel);
        form.addView(account);
        form.addView(toAccountTypeLabel);
        form.addView(toAccountType);
        form.addView(toAccountLabel);
        form.addView(toAccount);
        form.addView(label("Nota"));
        form.addView(note);
        form.addView(label("Descripcion"));
        form.addView(description);

        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyMovementType(position, typeButtons, accountLabel, accountTypeLabel, accountType,
                        toAccountTypeLabel, toAccountType, toAccountLabel, toAccount, categoryLabel, category);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        incomeType.setOnClickListener(v -> {
            type.setSelection(1);
            applyMovementType(1, typeButtons, accountLabel, accountTypeLabel, accountType,
                    toAccountTypeLabel, toAccountType, toAccountLabel, toAccount, categoryLabel, category);
        });
        expenseType.setOnClickListener(v -> {
            type.setSelection(0);
            applyMovementType(0, typeButtons, accountLabel, accountTypeLabel, accountType,
                    toAccountTypeLabel, toAccountType, toAccountLabel, toAccount, categoryLabel, category);
        });
        transferType.setOnClickListener(v -> {
            type.setSelection(2);
            applyMovementType(2, typeButtons, accountLabel, accountTypeLabel, accountType,
                    toAccountTypeLabel, toAccountType, toAccountLabel, toAccount, categoryLabel, category);
        });
        accountType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String preferred = account.getSelectedItem() == null ? initialAccountName : account.getSelectedItem().toString();
                setAccountSpinnerChoices(account, accountChoicesForType(accountChoices, parent.getItemAtPosition(position).toString()), preferred);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        toAccountType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String preferred = toAccount.getSelectedItem() == null ? initialToAccountName : toAccount.getSelectedItem().toString();
                setAccountSpinnerChoices(toAccount, accountChoicesForType(accountChoices, parent.getItemAtPosition(position).toString()), preferred);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        applyMovementType(0, typeButtons, accountLabel, accountTypeLabel, accountType,
                toAccountTypeLabel, toAccountType, toAccountLabel, toAccount, categoryLabel, category);

        if (copyFrom != null) {
            int mode = MovementFormRules.positionFor(copyFrom.kind, copyFrom.isTransfer());
            type.setSelection(mode);
            applyMovementType(mode, typeButtons, accountLabel, accountTypeLabel, accountType,
                    toAccountTypeLabel, toAccountType, toAccountLabel, toAccount, categoryLabel, category);
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
        MovementFormRule rule = MovementFormRules.forPosition(selected);
        if (account.getSelectedItem() == null || "Sin cuentas".equals(account.getSelectedItem().toString())) {
            toast("Crea una cuenta para registrar el movimiento.");
            return false;
        }
        if (rule.transfer) {
            String from = account.getSelectedItem().toString();
            if (toAccount.getSelectedItem() == null || "Sin cuentas".equals(toAccount.getSelectedItem().toString())) {
                toast("Elige una cuenta de destino disponible.");
                return false;
            }
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
            String kind = rule.kind;
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
        transactionAnchorDate = d;
        markLocalDataChanged();
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
                .setPositiveButton("Copiar", (d, w) -> movementDialog(
                        row,
                        false,
                        selected[0] == 0 ? today() : row.date,
                        selected[0] == 0
                ))
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
                    markLocalDataChanged();
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
        List<String> typeOptions = new ArrayList<>(db.accountTypes());
        typeOptions.add("Crear nuevo tipo...");
        Spinner type = spinner(typeOptions);
        EditText newType = input("Nombre del nuevo tipo");
        newType.setVisibility(View.GONE);
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
        form.addView(newType);
        form.addView(name);
        form.addView(balance);
        form.addView(currency);
        form.addView(description);
        form.addView(includeTotal);
        form.addView(hidden);
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                newType.setVisibility("Crear nuevo tipo...".equals(parent.getItemAtPosition(position).toString()) ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(edit == null ? "Nueva cuenta" : "Editar cuenta")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d, w) -> {
                    if (name.getText().toString().trim().isEmpty()) return;
                    String selectedType = type.getSelectedItem().toString();
                    if ("Crear nuevo tipo...".equals(selectedType)) selectedType = newType.getText().toString().trim();
                    if (selectedType.isEmpty()) {
                        toast("Escribe un nombre para el tipo de cuenta.");
                        return;
                    }
                    db.addAccountType(selectedType);
                    if (edit == null) {
                        db.addAccount(name.getText().toString(), currency.getText().toString(), selectedType, parse(balance.getText().toString()), description.getText().toString(), includeTotal.isChecked(), hidden.isChecked());
                    } else {
                        db.updateAccount(edit.id, edit.name, name.getText().toString(), currency.getText().toString(), selectedType, parse(balance.getText().toString()), description.getText().toString(), includeTotal.isChecked(), hidden.isChecked());
                    }
                    markLocalDataChanged();
                    renderScreen();
                });
        if (edit != null) {
            builder.setNeutralButton("Eliminar", (d, w) -> {
                if (db.deleteAccount(edit.id, edit.name)) {
                    markLocalDataChanged();
                } else {
                    toast("No se puede eliminar una cuenta con movimientos.");
                }
                renderScreen();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        styleDialog(dialog);
    }

    private void accountMetadataDialog() {
        String[] options = {"Tipos de cuenta", "Categorias de movimientos"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Organizar cuentas")
                .setItems(options, (d, which) -> {
                    if (which == 0) accountTypeDialog();
                    else categoryDialog();
                })
                .setNegativeButton("Cerrar", null)
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void accountTypeDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        form.setOrientation(LinearLayout.VERTICAL);
        EditText name = input("Nuevo tipo de cuenta");
        form.addView(name);
        form.addView(label("Tipos disponibles"));
        for (String type : db.accountTypes()) form.addView(accountTypeLine(type));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Tipos de cuenta")
                .setView(scroll)
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Agregar", (d, w) -> {
                    String value = name.getText().toString().trim();
                    if (value.isEmpty()) return;
                    db.addAccountType(value);
                    markLocalDataChanged();
                    renderScreen();
                })
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private TextView accountTypeLine(String type) {
        boolean builtIn = "Efectivo".equals(type) || "Cuentas de Banco".equals(type);
        TextView view = text(type + (builtIn ? "  ·  Predeterminado" : "  ·  Toca para editar"), 14, !builtIn, textColor);
        view.setPadding(dp(12), dp(11), dp(12), dp(11));
        view.setBackground(rounded(surface2, 10, 1, strokeColor));
        view.setLayoutParams(margins(-1, -2, 0, 6));
        if (!builtIn) view.setOnClickListener(v -> accountTypeEditDialog(type));
        return view;
    }

    private void accountTypeEditDialog(String oldName) {
        EditText name = input("Nombre del tipo");
        name.setText(oldName);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        form.addView(name);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Editar tipo de cuenta")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d, w) -> {
                    String value = name.getText().toString().trim();
                    if (value.isEmpty()) return;
                    db.updateAccountType(oldName, value);
                    markLocalDataChanged();
                    renderScreen();
                })
                .setNeutralButton("Eliminar", (d, w) -> {
                    if (!db.deleteAccountType(oldName)) toast("No se puede eliminar un tipo que tiene cuentas.");
                    else markLocalDataChanged();
                    renderScreen();
                })
                .create();
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
                    markLocalDataChanged();
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
                    markLocalDataChanged();
                    renderScreen();
                })
                .setNeutralButton("Eliminar", (d, w) -> {
                    if (db.deleteCategory(category.id, category.name)) {
                        markLocalDataChanged();
                    } else {
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
                    markLocalDataChanged();
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
        } else if (hasStoredSupabaseSession()) {
            supabaseActionsDialog(url, key);
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
        Button guide = iconSmallButton("Cómo crear y configurar Supabase", R.drawable.ic_action_help, null);
        form.addView(guide, new LinearLayout.LayoutParams(-1, dp(56)));
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
                String previousUrl = prefs.getString("supabase_url", "");
                String previousKey = prefs.getString("supabase_key", "");
                prefs.edit()
                        .putString("supabase_url", projectUrl)
                        .putString("supabase_key", publicKey)
                        .apply();
                if (!projectUrl.equals(previousUrl) || !publicKey.equals(previousKey)) {
                    clearSupabaseSession();
                }
                dialog.dismiss();
                toast("Conexión de Supabase guardada.");
                supabaseAccountDialog();
            });
        });
        guide.setOnClickListener(v -> {
            dialog.dismiss();
            supabaseHelpDialog();
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
        if (hasStoredSupabaseSession()) {
            supabaseActionsDialog(projectUrl, publicKey);
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

        Button accountAction = iconActionWide("Iniciar sesión", R.drawable.ic_menu_sync, null);
        form.addView(accountAction);
        Button guide = iconSmallButton("Cómo crear y configurar Supabase", R.drawable.ic_action_help, null);
        form.addView(guide, new LinearLayout.LayoutParams(-1, dp(56)));
        Button changeConnection = iconSmallButton("Cambiar conexión de Supabase", R.drawable.ic_menu_settings, null);
        form.addView(changeConnection, new LinearLayout.LayoutParams(-1, dp(52)));

        Runnable refreshMode = () -> {
            loginTab.setTextColor(createMode[0] ? muted : Color.WHITE);
            createTab.setTextColor(createMode[0] ? Color.WHITE : muted);
            loginTab.setBackground(rounded(createMode[0] ? surface2 : actionColor, 12, 0, strokeColor));
            createTab.setBackground(rounded(createMode[0] ? actionColor : surface2, 12, 0, strokeColor));
            accountAction.setText(ui(createMode[0] ? "Crear cuenta" : "Iniciar sesión"));
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
        guide.setOnClickListener(v -> {
            dialog.dismiss();
            supabaseHelpDialog();
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
                        saveSupabaseSession(session, email);
                        toast(createAccount ? "Cuenta creada y conectada." : "Cuenta conectada.");
                        supabaseActionsDialog(url, key);
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

    private void supabaseActionsDialog(String url, String key) {
        String email = prefs.getString("supabase_email", "");
        boolean conflict = prefs.getBoolean("supabase_conflict", false);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        TextView status = text(
                "Cuenta conectada\n" + email
                        + "\nSincronización automática activa"
                        + (conflict ? "\nConflicto pendiente: elige qué copia conservar" : "")
                        + "\n" + lastSupabaseSyncLabel(),
                13,
                true,
                conflict ? expenseColor : incomeColor
        );
        status.setBackground(rounded(surface2, 10, 0, strokeColor));
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        form.addView(status, margins(-1, -2, 0, 12));
        AlertDialog[] holder = new AlertDialog[1];
        form.addView(iconActionWide(conflict ? "Conservar copia local y subir" : "Subir copia local ahora", R.drawable.ic_menu_sync, v -> {
            holder[0].dismiss();
            runSupabaseSync(true, url, key);
        }));
        form.addView(iconSmallButton(conflict ? "Usar copia de la nube" : "Descargar copia de la nube", R.drawable.ic_menu_import, v -> {
            holder[0].dismiss();
            confirmSupabaseDownload(url, key);
        }), new LinearLayout.LayoutParams(-1, dp(48)));
        form.addView(iconSmallButton("Manual y script SQL", R.drawable.ic_action_help, v -> {
            holder[0].dismiss();
            supabaseHelpDialog();
        }), new LinearLayout.LayoutParams(-1, dp(52)));
        form.addView(iconSmallButton("Cambiar conexión de Supabase", R.drawable.ic_menu_settings, v -> {
            holder[0].dismiss();
            supabaseConfigurationDialog();
        }), new LinearLayout.LayoutParams(-1, dp(52)));
        form.addView(iconSmallButton("Cerrar sesión en este dispositivo", R.drawable.ic_action_logout, v -> {
            holder[0].dismiss();
            clearSupabaseSession();
            toast("Sesión cerrada.");
            supabaseAccountDialog();
        }), new LinearLayout.LayoutParams(-1, dp(52)));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        holder[0] = new AlertDialog.Builder(this)
                .setTitle("Sincronización")
                .setView(scroll)
                .setPositiveButton("Cerrar", null)
                .create();
        holder[0].show();
        styleDialog(holder[0]);
    }

    private void confirmSupabaseDownload(String url, String key) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Reemplazar datos locales")
                .setMessage("La copia de Supabase reemplazara las cuentas, categorias y transacciones guardadas en este dispositivo.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Descargar", (d, w) -> runSupabaseSync(false, url, key))
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void runSupabaseSync(boolean upload, String url, String key) {
        if (!claimSyncOperation()) {
            toast("Ya hay una sincronización en curso.");
            return;
        }
        toast(upload ? "Subiendo datos..." : "Descargando datos...");
        new Thread(() -> {
            try {
                SupabaseSync.Session session = storedSupabaseSession(url, key);
                if (upload) {
                    long revision = prefs.getLong("supabase_local_revision", 0);
                    String updatedAt = SupabaseSync.upload(url.trim(), key.trim(), session, cloudSnapshotJson());
                    recordUploadSuccess(revision, updatedAt);
                } else {
                    SupabaseSync.RemoteSnapshot remote = SupabaseSync.download(url.trim(), key.trim(), session);
                    replaceFromCloudSnapshot(remote.data);
                    prefs.edit()
                            .putString("supabase_remote_updated_at", remote.updatedAt)
                            .putBoolean("supabase_pending_upload", false)
                            .putBoolean("supabase_conflict", false)
                            .apply();
                }
                runOnUiThread(() -> {
                    prefs.edit()
                            .putLong("last_supabase_sync", System.currentTimeMillis())
                            .remove("supabase_last_error")
                            .apply();
                    period = db.latestMonth();
                    statsAnchorDate = db.latestDate();
                    toast(upload ? "Copia subida a Supabase." : "Datos descargados desde Supabase.");
                    draw();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> toast("No se pudo sincronizar: " + ex.getMessage()));
            } finally {
                releaseSyncOperation();
            }
        }).start();
    }

    private void markLocalDataChanged() {
        long revision = prefs.getLong("supabase_local_revision", 0) + 1;
        prefs.edit()
                .putLong("supabase_local_revision", revision)
                .putBoolean("supabase_pending_upload", true)
                .apply();
        scheduleAutoSync();
    }

    private void scheduleAutoSync() {
        if (!hasStoredSupabaseSession()) return;
        syncHandler.removeCallbacks(pendingAutoSync);
        syncHandler.postDelayed(pendingAutoSync, 900);
    }

    private void runPendingAutoSync() {
        if (!prefs.getBoolean("supabase_pending_upload", false) || !hasStoredSupabaseSession()) return;
        String url = prefs.getString("supabase_url", "").trim();
        String key = prefs.getString("supabase_key", "").trim();
        if (url.isEmpty() || key.isEmpty()) return;
        if (!claimSyncOperation()) {
            syncHandler.postDelayed(pendingAutoSync, 1200);
            return;
        }
        long revision = prefs.getLong("supabase_local_revision", 0);
        new Thread(() -> {
            try {
                SupabaseSync.Session session = storedSupabaseSession(url, key);
                if (detectRemoteConflict(url, key, session)) return;
                String updatedAt = SupabaseSync.upload(url, key, session, cloudSnapshotJson());
                recordUploadSuccess(revision, updatedAt);
            } catch (Exception ex) {
                prefs.edit().putString("supabase_last_error", String.valueOf(ex.getMessage())).apply();
            } finally {
                releaseSyncOperation();
                if (activityResumed
                        && prefs.getBoolean("supabase_pending_upload", false)
                        && !prefs.getBoolean("supabase_conflict", false)) {
                    long currentRevision = prefs.getLong("supabase_local_revision", 0);
                    syncHandler.postDelayed(pendingAutoSync, currentRevision == revision ? 15000 : 900);
                }
            }
        }).start();
    }

    private void recordUploadSuccess(long revision, String updatedAt) {
        SharedPreferences.Editor editor = prefs.edit()
                .putLong("last_supabase_sync", System.currentTimeMillis())
                .putBoolean("supabase_conflict", false)
                .remove("supabase_last_error");
        if (revision == prefs.getLong("supabase_local_revision", 0)) {
            editor.putBoolean("supabase_pending_upload", false);
        }
        if (updatedAt != null && !updatedAt.isEmpty()) {
            editor.putString("supabase_remote_updated_at", updatedAt);
        }
        editor.apply();
    }

    private void checkForCloudUpdates() {
        if (!hasStoredSupabaseSession()
                || prefs.getBoolean("supabase_pending_upload", false)
                || prefs.getBoolean("supabase_conflict", false)
                || !claimSyncOperation()) {
            return;
        }
        String url = prefs.getString("supabase_url", "").trim();
        String key = prefs.getString("supabase_key", "").trim();
        if (url.isEmpty() || key.isEmpty()) {
            releaseSyncOperation();
            return;
        }
        new Thread(() -> {
            boolean changed = false;
            try {
                SupabaseSync.Session session = storedSupabaseSession(url, key);
                SupabaseSync.RemoteSnapshot remote = SupabaseSync.download(url, key, session);
                String previous = prefs.getString("supabase_remote_updated_at", "");
                if (previous.isEmpty()) {
                    prefs.edit().putString("supabase_remote_updated_at", remote.updatedAt).apply();
                } else if (!remote.updatedAt.isEmpty() && !remote.updatedAt.equals(previous)) {
                    replaceFromCloudSnapshot(remote.data);
                    prefs.edit()
                            .putString("supabase_remote_updated_at", remote.updatedAt)
                            .putLong("last_supabase_sync", System.currentTimeMillis())
                            .remove("supabase_last_error")
                            .apply();
                    changed = true;
                }
            } catch (Exception ex) {
                String message = String.valueOf(ex.getMessage());
                if (!message.contains("Todavia no existe")) {
                    prefs.edit().putString("supabase_last_error", message).apply();
                }
            } finally {
                releaseSyncOperation();
            }
            if (changed) {
                runOnUiThread(() -> {
                    period = db.latestMonth();
                    statsAnchorDate = db.latestDate();
                    draw();
                    toast("Cambios recibidos desde Supabase.");
                });
            }
        }).start();
    }

    private boolean detectRemoteConflict(String url, String key, SupabaseSync.Session session) throws Exception {
        try {
            SupabaseSync.RemoteSnapshot remote = SupabaseSync.download(url, key, session);
            String previous = prefs.getString("supabase_remote_updated_at", "");
            if (!remote.updatedAt.isEmpty() && (previous.isEmpty() || !remote.updatedAt.equals(previous))) {
                prefs.edit()
                        .putBoolean("supabase_conflict", true)
                        .putString("supabase_last_error", "Hay cambios locales y en la nube. Abre Sincronización y elige qué copia conservar.")
                        .apply();
                return true;
            }
        } catch (Exception ex) {
            if (!String.valueOf(ex.getMessage()).contains("Todavia no existe")) throw ex;
        }
        return false;
    }

    private boolean claimSyncOperation() {
        synchronized (syncLock) {
            if (syncRunning) return false;
            syncRunning = true;
            return true;
        }
    }

    private void releaseSyncOperation() {
        synchronized (syncLock) {
            syncRunning = false;
        }
    }

    private boolean hasStoredSupabaseSession() {
        return prefs != null
                && !prefs.getString("supabase_user_id", "").isEmpty()
                && !prefs.getString("supabase_refresh_token", "").isEmpty();
    }

    private SupabaseSync.Session storedSupabaseSession(String url, String key) throws Exception {
        String accessToken = prefs.getString("supabase_access_token", "");
        String refreshToken = prefs.getString("supabase_refresh_token", "");
        String userId = prefs.getString("supabase_user_id", "");
        long expiresAt = prefs.getLong("supabase_expires_at", 0);
        if (refreshToken.isEmpty() || userId.isEmpty()) {
            throw new IllegalArgumentException("La sesión terminó. Inicia sesión nuevamente.");
        }
        SupabaseSync.Session session = new SupabaseSync.Session(accessToken, refreshToken, userId, expiresAt);
        if (accessToken.isEmpty() || session.needsRefresh()) {
            session = SupabaseSync.refreshSession(url, key, refreshToken);
            saveSupabaseSession(session, prefs.getString("supabase_email", ""));
        }
        return session;
    }

    private void saveSupabaseSession(SupabaseSync.Session session, String email) {
        prefs.edit()
                .putString("supabase_access_token", session.accessToken)
                .putString("supabase_refresh_token", session.refreshToken)
                .putString("supabase_user_id", session.userId)
                .putLong("supabase_expires_at", session.expiresAtEpochSeconds)
                .putString("supabase_email", email)
                .remove("supabase_last_error")
                .apply();
    }

    private void clearSupabaseSession() {
        prefs.edit()
                .remove("supabase_access_token")
                .remove("supabase_refresh_token")
                .remove("supabase_user_id")
                .remove("supabase_expires_at")
                .remove("supabase_remote_updated_at")
                .remove("supabase_last_error")
                .putBoolean("supabase_pending_upload", false)
                .putBoolean("supabase_conflict", false)
                .apply();
    }

    private String lastSupabaseSyncLabel() {
        long last = prefs.getLong("last_supabase_sync", 0);
        if (last <= 0) return "Aún no se ha sincronizado";
        return "Última actualización: "
                + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date(last));
    }

    private void supabaseHelpDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(iconButton(R.drawable.ic_action_back, v -> dialog.dismiss()), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView headerTitle = text("Cuenta y sincronización", 18, true, textColor);
        headerTitle.setGravity(Gravity.CENTER);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, dp(52), 1));
        header.addView(new View(this), new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(12), 0, dp(24));
        body.addView(text("Configurar Supabase", 23, true, textColor));
        body.addView(text("Sigue estos pasos una sola vez para activar la sincronización segura por cuenta.", 14, false, muted), margins(-1, -2, 0, 14));
        body.addView(supabaseStep("1", "Crear el proyecto", "Abre Supabase, crea un proyecto llamado Control Financiero, guarda la contraseña de la base de datos y elige la región más cercana."));
        body.addView(iconActionWide("Abrir Supabase", R.drawable.ic_action_open, v -> openSupabaseDashboard()));
        body.addView(supabaseStep("2", "Crear la tabla segura", "En SQL Editor pulsa New query, pega el script de configuración y selecciona Run."));
        body.addView(iconSmallButton("Copiar script SQL", R.drawable.ic_action_copy, v -> copySupabaseSql()), new LinearLayout.LayoutParams(-1, dp(52)));
        body.addView(supabaseStep("3", "Habilitar correo y contraseña", "En Authentication > Sign In / Providers abre Email y activa el proveedor. Para una prueba puedes desactivar Confirm email."));
        body.addView(supabaseStep("4", "Copiar la conexión", "En Connect o Settings > API Keys copia Project URL y Publishable key. Nunca copies Secret key ni service_role."));
        body.addView(supabaseStep("5", "Conectar tus dispositivos", "Regresa a la conexión, guarda la URL y la clave pública. Crea la cuenta una vez e inicia sesión con la misma cuenta en Android y web."));
        TextView ready = text("La configuración es correcta cuando Authentication > Users muestra tu correo y la tabla money_snapshots contiene una fila después de la primera subida.", 13, true, incomeColor);
        ready.setBackground(rounded(softIncome(), 14, 0, strokeColor));
        ready.setPadding(dp(14), dp(12), dp(14), dp(12));
        body.addView(ready, margins(-1, -2, 0, 12));
        body.addView(iconSmallButton("Volver a la conexión", R.drawable.ic_action_back, v -> {
            dialog.dismiss();
            supabaseDialog();
        }), new LinearLayout.LayoutParams(-1, dp(48)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
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

    private View supabaseStep(String number, String title, String description) {
        LinearLayout step = new LinearLayout(this);
        step.setGravity(Gravity.TOP);
        step.setPadding(dp(12), dp(12), dp(12), dp(12));
        step.setBackground(rounded(surface2, 14, 0, strokeColor));
        step.setLayoutParams(margins(-1, -2, 0, 8));
        TextView badge = text(number, 15, true, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(transferColor, 12, 0, transferColor));
        step.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        TextView titleView = text(title, 15, true, textColor);
        TextView descriptionView = text(description, 14, false, textColor);
        descriptionView.setPadding(0, dp(3), 0, 0);
        copy.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        copy.addView(descriptionView, new LinearLayout.LayoutParams(-1, -2));
        step.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return step;
    }

    private void openSupabaseDashboard() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SupabaseSetup.DASHBOARD_URL)));
        } catch (Exception ex) {
            toast("No se pudo abrir el navegador.");
        }
    }

    private void copySupabaseSql() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            toast("No se pudo acceder al portapapeles.");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Control Financiero - Supabase SQL", SupabaseSetup.SQL));
        toast("Script SQL copiado.");
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

    private void transactionDateDialog() {
        Calendar cal = calendarFrom(transactionAnchorDate == null ? db.latestDate() : transactionAnchorDate);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            transactionAnchorDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            period = transactionAnchorDate.substring(0, 7);
            if ("buscar".equals(transactionMode)) transactionMode = "diario";
            renderScreen();
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
        String[] labels = {"Anual", "Semestral", "Mensual", "Semanal", "Diario", "Todo"};
        String[] values = {"anual", "semestral", "mensual", "semanal", "diario", "todo"};
        int checked = 1;
        for (int i = 0; i < values.length; i++) if (values[i].equals(statsScope)) checked = i;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Filtrar estado")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    statsScope = values[which];
                    statsDetailAccount = null;
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
            statsDetailAccount = null;
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
        else if ("semestral".equals(statsScope)) cal.add(Calendar.MONTH, direction * 6);
        else if ("anual".equals(statsScope)) cal.add(Calendar.YEAR, direction);
        else cal.add(Calendar.MONTH, direction);
        statsAnchorDate = isoDate(cal);
        period = statsAnchorDate.substring(0, 7);
        statsDetailAccount = null;
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
        } else if ("semestral".equals(scope)) {
            int startMonth = anchor.get(Calendar.MONTH) < Calendar.JULY ? Calendar.JANUARY : Calendar.JULY;
            start.set(Calendar.MONTH, startMonth);
            start.set(Calendar.DAY_OF_MONTH, 1);
            end.set(Calendar.MONTH, startMonth + 5);
            end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
            SimpleDateFormat semesterMonth = new SimpleDateFormat("MMM", uiLocale());
            label = semesterMonth.format(start.getTime()) + " - " + semesterMonth.format(end.getTime()) + " " + anchor.get(Calendar.YEAR);
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
        if ("semestral".equals(scope)) return "Semestral";
        if ("semanal".equals(scope)) return "Semanal";
        if ("diario".equals(scope)) return "Diario";
        if ("todo".equals(scope)) return "Todo";
        return "Mensual";
    }

    private String isoDate(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private String shortDate(Calendar calendar) {
        return new SimpleDateFormat("dd MMM", uiLocale()).format(calendar.getTime());
    }

    private String longDate(Calendar calendar) {
        return new SimpleDateFormat("dd MMM yyyy", uiLocale()).format(calendar.getTime());
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
        String[] labels = {"Diario", "Semanal", "Mensual", "Semestral", "Anual", "Todo"};
        String[] values = {"diario", "semanal", "mensual", "semestral", "anual", "todo"};
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
        pendingStatusReport = false;
        String[] formats = {"XLSX moderno", "XLS compatible"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(pendingReportTitle)
                .setItems(formats, (d, which) -> createReportExport(which == 0 ? "xlsx" : "xls"))
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void prepareStatusReport() {
        DateRange range = statsRange();
        pendingReportStart = range.start;
        pendingReportEnd = range.end;
        pendingReportTitle = "Estado " + scopeLabel(statsScope) + " - " + range.label;
        pendingStatusReport = true;
        createReportExport("xlsx");
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
        if (importInProgress) {
            toast("Ya hay una importación en curso.");
            return;
        }
        final String fileName = displayName(uri);
        importInProgress = true;
        if (content != null) renderScreen();
        toast("Analizando archivo...");
        new Thread(() -> {
            MoneyDb previewDb = null;
            try {
                deleteDatabase(IMPORT_PREVIEW_DB);
                previewDb = new MoneyDb(this, IMPORT_PREVIEW_DB);
                String name = fileName.toLowerCase(Locale.US);
                ImportResult result;
                if (name.endsWith(".csv")) result = importCsv(uri, previewDb);
                else if (name.endsWith(".json")) result = importJson(uri, previewDb);
                else if (name.endsWith(".xlsx")) result = SpreadsheetExchange.importXlsx(this, uri, previewDb, currentCurrencyCode());
                else {
                    MmbakImporter importer = new MmbakImporter(this);
                    result = importer.importInto(uri, previewDb);
                }
                int duplicatesRemoved = previewDb.removeExactDuplicateTransactions();
                ImportPreview preview = ImportSafety.analyze(fileName, db, previewDb, duplicatesRemoved);
                MoneyDb.ImportBundle bundle = previewDb.exportImportBundle();
                previewDb.close();
                previewDb = null;
                deleteDatabase(IMPORT_PREVIEW_DB);
                runOnUiThread(() -> {
                    importInProgress = false;
                    showImportPreview(preview, bundle);
                    if (content != null) renderScreen();
                });
            } catch (Exception ex) {
                if (previewDb != null) previewDb.close();
                deleteDatabase(IMPORT_PREVIEW_DB);
                runOnUiThread(() -> {
                    importInProgress = false;
                    toast("No se pudo importar: " + ex.getMessage());
                    if (content != null) renderScreen();
                });
            }
        }).start();
    }

    private void showImportPreview(ImportPreview preview, MoneyDb.ImportBundle bundle) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(4), dp(14), dp(4));
        body.addView(text(preview.getFileName(), 14, true, textColor), margins(-1, -2, 0, 10));
        body.addView(previewMetric("Movimientos", String.valueOf(preview.getMovements())));
        body.addView(previewMetric("Transferencias", String.valueOf(preview.getTransfers())));
        body.addView(previewMetric("Cuentas", String.valueOf(preview.getAccounts())));
        body.addView(previewMetric("Categorías", String.valueOf(preview.getCategories())));
        if (!preview.getFirstDate().isEmpty()) {
            body.addView(previewMetric("Periodo", preview.getFirstDate() + "  →  " + preview.getLastDate()));
        }
        body.addView(previewMetric("Ingresos", money(preview.getIncome(), preview.getCurrencyCode())));
        body.addView(previewMetric("Gastos", money(preview.getExpense(), preview.getCurrencyCode())));
        if (preview.getDuplicatesRemoved() > 0) {
            body.addView(previewMetric("Duplicados omitidos", String.valueOf(preview.getDuplicatesRemoved())));
        }
        for (String warning : preview.getWarnings()) {
            TextView line = text("• " + warning, 12, false, warning.contains("sin una cuenta") ? expenseColor : muted);
            line.setPadding(0, dp(5), 0, 0);
            body.addView(line);
        }
        TextView replacement = text("Al confirmar se reemplazarán los datos locales. Se guardará una copia para poder deshacer la importación.", 12, false, muted);
        replacement.setPadding(0, dp(12), 0, 0);
        body.addView(replacement);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Vista previa de importación")
                .setView(scroll)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Importar ahora", (d, w) -> applyImportBundle(preview, bundle))
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private View previewMetric(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));
        row.addView(text(label, 13, false, muted), new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView amount = text(value, 13, true, textColor);
        amount.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(amount, new LinearLayout.LayoutParams(0, dp(34), 1));
        return row;
    }

    private void applyImportBundle(ImportPreview preview, MoneyDb.ImportBundle bundle) {
        importInProgress = true;
        if (content != null) renderScreen();
        toast("Importando datos...");
        new Thread(() -> {
            File recovery = importRecoveryFile();
            try {
                db.copyDatabaseTo(recovery, this);
                SharedPreferences.Editor recoveryPreferences = prefs.edit()
                        .putBoolean("last_import_had_currency", prefs.contains("currency_code"));
                if (prefs.contains("currency_code")) {
                    recoveryPreferences.putString("last_import_currency", prefs.getString("currency_code", "USD"));
                } else {
                    recoveryPreferences.remove("last_import_currency");
                }
                recoveryPreferences.apply();
                db.replaceFromImport(bundle.accounts, bundle.categories, bundle.transactions);
                runOnUiThread(() -> {
                    if (!prefs.contains("currency_code")) prefs.edit().putString("currency_code", db.primaryCurrency()).apply();
                    prefs.edit()
                            .putString("last_import_name", preview.getFileName())
                            .putInt("last_import_count", preview.getMovements())
                            .apply();
                    period = db.latestMonth();
                    statsAnchorDate = db.latestDate();
                    transactionAnchorDate = db.latestDate();
                    importInProgress = false;
                    markLocalDataChanged();
                    toast("Importado: " + preview.getMovements() + " movimientos");
                    draw();
                });
            } catch (Exception ex) {
                try {
                    if (recovery.isFile()) db.restoreDatabaseFrom(recovery, this);
                } catch (Exception ignored) {
                }
                recovery.delete();
                prefs.edit().remove("last_import_had_currency").remove("last_import_currency").apply();
                runOnUiThread(() -> {
                    importInProgress = false;
                    toast("No se pudo importar: " + ex.getMessage());
                    if (content != null) renderScreen();
                });
            }
        }).start();
    }

    private File importRecoveryFile() {
        return new File(getFilesDir(), IMPORT_RECOVERY_FILE);
    }

    private void confirmUndoLastImport() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Deshacer última importación")
                .setMessage("Se restaurarán las cuentas, categorías y movimientos que existían antes de la última importación.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Restaurar", (d, w) -> undoLastImport())
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void undoLastImport() {
        File recovery = importRecoveryFile();
        try {
            db.restoreDatabaseFrom(recovery, this);
            recovery.delete();
            SharedPreferences.Editor editor = prefs.edit()
                    .remove("last_import_name")
                    .remove("last_import_count");
            if (prefs.getBoolean("last_import_had_currency", false)) {
                editor.putString("currency_code", prefs.getString("last_import_currency", db.primaryCurrency()));
            } else {
                editor.remove("currency_code");
            }
            editor.remove("last_import_had_currency").remove("last_import_currency").apply();
            period = db.latestMonth();
            statsAnchorDate = db.latestDate();
            transactionAnchorDate = db.latestDate();
            markLocalDataChanged();
            draw();
            toast("Importación deshecha.");
        } catch (Exception ex) {
            toast("No se pudo restaurar: " + ex.getMessage());
        }
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
            toast("Datos exportados en: " + pendingExportFormat.toUpperCase(Locale.US));
        } catch (Exception ex) {
            toast("No se pudo exportar: " + ex.getMessage());
        }
    }

    private void exportReport(Uri uri) {
        try {
            List<MoneyDb.Row> rows = db.transactionsForDisplay(pendingReportStart, pendingReportEnd);
            if (pendingStatusReport) {
                SpreadsheetExchange.exportStatusXlsx(this, uri, rows, currentCurrencyCode(), pendingReportTitle);
            } else if ("xls".equals(pendingExportFormat)) {
                SpreadsheetExchange.exportXls(this, uri, rows, currentCurrencyCode(), pendingReportTitle);
            } else {
                SpreadsheetExchange.exportXlsx(this, uri, rows, currentCurrencyCode(), pendingReportTitle);
            }
            toast("Reporte generado: " + rows.size() + " transacciones");
            pendingStatusReport = false;
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

    private ImportResult importCsv(Uri uri, MoneyDb target) throws Exception {
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
        target.replaceFromImport(accounts, categories, txns);
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

    private ImportResult importJson(Uri uri, MoneyDb target) throws Exception {
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
        target.replaceFromImport(accounts, categories, txns);
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
        send.putExtra(Intent.EXTRA_TEXT, "Control Financiero: finanzas personales sin anuncios.");
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
        boolean automatic = hasZeroBalance(a);
        boolean hidden = isHiddenAccount(a);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(rounded(hidden ? surface2 : surface, 10, 0, strokeColor));
        row.setPadding(dp(10), dp(9), dp(10), dp(9));
        row.setLayoutParams(margins(-1, -2, 0, 1));

        String status = automatic ? "Saldo 0.00 · Oculta automáticamente" : a.hidden ? "Oculta manualmente" : displayAccountType(a.type);
        TextView name = text(a.name + "\n" + status, 13, false, hidden ? muted : textColor);
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
        box.setBackground(rounded(surface, 8, 1, strokeColor));
        box.setElevation(0);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setLayoutParams(margins(-1, -2, 0, 8));
        return box;
    }

    private LinearLayout compactPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded(surface, 8, 1, strokeColor));
        box.setPadding(dp(8), dp(10), dp(8), dp(10));
        box.setLayoutParams(margins(-1, -2, 0, 8));
        return box;
    }

    private TextView kpi(String label, String value, int color) {
        TextView v = text("", 13, false, color);
        label = ui(label);
        String full = label + "\n" + value;
        SpannableString span = new SpannableString(full);
        span.setSpan(new ForegroundColorSpan(muted), 0, label.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(color), label.length() + 1, full.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(Typeface.BOLD), label.length() + 1, full.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        v.setText(span);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(6), 0, dp(6), 0);
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
        b.setTextColor(transactionMode.equals(target) ? actionColor : muted);
        b.setTextSize(10);
        b.setBackground(rounded(transactionMode.equals(target) ? actionSoft : surface, 6, 1, strokeColor));
        return b;
    }

    private Button searchModeButton() {
        Button button = smallButton("Buscar", v -> searchDialog());
        boolean selected = "buscar".equals(transactionMode);
        Drawable icon = getResources().getDrawable(R.drawable.ic_action_search).mutate();
        icon.setTint(selected ? actionColor : muted);
        icon.setBounds(0, 0, dp(16), dp(16));
        button.setCompoundDrawables(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(3));
        button.setTextColor(selected ? actionColor : muted);
        button.setTextSize(11);
        button.setBackground(rounded(selected ? actionSoft : surface, 6, 1, strokeColor));
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
        if (!"stats".equals(target)) statsDetailAccount = null;
        draw();
    }

    private Button smallButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(ui(label));
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(textColor);
        b.setBackground(rounded(actionSoft, 6, 1, strokeColor));
        b.setOnClickListener(listener);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setIncludeFontPadding(false);
        b.setElevation(0);
        b.setStateListAnimator(null);
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
        b.setBackground(rounded(actionColor, 6, 0, actionColor));
        b.setLayoutParams(margins(-1, dp(52), 0, 8));
        return b;
    }

    private Button iconActionWide(String label, int drawableRes, View.OnClickListener listener) {
        Button button = actionWide(label, listener);
        setButtonIcon(button, drawableRes, Color.WHITE);
        return button;
    }

    private Button iconSmallButton(String label, int drawableRes, View.OnClickListener listener) {
        Button button = smallButton(label, listener);
        setButtonIcon(button, drawableRes, actionColor);
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setTextSize(12);
        button.setPadding(dp(10), dp(4), dp(10), dp(4));
        button.setMinHeight(dp(48));
        return button;
    }

    private void setButtonIcon(Button button, int drawableRes, int color) {
        Drawable icon = getResources().getDrawable(drawableRes).mutate();
        icon.setTint(color);
        icon.setBounds(0, 0, dp(19), dp(19));
        button.setCompoundDrawables(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(7));
        button.setGravity(Gravity.CENTER);
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
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setText(ui(textView.getText().toString()));
            if (!(view instanceof Button)) textView.setTextColor(textColor);
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
        int secondaryEnd = secondaryStart + secondary.length();
        span.setSpan(new ForegroundColorSpan(muted), secondaryStart, secondaryEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (note != null && !note.trim().isEmpty()) {
            int noteStart = secondaryEnd + 1;
            span.setSpan(new ForegroundColorSpan(textColor), noteStart, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new StyleSpan(Typeface.BOLD), noteStart, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new RelativeSizeSpan(1.06f), noteStart, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView v = new TextView(this);
        v.setText(ui(value));
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setLineSpacing(dp(2), 1);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setIncludeFontPadding(false);
        return v;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(ui(hint));
        e.setSingleLine(true);
        e.setTextColor(textColor);
        e.setHintTextColor(muted);
        e.setTextSize(14);
        e.setBackground(rounded(controlSurface(), 16, 1, strokeColor));
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setLayoutParams(margins(-1, dp(48), 0, 8));
        return e;
    }

    private AutoCompleteTextView noteInput(List<String> suggestions) {
        AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setHint(ui("Escribe para buscar notas anteriores"));
        input.setSingleLine(true);
        input.setTextColor(textColor);
        input.setHintTextColor(muted);
        input.setTextSize(14);
        input.setBackground(rounded(controlSurface(), 16, 1, strokeColor));
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setLayoutParams(margins(-1, dp(48), 0, 8));
        input.setThreshold(1);
        input.setDropDownVerticalOffset(dp(4));
        input.setDropDownBackgroundDrawable(rounded(surface, 12, 1, strokeColor));
        input.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, suggestions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getView(position, convertView, parent);
                item.setTextColor(textColor);
                item.setTextSize(14);
                item.setSingleLine(true);
                item.setPadding(dp(14), dp(12), dp(14), dp(12));
                item.setBackgroundColor(surface);
                return item;
            }
        });
        return input;
    }

    private LinearLayout.LayoutParams movementTypeParams(boolean last) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(0, 0, last ? 0 : dp(6), dp(10));
        return params;
    }

    private void styleMovementTypeButtons(Button[] buttons, int selected) {
        for (int i = 0; i < buttons.length; i++) {
            boolean active = i == selected;
            int color = i == 0 ? expenseColor : (i == 1 ? incomeColor : transferColor);
            buttons[i].setTextColor(active ? Color.WHITE : muted);
            buttons[i].setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            buttons[i].setBackground(rounded(active ? color : controlSurface(), 8, 1, active ? color : strokeColor));
        }
    }

    private void applyMovementType(int position, Button[] buttons, TextView accountLabel, TextView accountTypeLabel,
                                   Spinner accountType, TextView toAccountTypeLabel, Spinner toAccountType,
                                   TextView toAccountLabel, Spinner toAccount, TextView categoryLabel, Spinner category) {
        MovementFormRule rule = MovementFormRules.forPosition(position);
        accountLabel.setText(ui(rule.accountLabel));
        accountTypeLabel.setText(ui(rule.showDestination ? "Tipo de origen" : "Tipo de cuenta"));
        accountType.setVisibility(View.VISIBLE);
        toAccountTypeLabel.setVisibility(rule.showDestination ? View.VISIBLE : View.GONE);
        toAccountType.setVisibility(rule.showDestination ? View.VISIBLE : View.GONE);
        toAccountLabel.setVisibility(rule.showDestination ? View.VISIBLE : View.GONE);
        toAccount.setVisibility(rule.showDestination ? View.VISIBLE : View.GONE);
        categoryLabel.setVisibility(rule.showCategory ? View.VISIBLE : View.GONE);
        category.setVisibility(rule.showCategory ? View.VISIBLE : View.GONE);
        styleMovementTypeButtons(buttons, position);
        if (rule.showCategory) {
            category.setAdapter(stringAdapter(db.categories(rule.kind)));
        }
    }

    private TextView label(String value) {
        TextView v = text(value, 12, true, muted);
        v.setPadding(0, dp(8), 0, 0);
        return v;
    }

    private CheckBox checkbox(String label, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(ui(label));
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

    private List<AccountSelectionOption> movementAccountChoices() {
        List<AccountSelectionOption> choices = new ArrayList<>();
        for (MoneyDb.AccountTotal account : db.accountTotals()) {
            choices.add(new AccountSelectionOption(
                    account.name,
                    displayAccountType(account.type),
                    AccountSelectionRules.isActive(account.balance, account.hidden)
            ));
        }
        Collections.sort(choices, Comparator.comparingInt((AccountSelectionOption choice) -> choice.active ? 0 : 1)
                .thenComparing(choice -> choice.name.toLowerCase(Locale.US)));
        return choices;
    }

    private Spinner accountSpinner(List<AccountSelectionOption> values) {
        Spinner spinner = new Spinner(this);
        setAccountSpinnerChoices(spinner, values, null);
        spinner.setBackground(rounded(controlSurface(), 16, 1, strokeColor));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setLayoutParams(margins(-1, dp(48), 0, 8));
        return spinner;
    }

    private void setAccountSpinnerChoices(Spinner spinner, List<AccountSelectionOption> source, String preferred) {
        List<AccountSelectionOption> values = new ArrayList<>(source);
        if (values.isEmpty()) values.add(new AccountSelectionOption("Sin cuentas", "", false));
        ArrayAdapter<AccountSelectionOption> adapter = new ArrayAdapter<AccountSelectionOption>(this, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getView(position, convertView, parent);
                styleAccountSpinnerText(item, getItem(position), false);
                return item;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getDropDownView(position, convertView, parent);
                styleAccountSpinnerText(item, getItem(position), true);
                return item;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (preferred != null) setSpinnerSelection(spinner, preferred);
    }

    private List<AccountSelectionOption> accountChoicesForType(List<AccountSelectionOption> choices, String type) {
        List<AccountSelectionOption> filtered = new ArrayList<>();
        for (AccountSelectionOption choice : choices) {
            if (choice.type.equals(type)) filtered.add(choice);
        }
        return filtered;
    }

    private String accountTypeForName(List<AccountSelectionOption> choices, String accountName, String fallback) {
        for (AccountSelectionOption choice : choices) {
            if (choice.name.equals(accountName)) return choice.type;
        }
        return fallback;
    }

    private void styleAccountSpinnerText(TextView item, AccountSelectionOption account, boolean dropdown) {
        String status = ui(account.active ? "Activa" : "Inactiva");
        String value = account.name + "  ·  " + status;
        SpannableString styled = new SpannableString(value);
        int statusStart = value.length() - status.length();
        styled.setSpan(new ForegroundColorSpan(account.active ? actionColor : muted), statusStart, value.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new RelativeSizeSpan(0.86f), statusStart, value.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (account.active) {
            styled.setSpan(new StyleSpan(Typeface.BOLD), 0, account.name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            styled.setSpan(new ForegroundColorSpan(muted), 0, account.name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        item.setText(styled);
        item.setTextColor(account.active ? textColor : muted);
        item.setTextSize(14);
        item.setSingleLine(true);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), dropdown ? dp(13) : 0, dp(12), dropdown ? dp(13) : 0);
        item.setAlpha(account.active ? 1f : 0.72f);
        if (dropdown) item.setBackgroundColor(account.active ? actionSoft : surface);
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
        item.setText(ui(item.getText().toString()));
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

    private void pickTime(EditText target) {
        Calendar cal = Calendar.getInstance();
        try {
            String[] parts = target.getText().toString().split(":");
            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
            cal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
        } catch (Exception ignored) {
        }
        TimePickerDialog dialog = new TimePickerDialog(this, (view, hour, minute) -> {
            target.setText(String.format(Locale.US, "%02d:%02d", hour, minute));
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true);
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
            Calendar calendar = calendarFrom(value + "-01");
            return new SimpleDateFormat("MMM yyyy", uiLocale()).format(calendar.getTime());
        } catch (Exception ignored) {
            return value;
        }
    }

    private Locale uiLocale() {
        if ("en".equals(language)) return Locale.US;
        if ("pt".equals(language)) return new Locale("pt", "BR");
        if ("fr".equals(language)) return Locale.FRANCE;
        return new Locale("es", "PE");
    }

    private String money(double value) {
        return money(value, currentCurrencyCode());
    }

    private String money(double value, String currencyCode) {
        return String.format(Locale.US, "%s%,.2f", currencySymbol(currencyCode), value);
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
        Toast.makeText(this, ui(value), Toast.LENGTH_LONG).show();
    }

    private String ui(String value) {
        return UiTranslations.translate(value == null ? "" : value, language);
    }
}
