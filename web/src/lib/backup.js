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

  const seenTransactions = new Map();
  let duplicatesRemoved = 0;
  const transactions = [];
  for (const transaction of snapshotRows(source)) {
    const normalized = {
      id: transaction.id || crypto.randomUUID(),
      date: validDate(transaction.date) ? transaction.date : today(),
      time: /^\d{2}:\d{2}$/.test(transaction.time || "") ? transaction.time : "00:00",
      kind: ["income", "expense", "transfer"].includes(transaction.kind) ? transaction.kind : "expense",
      account: canonicalAccount(transaction.account, "Cuenta"),
      toAccount: transaction.kind === "transfer" ? canonicalAccount(transaction.toAccount, "") : "",
      category: transaction.kind === "transfer" ? "Transferencia" : clean(transaction.category, "Sin categoria"),
      amount: Math.abs(Number(transaction.amount) || 0),
      note: transaction.note || "",
      description: transaction.description || "",
    };
    if (!Number.isFinite(normalized.amount) || !normalized.amount || (normalized.kind === "transfer" && (!normalized.toAccount || normalizeText(normalized.toAccount) === normalizeText(normalized.account)))) continue;
    const fingerprint = transactionFingerprint(normalized);
    if (seenTransactions.has(normalized.id)) {
      if (seenTransactions.get(normalized.id) === fingerprint) {
        duplicatesRemoved += 1;
        continue;
      }
      normalized.id = crypto.randomUUID();
    }
    seenTransactions.set(normalized.id, fingerprint);
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

  function canonicalAccount(value, fallback) {
    const name = clean(value, fallback);
    return accountMap.get(normalizeText(name))?.name || name;
  }
}

export function prepareBackupImport(raw, currentData, fileName) {
  validateSnapshot(raw);
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

export function validateSnapshot(raw) {
  const rows = Array.isArray(raw) ? raw : raw?.transactions;
  const validVersion = raw && (raw.version === 1 || raw.version === 2);
  if (!Array.isArray(rows) || (rows.length === 0 && (!validVersion || !Array.isArray(raw.accounts)))
      || rows.some((row) => !row || !validDate(row.date) || !["income", "expense", "transfer"].includes(row.kind)
        || !clean(row.account, "") || !Number.isFinite(Number(row.amount)) || Number(row.amount) === 0
        || (row.kind === "transfer" && (!clean(row.toAccount, "") || normalizeText(row.account) === normalizeText(row.toAccount))))) {
    throw new Error("El archivo JSON no es un respaldo válido.");
  }
}

function snapshotRows(source) {
  const rows = Array.isArray(source.transactions) ? source.transactions : [];
  if (source.version !== 1) return rows;

  // Android v1 exported a matched debit and credit instead of a transfer row.
  const consumed = new Set();
  const restored = [];
  for (const [index, row] of rows.entries()) {
    if (row.kind !== "expense" || row.category !== "Transferencia") continue;
    const pair = rows.findIndex((other, candidate) => !consumed.has(candidate)
      && other.kind === "income" && other.category === "Transferencia"
      && normalizeText(other.account) !== normalizeText(row.account)
      && other.date === row.date && other.time === row.time && Number(other.amount) === Number(row.amount)
      && (other.note || "") === (row.note || "") && (other.description || "") === (row.description || ""));
    if (pair === -1) continue;
    consumed.add(index);
    consumed.add(pair);
    restored.push({ ...row, kind: "transfer", toAccount: rows[pair].account });
  }
  return [...restored, ...rows.filter((_, index) => !consumed.has(index))];
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
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value || "")) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function sortTransactions(left, right) {
  return `${right.date} ${right.time}`.localeCompare(`${left.date} ${left.time}`);
}
