import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { api } from '../lib/api';

export default function Join() {
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const nav = useNavigate();

  async function submit(e) {
    e.preventDefault();
    if (!name.trim() || submitting) return;
    setSubmitting(true);
    setError('');
    try {
      const { token } = await api.join(name.trim());
      localStorage.setItem('token', token);
      localStorage.setItem('username', name.trim());
      nav('/');
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
        Code. Compete. Win.
      </motion.p>
      <motion.form
        onSubmit={submit}
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.3, ease: 'easeOut' }}
      >
        <input
          className="join-name-input"
          placeholder="Your name"
          value={name}
          onChange={e => setName(e.target.value)}
          autoFocus
          maxLength={20}
        />
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={submitting || !name.trim()}>
          {submitting ? 'Entering...' : 'Enter'}
        </button>
      </motion.form>
    </div>
  );
}
