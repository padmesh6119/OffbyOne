import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
import { subscribeRoom } from '../lib/ws';
import ResultTable from '../components/SqlResultTable';
import ArenaVisual from '../components/ArenaVisual';
import PracticeGuide from '../components/PracticeGuide';
import ChallengeLibrary from '../components/ChallengeLibrary';

const SqlEditor = lazy(() => import('../components/SqlEditor'));
const fmt = s => `${Math.floor(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}`;
const human = s => s?.replaceAll('_', ' ');
const starter = '-- Make every row count.\nSELECT\n  \nFROM ';
function Glyph({ type = 'bolt' }) {
  return <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">{type === 'db' ? <><ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 4 16 4 16 0V5M4 12c0 4 16 4 16 0"/></> : type === 'arrow' ? <path d="M5 12h14m-6-6 6 6-6 6"/> : <path d="m13 2-9 12h7l-1 8 10-13h-8z"/>}</svg>;
}
function Status({ result }) {
  if (!result) return <div className="arena-empty"><span>⌘</span><div><strong>Your next breakthrough starts here.</strong><p>Run the sample to inspect your output. Submit to test every dataset.</p></div></div>;
  const passed = ['accepted', 'sample_passed'].includes(result.status);
  return <div className={`arena-verdict ${passed ? 'pass' : 'fail'}`} role="status"><div className="verdict-heading"><span>{passed ? '✓' : '!'}</span><div><strong>{human(result.status)}</strong><p>{result.message}</p></div></div>{result.actualColumns && <ResultTable columns={result.actualColumns} rows={result.actualRows}/>} {result.expectedColumns && <details><summary>Compare with expected sample</summary><ResultTable columns={result.expectedColumns} rows={result.expectedRows}/></details>}</div>;
}

