import { motion } from 'framer-motion';
import ResultTable from './SqlResultTable';

/** Renders a room-based SQL submission verdict — same "reveal after submit" shape as the
 * standalone SQL practice page (§7), reused so Duel/Tournament SQL rounds match it exactly. */
export default function SqlVerdict({ submitting, verdict }) {
  if (submitting) {
    return (
      <div className="judge-feedback judge-pending">
        <span className="judge-spinner" />
        Running query...
      </div>
    );
  }

  if (!verdict) return null;

  if (verdict.verdict === 'accepted') {
    return (
      <motion.div className="sql-result-panel" initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }}>
        <div className="judge-feedback verdict-accepted"><div className="judge-title">✓ ACCEPTED</div></div>
        <ResultTable columns={verdict.actual.columns} rows={verdict.actual.rows} />
      </motion.div>
    );
  }

  if (verdict.verdict === 'wrong_answer') {
    return (
      <div className="sql-result-panel">
        <div className="judge-feedback verdict-wrong_answer"><div className="judge-title">✗ WRONG ANSWER</div></div>
        <div className="sql-diff">
          <div><h4>Your Result</h4><ResultTable columns={verdict.actual.columns} rows={verdict.actual.rows} /></div>
          <div><h4>Expected Result</h4><ResultTable columns={verdict.expected.columns} rows={verdict.expected.rows} /></div>
        </div>
      </div>
    );
  }

  return (
    <div className="sql-error-box">
      <strong>{verdict.verdict === 'tle' ? 'Timed out' : 'SQL Error'}</strong>
      <pre>{verdict.message}</pre>
    </div>
  );
}
