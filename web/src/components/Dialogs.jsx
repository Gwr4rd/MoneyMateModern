import { useEffect, useState } from "react";
import {
  ArrowLeft,
  BookOpen,
  CheckCircle2,
  CloudDownload,
  CloudUpload,
  Copy,
  ExternalLink,
  FileSpreadsheet,
  LogIn,
  LogOut,
  Save,
  UserPlus,
  X,
} from "lucide-react";
import { getSupabaseConfig, isSupabaseConfigured, saveSupabaseConfig } from "../lib/supabase";
import { SUPABASE_DASHBOARD_URL, SUPABASE_SQL } from "../lib/supabaseSetup";
import { today } from "../lib/finance";

export function MovementDialog({ data, onClose, onSave }) {
  const [form, setForm] = useState({
    date: today(),
    time: new Date().toTimeString().slice(0, 5),
    kind: "expense",
    account: data.accounts[0]?.name || "",
    toAccount: data.accounts[1]?.name || "",
    category: data.categories.find((category) => category.kind === "expense")?.name || "",
    amount: "",
    note: "",
    description: "",
  });
  const categories = data.categories.filter((category) => category.kind === form.kind);

  function change(event) {
    const { name, value } = event.target;
    setForm((current) => {
      const next = { ...current, [name]: value };
      if (name === "kind" && value !== "transfer") {
        next.category = data.categories.find((category) => category.kind === value)?.name || "";
      }
      return next;
    });
  }

  function submit(event) {
    event.preventDefault();
    if (!Number(form.amount) || Number(form.amount) <= 0) return;
    if (form.kind === "transfer" && form.account === form.toAccount) return;
    onSave({
      ...form,
      id: crypto.randomUUID(),
      amount: Number(form.amount),
      category: form.kind === "transfer" ? "Transferencia" : form.category,
      toAccount: form.kind === "transfer" ? form.toAccount : "",
    });
  }

  return (
    <Modal title="Nuevo movimiento" onClose={onClose}>
      <form className="movement-form" onSubmit={submit}>
        <label>Tipo
          <select name="kind" value={form.kind} onChange={change}>
            <option value="expense">Gasto</option>
            <option value="income">Ingreso</option>
            <option value="transfer">Transferencia</option>
          </select>
        </label>
        <div className="form-pair">
          <label>Fecha<input name="date" type="date" value={form.date} onChange={change} /></label>
          <label>Hora<input name="time" type="time" value={form.time} onChange={change} /></label>
        </div>
        <label>Importe<input name="amount" type="number" min="0.01" step="0.01" value={form.amount} onChange={change} autoFocus /></label>
        <label>Cuenta
          <select name="account" value={form.account} onChange={change}>
            {data.accounts.filter((account) => !account.hidden).map((account) => <option key={account.name}>{account.name}</option>)}
          </select>
        </label>
        {form.kind === "transfer" ? (
          <label>Cuenta destino
            <select name="toAccount" value={form.toAccount} onChange={change}>
              {data.accounts.filter((account) => !account.hidden).map((account) => <option key={account.name}>{account.name}</option>)}
            </select>
          </label>
        ) : (
          <label>Categoria
            <select name="category" value={form.category} onChange={change}>
              {categories.map((category) => <option key={category.name}>{category.name}</option>)}
            </select>
          </label>
        )}
        <label>Nota<input name="note" value={form.note} onChange={change} /></label>
        <label>Descripcion<input name="description" value={form.description} onChange={change} /></label>
        <button className="dialog-primary" type="submit">Guardar</button>
      </form>
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
    <Modal title="Cuenta y sincronizacion" onClose={onClose}>
      <div className="sync-form">
        {stage === "help" ? (
          <SupabaseGuide
            onBack={() => {
              setLocalMessage("");
              setStage(connectionStage);
            }}
            onCopy={copySql}
          />
        ) : null}
        {stage === "config" ? (
          <>
            <div className="sync-status">Supabase no configurado. Guarda primero la conexion del proyecto.</div>
            <label>URL del proyecto
              <input type="url" placeholder="https://tu-proyecto.supabase.co" value={url} onChange={(event) => setUrl(event.target.value)} />
            </label>
            <label>Clave publica
              <input type="text" placeholder="Clave publishable o anon" value={key} onChange={(event) => setKey(event.target.value)} />
            </label>
            <p className="dialog-message">Usa solamente la clave publica. Nunca coloques una clave service_role.</p>
            <button className="dialog-primary" onClick={storeConnection}>
              <Save size={19} /> Guardar conexion
            </button>
            <button className="sync-guide-button" onClick={() => setStage("help")}>
              <BookOpen size={19} /> Como crear y configurar Supabase
            </button>
          </>
        ) : null}
        {stage === "account" ? (
          <>
            <div className="sync-status ready">Configuracion de Supabase guardada. Conecta tu cuenta para sincronizar.</div>
            <div className="sync-account-tabs" role="tablist" aria-label="Acceso a Supabase">
              <button className={accountMode === "login" ? "active" : ""} onClick={() => setAccountMode("login")}>Iniciar sesion</button>
              <button className={accountMode === "create" ? "active" : ""} onClick={() => setAccountMode("create")}>Crear cuenta</button>
            </div>
            <label>Correo<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} /></label>
            <label>Contrasena<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
            {accountMode === "create" ? (
              <button className="dialog-primary" disabled={busy || !email || !password} onClick={() => onCreateAccount(email, password)}>
                <UserPlus size={19} /> Crear cuenta
              </button>
            ) : (
              <button className="dialog-primary" disabled={busy || !email || !password} onClick={() => onSignIn(email, password)}>
                <LogIn size={19} /> Iniciar sesion
              </button>
            )}
            <button className="sync-guide-button" onClick={() => setStage("help")}>
              <BookOpen size={19} /> Manual y script SQL
            </button>
            <button className="sync-change" onClick={() => { setLocalMessage(""); setStage("config"); }}>
              Cambiar conexion de Supabase
            </button>
          </>
        ) : null}
        {stage === "connected" ? (
          <>
            <div className="sync-status connected">
              <CheckCircle2 size={21} />
              <span><strong>Cuenta conectada</strong><small>{user?.email}</small><small>Sesion activa y sincronizacion automatica</small></span>
            </div>
            <div className="dialog-actions">
              <button disabled={busy} onClick={onDownload}>
                <CloudDownload size={19} /> Descargar
              </button>
              <button className="dialog-primary" disabled={busy} onClick={onUpload}>
                <CloudUpload size={19} /> Subir ahora
              </button>
            </div>
            <button className="sync-guide-button" onClick={() => setStage("help")}>
              <BookOpen size={19} /> Manual y script SQL
            </button>
            <button className="sync-change" onClick={() => { setLocalMessage(""); setStage("config"); }}>
              Cambiar conexion de Supabase
            </button>
            <button className="sync-signout" disabled={busy} onClick={onSignOut}>
              <LogOut size={18} /> Cerrar sesion
            </button>
          </>
        ) : null}
        {localMessage || message ? <p className="dialog-message">{localMessage || message}</p> : null}
      </div>
    </Modal>
  );
}

