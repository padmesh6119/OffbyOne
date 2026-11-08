const keys = ['token', 'username', 'expiresAt', 'skillLevel'];
export function updateSession(values) {
  for (const key of keys) if (values[key] !== undefined) localStorage.setItem(key, String(values[key]));
  window.dispatchEvent(new Event('obo:session'));
}
export function clearSession(clearPersonal = false) {
  const name = localStorage.getItem('username');
  if (clearPersonal && name) Object.keys(localStorage).filter(k => k.startsWith(`obo:draft:${name}:`) || k === `obo:bookmarks:${name}` || k === `obo:match:${name}`).forEach(k => localStorage.removeItem(k));
  keys.forEach(k => localStorage.removeItem(k));
  window.dispatchEvent(new Event('obo:session'));
}
export function downloadFile(name, text, type = 'text/plain') {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const link = document.createElement('a'); link.href = url; link.download = name; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
