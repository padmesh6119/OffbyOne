import { useState, useEffect, useRef, useCallback } from 'react';
import Editor from '@monaco-editor/react';
import { motion, AnimatePresence } from 'framer-motion';
import { api, errorMessage } from '../lib/api';
import { connect, subscribe, disconnect } from '../lib/ws';
import { STARTERS } from '../lib/starters';
import DuelScreen from './DuelScreen';
import ResultsScreen from './ResultsScreen';
import JudgeFeedback from '../components/JudgeFeedback';
import SqlVerdict from '../components/SqlVerdict';
import SqlResultTable from '../components/SqlResultTable';

export default function Room() {
  const [roomId, setRoomId] = useState(null);
  const [state, setState] = useState(null);
  const [finalStandings, setFinalStandings] = useState(null);
  const [error, setError] = useState('');

  const [homeMode, setHomeMode] = useState(null); // null | 'duel' | 'tournament'
  const [homeLanguage, setHomeLanguage] = useState(null); // null | 'java' | 'sql'
  const [problemCount, setProblemCount] = useState(5);
  const [codeCopied, setCodeCopied] = useState(false);
  const [roomName, setRoomName] = useState('');
  const [joinCode, setJoinCode] = useState('');
  const [submittingHome, setSubmittingHome] = useState(false);

  const [selectedSlug, setSelectedSlug] = useState(null);
  const [lang, setLang] = useState('java');
  const [codeBySlug, setCodeBySlug] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [verdict, setVerdict] = useState(null);
  const [progress, setProgress] = useState(null);
  const [firstBlood, setFirstBlood] = useState(false);
  const [problemDetail, setProblemDetail] = useState(null);
  const [samples, setSamples] = useState([]);
  const [sqlProblemDetail, setSqlProblemDetail] = useState(null);
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
      subsRef.current.push(subscribe(`/topic/room/${roomId}/submission`, (data) => {
        if (data.firstBlood && data.username === username) {
          setFirstBlood(true);
          setTimeout(() => setFirstBlood(false), 3000);
        }
        refresh(roomId);
      }));
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

  // fetch problem statement + samples when active tab changes
  const activeSlug = selectedSlug || state?.problems?.[0]?.slug || null;
  const activeType = state?.problems?.find((p) => p.slug === activeSlug)?.type || 'java';
  useEffect(() => {
    if (!activeSlug) return;
    setProblemDetail(null);
    setSamples([]);
    setSqlProblemDetail(null);
    if (activeType === 'sql') {
      api.sqlProblem(activeSlug).then(setSqlProblemDetail).catch(() => {});
    } else {
      api.problem(activeSlug).then(setProblemDetail).catch(() => {});
      api.samples(activeSlug).then(setSamples).catch(() => {});
    }
  }, [activeSlug, activeType]);

  async function createRoom() {
    setError('');
    setSubmittingHome(true);
    try {
      const body = { name: roomName, track: homeLanguage };
      if (homeMode === 'duel') body.mode = 'duel';
      else body.problemCount = String(problemCount);
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
    setProgress(null);
    try {
      const isSqlTab = activeType === 'sql';
      const code = codeBySlug[activeSlug] ?? (isSqlTab ? '' : STARTERS[lang]);
      const body = isSqlTab
        ? { slug: activeSlug, roomId, language: 'sql', code }
        : { slug: activeSlug, roomId, language: lang, code };
      const { submissionId } = await api.submit(body);
      const progressSub = isSqlTab ? null : subscribe(`/topic/submission/${submissionId}/progress`, (data) => setProgress(data));
      const sub = subscribe(`/topic/submission/${submissionId}`, (data) => {
        setVerdict(data);
        setSubmitting(false);
        setProgress(null);
        sub.unsubscribe();
        progressSub?.unsubscribe?.();
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
    setProblemDetail(null); setSamples([]); setSqlProblemDetail(null); setError(''); setHomeMode(null); setHomeLanguage(null);
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
              <span className="mode-desc">Any number of players, no cap. Fixed problem set. Race to the top of the leaderboard.</span>
            </button>
          </div>
        </motion.div>
      );
    }

    if (!homeLanguage) {
      return (
        <motion.div className="page" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.3 }}>
          <button onClick={() => setHomeMode(null)} className="link-btn">← Back</button>
          <h2>{homeMode === 'duel' ? '1v1 Duel' : 'Tournament'} — choose track</h2>
          <div className="language-toggle">
            <button className="language-btn" onClick={() => setHomeLanguage('java')}>Coding</button>
            <button className="language-btn" onClick={() => setHomeLanguage('mixed')}>Coding + SQL</button>
            <button className="language-btn" onClick={() => setHomeLanguage('sql')}>SQL</button>
          </div>
        </motion.div>
      );
    }

    return (
      <motion.div className="page" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.3 }}>
        <button onClick={() => setHomeLanguage(null)} className="link-btn">← Back</button>
        <h2>{homeMode === 'duel' ? '1v1 Duel' : 'Tournament'} — {homeLanguage === 'mixed' ? 'Coding + SQL' : homeLanguage === 'sql' ? 'SQL' : 'Coding'}</h2>
        <p className="hint">
          {homeMode === 'duel'
            ? 'Exactly 2 players. Live leaderboard.'
            : `2+ players, ${problemCount} problems, live leaderboard. Never solo.`}
        </p>
        <div className="room-actions">
          <div>
            <input placeholder={homeMode === 'duel' ? 'Duel name' : 'Room name'} value={roomName} onChange={(e) => setRoomName(e.target.value)} />
            {homeMode === 'tournament' && (
              <div className="count-toggle">
                <span className="hint">Questions</span>
                <div className="count-pills">
                  {[2, 3, 4, 5, 6].map((n) => (
                    <button key={n} type="button" className={`count-pill ${problemCount === n ? 'active' : ''}`}
                      onClick={() => setProblemCount(n)}>{n}</button>
                  ))}
                </div>
              </div>
            )}
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
        <button type="button" className="lobby-code-card" onClick={() => {
          navigator.clipboard?.writeText(state.room.joinCode).then(() => {
            setCodeCopied(true);
            setTimeout(() => setCodeCopied(false), 1500);
          });
        }}>
          <span className="hint">{codeCopied ? 'Copied!' : 'Share this code — click to copy'}</span>
          <span className="lobby-code">{state.room.joinCode}</span>
        </button>
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

  if (state.room.status === 'active' && state.room.isDuel) {
    return (
      <DuelScreen
        roomId={roomId}
        room={state.room}
        leaderboard={state.leaderboard}
        me={state.me}
        username={username}
        onLeave={leaveToHome}
      />
    );
  }

  if (state.room.status === 'active') {
    const problem = state.problems.find((p) => p.slug === activeSlug) || state.problems[0];
    const mySolved = new Set(state.me?.solved || []);
    const mins = Math.floor(secondsLeft / 60);
    const secs = String(secondsLeft % 60).padStart(2, '0');
    const timeLow = secondsLeft > 0 && secondsLeft < 300;
    const medal = { 1: '🥇', 2: '🥈', 3: '🥉' };

    return (
      <motion.div className="duel-layout" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.35 }}>
        <AnimatePresence>
          {firstBlood && (
            <motion.div className="first-blood-toast" initial={{ opacity: 0, x: 40 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 40 }}>
              🩸 First Blood! +50 bonus
            </motion.div>
          )}
        </AnimatePresence>
        <div className="duel-main">
          <div className="tournament-topbar">
            <span className="tournament-room-name">{state.room.name}</span>
            <span className={`tournament-timer ${timeLow ? 'timer-low' : ''}`}>{mins}:{secs}</span>
          </div>
          <div className="duel-tabs">
            {state.problems.map((p) => (
              <button key={p.slug}
                className={`duel-tab ${p.slug === activeSlug ? 'active' : ''} ${mySolved.has(p.slug) ? 'solved' : ''}`}
                onClick={() => { setSelectedSlug(p.slug); setVerdict(null); }}>
                {mySolved.has(p.slug) ? '✓ ' : ''}{p.type === 'sql' ? '🗄️ ' : ''}{p.title}
                <span className={`diff-${p.difficulty}`}> ({p.difficulty})</span>
              </button>
            ))}
          </div>
          {problem && (
            <>
              <div className="statement-panel">
                <h3>{problem.title}</h3>
                {activeType === 'sql' ? (
                  sqlProblemDetail ? (
                    <>
                      <div className="statement">
                        {sqlProblemDetail.task.split('\n').filter(Boolean).map((p, i) => <p key={i}>{p}</p>)}
                      </div>
                      {(sqlProblemDetail.tables || []).map((t) => (
                        <div key={t.name} className="sql-schema-table">
                          <h4>📋 {t.name}</h4>
                          <SqlResultTable columns={t.columns} rows={t.rows} />
                        </div>
                      ))}
                    </>
                  ) : <div className="hint">Loading...</div>
                ) : (
                  problemDetail ? (
                    <>
                      <div className="statement">
                        {problemDetail.statement.split('\n').filter(Boolean).map((p, i) => <p key={i}>{p}</p>)}
                      </div>
                      {samples.map((tc, i) => (
                        <div key={i} className="sample">
                          <pre><strong>Sample Input {i + 1}</strong>{'\n'}{tc.input}</pre>
                          <pre><strong>Sample Output {i + 1}</strong>{'\n'}{tc.expectedOutput}</pre>
                        </div>
                      ))}
                    </>
                  ) : <div className="hint">Loading...</div>
                )}
              </div>
              {activeType !== 'sql' && (
                <div className="lang-pills">
                  {['java', 'python', 'cpp'].map((l) => (
                    <button key={l} className={`lang-pill ${lang === l ? 'active' : ''}`} onClick={() => setLang(l)}>
                      {l === 'cpp' ? 'C++' : l[0].toUpperCase() + l.slice(1)}
                    </button>
                  ))}
                </div>
              )}
              <Editor
                height="38vh"
                language={activeType === 'sql' ? 'sql' : (lang === 'cpp' ? 'cpp' : lang)}
                value={codeBySlug[activeSlug] ?? (activeType === 'sql' ? '' : STARTERS[lang])}
                onChange={(v) => setCodeBySlug((prev) => ({ ...prev, [activeSlug]: v }))}
                theme="vs-dark"
              />
              <button className="duel-submit-btn" onClick={submit} disabled={submitting}>
                {submitting ? 'Judging...' : 'Submit'}
              </button>
              {activeType === 'sql'
                ? <SqlVerdict submitting={submitting} verdict={verdict} />
                : <JudgeFeedback submitting={submitting} progress={progress} verdict={verdict} />}
              {error && <p className="error">{error}</p>}
            </>
          )}
        </div>
        <div className="duel-sidebar">
          <h3>Leaderboard</h3>
          <table>
            <thead><tr><th></th><th>Player</th><th>Score</th><th>Solved</th></tr></thead>
            <tbody>
              {state.leaderboard.map((row) => (
                <motion.tr key={row.username} layout transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                  className={row.username === username ? 'me' : ''}>
                  <td>{medal[row.rank] || row.rank}</td><td>{row.username}</td><td>{row.score}</td><td>{row.solvedCount}</td>
                </motion.tr>
              ))}
            </tbody>
          </table>
        </div>
      </motion.div>
    );
  }

  // finished
  const standings = finalStandings || state.leaderboard;
  const iWon = standings[0]?.username === username;
  return (
    <ResultsScreen standings={standings} iWon={iWon} onLeave={leaveToHome} />
  );
}
