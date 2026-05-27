import test from 'node:test'
import assert from 'node:assert/strict'

import {
  extractFirstEditableCodeBlock,
  extractWritingCoachEditActions,
} from './writingCoachEditActions.ts'

test('extractFirstEditableCodeBlock reads the first editable fenced block', () => {
  const code = extractFirstEditableCodeBlock([
    '可以直接使用：',
    '',
    '```text',
    'I believe this course is useful.',
    '```',
  ].join('\n'))

  assert.equal(code, 'I believe this course is useful.')
})

test('extractWritingCoachEditActions creates replace action for polish with a selected range', () => {
  const actions = extractWritingCoachEditActions({
    markdown: [
      '润色后：',
      '',
      '```text',
      'This course can improve students\' language skills.',
      '```',
    ].join('\n'),
    selectedText: 'This course is useful.',
    selectedSpan: { start: 10, end: 32 },
    selectedToolKey: 'polish',
  })

  assert.equal(actions.length, 1)
  assert.equal(actions[0]?.type, 'replace_selection')
  assert.equal(actions[0]?.target?.mode, 'selected_range')
  assert.deepEqual(actions[0]?.target?.range, { start: 10, end: 32 })
  assert.deepEqual(actions[0]?.patch, {
    op: 'replace_selection',
    range: { start: 10, end: 32 },
    originalText: 'This course is useful.',
    newText: 'This course can improve students\' language skills.',
    reason: '适合把当前选中的表达直接改成这一版。',
  })
})

test('extractWritingCoachEditActions creates insert action for next paragraph after selected range', () => {
  const actions = extractWritingCoachEditActions({
    markdown: [
      '下一句可以这样接：',
      '',
      '```essay-draft',
      'It also helps students express ideas more clearly.',
      '```',
    ].join('\n'),
    selectedText: 'College Chinese is useful.',
    selectedSpan: { start: 0, end: 26 },
    selectedToolKey: 'next',
  })

  assert.equal(actions.length, 1)
  assert.equal(actions[0]?.type, 'insert_after_selection')
  assert.equal(actions[0]?.patch?.op, 'insert_after_anchor')
})

test('extractWritingCoachEditActions creates append action when no selection is available for next', () => {
  const actions = extractWritingCoachEditActions({
    markdown: [
      '新增段落：',
      '',
      '```text',
      'In conclusion, the course should be compulsory.',
      '```',
    ].join('\n'),
    selectedToolKey: 'next',
  })

  assert.equal(actions.length, 1)
  assert.equal(actions[0]?.type, 'append_paragraph')
  assert.equal(actions[0]?.target?.mode, 'document_end')
  assert.equal(actions[0]?.patch?.op, 'append_paragraph')
})
