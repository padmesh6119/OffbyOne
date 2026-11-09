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
        rules: [{ token: 'keyword', foreground: 'FFAD8F' }, { token: 'comment', foreground: '858F95' }, { token: 'string', foreground: 'B5DDBD' }],
        colors: { 'editor.background': '#1B1E21', 'editor.foreground': '#E2E6E5', 'editorLineNumber.foreground': '#727C82', 'editorLineNumber.activeForeground': '#FF916E', 'editor.lineHighlightBackground': '#24282C', 'editor.selectionBackground': '#FF916E28', 'editorCursor.foreground': '#FF916E' } });
      instance.editor.defineTheme('offbyone-light', { base: 'vs', inherit: true,
        rules: [{ token: 'keyword', foreground: '2854D7' }, { token: 'comment', foreground: '73796C' }, { token: 'string', foreground: '34744B' }],
        colors: { 'editor.background': '#FFFEFA', 'editor.foreground': '#202B43', 'editorLineNumber.foreground': '#8A908B', 'editorLineNumber.activeForeground': '#2854D7', 'editor.lineHighlightBackground': '#F1F3FA', 'editor.selectionBackground': '#2854D725', 'editorCursor.foreground': '#2854D7' } });
    }}
    onMount={editor => {
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => run.current?.(false));
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.Enter, () => run.current?.(true));
    }}
    options={{ fontSize: preferences.fontSize, fontFamily: "'SFMono-Regular', Consolas, monospace", minimap: { enabled: false }, padding: { top: 22 }, scrollBeyondLastLine: false, lineNumbersMinChars: 3, automaticLayout: true, wordWrap: preferences.wordWrap ? 'on' : 'off', lineNumbers: preferences.lineNumbers ? 'on' : 'off', tabSize: 2, readOnly }}/>
}
