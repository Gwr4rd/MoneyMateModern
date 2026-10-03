import {
  Banknote,
  BarChart3,
  CloudMoon,
  DatabaseBackup,
  FileSpreadsheet,
  ListTree,
  Languages,
  Info,
  MoreVertical,
  Plus,
  RefreshCw,
  Search,
  Sun,
  WalletCards,
} from "lucide-react";
import { t } from "../i18n";

const navItems = [
  { id: "transactions", label: "Transacciones", icon: ListTree },
  { id: "status", label: "Estado", icon: BarChart3 },
  { id: "accounts", label: "Cuentas", icon: WalletCards },
];

export function Header({ active, dark, language, onNav, onTheme, onCurrency, onSearch, onNew, onReport, onData, onSync, onManage, onLanguage, onAbout }) {
  const mobileAction = (action) => (event) => {
    event.currentTarget.closest(".mobile-overflow")?.removeAttribute("open");
    action();
  };
  return (
    <header className="app-header">
      <div className="brand">
        <img src="/pig.png" alt="" />
        <strong>Control Financiero</strong>
      </div>
      <nav className="desktop-top-nav" aria-label={t("Navegacion principal", language)}>
        {navItems.map(({ id, label }) => (
          <button className={active === id ? "active" : ""} onClick={() => onNav(id)} key={id}>{t(label, language)}</button>
        ))}
      </nav>
      <div className="header-actions">
        <IconAction icon={Search} label={t("Buscar", language)} onClick={onSearch} />
        <button className="primary-action" onClick={onNew}>
          <Plus size={19} />
          <span>{t("Nuevo movimiento", language)}</span>
        </button>
        <details className="mobile-overflow">
          <summary aria-label={t("Abrir menu", language)}><MoreVertical size={25} /></summary>
          <div>
            <details className="menu-group">
              <summary><DatabaseBackup size={20} /> {t("Datos", language)}</summary>
              <button onClick={mobileAction(onReport)}><FileSpreadsheet size={18} /> {t("Generar reporte", language)}</button>
              <button onClick={mobileAction(onData)}><DatabaseBackup size={18} /> {t("Datos y respaldos", language)}</button>
            </details>
            <button onClick={mobileAction(onSync)}><RefreshCw size={20} /> {t("Sincronizar", language)}</button>
            <button onClick={mobileAction(onManage)}><ListTree size={20} /> {t("Organizar cuentas", language)}</button>
            <details className="menu-group">
              <summary><CloudMoon size={20} /> {t("Preferencias", language)}</summary>
              <button onClick={mobileAction(onTheme)}>{dark ? <Sun size={18} /> : <CloudMoon size={18} />} {t(dark ? "Modo claro" : "Modo oscuro", language)}</button>
              <button onClick={mobileAction(onCurrency)}><Banknote size={18} /> {t("Moneda y pais", language)}</button>
              <button onClick={mobileAction(onLanguage)}><Languages size={18} /> {t("Idioma", language)}</button>
            </details>
            <button onClick={mobileAction(onAbout)}><Info size={20} /> {t("Acerca de", language)}</button>
          </div>
        </details>
      </div>
    </header>
  );
}

export function SideNav({ active, onChange, language }) {
  return (
    <aside className="side-nav">
      {navItems.map(({ id, label, icon: Icon }) => (
        <button className={active === id ? "active" : ""} onClick={() => onChange(id)} key={id}>
          <Icon size={22} />
          <span>{t(label, language)}</span>
        </button>
      ))}
    </aside>
  );
}

export function MobileNav({ active, onChange, language }) {
  return (
    <nav className="mobile-nav" aria-label={t("Navegacion principal", language)}>
      {navItems.map(({ id, label, icon: Icon }) => (
        <button className={active === id ? "active" : ""} onClick={() => onChange(id)} key={id}>
          <Icon size={24} />
          <span>{t(label, language)}</span>
        </button>
      ))}
    </nav>
  );
}

function IconAction({ icon: Icon, label, onClick, className = "" }) {
  return (
    <button className={`icon-action ${className}`} onClick={onClick} aria-label={label}>
      <Icon size={20} />
      <span>{label}</span>
    </button>
  );
}
