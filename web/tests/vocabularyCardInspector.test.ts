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

test('inspector provides a compact catalog-backed regenerate template selector', () => {
  assert.match(inspector, /templates:\s*VocabularyTemplate\[\]/)
  assert.match(inspector, /v-model="regenerateTemplateKey"/)
  assert.match(inspector, /aria-label="重新生成模板"/)
  assert.match(inspector, /templateKey:\s*regenerateTemplateKey\.value/)
  assert.match(view, /:templates="templateQuery\.data\.value\?\.items\s*\?\?\s*\[\]"/)
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

test('inspector exposes retry when either card or generation status is failed', () => {
  assert.match(inspector, /card\.status\s*===\s*['"]failed['"]\s*\|\|\s*card\.generationStatus\s*===\s*['"]failed['"]/)
})

test('inspector describes soft deletion and uses a safe source URL guard', () => {
  assert.match(inspector, /safeExternalUrl\(source\.sourceUrl\)/)
  assert.match(inspector, /再次收藏或录入时可恢复/)
  assert.doesNotMatch(inspector, /无法恢复这张单词卡|永久丢失/)
})
