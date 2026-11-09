import { useEffect, useRef } from 'react';
import { Link, useLocation } from 'react-router-dom';
import BrandMark from '../components/BrandMark';
import '../creators.css';

// Replace photo with a local image URL and edit the names when profiles are ready.
const creators = [
  { id: 'padmesh', name: 'Padmesh', initial: 'P', role: 'Developer & co-creator', photo: null },
  { id: 'friend', name: 'Your friend', initial: '?', role: 'Developer & co-creator', photo: null },
];

function PortraitPlaceholder({ initial }) {
  return <div className="creator-portrait" aria-label="Profile photo placeholder">
    <svg viewBox="0 0 320 235" fill="none" aria-hidden="true">
      <circle cx="160" cy="116" r="89" stroke="currentColor" strokeOpacity=".16"/>
      <circle cx="160" cy="116" r="69" stroke="currentColor" strokeOpacity=".15" strokeDasharray="3 8"/>
      <path d="m82 93-17 23 17 23m156-46 17 23-17 23" stroke="currentColor" strokeOpacity=".45" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
      <path d="M113 188c4-27 20-42 47-42s43 15 47 42" fill="currentColor" fillOpacity=".06" stroke="currentColor" strokeOpacity=".22"/>
      <circle cx="160" cy="102" r="33" fill="currentColor" fillOpacity=".07" stroke="currentColor" strokeOpacity=".3"/>
      <text x="160" y="114" textAnchor="middle" fill="currentColor" fontFamily="sans-serif" fontSize="31">{initial}</text>
      <path d="M43 43h14m-7-7v14m213 144h14m-7-7v14" stroke="currentColor" strokeOpacity=".4"/>
    </svg>
    <span>Photo coming soon</span>
  </div>;
}

export default function Creators() {
  const location = useLocation(), heading = useRef(null);
  const from = location.state?.from;
  const back = typeof from === 'string' && /^\/(?:\?|$|join(?:\?|$)|profile(?:\?|$)|settings(?:\?|$)|help(?:\?|$)|leaderboard(?:\?|$))/.test(from)
    ? from : localStorage.getItem('token') ? '/' : '/join';
  useEffect(() => { window.scrollTo(0, 0); heading.current?.focus({ preventScroll: true }); }, []);
  return <main className="sql-app creators-page">
    <div className="creator-aurora" aria-hidden="true"><div className="aurora-orb aurora-moss"/><div className="aurora-orb aurora-violet"/><div className="aurora-orb aurora-amber"/><div className="aurora-grain"/></div>
    <div className="creators-content">
      <header className="creators-header"><Link to={back} className="workspace-brand"><span className="brand-symbol"><BrandMark/></span>offbyone.</Link><Link className="creators-back" to={back}>← Back to {back.startsWith('/join') ? 'start' : 'OffbyOne'}</Link></header>
      <section className="creators-intro"><span className="secret-found">✦ You found us.</span><h1 ref={heading} tabIndex="-1">Behind the <em>code.</em></h1><p>Two friends. One idea. A place to make every query count.</p></section>
      <div className="creator-grid">{creators.map((creator, index) => <article className={`creator-card creator-card-${index + 1}`} key={creator.id}>
        {creator.photo ? <img className="creator-photo" src={creator.photo} alt={creator.name}/> : <PortraitPlaceholder initial={creator.initial}/>}
        <div className="creator-info"><span className="creator-role">{creator.role}</span><h2>{creator.name}</h2><p>Profile coming soon.</p></div>
      </article>)}</div>
      <footer className="creators-footer"><span>Made with curiosity. Built for yours.</span><Link to={back}>Back to the queries ↗</Link></footer>
    </div>
  </main>;
}
