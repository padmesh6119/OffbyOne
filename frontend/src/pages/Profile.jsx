import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { api } from '../lib/api';
import RatingSparkline from '../components/RatingSparkline';

export default function Profile() {
  const username = localStorage.getItem('username');
  const nav = useNavigate();
  const [profile, setProfile] = useState(null);
  const [rivals, setRivals] = useState([]);
  const [streak, setStreak] = useState(null);
  const [daily, setDaily] = useState(null);

  useEffect(() => {
    if (!username) return;
    api.profile(username).then(setProfile).catch(() => {});
    api.rivals(username).then(setRivals).catch(() => {});
    api.streak().then(setStreak).catch(() => {});
    api.dailyChallenge().then(setDaily).catch(() => {});
  }, [username]);

  if (!profile) return <div className="page"><p className="hint">Loading...</p></div>;

  const totalMatches = profile.wins + profile.losses;
  const winRate = totalMatches ? Math.round((profile.wins / totalMatches) * 100) : 0;

  return (
    <motion.div className="page profile-page" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.3 }}>
      <h2>{profile.username}</h2>

      <div className="profile-grid">
        <div className="profile-card">
          <span className="hint">Rating</span>
          <span className="profile-stat">{profile.rating}</span>
          <RatingSparkline history={profile.ratingHistory} />
        </div>

        <div className="profile-card">
          <span className="hint">Record</span>
          <span className="profile-stat">{profile.wins}–{profile.losses}</span>
          <span className="hint">{totalMatches ? `${winRate}% win rate over ${totalMatches} matches` : 'No matches yet'}</span>
        </div>

        <div className="profile-card">
          <span className="hint">Solved</span>
          <div className="split-bar">
            <div className="split-bar-java" style={{ flex: profile.solvedJava || 0.001 }} />
            <div className="split-bar-sql" style={{ flex: profile.solvedSql || 0.001 }} />
          </div>
          <span className="hint">{profile.solvedJava} coding · {profile.solvedSql} SQL</span>
        </div>

        <div className="profile-card">
          <span className="hint">Streak</span>
          <span className="profile-stat">🔥 {streak?.currentStreak ?? 0}</span>
          <span className="hint">
            {streak?.solvedToday ? 'Solved today' : 'Not solved today'} · longest {streak?.longestStreak ?? 0}
          </span>
        </div>
      </div>

      {daily && (
        <button className="daily-challenge-card" onClick={() => nav(`/problem/${daily.slug}`)}>
          <span className="hint">Today's challenge</span>
          <span className="profile-stat" style={{ fontSize: '1.2rem' }}>{daily.title}</span>
          <span className={`diff-${daily.difficulty}`}>{daily.difficulty}</span>
        </button>
      )}

      <h3>Rivals</h3>
      {rivals.length === 0 ? (
        <p className="hint">No head-to-head history yet — play someone more than once.</p>
      ) : (
        <table>
          <thead><tr><th>Opponent</th><th>W</th><th>L</th><th>Matches</th></tr></thead>
          <tbody>
            {rivals.map((r) => (
              <tr key={r.username}>
                <td>{r.username}</td><td>{r.wins}</td><td>{r.losses}</td><td>{r.matches}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </motion.div>
  );
}
