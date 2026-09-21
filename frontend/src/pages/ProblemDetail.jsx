import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import { api } from '../lib/api';
import { connect, subscribe } from '../lib/ws';

const STARTERS = {
  java: `import java.util.Scanner;\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n    }\n}`,
  python: `import sys\ninput = sys.stdin.readline\n`,
  cpp: `#include <bits/stdc++.h>\nusing namespace std;\nint main() {\n    \n}`,
};

export default function ProblemDetail() {
  const { slug } = useParams();
  const [problem, setProblem] = useState(null);
  const [samples, setSamples] = useState([]);
  const [lang, setLang] = useState('java');
  const [code, setCode] = useState(STARTERS.java);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api.problem(slug).then(setProblem);
    api.samples(slug).then(setSamples);
  }, [slug]);

  async function submit() {
    setLoading(true);
    setResult(null);
    const { submissionId } = await api.submit({ slug, language: lang, code });

    const client = connect(() => {
      subscribe(`/topic/submission/${submissionId}`, (data) => {
        setResult(data);
        setLoading(false);
        client.deactivate();
      });
    });
  }

  if (!problem) return <div>Loading...</div>;

  return (
    <div className="problem-layout">
      <div className="problem-left">
        <h2>{problem.title}</h2>
        <span className={`diff-${problem.difficulty}`}>{problem.difficulty}</span>
        <div className="statement" dangerouslySetInnerHTML={{ __html: problem.statement }} />
        <h4>Sample Cases</h4>
        {samples.map((tc, i) => (
          <div key={i} className="sample">
            <pre><strong>Input:</strong>{'\n'}{tc.input}</pre>
            <pre><strong>Output:</strong>{'\n'}{tc.expectedOutput}</pre>
          </div>
        ))}
      </div>
      <div className="problem-right">
        <select value={lang} onChange={e => { setLang(e.target.value); setCode(STARTERS[e.target.value]); }}>
          <option value="java">Java</option>
          <option value="python">Python</option>
          <option value="cpp">C++</option>
        </select>
        <Editor
          height="60vh"
          language={lang === 'cpp' ? 'cpp' : lang}
          value={code}
          onChange={setCode}
          theme="vs-dark"
        />
        <button onClick={submit} disabled={loading}>
          {loading ? 'Judging...' : 'Submit'}
        </button>
        {result && (
          <div className={`verdict verdict-${result.verdict}`}>
            {result.verdict.toUpperCase()} — {result.runtimeMs}ms
          </div>
        )}
      </div>
    </div>
  );
}
