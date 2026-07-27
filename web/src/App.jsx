import { useEffect, useMemo, useRef, useState } from "react";
import { Plus } from "lucide-react";
import { Header, MobileNav, SideNav } from "./components/AppChrome";
import { AccountsPanel } from "./components/AccountsPanel";
import { MovementDialog, ReportDialog, SyncDialog } from "./components/Dialogs";
import { Filters } from "./components/Filters";
import { StatusPanel } from "./components/StatusPanel";
import { Summary } from "./components/Summary";
import { TransactionList } from "./components/TransactionList";
import { seedData, STORAGE_KEY } from "./data";
import {
  accountBalances,
  filterTransactions,
  inRange,
  rangeFor,
  reportRows,
  summary,
  today,
} from "./lib/finance";
import { createAccount, downloadSnapshot, uploadSnapshot } from "./lib/supabase";

export default function App() {
  const [data, setData] = useState(loadData);
  const [active, setActive] = useState("transactions");
  const [dark, setDark] = useState(() => localStorage.getItem("moneymate-theme") === "dark");
  const [filters, setFilters] = useState({ query: "", account: "", anchor: latestDate(data.transactions) });
  const [scope, setScope] = useState("mensual");
  const [statusKind, setStatusKind] = useState("expense");
  const [dialog, setDialog] = useState(null);
  const [syncBusy, setSyncBusy] = useState(false);
  const [syncMessage, setSyncMessage] = useState("");
  const searchRef = useRef(null);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  }, [data]);

  useEffect(() => {
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    localStorage.setItem("moneymate-theme", dark ? "dark" : "light");
  }, [dark]);

  const range = useMemo(() => rangeFor(scope, filters.anchor), [scope, filters.anchor]);
  const visibleTransactions = useMemo(
    () => filterTransactions(data.transactions, { ...filters, range }),
    [data.transactions, filters, range],
  );
  const summaryValue = useMemo(() => summary(visibleTransactions), [visibleTransactions]);
  const accounts = useMemo(() => accountBalances(data), [data]);

  function navigate(next) {
    setActive(next);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function openSearch() {
    setActive("transactions");
    requestAnimationFrame(() => searchRef.current?.querySelector("input")?.focus());
  }

  function saveMovement(transaction) {
    setData((current) => ({
      ...current,
      transactions: [transaction, ...current.transactions].sort(sortTransactions),
    }));
    setDialog(null);
    setFilters((current) => ({ ...current, anchor: transaction.date }));
  }

  async function syncUpload(email, password) {
    setSyncBusy(true);
    setSyncMessage("");
    try {
      await uploadSnapshot(email, password, data);
      setSyncMessage("Copia subida correctamente.");
    } catch (error) {
      setSyncMessage(error.message);
    } finally {
      setSyncBusy(false);
    }
  }

  async function syncDownload(email, password) {
    if (!window.confirm("La copia de Supabase reemplazara los datos guardados en este navegador.")) return;
    setSyncBusy(true);
    setSyncMessage("");
    try {
      const remote = await downloadSnapshot(email, password);
      setData(normalizeSnapshot(remote.data));
      setSyncMessage("Datos descargados correctamente.");
    } catch (error) {
      setSyncMessage(error.message);
    } finally {
      setSyncBusy(false);
    }
  }

  async function syncCreateAccount(email, password) {
    setSyncBusy(true);
    setSyncMessage("");
    try {
      const result = await createAccount(email, password);
      setSyncMessage(result.session
        ? "Cuenta creada y conectada. Ya puedes subir tus datos."
        : "Cuenta creada. Revisa tu correo para confirmarla antes de iniciar sesion.");
    } catch (error) {
      setSyncMessage(error.message);
    } finally {
      setSyncBusy(false);
    }
  }

  async function exportReport(reportScope, anchor, format) {
    const reportRange = rangeFor(reportScope, anchor);
    const rows = data.transactions.filter((transaction) => inRange(transaction, reportRange));
    const XLSX = await import("xlsx");
    const worksheet = XLSX.utils.json_to_sheet(reportRows(rows));
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Reporte");
    const extension = format === "xls" ? "xls" : "xlsx";
    XLSX.writeFile(workbook, `moneymate_reporte_${reportScope}_${anchor}.${extension}`, {
      bookType: format === "xls" ? "biff8" : "xlsx",
    });
    setDialog(null);
  }

  return (
    <div className="app-shell">
      <Header
        active={active}
        dark={dark}
        onNav={navigate}
        onTheme={() => setDark((value) => !value)}
        onSearch={openSearch}
        onNew={() => setDialog("movement")}
        onReport={() => setDialog("report")}
        onSync={() => { setSyncMessage(""); setDialog("sync"); }}
      />
      <div className="app-body">
        <SideNav active={active} onChange={navigate} />
        <main className="main-content">
          {active === "transactions" ? (
            <>
              <Summary value={summaryValue} currencyCode={data.currency} />
              <button className="mobile-new" onClick={() => setDialog("movement")}><Plus size={20} /> Nuevo movimiento</button>
              <div ref={searchRef}>
                <Filters
                  filters={filters}
                  accounts={data.accounts}
                  onChange={setFilters}
                  onClear={() => setFilters({ query: "", account: "", anchor: today() })}
                />
              </div>
              <div className="scope-row">
                {["anual", "mensual", "semanal", "diario", "todo"].map((value) => (
                  <button className={scope === value ? "active" : ""} onClick={() => setScope(value)} key={value}>
                    {scopeLabel(value)}
                  </button>
                ))}
              </div>
              <TransactionList transactions={visibleTransactions} currencyCode={data.currency} />
            </>
          ) : null}
          {active === "status" ? (
            <StatusPanel
              data={data}
              scope={scope}
              anchor={filters.anchor}
              kind={statusKind}
              onScope={setScope}
              onKind={setStatusKind}
            />
          ) : null}
          {active === "accounts" ? <AccountsPanel accounts={accounts} currencyCode={data.currency} full /> : null}
        </main>
        <aside className="context-rail">
          <StatusPanel
            data={data}
            scope={scope}
            anchor={filters.anchor}
            kind={statusKind}
            onScope={setScope}
            onKind={setStatusKind}
            compact
          />
          <AccountsPanel accounts={accounts} currencyCode={data.currency} />
        </aside>
      </div>
      <MobileNav active={active} onChange={navigate} />
      {dialog === "movement" ? <MovementDialog data={data} onClose={() => setDialog(null)} onSave={saveMovement} /> : null}
      {dialog === "sync" ? (
        <SyncDialog
          onClose={() => setDialog(null)}
          onUpload={syncUpload}
          onDownload={syncDownload}
          onCreateAccount={syncCreateAccount}
          busy={syncBusy}
          message={syncMessage}
        />
      ) : null}
      {dialog === "report" ? <ReportDialog onClose={() => setDialog(null)} onExport={exportReport} /> : null}
    </div>
  );
}