export default function SqlArena() {
  const [params, setParams] = useSearchParams();
  const routeLocation = useLocation();
  const username = localStorage.getItem('username');
  const savedKey = `obo:match:${username}`;
  const bookmarkKey = `obo:bookmarks:${username}`;
  const [bookmarks, setBookmarks] = useState(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(bookmarkKey) || '[]');
      return new Set(Array.isArray(saved) ? saved.filter(s => typeof s === 'string') : []);
    } catch { return new Set(); }
  });
  const [practiceStatus, setPracticeStatus] = useState('all');
  function toggleBookmark(slug) {
    const next = new Set(bookmarks);
    if (next.has(slug)) next.delete(slug); else next.add(slug);
    try { localStorage.setItem(bookmarkKey, JSON.stringify([...next])); setBookmarks(next); }
    catch { setError('Your browser could not save the bookmark. Check its storage settings.'); }
  }
  const [roomId, setRoomId] = useState(() => localStorage.getItem(savedKey));
  const [match, setMatch] = useState(null);
  const [bank, setBank] = useState([]);
  const [practice, setPractice] = useState(null);
  const [code, setCode] = useState(params.get('code') || '');
  const [query, setQuery] = useState(starter);
  const [result, setResult] = useState(null);
  const [practiceReview, setPracticeReview] = useState(null);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);
  const [skillLevel, setSkillLevel] = useState(Number(localStorage.getItem('skillLevel') || 5));
  const [filter, setFilter] = useState('all');
  const [focusEditor, setFocusEditor] = useState(false);
  const [search, setSearch] = useState('');
  const [solved, setSolved] = useState(new Set());
  const [clock, setClock] = useState(Date.now());
  const [offset, setOffset] = useState(0);
  const generation = useRef(0);
  const runRef = useRef(null);
  const selectedRef = useRef(0);
  const problem = roomId ? match?.problem : practice;
  const draftKey = problem ? `obo:draft:${username}:${roomId || 'practice'}:${problem.slug}` : null;

  useEffect(() => { if (!roomId) setPractice(null); }, [routeLocation.key]);

  useEffect(() => {
    let live = true;
    Promise.all([api.sqlProblems(), api.mySubmissions()]).then(([problems, attempts]) => {
      if (!live) return; setBank(problems); setSolved(new Set(attempts.filter(s => s.language === 'sql' && s.verdict === 'accepted').map(s => s.problemSlug)));
    }).catch(e => { if (live) setError(errorMessage(e)); });
    return () => { live = false; };
  }, []);
  const updateMatch = useCallback(m => {
    setMatch(m); setOffset(Date.parse(m.serverTime) - Date.now());
  }, []);
  const inFlightRef = useRef(false);
  const queuedRefreshRef = useRef(false);

  const refreshMatch = useCallback(async () => {
    if (!roomId) return;
    if (inFlightRef.current) {
      queuedRefreshRef.current = true;
      return;
    }
    inFlightRef.current = true;
    queuedRefreshRef.current = false;
    const version = generation.current;
    try {
      const m = await api.arenaState(roomId);
      if (version === generation.current) updateMatch(m);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      inFlightRef.current = false;
      if (queuedRefreshRef.current) {
        queuedRefreshRef.current = false;
        refreshMatch();
      }
    }
  }, [roomId, updateMatch]);

  useEffect(() => {
    if (!roomId) return;
    const unsubscribe = subscribeRoom(roomId, {
      onLobby: () => { refreshMatch(); },
      onSubmission: () => { refreshMatch(); },
      onFinished: () => { refreshMatch(); },
    });
    return () => { unsubscribe(); };
  }, [roomId, refreshMatch]);

  useEffect(() => {
    if (!roomId) return;
    let live = true, timer;
    const poll = async () => {
      if (!live) return;
      await refreshMatch();
      if (live) timer = setTimeout(poll, 2500);
    };
    poll();
    return () => { live = false; clearTimeout(timer); };
  }, [roomId, refreshMatch]);
  useEffect(() => { const t = setInterval(() => setClock(Date.now()), 250); return () => clearInterval(t); }, []);
  useEffect(() => {
    if (draftKey) setQuery(localStorage.getItem(draftKey) ?? starter);
    setResult(null);
    setPracticeReview(null);
  }, [draftKey]);
  function edit(value) { const text = value ?? ''; setQuery(text); if (draftKey) localStorage.setItem(draftKey, text); }
  function enter(m) { generation.current++; selectedRef.current++; setPractice(null); setRoomId(m.id); localStorage.setItem(savedKey, m.id); updateMatch(m); setParams({}); }
  async function action(label, fn) {
    if (busy) return;
    setBusy(label); setError(''); generation.current++;
    try { await fn(); } catch (e) { setError(errorMessage(e)); } finally { generation.current++; setBusy(''); }
  }
  function home() { selectedRef.current++; generation.current++; setRoomId(null); setMatch(null); setPractice(null); setResult(null); localStorage.removeItem(savedKey); }
  async function openPractice(p) {
    const version = ++selectedRef.current;
    await action('Opening challenge', async () => { const detail = await api.sqlProblem(p.slug); if (version === selectedRef.current) { setPractice(detail); setResult(null); } });
  }
  async function execute(submit) {
    if (!problem || !query.trim()) return;
    await action(submit ? 'Judging' : 'Running', async () => {
      if (!submit) setResult(await api.sqlRun(problem.slug, query));
      else if (roomId) { const response = await api.arenaAction(roomId, 'submit', { round: match.round, query }); setResult(response.result); updateMatch(response.match); }
      else {
        const r = await api.sqlSubmit(problem.slug, query);
        setResult({ status: r.verdict, message: r.message, actualColumns: r.actual?.columns, actualRows: r.actual?.rows, expectedColumns: r.expected?.columns, expectedRows: r.expected?.rows });
        if (r.verdict === 'accepted') {
          setSolved(previous => new Set([...previous, problem.slug]));
          setPracticeReview(r.review || null);
        }
      }
    });
  }
  runRef.current = (submit = false) => execute(submit);
  async function copyInvite() {
    try { await navigator.clipboard.writeText(`${location.origin}${location.pathname}#/sql?code=${match.code}`); setCopied(true); setTimeout(() => setCopied(false), 2500); }
    catch { setError('Copy the invite code below and send it to your opponent.'); }
  }
  const seconds = match?.deadline ? Math.max(0, Math.ceil((Date.parse(match.deadline) - clock - offset) / 1000)) : 0;
  const tickDeadlineRef = useRef(null);
  useEffect(() => {
    if (['active', 'intermission'].includes(match?.status) && match?.deadline && seconds === 0) {
      if (tickDeadlineRef.current !== match.deadline) {
        tickDeadlineRef.current = match.deadline;
        refreshMatch();
      }
    }
  }, [match?.status, match?.deadline, seconds, refreshMatch]);
  const active = match?.status === 'active';
  const finished = match?.status === 'finished';
  const me = match?.players.find(p => p.username === username);
  const opponent = match?.players.find(p => p.username !== username);
  const nextUnsolved = bank.find(p => !solved.has(p.slug) && p.slug !== problem?.slug);

  return <>
      {error && <div className="arena-error" role="alert"><span>{error}</span><button onClick={() => setError('')} aria-label="Dismiss error">×</button></div>}
      {roomId && !match && <section className="arena-loading"><span className="judge-spinner"/> Reconnecting to your match…<button className="arena-secondary" onClick={home}>Back to arena</button></section>}
      {!roomId && !practice && <>
        <section className="arena-hero"><div className="hero-copy"><div className="arena-eyebrow"><span/> A SMALL EDGE. A GREAT RIVALRY.</div><h1>Good query.<br/><em>Great game.</em></h1><p>A battle of logic, not luck. Challenge a friend to a SQL duel, or find your edge in the training ground.</p><div className="hero-buttons"><button className="arena-primary" disabled={!!busy} onClick={() => action('Creating match', async () => enter(await api.arenaCreate()))}><Glyph/> {busy === 'Creating match' ? 'Deploying…' : 'Deploy a duel'} <Glyph type="arrow"/></button><a className="arena-text-link" href="#challenges" onClick={e => { e.preventDefault(); document.getElementById('challenges')?.scrollIntoView({ behavior: 'smooth' }); }}>Enter training ↗</a></div><div className="hero-facts"><span><b>01</b> opponent</span><span><b>03</b> rounds max</span><span><b>05</b> minutes / round</span></div></div>
          <ArenaVisual/>
        </section>
        <section className="arena-invite"><div className="invite-icon"><Glyph/></div><div><strong>Your rival is waiting.</strong><p>Enter your opponent’s code to enter their arena.</p></div><form onSubmit={e => { e.preventDefault(); action('Joining match', async () => enter(await api.arenaJoin(code.trim()))); }}><input aria-label="Invite code" placeholder="INVITE CODE" value={code} maxLength={40} onChange={e => setCode(e.target.value.toUpperCase())}/><button className="arena-secondary" disabled={!!busy || !code.trim()}>Join duel <span>↗</span></button></form></section>
        <ChallengeLibrary bank={bank} solved={solved} bookmarks={bookmarks} filter={filter} setFilter={setFilter} search={search} setSearch={setSearch} status={practiceStatus} setStatus={setPracticeStatus} busy={!!busy} onOpen={openPractice} onBookmark={toggleBookmark}/>
      </>}
      {match?.status === 'waiting' && <section className="arena-lobby"><div className="arena-eyebrow">PRIVATE MATCH / BEST OF THREE</div><h1>A rivalry starts<br/>with an <em>invite.</em></h1><p>Two players. Three SQL challenges. Five minutes per round.</p><div className="match-skill"><label htmlFor="lobby-skill">YOUR SQL LEVEL · {skillLevel}/10</label><input id="lobby-skill" aria-label="Duel skill level" type="range" min="1" max="10" value={skillLevel} onChange={e => setSkillLevel(Number(e.target.value))}/><button className="arena-secondary" disabled={!!busy || me?.skillLevel === skillLevel} onClick={() => action('Setting skill', async () => { updateMatch(await api.arenaAction(roomId, 'skill', {level: skillLevel})); localStorage.setItem('skillLevel', skillLevel); })}>Save level</button><strong>DUEL LEVEL {match.skillLevel}/10 · {match.skillLevel <= 3 ? 'FOUNDATIONS' : match.skillLevel <= 7 ? 'INTERMEDIATE' : 'ADVANCED'}</strong><p>{match.players.map(p => `${p.username}: ${p.skillLevel}/10`).join(' · ')}</p><small>Rounded average of both players. Same three questions, closest available difficulty, no repeats. Levels are self-rated.</small></div><div className="lobby-duo"><Player name={username} score={0}/><span>VS</span><Player name={opponent?.username} score={0}/></div><button className="invite-code" onClick={copyInvite}><small>{copied ? 'INVITE LINK COPIED' : 'CLICK TO COPY INVITE LINK'}</small><strong>{match.code.length > 12 ? 'REMATCH INVITE ↗' : match.code}</strong></button><div className="lobby-actions">{match.host === username ? <button className="arena-primary" disabled={!!busy || !opponent} onClick={() => action('Starting', async () => updateMatch(await api.arenaAction(roomId, 'start')))}>{opponent ? 'Start the duel →' : 'Waiting for your challenger…'}</button> : <p className="arena-eyebrow">Waiting for {match.host} to start…</p>}<button className="arena-text-link" disabled={!!busy} onClick={() => action('Leaving', async () => { await api.arenaAction(roomId, 'leave'); home(); })}>Leave lobby</button></div></section>}
      {(practice || (match && ['active', 'intermission'].includes(match.status))) && problem && <>
        <div className="workspace-heading"><div><div className="arena-eyebrow">{roomId ? `ROUND ${match.round} OF 3 / FIRST TO TWO` : 'TRAINING GROUND / SOLO PRACTICE'}</div><h2>{roomId ? 'Every query counts.' : 'Find your flow.'}</h2></div>{roomId ? <div className={`arena-clock ${seconds < 30 ? 'urgent' : ''}`}><span>{active ? 'ROUND ENDS IN' : 'NEXT ROUND IN'}</span><strong>{fmt(seconds)}</strong></div> : <div className="practice-actions"><button className="arena-secondary" aria-pressed={bookmarks.has(problem.slug)} onClick={() => toggleBookmark(problem.slug)}>{bookmarks.has(problem.slug) ? '★ Bookmarked' : '☆ Bookmark challenge'}</button><button className="arena-secondary" disabled={!!busy} onClick={() => { selectedRef.current++; setPractice(null); }}>← All challenges</button></div>}</div>
        {roomId && <div className="arena-scoreboard"><Player name={username} score={me?.score || 0}/><div className="round-pips">{[1, 2, 3].map(n => <span key={n} className={n === match.round ? 'current' : n < match.round ? 'done' : ''}>{n}</span>)}</div><Player name={opponent?.username} score={opponent?.score || 0}/></div>}
        {match?.status === 'intermission' && <div className="round-banner" role="status"><strong>{match.history.find(h => h.round === match.round)?.winner ? `${match.history.find(h => h.round === match.round).winner} takes the round.` : 'Time is up. This round is a draw.'}</strong><span>Next challenge in {seconds}s</span></div>}
        <section className={`arena-workspace ${focusEditor ? 'focus-editor' : ''}`}><div className="challenge-panel"><div className="panel-title"><span><Glyph type="db"/> THE CHALLENGE</span><span className={`arena-difficulty ${problem.difficulty}`}>{problem.difficulty}</span></div><div className="challenge-description"><span className="arena-eyebrow">{problem.pattern?.replaceAll('+', ' + ')}</span><h2>{problem.title}</h2><p>{problem.task}</p><div className="query-rules">Return columns in the requested order. Column aliases are flexible.</div></div><div className="schema-heading">EXPLORE THE DATA <span>PUBLIC SAMPLE</span></div>{problem.tables?.map(t => <details className="schema-card" key={t.name} open><summary><Glyph type="db"/> {t.name}<span>{t.columns.length} columns</span></summary><ResultTable columns={t.columns} rows={t.rows}/></details>)}<details className="schema-ddl"><summary>View table definitions</summary><pre>{problem.schema.join(';\n\n')};</pre></details></div>
        <div className="query-panel"><div className="panel-title"><span><span className="file-dot"/> solution.sql</span><button className="editor-focus" onClick={() => setFocusEditor(!focusEditor)}>{focusEditor ? '↙ Show schema' : '↗ Focus editor'}</button><span className="editor-language">SQLite <i/></span></div><div className="editor-wrap"><Suspense fallback={<div className="arena-loading">Loading editor…</div>}><SqlEditor value={query} onChange={edit} onRun={submit => runRef.current?.(submit)} readOnly={!!busy || (!!roomId && !active)}/></Suspense></div><div className="editor-actions"><span>Ctrl + Enter · Run / + Shift · Submit</span><button className="arena-secondary" disabled={!!busy || !query.trim() || (roomId && !active)} onClick={() => execute(false)}>{busy === 'Running' ? 'Running…' : '▷ Run sample'}</button><button className="arena-primary" disabled={!!busy || !query.trim() || (roomId && !active)} onClick={() => execute(true)}>{busy === 'Judging' ? 'Judging…' : 'Submit query →'}</button></div><div className="output-panel"><div className="panel-title"><span>QUERY OUTPUT</span><span>{busy === 'Judging' ? 'CHECKING DATASETS' : 'RESULTS'}</span></div><Status result={result}/></div>{roomId && match.myAttempts.length > 0 && <div className="attempt-strip">YOUR RECENT SUBMISSIONS {match.myAttempts.map((a, i) => <span key={i} className={a.verdict === 'accepted' ? 'ok' : ''}>{human(a.verdict)}</span>)}</div>}</div></section>
        {!roomId && <PracticeGuide key={problem.slug} problem={problem} review={practiceReview} busy={!!busy} hasNext={!!nextUnsolved} onNext={() => { if (nextUnsolved) { openPractice(nextUnsolved); window.scrollTo({top: 0, behavior: 'smooth'}); } }}/>}
        {roomId && <button className="arena-text-link forfeit" disabled={!!busy} onClick={() => { if (window.confirm('Forfeit this match? Your opponent will win.')) action('Leaving', async () => updateMatch(await api.arenaAction(roomId, 'leave'))); }}>Forfeit match</button>}
      </>}
      {finished && <section className="arena-results"><div className="arena-eyebrow">MATCH COMPLETE / SQL ARENA</div><div className="result-emblem">{match.winner === username ? '★' : match.winner ? '↗' : '='}</div><h1>{match.winner === username ? 'Well queried.' : match.winner ? 'The next one is yours.' : 'Evenly matched.'}</h1><p>{match.winner ? `${match.winner} wins this duel.` : 'This match ends in a draw.'} There’s always another way to improve.</p><div className="result-score"><Player name={username} score={me?.score || 0}/><span>:</span><Player name={opponent?.username} score={opponent?.score || 0}/></div><div className="results-actions"><button className="arena-primary" disabled={!!busy} onClick={() => action('Rematching', async () => enter(await api.arenaAction(roomId, 'rematch')))}>Rematch ↗</button><button className="arena-secondary" onClick={home}>Back to arena</button></div><p className="rematch-note">Both players can press Rematch to meet in the same new lobby.</p><div className="match-review"><div className="section-heading"><h2>Leave with more than a score.</h2><span>THE MATCH REVIEW</span></div>{match.history.map(h => <details className="review-round" key={h.round}><summary><span>0{h.round}</span><strong>{h.title}</strong><small>{h.winner ? `${h.winner} won` : 'No round winner'} ↓</small></summary><p>{h.explanation}</p><pre>{h.solution}</pre></details>)}</div></section>}
  </>;
}
function Player({ name, score }) { return <div className={`arena-player ${!name ? 'waiting' : ''}`}><div className="player-monogram">{name?.[0]?.toUpperCase() || '?'}</div><div><strong>{name || 'Waiting for opponent'}</strong><div className="score-dots">{[0, 1].map(n => <i key={n} className={n < score ? 'won' : ''}/>)}</div></div><b>{score}</b></div>; }
