import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
import { updateSession, downloadFile } from '../lib/session';
import { usePreferences } from '../preferences';
import { useTheme } from '../theme';
import ConfirmDialog from '../components/ConfirmDialog';

export default function Settings() {
  const username = localStorage.getItem('username'), navigate = useNavigate();
  const { preferences, update, reset } = usePreferences(), { theme, setTheme } = useTheme();
  const [level, setLevel] = useState(Number(localStorage.getItem('skillLevel') || 1));
  const [savedLevel, setSavedLevel] = useState(level), [busy, setBusy] = useState(false), [notice, setNotice] = useState(''), [error, setError] = useState('');
  const [bank, setBank] = useState([]), [drafts, setDrafts] = useState([]), [confirm, setConfirm] = useState(null);
  function refreshDrafts() {
    setDrafts(Object.keys(localStorage).filter(k => k.startsWith(`obo:draft:${username}:`)).map(key => ({ key, slug: key.split(':').at(-1), query: localStorage.getItem(key) || '', practice: key.includes(':practice:') })));
  }
  useEffect(() => {
    refreshDrafts(); let live = true;
    Promise.all([api.session(), api.sqlProblems()]).then(([session, problems]) => { if (live) { setLevel(session.skillLevel); setSavedLevel(session.skillLevel); setBank(problems); } }).catch(e => { if (live) setError(errorMessage(e)); });
    return () => { live = false; };
  }, []);
  async function saveLevel() {
    setBusy(true); setError(''); setNotice('');
    try { const saved = await api.updateSession(level); updateSession(saved); setSavedLevel(saved.skillLevel); setNotice('SQL level saved for future duels. Existing duels keep their current difficulty.'); }
    catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  function clearData() {
    if (confirm.kind === 'one') localStorage.removeItem(confirm.key);
    else if (confirm.kind === 'drafts') drafts.forEach(d => localStorage.removeItem(d.key));
    else localStorage.removeItem(`obo:bookmarks:${username}`);
    refreshDrafts(); setConfirm(null); setNotice('Browser data cleared. Your submitted queries and server progress are unchanged.');
  }
  function restore(draft, confirmed = false) {
    const key = `obo:draft:${username}:practice:${draft.slug}`;
    const existing = localStorage.getItem(key);
    if (!confirmed && existing !== null && existing !== draft.query) { setConfirm({ kind: 'restore', draft }); return; }
    localStorage.setItem(key, draft.query); navigate(`/?challenge=${encodeURIComponent(draft.slug)}`);
  }
  return <section className="settings-page"><div className="page-intro"><div><h1>Make yourself at home.</h1><p>Fine tune your workspace and manage what is saved in this browser.</p></div></div>
    {notice && <div className="success-notice" role="status">{notice}</div>}{error && <div className="hub-notice" role="alert">{error}</div>}
    <div className="settings-grid"><section className="settings-card"><span className="arena-eyebrow">01 / GUEST PASS</span><h2>{username}</h2><p>This pass ends {new Date(localStorage.getItem('expiresAt')).toLocaleString()}. Guest names cannot be renamed during a pass. Log out to start a fresh pass.</p><label htmlFor="settings-level">Your SQL level · {level}/10</label><input id="settings-level" type="range" min="1" max="10" value={level} onChange={e => setLevel(Number(e.target.value))}/><div className="settings-range"><span>Foundations</span><span>Advanced</span></div><p>Your saved level sets the starting difficulty of future duels. You can still adjust it in a duel lobby.</p><button className="arena-primary" disabled={busy || level === savedLevel} onClick={saveLevel}>{busy ? 'Saving…' : 'Save SQL level'}</button></section>
    <section className="settings-card"><span className="arena-eyebrow">02 / WORKSPACE</span><h2>Comfort is personal.</h2><fieldset className="theme-choices"><legend>Appearance</legend>{['dark', 'light'].map(t => <button key={t} className="arena-secondary" aria-pressed={theme === t} onClick={() => setTheme(t)}>{t === 'dark' ? '☾ Dark' : '☀ Light'}</button>)}</fieldset><label htmlFor="font-size">Editor font size · {preferences.fontSize}px</label><input id="font-size" type="range" min="12" max="22" step="1" value={preferences.fontSize} onChange={e => update({ fontSize: Number(e.target.value) })}/>{[['wordWrap', 'Wrap long lines'], ['lineNumbers', 'Show line numbers'], ['reducedMotion', 'Reduce animations']].map(([key, label]) => <label className="settings-switch" key={key}><span>{label}</span><input type="checkbox" checked={preferences[key]} onChange={e => update({ [key]: e.target.checked })}/></label>)}<small>Changes save automatically in this browser.</small><button className="arena-secondary" onClick={() => { reset(); setNotice('Editor preferences reset.'); }}>Reset editor preferences</button></section></div>
    <section className="draft-manager"><div className="section-heading"><div><span className="arena-eyebrow">03 / YOUR LOCAL NOTEBOOK</span><h2>Keep the queries worth keeping.</h2></div><button className="arena-secondary" disabled={!drafts.length} onClick={() => setConfirm({ kind: 'drafts' })}>Clear all drafts</button></div><p className="settings-copy">Drafts are saved automatically in this browser for this guest pass. Download a query as a .sql file to keep it after logout.</p>{drafts.length ? <div className="draft-list">{drafts.map(d => <details className="draft-item" key={d.key}><summary><span>{bank.find(p => p.slug === d.slug)?.title || d.slug}<small>{d.practice ? 'Solo practice' : 'Duel draft'} · {d.query.length.toLocaleString()} characters</small></span><span aria-hidden="true">↓</span></summary><pre>{d.query || '-- Empty draft'}</pre><div className="draft-actions"><button className="arena-secondary" disabled={!bank.some(p => p.slug === d.slug)} onClick={() => restore(d)}>Open in practice ↗</button><button className="arena-secondary" onClick={() => downloadFile(`${d.slug}.sql`, d.query)}>Download .sql ↓</button><button className="text-danger" onClick={() => setConfirm({ kind: 'one', key: d.key })}>Delete draft</button></div></details>)}</div> : <div className="workspace-empty"><h3>A clean notebook.</h3><p>Open a practice challenge and start typing. Your draft will appear here.</p><button className="arena-secondary" onClick={() => navigate('/')}>Browse challenges ↗</button></div>}<div className="settings-bookmarks"><div><strong>Start your reading list again.</strong><p>Clear bookmarks for this pass. Your solved challenges stay recorded.</p></div><button className="arena-secondary" onClick={() => setConfirm({ kind: 'bookmarks' })}>Clear bookmarks</button></div></section>
    {confirm && <ConfirmDialog title={confirm.kind === 'restore' ? 'Replace your practice draft?' : confirm.kind === 'one' ? 'Delete this draft?' : confirm.kind === 'drafts' ? 'Clear all your drafts?' : 'Clear your bookmarks?'} confirmText={confirm.kind === 'restore' ? 'Replace practice draft' : 'Clear browser data'} onClose={() => setConfirm(null)} onConfirm={() => confirm.kind === 'restore' ? restore(confirm.draft, true) : clearData()}>{confirm.kind === 'restore' ? <p>A different solo draft already exists for this challenge. Opening this draft will replace it.</p> : <p>This removes only the selected browser data. Your submitted queries and server progress remain available for the rest of this guest pass.</p>}<p>Download any drafts you want to keep before clearing them.</p></ConfirmDialog>}
  </section>;
}
