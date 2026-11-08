import { createContext, useContext, useEffect, useState } from 'react';
const defaults = { fontSize: 14, wordWrap: true, lineNumbers: true, reducedMotion: false };
const Preferences = createContext(null);
function read() { try { return { ...defaults, ...JSON.parse(localStorage.getItem('obo:preferences') || '{}') }; } catch { return defaults; } }
export function PreferencesProvider({ children }) {
  const [preferences, setPreferences] = useState(read);
  useEffect(() => {
    localStorage.setItem('obo:preferences', JSON.stringify(preferences));
    document.documentElement.dataset.motion = preferences.reducedMotion ? 'reduced' : 'normal';
  }, [preferences]);
  useEffect(() => { const sync = e => { if (e.key === 'obo:preferences') setPreferences(read()); }; window.addEventListener('storage', sync); return () => window.removeEventListener('storage', sync); }, []);
  return <Preferences.Provider value={{ preferences, update: value => setPreferences(p => ({ ...p, ...value })), reset: () => setPreferences(defaults) }}>{children}</Preferences.Provider>;
}
export const usePreferences = () => useContext(Preferences);
