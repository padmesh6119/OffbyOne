import { useEffect, useRef } from 'react';
export default function ConfirmDialog({ title, children, confirmText = 'Confirm', busy = false, onConfirm, onClose }) {
  const dialog = useRef(null);
  useEffect(() => { const d = dialog.current; d.showModal(); return () => { if (d.open) d.close(); }; }, []);
  return <dialog ref={dialog} className="confirm-dialog" aria-labelledby="confirm-title" onCancel={e => { e.preventDefault(); if (!busy) onClose(); }} onClick={e => { if (e.target === e.currentTarget && !busy) onClose(); }}><h2 id="confirm-title">{title}</h2><div className="dialog-copy">{children}</div><div className="dialog-actions"><button className="arena-secondary" disabled={busy} onClick={onClose}>Cancel</button><button className="arena-primary" disabled={busy} onClick={onConfirm}>{busy ? 'Please wait…' : confirmText}</button></div></dialog>;
}
