import assert from "node:assert/strict";
import { normalizeSnapshot, prepareBackupImport } from "../src/lib/backup.js";

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
assert.equal(normalized.duplicatesRemoved, 1, "debe eliminar movimientos exactos repetidos");
assert.equal(normalized.data.transactions.length, 2, "debe conservar los movimientos únicos");
assert.equal(normalized.data.accounts.length, 2, "debe unificar cuentas y crear las que falten");
assert.ok(normalized.data.accounts.some((account) => account.name === "Cuenta nueva"));
assert.ok(normalized.data.categories.some((category) => category.name === "Reembolso" && category.kind === "income"));

const preview = prepareBackupImport(source, { transactions: [duplicate] }, "respaldo.json");
assert.equal(preview.existingMatches, 1, "debe avisar cuando un movimiento ya existe localmente");
assert.equal(preview.movements, 2);
assert.equal(preview.expense, 12.5);
assert.equal(preview.income, 25);
assert.equal(preview.firstDate, "2026-08-19");
assert.equal(preview.lastDate, "2026-08-19");

console.log("Backup safety checks passed.");
