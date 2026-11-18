import assert from "node:assert/strict";
import { normalizeSnapshot, prepareBackupImport } from "../src/lib/backup.js";
import { accountBalances, accountFlowsByType, rangeFor, summary } from "../src/lib/finance.js";

const duplicate = {
  id: "movement-1",
  date: "2026-08-19",
  time: "09:30",
  kind: "expense",
  account: "Billetera",
  category: "Comida",
  amount: 12.5,
  note: "Desayuno",
  description: "",
};

const source = {
  version: 2,
  currency: "PEN",
  accountTypes: ["Efectivo", "Cuentas de Banco"],
  accounts: [
    { name: "Billetera", type: "Efectivo", balance: 30 },
    { name: " billetera ", type: "Efectivo", balance: 999 },
  ],
  categories: [{ name: "Comida", kind: "expense" }],
  transactions: [
    duplicate,
    { ...duplicate },
    { ...duplicate, id: "movement-duplicate" },
    {
      id: "movement-2",
      date: "2026-08-19",
      time: "10:00",
      kind: "income",
      account: "Cuenta nueva",
      category: "Reembolso",
      amount: 25,
      note: "",
      description: "",
    },
  ],
};

const normalized = normalizeSnapshot(source);
assert.equal(normalized.duplicatesRemoved, 1, "el mismo identificador y contenido solo se importan una vez");
assert.equal(normalized.data.transactions.length, 3, "compras idénticas con IDs distintos se conservan");
assert.equal(normalized.data.accounts.length, 2, "debe unificar cuentas y crear las que falten");
assert.ok(normalized.data.accounts.some((account) => account.name === "Cuenta nueva"));
assert.ok(normalized.data.categories.some((category) => category.name === "Reembolso" && category.kind === "income"));

const preview = prepareBackupImport(source, { transactions: [duplicate] }, "respaldo.json");
assert.equal(preview.existingMatches, 2, "la coincidencia visual solo genera un aviso");
assert.equal(preview.movements, 3);
assert.equal(preview.expense, 25);
assert.equal(preview.income, 25);
assert.equal(preview.firstDate, "2026-08-19");
assert.equal(preview.lastDate, "2026-08-19");

assert.throws(() => prepareBackupImport({ settings: {} }, source, "settings.json"));
assert.throws(() => prepareBackupImport({ version: 2, transactions: [] }, source, "bad.json"));
assert.throws(() => prepareBackupImport([{ ...duplicate, date: "2026-02-30" }], source, "bad-date.json"));
assert.equal(prepareBackupImport({ version: 2, accounts: [], transactions: [] }, source, "empty.json").movements, 0);

const variants = normalizeSnapshot({ ...source, transactions: [{ ...duplicate, account: " billetera " }] }).data;
assert.equal(accountBalances(variants)[0].currentBalance, 17.5);

const accounts = [{ name: "Billetera", type: "Efectivo", balance: 30 }, { name: "Banco", type: "Cuentas de Banco", balance: 0 }];
const transfer = { ...duplicate, id: "transfer-a", kind: "transfer", toAccount: "Banco", category: "Transferencia" };
const legacy = prepareBackupImport({ version: 1, accounts, transactions: [
  { ...transfer, id: undefined, toAccount: undefined, kind: "expense" },
  { ...transfer, id: undefined, toAccount: undefined, kind: "income", account: "Banco" },
] }, source, "android.json");
assert.equal(legacy.transfers, 1);
assert.deepEqual(accountBalances(legacy.data).map((account) => account.currentBalance), [17.5, 12.5]);
assert.deepEqual(summary([duplicate, transfer]), { income: 0, expense: 12.5, transfer: 12.5, balance: -12.5 });
assert.deepEqual(accountFlowsByType([duplicate, transfer], accounts), [
  { label: "Billetera", type: "Efectivo", income: 0, expense: 25 },
  { label: "Banco", type: "Cuentas de Banco", income: 12.5, expense: 0 },
]);
assert.doesNotThrow(() => rangeFor("mensual", ""));
assert.deepEqual([rangeFor("semanal", "2026-11-18").start, rangeFor("semanal", "2026-11-18").end], ["2026-11-16", "2026-11-22"]);

console.log("Backup safety checks passed.");
