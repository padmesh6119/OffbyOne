import { useState, useEffect, useRef, useCallback } from 'react';
import Editor from '@monaco-editor/react';
import { motion } from 'framer-motion';
import { api, errorMessage } from '../lib/api';
import { connect, subscribe, disconnect } from '../lib/ws';
import { STARTERS } from '../lib/starters';

export default function Room() {
  const [roomId, setRoomId] = useState(null);
  const [state, setState] = useState(null);
  const [finalStandings, setFinalStandings] = useState(null);
  const [error, setError] = useState('');

  const [homeMode, setHomeMode] = useState(null); // null | 'duel' | 'tournament'
  const [homeLanguage, setHomeLanguage] = useState(null); // null | 'java' | 'sql'
  const [roomName, setRoomName] = useState('');
  const [joinCode, setJoinCode] = useState('');
  const [submittingHome, setSubmittingHome] = useState(false);

  const [selectedSlug, setSelectedSlug] = useState(null);
  const [lang, setLang] = useState('java');
  const [codeBySlug, setCodeBySlug] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [verdict, setVerdict] = useState(null);
  const [problemDetail, setProblemDetail] = useState(null);
  const [secondsLeft, setSecondsLeft] = useState(0);

  const username = localStorage.getItem('username');
  const subsRef = useRef([]);

  const refresh = useCallback((id) => {
    api.roomState(id).then((s) => { setState(s); setError(''); }).catch((err) => setError(errorMessage(err)));
  }, []);

  useEffect(() => {
    if (!roomId) return;
    refresh(roomId);
    connect(() => {
      subsRef.current.push(subscribe(`/topic/room/${roomId}/lobby`, () => refresh(roomId)));
      subsRef.current.push(subscribe(`/topic/room/${roomId}/submission`, () => refresh(roomId)));
      subsRef.current.push(subscribe(`/topic/room/${roomId}/finished`, (data) => {
        setFinalStandings(data.standings);
        refresh(roomId);
      }));
    });
    const poll = setInterval(() => refresh(roomId), 8000);
    return () => {
      subsRef.current.forEach((s) => s?.unsubscribe?.());
      subsRef.current = [];
      clearInterval(poll);
      disconnect();
    };
  }, [roomId, refresh]);

  // 1-second countdown tick
  useEffect(() => {
    if (!state?.room?.endTime || state.room.status !== 'active') return;
    const end = new Date(state.room.endTime).getTime();
    const tick = () => setSecondsLeft(Math.max(0, Math.floor((end - Date.now()) / 1000)));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [state?.room?.endTime, state?.room?.status]);

  // fetch problem statement when active tab changes
  const activeSlug = selectedSlug || state?.problems?.[0]?.slug || null;
  useEffect(() => {
    if (!activeSlug) return;
    setProblemDetail(null);
    api.problem(activeSlug).then(setProblemDetail).catch(() => {});
  }, [activeSlug]);

  async function createRoom() {
    setError('');
    setSubmittingHome(true);
    try {
      const body = { name: roomName };
      if (homeMode === 'duel') body.mode = 'duel';
      const r = await api.createRoom(body);
      setRoomId(r.id);
    } catch (err) { setError(errorMessage(err)); }
    finally { setSubmittingHome(false); }
  }

  async function joinRoom() {
    setError('');
    setSubmittingHome(true);
    try {
      const r = await api.joinRoom(joinCode.trim());
      setRoomId(r.roomId);
    } catch (err) { setError(errorMessage(err)); }
    finally { setSubmittingHome(false); }
  }

  async function startDuel() {
    setError('');
    try {
      await api.startRoom(roomId);
      refresh(roomId);
    } catch (err) { setError(errorMessage(err)); }
  }

  async function submit() {
    if (!activeSlug) return;
    setSubmitting(true);
    setVerdict(null);
    try {
      const code = codeBySlug[activeSlug] ?? STARTERS[lang];
      const { submissionId } = await api.submit({ slug: activeSlug, roomId, language: lang, code });
      const sub = subscribe(`/topic/submission/${submissionId}`, (data) => {
        setVerdict(data);
        setSubmitting(false);
        sub.unsubscribe();
        refresh(roomId);
      });
    } catch (err) {
      setError(errorMessage(err));
      setSubmitting(false);
    }
  }

  async function leaveToHome() {
    if (roomId) { try { await api.leaveRoom(roomId); } catch { /* leaving regardless */ } }
    setRoomId(null); setState(null); setFinalStandings(null);
    setSelectedSlug(null); setVerdict(null); setCodeBySlug({});
    setProblemDetail(null); setError(''); setHomeMode(null); setHomeLanguage(null);
    setRoomName(''); setJoinCode('');
  }

  if (!roomId) {
    if (!homeMode) {
      return (
        <motion.div className="page" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.4 }}>
          <h2>Play</h2>
          <div className="mode-cards">
            <button className="mode-card mode-card-duel" onClick={() => setHomeMode('duel')}>
              <span className="mode-icon">⚔️</span>
              <span className="mode-title">1v1 Duel</span>
              <span className="mode-desc">Same problem. Same time. First to solve wins the round. Play as long as you want.</span>
            </button>
            <button className="mode-card mode-card-tournament" onClick={() => setHomeMode('tournament')}>
              <span className="mode-icon">🏆</span>
              <span className="mode-title">Tournament</span>
              <span className="mode-desc">2–6 players. Fixed problem set. Race to the top of the leaderboard.</span>
            </button>
          </div>
        </motion.div>
      );
    }

    if (!homeLanguage) {
      return (
        <motion.div className="page" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.3 }}>
          <button onClick={() => setHomeMode(null)} className="link-btn">← Back</button>
          <h2>{homeMode === 'duel' ? '1v1 Duel' : 'Tournament'} — choose language</h2>
          <div className="language-toggle">
            <button className="language-btn" onClick={() => setHomeLanguage('java')}>Java</button>
            <button className="language-btn" disabled title="SQL duels aren't wired into rooms yet — coming soon">
              SQL <span className="soon-badge">soon</span>
            </button>
          </div>
        </motion.div>
      );
    }

    return (
      <motion.div className="page" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.3 }}>
        <button onClick={() => setHomeLanguage(null)} className="link-btn">← Back</button>
        <h2>{homeMode === 'duel' ? '1v1 Duel' : 'Tournament'} — Java</h2>
        <p className="hint">
          {homeMode === 'duel'
            ? 'Exactly 2 players. Live leaderboard.'
            : '2+ players, 5 problems, live leaderboard. Never solo.'}
        </p>
        <div className="room-actions">
          <div>
            <input placeholder={homeMode === 'duel' ? 'Duel name' : 'Room name'} value={roomName} onChange={(e) => setRoomName(e.target.value)} />
            <button onClick={createRoom} disabled={!roomName.trim() || submittingHome}>
              {submittingHome ? 'Creating...' : `Create ${homeMode === 'duel' ? 'Duel' : 'Room'}`}
            </button>
          </div>
          <div>
            <input placeholder="6-char code" value={joinCode} onChange={(e) => setJoinCode(e.target.value.toUpperCase())} />
            <button onClick={joinRoom} disabled={!joinCode.trim() || submittingHome}>
              {submittingHome ? 'Joining...' : 'Join'}
            </button>
          </div>
        </div>
        {submittingHome && <p className="hint">If the backend was idle, this can take up to a minute to wake up.</p>}
        {error && <p className="error">{error}</p>}
      </motion.div>
    );
  }

  if (!state) return (
    <div className="page">
      <p>Loading...</p>
      {error && (
        <>
          <p className="error">{error}</p>
          <p className="hint">If the backend was idle, it can take up to a minute to wake up on the free tier.</p>
          <button onClick={() => refresh(roomId)} className="start-btn">Retry</button>
          <button onClick={leaveToHome} className="link-btn">Back</button>
        </>
      )}
    </div>
  );

  const isHost = state.room.hostUsername === username;
  const playerCount = state.leaderboard.length;

  if (state.room.status === 'waiting') {
    const isDuel = state.room.isDuel;
    const canStart = isDuel ? playerCount === 2 : playerCount >= 2;
    const startReason = isDuel
      ? (playerCount < 2 ? 'Waiting for opponent to join...' : playerCount > 2 ? 'A duel is 1v1 only.' : '')
      : (playerCount < 2 ? 'Need at least 2 players to start.' : '');

    return (
      <motion.div className="page lobby" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.35 }}>
        <span className={`mode-badge ${isDuel ? 'mode-badge-duel' : 'mode-badge-tournament'}`}>
          {isDuel ? '⚔️ 1v1 Duel' : '🏆 Tournament'}
        </span>
        <h2>{state.room.name}</h2>
        <div className="lobby-code-card">
          <span className="hint">Share this code</span>
          <span className="lobby-code">{state.room.joinCode}</span>
        </div>
        <h3>Players ({playerCount}{isDuel ? '/2' : ''})</h3>
        <div className="player-list">
          {state.leaderboard.map((row) => (
            <motion.div key={row.username} className="player-row"
              initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }}>
              <span className="player-avatar">{row.username[0]?.toUpperCase()}</span>
              <span>{row.username}</span>
              {row.username === state.room.hostUsername && <span className="host-tag">host</span>}
            </motion.div>
          ))}
          {isDuel && playerCount < 2 && (
            <div className="player-row player-row-empty">
              <span className="player-avatar player-avatar-empty">?</span>
              <span className="hint">waiting for opponent...</span>
            </div>
          )}
        </div>
        {isHost ? (
          <>
            <button onClick={startDuel} disabled={!canStart} className="start-btn">
              Start {isDuel ? 'Duel' : 'Tournament'}
            </button>
            {startReason && <p className="hint">{startReason}</p>}
          </>
        ) : (
          <p className="hint">Waiting for host to start...</p>
        )}
        {error && <p className="error">{error}</p>}
        <button onClick={leaveToHome} className="link-btn">Leave</button>
      </motion.div>
    );
  }

  if (state.room.status === 'active') {
    const problem = state.problems.find((p) => p.slug === activeSlug) || state.problems[0];
    const mySolved = new Set(state.me?.solved || []);
    const mins = Math.floor(secondsLeft / 60);
    const secs = String(secondsLeft % 60).padStart(2, '0');

    return (
      <div className="duel-layout">
        <div className="duel-main">
          <div className="duel-tabs">
            {state.problems.map((p) => (
              <button key={p.slug}
                className={`duel-tab ${p.slug === activeSlug ? 'active' : ''} ${mySolved.has(p.slug) ? 'solved' : ''}`}
                onClick={() => { setSelectedSlug(p.slug); setVerdict(null); }}>
                {mySolved.has(p.slug) ? '✓ ' : ''}{p.title}
                <span className={`diff-${p.difficulty}`}> ({p.difficulty})</span>
              </button>
            ))}
          </div>
          {problem && (
            <>
              <div className="statement-panel">
                <h3>{problem.title}</h3>
                {problemDetail
                  ? <div className="statement" style={{whiteSpace:'pre-wrap'}}>{problemDetail.statement}</div>
                  : <div className="hint">Loading...</div>
                }
              </div>
              <div style={{display:'flex', gap:'0.5rem', alignItems:'center'}}>
                <select value={lang} onChange={(e) => setLang(e.target.value)}>
                  <option value="java">Java</option>
                  <option value="python">Python</option>
                  <option value="cpp">C++</option>
                </select>
                <button onClick={submit} disabled={submitting} style={{flex:1}}>
                  {submitting ? 'Judging...' : 'Submit'}
                </button>
              </div>
              <Editor
                height="42vh"
                language={lang === 'cpp' ? 'cpp' : lang}
                value={codeBySlug[activeSlug] ?? STARTERS[lang]}
                onChange={(v) => setCodeBySlug((prev) => ({ ...prev, [activeSlug]: v }))}
                theme="vs-dark"
              />
              {verdict && (
                <div className={`verdict verdict-${verdict.verdict}`}>
                  {verdict.verdict.toUpperCase()}{verdict.runtimeMs ? ` — ${verdict.runtimeMs}ms` : ''}
                  {verdict.message ? <div style={{fontSize:'0.8rem',marginTop:'0.25rem',fontWeight:'normal'}}>{verdict.message}</div> : null}
                </div>
              )}
              {error && <p className="error">{error}</p>}
            </>
          )}
        </div>
        <div className="duel-sidebar">
          <div className="countdown">{mins}:{secs}</div>
          <h3>Leaderboard</h3>
          <table>
            <thead><tr><th>#</th><th>Player</th><th>Score</th></tr></thead>
            <tbody>
              {state.leaderboard.map((row) => (
                <tr key={row.username} className={row.username === username ? 'me' : ''}>
                  <td>{row.rank}</td><td>{row.username}</td><td>{row.score}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  // finished
  const standings = finalStandings || state.leaderboard;
  return (
    <div className="page">
      <h2>{state.room.name} — Final Standings</h2>
      <table>
        <thead><tr><th>Rank</th><th>Player</th><th>Score</th></tr></thead>
        <tbody>
          {standings.map((row) => (
            <tr key={row.username} className={row.rank === 1 ? 'winner' : ''}>
              <td>{row.rank === 1 ? '🏆' : row.rank}</td><td>{row.username}</td><td>{row.score}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <button onClick={leaveToHome} className="start-btn">New Duel</button>
    </div>
  );
}
