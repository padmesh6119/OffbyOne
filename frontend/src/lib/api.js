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
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

export const api = {
  register: (body) => req('/api/auth/register', { method: 'POST', body: JSON.stringify(body) }),
  login: (body) => req('/api/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  problems: (difficulty) => req('/api/problems' + (difficulty ? `?difficulty=${difficulty}` : '')),
  problem: (slug) => req(`/api/problems/${slug}`),
  samples: (slug) => req(`/api/problems/${slug}/samples`),
  submit: (body) => req('/api/submissions', { method: 'POST', body: JSON.stringify(body) }),
  submission: (id) => req(`/api/submissions/${id}`),
  mySubmissions: () => req('/api/submissions/my'),
  createRoom: (body) => req('/api/rooms', { method: 'POST', body: JSON.stringify(body) }),
  joinRoom: (code) => req(`/api/rooms/${code}/join`, { method: 'POST' }),
  startRoom: (id) => req(`/api/rooms/${id}/start`, { method: 'POST' }),
  leaderboard: (id) => req(`/api/rooms/${id}/leaderboard`),
};
