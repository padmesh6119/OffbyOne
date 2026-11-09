// Original bracket sculpture. Decorative only; it never implies a live match.
export default function ArenaVisual() {
  return <div className="match-art" aria-hidden="true"><svg viewBox="0 0 420 420" fill="none">
    <g className="sculpture-grid" stroke="currentColor" strokeWidth=".7"><path d="M0 105 210 0l210 105v210L210 420 0 315Zm0 0 210 105 210-105M0 315l210-105 210 105M210 0v420M0 210l210 105 210-105M105 52v315m210-315v315"/><circle cx="210" cy="210" r="158"/></g>
    <g className="sculpture-body">
      <path d="m68 157 91-56v53l-47 29v92l-44-26Z" fill="#718d35"/><path d="m68 157 44 26v92l47-29v54l-91-51Z" fill="#b3dc5b"/><path d="m68 157 91-56 44 26-91 56Z" fill="#e5ffae"/><path d="m112 183 91-56v53l-47 29v37l-44 29Z" fill="#d8fc86"/><path d="m112 275 47-29 44 26-44 28Z" fill="#819d43"/>
      <path d="m352 263-91 56v-53l47-29v-92l44 26Z" fill="#718d35"/><path d="m352 263-44-26v-92l-47 29v-54l91 51Z" fill="#b3dc5b"/><path d="m352 263-91 56-44-26 91-56Z" fill="#a4ce53"/><path d="m308 237-91 56v-53l47-29v-37l44-29Z" fill="#d8fc86"/><path d="m308 145-47 29-44-26 44-28Z" fill="#e5ffae"/>
      <path d="m222 170 29 17-51 111-29-17Z" fill="#d8fc86"/><path d="m222 170 12-7 29 17-12 7Z" fill="#f1ffd9"/><path d="m251 187 12-7-51 111-12 7Z" fill="#78983d"/>
    </g>
    <g className="sculpture-points" fill="#d8fc86"><circle cx="105" cy="52" r="4"/><circle cx="315" cy="367" r="4"/><path d="M369 88h14m-7-7v14M34 328h14m-7-7v14" stroke="#d8fc86"/></g>
  </svg></div>;
}
