import { motion } from 'framer-motion';

/** Renders the full judge verdict per UI-SPEC.md §8 — while-judging spinner, accepted banner,
 * wrong-answer sample diff, compile/runtime error blocks, TLE. Shared by Duel and Tournament screens. */
export default function JudgeFeedback({ submitting, progress, verdict }) {
  if (submitting) {
    return (
      <div className="judge-feedback judge-pending">
        <span className="judge-spinner" />
        {progress ? `Running test ${progress.current} of ${progress.total}...` : 'Judging...'}
      </div>
    );
  }

  if (!verdict) return null;

  if (verdict.verdict === 'accepted') {
    return (
      <motion.div className="judge-feedback verdict-accepted" initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }}>
        <div className="judge-title">✓ ACCEPTED <span className="judge-runtime">{verdict.runtimeMs}ms</span></div>
        <div className="judge-sub">Passed all {verdict.totalTests} test case{verdict.totalTests === 1 ? '' : 's'}</div>
      </motion.div>
    );
  }

  if (verdict.verdict === 'wrong_answer') {
    return (
      <div className="judge-feedback verdict-wrong_answer">
        <div className="judge-title">✗ WRONG ANSWER {verdict.totalTests ? `(test ${verdict.testIndex} of ${verdict.totalTests})` : ''}</div>
        {verdict.sampleFailed ? (
          <div className="judge-detail">
            <div>Input: <code>{verdict.sampleFailed.input}</code></div>
            <div>Expected: <code>{verdict.sampleFailed.expected}</code></div>
            <div>Your output: <code>{verdict.sampleFailed.got}</code></div>
          </div>
        ) : (
          <div className="judge-sub">{verdict.message}</div>
        )}
      </div>
    );
  }

  if (verdict.verdict === 'ce') {
    return (
      <div className="judge-feedback verdict-ce">
        <div className="judge-title">⚠ COMPILE ERROR</div>
        <pre className="judge-code-block">{verdict.message}</pre>
      </div>
    );
  }

  if (verdict.verdict === 'tle') {
    return (
      <div className="judge-feedback verdict-tle">
        <div className="judge-title">⏱ TIME LIMIT EXCEEDED</div>
        <div className="judge-sub">{verdict.message}</div>
      </div>
    );
  }

  if (verdict.verdict === 'mle') {
    return (
      <div className="judge-feedback verdict-mle">
        <div className="judge-title">💾 MEMORY LIMIT EXCEEDED</div>
        {verdict.message && <div className="judge-sub">{verdict.message}</div>}
      </div>
    );
  }

  // re / error
  return (
    <div className="judge-feedback verdict-re">
      <div className="judge-title">💥 RUNTIME ERROR</div>
      <pre className="judge-code-block">{verdict.message}</pre>
    </div>
  );
}
