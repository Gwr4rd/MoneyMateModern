import { Banknote, Eye, EyeOff, Landmark, Pencil, Plus, Trash2, WalletCards } from "lucide-react";
import { currency } from "../lib/finance";

const hasZeroBalance = (account) => Math.abs(Number(account.currentBalance) || 0) < 0.005;
const isHiddenAccount = (account) => account.hidden || hasZeroBalance(account);

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
  const hiddenCount = accounts.filter(isHiddenAccount).length;
  const visibleCount = accounts.length - hiddenCount;
  const visible = accounts
    .filter((account) => full && showHidden ? true : !isHiddenAccount(account))
    .sort((left, right) => {
      const leftHidden = isHiddenAccount(left);
      const rightHidden = isHiddenAccount(right);
      if (leftHidden !== rightHidden) return leftHidden ? 1 : -1;
      return left.name.localeCompare(right.name, "es");
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
          <span>{visibleCount} visibles</span>
        </div>
        {full ? (
          <button className="section-primary" onClick={onAdd}><Plus size={18} /> Nueva cuenta</button>
        ) : <WalletCards size={22} />}
      </div>
      {groups.map(([name, rows]) => rows.length ? (
        <div className="account-group" key={name}>
          <h3>{name}</h3>
          {rows.map((account) => {
            const automatic = hasZeroBalance(account);
            const hidden = isHiddenAccount(account);
            return (
            <article className={`account-row ${hidden ? "hidden" : ""}`} key={account.name}>
              <div className="account-icon">
                {account.type === "Efectivo" ? <Banknote size={21} /> : <Landmark size={21} />}
              </div>
              <div>
                <strong>{account.name}</strong>
                <span>{automatic ? "Saldo 0.00 · Oculta automáticamente" : account.hidden ? "Oculta manualmente" : account.type}</span>
              </div>
              <strong className={account.currentBalance < 0 ? "negative" : ""}>
                {currency(account.currentBalance, currencyCode)}
              </strong>
              {full ? (
                <div className="account-actions">
                  <button onClick={() => onEdit(account)} title="Editar cuenta" aria-label={`Editar ${account.name}`}><Pencil size={17} /></button>
                  <button
                    disabled={automatic}
                    onClick={() => onToggleHidden(account)}
                    title={automatic ? "Se mostrará cuando su saldo deje de ser 0.00" : account.hidden ? "Mostrar cuenta" : "Ocultar cuenta"}
                    aria-label={automatic ? `${account.name} oculta por saldo cero` : account.hidden ? `Mostrar ${account.name}` : `Ocultar ${account.name}`}
                  >
                    {hidden ? <Eye size={18} /> : <EyeOff size={18} />}
                  </button>
                  <button className="danger" onClick={() => onDelete(account)} title="Eliminar cuenta" aria-label={`Eliminar ${account.name}`}><Trash2 size={17} /></button>
                </div>
              ) : null}
            </article>
          )})}
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
