import { useRef } from 'react';
import Editor, { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import 'monaco-editor/esm/vs/basic-languages/sql/sql.contribution';
import 'monaco-editor/esm/vs/basic-languages/java/java.contribution';
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
self.MonacoEnvironment = { getWorker: () => new EditorWorker() };
loader.config({ monaco });
export default function SqlEditor({ value, onChange, onRun, readOnly }) {
  const run = useRef(onRun); run.current = onRun;
  return <Editor height="clamp(360px, 46vh, 640px)" language="sql" theme="vs-dark" value={value} onChange={onChange}
    onMount={editor => {
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => run.current?.(false));
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.Enter, () => run.current?.(true));
    }}
    options={{ fontSize: 14, fontFamily: "'SFMono-Regular', Consolas, monospace", minimap: { enabled: false }, padding: { top: 22 }, scrollBeyondLastLine: false, lineNumbersMinChars: 3, automaticLayout: true, wordWrap: 'on', tabSize: 2, readOnly }}/>
}
