import { Banknote, Eye, EyeOff, Landmark, ListTree, Pencil, Plus, Trash2, WalletCards } from "lucide-react";
import { currency } from "../lib/finance";
import { t } from "../i18n";

const hasZeroBalance = (account) => Math.abs(Number(account.currentBalance) || 0) < 0.005;
const isHiddenAccount = (account) => account.hidden || hasZeroBalance(account);

export function AccountsPanel({
  accounts,
  currencyCode,
  full = false,
  showHidden = false,
  onShowHidden,
  onAdd,
  onManage,
  onEdit,
  onToggleHidden,
  onDelete,
  language,
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
  const orderedTypes = [...new Set([
    "Efectivo",
    "Cuentas de Banco",
    ...visible.map((account) => account.type || "Cuentas de Banco"),
  ])];
  const groups = orderedTypes.map((type) => [
    type,
    visible.filter((account) => (account.type || "Cuentas de Banco") === type),
  ]);
  return (
    <section className={`accounts-panel ${full ? "full" : ""}`}>
      <div className="section-heading">
        <div>
          <h2>{t("Cuentas", language)}</h2>
          <span>{t(`${visibleCount} visibles`, language)}</span>
        </div>
        {full ? (
          <div className="section-actions">
            <button className="section-secondary" onClick={onManage}><ListTree size={18} /> {t("Organizar", language)}</button>
            <button className="section-primary" onClick={onAdd}><Plus size={18} /> {t("Nueva cuenta", language)}</button>
          </div>
        ) : <WalletCards size={22} />}
      </div>
      {groups.map(([name, rows]) => rows.length ? (
        <div className="account-group" key={name}>
          <h3>{t(name, language)}</h3>
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
                <span>{automatic ? t("Saldo 0.00 · Oculta automáticamente", language) : account.hidden ? t("Oculta manualmente", language) : t(account.type, language)}</span>
              </div>
              <strong className={account.currentBalance < 0 ? "negative" : ""}>
                {currency(account.currentBalance, currencyCode)}
              </strong>
              {full ? (
                <div className="account-actions">
                  <button onClick={() => onEdit(account)} title={t("Editar cuenta", language)} aria-label={`${t("Editar", language)} ${account.name}`}><Pencil size={17} /></button>
                  <button
                    disabled={automatic}
                    onClick={() => onToggleHidden(account)}
                    title={t(automatic ? "Se mostrará cuando su saldo deje de ser 0.00" : account.hidden ? "Mostrar cuenta" : "Ocultar cuenta", language)}
                    aria-label={automatic ? `${account.name}: ${t("Saldo 0.00 · Oculta automáticamente", language)}` : `${t(account.hidden ? "Mostrar cuenta" : "Ocultar cuenta", language)}: ${account.name}`}
                  >
                    {hidden ? <Eye size={18} /> : <EyeOff size={18} />}
                  </button>
                  <button className="danger" onClick={() => onDelete(account)} title={t("Eliminar cuenta", language)} aria-label={`${t("Eliminar", language)} ${account.name}`}><Trash2 size={17} /></button>
                </div>
              ) : null}
            </article>
          )})}
        </div>
      ) : null)}
      {full && (hiddenCount > 0 || showHidden) ? (
        <button className="hidden-toggle" onClick={onShowHidden}>
          {showHidden ? <EyeOff size={16} /> : <Eye size={16} />}
          {t(showHidden ? "Ocultar cuentas ocultas" : `Mostrar cuentas ocultas (${hiddenCount})`, language)}
        </button>
      ) : null}
    </section>
  );
}
