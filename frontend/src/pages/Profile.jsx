import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
import { PlayerLevel } from '../components/PracticeHub';
import Reveal from '../components/Reveal';
import SubmissionHistory from '../components/SubmissionHistory';
export default function Profile() {
  const username=localStorage.getItem('username');
  const [data,setData]=useState(null),[error,setError]=useState('');
  useEffect(()=>{let live=true;Promise.all([api.profile(username),api.rivals(username),api.streak(),api.mySubmissions(),api.practiceHub()]).then(([profile,rivals,streak,attempts,hub])=>live&&setData({profile,rivals,streak,attempts,hub})).catch(e=>live&&setError(errorMessage(e)));return()=>{live=false;};},[username]);
  if(error)return <div className="arena-error" role="alert">{error}</div>;
  if(!data)return <div className="arena-loading">Loading your progress…</div>;
  const {profile:p,rivals,streak,attempts,hub}=data;
  const sqlAttempts=attempts.filter(a => a.language === 'sql');
  return <section className="workspace-page"><div className="page-intro"><div><h1>Every attempt<br/><em>moves you forward.</em></h1><p>{username}, this is the work you have put in.</p></div><div className="profile-monogram">{username[0].toUpperCase()}<span>OFFBYONE / PLAYER</span></div></div>
    <PlayerLevel data={hub}/>
    <section className="achievement-section"><div className="section-heading"><h2>A little proof of progress.</h2><span className="hub-muted">{hub.badges.filter(b=>b.unlocked).length} / {hub.badges.length} unlocked</span></div><div className="achievement-grid">{hub.badges.map((b,i)=><Reveal as="article" delay={(i % 3) * 60} className={`achievement ${b.unlocked?'unlocked':''}`} key={b.id}><span className="achievement-icon" aria-hidden="true">{b.unlocked?['✦','Ⅴ','Ⅹ','◎','⋈','☀','◈','✺','⌁','↗','▲','✳','✧','✶','✹'][i % 15]:'◇'}</span><div><small>{b.unlocked?'UNLOCKED':'IN PROGRESS'}</small><h3>{b.title}</h3><p>{b.description}</p></div></Reveal>)}</div></section>
    <div className="progress-stats">{[[p.solvedSql,'SQL CHALLENGES SOLVED'],[sqlAttempts.length,'SQL ATTEMPTS'],[p.wins+' – '+p.losses,'MATCH WINS / LOSSES'],[streak.currentStreak,'DAY STREAK']].map(([n,label])=><div key={label}><small>{label}</small><strong>{n}</strong></div>)}</div>
    <div className="progress-columns"><SubmissionHistory attempts={sqlAttempts}/><aside className="arena-playbook"><h3>Your rivals.</h3>{rivals.length?rivals.map(r=><div className="rival-row" key={r.username}><strong>{r.username}</strong><span>{r.wins} W · {r.losses} L</span></div>):<p className="workspace-copy">The best rivals make you better. Invite a friend and your match history will appear here.</p>}<div className="playbook-note">Longest streak: {streak.longestStreak} days<br/>{streak.solvedToday?'You have solved a challenge today.':'A fresh solve keeps your streak going.'}<br/>SQL arena matches are friendly and do not change Elo.</div><Link className="arena-secondary" to="/">Challenge a friend ↗</Link><div className="progress-export"><h4>Take your progress with you.</h4><p>Your stats belong to this 24 hour guest pass. Download a personal record before it expires. This snapshot cannot extend or restore a pass.</p><button className="arena-secondary" onClick={()=>{
      const snapshot={username,exportedAt:new Date().toISOString(),...hub,profile:p,attempts:sqlAttempts};
      const url=URL.createObjectURL(new Blob([JSON.stringify(snapshot,null,2)],{type:'application/json'}));
      const a=document.createElement('a');a.href=url;a.download=`offbyone-${username}-progress.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
    }}>Download progress ↓</button></div></aside></div>
  </section>;
}
