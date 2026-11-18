import { useState } from 'react';
import { downloadFile } from '../lib/session';
export default function PracticeNotes({ problem }) {
  const key = `obo:notes:${localStorage.getItem('username')}:${problem.slug}`;
  const [text,setText] = useState(() => localStorage.getItem(key) || ''), [error,setError] = useState(''), [copied,setCopied] = useState(false);
  const url = `${location.origin}${location.pathname}#/?challenge=${encodeURIComponent(problem.slug)}`;
  function edit(value) { setText(value); try { if(value) localStorage.setItem(key,value); else localStorage.removeItem(key);setError(''); } catch { setError('Your browser could not save these notes. Download a copy before leaving.'); } }
  async function share() { try { await navigator.clipboard.writeText(url);setCopied(true); } catch { setCopied(false);setError('Clipboard unavailable. Copy the challenge link below.'); } }
  return <section className="practice-notes"><details><summary>My notes <span>{text ? 'Saved in this browser' : 'Keep what you learn'}</span></summary><label className="sr-only" htmlFor="challenge-notes">Private notes for this challenge</label><textarea id="challenge-notes" maxLength={8000} value={text} onChange={e=>edit(e.target.value)} placeholder="What clicked? What would you try differently next time?"/><div><small>{text.length}/8,000 · Private to this guest pass in this browser.</small><button className="arena-secondary" disabled={!text} onClick={()=>downloadFile(`${problem.slug}-notes.txt`,`${problem.title}\n${url}\n\n${text}`)}>Download notes ↓</button></div></details><details className="challenge-share"><summary>Challenge a friend to solve this ↗</summary><p>Share the challenge, without your notes or query.</p><input aria-label="Shareable challenge link" value={url} readOnly onFocus={e=>e.target.select()}/><button className="arena-secondary" onClick={share}>{copied?'Link copied ✓':'Copy challenge link'}</button></details>{error&&<p role="alert">{error}</p>}</section>;
}
