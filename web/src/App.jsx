import { useEffect, useMemo, useRef, useState } from "react";
import { Plus } from "lucide-react";
import { Header, MobileNav, SideNav } from "./components/AppChrome";
import { AccountsPanel } from "./components/AccountsPanel";
import {
  AccountDialog,
  AboutDialog,
  BackupDialog,
  CurrencyDialog,
  MetadataDialog,
  LanguageDialog,
  MovementDialog,
  ReportDialog,
  SyncDialog,
} from "./components/Dialogs";
import { Filters } from "./components/Filters";
import { StatusPanel } from "./components/StatusPanel";
import { Summary } from "./components/Summary";
import { TransactionList } from "./components/TransactionList";
import { APP_VERSION, seedData, STORAGE_KEY } from "./data";
import { t } from "./i18n";
import {
  accountBalances,
  filterTransactions,
  inRange,
  rangeFor,
  reportRows,
  statusReportRows,
  summary,
  today,
} from "./lib/finance";
import { IMPORT_UNDO_KEY, normalizeSnapshot } from "./lib/backup";
import {
  createAccount,
  downloadSnapshot,
  getCurrentUser,
  signIn,
  signOut,
  SYNC_CONFLICT_KEY,
  SYNC_PENDING_KEY,
  SYNC_REMOTE_KEY,
  uploadSnapshot,
} from "./lib/supabase";

