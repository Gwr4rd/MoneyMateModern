import { CalendarDays, Search, SlidersHorizontal, WalletCards, X } from "lucide-react";
import { t } from "../i18n";

export function Filters({ filters, accounts, onChange, onClear, language }) {
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
      <label className="select-field">
        <WalletCards size={19} />
        <select value={filters.account} onChange={(event) => onChange({ ...filters, account: event.target.value })}>
          <option value="">{t("Todas las cuentas", language)}</option>
          {accounts.map((account) => <option key={account.name}>{account.name}</option>)}
        </select>
      </label>
      <label className="date-field">
        <CalendarDays size={19} />
        <input type="date" value={filters.anchor} onChange={(event) => onChange({ ...filters, anchor: event.target.value })} />
      </label>
      <button className="filter-reset" onClick={onClear} title={t("Limpiar filtros", language)} aria-label={t("Limpiar filtros", language)}>
        <SlidersHorizontal size={20} />
      </button>
    </section>
  );
}
