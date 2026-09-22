import { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Editor from '@monaco-editor/react';
import confetti from 'canvas-confetti';
import { api, errorMessage } from '../lib/api';
import { subscribe } from '../lib/ws';
import { STARTERS } from '../lib/starters';
import JudgeFeedback from '../components/JudgeFeedback';

export default function DuelScreen({ roomId, room, leaderboard, me, username, onLeave }) {
  const [problem, setProblem] = useState(null);
  const [samples, setSamples] = useState([]);
  const [lang, setLang] = useState('java');
  const [code, setCode] = useState(STARTERS.java);
  const [submitting, setSubmitting] = useState(false);
  const [verdict, setVerdict] = useState(null);
  const [progress, setProgress] = useState(null);
  const [error, setError] = useState('');

  const [flashRound, setFlashRound] = useState(null);
  const [resultPulse, setResultPulse] = useState(null); // 'correct' | 'wrong' | null
  const [shaking, setShaking] = useState(false);
  const [roundWon, setRoundWon] = useState(null); // { winner, round } | null
  const [countdown, setCountdown] = useState(5);
  const [opponentStatus, setOpponentStatus] = useState('thinking'); // 'thinking' | 'wrong'

  const seenRoundRef = useRef(null);
  const opponentTimerRef = useRef(null);

  const opponent = leaderboard.find((p) => p.username !== username);
  const myScore = me?.score ?? 0;
  const opponentScore = opponent?.score ?? 0;

  // fetch the current round's problem + samples whenever it changes
  useEffect(() => {
    if (!room.currentProblemSlug) return;
    api.problem(room.currentProblemSlug).then(setProblem).catch(() => {});
    api.samples(room.currentProblemSlug).then(setSamples).catch(() => {});
    setCode(STARTERS[lang]);
    setVerdict(null);
    setError('');
    setResultPulse(null);
    // fire the round flash the first time we see this round (covers the initial round too)
    if (seenRoundRef.current !== room.roundsPlayed) {
      seenRoundRef.current = room.roundsPlayed;
      setFlashRound(room.roundsPlayed);
      const t = setTimeout(() => setFlashRound(null), 1400);
      return () => clearTimeout(t);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [room.currentProblemSlug]);

  // round_won interlude — own subscription, independent of parent's lobby subscription
  useEffect(() => {
    if (!roomId) return;
    const sub = subscribe(`/topic/room/${roomId}/lobby`, (data) => {
      if (data.event === 'round_won') {
        setRoundWon({ winner: data.winner, round: data.round });
        setCountdown(5);
      }
    });
    return () => sub?.unsubscribe?.();
  }, [roomId]);

  useEffect(() => {
    if (!roundWon) return;
    if (countdown <= 0) { setRoundWon(null); return; }
    const t = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [roundWon, countdown]);

  // opponent status — own subscription, filters out my own submissions
  useEffect(() => {
    if (!roomId) return;
    const sub = subscribe(`/topic/room/${roomId}/submission`, (data) => {
      if (data.username === username) return;
      if (data.verdict !== 'accepted') {
        setOpponentStatus('wrong');
        clearTimeout(opponentTimerRef.current);
        opponentTimerRef.current = setTimeout(() => setOpponentStatus('thinking'), 3000);
      }
    });
    return () => { sub?.unsubscribe?.(); clearTimeout(opponentTimerRef.current); };
  }, [roomId, username]);

  async function submit() {
    setSubmitting(true);
    setVerdict(null);
    setProgress(null);
    setError('');
    try {
      const { submissionId } = await api.submit({ slug: room.currentProblemSlug, roomId, language: lang, code });
      const progressSub = subscribe(`/topic/submission/${submissionId}/progress`, (data) => setProgress(data));
      const sub = subscribe(`/topic/submission/${submissionId}`, (data) => {
        setVerdict(data);
        setSubmitting(false);
        setProgress(null);
        sub.unsubscribe();
        progressSub?.unsubscribe?.();
        if (data.verdict === 'accepted') {
          setResultPulse('correct');
          confetti({ particleCount: 120, spread: 70, origin: { y: 0.6 } });
        } else {
          setResultPulse('wrong');
          setShaking(true);
          setTimeout(() => setShaking(false), 500);
        }
        setTimeout(() => setResultPulse(null), 700);
      });
    } catch (err) {
      setError(errorMessage(err));
      setSubmitting(false);
    }
  }

  return (
    <div className="duel-screen">
      <AnimatePresence>
        {flashRound && (
          <motion.div className="round-flash-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <motion.span initial={{ scale: 0.6, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ type: 'spring', stiffness: 200 }}>
              ROUND {flashRound}
            </motion.span>
          </motion.div>
        )}
        {roundWon && (
          <motion.div className="round-won-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            <motion.div initial={{ scale: 0.8, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ type: 'spring', stiffness: 180 }}>
              <h2>{roundWon.winner} won Round {roundWon.round}!</h2>
              <div className="round-won-score">{myScore} – {opponentScore}</div>
              <div className="round-won-countdown">Next round in {countdown}…</div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="duel-vs-bar">
        <div className="duel-player">
          <span className="duel-player-name">{username}</span>
          <motion.span key={myScore} className="duel-score-circle" initial={{ scale: 0.5, opacity: 0 }} animate={{ scale: 1, opacity: 1 }}>
            {myScore}
          </motion.span>
        </div>
        <div className="duel-vs-center">
          <span className="duel-vs-text">VS</span>
          <span className="round-counter">Round {room.roundsPlayed}</span>
        </div>
        <div className="duel-player duel-player-right">
          <motion.span key={opponentScore} className="duel-score-circle" initial={{ scale: 0.5, opacity: 0 }} animate={{ scale: 1, opacity: 1 }}>
            {opponentScore}
          </motion.span>
          <span className="duel-player-name">{opponent?.username || '...'}</span>
        </div>
      </div>

      <div className="opponent-status">
        {opponentStatus === 'wrong' ? `🔴 ${opponent?.username || 'opponent'} got wrong answer` : `🔴 ${opponent?.username || 'opponent'} is thinking...`}
      </div>

      <div className={`duel-body ${resultPulse ? `flash-${resultPulse}` : ''} ${shaking ? 'shake' : ''}`}>
        <motion.div className="duel-problem-panel" key={room.currentProblemSlug}
          initial={{ opacity: 0, x: 40 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: 0.4 }}>
          {problem ? (
            <>
              <h2>{problem.title}</h2>
              <span className={`diff-${problem.difficulty}`}>{problem.difficulty}</span>
              <div className="statement">
                {problem.statement.split('\n').filter(Boolean).map((p, i) => <p key={i}>{p}</p>)}
              </div>
              {samples.map((tc, i) => (
                <div key={i} className="sample">
                  <pre><strong>Sample Input {i + 1}</strong>{'\n'}{tc.input}</pre>
                  <pre><strong>Sample Output {i + 1}</strong>{'\n'}{tc.expectedOutput}</pre>
                </div>
              ))}
            </>
          ) : <p className="hint">Loading problem...</p>}
        </motion.div>

        <div className="duel-editor-panel">
          <div className="lang-pills">
            {['java', 'python', 'cpp'].map((l) => (
              <button key={l} className={`lang-pill ${lang === l ? 'active' : ''}`}
                onClick={() => { setLang(l); setCode(STARTERS[l]); }}>
                {l === 'cpp' ? 'C++' : l[0].toUpperCase() + l.slice(1)}
              </button>
            ))}
          </div>
          <Editor height="45vh" language={lang === 'cpp' ? 'cpp' : lang} value={code} onChange={setCode} theme="vs-dark" />
          <button className="duel-submit-btn" onClick={submit} disabled={submitting || !problem}>
            {submitting ? 'Judging...' : 'Submit'}
          </button>

          <JudgeFeedback submitting={submitting} progress={progress} verdict={verdict} />
          {error && <p className="error">{error}</p>}
        </div>
      </div>

      <button onClick={onLeave} className="link-btn">Leave duel</button>
    </div>
  );
}
