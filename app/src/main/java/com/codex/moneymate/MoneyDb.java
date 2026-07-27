package com.codex.moneymate;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class MoneyDb extends SQLiteOpenHelper {
    static final String DB_NAME = "moneymate.sqlite";
    private static final int VERSION = 7;

    MoneyDb(Context context) {
        super(context, DB_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, balance REAL NOT NULL DEFAULT 0, currency TEXT NOT NULL DEFAULT 'USD', type TEXT NOT NULL DEFAULT 'Cuentas de Banco', description TEXT, include_total INTEGER NOT NULL DEFAULT 1, hidden INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, kind TEXT NOT NULL, color TEXT NOT NULL DEFAULT '#0E8F70')");
        db.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, time TEXT NOT NULL DEFAULT '00:00', account TEXT NOT NULL, category TEXT NOT NULL, kind TEXT NOT NULL, amount REAL NOT NULL, note TEXT, description TEXT, transfer_ref TEXT)");
        db.execSQL("CREATE TABLE budgets(id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL, month TEXT NOT NULL, amount REAL NOT NULL)");
        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE transactions ADD COLUMN time TEXT NOT NULL DEFAULT '00:00'");
            } catch (Exception ignored) {
            }
            try {
                db.execSQL("ALTER TABLE transactions ADD COLUMN description TEXT");
            } catch (Exception ignored) {
            }
        }
        if (oldVersion < 3) {
            addColumn(db, "accounts", "type TEXT NOT NULL DEFAULT 'Cuentas de Banco'");
            addColumn(db, "accounts", "description TEXT");
            addColumn(db, "accounts", "include_total INTEGER NOT NULL DEFAULT 1");
            addColumn(db, "accounts", "hidden INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 5) {
            normalizeAccountGroups(db);
        }
        if (oldVersion < 6) {
            addColumn(db, "transactions", "transfer_ref TEXT");
            backfillTransferRefs(db);
        }
        if (oldVersion < 7) {
            dedupeAccounts(db);
        }
    }

    void seed(SQLiteDatabase db) {
        db.execSQL("INSERT INTO accounts(name,balance,currency,type,description,include_total,hidden) VALUES('Efectivo',0,'USD','Efectivo','',1,0),('Cuenta',0,'USD','Cuentas de Banco','',1,0)");
        db.execSQL("INSERT INTO categories(name,kind,color) VALUES('Comida','expense','#D94A4A'),('Transporte','expense','#DB8A35'),('Hogar','expense','#4067B2'),('Salario','income','#168A5A')");
    }

    Summary summary() {
        return summary(null);
    }

    Summary summary(String month) {
        String start = month == null ? null : month + "-01";
        String end = month == null ? null : month + "-31";
        return summaryBetween(start, end);
    }

    Summary summaryBetween(String startDate, String endDate) {
        SQLiteDatabase db = getReadableDatabase();
        double income = sumBetween(db, "income", startDate, endDate);
        double expense = sumBetween(db, "expense", startDate, endDate);
        int count = transactionsForDisplay(startDate, endDate).size();
        return new Summary(income, expense, income - expense, count);
    }

    String latestMonth() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT substr(date,1,7) FROM transactions ORDER BY date DESC,time DESC,id DESC LIMIT 1", null)) {
            return c.moveToFirst() ? c.getString(0) : new java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(new java.util.Date());
        }
    }

    String latestDate() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT date FROM transactions ORDER BY date DESC,time DESC,id DESC LIMIT 1", null)) {
            return c.moveToFirst() ? c.getString(0) : new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        }
    }

    List<String> accounts() {
        return accounts(false);
    }

    List<String> accounts(boolean includeHidden) {
        if (includeHidden) {
            return strings("SELECT name FROM accounts ORDER BY CASE WHEN COALESCE(type,'')='Efectivo' THEN 0 ELSE 1 END,name", "name");
        }
        return strings("SELECT name FROM accounts WHERE COALESCE(hidden,0)=0 ORDER BY CASE WHEN COALESCE(type,'')='Efectivo' THEN 0 ELSE 1 END,name", "name");
    }

    List<AccountOption> accountOptions() {
        SQLiteDatabase db = getReadableDatabase();
        List<AccountOption> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT id,name,currency,COALESCE(type,'Cuentas de Banco'),COALESCE(balance,0),COALESCE(description,''),COALESCE(include_total,1),COALESCE(hidden,0) FROM accounts ORDER BY CASE WHEN COALESCE(type,'')='Efectivo' THEN 0 ELSE 1 END,type,name", null)) {
            while (c.moveToNext()) out.add(new AccountOption(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(4), c.getString(3), c.getString(5), c.getInt(6) == 1, c.getInt(7) == 1));
        }
        return out;
    }

    String primaryCurrency() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT currency FROM accounts GROUP BY currency ORDER BY COUNT(*) DESC LIMIT 1", null)) {
            return c.moveToFirst() ? c.getString(0) : "USD";
        }
    }

    List<String> categories(String kind) {
        SQLiteDatabase db = getReadableDatabase();
        List<String> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT name FROM categories WHERE kind=? ORDER BY name", new String[]{kind})) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    List<CategoryOption> categoryOptions(String kind) {
        SQLiteDatabase db = getReadableDatabase();
        List<CategoryOption> out = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT id,name,kind FROM categories WHERE kind=? ORDER BY name", new String[]{kind})) {
            while (c.moveToNext()) out.add(new CategoryOption(c.getLong(0), c.getString(1), c.getString(2)));
        }
        return out;
    }

    List<Row> transactions() {
        return transactions(null);
    }

    List<Row> transactions(String month) {
        String start = month == null ? null : month + "-01";
        String end = month == null ? null : month + "-31";
        return rawTransactions(start, end, 500);
    }

    List<Row> transactionsForAccount(String account, String datePrefix) {
        String start = datePrefix == null ? null : datePrefix + "-01";
        String end = datePrefix == null ? null : datePrefix + "-31";
        return transactionsForDisplayAccount(account, start, end);
    }

    List<Row> allTransactions() {
        List<Row> rows = rawTransactions(null, null, 0);
        java.util.Collections.reverse(rows);
        return rows;
    }

    List<String> recentNotes() {
        Map<String, String> unique = new LinkedHashMap<>();
        for (Row row : rawTransactions(null, null, 0)) {
            String note = row.note == null ? "" : row.note.trim();
            if (note.isEmpty()) continue;
            String key = note.toLowerCase(Locale.ROOT);
            if (!unique.containsKey(key)) unique.put(key, note);
            if (unique.size() >= 100) break;
        }
        return new ArrayList<>(unique.values());
    }

    List<Row> transactionsForDisplay(String startDate, String endDate) {
        return collapseTransfers(rawTransactions(startDate, endDate, 0));
    }

    List<Row> transactionsForDisplayAccount(String account, String startDate, String endDate) {
        List<Row> out = new ArrayList<>();
        for (Row row : transactionsForDisplay(startDate, endDate)) {
            if (row.isTransfer()) {
                if (account.equals(row.transferFrom) || account.equals(row.transferTo)) out.add(row);
            } else if (account.equals(row.account)) {
                out.add(row);
            }
        }
        return out;
    }

    private List<Row> rawTransactions(String startDate, String endDate, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        List<Row> rows = new ArrayList<>();
        String sql = "SELECT id,date,COALESCE(time,'00:00'),account,category,kind,amount,COALESCE(note,''),COALESCE(description,''),COALESCE(transfer_ref,'') FROM transactions";
        List<String> args = new ArrayList<>();
        if (startDate != null && endDate != null) {
            sql += " WHERE date>=? AND date<=?";
            args.add(startDate);
            args.add(endDate);
        }
        sql += " ORDER BY date DESC,time DESC,id DESC";
        if (limit > 0) sql += " LIMIT " + limit;
        try (Cursor c = db.rawQuery(sql, args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                rows.add(new Row(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getDouble(6), c.getString(7), c.getString(8), c.getString(9)));
            }
        }
        return rows;
    }

    private List<Row> collapseTransfers(List<Row> rows) {
        List<Row> out = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Row row : rows) {
            if (used.contains(row.id)) continue;
            if (!row.isTransferPart()) {
                out.add(row);
                continue;
            }
            Row pair = findTransferPair(row, rows, used);
            if (pair == null) {
                out.add(row.asUnpairedTransfer());
                used.add(row.id);
                continue;
            }
            Row expense = "expense".equals(row.kind) ? row : pair;
            Row income = "income".equals(row.kind) ? row : pair;
            out.add(Row.transfer(expense, income));
            used.add(expense.id);
            used.add(income.id);
        }
        return out;
    }

    private Row findTransferPair(Row row, List<Row> rows, Set<Long> used) {
        Row best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Row candidate : rows) {
            if (candidate.id == row.id || used.contains(candidate.id) || !candidate.isTransferPart()) continue;
            if (candidate.kind.equals(row.kind)) continue;
            if (!sameTransfer(row, candidate)) continue;
            long distance = Math.abs(candidate.id - row.id);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean sameTransfer(Row left, Row right) {
        if (!left.transferRef.isEmpty() && !right.transferRef.isEmpty()) return left.transferRef.equals(right.transferRef);
        return left.date.equals(right.date)
                && left.time.equals(right.time)
                && Math.abs(left.amount - right.amount) < 0.005
                && left.note.equals(right.note)
                && left.description.equals(right.description);
    }

    List<AccountTotal> accountTotals() {
        SQLiteDatabase db = getReadableDatabase();
        List<AccountTotal> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "SELECT a.id,a.name,a.currency,COALESCE(a.balance,0),COALESCE(a.type,'Cuentas de Banco'),COALESCE(a.description,''),COALESCE(a.include_total,1),COALESCE(a.hidden,0)," +
                        "COALESCE(SUM(CASE WHEN t.kind='income' THEN t.amount ELSE 0 END),0)," +
                        "COALESCE(SUM(CASE WHEN t.kind='expense' THEN t.amount ELSE 0 END),0) " +
                        "FROM accounts a LEFT JOIN transactions t ON t.account=a.name " +
                        "GROUP BY a.id,a.name,a.currency,a.balance,a.type,a.description,a.include_total,a.hidden ORDER BY CASE WHEN COALESCE(a.type,'')='Efectivo' THEN 0 ELSE 1 END,a.type,a.name", null)) {
            while (c.moveToNext()) {
                double start = c.getDouble(3);
                double income = c.getDouble(8);
                double expense = c.getDouble(9);
                out.add(new AccountTotal(c.getLong(0), c.getString(1), c.getString(2), start + income - expense, income, expense, c.getString(4), c.getString(5), c.getInt(6) == 1, c.getInt(7) == 1));
            }
        }
        return out;
    }

    List<Bar> categoryTotals(String kind) {
        return categoryTotals(kind, null);
    }

    List<Bar> categoryTotals(String kind, String month) {
        String start = month == null ? null : month + "-01";
        String end = month == null ? null : month + "-31";
        return categoryTotalsBetween(kind, start, end);
    }

    List<Bar> categoryTotalsBetween(String kind, String startDate, String endDate) {
        SQLiteDatabase db = getReadableDatabase();
        List<Bar> rows = new ArrayList<>();
        String sql = "SELECT category,SUM(amount) FROM transactions WHERE kind=? AND category<>'Transferencia'";
        List<String> args = new ArrayList<>();
        args.add(kind);
        if (startDate != null && endDate != null) {
            sql += " AND date>=? AND date<=?";
            args.add(startDate);
            args.add(endDate);
        }
        sql += " GROUP BY category ORDER BY SUM(amount) DESC LIMIT 8";
        try (Cursor c = db.rawQuery(sql, args.toArray(new String[0]))) {
            while (c.moveToNext()) rows.add(new Bar(c.getString(0), c.getDouble(1)));
        }
        return rows;
    }

    void addTransaction(String date, String account, String category, String kind, double amount, String note) {
        addTransaction(date, "00:00", account, category, kind, amount, note, "");
    }

    void addTransaction(String date, String time, String account, String category, String kind, double amount, String note, String description) {
        getWritableDatabase().execSQL(
                "INSERT INTO transactions(date,time,account,category,kind,amount,note,description) VALUES(?,?,?,?,?,?,?,?)",
                new Object[]{date, time, account, category, kind, amount, note, description}
        );
    }

    void addTransfer(String date, String time, String from, String to, double amount, String note, String description) {
        SQLiteDatabase db = getWritableDatabase();
        String ref = UUID.randomUUID().toString();
        db.beginTransaction();
        try {
            insertTransaction(db, date, time, from, "Transferencia", "expense", amount, note, description, ref);
            insertTransaction(db, date, time, to, "Transferencia", "income", amount, note, description, ref);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void updateTransaction(long id, String date, String time, String account, String category, String kind, double amount, String note, String description) {
        getWritableDatabase().execSQL(
                "UPDATE transactions SET date=?,time=?,account=?,category=?,kind=?,amount=?,note=?,description=? WHERE id=?",
                new Object[]{date, time, account, category, kind, amount, note, description, id}
        );
    }

    void deleteTransaction(long id) {
        getWritableDatabase().delete("transactions", "id=?", new String[]{String.valueOf(id)});
    }

    void updateTransfer(Row row, String date, String time, String from, String to, double amount, String note, String description) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (!row.transferRef.isEmpty()) {
                db.execSQL("UPDATE transactions SET date=?,time=?,account=?,amount=?,note=?,description=? WHERE transfer_ref=? AND kind='expense'",
                        new Object[]{date, time, from, amount, note, description, row.transferRef});
                db.execSQL("UPDATE transactions SET date=?,time=?,account=?,amount=?,note=?,description=? WHERE transfer_ref=? AND kind='income'",
                        new Object[]{date, time, to, amount, note, description, row.transferRef});
            } else {
                String ref = UUID.randomUUID().toString();
                db.execSQL("UPDATE transactions SET date=?,time=?,account=?,category='Transferencia',kind='expense',amount=?,note=?,description=?,transfer_ref=? WHERE id=?",
                        new Object[]{date, time, from, amount, note, description, ref, row.id});
                if (row.linkedId > 0) {
                    db.execSQL("UPDATE transactions SET date=?,time=?,account=?,category='Transferencia',kind='income',amount=?,note=?,description=?,transfer_ref=? WHERE id=?",
                            new Object[]{date, time, to, amount, note, description, ref, row.linkedId});
                } else {
                    insertTransaction(db, date, time, to, "Transferencia", "income", amount, note, description, ref);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void deleteMovement(Row row) {
        SQLiteDatabase db = getWritableDatabase();
        if (row.isTransfer() && !row.transferRef.isEmpty()) {
            db.delete("transactions", "transfer_ref=?", new String[]{row.transferRef});
            return;
        }
        db.delete("transactions", "id=?", new String[]{String.valueOf(row.id)});
        if (row.linkedId > 0) db.delete("transactions", "id=?", new String[]{String.valueOf(row.linkedId)});
    }

    void addAccount(String name, String currency) {
        addAccount(name, currency, "Cuentas de Banco", 0, "", true, false);
    }

    void addAccount(String name, String currency, String type, double balance, String description, boolean includeTotal, boolean hidden) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT INTO accounts(name,balance,currency,type,description,include_total,hidden) VALUES(?,?,?,?,?,?,?)",
                new Object[]{name, balance, currency, accountType(type, name), description, includeTotal ? 1 : 0, hidden ? 1 : 0});
        dedupeAccounts(db);
    }

    void updateAccount(long id, String oldName, String newName, String currency) {
        updateAccount(id, oldName, newName, currency, "Cuentas de Banco", 0, "", true, false);
    }

    void updateAccount(long id, String oldName, String newName, String currency, String type, double balance, String description, boolean includeTotal, boolean hidden) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("UPDATE accounts SET name=?,currency=?,type=?,balance=?,description=?,include_total=?,hidden=? WHERE id=?",
                    new Object[]{newName, currency, accountType(type, newName), balance, description, includeTotal ? 1 : 0, hidden ? 1 : 0, id});
            if (!oldName.equals(newName)) {
                db.execSQL("UPDATE transactions SET account=? WHERE account=?", new Object[]{newName, oldName});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    boolean deleteAccount(long id, String name) {
        SQLiteDatabase db = getWritableDatabase();
        if (hasTransactionsFor("account", name)) return false;
        db.delete("accounts", "id=?", new String[]{String.valueOf(id)});
        return true;
    }

    void addCategory(String name, String kind) {
        String color = "income".equals(kind) ? "#168A5A" : "#D94A4A";
        getWritableDatabase().execSQL("INSERT INTO categories(name,kind,color) VALUES(?,?,?)", new Object[]{name, kind, color});
    }

    void updateCategory(long id, String oldName, String newName, String kind) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String color = "income".equals(kind) ? "#168A5A" : "#D94A4A";
            db.execSQL("UPDATE categories SET name=?,kind=?,color=? WHERE id=?", new Object[]{newName, kind, color, id});
            if (!oldName.equals(newName)) {
                db.execSQL("UPDATE transactions SET category=? WHERE category=?", new Object[]{newName, oldName});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    boolean deleteCategory(long id, String name) {
        SQLiteDatabase db = getWritableDatabase();
        if (hasTransactionsFor("category", name)) return false;
        db.delete("categories", "id=?", new String[]{String.valueOf(id)});
        return true;
    }

    void addBudget(String category, String month, double amount) {
        getWritableDatabase().execSQL("INSERT INTO budgets(category,month,amount) VALUES(?,?,?)", new Object[]{category, month, amount});
    }

    List<String> budgets() {
        return strings("SELECT month || ' · ' || category || ' · ' || printf('%.2f',amount) AS label FROM budgets ORDER BY month DESC,category", "label");
    }

    void replaceFromImport(List<ImportedAccount> accounts, List<ImportedCategory> categories, List<ImportedTxn> txns) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("accounts", null, null);
            db.delete("categories", null, null);
            db.delete("transactions", null, null);
            if (accounts.isEmpty()) seedAccountsOnly(db);
            for (ImportedAccount a : accounts) {
                db.execSQL("INSERT INTO accounts(name,balance,currency,type,description,include_total,hidden) VALUES(?,?,?,?,?,?,?)", new Object[]{a.name, a.balance, a.currency, accountType(a.type, a.name), a.description, a.includeTotal ? 1 : 0, a.hidden ? 1 : 0});
            }
            if (categories.isEmpty()) seedCategoriesOnly(db);
            for (ImportedCategory c : categories) {
                db.execSQL("INSERT INTO categories(name,kind,color) VALUES(?,?,?)", new Object[]{c.name, c.kind, c.color});
            }
            for (int i = 0; i < txns.size(); i++) {
                ImportedTxn t = txns.get(i);
                String transferRef = null;
                if (isTransferExpense(t) && i + 1 < txns.size() && isMatchingTransferIncome(t, txns.get(i + 1))) {
                    transferRef = UUID.randomUUID().toString();
                    insertTransaction(db, t.date, t.time, t.account, t.category, t.kind, t.amount, t.note, t.description, transferRef);
                    ImportedTxn income = txns.get(++i);
                    insertTransaction(db, income.date, income.time, income.account, income.category, income.kind, income.amount, income.note, income.description, transferRef);
                    continue;
                }
                insertTransaction(db, t.date, t.time, t.account, t.category, t.kind, t.amount, t.note, t.description, transferRef);
            }
            dedupeAccounts(db);
            backfillTransferRefs(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    File databaseFile(Context context) {
        return context.getDatabasePath(DB_NAME);
    }

    void copyDatabaseTo(File target, Context context) throws IOException {
        try (FileInputStream in = new FileInputStream(databaseFile(context)); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private void insertTransaction(SQLiteDatabase db, String date, String time, String account, String category, String kind, double amount, String note, String description, String transferRef) {
        db.execSQL(
                "INSERT INTO transactions(date,time,account,category,kind,amount,note,description,transfer_ref) VALUES(?,?,?,?,?,?,?,?,?)",
                new Object[]{date, time, account, category, kind, amount, note, description, transferRef}
        );
    }

    private boolean isTransferExpense(ImportedTxn txn) {
        return "Transferencia".equals(txn.category) && "expense".equals(txn.kind);
    }

    private boolean isMatchingTransferIncome(ImportedTxn expense, ImportedTxn income) {
        return "Transferencia".equals(income.category)
                && "income".equals(income.kind)
                && expense.date.equals(income.date)
                && expense.time.equals(income.time)
                && Math.abs(expense.amount - income.amount) < 0.005
                && expense.note.equals(income.note)
                && expense.description.equals(income.description);
    }

    private void backfillTransferRefs(SQLiteDatabase db) {
        List<Row> rows = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT id,date,COALESCE(time,'00:00'),account,category,kind,amount,COALESCE(note,''),COALESCE(description,''),COALESCE(transfer_ref,'') FROM transactions WHERE category='Transferencia' ORDER BY id", null)) {
            while (c.moveToNext()) {
                rows.add(new Row(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getDouble(6), c.getString(7), c.getString(8), c.getString(9)));
            }
        }
        Set<Long> used = new HashSet<>();
        for (Row row : rows) {
            if (used.contains(row.id) || !"expense".equals(row.kind)) continue;
            Row pair = findTransferPair(row, rows, used);
            if (pair == null) continue;
            String ref = UUID.randomUUID().toString();
            db.execSQL("UPDATE transactions SET transfer_ref=? WHERE id IN (?,?)", new Object[]{ref, row.id, pair.id});
            used.add(row.id);
            used.add(pair.id);
        }
    }

    private void seedAccountsOnly(SQLiteDatabase db) {
        db.execSQL("INSERT INTO accounts(name,balance,currency,type,description,include_total,hidden) VALUES('Efectivo',0,'USD','Efectivo','',1,0),('Cuenta',0,'USD','Cuentas de Banco','',1,0)");
    }

    private void seedCategoriesOnly(SQLiteDatabase db) {
        db.execSQL("INSERT INTO categories(name,kind,color) VALUES('Comida','expense','#D94A4A'),('Transporte','expense','#DB8A35'),('Hogar','expense','#4067B2'),('Salario','income','#168A5A')");
    }

    private void addColumn(SQLiteDatabase db, String table, String definition) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + definition);
        } catch (Exception ignored) {
        }
    }

    private String accountType(String type, String name) {
        String value = type == null ? "" : type.trim();
        if (isCashLike(name) || value.toLowerCase(java.util.Locale.US).contains("efectivo") || value.toLowerCase(java.util.Locale.US).contains("cash")) return "Efectivo";
        return "Cuentas de Banco";
    }

    private void normalizeAccountGroups(SQLiteDatabase db) {
        try {
            db.execSQL("UPDATE accounts SET type='Cuentas de Banco' WHERE type IS NULL OR trim(type)='' OR lower(type)='cuentas'");
        } catch (Exception ignored) {
        }
        try {
            db.execSQL("UPDATE accounts SET type='Efectivo' WHERE lower(name) LIKE '%efectivo%' OR lower(name) LIKE '%cash%' OR lower(name)='ahorro s/' OR lower(name)='ahorro s' OR lower(name) LIKE 'ahorro s/%'");
        } catch (Exception ignored) {
        }
    }

    private void dedupeAccounts(SQLiteDatabase db) {
        Map<String, Long> canonicalIds = new LinkedHashMap<>();
        Map<String, String> canonicalNames = new LinkedHashMap<>();
        List<Long> duplicateIds = new ArrayList<>();
        List<String> duplicateNames = new ArrayList<>();
        List<String> replacementNames = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT id,name FROM accounts ORDER BY id", null)) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                String key = accountKey(name);
                if (key.isEmpty() || !canonicalIds.containsKey(key)) {
                    canonicalIds.put(key, id);
                    canonicalNames.put(key, name);
                    continue;
                }
                duplicateIds.add(id);
                duplicateNames.add(name);
                replacementNames.add(canonicalNames.get(key));
            }
        }
        for (int i = 0; i < duplicateIds.size(); i++) {
            String duplicateName = duplicateNames.get(i);
            String canonicalName = replacementNames.get(i);
            if (!canonicalName.equals(duplicateName)) {
                db.execSQL("UPDATE transactions SET account=? WHERE account=?", new Object[]{canonicalName, duplicateName});
            }
            db.delete("accounts", "id=?", new String[]{String.valueOf(duplicateIds.get(i))});
        }
    }

    private String accountKey(String name) {
        if (name == null) return "";
        return name.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*/\\s*", "/")
                .toLowerCase(Locale.US);
    }

    private boolean isCashLike(String name) {
        String lower = name == null ? "" : name.trim().toLowerCase(java.util.Locale.US);
        return lower.contains("efectivo") || lower.contains("cash") || lower.equals("ahorro s/") || lower.equals("ahorro s") || lower.startsWith("ahorro s/");
    }

    private List<String> strings(String sql, String col) {
        SQLiteDatabase db = getReadableDatabase();
        List<String> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(sql, null)) {
            int idx = c.getColumnIndexOrThrow(col);
            while (c.moveToNext()) out.add(c.getString(idx));
        }
        return out;
    }

    private double sumBetween(SQLiteDatabase db, String kind, String startDate, String endDate) {
        String sql = "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE kind=? AND category<>'Transferencia'";
        List<String> args = new ArrayList<>();
        args.add(kind);
        if (startDate != null && endDate != null) {
            sql += " AND date>=? AND date<=?";
            args.add(startDate);
            args.add(endDate);
        }
        try (Cursor c = db.rawQuery(sql, args.toArray(new String[0]))) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    private boolean hasTransactionsFor(String column, String value) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT 1 FROM transactions WHERE " + column + "=? LIMIT 1", new String[]{value})) {
            return c.moveToFirst();
        }
    }

    static final class Summary {
        final double income;
        final double expense;
        final double balance;
        final int count;
        Summary(double income, double expense, double balance, int count) {
            this.income = income;
            this.expense = expense;
            this.balance = balance;
            this.count = count;
        }
    }

    static final class Row {
        final long id;
        final long linkedId;
        final String date, time, account, category, kind, note, description, transferRef, transferFrom, transferTo;
        final double amount;
        Row(long id, String date, String time, String account, String category, String kind, double amount, String note, String description) {
            this(id, date, time, account, category, kind, amount, note, description, "");
        }
        Row(long id, String date, String time, String account, String category, String kind, double amount, String note, String description, String transferRef) {
            this(id, -1, date, time, account, category, kind, amount, note, description, transferRef, "", "");
        }
        private Row(long id, long linkedId, String date, String time, String account, String category, String kind, double amount, String note, String description, String transferRef, String transferFrom, String transferTo) {
            this.id = id;
            this.linkedId = linkedId;
            this.date = date;
            this.time = time;
            this.account = account;
            this.category = category;
            this.kind = kind;
            this.amount = amount;
            this.note = note;
            this.description = description;
            this.transferRef = transferRef == null ? "" : transferRef;
            this.transferFrom = transferFrom == null ? "" : transferFrom;
            this.transferTo = transferTo == null ? "" : transferTo;
        }
        static Row transfer(Row expense, Row income) {
            String ref = !expense.transferRef.isEmpty() ? expense.transferRef : income.transferRef;
            return new Row(expense.id, income.id, expense.date, expense.time, expense.account, "Transferencia", "transfer", expense.amount, expense.note, expense.description, ref, expense.account, income.account);
        }
        Row asUnpairedTransfer() {
            String from = "expense".equals(kind) ? account : "";
            String to = "income".equals(kind) ? account : "";
            return new Row(id, -1, date, time, account, "Transferencia", "transfer", amount, note, description, transferRef, from, to);
        }
        boolean isTransferPart() {
            return "Transferencia".equals(category);
        }
        boolean isTransfer() {
            return "transfer".equals(kind) || isTransferPart();
        }
    }

    static final class Bar {
        final String label;
        final double value;
        Bar(String label, double value) {
            this.label = label;
            this.value = value;
        }
    }

    static final class AccountTotal {
        final long id;
        final String name;
        final String currency;
        final String type;
        final String description;
        final double balance;
        final double income;
        final double expense;
        final boolean includeTotal;
        final boolean hidden;
        AccountTotal(long id, String name, String currency, double balance, double income, double expense, String type, String description, boolean includeTotal, boolean hidden) {
            this.id = id;
            this.name = name;
            this.currency = currency;
            this.balance = balance;
            this.income = income;
            this.expense = expense;
            this.type = type == null || type.trim().isEmpty() ? "Cuentas de Banco" : type;
            this.description = description == null ? "" : description;
            this.includeTotal = includeTotal;
            this.hidden = hidden;
        }
    }

    static final class AccountOption {
        final long id;
        final String name;
        final String currency;
        final String type;
        final String description;
        final double balance;
        final boolean includeTotal;
        final boolean hidden;
        AccountOption(long id, String name, String currency, double balance, String type, String description, boolean includeTotal, boolean hidden) {
            this.id = id;
            this.name = name;
            this.currency = currency;
            this.balance = balance;
            this.type = type == null || type.trim().isEmpty() ? "Cuentas de Banco" : type;
            this.description = description == null ? "" : description;
            this.includeTotal = includeTotal;
            this.hidden = hidden;
        }
    }

    static final class CategoryOption {
        final long id;
        final String name;
        final String kind;
        CategoryOption(long id, String name, String kind) {
            this.id = id;
            this.name = name;
            this.kind = kind;
        }
    }
}
