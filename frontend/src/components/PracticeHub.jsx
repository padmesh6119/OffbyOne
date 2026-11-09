import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from './Icon';
import Reveal from './Reveal';
import { api, errorMessage } from '../lib/api';

export const paths = [
  { title: 'Read the room', label: '01 / FOUNDATIONS', description: 'Select, filter and sort. Build confidence one query at a time.', match: p => p.difficulty === 'easy' && !['count', 'sum', 'max', 'min', 'avg'].includes(p.pattern) },
  { title: 'Make numbers talk', label: '02 / AGGREGATION', description: 'Turn rows into totals, groups and useful answers.', match: p => ['count', 'sum', 'max', 'min', 'avg', 'group-by', 'having', 'date+group-by'].includes(p.pattern) },
  { title: 'Better together', label: '03 / JOINS', description: 'Connect tables and find what is missing between them.', match: p => p.pattern.includes('join') },
  { title: 'Think in layers', label: '04 / ADVANCED', description: 'Rank results, nest queries and work with windows.', match: p => /subquery|ranking|window/.test(p.pattern) },
];

export function PlayerLevel({ data }) {
  return <div className="player-level"><span className="level-number">{data.level}<small>LEVEL</small></span><div><div className="level-caption"><strong>{data.xp.toLocaleString()} XP</strong><span>{500 - data.levelProgress} XP to level {data.level + 1}</span></div><progress aria-label="Progress to next level" value={data.levelProgress} max="500"/><small>First solve: easy +100 · medium +200 · hard +350 XP</small></div></div>;
}

export default function PracticeHub({ bank, solved, busy, onOpen, children, library }) {
  const [view, setView] = useState('challenges');
  const [data, setData] = useState(null), [error, setError] = useState(''), [now, setNow] = useState(Date.now());
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let live = true; setError('');
    api.practiceHub().then(d => { if (live) setData(d); }).catch(e => { if (live) setError(errorMessage(e)); });
    return () => { live = false; };
  }, [reload]);
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  useEffect(() => {
    if (!data) return;
    const timer = setTimeout(() => setReload(n => n + 1), Math.max(0, Date.parse(data.daily.resetsAt) - Date.now()) + 1000);
    return () => clearTimeout(timer);
  }, [data?.daily.resetsAt]);
  const remaining = data ? Math.max(0, Math.floor((Date.parse(data.daily.resetsAt) - now) / 1000)) : 0;
  const countdown = `${String(Math.floor(remaining / 3600)).padStart(2, '0')}:${String(Math.floor(remaining / 60) % 60).padStart(2, '0')}:${String(remaining % 60).padStart(2, '0')}`;
  return <>
    <div className="play-grid">
      {children}
      <article className={`daily-card ${data?.daily.completed ? 'daily-cleared' : ''}`} aria-label="Daily challenge">
        <div className="daily-top"><span>{data?.daily.completed ? '✓ Daily completed' : 'Daily challenge'}</span><Icon name="code" size={24}/></div>
        <div className="daily-emblem" aria-hidden="true"><span>✳</span></div>
        {error ? <div className="hub-notice" role="alert">{error} <button onClick={() => setReload(n => n + 1)}>Try again</button></div> : !data ? <p className="daily-loading" role="status">Loading your daily challenge…</p> : <>
          <h2>{data.daily.title}</h2><p>A fresh start. One good query.</p>
          <button className="arena-primary" disabled={busy} onClick={() => onOpen(data.daily)}>{data.daily.completed ? 'Play it again' : 'Take the challenge'} <span aria-hidden="true">↗</span></button>
          <span className="daily-countdown" title="Resets at midnight UTC">Next challenge in {countdown}</span>
        </>}
      </article>
    </div>
    {data && <div className="session-card" aria-label="Your progress">
      <PlayerLevel data={data}/><div className="session-mini-stats"><span><strong>{data.solved.length}</strong> solved</span><span><strong>{data.badges.filter(b => b.unlocked).length}/{data.badges.length}</strong> badges</span></div><Link to="/profile" className="arena-text-link">My progress ↗</Link>
    </div>}
    <section className="practice-hub" id="practice" aria-label="Practice challenges and learning paths">
      <div className="practice-browser-heading"><h2>Build your edge.</h2><div className="practice-view" role="group" aria-label="Practice view"><button aria-pressed={view === 'challenges'} onClick={() => setView('challenges')}>All challenges</button><button aria-pressed={view === 'paths'} onClick={() => setView('paths')}>Learning paths</button></div></div>
      <div hidden={view !== 'challenges'}>{library}</div>
      <div hidden={view !== 'paths'}>
        <div className="paths-heading"><h3>One skill at a time.</h3><p>Pick a path. We’ll find your next challenge.</p></div>
        <div className="learning-paths">{paths.map((path, index) => {
          const missions = bank.filter(path.match), done = missions.filter(p => solved.has(p.slug)).length;
          const next = missions.find(p => !solved.has(p.slug)) || missions[0];
          return <Reveal as="article" className="learning-path" delay={index * 65} key={path.title}><div className="path-top"><span className="path-icon"><Icon name={["code", "chart", "join", "layers"][index]} size={28}/></span><span className="path-index">0{index + 1}</span></div><h3>{path.title}</h3><p>{path.description}</p><progress aria-label={`${path.title} completion`} value={done} max={missions.length || 1}/><div className="path-progress"><span>{done} / {missions.length} cleared</span><strong>{missions.length && done === missions.length ? '✓ Complete' : `${missions.length ? Math.round(done / missions.length * 100) : 0}%`}</strong></div><button className="arena-secondary" disabled={busy || !next} onClick={() => onOpen(next)}>{done === missions.length && next ? 'Revisit path ↗' : done ? 'Continue path →' : 'Start path →'}</button>{next && <small>Next: {next.title}</small>}</Reveal>;
        })}</div>
      </div>
    </section>
  </>;
}
