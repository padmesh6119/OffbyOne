import { useEffect } from 'react';
import { ThemeProvider, ThemeToggle } from './theme';
import { HashRouter, Routes, Route, NavLink, Navigate, Outlet, useLocation } from 'react-router-dom';
import Join from './pages/Join';
import SqlArena from './pages/SqlArena';
import Profile from './pages/Profile';
import Leaderboard from './pages/Leaderboard';
import './engagement.css';
import './index.css';
import './design.css';

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
  const section = location.pathname === '/profile' ? 'YOUR PROGRESS' : location.pathname === '/leaderboard' ? 'THE STANDINGS' : 'THE ARENA';
  return <div className="sql-app workspace">
    <header className="site-header">
      <NavLink to="/" className="workspace-brand"><span className="brand-symbol">[ / ]</span> offbyone<span className="brand-period">.</span></NavLink>
      <nav aria-label="Main navigation" className="workspace-nav"><NavLink to="/" end>SQL Arena</NavLink><NavLink to="/profile">My Progress</NavLink><NavLink to="/leaderboard">Standings</NavLink></nav>
      <div className="header-account"><ThemeToggle/><span className="player-monogram">{username?.[0]?.toUpperCase()}</span><span>{username}</span></div>
    </header>
    <main className="workspace-main">
      <header className="arena-top"><span className="arena-breadcrumb">PLAY / <strong>{section}</strong></span><span className="session-label">GUEST PASS <i/> 24 HOURS</span></header>
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
      <Route path="/leaderboard" element={<Leaderboard />} />
    </Route>
    <Route path="/sql" element={<ArenaAlias />} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes></HashRouter></ThemeProvider>;
}
