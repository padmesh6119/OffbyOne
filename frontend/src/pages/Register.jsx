import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { api } from '../lib/api';

export default function Register() {
  const [form, setForm] = useState({ username: '', email: '', password: '' });
  const [error, setError] = useState('');
  const nav = useNavigate();

  async function submit(e) {
    e.preventDefault();
    try {
      const { token } = await api.register(form);
      localStorage.setItem('token', token);
      nav('/');
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="auth-container">
      <h2>Register</h2>
      <form onSubmit={submit}>
        <input placeholder="Username" value={form.username}
          onChange={e => setForm({ ...form, username: e.target.value })} />
        <input placeholder="Email" type="email" value={form.email}
          onChange={e => setForm({ ...form, email: e.target.value })} />
        <input placeholder="Password" type="password" value={form.password}
          onChange={e => setForm({ ...form, password: e.target.value })} />
        {error && <p className="error">{error}</p>}
        <button type="submit">Create Account</button>
      </form>
      <p>Have account? <Link to="/login">Login</Link></p>
    </div>
  );
}
