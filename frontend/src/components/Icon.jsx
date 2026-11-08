export default function Icon({ name = 'arena', size = 20 }) {
  const paths = {
    arena: <><path d="m4 3 8 8-3 3-8-8V3h3Zm16 0-8 8m3 3 6-8V3h-3M5 14l5 5m-8 3 6-6m6 3 5-5m-3 2 6 6"/></>,
    progress: <><path d="M4 20V10m8 10V4m8 16v-7M2 21h20"/></>,
    trophy: <><path d="M8 3h8v6a4 4 0 0 1-8 0V3Zm0 2H4v3a4 4 0 0 0 4 4m8-7h4v3a4 4 0 0 1-4 4m-4 1v5m-5 3h10m-9 0 1-3h6l1 3"/></>,
    settings: <><path d="M4 7h16M4 17h16"/><circle cx="9" cy="7" r="3"/><circle cx="16" cy="17" r="3"/></>,
    help: <><circle cx="12" cy="12" r="9"/><path d="M9 9a3 3 0 0 1 6 0c0 2-3 2-3 4m0 3v.1"/></>,
    book: <><path d="M12 6C9 3 5 3 2 4v15c4-1 7-1 10 2 3-3 6-3 10-2V4c-3-1-7-1-10 2Zm0 0v15"/></>,
    code: <path d="m7 6-6 6 6 6m10-12 6 6-6 6M14 3l-4 18"/>,
    layers: <><path d="m12 3 10 6-10 6L2 9l10-6ZM2 13l10 6 10-6M2 17l10 6 10-6"/></>,
    join: <><circle cx="8" cy="12" r="6"/><circle cx="16" cy="12" r="6"/></>,
    chart: <><path d="M3 3v18h18M7 15l4-5 4 3 6-8"/></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] || paths.code}</svg>;
}
