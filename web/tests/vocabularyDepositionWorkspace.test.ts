import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const view = fs.readFileSync(new URL('../src/views/VocabularyView.vue', import.meta.url), 'utf8')
const capture = fs.readFileSync(new URL('../src/components/vocabulary/VocabularyCapturePanel.vue', import.meta.url), 'utf8')
const list = fs.readFileSync(new URL('../src/components/vocabulary/VocabularyCardList.vue', import.meta.url), 'utf8')

test('vocabulary view composes durable capture and list components', () => {
  assert.match(view, /VocabularyCapturePanel/)
  assert.match(view, /VocabularyCardList/)
  assert.match(view, /useVocabularyCards/)
  assert.doesNotMatch(view, /const\s+savedWords\s*=\s*ref/)
})

test('capture panel exposes template choice and bulk submission states', () => {
  assert.match(capture, /basic/)
  assert.match(capture, /exam/)
  assert.match(capture, /reading/)
  assert.match(capture, /captureMutation/)
  assert.match(capture, /已存在，已追加来源/)
})

test('list renders every persisted status and filters', () => {
  for (const token of ['generating', 'ready', 'needs_review', 'failed', 'sourceType']) assert.match(list, new RegExp(token))
})

test('legacy word-card route opens the collection workspace with a keyword filter', () => {
  assert.match(view, /route\.name\s*===\s*['"]VocabularyWordCard['"]/)
  assert.match(view, /route\.params\.word/)
  assert.match(view, /isVocabularyWordCardRoute\(\)\s*\?\s*['"]collection['"]/)
  assert.match(view, /keyword:\s*legacyVocabularyCardKeyword\(\)/)
  assert.match(view, /watch\(\(\)\s*=>\s*\[route\.name,\s*route\.params\.word,\s*route\.query\.tab\]/)
  assert.match(view, /selectedCardUid\.value\s*=\s*null/)
})
