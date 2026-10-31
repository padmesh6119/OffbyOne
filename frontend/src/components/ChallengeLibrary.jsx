export default function ChallengeLibrary({ bank, solved, bookmarks, filter, setFilter, search, setSearch, status, setStatus, busy, onOpen, onBookmark }) {
  const visible = bank.filter(p =>
    (filter === 'all' || p.difficulty === filter) &&
    `${p.title} ${p.pattern}`.toLowerCase().includes(search.toLowerCase()) &&
    (status === 'all' || (status === 'unsolved' && !solved.has(p.slug)) ||
      (status === 'solved' && solved.has(p.slug)) || (status === 'bookmarked' && bookmarks.has(p.slug))));
  const completed = bank.filter(p => solved.has(p.slug)).length;
  return <section className="arena-library" id="challenges">
    <div className="library-main">
      <div className="section-heading"><div><div className="arena-eyebrow">PRACTICE MAKES PRECISE</div><h2>Your next breakthrough<span>.</span></h2></div><span className="library-count">{completed} / {bank.length} CLEARED</span></div>
      <progress className="practice-progress" aria-label="Challenges solved" value={completed} max={bank.length || 1}/>
      <div className="library-tools">
        <div className="difficulty-tabs" role="group" aria-label="Filter by difficulty">{['all', 'easy', 'medium', 'hard'].map(f => <button key={f} aria-pressed={filter === f} className={filter === f ? 'selected' : ''} onClick={() => setFilter(f)}>{f === 'all' ? 'All missions' : f}</button>)}</div>
        <input aria-label="Search challenges" placeholder="Search missions…" value={search} onChange={e => setSearch(e.target.value)}/>
      </div>
      <div className="practice-filters">
        <label>SHOW <select aria-label="Practice status" value={status} onChange={e => setStatus(e.target.value)}><option value="all">All challenges</option><option value="unsolved">Unsolved</option><option value="solved">Solved</option><option value="bookmarked">Bookmarked</option></select></label>
        <span aria-live="polite">{visible.length} matching</span>
        <button className="arena-secondary" disabled={busy || !visible.length} onClick={() => onOpen(visible[Math.floor(Math.random() * visible.length)])}>Pick random ↗</button>
      </div>
      <div className="challenge-list">{visible.map((p, i) => <div className="challenge-card" key={p.slug}>
        <button className="challenge-row" aria-label={`Open ${p.title}`} disabled={busy} onClick={() => onOpen(p)}><span className={`challenge-number ${solved.has(p.slug) ? 'solved' : ''}`}>{solved.has(p.slug) ? '✓' : String(i + 1).padStart(2, '0')}</span><span className="challenge-name"><strong>{p.title}</strong><small>{p.pattern.replaceAll('+', ' / ').replaceAll('-', ' ')}</small></span><span className={`arena-difficulty ${p.difficulty}`}>{p.difficulty}</span></button>
        <button className="challenge-save" aria-label={`${bookmarks.has(p.slug) ? 'Remove bookmark for' : 'Bookmark'} ${p.title}`} aria-pressed={bookmarks.has(p.slug)} onClick={() => onBookmark(p.slug)}>{bookmarks.has(p.slug) ? '★' : '☆'}</button>
      </div>)}{!visible.length && <p className="arena-no-results">{bank.length ? 'No challenges match these filters. Try another difficulty, status, or search.' : 'Loading missions…'}</p>}</div>
      <p className="bookmark-note">Bookmarks stay in this browser for your current guest pass. Random practice uses the filters above.</p>
    </div>
    <aside className="arena-playbook"><span className="arena-eyebrow">HOW TO PLAY</span><h3>The rules.<br/>The rivalry.</h3>{[['01', 'Pick your opponent', 'Create a private duel. Send the invite to a friend.'], ['02', 'Secure the round', 'First correct query takes the point. Hidden datasets prevent lucky guesses.'], ['03', 'Review the tape', 'First to two wins. Study the solutions and run it back.']].map(([n, title, desc]) => <div className="playbook-step" key={n}><span>{n}</span><div><strong>{title}</strong><p>{desc}</p></div></div>)}<div className="playbook-note">SQLite dialect <span>•</span> Friendly competition<br/>Timeouts draw the round. Three rounds maximum.</div></aside>
  </section>;
}
