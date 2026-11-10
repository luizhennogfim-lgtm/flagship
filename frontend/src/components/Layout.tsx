import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Badge } from './ui';

export default function Layout() {
  const { session, logout, canAdmin } = useAuth();
  const link = ({ isActive }: { isActive: boolean }) => (isActive ? 'nav-link active' : 'nav-link');
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">🚩 Flagship</div>
        <nav>
          <NavLink to="/" end className={link}>Overview</NavLink>
          <NavLink to="/flags" className={link}>Feature flags</NavLink>
          <NavLink to="/environments" className={link}>Environments</NavLink>
          {canAdmin && <NavLink to="/audit" className={link}>Audit log</NavLink>}
          <NavLink to="/organization" className={link}>Organization</NavLink>
        </nav>
      </aside>
      <div className="main">
        <header className="topbar">
          <span className="muted">Organization: <strong>{session?.organizationSlug}</strong></span>
          <span className="topbar-right">
            <span>{session?.email}</span>
            <Badge tone="blue">{session?.role}</Badge>
            <button className="btn" onClick={logout}>Sign out</button>
          </span>
        </header>
        <main className="content"><Outlet /></main>
      </div>
    </div>
  );
}
