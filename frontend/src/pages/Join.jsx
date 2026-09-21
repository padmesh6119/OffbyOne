import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../lib/api';

export default function Join() {
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const nav = useNavigate();

  async function submit(e) {
    e.preventDefault();
    if (!name.trim()) return;
    try {
      const { token } = await api.join(name.trim());
      localStorage.setItem('token', token);
      localStorage.setItem('username', name.trim());
      nav('/');
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="join-screen">
      <h1>OffByOne</h1>
      <p>Competitive coding. Enter your name to play.</p>
      <form onSubmit={submit}>
        <input
          placeholder="Your name"
          value={name}
          onChange={e => setName(e.target.value)}
          autoFocus
          maxLength={20}
        />
        {error && <p className="error">{error}</p>}
        <button type="submit">Enter</button>
      </form>
    </div>
  );
}
