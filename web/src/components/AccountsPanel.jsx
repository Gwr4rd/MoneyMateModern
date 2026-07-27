import { Banknote, Eye, EyeOff, Landmark, Pencil, Plus, Trash2, WalletCards } from "lucide-react";
import { currency } from "../lib/finance";

export function AccountsPanel({
  accounts,
  currencyCode,
  full = false,
  showHidden = false,
  onShowHidden,
  onAdd,
  onEdit,
  onToggleHidden,
  onDelete,
}) {
  const hiddenCount = accounts.filter((account) => account.hidden).length;
  const visible = accounts
    .filter((account) => full && showHidden ? true : !account.hidden)
    .sort((left, right) => {
      if (left.hidden !== right.hidden) return left.hidden ? 1 : -1;
      const leftEmpty = Math.abs(left.currentBalance) < 0.005;
      const rightEmpty = Math.abs(right.currentBalance) < 0.005;
      return leftEmpty === rightEmpty ? left.name.localeCompare(right.name, "es") : leftEmpty ? 1 : -1;
    });
  const groups = [
    ["Efectivo", visible.filter((account) => account.type === "Efectivo")],
    ["Cuentas de Banco", visible.filter((account) => account.type !== "Efectivo")],
  ];
  return (
    <section className={`accounts-panel ${full ? "full" : ""}`}>
      <div className="section-heading">
        <div>
          <h2>Cuentas</h2>
          <span>{visible.length} visibles</span>
        </div>
        {full ? (
          <button className="section-primary" onClick={onAdd}><Plus size={18} /> Nueva cuenta</button>
        ) : <WalletCards size={22} />}
      </div>
      {groups.map(([name, rows]) => rows.length ? (
        <div className="account-group" key={name}>
          <h3>{name}</h3>
          {rows.map((account) => (
            <article className={`account-row ${account.hidden ? "hidden" : ""}`} key={account.name}>
              <div className="account-icon">
                {account.type === "Efectivo" ? <Banknote size={21} /> : <Landmark size={21} />}
              </div>
              <div>
                <strong>{account.name}</strong>
                <span>{account.type}</span>
              </div>
              <strong className={account.currentBalance < 0 ? "negative" : ""}>
                {currency(account.currentBalance, currencyCode)}
              </strong>
              {full ? (
                <div className="account-actions">
                  <button onClick={() => onEdit(account)} title="Editar cuenta" aria-label={`Editar ${account.name}`}><Pencil size={17} /></button>
                  <button onClick={() => onToggleHidden(account)} title={account.hidden ? "Mostrar cuenta" : "Ocultar cuenta"} aria-label={account.hidden ? `Mostrar ${account.name}` : `Ocultar ${account.name}`}>
                    {account.hidden ? <Eye size={18} /> : <EyeOff size={18} />}
                  </button>
                  <button className="danger" onClick={() => onDelete(account)} title="Eliminar cuenta" aria-label={`Eliminar ${account.name}`}><Trash2 size={17} /></button>
                </div>
              ) : null}
            </article>
          ))}
        </div>
      ) : null)}
      {full && (hiddenCount > 0 || showHidden) ? (
        <button className="hidden-toggle" onClick={onShowHidden}>
          {showHidden ? <EyeOff size={16} /> : <Eye size={16} />}
          {showHidden ? "Ocultar cuentas ocultas" : `Mostrar cuentas ocultas (${hiddenCount})`}
        </button>
      ) : null}
    </section>
  );
}
