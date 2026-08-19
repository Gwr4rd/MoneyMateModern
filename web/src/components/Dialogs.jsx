import { useEffect, useMemo, useState } from "react";
import {
  ArrowLeft,
  BookOpen,
  CheckCircle2,
  CloudDownload,
  CloudUpload,
  Copy,
  ExternalLink,
  FileSpreadsheet,
  Landmark,
  Languages,
  ListPlus,
  LogIn,
  LogOut,
  Save,
  Trash2,
  UserPlus,
  X,
} from "lucide-react";
import { getSupabaseConfig, isSupabaseConfigured, saveSupabaseConfig } from "../lib/supabase";
import { SUPABASE_DASHBOARD_URL, SUPABASE_SQL } from "../lib/supabaseSetup";
import { accountBalances, today } from "../lib/finance";
import { t } from "../i18n";

function AccountSelect({ label, name, value, accounts, onChange, language }) {
  const selected = accounts.find((account) => account.name === value);
  const activeAccounts = accounts.filter((account) => account.active);
  const inactiveAccounts = accounts.filter((account) => !account.active);

  const options = (items) => items.map((account) => (
    <option value={account.name} key={account.name}>
      {account.name} · {t(account.active ? "Activa" : "Inactiva", language)}
    </option>
  ));

  return (
    <label>{t(label, language)}
      <select
        className={`account-select ${selected?.active ? "is-active" : "is-inactive"}`}
        name={name}
        value={value}
        onChange={onChange}
      >
        {activeAccounts.length ? <optgroup label={t("Cuentas activas", language)}>{options(activeAccounts)}</optgroup> : null}
        {inactiveAccounts.length ? <optgroup label={t("Cuentas inactivas", language)}>{options(inactiveAccounts)}</optgroup> : null}
      </select>
    </label>
  );
}

