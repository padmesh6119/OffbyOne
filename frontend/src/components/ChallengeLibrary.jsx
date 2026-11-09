import { useEffect, useState } from 'react';

export default function ChallengeLibrary({ loading, loadError, onRetry, bank, solved, bookmarks, filter, setFilter, search, setSearch, status, setStatus, busy, onOpen, onBookmark }) {
  const [pageIndex, setPageIndex] = useState(0);
  useEffect(() => { setPageIndex(0); }, [filter, search, status]);
  const visible = bank.filter(p =>
    (filter === 'all' || p.difficulty === filter) &&
    `${p.title} ${p.pattern}`.toLowerCase().includes(search.toLowerCase()) &&
    (status === 'all' || (status === 'unsolved' && !solved.has(p.slug)) ||
      (status === 'solved' && solved.has(p.slug)) || (status === 'bookmarked' && bookmarks.has(p.slug))));
  const completed = bank.filter(p => solved.has(p.slug)).length;
  const pageCount = Math.ceil(visible.length / 12);
  const page = Math.min(pageIndex, Math.max(0, pageCount - 1));
  const displayed = visible.slice(page * 12, (page + 1) * 12);
  function changePage(next) {
    setPageIndex(next);
    document.getElementById('challenges')?.scrollIntoView({ behavior: 'instant', block: 'start' });
  }
  return <section className="arena-library" id="challenges">
    <div className="library-main">
      <div className="section-heading"><div><h2>Challenges</h2></div><span className="library-count">{completed} / {bank.length} solved</span></div>
      <progress className="practice-progress" aria-label="Challenges solved" value={completed} max={bank.length || 1}/>
      <div className="library-tools">
        <div className="difficulty-tabs" role="group" aria-label="Filter by difficulty">{['all', 'easy', 'medium', 'hard'].map(f => <button key={f} aria-pressed={filter === f} className={filter === f ? 'selected' : ''} onClick={() => setFilter(f)}>{f === 'all' ? 'All levels' : f}</button>)}</div>
        <input aria-label="Search challenges" placeholder="Search challenges…" value={search} onChange={e => setSearch(e.target.value)}/>
      </div>
      <div className="practice-filters">
        <label>Show <select aria-label="Practice status" value={status} onChange={e => setStatus(e.target.value)}><option value="all">All challenges</option><option value="unsolved">Unsolved</option><option value="solved">Solved</option><option value="bookmarked">Bookmarked</option></select></label>
        <span aria-live="polite">{visible.length} matching</span>
        <button className="arena-secondary" disabled={busy || !visible.length} onClick={() => onOpen(visible[Math.floor(Math.random() * visible.length)])}>Pick random ↗</button>
      </div>
      <div className="challenge-list">{displayed.map((p, i) => <div className="challenge-card" key={p.slug}>
        <button className="challenge-row" aria-label={`Open ${p.title}`} disabled={busy} onClick={() => onOpen(p)}><span className={`challenge-number ${solved.has(p.slug) ? 'solved' : ''}`}>{solved.has(p.slug) ? '✓' : String(page * 12 + i + 1).padStart(2, '0')}</span><span className="challenge-name"><strong>{p.title}</strong><small>{p.pattern.replaceAll('+', ' / ').replaceAll('-', ' ')}</small></span><span className={`arena-difficulty ${p.difficulty}`}>{p.difficulty}</span></button>
        <button className="challenge-save" aria-label={`${bookmarks.has(p.slug) ? 'Remove bookmark for' : 'Bookmark'} ${p.title}`} aria-pressed={bookmarks.has(p.slug)} onClick={() => onBookmark(p.slug)}>{bookmarks.has(p.slug) ? '★' : '☆'}</button>
      </div>)}{!visible.length && <p className="arena-no-results">{loadError ? <>{loadError} <button className="arena-secondary" onClick={onRetry}>Try loading challenges again</button></> : loading ? 'Waking the server and loading challenges. This can take a few minutes on free hosting…' : bank.length ? 'No challenges match these filters. Try another difficulty, status, or search.' : 'No challenges are available right now.'}</p>}</div>
      {pageCount > 1 && <nav className="library-pagination" aria-label="Challenge pages"><span>{page * 12 + 1}–{Math.min((page + 1) * 12, visible.length)} of {visible.length} challenges</span><div>{Array.from({ length: pageCount }, (_, n) => <button key={n} aria-label={`Challenge page ${n + 1}`} aria-current={page === n ? 'page' : undefined} onClick={() => changePage(n)}>{n + 1}</button>)}</div></nav>}
      <p className="bookmark-note">Bookmarks stay in this browser for your current guest pass. Random practice uses the filters above.</p>
    </div>
    <aside className="arena-playbook"><h3>How duels work</h3>{[['01', 'Pick your opponent', 'Create a private duel. Send the invite to a friend.'], ['02', 'Secure the round', 'First correct query takes the point. Hidden datasets prevent lucky guesses.'], ['03', 'Review the tape', 'First to two wins. Study the solutions and run it back.']].map(([n, title, desc]) => <div className="playbook-step" key={n}><span>{n}</span><div><strong>{title}</strong><p>{desc}</p></div></div>)}<div className="playbook-note">SQLite dialect <span>•</span> Friendly competition<br/>Timeouts draw the round. Three rounds maximum.</div></aside>
  </section>;
}
