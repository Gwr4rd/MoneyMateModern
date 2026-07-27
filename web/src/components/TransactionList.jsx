import { ArrowRightLeft, CircleDollarSign, Copy, Pencil, ReceiptText, Trash2 } from "lucide-react";
import { currency } from "../lib/finance";

export function TransactionList({ transactions, currencyCode, onEdit, onCopy, onDelete }) {
  if (transactions.length === 0) {
    return <div className="empty-state">No se encontraron transacciones.</div>;
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
          <h2>{formatDate(date)}</h2>
          {rows.map((transaction) => (
            <TransactionRow
              transaction={transaction}
              currencyCode={currencyCode}
              onEdit={onEdit}
              onCopy={onCopy}
              onDelete={onDelete}
              key={transaction.id}
            />
          ))}
        </section>
      ))}
    </div>
  );
}

function TransactionRow({ transaction, currencyCode, onEdit, onCopy, onDelete }) {
  const transfer = transaction.kind === "transfer";
  const income = transaction.kind === "income";
  const Icon = transfer ? ArrowRightLeft : income ? CircleDollarSign : ReceiptText;
  const title = transfer ? "Transferencia" : transaction.category;
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
        <button onClick={() => onCopy(transaction)} title="Copiar movimiento" aria-label="Copiar movimiento"><Copy size={17} /></button>
        <button onClick={() => onEdit(transaction)} title="Editar movimiento" aria-label="Editar movimiento"><Pencil size={17} /></button>
        <button className="danger" onClick={() => onDelete(transaction)} title="Eliminar movimiento" aria-label="Eliminar movimiento"><Trash2 size={17} /></button>
      </div>
    </article>
  );
}

function formatDate(value) {
  return new Intl.DateTimeFormat("es-PE", {
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${value}T12:00:00Z`));
}
