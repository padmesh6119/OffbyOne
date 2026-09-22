import { useState, useEffect, useRef, useCallback } from 'react';
import Editor from '@monaco-editor/react';
import { api, errorMessage } from '../lib/api';
import { connect, subscribe, disconnect } from '../lib/ws';
import { STARTERS } from '../lib/starters';

export default function Room() {
  const [roomId, setRoomId] = useState(null);
  const [state, setState] = useState(null);
  const [finalStandings, setFinalStandings] = useState(null);
  const [error, setError] = useState('');

  const [roomName, setRoomName] = useState('');
  const [joinCode, setJoinCode] = useState('');

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
    api.roomState(id).then(setState).catch(() => {});
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
    try {
      const r = await api.createRoom({ name: roomName });
      setRoomId(r.id);
    } catch (err) { setError(errorMessage(err)); }
  }

  async function joinRoom() {
    setError('');
    try {
      const r = await api.joinRoom(joinCode.trim());
      setRoomId(r.roomId);
    } catch (err) { setError(errorMessage(err)); }
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

  function leaveToHome() {
    setRoomId(null); setState(null); setFinalStandings(null);
    setSelectedSlug(null); setVerdict(null); setCodeBySlug({});
    setProblemDetail(null); setError('');
  }

  if (!roomId) {
    return (
      <div className="page">
        <h2>Duels</h2>
        <p className="hint">2+ players, 5 problems, live leaderboard. Never solo.</p>
        <div className="room-actions">
          <div>
            <input placeholder="Duel name" value={roomName} onChange={(e) => setRoomName(e.target.value)} />
            <button onClick={createRoom} disabled={!roomName.trim()}>Create Duel</button>
          </div>
          <div>
            <input placeholder="6-char code" value={joinCode} onChange={(e) => setJoinCode(e.target.value.toUpperCase())} />
            <button onClick={joinRoom} disabled={!joinCode.trim()}>Join Duel</button>
          </div>
        </div>
        {error && <p className="error">{error}</p>}
      </div>
    );
  }

  if (!state) return <div className="page">Loading...</div>;

  const isHost = state.room.hostUsername === username;
  const playerCount = state.leaderboard.length;

  if (state.room.status === 'waiting') {
    return (
      <div className="page">
        <h2>{state.room.name}</h2>
        <p>Share this code: <strong className="join-code">{state.room.joinCode}</strong></p>
        <h3>Players ({playerCount})</h3>
        <table>
          <thead><tr><th>Player</th></tr></thead>
          <tbody>
            {state.leaderboard.map((row) => (
              <tr key={row.username}>
                <td>{row.username}{row.username === state.room.hostUsername ? ' (host)' : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {isHost ? (
          <>
            <button onClick={startDuel} disabled={playerCount < 2} className="start-btn">Start Duel</button>
            {playerCount < 2 && <p className="hint">Need at least 2 players to start.</p>}
          </>
        ) : (
          <p className="hint">Waiting for host to start...</p>
        )}
        {error && <p className="error">{error}</p>}
        <button onClick={leaveToHome} className="link-btn">Leave</button>
      </div>
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
