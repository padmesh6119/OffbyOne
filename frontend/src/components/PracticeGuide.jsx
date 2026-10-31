import ResultTable from './SqlResultTable';

const reference = [
  ['Choose columns', 'SELECT name, score FROM students;'],
  ['Filter rows', 'SELECT name FROM students WHERE score >= 60;'],
  ['Sort results', 'SELECT name FROM students ORDER BY name;'],
  ['Remove repeats', 'SELECT DISTINCT city FROM students;'],
  ['Limit results', 'SELECT id FROM students ORDER BY id LIMIT 2;'],
  ['Count or calculate', 'SELECT COUNT(*), SUM(score), MAX(score) FROM students;'],
];

export default function PracticeGuide({ problem, review, onNext, hasNext, busy }) {
  return <section className="practice-guide" aria-label="Practice learning tools">
    <div className="section-heading"><div><div className="arena-eyebrow">LEARN AT YOUR PACE</div><h2>A little help goes a long way.</h2></div></div>
    <div className="guide-grid">
      <details className="guide-card"><summary>Need a hint?<span>01</span></summary><p>{problem.hint || 'Start with the requested columns, then work through each condition in the question.'}</p></details>
      <details className="guide-card"><summary>Expected sample output<span>02</span></summary><p>This is the result for the public sample. Your query should also work when the data changes.</p>{problem.expected ? <div className="guide-table"><ResultTable columns={problem.expected.columns} rows={problem.expected.rows}/></div> : <p>Run the sample to compare your result.</p>}</details>
      <details className="guide-card"><summary>SQL quick reference<span>03</span></summary><p>Small examples to borrow from. Use the table and columns in your challenge.</p>{reference.map(([label, sql]) => <div className="reference-item" key={label}><strong>{label}</strong><pre>{sql}</pre></div>)}</details>
    </div>
    {review && <div className="practice-review" role="region" aria-label="Practice solution"><div className="arena-eyebrow">SOLVED · NOW UNDERSTAND WHY</div><h3>One way to solve it.</h3><p>{review.explanation}</p><pre>{review.solution}</pre><button className="arena-primary" disabled={busy || !hasNext} onClick={onNext}>{hasNext ? 'Next unsolved challenge →' : 'All challenges solved ✓'}</button></div>}
  </section>;
}