export function MovementDialog({ data, initial = null, mode = "new", onClose, onSave, language }) {
  const availableAccounts = useMemo(() => accountBalances(data)
    .map((account, index) => ({
      ...account,
      active: !account.hidden && Math.abs(Number(account.currentBalance) || 0) >= 0.005,
      originalIndex: index,
    }))
    .sort((left, right) => Number(right.active) - Number(left.active) || left.originalIndex - right.originalIndex), [data]);
  const accountTypes = useMemo(() => [...new Set([
    ...(data.accountTypes || []),
    ...availableAccounts.map((account) => account.type),
  ].filter(Boolean))], [data.accountTypes, availableAccounts]);
  const defaultAccount = initial?.account || availableAccounts[0]?.name || "";
  const defaultToAccount = initial?.toAccount
    || availableAccounts.find((account) => account.name !== defaultAccount)?.name
    || "";
  const defaultAccountType = availableAccounts.find((account) => account.name === defaultAccount)?.type || accountTypes[0] || "";
  const defaultToAccountType = availableAccounts.find((account) => account.name === defaultToAccount)?.type || accountTypes[0] || "";
  const [showNoteSuggestions, setShowNoteSuggestions] = useState(false);
  const [form, setForm] = useState({
    date: mode === "copy" ? today() : initial?.date || today(),
    time: mode === "copy" ? new Date().toTimeString().slice(0, 5) : initial?.time || new Date().toTimeString().slice(0, 5),
    kind: initial?.kind || "expense",
    accountType: defaultAccountType,
    account: defaultAccount,
    toAccountType: defaultToAccountType,
    toAccount: defaultToAccount,
    category: initial?.category || data.categories.find((category) => category.kind === "expense")?.name || "",
    amount: initial?.amount ?? "",
    note: initial?.note || "",
    description: initial?.description || "",
  });
  const filteredAccounts = availableAccounts.filter((account) => account.type === form.accountType);
  const filteredToAccounts = availableAccounts.filter((account) => account.type === form.toAccountType);
  const categories = data.categories.filter((category) => category.kind === form.kind);
  const previousNotes = useMemo(() => {
    const seen = new Set();
    return data.transactions.reduce((notes, transaction) => {
      const note = transaction.note?.trim();
      const key = note?.toLocaleLowerCase("es");
      if (note && !seen.has(key)) {
        seen.add(key);
        notes.push(note);
      }
      return notes;
    }, []);
  }, [data.transactions]);
  const noteQuery = form.note.trim().toLocaleLowerCase("es");
  const noteSuggestions = noteQuery
    ? previousNotes
      .filter((note) => {
        const normalized = note.toLocaleLowerCase("es");
        return normalized.includes(noteQuery) && normalized !== noteQuery;
      })
      .slice(0, 6)
    : [];

  function change(event) {
    const { name, value } = event.target;
    setForm((current) => {
      const next = { ...current, [name]: value };
      if (name === "kind" && value !== "transfer") {
        next.category = data.categories.find((category) => category.kind === value)?.name || "";
      }
      if (name === "accountType") {
        const choices = availableAccounts.filter((account) => account.type === value);
        if (!choices.some((account) => account.name === next.account)) next.account = choices[0]?.name || "";
      }
      if (name === "toAccountType") {
        const choices = availableAccounts.filter((account) => account.type === value);
        if (!choices.some((account) => account.name === next.toAccount)) next.toAccount = choices[0]?.name || "";
      }
      return next;
    });
  }

  function submit(event) {
    event.preventDefault();
    if (!Number(form.amount) || Number(form.amount) <= 0) return;
    if (!form.account || !filteredAccounts.some((account) => account.name === form.account)) return;
    if (form.kind === "transfer" && (!form.toAccount || !filteredToAccounts.some((account) => account.name === form.toAccount))) return;
    if (form.kind === "transfer" && form.account === form.toAccount) return;
    const { accountType, toAccountType, ...movement } = form;
    onSave({
      ...movement,
      id: mode === "edit" ? initial.id : crypto.randomUUID(),
      amount: Number(form.amount),
      category: form.kind === "transfer" ? "Transferencia" : form.category,
      toAccount: form.kind === "transfer" ? form.toAccount : "",
    });
  }

  function selectKind(kind) {
    setForm((current) => ({
      ...current,
      kind,
      category: kind === "transfer"
        ? "Transferencia"
        : data.categories.find((category) => category.kind === kind)?.name || "",
    }));
  }

  return (
    <Modal title={t(mode === "edit" ? "Editar" : mode === "copy" ? "Copiar" : "Nuevo movimiento", language)} onClose={onClose} language={language}>
      <form className="movement-form" onSubmit={submit}>
        <fieldset className="movement-kind-switch">
          <legend>{t("Tipo de movimiento", language)}</legend>
          {[
            ["income", "Ingreso"],
            ["expense", "Gasto"],
            ["transfer", "Transferencia"],
          ].map(([kind, label]) => (
            <button
              className={`${kind} ${form.kind === kind ? "active" : ""}`}
              type="button"
              aria-pressed={form.kind === kind}
              onClick={() => selectKind(kind)}
              key={kind}
            >
              {t(label, language)}
            </button>
          ))}
        </fieldset>
        <div className="form-pair">
          <label>{t("Fecha", language)}<input name="date" type="date" value={form.date} onChange={change} /></label>
          <label>{t("Hora", language)}<input name="time" type="time" value={form.time} onChange={change} /></label>
        </div>
        <label>{t("Importe", language)}<input name="amount" type="number" min="0.01" step="0.01" value={form.amount} onChange={change} autoFocus /></label>
        {form.kind === "transfer" ? (
          <>
            <label>{t("Tipo de origen", language)}
              <select name="accountType" value={form.accountType} onChange={change}>
                {accountTypes.map((value) => <option value={value} key={value}>{t(value, language)}</option>)}
              </select>
            </label>
            <AccountSelect label="Cuenta origen" name="account" value={form.account} accounts={filteredAccounts} onChange={change} language={language} />
            <label>{t("Tipo de destino", language)}
              <select name="toAccountType" value={form.toAccountType} onChange={change}>
                {accountTypes.map((value) => <option value={value} key={value}>{t(value, language)}</option>)}
              </select>
            </label>
            <AccountSelect label="Cuenta destino" name="toAccount" value={form.toAccount} accounts={filteredToAccounts} onChange={change} language={language} />
          </>
        ) : (
          <>
            <label>{t("Categoria", language)}
              <select name="category" value={form.category} onChange={change}>
                {categories.map((category) => <option value={category.name} key={category.name}>{t(category.name, language)}</option>)}
              </select>
            </label>
            <label>{t("Tipo de cuenta", language)}
              <select name="accountType" value={form.accountType} onChange={change}>
                {accountTypes.map((value) => <option value={value} key={value}>{t(value, language)}</option>)}
              </select>
            </label>
            <AccountSelect label="Cuenta" name="account" value={form.account} accounts={filteredAccounts} onChange={change} language={language} />
          </>
        )}
        <label className="note-field">{t("Nota", language)}
          <span className="note-autocomplete">
            <input
              name="note"
              value={form.note}
              onChange={(event) => {
                change(event);
                setShowNoteSuggestions(true);
              }}
              onFocus={() => setShowNoteSuggestions(true)}
              onBlur={() => window.setTimeout(() => setShowNoteSuggestions(false), 120)}
              autoComplete="off"
              placeholder={t("Escribe para buscar notas anteriores", language)}
            />
            {showNoteSuggestions && noteSuggestions.length ? (
              <span className="note-suggestions" role="listbox" aria-label={t("Notas anteriores", language)}>
                {noteSuggestions.map((suggestion) => (
                  <button
                    type="button"
                    role="option"
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => {
                      setForm((current) => ({ ...current, note: suggestion }));
                      setShowNoteSuggestions(false);
                    }}
                    key={suggestion}
                  >
                    {suggestion}
                  </button>
                ))}
              </span>
            ) : null}
          </span>
        </label>
        <label>{t("Descripcion", language)}<input name="description" value={form.description} onChange={change} /></label>
        <button className="dialog-primary" type="submit">
          {t(mode === "edit" ? "Guardar" : mode === "copy" ? "Copiar" : "Guardar", language)}
        </button>
      </form>
    </Modal>
  );
}

