import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
export default function Profile() {
  const username=localStorage.getItem('username');
  const [data,setData]=useState(null),[error,setError]=useState('');
  useEffect(()=>{let live=true;Promise.all([api.profile(username),api.rivals(username),api.streak(),api.mySubmissions()]).then(([profile,rivals,streak,attempts])=>live&&setData({profile,rivals,streak,attempts})).catch(e=>live&&setError(errorMessage(e)));return()=>{live=false;};},[username]);
  if(error)return <div className="arena-error" role="alert">{error}</div>;
  if(!data)return <div className="arena-loading">Loading your progress…</div>;
  const {profile:p,rivals,streak,attempts}=data;
  const sqlAttempts=attempts.filter(a => a.language === 'sql');
  const recent=[...sqlAttempts].sort((a,b)=>b.submittedAt.localeCompare(a.submittedAt)).slice(0,8);
  return <section className="workspace-page"><div className="page-intro"><div><div className="arena-eyebrow">YOUR PLAYER CARD</div><h1>Every attempt<br/><em>moves you forward.</em></h1><p>{username}, this is the work you have put in.</p></div><div className="profile-monogram">{username[0].toUpperCase()}<span>OFFBYONE / PLAYER</span></div></div>
    <div className="progress-stats">{[[p.solvedSql,'SQL CHALLENGES SOLVED'],[sqlAttempts.length,'SQL ATTEMPTS'],[p.wins+' – '+p.losses,'MATCH WINS / LOSSES'],[streak.currentStreak,'DAY STREAK']].map(([n,label])=><div key={label}><small>{label}</small><strong>{n}</strong></div>)}</div>
    <div className="progress-columns"><section><div className="section-heading"><h2>Your recent attempts</h2><span className="arena-eyebrow">KEEP SHOWING UP</span></div>{recent.length?<div className="activity-list">{recent.map(a=><div className="activity-row" key={a.id}><span className={'activity-mark '+(a.verdict==='accepted'?'passed':'')}>{a.verdict==='accepted'?'✓':'↗'}</span><div><strong>{a.problemTitle}</strong><small>{a.language.toUpperCase()} · {new Date(a.submittedAt).toLocaleString()}</small></div><span className={a.verdict==='accepted'?'accepted-label':'attempt-label'}>{a.verdict.replaceAll('_',' ')}</span></div>)}</div>:<div className="workspace-empty"><strong>Your first attempt belongs here.</strong><p>Pick a challenge and make your first move.</p><Link className="arena-primary" to="/">Enter the arena ↗</Link></div>}</section><aside className="arena-playbook"><div className="arena-eyebrow">FAMILIAR COMPETITION</div><h3>Your rivals.</h3>{rivals.length?rivals.map(r=><div className="rival-row" key={r.username}><strong>{r.username}</strong><span>{r.wins} W · {r.losses} L</span></div>):<p className="workspace-copy">The best rivals make you better. Invite a friend and your match history will appear here.</p>}<div className="playbook-note">Longest streak: {streak.longestStreak} days<br/>{streak.solvedToday?'You have solved a challenge today.':'A fresh solve keeps your streak going.'}<br/>SQL arena matches are friendly and do not change Elo.</div><Link className="arena-secondary" to="/">Challenge a friend ↗</Link></aside></div>
  </section>;
}
