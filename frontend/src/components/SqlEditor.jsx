import { useRef } from 'react';
import { useTheme } from '../theme';
import { usePreferences } from '../preferences';
import Editor, { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import 'monaco-editor/esm/vs/basic-languages/sql/sql.contribution';
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
self.MonacoEnvironment = { getWorker: () => new EditorWorker() };
loader.config({ monaco });
export default function SqlEditor({ value, onChange, onRun, readOnly, language = 'sql' }) {
  const { theme } = useTheme();
  const { preferences } = usePreferences();
  const run = useRef(onRun); run.current = onRun;
  return <Editor height="clamp(280px, calc(100vh - 580px), 520px)" language={language} theme={theme === 'light' ? 'offbyone-light' : 'offbyone-dark'} value={value} onChange={onChange}
    beforeMount={instance => {
      instance.editor.defineTheme('offbyone-dark', { base: 'vs-dark', inherit: true,
        rules: [{ token: 'keyword', foreground: 'D8FC86' }, { token: 'comment', foreground: '919D87' }, { token: 'string', foreground: 'C8D9A9' }],
        colors: { 'editor.background': '#1C201C', 'editor.foreground': '#EFF3E7', 'editorLineNumber.foreground': '#7E8977', 'editorLineNumber.activeForeground': '#D8FC86', 'editor.lineHighlightBackground': '#282E26', 'editor.selectionBackground': '#D8FC8628', 'editorCursor.foreground': '#D8FC86' } });
      instance.editor.defineTheme('offbyone-light', { base: 'vs', inherit: true,
        rules: [{ token: 'keyword', foreground: '38552A' }, { token: 'comment', foreground: '6F7A65' }, { token: 'string', foreground: '59722B' }],
        colors: { 'editor.background': '#F9FBF2', 'editor.foreground': '#242D20', 'editorLineNumber.foreground': '#88927D', 'editorLineNumber.activeForeground': '#38552A', 'editor.lineHighlightBackground': '#EAF0DF', 'editor.selectionBackground': '#38552A25', 'editorCursor.foreground': '#38552A' } });
    }}
    onMount={editor => {
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => run.current?.(false));
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.Enter, () => run.current?.(true));
    }}
    options={{ fontSize: preferences.fontSize, fontFamily: "'SFMono-Regular', Consolas, monospace", minimap: { enabled: false }, padding: { top: 22 }, scrollBeyondLastLine: false, lineNumbersMinChars: 3, automaticLayout: true, wordWrap: preferences.wordWrap ? 'on' : 'off', lineNumbers: preferences.lineNumbers ? 'on' : 'off', tabSize: 2, readOnly }}/>
}