export function AccountDialog({ account = null, accountTypes = [], currencyCode, onClose, onSave, language }) {
  const [form, setForm] = useState({
    name: account?.name || "",
    type: account?.type || accountTypes[0] || "Cuentas de Banco",
    newType: "",
    balance: account?.balance ?? "",
    description: account?.description || "",
    includeTotal: account?.includeTotal !== false,
    hidden: account?.hidden || false,
  });

  function submit(event) {
    event.preventDefault();
    const name = form.name.trim();
    const type = form.type === "__new__" ? form.newType.trim() : form.type;
    if (!name || !type) return;
    onSave({
      ...form,
      name,
      type,
      balance: Number(form.balance) || 0,
      currency: currencyCode,
      originalName: account?.name || "",
    });
  }

  return (
    <Modal title={t(account ? "Editar cuenta" : "Nueva cuenta", language)} onClose={onClose} language={language}>
      <form className="movement-form" onSubmit={submit}>
        <label>{t("Nombre", language)}<input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} autoFocus /></label>
        <label>{t("Tipo de cuenta", language)}
          <select value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value })}>
            {accountTypes.map((type) => <option value={type} key={type}>{t(type, language)}</option>)}
            <option value="__new__">{t("Crear nuevo tipo...", language)}</option>
          </select>
        </label>
        {form.type === "__new__" ? (
          <label>{t("Nuevo tipo", language)}<input value={form.newType} onChange={(event) => setForm({ ...form, newType: event.target.value })} /></label>
        ) : null}
        <label>{t("Saldo inicial", language)} ({currencyCode})<input type="number" step="0.01" value={form.balance} onChange={(event) => setForm({ ...form, balance: event.target.value })} /></label>
        <label>{t("Descripcion", language)}<input value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
        <label className="check-field"><input type="checkbox" checked={form.includeTotal} onChange={(event) => setForm({ ...form, includeTotal: event.target.checked })} /> {t("Incluir en el total", language)}</label>
        <button className="dialog-primary" type="submit"><Save size={18} /> {t("Guardar", language)}</button>
      </form>
    </Modal>
  );
}

