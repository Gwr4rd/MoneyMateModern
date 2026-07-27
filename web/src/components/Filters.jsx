import { CalendarDays, Search, SlidersHorizontal, WalletCards, X } from "lucide-react";

export function Filters({ filters, accounts, onChange, onClear }) {
  return (
    <section className="filters" aria-label="Buscar y filtrar">
      <label className="search-field">
        <Search size={20} />
        <input
          value={filters.query}
          onChange={(event) => onChange({ ...filters, query: event.target.value })}
          placeholder="Buscar cuentas, notas, fechas o importes"
        />
        {filters.query ? (
          <button onClick={() => onChange({ ...filters, query: "" })} aria-label="Limpiar texto">
            <X size={18} />
          </button>
        ) : null}
      </label>
      <label className="select-field">
        <WalletCards size={19} />
        <select value={filters.account} onChange={(event) => onChange({ ...filters, account: event.target.value })}>
          <option value="">Todas las cuentas</option>
          {accounts.map((account) => <option key={account.name}>{account.name}</option>)}
        </select>
      </label>
      <label className="date-field">
        <CalendarDays size={19} />
        <input type="date" value={filters.anchor} onChange={(event) => onChange({ ...filters, anchor: event.target.value })} />
      </label>
      <button className="filter-reset" onClick={onClear} title="Limpiar filtros" aria-label="Limpiar filtros">
        <SlidersHorizontal size={20} />
      </button>
    </section>
  );
}
