import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const inspector = fs.readFileSync(
  new URL('../src/components/vocabulary/VocabularyCardInspector.vue', import.meta.url),
  'utf8',
)

test('inspector adapts core once and edits markdown without legacy field guesses', () => {
  assert.match(inspector, /VocabularyCoreSummary/)
  assert.match(inspector, /VocabularyMarkdownEditor/)
  assert.match(inspector, /card\.core\s*\?\?\s*projectLegacyVocabularyCore/)
  assert.match(inspector, /minimalVocabularyCore/)
  assert.doesNotMatch(inspector, /props\.template\.fields|fieldNames|isArrayField/)
})

test('save preserves term identity and sends core markdown revision and summary', () => {
  for (const token of ['baseRevisionUid', 'core:', 'markdown:', 'changeSummary:']) {
    assert.match(inspector, new RegExp(token))
  }
  assert.match(inspector, /term:\s*props\.card\.normalizedTerm/)
  assert.match(inspector, /updateMutation\.isPending\.value/)
  assert.match(inspector, /单词卡已保存/)
  assert.match(inspector, /保存失败，请重试/)
})

test('new format conflicts compare markdown as a whole and legacy revisions keep field merge', () => {
  assert.match(inspector, /contentFormatVersion\s*===\s*1/)
  assert.match(inspector, /当前 Markdown/)
  assert.match(inspector, /候选 Markdown/)
  assert.match(inspector, /const mergeFields = conflictMergeFields\(\)/)
  assert.match(inspector, /markdown:\s*mergeChoice\.value\.markdown/)
  assert.match(inspector, /legacyMergeableFields/)
  assert.match(inspector, /keep_current/)
  assert.match(inspector, /use_ai/)
  assert.match(inspector, /merge_fields/)
})

test('regenerate uses active cached themes and confirms switching to the latest revision', () => {
  assert.match(inspector, /useVocabularyThemes/)
  assert.match(inspector, /themesQuery\.isError\.value\s*&&\s*!themesQuery\.data\.value/)
  assert.match(inspector, /themesQuery\.refetch/)
  assert.match(inspector, /theme\.status\s*===\s*['"]active['"]/)
  assert.match(inspector, /将使用主题最新版本重新生成，当前版本会保留在历史中。/)
  assert.match(inspector, /themeUid:\s*selectedThemeUid\.value/)
  assert.match(inspector, /useLatestThemeVersion:\s*true/)
  assert.match(inspector, /暂无可用主题/)
})

test('inspector retains legacy retry conflict and soft-delete behavior', () => {
  assert.match(inspector, /card\.status\s*===\s*['"]failed['"]\s*\|\|\s*card\.generationStatus\s*===\s*['"]failed['"]/)
  assert.match(inspector, /safeExternalUrl\(source\.sourceUrl\)/)
  assert.match(inspector, /再次收藏或录入时可恢复/)
  assert.match(inspector, /card\.candidateRevisionUid/)
  assert.doesNotMatch(inspector, /无法恢复这张单词卡|永久丢失/)
})

test('inspector styles stable editors and narrow screens without horizontal overflow', () => {
  assert.match(inspector, /min-width:\s*0/)
  assert.match(inspector, /overflow-wrap:\s*anywhere/)
  assert.match(inspector, /@media \(max-width:\s*620px\)/)
  assert.match(inspector, /grid-template-columns:\s*1fr/)
})