export function MetadataDialog({ accountTypes, categories, onClose, onSaveType, onDeleteType, onSaveCategory, onDeleteCategory, language }) {
  const [typeName, setTypeName] = useState("");
  const [categoryName, setCategoryName] = useState("");
  const [categoryKind, setCategoryKind] = useState("expense");

  return (
    <Modal title={t("Tipos y categorias", language)} onClose={onClose} language={language}>
      <div className="metadata-form">
        <section>
          <h3>{t("Tipo de cuenta", language)}</h3>
          <div className="metadata-add">
            <input placeholder={t("Nuevo tipo", language)} value={typeName} onChange={(event) => setTypeName(event.target.value)} />
            <button type="button" onClick={() => {
              const value = typeName.trim();
              if (!value) return;
              onSaveType("", value);
              setTypeName("");
            }}><ListPlus size={18} /> {t("Agregar", language)}</button>
          </div>
          <div className="metadata-list">
            {accountTypes.map((type) => {
              const fixed = type === "Efectivo" || type === "Cuentas de Banco";
              return (
                <div key={type}>
                  <span>{t(type, language)}{fixed ? <small>{t("Predeterminado", language)}</small> : null}</span>
                  {!fixed ? <>
                    <button type="button" onClick={() => {
                      const value = window.prompt(t("Nuevo nombre del tipo", language), type)?.trim();
                      if (value) onSaveType(type, value);
                    }}>{t("Editar", language)}</button>
                    <button className="danger" type="button" onClick={() => onDeleteType(type)}><Trash2 size={16} /></button>
                  </> : null}
                </div>
              );
            })}
          </div>
        </section>
        <section>
          <h3>{t("Categoria", language)}</h3>
          <div className="metadata-add category-add">
            <input placeholder={t("Nueva categoria", language)} value={categoryName} onChange={(event) => setCategoryName(event.target.value)} />
            <select value={categoryKind} onChange={(event) => setCategoryKind(event.target.value)}>
              <option value="expense">{t("Gasto", language)}</option>
              <option value="income">{t("Ingreso", language)}</option>
            </select>
            <button type="button" onClick={() => {
              const value = categoryName.trim();
              if (!value) return;
              onSaveCategory(null, { name: value, kind: categoryKind });
              setCategoryName("");
            }}><ListPlus size={18} /> {t("Agregar", language)}</button>
          </div>
          <div className="metadata-list">
            {categories.map((category) => (
              <div className="category-metadata-row" key={`${category.kind}-${category.name}`}>
                <span>{category.name}<small>{t(category.kind === "income" ? "Ingreso" : "Gasto", language)}</small></span>
                <select
                  aria-label={`${t("Categoria", language)} ${category.name}`}
                  value={category.kind}
                  onChange={(event) => onSaveCategory(category, { ...category, kind: event.target.value })}
                >
                  <option value="expense">{t("Gasto", language)}</option>
                  <option value="income">{t("Ingreso", language)}</option>
                </select>
                <button type="button" onClick={() => {
                  const value = window.prompt(t("Nuevo nombre de la categoria", language), category.name)?.trim();
                  if (value) onSaveCategory(category, { ...category, name: value });
                }}>{t("Editar", language)}</button>
                <button className="danger" type="button" onClick={() => onDeleteCategory(category)}><Trash2 size={16} /></button>
              </div>
            ))}
          </div>
        </section>
      </div>
    </Modal>
  );
}

const CURRENCIES = [
  ["PEN", "Peru", "S/"],
  ["USD", "Estados Unidos", "$"],
  ["EUR", "Union Europea", "€"],
  ["MXN", "Mexico", "MX$"],
  ["COP", "Colombia", "COL$"],
  ["CLP", "Chile", "CLP$"],
  ["ARS", "Argentina", "AR$"],
  ["BRL", "Brasil", "R$"],
];

