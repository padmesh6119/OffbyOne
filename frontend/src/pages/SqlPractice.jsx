import { useEffect, useState } from 'react';
import Editor from '@monaco-editor/react';
import { api } from '../lib/api';

export default function SqlPractice() {
  const [problems, setProblems] = useState([]);
  const [selected, setSelected] = useState(null);
  const [query, setQuery] = useState('');
  const [result, setResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => { api.sqlProblems().then(setProblems); }, []);

  async function open(slug) {
    setResult(null);
    setQuery('');
    const detail = await api.sqlProblem(slug);
    setSelected(detail);
  }

  async function submit() {
    setSubmitting(true);
    setResult(null);
    try {
      const r = await api.sqlSubmit(selected.slug, query);
      setResult(r);
    } finally {
      setSubmitting(false);
    }
  }

  if (!selected) {
    return (
      <div className="page">
        <h2>SQL Duels</h2>
        <p className="hint">Answer by execution — logically correct queries pass, regardless of how they're written.</p>
        <table>
          <thead><tr><th>Title</th><th>Pattern</th><th>Difficulty</th><th>Rating</th></tr></thead>
          <tbody>
            {problems.map((p) => (
              <tr key={p.slug} onClick={() => open(p.slug)} style={{ cursor: 'pointer' }}>
                <td>{p.title}</td>
                <td>{p.pattern}</td>
                <td className={`diff-${p.difficulty}`}>{p.difficulty}</td>
                <td>{p.rating}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <div className="problem-layout">
      <div className="problem-left">
        <button onClick={() => setSelected(null)} className="link-btn">← Back to list</button>
        <h2>{selected.title}</h2>
        <span className={`diff-${selected.difficulty}`}>{selected.difficulty}</span>
        <div className="statement">{selected.task}</div>
        <h4>Schema</h4>
        {selected.schema.map((stmt, i) => (
          <pre key={i} className="sample">{stmt}</pre>
        ))}
      </div>
      <div className="problem-right">
        <Editor
          height="55vh"
          language="sql"
          value={query}
          onChange={(v) => setQuery(v ?? '')}
          theme="vs-dark"
        />
        <button onClick={submit} disabled={submitting || !query.trim()}>
          {submitting ? 'Running...' : 'Run Query'}
        </button>
        {result && (
          <div className={`verdict verdict-${result.verdict}`}>
            {result.verdict.toUpperCase()}{result.message ? ` — ${result.message}` : ''}
          </div>
        )}
      </div>
    </div>
  );
}
