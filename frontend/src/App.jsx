import CreatorSecret from './components/CreatorSecret';
import Creators from './pages/Creators';
import BrandMark from './components/BrandMark';
import { useEffect, useState } from 'react';
import { ThemeProvider, ThemeToggle } from './theme';
import { PreferencesProvider } from './preferences';
import { HashRouter, Routes, Route, NavLink, Navigate, Outlet, useLocation } from 'react-router-dom';
import Join from './pages/Join';
import SqlArena from './pages/SqlArena';
import Profile from './pages/Profile';
import Leaderboard from './pages/Leaderboard';
import Settings from './pages/Settings';
import Help from './pages/Help';
import NotFound from './pages/NotFound';
import PlayerMenu from './components/PlayerMenu';
import AppBoundary from './components/AppBoundary';
import Icon from './components/Icon';
import { api, errorMessage } from './lib/api';
import { clearSession, updateSession } from './lib/session';
import './index.css';
import './design.css';
import './engagement.css';
import './essentials.css';
import './studio.css';
import './daylight.css';
import './motion.css';
import './clarity.css';

function Workspace() {
  const location = useLocation();
  const [, refresh] = useState(0), [now, setNow] = useState(Date.now()), [online, setOnline] = useState(navigator.onLine);
  const [sessionError, setSessionError] = useState(''), [retry, setRetry] = useState(0);
  const token = localStorage.getItem('token');
  useEffect(() => { window.scrollTo({ top: 0, behavior: 'instant' }); }, [location.pathname, location.search]);
  useEffect(() => {
    const changed = () => refresh(v => v + 1);
    const connected = () => setOnline(navigator.onLine);
    window.addEventListener('storage', changed); window.addEventListener('obo:session', changed);
    window.addEventListener('online', connected); window.addEventListener('offline', connected);
    const timer = setInterval(() => {
      const expiry = localStorage.getItem('expiresAt'); setNow(Date.now());
      if (expiry && Date.parse(expiry) <= Date.now()) {
        clearSession(); window.location.hash = '/join?expired=1';
      }
    }, 1000);
    return () => { clearInterval(timer); window.removeEventListener('storage', changed); window.removeEventListener('obo:session', changed); window.removeEventListener('online', connected); window.removeEventListener('offline', connected); };
  }, []);
  useEffect(() => {
    if (!token) return;
    let live = true; setSessionError('');
    api.session().then(s => { if (live && localStorage.getItem('token') === token) updateSession(s); }).catch(e => { if (live) setSessionError(errorMessage(e)); });
    return () => { live = false; };
  }, [token, retry]);
  if (!token && location.pathname !== '/help') return <Navigate to="/join" state={{ from: location.pathname + location.search }} replace />;
  const remaining = Math.max(0, Math.ceil((Date.parse(localStorage.getItem('expiresAt')) - now) / 1000));
  const section = { '/profile': 'YOUR PROGRESS', '/leaderboard': 'THE STANDINGS', '/settings': 'YOUR SETTINGS', '/help': 'THE BRIEFING' }[location.pathname] || 'THE ARENA';
  return <div className="sql-app workspace">
    <a className="skip-link" href="#workspace-main">Skip to content</a>
    <aside className="club-sidebar">
      <div className="brand-home"><NavLink to="/" className="workspace-brand"><span className="brand-symbol"><BrandMark/></span> offbyone</NavLink><CreatorSecret/></div>
      <nav aria-label="Main navigation" className="club-nav">
        <NavLink to="/" end><Icon name="arena"/>SQL Arena<span className="nav-active-dot"/></NavLink>
        <NavLink to="/profile"><Icon name="progress"/>My Progress</NavLink>
        <NavLink to="/leaderboard"><Icon name="trophy"/>Standings</NavLink>
      </nav>
      <div className="sidebar-bottom">
        <nav className="club-nav" aria-label="Resources"><NavLink to="/settings"><Icon name="settings"/>Settings & drafts</NavLink><NavLink to="/help"><Icon name="help"/>Help & shortcuts</NavLink></nav>
      </div>
    </aside>
    <header className="site-header">
      <div className="header-title"><strong>{section === 'THE ARENA' ? 'SQL Arena' : section === 'YOUR PROGRESS' ? 'My Progress' : section === 'THE STANDINGS' ? 'Standings' : section === 'YOUR SETTINGS' ? 'Settings & drafts' : 'Help & shortcuts'}</strong></div>
      <div className="header-account"><ThemeToggle/>{token ? <PlayerMenu/> : <NavLink className="arena-secondary" to="/join">Get a guest pass ↗</NavLink>}</div>
    </header>
    <main className="workspace-main" id="workspace-main" tabIndex="-1">
      {!online && <div className="connection-banner" role="status">You are offline. Your open query stays here. Reconnect before running or submitting.</div>}
      {token && remaining > 0 && remaining < 900 && <div className="connection-banner" role="status">Your guest pass ends in less than 15 minutes. <NavLink to="/profile">Export progress</NavLink> or <NavLink to="/settings">download drafts</NavLink> before it expires.</div>}
      {sessionError && online && <div className="connection-banner" role="status">Could not check your guest pass. <button onClick={() => setRetry(v => v + 1)}>Try again</button></div>}
      <div className="workspace-content"><Outlet /></div>
      <footer className="arena-footer"><span>offbyone.</span><span className="footer-links"><NavLink to="/help">Help & shortcuts</NavLink>{token && <NavLink to="/settings">Settings</NavLink>}<a href="https://github.com/padmesh6119/OffbyOne" target="_blank" rel="noreferrer">GitHub ↗</a></span></footer>
    </main>
  </div>;
}
function ArenaAlias() { const { search } = useLocation(); return <Navigate to={'/' + search} replace />; }
export default function App() {
  return <AppBoundary><ThemeProvider><PreferencesProvider><HashRouter><Routes>
    <Route path="/join" element={<Join />} />
    <Route path="/creators" element={<Creators />} />
    <Route element={<Workspace />}>
      <Route index element={<SqlArena />} />
      <Route path="/profile" element={<Profile />} />
      <Route path="/leaderboard" element={<Leaderboard />} />
      <Route path="/settings" element={<Settings />} />
      <Route path="/help" element={<Help />} />
      <Route path="*" element={<NotFound />} />
    </Route>
    <Route path="/sql" element={<ArenaAlias />} />
  </Routes></HashRouter></PreferencesProvider></ThemeProvider></AppBoundary>;
}