export function CurrencyDialog({ value, onClose, onSave, language }) {
  const [currencyCode, setCurrencyCode] = useState(value || "PEN");
  return (
    <Modal title={t("Moneda y pais", language)} onClose={onClose} language={language}>
      <div className="currency-form">
        <p>{t("La moneda elegida se aplicara a los totales, cuentas, movimientos y reportes.", language)}</p>
        <div className="currency-options">
          {CURRENCIES.map(([code, country, symbol]) => (
            <button className={currencyCode === code ? "active" : ""} onClick={() => setCurrencyCode(code)} key={code}>
              <Landmark size={19} />
              <span><strong>{t(country, language)}</strong><small>{code} · {symbol}</small></span>
            </button>
          ))}
        </div>
        <button className="dialog-primary" onClick={() => onSave(currencyCode)}><Save size={18} /> {t("Guardar", language)}</button>
      </div>
    </Modal>
  );
}

export function SyncDialog({
  onClose,
  onUpload,
  onDownload,
  onSignIn,
  onSignOut,
  onCreateAccount,
  onConfigSaved,
  user,
  busy,
  message,
  language,
}) {
  const initialConfig = getSupabaseConfig();
  const [stage, setStage] = useState(() => isSupabaseConfigured() ? (user ? "connected" : "account") : "config");
  const [url, setUrl] = useState(initialConfig.url);
  const [key, setKey] = useState(initialConfig.key);
  const [accountMode, setAccountMode] = useState("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [localMessage, setLocalMessage] = useState("");

  useEffect(() => {
    setStage((current) => {
      if (user) return "connected";
      return current === "connected" ? "account" : current;
    });
  }, [user]);

  function storeConnection() {
    try {
      saveSupabaseConfig(url, key);
      setLocalMessage("Conexion guardada. Ahora conecta tu cuenta.");
      setStage("account");
      Promise.resolve(onConfigSaved?.());
    } catch (error) {
      setLocalMessage(error.message);
    }
  }

  async function copySql() {
    try {
      await navigator.clipboard.writeText(SUPABASE_SQL);
      setLocalMessage("Script SQL copiado.");
    } catch {
      const area = document.createElement("textarea");
      area.value = SUPABASE_SQL;
      document.body.appendChild(area);
      area.select();
      document.execCommand("copy");
      area.remove();
      setLocalMessage("Script SQL copiado.");
    }
  }

  const connectionStage = user ? "connected" : (isSupabaseConfigured() ? "account" : "config");

  return (
    <Modal title={t("Cuenta y sincronizacion", language)} onClose={onClose} language={language}>
      <div className="sync-form">
        {stage === "help" ? (
          <SupabaseGuide
            onBack={() => {
              setLocalMessage("");
              setStage(connectionStage);
            }}
            onCopy={copySql}
            language={language}
          />
        ) : null}
        {stage === "config" ? (
          <>
            <div className="sync-status">{t("Supabase no configurado. Guarda primero la conexion del proyecto.", language)}</div>
            <label>{t("URL del proyecto", language)}
              <input type="url" placeholder="https://tu-proyecto.supabase.co" value={url} onChange={(event) => setUrl(event.target.value)} />
            </label>
            <label>{t("Clave publica", language)}
              <input type="text" placeholder={t("Clave publishable o anon", language)} value={key} onChange={(event) => setKey(event.target.value)} />
            </label>
            <p className="dialog-message">{t("Usa solamente la clave publica. Nunca coloques una clave service_role.", language)}</p>
            <button className="dialog-primary" onClick={storeConnection}>
              <Save size={19} /> {t("Guardar conexion", language)}
            </button>
            <button className="sync-guide-button" onClick={() => setStage("help")}>
              <BookOpen size={19} /> {t("Como crear y configurar Supabase", language)}
            </button>
          </>
        ) : null}
        {stage === "account" ? (
          <>
            <div className="sync-status ready">{t("Configuracion de Supabase guardada. Conecta tu cuenta para sincronizar.", language)}</div>
            <div className="sync-account-tabs" role="tablist" aria-label={t("Acceso a Supabase", language)}>
              <button className={accountMode === "login" ? "active" : ""} onClick={() => setAccountMode("login")}>{t("Iniciar sesion", language)}</button>
              <button className={accountMode === "create" ? "active" : ""} onClick={() => setAccountMode("create")}>{t("Crear cuenta", language)}</button>
            </div>
            <label>{t("Correo", language)}<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} /></label>
            <label>{t("Contrasena", language)}<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
            {accountMode === "create" ? (
              <button className="dialog-primary" disabled={busy || !email || !password} onClick={() => onCreateAccount(email, password)}>
                <UserPlus size={19} /> {t("Crear cuenta", language)}
              </button>
            ) : (
              <button className="dialog-primary" disabled={busy || !email || !password} onClick={() => onSignIn(email, password)}>
                <LogIn size={19} /> {t("Iniciar sesion", language)}
              </button>
            )}
            <button className="sync-guide-button" onClick={() => setStage("help")}>
              <BookOpen size={19} /> {t("Manual y script SQL", language)}
            </button>
            <button className="sync-change" onClick={() => { setLocalMessage(""); setStage("config"); }}>
              {t("Cambiar conexion de Supabase", language)}
            </button>
          </>
        ) : null}
        {stage === "connected" ? (
          <>
            <div className="sync-status connected">
              <CheckCircle2 size={21} />
              <span><strong>{t("Cuenta conectada", language)}</strong><small>{user?.email}</small><small>{t("Sesion activa y sincronizacion automatica", language)}</small></span>
            </div>
            <div className="dialog-actions">
              <button disabled={busy} onClick={onDownload}>
                <CloudDownload size={19} /> {t("Descargar", language)}
              </button>
              <button className="dialog-primary" disabled={busy} onClick={onUpload}>
                <CloudUpload size={19} /> {t("Subir ahora", language)}
              </button>
            </div>
            <button className="sync-guide-button" onClick={() => setStage("help")}>
              <BookOpen size={19} /> {t("Manual y script SQL", language)}
            </button>
            <button className="sync-change" onClick={() => { setLocalMessage(""); setStage("config"); }}>
              {t("Cambiar conexion de Supabase", language)}
            </button>
            <button className="sync-signout" disabled={busy} onClick={onSignOut}>
              <LogOut size={18} /> {t("Cerrar sesion", language)}
            </button>
          </>
        ) : null}
        {localMessage || message ? <p className="dialog-message">{t(localMessage || message, language)}</p> : null}
      </div>
    </Modal>
  );
}

