import {
  BarChart3,
  CloudMoon,
  FileSpreadsheet,
  ListTree,
  Plus,
  RefreshCw,
  Search,
  Sun,
  WalletCards,
} from "lucide-react";

const navItems = [
  { id: "transactions", label: "Transacciones", icon: ListTree },
  { id: "status", label: "Estado", icon: BarChart3 },
  { id: "accounts", label: "Cuentas", icon: WalletCards },
];

export function Header({ active, dark, onNav, onTheme, onSearch, onNew, onReport, onSync }) {
  return (
    <header className="app-header">
      <div className="brand">
        <img src="/pig.png" alt="" />
        <strong>MoneyMate Modern</strong>
      </div>
      <nav className="desktop-top-nav" aria-label="Navegacion principal">
        {navItems.map(({ id, label }) => (
          <button className={active === id ? "active" : ""} onClick={() => onNav(id)} key={id}>{label}</button>
        ))}
      </nav>
      <div className="header-actions">
        <IconAction icon={Search} label="Buscar" onClick={onSearch} />
        <button className="primary-action" onClick={onNew}>
          <Plus size={19} />
          <span>Nuevo movimiento</span>
        </button>
        <IconAction icon={FileSpreadsheet} label="Reporte" onClick={onReport} />
        <IconAction icon={RefreshCw} label="Sincronizar" onClick={onSync} />
        <button className="icon-only" onClick={onTheme} title={dark ? "Modo claro" : "Modo oscuro"} aria-label={dark ? "Modo claro" : "Modo oscuro"}>
          {dark ? <Sun size={21} /> : <CloudMoon size={22} />}
        </button>
      </div>
    </header>
  );
}

export function SideNav({ active, onChange }) {
  return (
    <aside className="side-nav">
      {navItems.map(({ id, label, icon: Icon }) => (
        <button className={active === id ? "active" : ""} onClick={() => onChange(id)} key={id}>
          <Icon size={22} />
          <span>{label}</span>
        </button>
      ))}
    </aside>
  );
}

export function MobileNav({ active, onChange }) {
  return (
    <nav className="mobile-nav" aria-label="Navegacion principal">
      {navItems.map(({ id, label, icon: Icon }) => (
        <button className={active === id ? "active" : ""} onClick={() => onChange(id)} key={id}>
          <Icon size={24} />
          <span>{label}</span>
        </button>
      ))}
    </nav>
  );
}

function IconAction({ icon: Icon, label, onClick }) {
  return (
    <button className="icon-action" onClick={onClick}>
      <Icon size={20} />
      <span>{label}</span>
    </button>
  );
}
