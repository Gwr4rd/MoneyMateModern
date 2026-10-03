import { useState } from "react";
import { ArrowRightLeft, CircleDollarSign, Copy, MoreHorizontal, Pencil, ReceiptText, Trash2 } from "lucide-react";
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
  const [expanded, setExpanded] = useState(false);
  const transfer = transaction.kind === "transfer";
  const income = transaction.kind === "income";
  const Icon = transfer ? ArrowRightLeft : income ? CircleDollarSign : ReceiptText;
  const movementLabel = t(transfer ? "Transferencia" : income ? "Ingreso" : "Gasto", language);
  const title = transaction.note?.trim() || movementLabel;
  const accountMeta = transfer
    ? `${transaction.account} → ${transaction.toAccount}`
    : transaction.account;
  return (
    <article className={`transaction-row ${transaction.kind}`}>
      <button type="button" className="transaction-main" onClick={() => setExpanded((value) => !value)} aria-expanded={expanded}>
        <span className="transaction-icon"><Icon size={20} /></span>
        <span className="transaction-copy">
          <strong className="transaction-note-primary">{title}</strong>
          <span className="transaction-meta">{movementLabel} · {accountMeta} · {transaction.time}</span>
        </span>
        <strong className="transaction-amount">{currency(transaction.amount, currencyCode)}</strong>
      </button>
      <details className="transaction-actions">
        <summary title={t("Acciones", language)} aria-label={t("Acciones", language)}><MoreHorizontal size={20} /></summary>
        <div className="transaction-action-menu">
          <button type="button" aria-label={t("Copiar", language)} onClick={() => onCopy(transaction)}><Copy size={17} />{t("Copiar", language)}</button>
          <button type="button" aria-label={t("Editar", language)} onClick={() => onEdit(transaction)}><Pencil size={17} />{t("Editar", language)}</button>
          <button type="button" className="danger" aria-label={t("Eliminar", language)} onClick={() => onDelete(transaction)}><Trash2 size={17} />{t("Eliminar", language)}</button>
        </div>
      </details>
      {expanded ? <div className="transaction-detail"><span>{transaction.date} · {transaction.time}</span><span>{accountMeta}</span>{transaction.description ? <span>{transaction.description}</span> : null}</div> : null}
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