export default function App() {
  const [data, setData] = useState(loadData);
  const [active, setActive] = useState("transactions");
  const [dark, setDark] = useState(() => localStorage.getItem("moneymate-theme") === "dark");
  const [language, setLanguage] = useState(() => localStorage.getItem("moneymate-language") || "es");
  const [filters, setFilters] = useState({ query: "", account: "", anchor: latestDate(data.transactions) });
  const [scope, setScope] = useState("mensual");
  const [statusKind, setStatusKind] = useState("expense");
  const [showHiddenAccounts, setShowHiddenAccounts] = useState(false);
  const [dialog, setDialog] = useState(null);
  const [syncBusy, setSyncBusy] = useState(false);
  const [syncMessage, setSyncMessage] = useState("");
  const [syncUser, setSyncUser] = useState(null);
  const [syncRevision, setSyncRevision] = useState(0);
  const [syncConflict, setSyncConflict] = useState(() => localStorage.getItem(SYNC_CONFLICT_KEY) === "1");
  const searchRef = useRef(null);
  const syncTimerRef = useRef(null);
  const syncOperationRef = useRef(false);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  }, [data]);

  useEffect(() => {
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    localStorage.setItem("moneymate-theme", dark ? "dark" : "light");
  }, [dark]);

  useEffect(() => {
    localStorage.setItem("moneymate-language", language);
    document.documentElement.lang = language;
  }, [language]);

  useEffect(() => {
    let active = true;
    getCurrentUser()
      .then((user) => {
        if (!active) return;
        setSyncUser(user);
        if (user && localStorage.getItem(SYNC_PENDING_KEY) === "1") {
          setSyncRevision((value) => value + 1);
        }
      })
      .catch((error) => active && setSyncMessage(error.message));
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!syncUser || syncRevision === 0) return undefined;
    window.clearTimeout(syncTimerRef.current);
    syncTimerRef.current = window.setTimeout(async () => {
      if (syncOperationRef.current) {
        setSyncRevision((value) => value + 1);
        return;
      }
      syncOperationRef.current = true;
      try {
        if (await hasRemoteSyncConflict()) {
          localStorage.setItem(SYNC_CONFLICT_KEY, "1");
          setSyncConflict(true);
          setSyncMessage("Hay cambios locales y en la nube. Abre Sincronización y elige qué copia conservar.");
          return;
        }
        const result = await uploadSnapshot(data);
        if (result?.updated_at) localStorage.setItem(SYNC_REMOTE_KEY, result.updated_at);
        localStorage.removeItem(SYNC_PENDING_KEY);
        localStorage.removeItem(SYNC_CONFLICT_KEY);
        setSyncConflict(false);
        setSyncMessage("Cambios guardados automáticamente.");
      } catch (error) {
        localStorage.setItem(SYNC_PENDING_KEY, "1");
        setSyncMessage(`Sincronización pendiente: ${error.message}`);
        syncTimerRef.current = window.setTimeout(
          () => setSyncRevision((value) => value + 1),
          15000,
        );
      } finally {
        syncOperationRef.current = false;
      }
    }, 900);
    return () => window.clearTimeout(syncTimerRef.current);
  }, [syncRevision, syncUser]);

  useEffect(() => {
    if (!syncUser) return undefined;
    let active = true;
    async function pollCloud() {
      if (!active || syncOperationRef.current || localStorage.getItem(SYNC_PENDING_KEY) === "1") return;
      syncOperationRef.current = true;
      try {
        const remote = await downloadSnapshot();
        const previous = localStorage.getItem(SYNC_REMOTE_KEY) || "";
        if (!previous) {
          localStorage.setItem(SYNC_REMOTE_KEY, remote.updated_at || "");
        } else if (remote.updated_at && remote.updated_at !== previous) {
          setData(normalizeSnapshot(remote.data).data);
          localStorage.setItem(SYNC_REMOTE_KEY, remote.updated_at);
          setSyncMessage("Cambios recientes recibidos desde Supabase.");
        }
      } catch (error) {
        if (!String(error.message).includes("Todavia no existe")) {
          setSyncMessage(`No se pudo comprobar la nube: ${error.message}`);
        }
      } finally {
        syncOperationRef.current = false;
      }
    }
    const firstCheck = window.setTimeout(pollCloud, 2500);
    const interval = window.setInterval(pollCloud, 30000);
    return () => {
      active = false;
      window.clearTimeout(firstCheck);
      window.clearInterval(interval);
    };
  }, [syncUser]);

  const range = useMemo(() => rangeFor(scope, filters.anchor, language), [scope, filters.anchor, language]);
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

  function commitData(update, closeDialog = true) {
    localStorage.setItem(SYNC_PENDING_KEY, "1");
    setData(update);
    setSyncRevision((value) => value + 1);
    if (closeDialog) setDialog(null);
  }

  async function hasRemoteSyncConflict() {
    try {
      const remote = await downloadSnapshot();
      const previous = localStorage.getItem(SYNC_REMOTE_KEY) || "";
      return Boolean(remote.updated_at && (!previous || remote.updated_at !== previous));
    } catch (error) {
      if (String(error.message).includes("Todavia no existe")) return false;
      throw error;
    }
  }

  function saveMovement(transaction) {
    commitData((current) => {
      const exists = current.transactions.some((item) => item.id === transaction.id);
      const transactions = exists
        ? current.transactions.map((item) => item.id === transaction.id ? transaction : item)
        : [transaction, ...current.transactions];
      return { ...current, transactions: transactions.sort(sortTransactions) };
    });
    setFilters((current) => ({ ...current, anchor: transaction.date }));
  }

  function deleteMovement(transaction) {
    if (!window.confirm(t("¿Eliminar este movimiento? Esta accion no se puede deshacer.", language))) return;
    commitData((current) => ({
      ...current,
      transactions: current.transactions.filter((item) => item.id !== transaction.id),
    }), false);
  }

  function saveAccount(account) {
    const originalName = account.originalName;
    const duplicate = data.accounts.some((item) => item.name === account.name && item.name !== originalName);
    if (duplicate) {
      window.alert(t("Ya existe una cuenta con ese nombre.", language));
      return;
    }
    commitData((current) => {
      const clean = { ...account };
      delete clean.originalName;
      delete clean.newType;
      const accounts = originalName
        ? current.accounts.map((item) => item.name === originalName ? clean : item)
        : [...current.accounts, clean];
      const transactions = originalName && originalName !== clean.name
        ? current.transactions.map((item) => ({
            ...item,
            account: item.account === originalName ? clean.name : item.account,
            toAccount: item.toAccount === originalName ? clean.name : item.toAccount,
          }))
        : current.transactions;
      const accountTypes = [...new Set([...(current.accountTypes || []), clean.type])];
      return { ...current, accountTypes, accounts, transactions };
    });
  }

  function saveAccountType(original, name) {
    const clean = name.trim();
    if (!clean) return;
    commitData((current) => ({
      ...current,
      accountTypes: original
        ? [...new Set((current.accountTypes || []).map((type) => type === original ? clean : type))]
        : [...new Set([...(current.accountTypes || []), clean])],
      accounts: original
        ? current.accounts.map((account) => account.type === original ? { ...account, type: clean } : account)
        : current.accounts,
    }), false);
  }

  function deleteAccountType(type) {
    if (data.accounts.some((account) => account.type === type)) {
      window.alert(t("No se puede eliminar un tipo que tiene cuentas.", language));
      return;
    }
    commitData((current) => ({
      ...current,
      accountTypes: (current.accountTypes || []).filter((item) => item !== type),
    }), false);
  }

  function saveCategory(original, category) {
    const duplicate = data.categories.some((item) => item.name === category.name && item.kind === category.kind && item !== original);
    if (duplicate) return;
    commitData((current) => ({
      ...current,
      categories: original
        ? current.categories.map((item) => item.name === original.name && item.kind === original.kind ? category : item)
        : [...current.categories, category],
      transactions: original && original.name !== category.name
        ? current.transactions.map((transaction) => transaction.kind === original.kind && transaction.category === original.name
          ? { ...transaction, category: category.name }
          : transaction)
        : current.transactions,
    }), false);
  }

  function deleteCategory(category) {
    if (data.transactions.some((transaction) => transaction.category === category.name && transaction.kind === category.kind)) {
      window.alert(t("No se puede eliminar una categoria que tiene movimientos.", language));
      return;
    }
    commitData((current) => ({
      ...current,
      categories: current.categories.filter((item) => item.name !== category.name || item.kind !== category.kind),
    }), false);
  }

  function toggleAccountHidden(account) {
    commitData((current) => ({
      ...current,
      accounts: current.accounts.map((item) => item.name === account.name ? { ...item, hidden: !item.hidden } : item),
    }), false);
  }

  function deleteAccount(account) {
    if (!window.confirm(`${t("Eliminar cuenta", language)} "${account.name}"? ${t("Los movimientos historicos conservaran su nombre.", language)}`)) return;
    commitData((current) => ({
      ...current,
      accounts: current.accounts.filter((item) => item.name !== account.name),
    }), false);
  }

  function saveCurrency(currencyCode) {
    commitData((current) => ({
      ...current,
      currency: currencyCode,
      accounts: current.accounts.map((account) => ({ ...account, currency: currencyCode })),
    }));
  }

  async function syncLogin(email, password) {
    setSyncBusy(true);
    setSyncMessage("");
    syncOperationRef.current = true;
    try {
      const result = await signIn(email, password);
      setSyncUser(result.user);
      setSyncMessage("Cuenta conectada. La sesión permanecerá activa.");
      if (localStorage.getItem(SYNC_PENDING_KEY) === "1") {
        setSyncRevision((value) => value + 1);
      }
    } catch (error) {
      setSyncMessage(error.message);
    } finally {
      syncOperationRef.current = false;
      setSyncBusy(false);
    }
  }

  async function syncUpload() {
    setSyncBusy(true);
    setSyncMessage("");
    syncOperationRef.current = true;
    try {
      const result = await uploadSnapshot(data);
      if (result?.updated_at) localStorage.setItem(SYNC_REMOTE_KEY, result.updated_at);
      localStorage.removeItem(SYNC_PENDING_KEY);
      localStorage.removeItem(SYNC_CONFLICT_KEY);
      setSyncConflict(false);
      setSyncMessage("Copia subida correctamente.");
    } catch (error) {
      localStorage.setItem(SYNC_PENDING_KEY, "1");
      setSyncMessage(error.message);
    } finally {
      syncOperationRef.current = false;
      setSyncBusy(false);
    }
  }

  async function syncDownload() {
    if (!window.confirm(t("La copia de Supabase reemplazara los datos guardados en este navegador.", language))) return;
    setSyncBusy(true);
    setSyncMessage("");
    syncOperationRef.current = true;
    try {
      const remote = await downloadSnapshot();
      setData(normalizeSnapshot(remote.data).data);
      if (remote.updated_at) localStorage.setItem(SYNC_REMOTE_KEY, remote.updated_at);
      localStorage.removeItem(SYNC_PENDING_KEY);
      localStorage.removeItem(SYNC_CONFLICT_KEY);
      setSyncConflict(false);
      setSyncMessage("Datos descargados correctamente.");
    } catch (error) {
      setSyncMessage(error.message);
    } finally {
      syncOperationRef.current = false;
      setSyncBusy(false);
    }
  }

  async function syncCreateAccount(email, password) {
    setSyncBusy(true);
    setSyncMessage("");
    syncOperationRef.current = true;
    try {
      const result = await createAccount(email, password);
      if (result.session?.user) setSyncUser(result.session.user);
      setSyncMessage(result.session
        ? "Cuenta creada y conectada. La sesión permanecerá activa."
        : "Cuenta creada. Revisa tu correo para confirmarla antes de iniciar sesion.");
    } catch (error) {
      setSyncMessage(error.message);
    } finally {
      syncOperationRef.current = false;
      setSyncBusy(false);
    }
  }

  async function syncSignOut() {
    setSyncBusy(true);
    syncOperationRef.current = true;
    try {
      await signOut();
      setSyncUser(null);
      setSyncConflict(false);
      setSyncMessage("Sesión cerrada en este navegador.");
    } catch (error) {
      setSyncMessage(error.message);
    } finally {
      syncOperationRef.current = false;
      setSyncBusy(false);
    }
  }

  async function syncConfigSaved() {
    setSyncConflict(localStorage.getItem(SYNC_CONFLICT_KEY) === "1");
    try {
      setSyncUser(await getCurrentUser());
    } catch {
      setSyncUser(null);
    }
  }

  async function exportReport(reportScope, anchor, format) {
    const reportRange = rangeFor(reportScope, anchor, language);
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

  async function exportStatus(reportScope, anchor) {
    const reportRange = rangeFor(reportScope, anchor, language);
    const rows = data.transactions.filter((transaction) => inRange(transaction, reportRange));
    const XLSX = await import("xlsx");
    const worksheet = XLSX.utils.json_to_sheet(statusReportRows(rows));
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Estado");
    XLSX.writeFile(workbook, `moneymate_estado_${reportScope}_${anchor}.xlsx`);
  }

  function exportBackup() {
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `control-financiero-${today()}.json`;
    link.click();
    URL.revokeObjectURL(link.href);
  }

  function applyBackupImport(nextData) {
    localStorage.setItem(IMPORT_UNDO_KEY, JSON.stringify(data));
    commitData(nextData);
    const anchor = latestDate(nextData.transactions);
    setFilters((current) => ({ ...current, anchor, account: "", query: "" }));
  }

  function undoBackupImport() {
    if (!window.confirm(t("Se reemplazarán los datos actuales por la copia anterior a la última importación.", language))) return;
    try {
      const previous = JSON.parse(localStorage.getItem(IMPORT_UNDO_KEY));
      if (!previous) return;
      const restored = normalizeSnapshot(previous).data;
      localStorage.removeItem(IMPORT_UNDO_KEY);
      commitData(restored);
      setFilters((current) => ({ ...current, anchor: latestDate(restored.transactions), account: "", query: "" }));
    } catch {
      localStorage.removeItem(IMPORT_UNDO_KEY);
    }
  }

  return (
    <div className="app-shell">
      <Header
        active={active}
        dark={dark}
        language={language}
        onNav={navigate}
        onTheme={() => setDark((value) => !value)}
        onCurrency={() => setDialog("currency")}
        onSearch={openSearch}
        onNew={() => setDialog("movement")}
        onReport={() => setDialog("report")}
        onData={() => setDialog("backup")}
        onSync={() => { setSyncMessage(""); setDialog("sync"); }}
        onLanguage={() => setDialog("language")}
        onAbout={() => setDialog("about")}
      />
      <div className="app-body">
        <SideNav active={active} onChange={navigate} language={language} />
        <main className="main-content">
          {active === "transactions" ? (
            <>
              <Summary value={summaryValue} currencyCode={data.currency} language={language} />
              <button className="mobile-new" onClick={() => setDialog("movement")}><Plus size={20} /> {t("Nuevo movimiento", language)}</button>
              <div ref={searchRef}>
                <Filters
                  filters={filters}
                  accounts={data.accounts.filter((account) => !account.hidden)}
                  onChange={setFilters}
                  onClear={() => setFilters({ query: "", account: "", anchor: today() })}
                  language={language}
                />
              </div>
              <div className="scope-row">
                {["anual", "mensual", "semanal", "diario", "todo"].map((value) => (
                  <button className={scope === value ? "active" : ""} onClick={() => setScope(value)} key={value}>
                    {t(scopeLabel(value), language)}
                  </button>
                ))}
              </div>
              <TransactionList
                transactions={visibleTransactions}
                currencyCode={data.currency}
                onEdit={(transaction) => setDialog({ type: "movement", mode: "edit", item: transaction })}
                onCopy={(transaction) => setDialog({ type: "movement", mode: "copy", item: transaction })}
                onDelete={deleteMovement}
                language={language}
              />
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
              onExport={exportStatus}
              language={language}
            />
          ) : null}
          {active === "accounts" ? (
            <AccountsPanel
              accounts={accounts}
              currencyCode={data.currency}
              full
              showHidden={showHiddenAccounts}
              onShowHidden={() => setShowHiddenAccounts((value) => !value)}
              onAdd={() => setDialog({ type: "account" })}
              onManage={() => setDialog("metadata")}
              onEdit={(account) => setDialog({ type: "account", item: account })}
              onToggleHidden={toggleAccountHidden}
              onDelete={deleteAccount}
              language={language}
            />
          ) : null}
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
            language={language}
          />
          <AccountsPanel accounts={accounts} currencyCode={data.currency} language={language} />
        </aside>
      </div>
      <MobileNav active={active} onChange={navigate} language={language} />
      {(dialog === "movement" || dialog?.type === "movement") ? (
        <MovementDialog
          data={data}
          initial={dialog?.item || null}
          mode={dialog?.mode || "new"}
          onClose={() => setDialog(null)}
          onSave={saveMovement}
          language={language}
        />
      ) : null}
      {dialog?.type === "account" ? (
        <AccountDialog
          account={dialog.item || null}
          currencyCode={data.currency}
          onClose={() => setDialog(null)}
          onSave={saveAccount}
          accountTypes={data.accountTypes}
          language={language}
        />
      ) : null}
      {dialog === "metadata" ? (
        <MetadataDialog
          accountTypes={data.accountTypes}
          categories={data.categories}
          onClose={() => setDialog(null)}
          onSaveType={saveAccountType}
          onDeleteType={deleteAccountType}
          onSaveCategory={saveCategory}
          onDeleteCategory={deleteCategory}
          language={language}
        />
      ) : null}
      {dialog === "currency" ? <CurrencyDialog value={data.currency} onClose={() => setDialog(null)} onSave={saveCurrency} language={language} /> : null}
      {dialog === "language" ? <LanguageDialog value={language} onClose={() => setDialog(null)} onSave={(value) => { setLanguage(value); setDialog(null); }} /> : null}
      {dialog === "about" ? <AboutDialog version={APP_VERSION} language={language} onClose={() => setDialog(null)} /> : null}
      {dialog === "sync" ? (
        <SyncDialog
          onClose={() => setDialog(null)}
          onUpload={syncUpload}
          onDownload={syncDownload}
          onSignIn={syncLogin}
          onSignOut={syncSignOut}
          onCreateAccount={syncCreateAccount}
          onConfigSaved={syncConfigSaved}
          user={syncUser}
          busy={syncBusy}
          message={syncMessage}
          conflict={syncConflict}
          language={language}
        />
      ) : null}
      {dialog === "report" ? <ReportDialog onClose={() => setDialog(null)} onExport={exportReport} language={language} /> : null}
      {dialog === "backup" ? (
        <BackupDialog
          data={data}
          canUndo={Boolean(localStorage.getItem(IMPORT_UNDO_KEY))}
          onClose={() => setDialog(null)}
          onExport={exportBackup}
          onApply={applyBackupImport}
          onUndo={undoBackupImport}
          language={language}
        />
      ) : null}
    </div>
  );
}

function loadData() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return saved?.version === 2 ? normalizeSnapshot(saved).data : seedData;
  } catch {
    return seedData;
  }
}

function sortTransactions(left, right) {
  return `${right.date} ${right.time}`.localeCompare(`${left.date} ${left.time}`);
}

function latestDate(transactions) {
  return [...transactions].sort(sortTransactions)[0]?.date || today();
}

function scopeLabel(scope) {
  return scope === "anual" ? "Anual" : scope === "semestral" ? "Semestral" : scope === "semanal" ? "Semanal" : scope === "diario" ? "Diario" : scope === "todo" ? "Todo" : "Mensual";
}
