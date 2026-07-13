import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const inspector = fs.readFileSync(
  new URL('../src/components/vocabulary/VocabularyCardInspector.vue', import.meta.url),
  'utf8',
)

test('draft reset depends only on card and active revision identity', async () => {
  const cards = await import('../src/composables/useVocabularyCards.ts')
  const shouldReset = (cards as Record<string, unknown>).shouldResetVocabularyCardDraft
  assert.equal(typeof shouldReset, 'function')
  const decide = shouldReset as (
    previous: { cardUid: string, activeRevisionUid: string | null } | undefined,
    next: { cardUid: string, activeRevisionUid: string | null },
  ) => boolean

  assert.equal(decide(undefined, { cardUid: 'card_1', activeRevisionUid: 'rev_1' }), true)
  assert.equal(decide(
    { cardUid: 'card_1', activeRevisionUid: 'rev_1' },
    { cardUid: 'card_1', activeRevisionUid: 'rev_1' },
  ), false)
  assert.equal(decide(
    { cardUid: 'card_1', activeRevisionUid: 'rev_1' },
    { cardUid: 'card_2', activeRevisionUid: 'rev_1' },
  ), true)
  assert.equal(decide(
    { cardUid: 'card_1', activeRevisionUid: 'rev_1' },
    { cardUid: 'card_1', activeRevisionUid: 'rev_2' },
  ), true)

  assert.doesNotMatch(inspector, /watch\(\(\) => props\.card,[\s\S]*?deep:\s*true/)
  assert.match(inspector, /shouldResetVocabularyCardDraft/)
})

test('save success immediately adopts the server revision markdown', () => {
  assert.match(inspector, /const savedCard = await props\.updateMutation\.mutateAsync/)
  assert.match(inspector, /editMarkdown\.value = cardMarkdown\(savedCard\)/)
  assert.match(inspector, /savedCard\.activeRevisionUid/)
})

test('theme fallback runs on card changes and preserves an active manual choice', async () => {
  const cards = await import('../src/composables/useVocabularyCards.ts')
  const selectTheme = (cards as Record<string, unknown>).selectVocabularyThemeUid
  assert.equal(typeof selectTheme, 'function')
  const select = selectTheme as (
    themes: Array<{ themeUid: string }>,
    defaultThemeUid: string,
    preferredThemeUid: string | null | undefined,
  ) => string
  const active = [{ themeUid: 'theme_first' }, { themeUid: 'theme_default' }, { themeUid: 'theme_manual' }]

  assert.equal(select(active, 'theme_default', null), 'theme_default')
  assert.equal(select(active, 'theme_missing', null), 'theme_first')
  assert.equal(select(active, 'theme_default', 'theme_manual'), 'theme_manual')
  assert.equal(select([], 'theme_default', 'theme_manual'), '')

  assert.match(inspector, /cardChanged[\s\S]*selectVocabularyThemeUid/)
  assert.match(inspector, /some\(\(theme\) => theme\.themeUid === selectedThemeUid\.value\)\) return/)
})

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
  assert.match(inspector, /isVocabularyV1Revision/)
  assert.match(inspector, /当前 Markdown/)
  assert.match(inspector, /候选 Markdown/)
  assert.match(inspector, /const mergeFields = conflictMergeFields\(\)/)
  assert.match(inspector, /markdown:\s*mergeChoice\.value\.markdown/)
  assert.match(inspector, /legacyMergeableFields/)
  assert.match(inspector, /keep_current/)
  assert.match(inspector, /use_ai/)
  assert.match(inspector, /merge_fields/)
  assert.match(inspector, /schemaVersion:\s*source\.schemaVersion/)
  assert.match(inspector, /phonetics:\s*source\.phonetics/)
  assert.match(inspector, /senses:\s*source\.senses/)
  assert.doesNotMatch(inspector, /\{\s*\.\.\.source,\s*term:/, 'compatibility-only markdown must not leak into core')
})

test('conflict format follows the backend current revision shape check', async () => {
  const cards = await import('../src/composables/useVocabularyCards.ts')
  const classifyRevision = (cards as Record<string, unknown>).isVocabularyV1Revision
  assert.equal(typeof classifyRevision, 'function')
  const isV1 = classifyRevision as (formatVersion: number | null, content: unknown) => boolean
  const legacy = { term: 'record', definitions: ['entry'] }
  const v1 = { schemaVersion: 1, term: 'record', phonetics: [], senses: [], markdown: '# Card' }
  const v1Lookalike = { schemaVersion: 1, phonetics: [], senses: [], markdown: '# Card' }

  assert.equal(isV1(1, v1), true)
  assert.equal(isV1(null, legacy), false, 'current legacy stays legacy even when a candidate is v1')
  assert.equal(isV1(1, legacy), false, 'a mislabeled legacy current revision stays legacy')
  assert.equal(isV1(null, v1), false, 'shape alone cannot override the current revision format')
  assert.equal(isV1(1, v1Lookalike), false, 'a format marker still requires the real core compatibility shape')

  const block = inspector.match(/const v1Conflict = computed\(\(\) => \{[\s\S]*?\n\}\)/)?.[0] ?? ''
  assert.doesNotMatch(block, /candidateRevision|candidateContent/)
  assert.match(block, /currentContentFormatVersion/)
  assert.match(block, /currentContentFormatVersion\s*!==\s*undefined/)
  assert.match(block, /conflict\.value\?\.currentContent/)
  assert.match(block, /currentRevision\?\.contentFormatVersion/)
  assert.doesNotMatch(block, /currentRevision\?\.contentFormatVersion\s*\?\?/, 'an explicit null conflict format remains authoritative')
  assert.match(inspector, /currentContentFormatVersion:\s*props\.card\.contentFormatVersion/)
  assert.match(inspector, /candidateContentFormatVersion:\s*null/)
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
