import { useEffect } from 'react';
import { motion } from 'framer-motion';
import confetti from 'canvas-confetti';

export default function ResultsScreen({ standings, iWon, onLeave }) {
  useEffect(() => {
    if (!iWon) return;
    const duration = 1500;
    const end = Date.now() + duration;
    (function frame() {
      confetti({ particleCount: 4, angle: 60, spread: 60, origin: { x: 0 } });
      confetti({ particleCount: 4, angle: 120, spread: 60, origin: { x: 1 } });
      if (Date.now() < end) requestAnimationFrame(frame);
    })();
  }, [iWon]);

  const medal = { 1: '🥇', 2: '🥈', 3: '🥉' };

  return (
    <motion.div className="page results-screen" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.4 }}>
      <motion.div className="trophy-wrap" initial={{ scale: 0, rotate: -20 }} animate={{ scale: 1, rotate: 0 }}
        transition={{ type: 'spring', stiffness: 200, delay: 0.15 }}>
        {iWon ? '🏆' : '🍌'}
      </motion.div>
      <h2>{iWon ? 'You won!' : `${standings[0]?.username} wins`}</h2>
      <table>
        <thead><tr><th></th><th>Player</th><th>Score</th><th>Solved</th></tr></thead>
        <tbody>
          {standings.map((row) => (
            <motion.tr key={row.username} className={row.rank === 1 ? 'winner' : ''}
              initial={{ opacity: 0, x: -12 }} animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.1 * row.rank }}>
              <td>{medal[row.rank] || row.rank}</td>
              <td>{row.username}</td>
              <td>{row.score}{row.ratingDelta != null ? ` (${row.ratingDelta >= 0 ? '+' : ''}${row.ratingDelta})` : ''}</td>
              <td>{row.solvedCount ?? '-'}</td>
            </motion.tr>
          ))}
        </tbody>
      </table>
      <button onClick={onLeave} className="start-btn">Play Again</button>
    </motion.div>
  );
}
