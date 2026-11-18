import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowRightLeft, CircleDollarSign, Copy, MoreHorizontal, Pencil, ReceiptText, Trash2 } from "lucide-react";
import { localeFor, t } from "../i18n";

const PAGE_SIZE = 60;

export function TransactionList({ transactions, currencyCode = "PEN", onEdit, onCopy, onDelete, language }) {
  const [limit, setLimit] = useState(PAGE_SIZE);
  const more = useRef(null);
  const pageSize = Math.min(limit, transactions.length);
  const hasMore = pageSize < transactions.length;
  const groups = useMemo(() => {
    const byDay = new Map();
    for (const transaction of transactions.slice(0, pageSize)) {
      if (!byDay.has(transaction.date)) byDay.set(transaction.date, []);
      byDay.get(transaction.date).push(transaction);
    }
    return byDay;
  }, [transactions, pageSize]);
  const dateFormat = useMemo(() => new Intl.DateTimeFormat(localeFor(language), {
    day: "numeric", month: "long", year: "numeric", timeZone: "UTC",
  }), [language]);
  const moneyFormat = useMemo(() => new Intl.NumberFormat("es-PE", {
    style: "currency", currency: currencyCode, minimumFractionDigits: 2,
  }), [currencyCode]);

  useEffect(() => {
    if (!hasMore || !more.current || typeof IntersectionObserver === "undefined") return undefined;
    const observer = new IntersectionObserver(([entry]) => {
      if (!entry?.isIntersecting) return;
      observer.disconnect();
      setLimit((previous) => previous + PAGE_SIZE);
    }, { rootMargin: "0px 0px 400px" });
    observer.observe(more.current);
    return () => observer.disconnect();
  }, [hasMore, pageSize]);

  if (transactions.length === 0) {
    return <div className="empty-state">{t("No se encontraron transacciones.", language)}</div>;
  }

  return (
    <div className="transaction-groups">
      {[...groups].map(([date, rows]) => (
        <section className="transaction-day" key={date}>
          <h2>{dateFormat.format(new Date(`${date}T12:00:00Z`))}</h2>
          {rows.map((transaction) => (
            <TransactionRow
              transaction={transaction}
              formattedAmount={moneyFormat.format(transaction.amount)}
              onEdit={onEdit}
              onCopy={onCopy}
              onDelete={onDelete}
              language={language}
              key={transaction.id}
            />
          ))}
        </section>
      ))}
      {hasMore ? <button type="button" className="transaction-more" ref={more}
        onClick={() => setLimit((previous) => previous + PAGE_SIZE)}>{t("Cargar más movimientos", language)}</button> : null}
    </div>
  );
}

function TransactionRow({ transaction, formattedAmount, onEdit, onCopy, onDelete, language }) {
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
        <strong className="transaction-amount">{formattedAmount}</strong>
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
