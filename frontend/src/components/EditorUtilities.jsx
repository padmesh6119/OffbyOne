import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { downloadFile } from '../lib/session';
import ConfirmDialog from './ConfirmDialog';
export default function EditorUtilities({ query, slug, onChange, starter, disabled }) {
  const input = useRef(null), [pending, setPending] = useState(null), [error, setError] = useState('');
  async function importFile(e) {
    const file = e.target.files[0]; e.target.value = ''; setError('');
    if (!file) return;
    if (file.size > 16000 || !/\.(sql|txt)$/i.test(file.name)) { setError('Choose a .sql or .txt file up to 16 KB.'); return; }
    try { const text = await file.text(); setPending({ text, title: 'Replace your query with this file?' }); }
    catch { setError('This file could not be read. Please try another file.'); }
  }
  return <><div className="editor-utilities"><div><button disabled={disabled} onClick={() => input.current.click()}>Import .sql</button><button disabled={!query.trim()} onClick={() => downloadFile(`${slug}.sql`, query)}>Download .sql</button><button disabled={disabled} onClick={() => setPending({ text: starter, title: 'Reset your editor?' })}>Reset</button><Link to="/help">Shortcuts ↗</Link></div><input type="file" accept=".sql,.txt" aria-label="Import SQL file" ref={input} onChange={importFile} hidden/></div>{error && <p className="editor-file-error" role="alert">{error}</p>}{pending && <ConfirmDialog title={pending.title} confirmText="Replace query" onClose={() => setPending(null)} onConfirm={() => { onChange(pending.text); setPending(null); }}><p>This replaces the open editor and its browser draft. Your previously submitted queries remain in My Progress.</p><p>Download your current query first if you want to keep a copy.</p></ConfirmDialog>}</>;
}
