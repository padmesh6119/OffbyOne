import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';

export default function SubmissionHistory({ attempts }) {
  const [filter, setFilter] = useState('all'), [search, setSearch] = useState(''), [page, setPage] = useState(0);
  const [detail, setDetail] = useState(null), [error, setError] = useState(''), [loading, setLoading] = useState(null);
  const filtered = [...attempts].filter(a => (filter === 'all' || (filter === 'accepted' ? a.verdict === 'accepted' : a.verdict !== 'accepted')) && `${a.problemTitle} ${a.problemSlug}`.toLowerCase().includes(search.toLowerCase())).sort((a, b) => b.submittedAt.localeCompare(a.submittedAt));
  async function review(id) {
    if (loading) return;
    if (detail?.id === id) { setDetail(null); return; }
    setLoading(id); setError('');
    try { setDetail(await api.submission(id)); } catch (e) { setError(errorMessage(e)); } finally { setLoading(null); }
  }
  return <section className="submission-history"><div className="section-heading"><h2>Your query notebook</h2><span className="hub-muted">{filtered.length} attempts</span></div><div className="history-tools"><label className="sr-only" htmlFor="history-search">Search submission history</label><input id="history-search" placeholder="Find a challenge…" value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}/><select aria-label="Filter submission verdict" value={filter} onChange={e => { setFilter(e.target.value); setPage(0); }}><option value="all">All attempts</option><option value="accepted">Accepted</option><option value="retry">Needs another go</option></select></div>
    {error && <div className="hub-notice" role="alert">{error}</div>}
    <div className="history-list">{filtered.slice(page * 8, page * 8 + 8).map(a => <article className="history-entry" key={a.id}><div className="history-entry-heading"><span className={`history-verdict ${a.verdict === 'accepted' ? 'accepted' : ''}`}>{a.verdict === 'accepted' ? '✓' : '↗'}</span><div><Link to={`/?challenge=${encodeURIComponent(a.problemSlug)}`}>{a.problemTitle}</Link><small>{new Date(a.submittedAt + (/Z|[+-]\d\d:\d\d$/.test(a.submittedAt) ? '' : 'Z')).toLocaleString()} · {a.verdict.replaceAll('_', ' ')}</small></div><button className="arena-secondary" disabled={!!loading} aria-expanded={detail?.id === a.id} onClick={() => review(a.id)}>{loading === a.id ? 'Loading…' : detail?.id === a.id ? 'Close query' : 'Review query'}</button></div>{detail?.id === a.id && <div className="history-code"><pre>{detail.code}</pre><Link className="arena-text-link" to={`/?challenge=${encodeURIComponent(a.problemSlug)}&attempt=${a.id}`}>Restore query in editor ↗</Link></div>}</article>)}</div>
    {!filtered.length && <div className="workspace-empty"><h3>{attempts.length ? 'No matching attempts.' : 'Your first query belongs here.'}</h3><p>{attempts.length ? 'Try another search or verdict filter.' : 'Every submitted query is saved here, ready to review or restore.'}</p><Link className="arena-text-link" to="/">Enter training ↗</Link></div>}
    {filtered.length > 8 && <div className="history-pagination"><button className="arena-secondary" disabled={!page} onClick={() => setPage(p => p - 1)}>← Previous</button><span>Page {page + 1} of {Math.ceil(filtered.length / 8)}</span><button className="arena-secondary" disabled={(page + 1) * 8 >= filtered.length} onClick={() => setPage(p => p + 1)}>Next →</button></div>}
  </section>;
}
