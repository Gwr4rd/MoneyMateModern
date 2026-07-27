import { useState } from "react";
import { CloudDownload, CloudUpload, FileSpreadsheet, Save, UserPlus, X } from "lucide-react";
import { getSupabaseConfig, isSupabaseConfigured, saveSupabaseConfig } from "../lib/supabase";
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

export function SyncDialog({ onClose, onUpload, onDownload, onCreateAccount, busy, message }) {
  const initialConfig = getSupabaseConfig();
  const [stage, setStage] = useState(() => isSupabaseConfigured() ? "account" : "config");
  const [url, setUrl] = useState(initialConfig.url);
  const [key, setKey] = useState(initialConfig.key);
  const [accountMode, setAccountMode] = useState("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [localMessage, setLocalMessage] = useState("");

  function storeConnection() {
    try {
      saveSupabaseConfig(url, key);
      setLocalMessage("Conexion guardada. Ahora conecta tu cuenta.");
      setStage("account");
    } catch (error) {
      setLocalMessage(error.message);
    }
  }

  return (
    <Modal title="Cuenta y sincronizacion" onClose={onClose}>
      <div className="sync-form">
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
          </>
        ) : (
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
              <div className="dialog-actions">
                <button disabled={busy || !email || !password} onClick={() => onDownload(email, password)}>
                  <CloudDownload size={19} /> Descargar
                </button>
                <button className="dialog-primary" disabled={busy || !email || !password} onClick={() => onUpload(email, password)}>
                  <CloudUpload size={19} /> Subir
                </button>
              </div>
            )}
            <button className="sync-change" onClick={() => { setLocalMessage(""); setStage("config"); }}>
              Cambiar conexion de Supabase
            </button>
          </>
        )}
        {localMessage || message ? <p className="dialog-message">{localMessage || message}</p> : null}
      </div>
    </Modal>
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
