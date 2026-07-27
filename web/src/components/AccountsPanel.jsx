import { Banknote, Landmark, WalletCards } from "lucide-react";
import { currency } from "../lib/finance";

export function AccountsPanel({ accounts, currencyCode, full = false }) {
  const visible = accounts.filter((account) => !account.hidden);
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
        <WalletCards size={22} />
      </div>
      {groups.map(([name, rows]) => rows.length ? (
        <div className="account-group" key={name}>
          <h3>{name}</h3>
          {rows.map((account) => (
            <article className="account-row" key={account.name}>
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
            </article>
          ))}
        </div>
      ) : null)}
    </section>
  );
}
