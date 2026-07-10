import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const inspector = fs.readFileSync(
  new URL('../src/components/vocabulary/VocabularyCardInspector.vue', import.meta.url),
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
