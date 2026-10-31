import { createContext, useContext, useEffect, useState } from 'react';
const Theme = createContext(null);
export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => localStorage.getItem('obo:theme') === 'light' ? 'light' : 'dark');
  useEffect(() => { document.documentElement.dataset.theme = theme; localStorage.setItem('obo:theme', theme); }, [theme]);
  return <Theme.Provider value={{ theme, toggle: () => setTheme(t => t === 'dark' ? 'light' : 'dark') }}>{children}</Theme.Provider>;
}
export const useTheme = () => useContext(Theme);
export function ThemeToggle() {
  const { theme, toggle } = useTheme();
  return <button className="theme-toggle" onClick={toggle} aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}>{theme === 'dark' ? '☀ Light' : '☾ Dark'}</button>;
}
