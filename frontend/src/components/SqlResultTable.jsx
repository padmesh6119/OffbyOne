export default function SqlResultTable({ columns, rows }) {
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
