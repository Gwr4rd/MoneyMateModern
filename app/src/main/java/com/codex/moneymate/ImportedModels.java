package com.codex.moneymate;

final class ImportedAccount {
    final String name;
    final double balance;
    final String currency;
    final String type;
    final String description;
    final boolean includeTotal;
    final boolean hidden;
    ImportedAccount(String name, double balance, String currency) {
        this(name, balance, currency, "", "", true, false);
    }
    ImportedAccount(String name, double balance, String currency, String type, String description, boolean includeTotal, boolean hidden) {
        this.name = clean(name, "Cuenta");
        this.balance = balance;
        this.currency = clean(currency, "USD");
        this.type = clean(type, "");
        this.description = description == null ? "" : description;
        this.includeTotal = includeTotal;
        this.hidden = hidden;
    }
    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}

final class ImportedCategory {
    final String name;
    final String kind;
    final String color;
    ImportedCategory(String name, String kind, String color) {
        this.name = name == null || name.trim().isEmpty() ? "Sin categoria" : name.trim();
        this.kind = "income".equals(kind) ? "income" : "expense";
        this.color = color == null || color.trim().isEmpty() ? ("income".equals(this.kind) ? "#168A5A" : "#D94A4A") : color;
    }
}

final class ImportedTxn {
    final String date;
    final String time;
    final String account;
    final String category;
    final String kind;
    final double amount;
    final String note;
    final String description;
    ImportedTxn(String date, String account, String category, String kind, double amount, String note) {
        this(date, "00:00", account, category, kind, amount, note);
    }
    ImportedTxn(String date, String time, String account, String category, String kind, double amount, String note) {
        this(date, time, account, category, kind, amount, note, "");
    }
    ImportedTxn(String date, String time, String account, String category, String kind, double amount, String note, String description) {
        this.date = date == null || date.trim().isEmpty() ? "1970-01-01" : date;
        this.time = time == null || time.trim().isEmpty() ? "00:00" : time;
        this.account = account == null || account.trim().isEmpty() ? "Efectivo" : account;
        this.category = category == null || category.trim().isEmpty() ? "Sin categoria" : category;
        this.kind = "income".equals(kind) ? "income" : "expense";
        this.amount = Math.abs(amount);
        this.note = note == null ? "" : note;
        this.description = description == null ? "" : description;
    }
}

final class ImportResult {
    final int accounts;
    final int categories;
    final int transactions;
    ImportResult(int accounts, int categories, int transactions) {
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
    }
}
