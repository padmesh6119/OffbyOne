import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import { api } from '../lib/api';

export default function Join() {
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const nav = useNavigate();
  const location = useLocation();

  async function submit(e) {
    e.preventDefault();
    if (!name.trim() || submitting) return;
    setSubmitting(true);
    setError('');
    try {
      const { token } = await api.join(name.trim());
      localStorage.setItem('token', token);
      localStorage.setItem('username', name.trim());
      nav(location.state?.from || '/sql');
    } catch (err) {
      setError(err.message);
      setSubmitting(false);
    }
  }

  return (
    <div className="join-screen">
      <motion.h1
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: 'easeOut' }}
      >
        OffByOne
      </motion.h1>
      <motion.p
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.15, ease: 'easeOut' }}
      >
        Your next rivalry starts here.
      </motion.p>
      <motion.form
        onSubmit={submit}
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.3, ease: 'easeOut' }}
      >
        <input
          className="join-name-input"
          aria-label="Guest name"
          placeholder="Choose a guest name"
          value={name}
          onChange={e => setName(e.target.value)}
          autoFocus
          maxLength={20}
        />
        <small className="hint">Guest progress stays with this browser session. Choose a unique name.</small>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={submitting || !name.trim()}>
          {submitting ? 'Entering...' : 'Enter'}
        </button>
      </motion.form>
    </div>
  );
}
