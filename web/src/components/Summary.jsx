import { currency } from "../lib/finance";
import { t } from "../i18n";

export function Summary({ value, currencyCode, language }) {
  const items = [
    { label: "Ingresos", value: value.income, className: "income" },
    { label: "Gastos", value: value.expense, className: "expense" },
    { label: "Balance", value: value.balance, className: "balance" },
  ];
  return (
    <section className="summary-band" aria-label={t("Resumen", language)}>
      {items.map((item) => (
        <div className={`summary-item ${item.className}`} key={item.label}>
          <span>{t(item.label, language)}</span>
          <strong>{currency(item.value, currencyCode)}</strong>
        </div>
      ))}
    </section>
  );
}
