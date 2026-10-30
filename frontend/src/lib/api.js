const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

function headers() {
  const token = localStorage.getItem('token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function req(path, options = {}) {
  const res = await fetch(BASE + path, { ...options, headers: headers() });
  if (res.status === 401) {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    location.href = '/#/join';
    throw new Error('Session expired, please rejoin.');
  }
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export const api = {
  arenaCreate: () => req('/api/sql-arena', { method: 'POST' }),
  arenaJoin: (code) => req(`/api/sql-arena/join/${encodeURIComponent(code)}`, { method: 'POST' }),
  arenaState: (id) => req(`/api/sql-arena/${id}`),
  arenaAction: (id, action, body = {}) => req(`/api/sql-arena/${id}/${action}`, { method: 'POST', body: JSON.stringify(body) }),
  sqlRun: (slug, query) => req(`/api/sql-problems/${slug}/run`, { method: 'POST', body: JSON.stringify({ query }) }),
  join: (username) => req('/api/auth/join', { method: 'POST', body: JSON.stringify({ username }) }),
  problems: (difficulty) => req('/api/problems' + (difficulty ? `?difficulty=${difficulty}` : '')),
  problem: (slug) => req(`/api/problems/${slug}`),
  samples: (slug) => req(`/api/problems/${slug}/samples`),
  submit: (body) => req('/api/submissions', { method: 'POST', body: JSON.stringify(body) }),
  submission: (id) => req(`/api/submissions/${id}`),
  mySubmissions: () => req('/api/submissions/my'),
  createRoom: (body) => req('/api/rooms', { method: 'POST', body: JSON.stringify(body) }),
  joinRoom: (code) => req(`/api/rooms/${code}/join`, { method: 'POST' }),
  startRoom: (id) => req(`/api/rooms/${id}/start`, { method: 'POST' }),
  leaveRoom: (id) => req(`/api/rooms/${id}/leave`, { method: 'POST' }),
  leaderboard: (id) => req(`/api/rooms/${id}/leaderboard`),
  roomState: (id) => req(`/api/rooms/${id}/state`),
  sqlProblems: () => req('/api/sql-problems'),
  sqlProblem: (slug) => req(`/api/sql-problems/${slug}`),
  sqlSubmit: (slug, query) => req(`/api/sql-problems/${slug}/submit`, { method: 'POST', body: JSON.stringify({ query }) }),
  profile: (username) => req(`/api/users/${username}/profile`),
  rivals: (username) => req(`/api/users/${username}/rivals`),
  streak: () => req('/api/streak'),
  dailyChallenge: () => req('/api/daily-challenge'),
};

export function errorMessage(err) {
  try { return JSON.parse(err.message).message || JSON.parse(err.message).error || err.message; }
  catch { return err.message; }
}
