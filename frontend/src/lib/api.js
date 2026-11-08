import { clearSession } from './session';
const BASE = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '');

function headers() {
  const token = localStorage.getItem('token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function req(path, options = {}) {
  const requestHeaders = headers();
  if (!navigator.onLine) throw new Error('You are offline. Reconnect and try again.');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 75000);
  let res;
  try { res = await fetch(BASE + path, { ...options, headers: requestHeaders, signal: controller.signal }); }
  catch (e) { throw new Error(e.name === 'AbortError' ? 'The server is taking too long. Please try again; free hosting can take a moment to wake up.' : 'Could not reach the server. Check your connection and try again.'); }
  finally { clearTimeout(timeout); }
  if (res.status === 401 && requestHeaders.Authorization === headers().Authorization) {
    const next = location.hash.slice(1) || '/';
    clearSession();
    location.replace(`${location.pathname}#/join?expired=1&next=${encodeURIComponent(next)}`);
    throw new Error('Session expired, please rejoin.');
  }
  if (!res.ok) throw new Error(await res.text());
  return res.status === 204 ? null : res.json();
}

export const api = {
  session: () => req('/api/auth/session'),
  updateSession: (skillLevel) => req('/api/auth/session', { method: 'PATCH', body: JSON.stringify({skillLevel: String(skillLevel)}) }),
  logout: () => req('/api/auth/logout', {method:'POST'}),
  arenaCreate: () => req('/api/sql-arena', { method: 'POST' }),
  arenaJoin: (code) => req(`/api/sql-arena/join/${encodeURIComponent(code)}`, { method: 'POST' }),
  arenaState: (id) => req(`/api/sql-arena/${id}`),
  arenaAction: (id, action, body = {}) => req(`/api/sql-arena/${id}/${action}`, { method: 'POST', body: JSON.stringify(body) }),
  sqlRun: (slug, query) => req(`/api/sql-problems/${slug}/run`, { method: 'POST', body: JSON.stringify({ query }) }),
  join: (username, skillLevel = 5) => req('/api/auth/join', { method: 'POST', body: JSON.stringify({ username, skillLevel: String(skillLevel) }) }),
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
  practiceHub: () => req('/api/practice-hub'),
  standings: () => req('/api/practice-hub/leaderboard'),
  streak: () => req('/api/streak'),
  dailyChallenge: () => req('/api/daily-challenge'),
};

export function errorMessage(err) {
  try { return JSON.parse(err.message).message || JSON.parse(err.message).error || err.message; }
  catch { return err.message; }
}
