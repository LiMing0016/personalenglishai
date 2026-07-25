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
const importDialogSource = readFileSync(
  new URL('../src/components/vocabulary/VocabularyImportDialog.vue', import.meta.url),
  'utf8',
)
const importComposerSource = readFileSync(
  new URL('../src/components/vocabulary/VocabularyImportComposer.vue', import.meta.url),
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
const composedSource = [
  pageSource,
  captureSource,
  importDialogSource,
  importComposerSource,
  listSource,
  inspectorSource,
].join('\n')

for (const requiredText of [
  '搜索单词',
  '背词模式',
  '单词沉淀',
  '学习统计',
  '导入单词',
  '输入、粘贴或添加图片',
  'AI 分析',
  '生成卡片',
  '全部来源',
  'A-Z',
  '最近沉淀',
  '重新生成主题',
  '使用最新主题版本？',
  '再次收藏或录入时可恢复',
]) {
  assert.ok(composedSource.includes(requiredText), `vocabulary learning page should render ${requiredText}`)
}

for (const navigationLabel of ['搜索单词', '背词模式', '单词沉淀', '学习统计']) {
  assert.ok(pageSource.includes(navigationLabel), `vocabulary navigation should keep ${navigationLabel}`)
}
assert.match(
  pageSource,
  /:class="\{\s*active:\s*activeView === view\.key\s*\}"/,
  'vocabulary navigation should bind active state to activeView',
)
assert.match(
  pageSource,
  /@click="switchVocabularyView\(view\.key\)"/,
  'vocabulary navigation should keep its view switch handler',
)
assert.ok(!pageSource.includes('brand-lockup'), 'vocabulary navigation should remove the redundant brand lockup')
assert.ok(!pageSource.includes('topbar-actions'), 'vocabulary navigation should remove the redundant topbar actions')

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
assert.ok(pageSource.includes('isPersistentVocabularyCardRoute'), 'persistent card routes should have an explicit discriminator')
assert.ok(pageSource.includes('vocabulary-card-page'), 'persistent cards should render a dedicated full-page branch')
assert.ok(!pageSource.includes('<aside class="vocabulary-card-detail"'), 'collection should not render a right-side inspector')
assert.ok(!pageSource.includes('selectedVocabularyTemplate'), 'card details should not depend on a legacy template gate')
assert.ok(!pageSource.includes(':template='), 'card details should not pass legacy template props')
assert.ok(pageSource.includes('vocabulary-card-page__skeleton'), 'card loading should preserve a stable full-page layout')
assert.ok(pageSource.includes('单词卡不存在或已被删除'), 'missing cards should have a distinct unavailable state')
assert.ok(pageSource.includes('无权查看这张单词卡'), 'forbidden cards should have a distinct unavailable state')
assert.ok(pageSource.includes('detailQuery.refetch'), 'generic card failures should expose a retry action')

assert.ok(
  !composedSource.includes('从 PDF、AI 对话、笔记和错题中整理的单词会自动回到这里'),
  'the page should not promise sources that are not connected',
)
for (const obsoleteText of [
  '单词卡中心',
  '手动录入和词典收藏的单词会沉淀在这里；更多来源后续接入',
  '批量录入',
]) {
  assert.ok(!composedSource.includes(obsoleteText), `vocabulary learning page should remove ${obsoleteText}`)
}
assert.ok(
  !composedSource.includes('单词端数据源验收标记'),
  'the page should not expose backend integration markers to students',
)

console.log('vocabulary-learning-page-ok')
