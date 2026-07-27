import { useMemo } from "react";
import { categoryTotals, currency, inRange, rangeFor, summary } from "../lib/finance";

const scopes = [
  ["anual", "Anual"],
  ["mensual", "Mensual"],
  ["semanal", "Semanal"],
  ["diario", "Diario"],
  ["todo", "Todo"],
];
const palette = ["#ee5c63", "#f0a34d", "#f2c94c", "#4da47d", "#5a8fdc", "#a16fd0"];

export function StatusPanel({ data, scope, anchor, kind, onScope, onKind, compact = false }) {
  const range = useMemo(() => rangeFor(scope, anchor), [scope, anchor]);
  const rows = useMemo(() => data.transactions.filter((transaction) => inRange(transaction, range)), [data.transactions, range]);
  const totals = useMemo(() => categoryTotals(rows, kind), [rows, kind]);
  const summaryValue = useMemo(() => summary(rows), [rows]);
  const total = totals.reduce((sum, item) => sum + item.value, 0);
  const gradient = donutGradient(totals, total);

  return (
    <section className={`status-panel ${compact ? "compact" : ""}`}>
      <div className="section-heading">
        <div>
          <h2>Estado</h2>
          <span>{range.label}</span>
        </div>
        <div className="kind-switch">
          <button className={kind === "income" ? "active" : ""} onClick={() => onKind("income")}>Ingresos</button>
          <button className={kind === "expense" ? "active" : ""} onClick={() => onKind("expense")}>Gastos</button>
        </div>
      </div>
      <div className="scope-tabs">
        {scopes.map(([value, label]) => (
          <button className={scope === value ? "active" : ""} onClick={() => onScope(value)} key={value}>{label}</button>
        ))}
      </div>
      <div className="chart-layout">
        <div className="donut" style={{ background: gradient }}>
          <div>
            <strong>{currency(kind === "income" ? summaryValue.income : summaryValue.expense, data.currency)}</strong>
            <span>Total</span>
          </div>
        </div>
        <div className="legend">
          {totals.length ? totals.slice(0, 6).map((item, index) => (
            <div className="legend-row" key={item.label}>
              <i style={{ backgroundColor: palette[index % palette.length] }} />
              <span>{item.label}</span>
              <strong>{total ? Math.round(item.value * 100 / total) : 0}%</strong>
              <small>{currency(item.value, data.currency)}</small>
            </div>
          )) : <div className="empty-chart">Sin datos en este periodo.</div>}
        </div>
      </div>
    </section>
  );
}

function donutGradient(items, total) {
  if (!total) return "conic-gradient(#dce3df 0 100%)";
  let current = 0;
  const stops = [];
  items.slice(0, 6).forEach((item, index) => {
    const next = current + item.value * 100 / total;
    const color = palette[index % palette.length];
    stops.push(`${color} ${current}% ${next}%`);
    current = next;
  });
  if (current < 100) stops.push(`#cfd8d3 ${current}% 100%`);
  return `conic-gradient(${stops.join(",")})`;
}
