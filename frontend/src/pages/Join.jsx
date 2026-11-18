import CreatorSecret from '../components/CreatorSecret';
import BrandMark from '../components/BrandMark';
import ArenaVisual from '../components/ArenaVisual';
import { ThemeToggle } from '../theme';
import { useEffect, useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
import { updateSession } from '../lib/session';
import { Link } from 'react-router-dom';
export default function Join() {
  const [name, setName] = useState(''), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const [skillLevel, setSkillLevel] = useState(1);
  const [ready,setReady] = useState(false), [connectionRetry,setConnectionRetry] = useState(0), [waited,setWaited] = useState(0);
  useEffect(() => {
    let live=true,timer,tries=0;
    async function wake() { const ok=await api.health();if(!live)return;setReady(ok);if(!ok&&++tries<15)timer=setTimeout(wake,4000); }
    wake();return()=>{live=false;clearTimeout(timer);};
  },[connectionRetry]);
  useEffect(() => {setWaited(0);if(!busy)return;const timer=setInterval(()=>setWaited(n=>n+1),1000);return()=>clearInterval(timer);},[busy]);
  const navigate = useNavigate(), location = useLocation();
  const params = new URLSearchParams(location.search);
  const requested = location.state?.from || params.get('next') || '/';
  const destination = requested.startsWith('/') && !requested.startsWith('//') && !requested.startsWith('/join') ? requested : '/';
  if (localStorage.getItem('token')) return <Navigate to={destination} replace />;
  async function submit(event) {
    event.preventDefault(); if(busy)return; setBusy(true); setError('');
    try {
      const session = await api.join(name.trim(), skillLevel);
      Object.keys(localStorage).filter(k => k.startsWith(`obo:draft:${session.username}:`) || k === `obo:match:${session.username}` || k === `obo:bookmarks:${session.username}`).forEach(k => localStorage.removeItem(k));
      updateSession(session);
      navigate(destination, { replace: true });
    } catch (e) { setError(errorMessage(e)); setBusy(false); }
  }
  return <div className="sql-app entry-page">
    <div className="entry-story"><div className="workspace-brand"><span className="brand-symbol"><BrandMark/></span> offbyone<CreatorSecret/></div>
      <div className="entry-pitch"><h1>GOOD SQL.<br/>GREAT <em>RIVALRIES.</em></h1><p>A playground for sharp minds.<br/>Practice solo. Go head to head. Get better.</p></div><ArenaVisual/>
    </div>
    <section className="entry-form-panel"><ThemeToggle/><h2>Your next move<br/>starts here.</h2><p>No account needed. Pick a temporary name and your SQL level.</p>
      {params.has('loggedOut') && <div className="success-notice" role="status">You are logged out. A new pass starts fresh.</div>}
      {params.has('expired') && <div className="arena-error" role="status">Your saved session is no longer valid. Enter with any guest name to continue. A new guest pass starts fresh.</div>}
      <div className={`entry-connection ${ready ? 'ready' : ''}`} role="status"><span aria-hidden="true"/>{ready ? 'Ready to play' : 'Waking the SQL server…'}{!ready && !busy && <button type="button" onClick={()=>setConnectionRetry(n=>n+1)}>Retry connection</button>}</div>
      <form onSubmit={submit}><label htmlFor="guest">Player name</label><input id="guest" aria-label="Guest name" placeholder="e.g. query_master" value={name} onChange={e => setName(e.target.value)} minLength={2} maxLength={20} pattern="[a-zA-Z0-9_-]+" autoFocus required autoComplete="nickname"/><small>2–20 letters, numbers, underscores or hyphens.</small><label htmlFor="skill">SQL level · {skillLevel}/10</label><input id="skill" aria-label="SQL skill level" type="range" min="1" max="10" step="1" value={skillLevel} onChange={e => setSkillLevel(Number(e.target.value))}/><div className="skill-scale"><span>1 · New to SQL</span><strong>{skillLevel <= 3 ? 'Foundations' : skillLevel <= 7 ? 'Confident' : 'Advanced'}</strong><span>10 · Expert</span></div><small>Both players’ levels set the duel difficulty. You get identical questions.</small>{error && <div className="arena-error" role="alert">{error}</div>}<button className="arena-primary" type="submit" disabled={busy}>{busy ? waited >= 8 ? 'Waiting for the server…' : 'Entering…' : 'Enter'} <span aria-hidden="true">↗</span></button>{busy && waited >= 8 && <p className="entry-wait" role="status">The free server is waking up. Shared names are allowed. Keep this tab open; entry will continue automatically. {waited}s elapsed.</p>}</form>
      <div className="entry-session-note"><strong>A guest pass, not an account.</strong><p>Your pass lasts 24 hours. Names can be shared; every guest gets a separate pass. A new pass starts fresh; past matches stay recorded.</p></div>
      <Link className="arena-text-link" to="/help">New here? Read the guide ↗</Link>
    </section>
  </div>;
}
