const MS_DAY = 86_400_000;

export function currency(value, code = "PEN") {
  return new Intl.NumberFormat("es-PE", {
    style: "currency",
    currency: code,
    minimumFractionDigits: 2,
  }).format(value);
}

export function rangeFor(scope, anchorValue) {
  const anchor = new Date(`${anchorValue}T12:00:00`);
  if (scope === "todo") return { start: null, end: null, label: "Todo" };
  const start = new Date(anchor);
  const end = new Date(anchor);
  if (scope === "anual") {
    start.setMonth(0, 1);
    end.setMonth(11, 31);
    return { start: iso(start), end: iso(end), label: String(anchor.getFullYear()) };
  }
  if (scope === "semanal") {
    const day = start.getDay();
    start.setDate(start.getDate() - (day === 0 ? 6 : day - 1));
    end.setTime(start.getTime() + 6 * MS_DAY);
    return { start: iso(start), end: iso(end), label: `${shortDate(start)} - ${shortDate(end)}` };
  }
  if (scope === "diario") {
    return { start: iso(start), end: iso(end), label: longDate(start) };
  }
  start.setDate(1);
  end.setMonth(end.getMonth() + 1, 0);
  return { start: iso(start), end: iso(end), label: monthLabel(start) };
}

export function inRange(transaction, range) {
  if (!range.start || !range.end) return true;
  return transaction.date >= range.start && transaction.date <= range.end;
}

export function filterTransactions(transactions, filters) {
  const query = filters.query.trim().toLocaleLowerCase("es");
  return transactions.filter((transaction) => {
    if (!inRange(transaction, filters.range)) return false;
    if (filters.account && transaction.account !== filters.account && transaction.toAccount !== filters.account) return false;
    if (!query) return true;
    const searchable = [
      transaction.date,
      transaction.time,
      transaction.kind,
      transaction.account,
      transaction.toAccount,
      transaction.category,
      transaction.note,
      transaction.description,
      String(transaction.amount),
    ].join(" ").toLocaleLowerCase("es");
    return searchable.includes(query);
  });
}

export function summary(transactions) {
  let income = 0;
  let expense = 0;
  for (const transaction of transactions) {
    if (transaction.kind === "income") income += Number(transaction.amount);
    if (transaction.kind === "expense") expense += Number(transaction.amount);
  }
  return { income, expense, balance: income - expense };
}

export function accountBalances(data) {
  const balances = new Map(data.accounts.map((account) => [account.name, Number(account.balance) || 0]));
  for (const transaction of data.transactions) {
    const amount = Number(transaction.amount) || 0;
    if (transaction.kind === "income") balances.set(transaction.account, (balances.get(transaction.account) || 0) + amount);
    if (transaction.kind === "expense") balances.set(transaction.account, (balances.get(transaction.account) || 0) - amount);
    if (transaction.kind === "transfer") {
      balances.set(transaction.account, (balances.get(transaction.account) || 0) - amount);
      balances.set(transaction.toAccount, (balances.get(transaction.toAccount) || 0) + amount);
    }
  }
  return data.accounts.map((account) => ({ ...account, currentBalance: balances.get(account.name) || 0 }));
}

export function categoryTotals(transactions, kind) {
  const totals = new Map();
  for (const transaction of transactions) {
    if (transaction.kind !== kind) continue;
    totals.set(transaction.category, (totals.get(transaction.category) || 0) + Number(transaction.amount));
  }
  return [...totals.entries()]
    .map(([label, value]) => ({ label, value }))
    .sort((left, right) => right.value - left.value);
}

export function reportRows(transactions) {
  return transactions.map((transaction) => ({
    Fecha: transaction.date,
    Hora: transaction.time,
    Tipo: transaction.kind === "transfer" ? "Transferencia" : transaction.kind === "income" ? "Ingreso" : "Gasto",
    "Cuenta origen": transaction.account,
    "Cuenta destino o categoria": transaction.kind === "transfer" ? transaction.toAccount : transaction.category,
    Nota: transaction.note,
    Descripcion: transaction.description,
    Importe: Number(transaction.amount),
  }));
}

export function iso(date) {
  return date.toISOString().slice(0, 10);
}

export function today() {
  return iso(new Date());
}

function shortDate(date) {
  return new Intl.DateTimeFormat("es-PE", { day: "2-digit", month: "short" }).format(date);
}

function longDate(date) {
  return new Intl.DateTimeFormat("es-PE", { day: "2-digit", month: "short", year: "numeric" }).format(date);
}

function monthLabel(date) {
  return new Intl.DateTimeFormat("es-PE", { month: "short", year: "numeric" }).format(date);
}
