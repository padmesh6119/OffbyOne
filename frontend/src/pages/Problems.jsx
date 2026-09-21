import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';

export default function Problems() {
  const [problems, setProblems] = useState([]);
  const [filter, setFilter] = useState('');

  useEffect(() => {
    api.problems(filter || null).then(setProblems);
  }, [filter]);

  return (
    <div className="page">
      <h2>Problems</h2>
      <select value={filter} onChange={e => setFilter(e.target.value)}>
        <option value="">All</option>
        <option value="easy">Easy</option>
        <option value="medium">Medium</option>
        <option value="hard">Hard</option>
      </select>
      <table>
        <thead><tr><th>Title</th><th>Difficulty</th></tr></thead>
        <tbody>
          {problems.map(p => (
            <tr key={p.id}>
              <td><Link to={`/problem/${p.slug}`}>{p.title}</Link></td>
              <td className={`diff-${p.difficulty}`}>{p.difficulty}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
