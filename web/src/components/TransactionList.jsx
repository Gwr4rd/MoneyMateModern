import { ArrowRightLeft, CircleDollarSign, ReceiptText } from "lucide-react";
import { currency } from "../lib/finance";

export function TransactionList({ transactions, currencyCode }) {
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
            <TransactionRow transaction={transaction} currencyCode={currencyCode} key={transaction.id} />
          ))}
        </section>
      ))}
    </div>
  );
}

function TransactionRow({ transaction, currencyCode }) {
  const transfer = transaction.kind === "transfer";
  const income = transaction.kind === "income";
  const Icon = transfer ? ArrowRightLeft : income ? CircleDollarSign : ReceiptText;
  const title = transfer ? "Transferencia" : transaction.category;
  const meta = transfer
    ? `${transaction.account} → ${transaction.toAccount}`
    : `${transaction.account}${transaction.note ? ` · ${transaction.note}` : ""}`;
  return (
    <article className={`transaction-row ${transaction.kind}`}>
      <div className="transaction-icon"><Icon size={20} /></div>
      <div className="transaction-copy">
        <strong>{title}</strong>
        <span>{meta}</span>
      </div>
      <time>{transaction.time}</time>
      <strong className="transaction-amount">{currency(transaction.amount, currencyCode)}</strong>
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