function SupabaseGuide({ onBack, onCopy, language }) {
  return (
    <div className="sync-guide">
      <button className="sync-guide-back" onClick={onBack}><ArrowLeft size={20} /> {t("Volver a la conexion", language)}</button>
      <div className="sync-guide-heading">
        <h2>{t("Configurar Supabase", language)}</h2>
        <p>{t("Sigue estos pasos una sola vez para activar la sincronizacion segura por cuenta.", language)}</p>
      </div>
      <GuideStep number="1" title={t("Crear el proyecto", language)}>
        {t("Abre Supabase, crea un proyecto llamado Control Financiero, guarda la contrasena de la base de datos y elige la region mas cercana.", language)}
      </GuideStep>
      <a className="dialog-primary sync-external" href={SUPABASE_DASHBOARD_URL} target="_blank" rel="noreferrer">
        <ExternalLink size={19} /> {t("Abrir Supabase", language)}
      </a>
      <GuideStep number="2" title={t("Crear la tabla segura", language)}>
        {t("En SQL Editor pulsa New query, pega el script de configuracion y selecciona Run.", language)}
      </GuideStep>
      <button className="sync-guide-button" onClick={onCopy}><Copy size={19} /> {t("Copiar script SQL", language)}</button>
      <GuideStep number="3" title={t("Habilitar correo y contrasena", language)}>
        {t("En Authentication > Sign In / Providers abre Email y activa el proveedor. Para una prueba puedes desactivar Confirm email.", language)}
      </GuideStep>
      <GuideStep number="4" title={t("Copiar la conexion", language)}>
        {t("En Connect o Settings > API Keys copia Project URL y Publishable key. Nunca copies Secret key ni service_role.", language)}
      </GuideStep>
      <GuideStep number="5" title={t("Conectar tus dispositivos", language)}>
        {t("Guarda la URL y la clave publica. Crea la cuenta una vez e inicia sesion con la misma cuenta en Android y web.", language)}
      </GuideStep>
      <div className="sync-guide-ready">
        <CheckCircle2 size={21} />
        <span>{t("La configuracion esta lista cuando Authentication > Users muestra tu correo y money_snapshots contiene una fila despues de la primera subida.", language)}</span>
      </div>
    </div>
  );
}

