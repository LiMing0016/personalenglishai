import assert from 'node:assert/strict'

import { resolveTaskPromptViewerState } from '../src/components/writing/taskPromptViewerState.ts'

const hidden = resolveTaskPromptViewerState({
  writingMode: 'free',
  taskPrompt: '',
  activePanel: null,
})

assert.deepEqual(hidden, {
  visible: false,
  expanded: false,
  label: '查看题单',
})

const collapsed = resolveTaskPromptViewerState({
  writingMode: 'free',
  taskPrompt: 'Directions: Write an essay.',
  activePanel: null,
})

assert.deepEqual(collapsed, {
  visible: true,
  expanded: false,
  label: '查看题单',
})

const expanded = resolveTaskPromptViewerState({
  writingMode: 'free',
  taskPrompt: 'Directions: Write an essay.',
  activePanel: 'taskPrompt',
})

assert.deepEqual(expanded, {
  visible: true,
  expanded: true,
  label: '收起题单',
})

const examFallback = resolveTaskPromptViewerState({
  writingMode: 'exam',
  taskPrompt: '',
  activePanel: null,
})

assert.deepEqual(examFallback, {
  visible: true,
  expanded: false,
  label: '查看题单',
})

console.log('task-prompt-viewer-state-ok')
