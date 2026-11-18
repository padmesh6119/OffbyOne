import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
import { downloadFile } from '../lib/session';
import ResultTable from '../components/SqlResultTable';
import ConfirmDialog from '../components/ConfirmDialog';
import EditorUtilities from '../components/EditorUtilities';
const SqlEditor = lazy(() => import('../components/SqlEditor'));
const starter = '-- Your next clear starts here.\nSELECT\n  \nFROM ';
const time = seconds => `${Math.floor(seconds / 60).toString().padStart(2,'0')}:${(seconds % 60).toString().padStart(2,'0')}`;

export default function Sprint() {
  const [params,setParams] = useSearchParams(), id = params.get('id');
  const [run,setRun] = useState(null),[history,setHistory] = useState([]),[historyLoading,setHistoryLoading] = useState(true);
  const [minutes,setMinutes] = useState(5),[difficulty,setDifficulty] = useState('easy'),[busy,setBusy] = useState(''),[error,setError] = useState('');
  const [query,setQuery] = useState(starter),[result,setResult] = useState(null),[now,setNow] = useState(Date.now()),[offset,setOffset] = useState(0),[confirm,setConfirm] = useState(null),[retry,setRetry] = useState(0);
  const pending = useRef(false),generation = useRef(0);
  const active = run?.status === 'active', problem = run?.problem;
  const draftKey = problem ? `obo:draft:${localStorage.getItem('username')}:sprint-${id}:${problem.slug}` : null;
  const update = useCallback(data => { setRun(data); setOffset(Date.parse(data.serverTime) - Date.now()); },[]);
  useEffect(() => { let live=true;setHistoryLoading(true);api.sprints().then(h=>live&&setHistory(h)).catch(e=>live&&setError(errorMessage(e))).finally(()=>live&&setHistoryLoading(false));return()=>{live=false;}; },[id,run?.status,retry]);
  useEffect(() => {
    setRun(null);setResult(null);setError('');generation.current++;let live=true,timer;
    if (!id) return;
    async function poll() {
      if (!live) return;
      const version=generation.current;
      try { if (!pending.current) {const data=await api.sprintState(id);if(live&&version===generation.current) update(data);} }
      catch(e){if(live)setError(errorMessage(e));}
      if(live)timer=setTimeout(poll,3000);
    }
    poll();return()=>{live=false;clearTimeout(timer);};
  },[id,retry,update]);
  useEffect(()=>{const t=setInterval(()=>setNow(Date.now()),250);return()=>clearInterval(t);},[]);
  useEffect(()=>{setQuery(draftKey ? localStorage.getItem(draftKey) || starter : starter);},[draftKey]);
  function edit(value){setQuery(value||'');try{if(draftKey)localStorage.setItem(draftKey,value||'');}catch{setError('Your draft could not be saved. Download your query before leaving.');}}
  async function action(label,fn){if(pending.current)return;pending.current=true;generation.current++;setBusy(label);setError('');try{await fn();}catch(e){setError(errorMessage(e));setRetry(n=>n+1);}finally{pending.current=false;generation.current++;setBusy('');}}
  async function execute(submit=false){if(!active || !query.trim() || seconds===0)return;const title=problem.title;await action(submit?'Submitting':'Running',async()=>{
    if(!submit)setResult({...await api.sqlRun(problem.slug,query),title});
    else{const response=await api.sprintAction(id,'submit',{position:run.position,query});setResult({...response.result,title});update(response.sprint);}
  });}
  const seconds=run ? Math.max(0,Math.ceil((Date.parse(run.deadline)-now-offset)/1000)):0;
  const comparable=history.filter(h=>h.status==='finished'&&h.minutes===(run?.minutes||minutes)&&h.difficulty===(run?.difficulty||difficulty)&&h.id!==run?.id);
  const best=Math.max(0,...comparable.map(h=>h.score));
  const ongoing=history.find(h=>h.status==='active');
  const newBest=run?.status==='finished' && run.score>best;
  return <section className="sprint-page">
    {error && <div className="arena-error" role="alert">{error}<button onClick={()=>setRetry(n=>n+1)}>Retry</button></div>}
    {!id && <>
      <div className="arena-welcome"><h1>Race your own record<span>.</span></h1><Link className="arena-text-link" to="/training">← Training</Link></div>
      {ongoing && <div className="resume-match"><div><strong>Your sprint is still running.</strong><p>The clock continues while you’re away.</p></div><button className="arena-primary" onClick={()=>setParams({id:ongoing.id})}>Resume sprint →</button></div>}
      <div className="sprint-setup"><div><div className="sprint-orbit" aria-hidden="true"><span>↗</span></div><h2>Find your fast.</h2><p>A fresh set of up to 20 challenges. No repeats. Stay at your chosen difficulty and chase your personal best.</p><ul><li>100 points per solve, plus 50 for a first-submission clear.</li><li>Run samples freely. Skip when you’re stuck.</li><li>The clock keeps going through refreshes and closed tabs.</li></ul></div><div className="sprint-options"><fieldset disabled={!!busy||!!ongoing}><legend>Time on the clock</legend><div>{[5,10,15].map(n=><button className="arena-secondary" aria-pressed={minutes===n} key={n} onClick={()=>setMinutes(n)}>{n} minutes</button>)}</div></fieldset><fieldset disabled={!!busy||!!ongoing}><legend>Your difficulty</legend><div>{['easy','medium','hard'].map(d=><button className="arena-secondary" aria-pressed={difficulty===d} key={d} onClick={()=>setDifficulty(d)}>{d}</button>)}</div></fieldset><p className="sprint-level-note">{difficulty==='easy'?'One table, filters, sorting and simple totals.':difficulty==='medium'?'Grouping, joins and subqueries.':'Window functions and layered analysis.'}</p><div className="sprint-personal-best"><span>Best for this format</span><strong>{Math.max(0,...history.filter(h=>h.status==='finished'&&h.minutes===minutes&&h.difficulty===difficulty).map(h=>h.score))}<small>pts</small></strong></div><button className="arena-primary" disabled={!!busy||!!ongoing||historyLoading} onClick={()=>action('Starting',async()=>{const data=await api.sprintCreate(minutes,difficulty);setParams({id:data.id});})}>{busy==='Starting'?'Starting…':'Start sprint →'}</button></div></div>
      <section className="training-section"><div className="section-heading"><h2>Your recent runs.</h2><span>Current guest pass</span></div>{historyLoading?<p role="status">Loading runs…</p>:history.length?<div className="sprint-history">{history.map(h=><button key={h.id} onClick={()=>setParams({id:h.id})}><span>{h.minutes} min · {h.difficulty}<small>{new Date(h.startedAt).toLocaleString()}</small></span><strong>{h.score}<small>pts</small></strong><span>{h.solved} cleared · {h.status==='active'?'Resume →':'Review ↗'}</span></button>)}</div>:<div className="training-clear"><h3>Your first record is waiting.</h3></div>}</section>
    </>}
    {id&&!run&&<div className="arena-loading" role="status">Opening your sprint… <Link to="/sprint">Back to sprints</Link></div>}
    {run&&active&&problem&&<>
      <div className="workspace-heading"><div><Link className="arena-text-link" to="/sprint">← Sprint hub</Link><h1>Keep your momentum.</h1></div><div className={`arena-clock ${seconds<30?'urgent':''}`}><span>{seconds?'Time remaining':'Finishing…'}</span><strong>{time(seconds)}</strong></div></div>
      <div className="sprint-scorebar"><span><strong>{run.score}</strong> points</span><span><strong>{run.solved}</strong> cleared</span><span>Challenge <strong>{run.position+1}/{run.total}</strong></span><span>{run.minutes} min · {run.difficulty}</span><button className="arena-text-link" disabled={!!busy} onClick={()=>setConfirm('finish')}>Finish early</button></div>
      <section className="arena-workspace"><div className="challenge-panel"><div className="panel-title"><span>Challenge</span><span className={`arena-difficulty ${problem.difficulty}`}>{problem.difficulty}</span></div><div className="challenge-description"><h2>{problem.title}</h2><p>{problem.task}</p><div className="query-rules">Columns in the requested order. Aliases are flexible.</div></div>{problem.tables.map(t=><details className="schema-card" key={t.name} open><summary>{t.name}<span>{t.columns.length} columns</span></summary><ResultTable columns={t.columns} rows={t.rows}/></details>)}<details className="schema-ddl"><summary>Table definitions</summary><pre>{problem.schema.join(';\n\n')};</pre></details></div>
      <div className="query-panel"><div className="panel-title"><span>solution.sql</span><span>SQLite</span></div><EditorUtilities query={query} slug={problem.slug} starter={starter} onChange={edit} disabled={!!busy||!seconds}/><div className="editor-wrap"><Suspense fallback={<p className="arena-loading">Loading editor…</p>}><SqlEditor value={query} onChange={edit} onRun={execute} readOnly={!!busy||!seconds}/></Suspense></div><div className="editor-actions"><button className="arena-text-link" disabled={!!busy||!seconds} onClick={()=>setConfirm('skip')}>Skip challenge</button><button className="arena-secondary" disabled={!!busy||!seconds||!query.trim()} onClick={()=>execute(false)}>{busy==='Running'?'Running…':'Run sample'}</button><button className="arena-primary" disabled={!!busy||!seconds||!query.trim()} onClick={()=>execute(true)}>{busy==='Submitting'?'Judging…':'Submit query →'}</button></div>
      <div className="output-panel"><div className="panel-title">Results</div>{result?<div className={`arena-verdict ${['accepted','sample_passed'].includes(result.status)?'pass':'fail'}`} role="status"><strong>{result.status.replaceAll('_',' ')} · {result.title}</strong><p>{result.message}</p>{result.status==='accepted'&&<p>Your next challenge is ready above.</p>}{result.actualColumns&&<ResultTable columns={result.actualColumns} rows={result.actualRows}/>}</div>:<div className="arena-empty">Run a sample to explore. Submit to score.</div>}</div></div></section>
    </>}
    {run?.status==='finished'&&<section className="sprint-finish"><div className={`sprint-finish-mark ${newBest?'new-record':''}`} aria-hidden="true">{newBest?'↗':'✓'}</div><h1>{newBest?'A new personal best.':'That’s a run.'}</h1><p>{run.minutes} minutes · {run.difficulty} · {time(run.secondsUsed)} played</p><div className="sprint-result-stats">{[[run.score,'points'],[run.solved,'cleared'],[run.firstTry,'first try'],[run.accuracy+'%','accepted']].map(([v,label])=><div key={label}><strong>{v}</strong><span>{label}</span></div>)}</div><div className="sprint-finish-actions"><button className="arena-primary" onClick={()=>setParams({})}>Another run →</button><Link className="arena-secondary" to="/training">Review my practice</Link><button className="arena-text-link" onClick={()=>downloadFile('offbyone-sprint.json',JSON.stringify(run,null,2),'application/json')}>Download result ↓</button></div><div className="sprint-review"><h2>Take something into the next run.</h2>{run.review?.map(p=><details key={p.slug}><summary><span>{p.solved?'✓':'↗'}</span><strong>{p.title}</strong><span>{p.solved?'Cleared':'Revisit'}</span></summary><p>{p.explanation}</p><pre>{p.solution}</pre><Link className="arena-text-link" to={`/?challenge=${encodeURIComponent(p.slug)}`}>Practice without a timer ↗</Link></details>)}</div></section>}
    {confirm&&<ConfirmDialog title={confirm==='skip'?'Move to the next challenge?':'Finish this sprint?'} confirmText={confirm==='skip'?'Skip challenge':'Finish sprint'} busy={!!busy} onClose={()=>setConfirm(null)} onConfirm={()=>action('Updating',async()=>{update(await api.sprintAction(id,confirm,{position:run.position}));setResult(null);setConfirm(null);})}><p>{confirm==='skip'?'This challenge won’t return during this run. You can review it afterwards.':'Your current score will be saved. You can start a new run whenever you’re ready.'}</p></ConfirmDialog>}
  </section>;
}
