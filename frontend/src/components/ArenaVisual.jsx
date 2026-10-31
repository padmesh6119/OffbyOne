export default function ArenaVisual() {
  return <div className="arena-visual" aria-hidden="true">
    <div className="visual-caption"><span>THE MATCHUP</span><span>01 / 03</span></div>
    <svg viewBox="0 0 600 430" fill="none">
      <defs>
        <linearGradient id="orange-face" x1="150" y1="130" x2="330" y2="330" gradientUnits="userSpaceOnUse"><stop stopColor="#ff9363"/><stop offset="1" stopColor="#e84825"/></linearGradient>
        <linearGradient id="blue-face" x1="330" y1="100" x2="480" y2="300" gradientUnits="userSpaceOnUse"><stop stopColor="#a9d6ef"/><stop offset="1" stopColor="#50799e"/></linearGradient>
        <radialGradient id="arena-glow"><stop stopColor="#f66e4830"/><stop offset="1" stopColor="#f66e4800"/></radialGradient>
      </defs>
      <ellipse cx="300" cy="270" rx="290" ry="150" fill="url(#arena-glow)"/>
      <g stroke="#414146" strokeWidth=".6"><path d="M15 240 300 75 585 240 300 405Z"/><path d="M70 270 355 105M125 303 410 138M180 335 465 170M235 368 520 203M70 208 355 372M125 175 410 340M180 143 465 307M235 110 520 275"/></g>
      <ellipse cx="300" cy="260" rx="247" ry="126" stroke="#555056" strokeDasharray="3 9"/>
      <path d="M73 270C95 330 180 372 300 372M527 250C505 190 420 148 300 148" stroke="#ff6b45" strokeWidth="2"/>
      <g className="visual-tower tower-blue">
        <path d="m340 250 75-43 75 43-75 44Z" fill="#23364a" stroke="#6485a0"/>
        <path d="m340 250 75 44v16l-75-44Z" fill="#2b445a"/><path d="m415 294 75-44v16l-75 44Z" fill="#162a3b"/>
        <path d="m353 130 62-36 62 36-62 36Z" fill="#d1eafa"/><path d="m353 130 62 36v110l-62-36Z" fill="url(#blue-face)"/><path d="m415 166 62-36v110l-62 36Z" fill="#426783"/>
        <path d="m364 163 40 23m-40-4 40 23m-40-4 40 23m-40-4 23 13" stroke="#e7f7ff" strokeWidth="3" opacity=".65"/>
        <path d="m426 185 39-23m-39 42 39-23m-39 42 39-23" stroke="#93bcda" strokeWidth="2"/>
      </g>
      <g className="visual-tower tower-orange">
        <path d="m110 285 75-43 75 43-75 44Z" fill="#5b2d24" stroke="#f7774f"/>
        <path d="m110 285 75 44v16l-75-44Z" fill="#4b271f"/><path d="m185 329 75-44v16l-75 44Z" fill="#301d1c"/>
        <path d="m123 160 62-36 62 36-62 36Z" fill="#ffb18b"/><path d="m123 160 62 36v115l-62-36Z" fill="url(#orange-face)"/><path d="m185 196 62-36v115l-62 36Z" fill="#a53f2a"/>
        <path d="m134 194 40 23m-40-3 40 23m-40-3 40 23m-40-3 23 13" stroke="#ffe2c7" strokeWidth="3" opacity=".8"/>
        <path d="m196 215 39-23m-39 43 39-23m-39 43 39-23" stroke="#f48a5d" strokeWidth="2"/>
      </g>
      <path d="m282 216 35 20-35 20" stroke="#f9f5ec" strokeWidth="3"/>
      <circle cx="300" cy="262" r="4" fill="#f9f5ec"/>
      <g fill="#a09c9e" fontFamily="monospace" fontSize="9" letterSpacing="2"><text x="70" y="355">YOU</text><text x="455" y="115">OPPONENT</text></g>
    </svg>
    <div className="visual-footer"><span>SAME DATA. SAME CLOCK.</span><strong>ONE BETTER QUERY.</strong></div>
  </div>;
}
