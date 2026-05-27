import test from 'node:test'
import assert from 'node:assert/strict'

import { applyWritingPatch } from './writingPatchApplicator.ts'

test('applyWritingPatch replaces a valid selection', () => {
  const result = applyWritingPatch('College Chinese is useful.', {
    op: 'replace_selection',
    range: { start: 0, end: 15 },
    originalText: 'College Chinese',
    newText: 'The Chinese course',
  })

  assert.equal(result.status, 'success')
  if (result.status !== 'success') return
  assert.equal(result.nextText, 'The Chinese course is useful.')
  assert.equal(result.preview.operationLabel, '替换选区')
})

test('applyWritingPatch falls back to search replace when selection drifted', () => {
  const result = applyWritingPatch('Intro. College Chinese is useful.', {
    op: 'replace_selection',
    range: { start: 0, end: 7 },
    originalText: 'College Chinese',
    newText: 'The Chinese course',
  })

  assert.equal(result.status, 'success')
  if (result.status !== 'success') return
  assert.equal(result.nextText, 'Intro. The Chinese course is useful.')
})

test('applyWritingPatch reports ambiguous search replacement', () => {
  const result = applyWritingPatch('This course is useful. This course is useful.', {
    op: 'search_replace',
    searchText: 'This course is useful.',
    replaceText: 'This course is valuable.',
  })

  assert.equal(result.status, 'ambiguous')
  if (result.status !== 'ambiguous') return
  assert.equal(result.candidates.length, 2)
})

test('applyWritingPatch inserts after a unique anchor', () => {
  const result = applyWritingPatch('College Chinese is useful.', {
    op: 'insert_after_anchor',
    anchorText: 'College Chinese is useful.',
    insertText: 'It helps students communicate clearly.',
  })

  assert.equal(result.status, 'success')
  if (result.status !== 'success') return
  assert.equal(result.nextText, 'College Chinese is useful. It helps students communicate clearly.')
})

test('applyWritingPatch blocks duplicate nearby insertion', () => {
  const result = applyWritingPatch(
    'College Chinese is useful. It helps students communicate clearly.',
    {
      op: 'insert_after_anchor',
      anchorText: 'College Chinese is useful.',
      insertText: 'It helps students communicate clearly.',
    },
  )

  assert.equal(result.status, 'duplicate')
})

test('applyWritingPatch appends a paragraph with spacing', () => {
  const result = applyWritingPatch('First paragraph.', {
    op: 'append_paragraph',
    text: 'Second paragraph.',
  })

  assert.equal(result.status, 'success')
  if (result.status !== 'success') return
  assert.equal(result.nextText, 'First paragraph.\n\nSecond paragraph.')
})
