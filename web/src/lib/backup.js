import { today } from "./finance.js";

export const IMPORT_UNDO_KEY = "moneymate-before-last-import-v2";

export function normalizeSnapshot(snapshot) {
  const source = Array.isArray(snapshot) ? { transactions: snapshot } : (snapshot || {});
  const currency = source.currency || "PEN";
  const accountMap = new Map();
  for (const account of Array.isArray(source.accounts) ? source.accounts : []) {
    const name = clean(account.name, "Cuenta");
    const key = normalizeText(name);
    if (!accountMap.has(key)) {
      accountMap.set(key, {
        name,
        currency: account.currency || currency,
        type: clean(account.type, "Cuentas de Banco"),
        balance: Number(account.balance) || 0,
        description: account.description || "",
        includeTotal: account.includeTotal !== false,
        hidden: Boolean(account.hidden),
      });
    }
  }

  const categoryMap = new Map();
  for (const category of Array.isArray(source.categories) ? source.categories : []) {
    const kind = category.kind === "income" ? "income" : "expense";
    const name = clean(category.name, "Sin categoria");
    categoryMap.set(`${kind}|${normalizeText(name)}`, { ...category, name, kind });
  }

  const seenTransactions = new Set();
  let duplicatesRemoved = 0;
  const transactions = [];
  for (const transaction of Array.isArray(source.transactions) ? source.transactions : []) {
    const normalized = {
      id: transaction.id || crypto.randomUUID(),
      date: validDate(transaction.date) ? transaction.date : today(),
      time: /^\d{2}:\d{2}$/.test(transaction.time || "") ? transaction.time : "00:00",
      kind: ["income", "expense", "transfer"].includes(transaction.kind) ? transaction.kind : "expense",
      account: clean(transaction.account, "Cuenta"),
      toAccount: transaction.kind === "transfer" ? clean(transaction.toAccount, "") : "",
      category: transaction.kind === "transfer" ? "Transferencia" : clean(transaction.category, "Sin categoria"),
      amount: Math.abs(Number(transaction.amount) || 0),
      note: transaction.note || "",
      description: transaction.description || "",
    };
    if (!normalized.amount || (normalized.kind === "transfer" && (!normalized.toAccount || normalized.toAccount === normalized.account))) continue;
    const fingerprint = transactionFingerprint(normalized);
    if (seenTransactions.has(fingerprint)) {
      duplicatesRemoved += 1;
      continue;
    }
    seenTransactions.add(fingerprint);
    transactions.push(normalized);
    ensureAccount(accountMap, normalized.account, currency);
    if (normalized.toAccount) ensureAccount(accountMap, normalized.toAccount, currency);
    if (normalized.kind !== "transfer") {
      const categoryKey = `${normalized.kind}|${normalizeText(normalized.category)}`;
      if (!categoryMap.has(categoryKey)) categoryMap.set(categoryKey, { name: normalized.category, kind: normalized.kind });
    }
  }

  const accounts = [...accountMap.values()];
  const accountTypes = [...new Set([
    "Efectivo",
    "Cuentas de Banco",
    ...(Array.isArray(source.accountTypes) ? source.accountTypes : []),
    ...accounts.map((account) => account.type),
  ].filter(Boolean))];
  return {
    data: {
      version: 2,
      currency,
      accountTypes,
      accounts,
      categories: [...categoryMap.values()],
      transactions: transactions.sort(sortTransactions),
    },
    duplicatesRemoved,
  };
}

export function prepareBackupImport(raw, currentData, fileName) {
  const normalized = normalizeSnapshot(raw);
  const rows = normalized.data.transactions;
  const current = new Set((currentData?.transactions || []).map(transactionFingerprint));
  const dates = rows.map((transaction) => transaction.date).sort();
  const warnings = [];
  const existingMatches = rows.filter((transaction) => current.has(transactionFingerprint(transaction))).length;
  if (!rows.length) warnings.push("El archivo no contiene movimientos válidos.");
  if (normalized.duplicatesRemoved) warnings.push(`Se omitieron ${normalized.duplicatesRemoved} movimientos duplicados.`);
  if (existingMatches) warnings.push(`${existingMatches} movimientos también existen en los datos actuales.`);
  return {
    fileName,
    data: normalized.data,
    accounts: normalized.data.accounts.length,
    categories: normalized.data.categories.length,
    movements: rows.length,
    transfers: rows.filter((transaction) => transaction.kind === "transfer").length,
    duplicatesRemoved: normalized.duplicatesRemoved,
    existingMatches,
    firstDate: dates[0] || "",
    lastDate: dates.at(-1) || "",
    income: rows.filter((transaction) => transaction.kind === "income").reduce((sum, row) => sum + row.amount, 0),
    expense: rows.filter((transaction) => transaction.kind === "expense").reduce((sum, row) => sum + row.amount, 0),
    warnings,
  };
}

export function transactionFingerprint(transaction) {
  return [
    transaction.date,
    transaction.time,
    transaction.kind,
    normalizeText(transaction.account),
    normalizeText(transaction.toAccount),
    normalizeText(transaction.category),
    Number(transaction.amount || 0).toFixed(2),
    normalizeText(transaction.note),
    normalizeText(transaction.description),
  ].join("|");
}

function ensureAccount(accountMap, name, currency) {
  const key = normalizeText(name);
  if (accountMap.has(key)) return;
  accountMap.set(key, {
    name,
    currency,
    type: /efectivo|cash/i.test(name) ? "Efectivo" : "Cuentas de Banco",
    balance: 0,
    description: "",
    includeTotal: true,
    hidden: false,
  });
}

function clean(value, fallback) {
  return String(value || "").trim() || fallback;
}

function normalizeText(value) {
  return String(value || "").trim().replace(/\s+/g, " ").toLocaleLowerCase("es");
}

function validDate(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value || "") && !Number.isNaN(Date.parse(`${value}T00:00:00`));
}

function sortTransactions(left, right) {
  return `${right.date} ${right.time}`.localeCompare(`${left.date} ${left.time}`);
}
