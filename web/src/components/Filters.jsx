import { CalendarDays, Search, SlidersHorizontal, WalletCards, X } from "lucide-react";
import { t } from "../i18n";

export const periods = [
  ["diario", "Diario"], ["semanal", "Semanal"], ["mensual", "Mensual"],
  ["semestral", "Semestral"], ["anual", "Anual"], ["todo", "Total"],
];
const transactionPeriods = periods.filter(([scope]) => scope !== "semestral");

export function TransactionPeriods({ value, onChange, language }) {
  const selected = transactionPeriods.findIndex(([scope]) => scope === value);
  return <nav className="transaction-periods glass-control" aria-label={t("Periodo", language)}
    style={{ "--segment-index": Math.max(0, selected) }}>
    {transactionPeriods.map(([scope, label]) => (
      <button type="button" key={scope} aria-pressed={value === scope} onClick={() => onChange(scope)}>{t(label, language)}</button>
    ))}
  </nav>;
}

export function PeriodSelect({ value, onChange, language }) {
  return <select className="period-select" aria-label={t("Periodo", language)} value={value} onChange={(event) => onChange(event.target.value)}>
    {periods.map(([scope, label]) => <option value={scope} key={scope}>{t(label, language)}</option>)}
  </select>;
}

export function Filters({ filters, accounts, scope, onChange, onClear, language }) {
  return (
    <section className="filters" aria-label={t("Buscar y filtrar", language)}>
      <label className="search-field">
        <Search size={20} />
        <input
          value={filters.query}
          onChange={(event) => onChange({ ...filters, query: event.target.value })}
          placeholder={t("Buscar", language)}
        />
        {filters.query ? (
          <button onClick={() => onChange({ ...filters, query: "" })} aria-label={t("Limpiar texto", language)}>
            <X size={18} />
          </button>
        ) : null}
      </label>
      <details className="filter-more">
        <summary title={t("Buscar y filtrar", language)} aria-label={t("Buscar y filtrar", language)}><SlidersHorizontal size={20} /></summary>
        <div className="filter-extra">
          <label className="select-field">
            <WalletCards size={19} />
            <select value={filters.account} onChange={(event) => onChange({ ...filters, account: event.target.value })}>
              <option value="">{t("Todas las cuentas", language)}</option>
              {accounts.map((account) => <option key={account.name}>{account.name}</option>)}
            </select>
          </label>
          {scope !== "diario" && scope !== "todo" ? <label className="date-field">
            <CalendarDays size={19} />
            <input type="date" value={filters.anchor} onChange={(event) => onChange({ ...filters, anchor: event.target.value })} />
          </label> : null}
          <button className="filter-reset" onClick={onClear} title={t("Limpiar filtros", language)} aria-label={t("Limpiar filtros", language)}>
            <X size={19} /> {t("Limpiar filtros", language)}
          </button>
        </div>
      </details>
      {filters.account ? <button type="button" className="active-account-filter" onClick={() => onChange({ ...filters, account: "" })}>
        {t("Cuenta", language)}: {filters.account} <X size={15} />
      </button> : null}
    </section>
  );
}
