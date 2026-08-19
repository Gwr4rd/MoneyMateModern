import { ArrowRightLeft, CircleDollarSign, Copy, Pencil, ReceiptText, Trash2 } from "lucide-react";
import { currency } from "../lib/finance";
import { localeFor, t } from "../i18n";

export function TransactionList({ transactions, currencyCode, onEdit, onCopy, onDelete, language }) {
  if (transactions.length === 0) {
    return <div className="empty-state">{t("No se encontraron transacciones.", language)}</div>;
  }
  const groups = Object.groupBy
    ? Object.groupBy(transactions, (transaction) => transaction.date)
    : transactions.reduce((map, transaction) => {
        (map[transaction.date] ||= []).push(transaction);
        return map;
      }, {});

  return (
    <div className="transaction-groups">
      {Object.entries(groups).map(([date, rows]) => (
        <section className="transaction-day" key={date}>
          <h2>{formatDate(date, language)}</h2>
          {rows.map((transaction) => (
            <TransactionRow
              transaction={transaction}
              currencyCode={currencyCode}
              onEdit={onEdit}
              onCopy={onCopy}
              onDelete={onDelete}
              language={language}
              key={transaction.id}
            />
          ))}
        </section>
      ))}
    </div>
  );
}

function TransactionRow({ transaction, currencyCode, onEdit, onCopy, onDelete, language }) {
  const transfer = transaction.kind === "transfer";
  const income = transaction.kind === "income";
  const Icon = transfer ? ArrowRightLeft : income ? CircleDollarSign : ReceiptText;
  const title = transfer ? t("Transferencia", language) : t(transaction.category, language);
  const accountMeta = transfer
    ? `${transaction.account} → ${transaction.toAccount}`
    : transaction.account;
  const note = transaction.note || transaction.description;
  return (
    <article className={`transaction-row ${transaction.kind}`}>
      <div className="transaction-icon"><Icon size={20} /></div>
      <div className="transaction-copy">
        <strong>{title}</strong>
        <span className="transaction-meta">{accountMeta}</span>
        {note ? <span className="transaction-note">{note}</span> : null}
      </div>
      <time>{transaction.time}</time>
      <strong className="transaction-amount">{currency(transaction.amount, currencyCode)}</strong>
      <div className="transaction-actions">
        <button onClick={() => onCopy(transaction)} title={t("Copiar movimiento", language)} aria-label={t("Copiar movimiento", language)}><Copy size={17} /></button>
        <button onClick={() => onEdit(transaction)} title={t("Editar movimiento", language)} aria-label={t("Editar movimiento", language)}><Pencil size={17} /></button>
        <button className="danger" onClick={() => onDelete(transaction)} title={t("Eliminar movimiento", language)} aria-label={t("Eliminar movimiento", language)}><Trash2 size={17} /></button>
      </div>
    </article>
  );
}

function formatDate(value, language) {
  return new Intl.DateTimeFormat(localeFor(language), {
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${value}T12:00:00Z`));
}
