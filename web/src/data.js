export const STORAGE_KEY = "moneymate-modern-v2";
export const APP_VERSION = "2.0.0";

export const seedData = {
  version: 2,
  currency: "PEN",
  accountTypes: ["Efectivo", "Cuentas de Banco"],
  accounts: [
    { name: "Efectivo", currency: "PEN", type: "Efectivo", balance: 250, description: "", includeTotal: true, hidden: false },
    { name: "Cuenta principal", currency: "PEN", type: "Cuentas de Banco", balance: 800, description: "", includeTotal: true, hidden: false },
    { name: "Billetera digital", currency: "PEN", type: "Cuentas de Banco", balance: 120, description: "", includeTotal: true, hidden: false },
    { name: "Ahorros", currency: "PEN", type: "Cuentas de Banco", balance: 1500, description: "", includeTotal: true, hidden: false },
  ],
  categories: [
    { name: "Alimentacion", kind: "expense" },
    { name: "Transporte", kind: "expense" },
    { name: "Servicios", kind: "expense" },
    { name: "Salario", kind: "income" },
  ],
  transactions: [
    { id: "demo-1", date: "2026-07-26", time: "11:24", kind: "transfer", account: "Cuenta principal", toAccount: "Billetera digital", category: "Transferencia", amount: 200, note: "Recarga semanal", description: "" },
    { id: "demo-2", date: "2026-07-26", time: "09:15", kind: "expense", account: "Billetera digital", toAccount: "", category: "Alimentacion", amount: 85.4, note: "Compras", description: "" },
    { id: "demo-3", date: "2026-07-25", time: "18:42", kind: "income", account: "Cuenta principal", toAccount: "", category: "Salario", amount: 1800, note: "Ingreso mensual", description: "" },
    { id: "demo-4", date: "2026-07-24", time: "14:05", kind: "expense", account: "Efectivo", toAccount: "", category: "Transporte", amount: 60, note: "", description: "" },
    { id: "demo-5", date: "2026-07-23", time: "12:05", kind: "expense", account: "Cuenta principal", toAccount: "", category: "Servicios", amount: 110, note: "Internet", description: "" },
  ],
};
