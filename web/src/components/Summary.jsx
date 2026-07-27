import { currency } from "../lib/finance";

export function Summary({ value, currencyCode }) {
  const items = [
    { label: "Ingresos", value: value.income, className: "income" },
    { label: "Gastos", value: value.expense, className: "expense" },
    { label: "Balance", value: value.balance, className: "balance" },
  ];
  return (
    <section className="summary-band" aria-label="Resumen">
      {items.map((item) => (
        <div className={`summary-item ${item.className}`} key={item.label}>
          <span>{item.label}</span>
          <strong>{currency(item.value, currencyCode)}</strong>
        </div>
      ))}
    </section>
  );
}
