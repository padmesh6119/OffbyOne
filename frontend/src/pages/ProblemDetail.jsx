import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
import { STARTERS } from '../lib/starters';
const CodeEditor = lazy(() => import('../components/SqlEditor'));
export default function ProblemDetail() {
  const { slug }=useParams();
  const username=localStorage.getItem('username'), key='obo:java:'+username+':'+slug;
  const [problem,setProblem]=useState(null),[samples,setSamples]=useState([]),[code,setCode]=useState(''),[result,setResult]=useState(null),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const generation=useRef(0);
  useEffect(()=>{const version=++generation.current;setProblem(null);setResult(null);setBusy(false);setError('');setCode(localStorage.getItem(key)||STARTERS.java);Promise.all([api.problem(slug),api.samples(slug)]).then(([p,s])=>{if(version===generation.current){setProblem(p);setSamples(s);}}).catch(e=>version===generation.current&&setError(errorMessage(e)));return()=>{generation.current++;};},[slug,key]);
  function edit(value){setCode(value??'');localStorage.setItem(key,value??'');}
  async function submit(){
    if(busy||!code.trim())return;
    const version=generation.current;setBusy(true);setError('');setResult(null);
    try{
      const {submissionId}=await api.submit({slug,language:'java',code});
      const deadline=Date.now()+150000;
      while(version===generation.current){
        const status=await api.submission(submissionId);
        if(version!==generation.current)return;
        if(!['pending','queued'].includes(status.verdict)){setResult(status);return;}
        if(Date.now()>deadline)throw new Error('Judging is taking longer than expected. Your attempt is saved in My Progress.');
        await new Promise(resolve=>setTimeout(resolve,1500));
      }
    }catch(e){if(version===generation.current)setError(errorMessage(e));}
    finally{if(version===generation.current)setBusy(false);}
  }
  return <>{error&&<div className="arena-error" role="alert">{error}</div>}{!problem?<div className="arena-loading">Loading challenge…</div>:<>
    <div className="workspace-heading"><div><div className="arena-eyebrow">JAVA / SOLO PRACTICE</div><h2>Make every line count.</h2></div><Link className="arena-secondary" to="/problems">← All Java challenges</Link></div>
    <div className="arena-workspace"><section className="challenge-panel"><div className="panel-title"><span>THE CHALLENGE</span><span className={'arena-difficulty '+problem.difficulty}>{problem.difficulty}</span></div><div className="challenge-description"><h2>{problem.title}</h2><p className="java-description">{problem.statement}</p><div className="query-rules">Java · {problem.timeLimitMs}ms time limit · {problem.memoryLimitMb} MB memory</div></div><div className="schema-heading">SAMPLE CASES</div>{samples.map((s,i)=><div className="java-sample" key={i}><small>INPUT {i+1}</small><pre>{s.input}</pre><small>EXPECTED OUTPUT</small><pre>{s.expectedOutput}</pre></div>)}</section>
    <section className="query-panel"><div className="panel-title"><span><i className="file-dot"/> Main.java</span><span>JAVA</span></div><Suspense fallback={<div className="arena-loading">Loading editor…</div>}><CodeEditor language="java" value={code} onChange={edit} onRun={submit} readOnly={busy}/></Suspense><div className="editor-actions"><span>Ctrl / Cmd + Enter · Submit</span><button className="arena-primary" disabled={busy||!code.trim()} onClick={submit}>{busy?'Judging…':'Submit solution →'}</button></div><div className="output-panel"><div className="panel-title"><span>JUDGE FEEDBACK</span></div><div className="java-feedback" role="status">{busy?'Running your solution against the test cases…':result?<><strong>{result.verdict.replaceAll('_',' ')}</strong>{result.runtimeMs!=null&&<p>Runtime: {result.runtimeMs}ms</p>}<p>Your attempt has been saved to My Progress.</p></>:<p>Your result will appear here. Read standard input and write your answer to standard output.</p>}</div></div></section></div>
  </>}</>;
}