function loadData() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return saved?.version === 2 ? normalizeSnapshot(saved) : seedData;
  } catch {
    return seedData;
  }
}

function normalizeSnapshot(snapshot) {
  return {
    version: 2,
    currency: snapshot.currency || "PEN",
    accounts: Array.isArray(snapshot.accounts) ? snapshot.accounts : [],
    categories: Array.isArray(snapshot.categories) ? snapshot.categories : [],
    transactions: (Array.isArray(snapshot.transactions) ? snapshot.transactions : [])
      .map((transaction) => ({
        id: transaction.id || crypto.randomUUID(),
        date: transaction.date || today(),
        time: transaction.time || "00:00",
        kind: transaction.kind || "expense",
        account: transaction.account || "Cuenta",
        toAccount: transaction.toAccount || "",
        category: transaction.category || "Sin categoria",
        amount: Number(transaction.amount) || 0,
        note: transaction.note || "",
        description: transaction.description || "",
      }))
      .sort(sortTransactions),
  };
}

function sortTransactions(left, right) {
  return `${right.date} ${right.time}`.localeCompare(`${left.date} ${left.time}`);
}

function latestDate(transactions) {
  return [...transactions].sort(sortTransactions)[0]?.date || today();
}

function scopeLabel(scope) {
  return scope === "anual" ? "Anual" : scope === "semanal" ? "Semanal" : scope === "diario" ? "Diario" : scope === "todo" ? "Todo" : "Mensual";
}
