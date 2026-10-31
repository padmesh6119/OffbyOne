import { useEffect } from 'react';
import { ThemeProvider, ThemeToggle } from './theme';
import { HashRouter, Routes, Route, NavLink, Navigate, Outlet, useLocation } from 'react-router-dom';
import Join from './pages/Join';
import SqlArena from './pages/SqlArena';
import Profile from './pages/Profile';
import './index.css';
import './sql-arena.css';
import './workspace.css';
import './themes.css';

function Workspace() {
  const location = useLocation();
  const username = localStorage.getItem('username');
  useEffect(() => {
    const check = () => {
      const expiry = localStorage.getItem('expiresAt');
      if (expiry && Date.parse(expiry) <= Date.now()) {
        ['token', 'username', 'expiresAt', 'skillLevel'].forEach(k => localStorage.removeItem(k));
        window.location.hash = '/join?expired=1';
      }
    };
    check(); const timer = setInterval(check, 1000); return () => clearInterval(timer);
  }, []);
  if (!localStorage.getItem('token')) return <Navigate to="/join" state={{ from: location.pathname + location.search }} replace />;
  const section = location.pathname === '/profile' ? 'YOUR PROGRESS' : 'THE ARENA';
  return <div className="sql-app workspace">
    <aside className="workspace-sidebar">
      <NavLink to="/" className="workspace-brand">offby<span>one.</span><small>THE COMPETITIVE WORKSPACE</small></NavLink>
      <div className="sidebar-caption">MAKE YOUR NEXT MOVE</div>
      <nav aria-label="Main navigation" className="workspace-nav">
        <NavLink to="/" end><span>↗</span>SQL Arena<small>01</small></NavLink>
        <NavLink to="/profile"><span>◷</span>My Progress<small>03</small></NavLink>
      </nav>
      <div className="sidebar-note"><span>SMALL MARGINS.</span><strong>Better every round.</strong><p>Practice with intent.<br/>Compete with friends.</p></div>
      <div className="sidebar-identity"><span className="player-monogram">{username?.[0]?.toUpperCase()}</span><div><strong>{username}</strong><small>Guest session · this browser</small></div></div>
    </aside>
    <main className="workspace-main">
      <header className="arena-top"><div className="arena-breadcrumb">OFFBYONE <span>/</span><strong>{section}</strong></div><div className="arena-presence"><i/> SQL ARENA <ThemeToggle/><span className="arena-user">{username}</span></div></header>
      <div className="workspace-content"><Outlet /></div>
      <footer className="arena-footer"><span>OFFBYONE <b>MAKE EVERY ATTEMPT COUNT</b></span><span>Small margins. Better solutions.</span><span>BUILT FOR THE NEXT ROUND ↗</span></footer>
    </main>
  </div>;
}
function ArenaAlias() { const { search } = useLocation(); return <Navigate to={'/' + search} replace />; }
export default function App() {
  return <ThemeProvider><HashRouter><Routes>
    <Route path="/join" element={<Join />} />
    <Route element={<Workspace />}>
      <Route index element={<SqlArena />} />
      <Route path="/profile" element={<Profile />} />
    </Route>
    <Route path="/sql" element={<ArenaAlias />} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes></HashRouter></ThemeProvider>;
}
