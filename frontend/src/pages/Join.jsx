import { useState } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
export default function Join() {
  const [name, setName] = useState(''), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const navigate = useNavigate(), location = useLocation();
  const params = new URLSearchParams(location.search);
  const requested = location.state?.from || params.get('next') || '/';
  const destination = requested.startsWith('/') && !requested.startsWith('//') && !requested.startsWith('/join') ? requested : '/';
  if (localStorage.getItem('token')) return <Navigate to={destination} replace />;
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const session = await api.join(name.trim());
      localStorage.setItem('token', session.token); localStorage.setItem('username', session.username);
      navigate(destination, { replace: true });
    } catch (e) { setError(errorMessage(e)); setBusy(false); }
  }
  return <div className="sql-app entry-page">
    <div className="entry-story"><div className="workspace-brand">offby<span>one.</span><small>JAVA + SQL / THE COMPETITIVE WORKSPACE</small></div>
      <div className="entry-pitch"><div className="arena-eyebrow"><span/> ONE SMALL EDGE. A BETTER YOU.</div><h1>Your next<br/>breakthrough<br/>starts <em>here.</em></h1><p>Think in code. Think in sets. Turn practice into progress, and friends into your favourite rivals.</p><div className="entry-code" aria-hidden="true"><span>01</span> SELECT determination<br/><span>02</span> FROM every_attempt<br/><span>03</span> WHERE giving_up = false;</div></div>
      <div className="hero-facts"><span>JAVA PRACTICE</span><span>SQL DUELS</span><span>REAL PROGRESS</span></div>
    </div>
    <section className="entry-form-panel"><div className="arena-eyebrow">TAKE YOUR PLACE</div><h2>Make a name<br/>for yourself.</h2><p>Pick your guest name and enter the workspace.</p>
      {params.has('expired') && <div className="arena-error" role="status">Your saved session is no longer valid. Enter with an available guest name to continue. Your editor drafts are still saved in this browser.</div>}
      <form onSubmit={submit}><label htmlFor="guest">YOUR PLAYER NAME</label><input id="guest" aria-label="Guest name" placeholder="e.g. query_master" value={name} onChange={e => setName(e.target.value)} minLength={2} maxLength={20} pattern="[a-zA-Z0-9_-]+" autoFocus required autoComplete="nickname"/><small>2–20 letters, numbers, underscores or hyphens.</small>{error && <div className="arena-error" role="alert">{error}</div>}<button className="arena-primary" type="submit" disabled={busy}>{busy ? 'Entering…' : 'Enter'} <span aria-hidden="true">↗</span></button></form>
      <div className="entry-session-note"><strong>Your browser is your player card.</strong><p>Your guest session and progress stay here. Use a unique name and keep your browser data to return to it.</p></div>
    </section>
  </div>;
}
