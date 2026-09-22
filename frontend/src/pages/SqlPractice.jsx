import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import Editor from '@monaco-editor/react';
import { api } from '../lib/api';

function ResultTable({ columns, rows }) {
  return (
    <table className="sql-result-table">
      <thead><tr>{columns.map((c) => <th key={c}>{c}</th>)}</tr></thead>
      <tbody>
        {rows.length === 0
          ? <tr><td colSpan={columns.length} className="hint">(no rows)</td></tr>
          : rows.map((row, i) => (
              <tr key={i}>{row.map((v, j) => <td key={j}>{v === null ? <em className="hint">null</em> : String(v)}</td>)}</tr>
            ))}
      </tbody>
    </table>
  );
}

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
      <motion.div className="page" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.3 }}>
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
      </motion.div>
    );
  }

  return (
    <motion.div className="problem-layout" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.3 }}>
      <div className="problem-left">
        <button onClick={() => setSelected(null)} className="link-btn">← Back to list</button>
        <h2>{selected.title}</h2>
        <span className={`diff-${selected.difficulty}`}>{selected.difficulty}</span>
        <div className="statement">
          {selected.task.split('\n').filter(Boolean).map((p, i) => <p key={i}>{p}</p>)}
        </div>
        {(selected.tables || []).map((t) => (
          <div key={t.name} className="sql-schema-table">
            <h4>📋 {t.name}</h4>
            <ResultTable columns={t.columns} rows={t.rows} />
          </div>
        ))}
      </div>
      <div className="problem-right">
        <Editor
          height="45vh"
          language="sql"
          value={query}
          onChange={(v) => setQuery(v ?? '')}
          theme="vs-dark"
        />
        <button onClick={submit} disabled={submitting || !query.trim()}>
          {submitting ? 'Running...' : 'Run Query'}
        </button>

        {result && result.verdict === 'accepted' && (
          <div className="sql-result-panel sql-result-accepted">
            <div className="verdict verdict-accepted">✓ Accepted</div>
            <ResultTable columns={result.actual.columns} rows={result.actual.rows} />
          </div>
        )}

        {result && result.verdict === 'wrong_answer' && (
          <div className="sql-result-panel">
            <div className="verdict verdict-wrong_answer">✗ Wrong Answer</div>
            <div className="sql-diff">
              <div>
                <h4>Your Result</h4>
                <ResultTable columns={result.actual.columns} rows={result.actual.rows} />
              </div>
              <div>
                <h4>Expected Result</h4>
                <ResultTable columns={result.expected.columns} rows={result.expected.rows} />
              </div>
            </div>
          </div>
        )}

        {result && !['accepted', 'wrong_answer'].includes(result.verdict) && (
          <div className="sql-error-box">
            <strong>{result.verdict === 'tle' ? 'Timed out' : 'SQL Error'}</strong>
            <pre>{result.message}</pre>
          </div>
        )}
      </div>
    </motion.div>
  );
}
