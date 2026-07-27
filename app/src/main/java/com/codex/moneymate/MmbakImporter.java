package com.codex.moneymate;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MmbakImporter {
    private final Context context;

    MmbakImporter(Context context) {
        this.context = context;
    }

    ImportResult importInto(Uri uri, MoneyDb target) throws Exception {
        File temp = copyToTemp(uri);
        List<ImportedAccount> accounts = new ArrayList<>();
        List<ImportedCategory> categories = new ArrayList<>();
        List<ImportedTxn> txns = new ArrayList<>();

        SQLiteDatabase db = SQLiteDatabase.openDatabase(temp.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            if (!readKnownMmbakModern(db, accounts, categories, txns)) {
                for (String table : tables(db)) {
                    List<String> cols = columns(db, table);
                    String tableLower = table.toLowerCase(Locale.US);
                    if (looksLikeAccountTable(tableLower, cols)) readAccounts(db, table, cols, accounts);
                    if (looksLikeCategoryTable(tableLower, cols)) readCategories(db, table, cols, categories);
                    if (looksLikeTransactionTable(tableLower, cols)) readTransactions(db, table, cols, txns);
                }
            }
        } finally {
            db.close();
            temp.delete();
        }

        dedupeAccounts(accounts);
        ensureCategoriesForTransactions(categories, txns);
        target.replaceFromImport(accounts, categories, txns);
        return new ImportResult(accounts.size(), categories.size(), txns.size());
    }

    String displayName(Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return "backup.mmbak";
    }

    private boolean readKnownMmbakModern(SQLiteDatabase db, List<ImportedAccount> accounts, List<ImportedCategory> categories, List<ImportedTxn> txns) {
        if (!tableExists(db, "ASSETS") || !tableExists(db, "ZCATEGORY") || !tableExists(db, "INOUTCOME")) return false;

        Map<String, String> currencyByUid = new HashMap<>();
        if (tableExists(db, "CURRENCY")) {
            try (Cursor c = db.rawQuery("SELECT uid, COALESCE(ISO, NAME) FROM CURRENCY WHERE COALESCE(IS_DEL,0)=0", null)) {
                while (c.moveToNext()) currencyByUid.put(c.getString(0), c.getString(1));
            } catch (Exception ignored) {
            }
        }

        Map<String, String> accountByUid = new HashMap<>();
        Map<String, String> groupByUid = new HashMap<>();
        if (tableExists(db, "ASSETGROUP")) {
            try (Cursor c = db.rawQuery("SELECT uid, ACC_GROUP_NAME FROM ASSETGROUP", null)) {
                while (c.moveToNext()) groupByUid.put(c.getString(0), c.getString(1));
            } catch (Exception ignored) {
            }
        }
        try (Cursor c = db.rawQuery("SELECT uid, NIC_NAME, currencyUid FROM ASSETS ORDER BY ORDERSEQ, ID", null)) {
            while (c.moveToNext()) {
                String uid = c.getString(0);
                String name = c.getString(1);
                String groupName = groupByUid.get(uid);
                if (groupName != null && groupName.equals(name)) continue;
                String currencyUid = c.getString(2);
                String currency = currencyByUid.containsKey(currencyUid) ? currencyByUid.get(currencyUid) : "PEN";
                accountByUid.put(uid, name);
                accounts.add(new ImportedAccount(name, 0, currency));
            }
        } catch (Exception ex) {
            return false;
        }

        Map<String, ImportedCategory> categoryByUid = new HashMap<>();
        try (Cursor c = db.rawQuery("SELECT uid, NAME, TYPE FROM ZCATEGORY WHERE COALESCE(C_IS_DEL,0)=0 ORDER BY ORDERSEQ, ID", null)) {
            while (c.moveToNext()) {
                String uid = c.getString(0);
                String kind = c.getInt(2) == 0 ? "income" : "expense";
                ImportedCategory category = new ImportedCategory(c.getString(1), kind, null);
                categoryByUid.put(uid, category);
                categories.add(category);
            }
        } catch (Exception ex) {
            return false;
        }

        try (Cursor c = db.rawQuery("SELECT assetUid, toAssetUid, ctgUid, ZDATE, WDATE, wtime, DO_TYPE, ZMONEY, ZCONTENT FROM INOUTCOME WHERE COALESCE(IS_DEL,0)=0 ORDER BY WDATE, CAST(ZDATE AS INTEGER), AID", null)) {
            while (c.moveToNext()) {
                String fromUid = c.getString(0);
                String toUid = c.getString(1);
                String ctgUid = c.getString(2);
                String zDate = c.getString(3);
                String date = dateFrom(firstNonEmpty(c.getString(4), zDate));
                String time = timeFrom(firstNonEmpty(c.getString(5), zDate));
                String doType = c.getString(6);
                double amount = num(c, "ZMONEY");
                String note = c.getString(8);
                ImportedCategory category = categoryByUid.get(ctgUid);
                if ("0".equals(doType)) {
                    String categoryName = category == null ? "Ingreso" : category.name;
                    txns.add(new ImportedTxn(date, time, nameFor(accountByUid, fromUid), categoryName, "income", amount, note));
                } else if ("1".equals(doType)) {
                    String categoryName = category == null ? "Gasto" : category.name;
                    txns.add(new ImportedTxn(date, time, nameFor(accountByUid, fromUid), categoryName, "expense", amount, note));
                } else if ("3".equals(doType)) {
                    txns.add(new ImportedTxn(date, time, nameFor(accountByUid, fromUid), "Transferencia", "expense", amount, note));
                } else if ("4".equals(doType)) {
                    txns.add(new ImportedTxn(date, time, nameFor(accountByUid, fromUid), "Transferencia", "income", amount, note));
                }
            }
        } catch (Exception ex) {
            return false;
        }

        return true;
    }

    private File copyToTemp(Uri uri) throws Exception {
        File temp = File.createTempFile("mmbak-", ".mmbak", context.getCacheDir());
        try (InputStream in = context.getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(temp)) {
            if (in == null) throw new IllegalArgumentException("No se pudo abrir el archivo.");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        return temp;
    }

    private List<String> tables(SQLiteDatabase db) {
        List<String> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", null)) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    private boolean tableExists(SQLiteDatabase db, String table) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1", new String[]{table})) {
            return c.moveToFirst();
        }
    }

    private List<String> columns(SQLiteDatabase db, String table) {
        List<String> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + quoteIdent(table) + ")", null)) {
            int idx = c.getColumnIndex("name");
            while (c.moveToNext()) out.add(c.getString(idx));
        }
        return out;
    }

    private boolean looksLikeAccountTable(String table, List<String> cols) {
        return table.contains("asset") || table.contains("account") || (hasAny(cols, "balance", "amount") && hasAny(cols, "name", "title"));
    }

    private boolean looksLikeCategoryTable(String table, List<String> cols) {
        return table.contains("category") || table.contains("categ") || (hasAny(cols, "name", "title") && hasAny(cols, "type", "kind"));
    }

    private boolean looksLikeTransactionTable(String table, List<String> cols) {
        if (table.contains("category") || table.contains("asset") || table.contains("account") || table.contains("budget")) return false;
        return hasAny(cols, "amount", "price", "money", "cost") && hasAny(cols, "date", "time", "day");
    }

    private void readAccounts(SQLiteDatabase db, String table, List<String> cols, List<ImportedAccount> out) {
        String name = pick(cols, "name", "title", "assetname", "accountname");
        String balance = pick(cols, "balance", "amount", "sum", "total");
        String currency = pick(cols, "currency", "unit", "symbol");
        if (name == null) return;
        try (Cursor c = db.rawQuery("SELECT * FROM " + quoteIdent(table) + " LIMIT 500", null)) {
            Set<String> seen = new HashSet<>();
            while (c.moveToNext()) {
                String n = str(c, name);
                if (n == null || seen.contains(n)) continue;
                seen.add(n);
                out.add(new ImportedAccount(n, num(c, balance), str(c, currency)));
            }
        } catch (Exception ignored) {
        }
    }

    private void readCategories(SQLiteDatabase db, String table, List<String> cols, List<ImportedCategory> out) {
        String name = pick(cols, "name", "title", "categoryname", "categname");
        String type = pick(cols, "kind", "type", "classification");
        if (name == null) return;
        try (Cursor c = db.rawQuery("SELECT * FROM " + quoteIdent(table) + " LIMIT 1000", null)) {
            Set<String> seen = new HashSet<>();
            while (c.moveToNext()) {
                String n = str(c, name);
                if (n == null || seen.contains(n)) continue;
                seen.add(n);
                out.add(new ImportedCategory(n, kindFrom(str(c, type), -1), null));
            }
        } catch (Exception ignored) {
        }
    }

    private void readTransactions(SQLiteDatabase db, String table, List<String> cols, List<ImportedTxn> out) {
        String amount = pick(cols, "amount", "price", "money", "cost");
        String date = pick(cols, "date", "datetime", "time", "day");
        String account = pick(cols, "account", "accountname", "asset", "assetname", "card");
        String category = pick(cols, "category", "categoryname", "categ", "categname");
        String type = pick(cols, "kind", "type", "classification", "incomeexpense");
        String note = pick(cols, "note", "memo", "contents", "description");
        if (amount == null || date == null) return;
        try (Cursor c = db.rawQuery("SELECT * FROM " + quoteIdent(table), null)) {
            while (c.moveToNext()) {
                double value = num(c, amount);
                if (value == 0) continue;
                out.add(new ImportedTxn(dateFrom(str(c, date)), str(c, account), str(c, category), kindFrom(str(c, type), value), value, str(c, note)));
            }
        } catch (Exception ignored) {
        }
    }

    private void ensureCategoriesForTransactions(List<ImportedCategory> categories, List<ImportedTxn> txns) {
        Set<String> names = new HashSet<>();
        for (ImportedCategory c : categories) names.add(c.name);
        for (ImportedTxn t : txns) {
            if (!names.contains(t.category)) {
                names.add(t.category);
                categories.add(new ImportedCategory(t.category, t.kind, null));
            }
        }
    }

    private void dedupeAccounts(List<ImportedAccount> accounts) {
        Map<String, ImportedAccount> unique = new LinkedHashMap<>();
        for (ImportedAccount account : accounts) {
            String key = account.name.trim()
                    .replaceAll("\\s+", " ")
                    .replaceAll("\\s*/\\s*", "/")
                    .toLowerCase(Locale.US);
            if (!unique.containsKey(key)) unique.put(key, account);
        }
        accounts.clear();
        accounts.addAll(unique.values());
    }

    private boolean hasAny(List<String> cols, String... needles) {
        return pick(cols, needles) != null;
    }

    private String pick(List<String> cols, String... needles) {
        for (String n : needles) {
            for (String c : cols) {
                String simple = c.toLowerCase(Locale.US).replace("_", "").replace("-", "");
                if (simple.equals(n) || simple.contains(n)) return c;
            }
        }
        return null;
    }

    private String str(Cursor c, String col) {
        if (col == null) return null;
        int idx = c.getColumnIndex(col);
        if (idx < 0 || c.isNull(idx)) return null;
        return String.valueOf(c.getString(idx));
    }

    private double num(Cursor c, String col) {
        if (col == null) return 0;
        int idx = c.getColumnIndex(col);
        if (idx < 0 || c.isNull(idx)) return 0;
        try {
            return c.getDouble(idx);
        } catch (Exception ex) {
            try {
                return Double.parseDouble(c.getString(idx).replace(",", ""));
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    private String kindFrom(String raw, double amount) {
        if (raw != null) {
            String v = raw.toLowerCase(Locale.US);
            if (v.contains("income") || v.contains("in") || v.equals("1") || v.equals("plus")) return "income";
            if (v.contains("expense") || v.contains("out") || v.equals("0") || v.equals("minus")) return "expense";
        }
        return amount < 0 ? "expense" : "income";
    }

    private String dateFrom(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "1970-01-01";
        String v = raw.trim();
        if (v.matches("\\d{4}-\\d{2}-\\d{2}.*")) return v.substring(0, 10);
        if (v.matches("\\d{8}.*")) return v.substring(0, 4) + "-" + v.substring(4, 6) + "-" + v.substring(6, 8);
        if (v.matches("\\d{10,13}")) {
            long millis = Long.parseLong(v);
            if (v.length() == 10) millis *= 1000;
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            return fmt.format(new java.util.Date(millis));
        }
        return v.length() > 10 ? v.substring(0, 10) : v;
    }

    private String timeFrom(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "00:00";
        String v = raw.trim();
        if (v.matches("\\d{10,13}")) {
            long millis = Long.parseLong(v);
            if (v.length() == 10) millis *= 1000;
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm", Locale.US);
            return fmt.format(new java.util.Date(millis));
        }
        if (v.matches("\\d{4}-\\d{2}-\\d{2}.+\\d{2}:\\d{2}.*")) {
            return v.substring(11, 16);
        }
        return "00:00";
    }

    private String firstNonEmpty(String preferred, String fallback) {
        return preferred != null && !preferred.trim().isEmpty() ? preferred : fallback;
    }

    private String nameFor(Map<String, String> names, String uid) {
        String name = names.get(uid);
        return name == null || name.trim().isEmpty() ? "Cuenta" : name;
    }

    private String quoteIdent(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
