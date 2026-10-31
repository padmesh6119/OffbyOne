import ArenaVisual from '../components/ArenaVisual';
import { ThemeToggle } from '../theme';
import { useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
export default function Join() {
  const [name, setName] = useState(''), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const [skillLevel, setSkillLevel] = useState(1);
  const navigate = useNavigate(), location = useLocation();
  const params = new URLSearchParams(location.search);
  const requested = location.state?.from || params.get('next') || '/';
  const destination = requested.startsWith('/') && !requested.startsWith('//') && !requested.startsWith('/join') ? requested : '/';
  if (localStorage.getItem('token')) return <Navigate to={destination} replace />;
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const session = await api.join(name.trim(), skillLevel);
      Object.keys(localStorage).filter(k => k.startsWith(`obo:draft:${session.username}:`) || k === `obo:match:${session.username}`).forEach(k => localStorage.removeItem(k));
      localStorage.setItem('token', session.token); localStorage.setItem('username', session.username); localStorage.setItem('skillLevel', session.skillLevel); localStorage.setItem('expiresAt', session.expiresAt);
      navigate(destination, { replace: true });
    } catch (e) { setError(errorMessage(e)); setBusy(false); }
  }
  return <div className="sql-app entry-page">
    <div className="entry-story"><div className="workspace-brand"><span className="brand-symbol">[ / ]</span> offbyone<span className="brand-period">.</span></div>
      <div className="entry-pitch"><div className="arena-eyebrow">SQL IS A COMPETITIVE SPORT.</div><h1>Bring your<br/>best <em>query.</em></h1><p>Two minds. One dataset.<br/>Find your edge, one duel at a time.</p></div><ArenaVisual/>
      <div className="entry-bottom"><span>01 / THINK</span><span>02 / QUERY</span><span>03 / OUTPLAY</span></div>
    </div>
    <section className="entry-form-panel"><ThemeToggle/><div className="arena-eyebrow">YOUR SEAT AT THE TABLE</div><h2>Ready to<br/>make your move?</h2><p>No account needed. Pick a temporary name and your SQL level.</p>
      {params.has('expired') && <div className="arena-error" role="status">Your saved session is no longer valid. Enter with an available guest name to continue. Your editor drafts are still saved in this browser.</div>}
      <form onSubmit={submit}><label htmlFor="guest">YOUR PLAYER NAME</label><input id="guest" aria-label="Guest name" placeholder="e.g. query_master" value={name} onChange={e => setName(e.target.value)} minLength={2} maxLength={20} pattern="[a-zA-Z0-9_-]+" autoFocus required autoComplete="nickname"/><small>2–20 letters, numbers, underscores or hyphens.</small><label htmlFor="skill">YOUR SQL LEVEL · {skillLevel}/10</label><input id="skill" aria-label="SQL skill level" type="range" min="1" max="10" step="1" value={skillLevel} onChange={e => setSkillLevel(Number(e.target.value))}/><div className="skill-scale"><span>1 · New to SQL</span><strong>{skillLevel <= 3 ? 'Foundations' : skillLevel <= 7 ? 'Confident' : 'Advanced'}</strong><span>10 · Expert</span></div><small>Both players’ levels set the duel difficulty. You get identical questions.</small>{error && <div className="arena-error" role="alert">{error}</div>}<button className="arena-primary" type="submit" disabled={busy}>{busy ? 'Entering…' : 'Enter'} <span aria-hidden="true">↗</span></button></form>
      <div className="entry-session-note"><strong>A guest pass, not an account.</strong><p>Your pass lasts 24 hours. When it expires, your name becomes available again. A new pass starts fresh; past matches stay recorded.</p></div>
    </section>
  </div>;
}
