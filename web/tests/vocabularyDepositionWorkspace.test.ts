import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const view = fs.readFileSync(new URL('../src/views/VocabularyView.vue', import.meta.url), 'utf8')
const vocabularyCards = fs.readFileSync(new URL('../src/composables/useVocabularyCards.ts', import.meta.url), 'utf8')
const router = fs.readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8')
const capture = fs.readFileSync(new URL('../src/components/vocabulary/VocabularyCapturePanel.vue', import.meta.url), 'utf8')
const list = fs.readFileSync(new URL('../src/components/vocabulary/VocabularyCardList.vue', import.meta.url), 'utf8')

test('vocabulary view composes durable capture and list components', () => {
  assert.match(view, /VocabularyCapturePanel/)
  assert.match(view, /VocabularyCardList/)
  assert.match(view, /useVocabularyCards/)
  assert.doesNotMatch(view, /const\s+savedWords\s*=\s*ref/)
})

test('capture panel exposes theme choice and bulk submission states', () => {
  assert.match(capture, /VocabularyThemeShelf/)
  assert.match(capture, /selectedThemeUid/)
  assert.match(capture, /themeUid/)
  assert.doesNotMatch(capture, /template-control/)
  assert.match(capture, /captureMutation/)
  assert.match(capture, /已存在，已追加来源/)
})

test('list renders every persisted status and filters', () => {
  for (const token of ['generating', 'ready', 'needs_review', 'failed', 'sourceType']) assert.match(list, new RegExp(token))
  for (const token of ['phonetic', 'coreDefinition', 'sourceCount', 'updatedAt', 'recent', 'az']) assert.match(list, new RegExp(token))
})

test('single card route opens persistent cards and maps legacy words into collection filters', () => {
  assert.match(router, /path:\s*['"]vocabulary\/cards\/:cardUid['"]/)
  assert.match(router, /name:\s*['"]vocabulary-card['"]/)
  assert.doesNotMatch(router, /path:\s*['"]vocabulary\/card\/:cardUid['"]/)
  assert.doesNotMatch(router, /name:\s*['"]VocabularyWordCard['"]|path:\s*['"]vocabulary\/cards\/:word['"]/)
  assert.match(view, /route\.params\.cardUid/)
  assert.match(view, /cardUid\.startsWith\(['"]card_['"]\)/)
  assert.match(view, /selectedCardUid\.value\s*=\s*cardUid/)
  assert.match(view, /return typeof cardUid === ['"]string['"] \? cardUid\.trim\(\) \|\| null : null/)
  assert.match(view, /keyword:\s*legacyVocabularyCardKeyword\(\)/)
  assert.match(view, /selectedCardUid\.value\s*=\s*null/)
  assert.match(view, /watch\(\(\)\s*=>\s*\[route\.name,\s*route\.params\.cardUid,\s*route\.query\.tab\]/)
})

test('collection and persistent card routes render mutually exclusive page states', () => {
  assert.match(view, /isPersistentVocabularyCardRoute/)
  assert.match(view, /vocabulary-card-page/)
  assert.doesNotMatch(view, /<aside class="vocabulary-card-detail"/)
  assert.match(view, /legacyVocabularyCardKeyword/)
  assert.match(view, /cardUid\.startsWith\(['"]card_['"]\)/)
  assert.doesNotMatch(view, /selectedVocabularyTemplate/)
  assert.doesNotMatch(view, /:template=/)
  assert.match(view, /vocabulary-card-page__skeleton/)
  assert.match(view, /单词卡不存在或已被删除/)
  assert.match(view, /无权查看这张单词卡/)
  assert.match(view, /detailQuery\.refetch/)
})

test('collection page does not inherit the obsolete mode two-column grid', () => {
  assert.match(view, /\.mode-page\s*\{\s*grid-template-columns:\s*minmax\(0,\s*1fr\)\s+380px;/)
  assert.doesNotMatch(view, /\.mode-page,\s*\.collection-page\s*\{\s*grid-template-columns:\s*minmax\(0,\s*1fr\)\s+380px;/)
  assert.doesNotMatch(view, /\.collection-page\s*\{\s*grid-template-columns:\s*minmax\(0,\s*1fr\);\s*\}/)
})

test('vocabulary card queries no longer load legacy templates', () => {
  assert.doesNotMatch(vocabularyCards, /listVocabularyTemplates/)
  assert.doesNotMatch(vocabularyCards, /templateQuery/)
})

test('vocabulary card query retries stop for unavailable details and stay bounded otherwise', async () => {
  const cards = await import('../src/composables/useVocabularyCards.ts')
  const shouldRetry = (cards as Record<string, unknown>).shouldRetryVocabularyCardQuery
  assert.equal(typeof shouldRetry, 'function')
  const retry = shouldRetry as (failureCount: number, error: unknown) => boolean
  const axiosError = (status: number) => ({ response: { status } })

  assert.equal(retry(0, axiosError(403)), false)
  assert.equal(retry(0, axiosError(404)), false)
  assert.equal(retry(0, axiosError(500)), true)
  assert.equal(retry(2, new Error('temporary network failure')), true)
  assert.equal(retry(3, new Error('temporary network failure')), false)
})
