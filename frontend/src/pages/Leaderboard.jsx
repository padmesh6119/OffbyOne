import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
import '../engagement.css';

export default function Leaderboard() {
  const [data, setData] = useState(null), [error, setError] = useState(''), [loading, setLoading] = useState(false), [reload, setReload] = useState(0);
  useEffect(() => {
    let live = true; setLoading(true); setError('');
    api.standings().then(d => { if (live) setData(d); }).catch(e => { if (live) setError(errorMessage(e)); }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [reload]);
  return <section className="standings-page"><div className="page-intro"><div><h1>Earn your place<span className="brand-period">.</span></h1><p>Real players. Real solves. Every first clear moves you up.</p></div><button className="arena-secondary" disabled={loading} onClick={() => setReload(n => n + 1)}>{loading ? 'Refreshing…' : 'Refresh standings ↻'}</button></div>
    <div className="standings-explainer"><div><span className="arena-eyebrow">YOUR POSITION</span><strong>{data?.yourRank ? `#${data.yourRank}` : 'Unranked'}</strong><p>{data?.yourRank ? `Among ${data.totalPlayers} active guests with a solve.` : 'Solve your first challenge to join the board.'}</p></div><div><span className="arena-eyebrow">HOW XP WORKS</span><p><b>Easy 100</b> · <b>Medium 200</b> · <b>Hard 350</b></p><p>Each challenge awards XP once per guest pass. Repeated solves earn no extra XP. Equal XP shares a rank.</p></div><div><span className="arena-eyebrow">A FRESH FIELD</span><p>Only active guest passes appear. Your entry leaves when your pass expires.</p><Link className="arena-text-link" to="/">Find your next challenge ↗</Link></div></div>
    {error && <div className="hub-notice" role="alert">{error}</div>}
    {!data && loading && <p role="status" className="arena-loading">Finding the field…</p>}
    {data && <><div className="section-heading"><h2>The standings</h2><span className="hub-muted">Top 50 · Updated {new Date(data.generatedAt).toLocaleTimeString()}</span></div>{data.players.length ? <div className="standings-table-wrap"><table className="standings-table"><thead><tr><th scope="col">Rank</th><th scope="col">Player</th><th scope="col">Clears</th><th scope="col">XP</th></tr></thead><tbody>{data.players.map(p => <tr key={p.username} className={p.you ? 'your-standing' : ''}><td><span className={`rank-number rank-${p.rank}`}>{String(p.rank).padStart(2, '0')}</span></td><th scope="row"><span className="standing-name">{p.username} {p.you && <small>YOU</small>}</span></th><td>{p.solved}</td><td><strong>{p.xp.toLocaleString()}</strong></td></tr>)}</tbody></table></div> : <div className="workspace-empty"><h3>The first spot is open.</h3><p>Be the first active guest to put a solve on the board.</p><Link to="/" className="arena-primary">Start a challenge →</Link></div>}</>}
  </section>;
}