function SupabaseGuide({ onBack, onCopy }) {
  return (
    <div className="sync-guide">
      <button className="sync-guide-back" onClick={onBack}><ArrowLeft size={20} /> Volver a la conexion</button>
      <div className="sync-guide-heading">
        <h2>Configurar Supabase</h2>
        <p>Sigue estos pasos una sola vez para activar la sincronizacion segura por cuenta.</p>
      </div>
      <GuideStep number="1" title="Crear el proyecto">
        Abre Supabase, crea un proyecto llamado MoneyMate Modern, guarda la contrasena de la base de datos y elige la region mas cercana.
      </GuideStep>
      <a className="dialog-primary sync-external" href={SUPABASE_DASHBOARD_URL} target="_blank" rel="noreferrer">
        <ExternalLink size={19} /> Abrir Supabase
      </a>
      <GuideStep number="2" title="Crear la tabla segura">
        En SQL Editor pulsa New query, pega el script de configuracion y selecciona Run.
      </GuideStep>
      <button className="sync-guide-button" onClick={onCopy}><Copy size={19} /> Copiar script SQL</button>
      <GuideStep number="3" title="Habilitar correo y contrasena">
        En Authentication &gt; Sign In / Providers abre Email y activa el proveedor. Para una prueba puedes desactivar Confirm email.
      </GuideStep>
      <GuideStep number="4" title="Copiar la conexion">
        En Connect o Settings &gt; API Keys copia Project URL y Publishable key. Nunca copies Secret key ni service_role.
      </GuideStep>
      <GuideStep number="5" title="Conectar tus dispositivos">
        Guarda la URL y la clave publica. Crea la cuenta una vez e inicia sesion con la misma cuenta en Android y web.
      </GuideStep>
      <div className="sync-guide-ready">
        <CheckCircle2 size={21} />
        <span>La configuracion esta lista cuando Authentication &gt; Users muestra tu correo y money_snapshots contiene una fila despues de la primera subida.</span>
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

export function ReportDialog({ onClose, onExport }) {
  const [scope, setScope] = useState("mensual");
  const [anchor, setAnchor] = useState(today());
  const [format, setFormat] = useState("xlsx");
  return (
    <Modal title="Generar reporte" onClose={onClose}>
      <div className="report-form">
        <label>Periodo
          <select value={scope} onChange={(event) => setScope(event.target.value)}>
            <option value="semanal">Semanal</option>
            <option value="mensual">Mensual</option>
            <option value="anual">Anual</option>
            <option value="todo">Todo</option>
          </select>
        </label>
        {scope !== "todo" ? <label>Fecha<input type="date" value={anchor} onChange={(event) => setAnchor(event.target.value)} /></label> : null}
        <label>Formato
          <select value={format} onChange={(event) => setFormat(event.target.value)}>
            <option value="xlsx">XLSX</option>
            <option value="xls">XLS compatible</option>
          </select>
        </label>
        <button className="dialog-primary" onClick={() => onExport(scope, anchor, format)}>
          <FileSpreadsheet size={19} /> Descargar reporte
        </button>
      </div>
    </Modal>
  );
}

function Modal({ title, onClose, children }) {
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <header>
          <h2>{title}</h2>
          <button onClick={onClose} aria-label="Cerrar"><X size={22} /></button>
        </header>
        {children}
      </section>
    </div>
  );
}