function GuideStep({ number, title, children }) {
  return (
    <div className="sync-guide-step">
      <span className="sync-guide-number">{number}</span>
      <div><strong>{title}</strong><p>{children}</p></div>
    </div>
  );
}

export function ReportDialog({ onClose, onExport, language }) {
  const [scope, setScope] = useState("mensual");
  const [anchor, setAnchor] = useState(today());
  const [format, setFormat] = useState("xlsx");
  return (
    <Modal title={t("Generar reporte", language)} onClose={onClose} language={language}>
      <div className="report-form">
        <label>{t("Periodo", language)}
          <select value={scope} onChange={(event) => setScope(event.target.value)}>
            <option value="diario">{t("Diario", language)}</option>
            <option value="semanal">{t("Semanal", language)}</option>
            <option value="mensual">{t("Mensual", language)}</option>
            <option value="semestral">{t("Semestral", language)}</option>
            <option value="anual">{t("Anual", language)}</option>
            <option value="todo">{t("Todo", language)}</option>
          </select>
        </label>
        {scope !== "todo" ? <label>{t("Fecha", language)}<input type="date" value={anchor} onChange={(event) => setAnchor(event.target.value)} /></label> : null}
        <label>{t("Formato", language)}
          <select value={format} onChange={(event) => setFormat(event.target.value)}>
            <option value="xlsx">XLSX</option>
            <option value="xls">{t("XLS compatible", language)}</option>
          </select>
        </label>
        <button className="dialog-primary" onClick={() => onExport(scope, anchor, format)}>
          <FileSpreadsheet size={19} /> {t("Descargar reporte", language)}
        </button>
      </div>
    </Modal>
  );
}

export function LanguageDialog({ value, onClose, onSave }) {
  const [language, setLanguage] = useState(value || "es");
  const languages = [
    ["es", "Español"],
    ["en", "English"],
    ["pt", "Português"],
    ["fr", "Français"],
  ];
  return (
    <Modal title={t("Idioma", value)} onClose={onClose} language={value}>
      <div className="language-options">
        {languages.map(([code, label]) => (
          <button className={language === code ? "active" : ""} onClick={() => setLanguage(code)} key={code}>
            <Languages size={19} /> {label}
          </button>
        ))}
        <button className="dialog-primary" onClick={() => onSave(language)}><Save size={18} /> {t("Guardar", language)}</button>
      </div>
    </Modal>
  );
}

export function AboutDialog({ version, language, onClose }) {
  return (
    <Modal title={t("Acerca de", language)} onClose={onClose} language={language}>
      <div className="about-dialog">
        <img src="/pig.png" alt="" />
        <h3>Control Financiero</h3>
        <p><strong>{t("Version", language)}:</strong> {version}</p>
        <p><strong>{t("Desarrollador", language)}:</strong> Gwr4rd</p>
        <a className="dialog-primary" href="https://github.com/Gwr4rd" target="_blank" rel="noreferrer">
          <ExternalLink size={18} /> {t("Ver repositorios", language)}
        </a>
      </div>
    </Modal>
  );
}

function Modal({ title, onClose, children, language }) {
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <header>
          <h2>{title}</h2>
          <button onClick={onClose} aria-label={t("Cerrar dialogo", language)}><X size={22} /></button>
        </header>
        {children}
      </section>
    </div>
  );
}
