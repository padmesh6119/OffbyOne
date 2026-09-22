/** Minimal hand-rolled line chart — no charting library needed for a single series like this. */
export default function RatingSparkline({ history }) {
  if (!history || history.length < 2) {
    return <p className="hint">Play a few tournaments to build a rating history.</p>;
  }
  const ratings = history.map((h) => h.rating);
  const min = Math.min(...ratings), max = Math.max(...ratings);
  const range = Math.max(1, max - min);
  const w = 320, h = 80, pad = 8;

  const points = ratings.map((r, i) => {
    const x = pad + (i / (ratings.length - 1)) * (w - pad * 2);
    const y = h - pad - ((r - min) / range) * (h - pad * 2);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');

  return (
    <svg viewBox={`0 0 ${w} ${h}`} className="rating-sparkline" preserveAspectRatio="none">
      <polyline points={points} fill="none" stroke="var(--color-primary)" strokeWidth="2" />
    </svg>
  );
}
