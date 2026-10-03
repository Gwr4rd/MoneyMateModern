import { useMemo, useState } from "react";
import { BarChart3, FileSpreadsheet, PieChart } from "lucide-react";
import { accountFlowTotals, currency, inRange, rangeFor, statusSummary } from "../lib/finance";
import { t } from "../i18n";
import { PeriodSelect } from "./Filters";

const palette = ["#ff3b5c", "#ff8a34", "#ffca28", "#00b894", "#00a8e8", "#3d5afe", "#a855f7", "#ec4899"];

export function StatusPanel({ data, scope, anchor, kind, onScope, onKind, onExport, language, compact = false }) {
  const [chart, setChart] = useState("distribution");
  const range = useMemo(() => rangeFor(scope, anchor, language), [scope, anchor, language]);
  const rows = useMemo(() => data.transactions.filter((transaction) => inRange(transaction, range)), [data.transactions, range]);
  const totals = useMemo(() => accountFlowTotals(rows, kind), [rows, kind]);
  const summaryValue = useMemo(() => statusSummary(rows), [rows]);
  const total = totals.reduce((sum, item) => sum + item.value, 0);
  const gradient = donutGradient(totals, total);

  return (
    <section className={`status-panel ${compact ? "compact" : ""}`}>
      <div className="section-heading">
        <div>
          <h2>{t("Estado", language)}</h2>
          <span>{range.label}</span>
        </div>
        <div className="status-heading-actions">
          {!compact ? <button className="status-export" onClick={() => onExport(scope, anchor)}><FileSpreadsheet size={17} /> {t("Exportar estado XLSX", language)}</button> : null}
          <div className="kind-switch">
          <button className={kind === "income" ? "active" : ""} onClick={() => onKind("income")}>{t("Ingresos", language)}</button>
          <button className={kind === "expense" ? "active" : ""} onClick={() => onKind("expense")}>{t("Gastos", language)}</button>
          </div>
        </div>
      </div>
      <div className="status-controls">
        {!compact ? <PeriodSelect value={scope} onChange={onScope} language={language} /> : null}
        <div className="chart-switch" aria-label={t("Grafico", language)}>
          <button type="button" className={chart === "distribution" ? "active" : ""} onClick={() => setChart("distribution")} aria-label={t("Distribución por cuenta", language)} title={t("Distribución por cuenta", language)}><PieChart size={18} /></button>
          <button type="button" className={chart === "bars" ? "active" : ""} onClick={() => setChart("bars")} aria-label={t("Barras por cuenta", language)} title={t("Barras por cuenta", language)}><BarChart3 size={18} /></button>
        </div>
      </div>
      {!compact ? <div className="status-metrics">
        <div><span>{t("Ingresos", language)}</span><strong className="income">{currency(summaryValue.income, data.currency)}</strong></div>
        <div><span>{t("Gastos", language)}</span><strong className="expense">{currency(summaryValue.expense, data.currency)}</strong></div>
        <div><span>{t("Balance", language)}</span><strong>{currency(summaryValue.balance, data.currency)}</strong></div>
      </div> : null}
      <p className="chart-caption">{t(chart === "distribution" ? "Distribución por cuenta" : "Barras por cuenta", language)}</p>
      {chart === "distribution" ? <div className="chart-layout">
        <div className="donut" style={{ background: gradient }}>
          <div>
            <strong>{currency(kind === "income" ? summaryValue.income : summaryValue.expense, data.currency)}</strong>
            <span>{t("Total", language)}</span>
          </div>
        </div>
        <div className="legend">
          {totals.length ? totals.map((item, index) => (
            <div className="legend-row" key={item.label}>
              <i style={{ backgroundColor: palette[index % palette.length] }} />
              <span>{item.label}</span>
              <strong>{total ? Math.round(item.value * 100 / total) : 0}%</strong>
              <small>{currency(item.value, data.currency)}</small>
            </div>
          )) : <div className="empty-chart">{t("Sin datos en este periodo.", language)}</div>}
        </div>
      </div> : <div className="status-bars">
        {totals.length ? totals.map((item, index) => (
          <div className="status-bar-row" key={item.label}>
            <div><strong>{item.label}</strong><span>{currency(item.value, data.currency)}</span></div>
            <div className="status-bar-track"><i style={{ width: `${Math.max(2, item.value * 100 / (totals[0].value || 1))}%`, backgroundColor: palette[index % palette.length] }} /></div>
          </div>
        )) : <div className="empty-chart">{t("Sin datos en este periodo.", language)}</div>}
      </div>}
    </section>
  );
}

function donutGradient(items, total) {
  if (!total) return "conic-gradient(#dce3df 0 100%)";
  let current = 0;
  const stops = [];
  items.forEach((item, index) => {
    const next = current + item.value * 100 / total;
    const color = palette[index % palette.length];
    stops.push(`${color} ${current}% ${next}%`);
    current = next;
  });
  if (current < 100) stops.push(`#cfd8d3 ${current}% 100%`);
  return `conic-gradient(${stops.join(",")})`;
}
