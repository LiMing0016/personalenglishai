import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const inspector = fs.readFileSync(
  new URL('../src/components/vocabulary/VocabularyCardInspector.vue', import.meta.url),
  'utf8',
)
const view = fs.readFileSync(
  new URL('../src/views/VocabularyView.vue', import.meta.url),
  'utf8',
)

test('inspector exposes safe edit controls and all conflict choices', () => {
  for (const token of [
    'baseRevisionUid',
    'keep_current',
    'use_ai',
    'merge_fields',
    'retryVocabularyCard',
    'listVocabularyRevisions',
  ]) {
    assert.match(inspector, new RegExp(token))
  }

  assert.match(inspector, /保留当前内容/)
  assert.match(inspector, /使用 AI 新版本/)
  assert.match(inspector, /逐字段合并/)
  assert.match(inspector, /个人笔记/)
  assert.match(inspector, /term.*readonly|readonly.*term/s)
})

test('inspector derives editable and merge fields from the selected template', () => {
  assert.match(inspector, /template:\s*VocabularyTemplate/)
  assert.match(inspector, /props\.template\.fields/)
  assert.match(inspector, /field\s*!==\s*['"]term['"]/)
  assert.match(inspector, /notes/)
  assert.match(view, /:template="selectedVocabularyTemplate"/)
})

test('inspector restores card content when editing is cancelled', () => {
  assert.match(inspector, /function\s+cancelEditing/)
  assert.match(inspector, /cloneEditableContent\(props\.card\.content\)/)
  assert.match(inspector, /@click="cancelEditing"/)
})

test('inspector opens a persisted needs-review conflict without requiring a save', () => {
  assert.match(inspector, /card\.status\s*===\s*['"]needs_review['"]/)
  assert.match(inspector, /card\.candidateRevisionUid/)
  assert.match(inspector, /card\.candidateContent/)
  assert.match(inspector, /currentContent:\s*card\.content/)
})
