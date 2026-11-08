import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
import { clearSession } from '../lib/session';
import ConfirmDialog from './ConfirmDialog';
export default function PlayerMenu() {
  const [open, setOpen] = useState(false), [confirm, setConfirm] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const menu = useRef(null), trigger = useRef(null), location = useLocation(), navigate = useNavigate();
  const username = localStorage.getItem('username') || 'Guest';
  useEffect(() => { setOpen(false); }, [location]);
  useEffect(() => {
    if (!open) return;
    const close = e => { if (!menu.current?.contains(e.target)) setOpen(false); };
    const escape = e => { if (e.key === 'Escape') { setOpen(false); trigger.current?.focus(); } };
    document.addEventListener('pointerdown', close); document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('pointerdown', close); document.removeEventListener('keydown', escape); };
  }, [open]);
  async function logout() {
    setBusy(true); setError('');
    try { await api.logout(); clearSession(true); navigate('/join?loggedOut=1', { replace: true }); }
    catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }
  return <><div className="player-menu" ref={menu}><button ref={trigger} className="account-trigger" aria-expanded={open} aria-controls="player-menu-links" onClick={() => setOpen(v => !v)}><span className="player-monogram">{username[0].toUpperCase()}</span><span>{username}</span><span aria-hidden="true">⌄</span></button>{open && <div className="account-popover" id="player-menu-links"><small>YOUR GUEST PASS</small><strong>{username}</strong><Link to="/profile">My progress <span aria-hidden="true">↗</span></Link><Link to="/settings">Settings & drafts <span aria-hidden="true">⚙</span></Link><Link to="/help">Help & shortcuts <span aria-hidden="true">?</span></Link><button onClick={() => { setOpen(false); setConfirm(true); }}>Log out <span aria-hidden="true">→</span></button></div>}</div>{confirm && <ConfirmDialog title="End this guest pass?" confirmText="Log out" busy={busy} onClose={() => setConfirm(false)} onConfirm={logout}><p>Your name becomes available again, your current pass ends, and unfinished duels are forfeited.</p><p>Drafts and bookmarks for this pass will be removed from this browser. Download anything you want to keep first.</p><Link to="/profile" onClick={() => setConfirm(false)}>Export progress first ↗</Link>{error && <p className="form-error" role="alert">{error}</p>}</ConfirmDialog>}</>;
}
