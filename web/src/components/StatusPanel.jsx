import { useMemo } from "react";
import { FileSpreadsheet } from "lucide-react";
import { accountFlowTotals, accountFlowsByType, currency, inRange, rangeFor, statusSummary } from "../lib/finance";
import { t } from "../i18n";
import { PeriodSelect } from "./Filters";

const palette = ["#00a87d", "#3478f6", "#e25b72", "#ea900e", "#835ad6", "#149bb2"];
const kinds = [["income", "Ingresos"], ["expense", "Gastos"]];

export function StatusPanel({ data, scope, anchor, onScope, onAnchor, onExport, language }) {
  const range = useMemo(() => rangeFor(scope, anchor, language), [scope, anchor, language]);
  const rows = useMemo(() => data.transactions.filter((row) => inRange(row, range)), [data.transactions, range]);
  const flows = accountFlowsByType(rows, data.accounts);
  const colors = new Map(flows.map(({ label }, index) => [label, palette[index % palette.length]]));
  const scale = flows.reduce((max, row) => Math.max(max, row.income, row.expense), 0);
  const totals = statusSummary(rows);
  const groups = new Map();
  for (const flow of flows) {
    if (!groups.has(flow.type)) groups.set(flow.type, []);
    groups.get(flow.type).push(flow);
  }

  return (
    <section className="status-overview">
      <h1 className="page-heading">{t("Estado", language)}</h1>
      <div className="status-toolbar">
        <PeriodSelect value={scope} onChange={onScope} language={language} />
        {scope !== "todo" ? <input type="date" aria-label={t("Fecha", language)} value={anchor} onChange={(event) => onAnchor(event.target.value)} /> : null}
        <button className="status-export" onClick={() => onExport(scope, anchor)}><FileSpreadsheet size={17} /> {t("Exportar estado XLSX", language)}</button>
      </div>
      <div className="status-period">
        <h2>{range.label}</h2>
        <span>{t("Balance", language)} <strong>{currency(totals.balance, data.currency)}</strong></span>
      </div>
      <div className="paired-charts">
        {kinds.map(([kind, label]) => {
          const items = accountFlowTotals(rows, kind);
          return <figure key={kind} className={`flow-chart ${kind}`}>
            <figcaption>{t(label, language)}</figcaption>
            <div className="flow-ring" style={{ background: donutGradient(items, totals[kind], colors) }} aria-hidden="true">
              <div />
            </div>
            <strong>{currency(totals[kind], data.currency)}</strong>
            {items.length > 0 && <ul className="flow-legend">
              {items.map(({ label: account, value }) => <li key={account}>
                <i style={{ background: colors.get(account) }} />
                <span>{account}</span><span>{Math.round(100 * value / totals[kind])}%</span>
              </li>)}
            </ul>}
          </figure>;
        })}
      </div>
      {flows.length === 0 ? <p className="empty-chart">{t("Sin datos en este periodo.", language)}</p> : null}
      {[...groups].map(([type, items]) => <section className="flow-group" key={type}>
        <h2>{t(type, language)}</h2>
        {items.map((item) => <div className="account-flow" key={item.label}>
          <h3>{item.label}</h3>
          {kinds.map(([kind, label]) => <div className={`account-flow-line ${kind}`} key={kind}>
            <span>{t(label, language)}</span>
            <div className="flow-track" aria-hidden="true"><i style={{ width: `${scale ? 100 * item[kind] / scale : 0}%` }} /></div>
            <strong>{currency(item[kind], data.currency)}</strong>
          </div>)}
        </div>)}
      </section>)}
    </section>
  );
}

function donutGradient(items, total, colors) {
  if (total <= 0) return "var(--surface-2)";
  let current = 0;
  const stops = items.map((item) => {
    const start = current;
    current += 100 * item.value / total;
    return `${colors.get(item.label)} ${start}% ${current}%`;
  });
  return `conic-gradient(${stops.join(", ")})`;
}
