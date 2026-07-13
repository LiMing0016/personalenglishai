import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const pageSource = readFileSync(
  new URL('../src/views/VocabularyView.vue', import.meta.url),
  'utf8',
)
const routerSource = readFileSync(
  new URL('../src/router/index.ts', import.meta.url),
  'utf8',
)
const captureSource = readFileSync(
  new URL('../src/components/vocabulary/VocabularyCapturePanel.vue', import.meta.url),
  'utf8',
)
const listSource = readFileSync(
  new URL('../src/components/vocabulary/VocabularyCardList.vue', import.meta.url),
  'utf8',
)
const inspectorSource = readFileSync(
  new URL('../src/components/vocabulary/VocabularyCardInspector.vue', import.meta.url),
  'utf8',
)
const composedSource = [pageSource, captureSource, listSource, inspectorSource].join('\n')

for (const requiredText of [
  '词启',
  '搜索单词',
  '背词模式',
  '单词沉淀',
  '学习统计',
  '单词卡中心',
  '手动录入和词典收藏的单词会沉淀在这里；更多来源后续接入',
  '批量录入',
  '全部来源',
  'A-Z',
  '最近沉淀',
  '重新生成主题',
  '使用最新主题版本？',
  '再次收藏或录入时可恢复',
]) {
  assert.ok(composedSource.includes(requiredText), `vocabulary learning page should render ${requiredText}`)
}

assert.ok(pageSource.includes('lookupDictionary'), 'top search should keep dictionary lookup integration')
assert.ok(pageSource.includes('useVocabularyCards'), 'collection should use the persisted vocabulary query layer')
assert.ok(pageSource.includes('VocabularyCapturePanel'), 'collection should compose the capture panel')
assert.ok(pageSource.includes('VocabularyCardList'), 'collection should compose the persisted list')
assert.ok(pageSource.includes('VocabularyCardInspector'), 'card route should compose the inspector')
assert.ok(!pageSource.includes('const savedWords = ref'), 'collection should not restore the removed mock list')

assert.ok(
  routerSource.includes("path: 'vocabulary/cards/:cardUid'") && routerSource.includes("name: 'vocabulary-card'"),
  'vocabulary card detail should use the canonical route',
)
assert.ok(pageSource.includes("route.name === 'vocabulary-card'"), 'canonical and legacy card URLs should be interpreted by the view')
assert.ok(pageSource.includes("cardUid.startsWith('card_')"), 'persisted card IDs should open the inspector')
assert.ok(pageSource.includes('legacyVocabularyCardKeyword'), 'legacy words should remain collection search filters')

assert.ok(
  !composedSource.includes('从 PDF、AI 对话、笔记和错题中整理的单词会自动回到这里'),
  'the page should not promise sources that are not connected',
)
assert.ok(
  !composedSource.includes('单词端数据源验收标记'),
  'the page should not expose backend integration markers to students',
)

console.log('vocabulary-learning-page-ok')
