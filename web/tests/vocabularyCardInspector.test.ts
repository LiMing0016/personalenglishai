import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const inspector = fs.readFileSync(
  new URL('../src/components/vocabulary/VocabularyCardInspector.vue', import.meta.url),
  'utf8',
)
const markdownEditor = fs.readFileSync(
  new URL('../src/components/vocabulary/VocabularyMarkdownEditor.vue', import.meta.url),
  'utf8',
)
const vocabularyArchitecture = fs.readFileSync(
  new URL('../../docs/architecture/vocabulary-deposition.md', import.meta.url),
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
  assert.match(inspector, /VocabularyMarkdownRenderer/)
  assert.match(inspector, /VocabularyMarkdownEditor/)
  assert.match(inspector, /buildVocabularyCardSections/)
  assert.match(inspector, /card\.core\s*\?\?\s*projectLegacyVocabularyCore/)
  assert.match(inspector, /minimalVocabularyCore/)
  assert.doesNotMatch(inspector, /VocabularyTemplate|props\.template|templates|fieldNames|isArrayField/)
})

test('inspector is a notebook document with dynamic chapter navigation', () => {
  assert.match(inspector, /ref<MarkdownSection\[\]>\(\[\]\)/)
  assert.match(inspector, /buildVocabularyCardSections\(/)
  assert.match(inspector, /markdownSections\.value/)
  assert.match(inspector, /props\.card\.sources\.length\s*>\s*0/)
  assert.match(inspector, /Boolean\(props\.listVocabularyRevisions\?\.items\.length\)/)
  assert.match(inspector, /aria-label="单词卡章节"/)
  assert.match(inspector, /id="core-information"/)
  assert.match(inspector, /id="card-sources"/)
  assert.match(inspector, /id="card-history"/)
  assert.match(inspector, /aria-current="location"/)
  assert.match(inspector, /scrollToSection\(section\.id\)/)
  assert.doesNotMatch(inspector, /activeTab/)
  assert.doesNotMatch(inspector, /role="tab(?:list)?"/)
})

test('inspector tracks notebook chapters with one rebuilt window observer', () => {
  assert.match(inspector, /IntersectionObserver/)
  assert.match(inspector, /document\.getElementById\(id\)\?\.scrollIntoView\(\{\s*behavior:\s*['"]smooth['"],\s*block:\s*['"]start['"]\s*\}\)/)
  assert.match(inspector, /rootMargin:\s*['"]-\d+px\s+0px/)
  assert.match(inspector, /observer\?\.disconnect\(\)/)
  assert.match(inspector, /onBeforeUnmount/)
  assert.match(inspector, /typeof window\s*===\s*['"]undefined['"]/)
  assert.match(inspector, /activeSectionId\s*=\s*ref\(['"]core-information['"]\)/)
  assert.match(inspector, /const intersectingSectionIds = new Set<string>\(\)/)
  assert.match(inspector, /entry\.isIntersecting[\s\S]*intersectingSectionIds\.(?:add|delete)\(entry\.target\.id\)/)
  assert.match(inspector, /\[\.\.\.intersectingSectionIds\][\s\S]*getBoundingClientRect\(\)\.top/)
  assert.match(inspector, /intersectingSectionIds\.clear\(\)/)
})

test('inspector provides read and edit modes with polite save announcements', () => {
  assert.match(inspector, />阅读</)
  assert.match(inspector, />编辑</)
  assert.match(inspector, /v-if="editing"[\s\S]*VocabularyMarkdownEditor/)
  assert.match(inspector, /v-else[\s\S]*VocabularyMarkdownRenderer/)
  assert.match(inspector, /saveAnnouncement\s*=\s*ref\(['"]['"]\)/)
  assert.match(inspector, /aria-live="polite"/)
  assert.match(inspector, /saveAnnouncement\.value\s*=\s*['"]单词卡已保存['"]/)
  assert.match(inspector, /saveAnnouncement\.value\s*=\s*['"]保存失败，请重试['"]/)
  assert.match(inspector, /<template v-if="editing">[\s\S]*?<template v-else>/)
  const saveBlock = inspector.match(/async function save\([\s\S]*?\n\}\n\nfunction requestRegenerate/)?.[0] ?? ''
  assert.ok(saveBlock.indexOf("saveAnnouncement.value = '保存失败，请重试'") < saveBlock.indexOf('error instanceof VocabularyConflictError'))

  const contentClose = inspector.indexOf('\n    </div>\n\n    <p class="card-inspector__save-announcement sr-only"')
  const saveLiveRegion = inspector.indexOf('class="card-inspector__save-announcement sr-only"')
  assert.ok(contentClose >= 0, 'the inert content wrapper has a stable closing boundary')
  assert.ok(saveLiveRegion > contentClose, 'the save live region stays outside inert dialog background content')
})

test('inspector exposes the stable generation state matrix', () => {
  assert.match(inspector, /hasReadableRevision/)
  for (const text of [
    '正在生成单词卡',
    '正在生成新版本，当前内容可继续阅读',
    '发现待确认的新版本',
    '本次生成失败，当前内容未受影响',
    '暂时没有可阅读的卡片内容',
    '主题内容待完善',
  ]) {
    assert.match(inspector, new RegExp(text))
  }
  assert.match(inspector, /role="status"/)
  const liveRegion = inspector.match(/<div v-if="generationState"[^>]*role="status"[\s\S]*?<\/div>/)?.[0] ?? ''
  assert.doesNotMatch(liveRegion, /<button/)
  const placeholder = inspector.match(/<div v-else class="card-inspector__placeholder"[\s\S]*?<\/div>/)?.[0] ?? ''
  assert.doesNotMatch(placeholder, /generationState|正在生成单词卡|暂时没有可阅读的卡片内容/)
  assert.equal((inspector.match(/重试生成/g) ?? []).length, 1)
  const retryBlock = inspector.match(/const showRetry = computed\(\(\) => \([\s\S]*?\n\)\)/)?.[0] ?? ''
  assert.match(retryBlock, /generationOutcome\s*===\s*['"]failed['"]/)
  assert.match(retryBlock, /generationError/)
  assert.doesNotMatch(inspector, /poll|轮询|count/i)
})

test('inspector moves theme and delete into one narrow-screen more menu', () => {
  assert.match(inspector, /useMediaQuery\(['"]\(max-width: 767px\)['"]\)/)
  assert.match(inspector, /aria-label="更多单词卡操作"/)
  assert.match(inspector, /:aria-expanded="moreMenuOpen"/)
  assert.match(inspector, /aria-controls="vocabulary-card-more-menu"/)
  assert.doesNotMatch(inspector, /role="menu(?:item)?"/)
  assert.match(inspector, /event\.key\s*!==\s*['"]Escape['"]/)
  assert.match(inspector, /moreButton\.value\?\.focus\(\)/)
  assert.match(inspector, /window\.addEventListener\(['"]keydown['"]/)
  assert.match(inspector, /window\.removeEventListener\(['"]keydown['"]/)
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

test('inspector serializes every card mutation through one operation lock', () => {
  const lock = inspector.match(/const cardOperationPending = computed\(\(\) => \([\s\S]*?\n\)\)/)?.[0] ?? ''
  for (const mutation of [
    'updateMutation',
    'regenerateMutation',
    'retryVocabularyCard',
    'resolveConflictMutation',
    'deleteMutation',
  ]) {
    assert.match(lock, new RegExp(`${mutation}\\.isPending\\.value`))
  }
  assert.match(inspector, /async function runCardOperation/)
  for (const handler of ['save', 'regenerate', 'retry', 'removeCard', 'resolveConflict']) {
    const block = inspector.match(new RegExp(`async function ${handler}\\([^)]*\\) \\{[\\s\\S]*?\\n\\}`))?.[0] ?? ''
    assert.match(block, /cardOperationPending\.value|runCardOperation/)
  }
  assert.match(inspector, /disabled:[^\n]*cardOperationPending\.value/)
})

test('inspector dialogs share focus trapping inert background and conflict guidance', () => {
  assert.match(inspector, /class="card-inspector__content"[^>]*:inert="Boolean\(activeDialog\)"/)
  assert.match(inspector, /aria-describedby="conflict-card-guidance"/)
  assert.match(inspector, /id="conflict-card-guidance"/)
  assert.match(inspector, /function trapDialogFocus/)
  assert.match(inspector, /event\.shiftKey/)
  assert.match(inspector, /restoreDialogFocus/)
  assert.match(inspector, /regenerateDialog/)
  assert.match(inspector, /deleteDialog/)
  assert.match(inspector, /conflictDialog/)
  assert.match(inspector, /saveAnnouncement\.value\s*=\s*['"]保存失败，请重试['"]/)
  assert.match(inspector, /const conflictDialogOpen = ref\(false\)/)
  assert.match(inspector, /v-if="conflict && conflictDialogOpen"/)
  assert.match(inspector, /v-show="conflict && !conflictDialogOpen"[\s\S]*?>处理冲突</)

  const closeConflictBlock = inspector.match(/function closeConflictDialog\([\s\S]*?\n\}/)?.[0] ?? ''
  assert.match(closeConflictBlock, /conflictDialogOpen\.value\s*=\s*false/)
  assert.doesNotMatch(closeConflictBlock, /conflict\.value\s*=\s*null/)

  assert.match(inspector, /watch\(\s*\[activeDialog, cardOperationPending\]/)
  const focusBlock = inspector.match(/async function focusActiveDialog\([\s\S]*?\n\}/)?.[0] ?? ''
  assert.match(focusBlock, /cardOperationPending\.value/)
  const restoreBlock = inspector.match(/async function restoreDialogFocus\([\s\S]*?\n\}/)?.[0] ?? ''
  assert.match(restoreBlock, /cardOperationPending\.value/)
  assert.ok(
    restoreBlock.indexOf('target.focus()') < restoreBlock.indexOf('dialogReturnTarget = null'),
    'the return target remains pending until focus restoration succeeds',
  )
})

test('documented web verification retains capture and API contract suites', () => {
  const command = vocabularyArchitecture.match(/npx tsx --test[^\n]+/)?.[0] ?? ''
  assert.match(command, /tests\/vocabularyCaptureTerms\.test\.ts/)
  assert.match(command, /tests\/vocabularyApiContract\.test\.ts/)
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
  assert.match(inspector, /props\.card\.status\s*===\s*['"]failed['"]\s*\|\|\s*props\.card\.generationStatus\s*===\s*['"]failed['"]/)
  assert.match(inspector, /safeExternalUrl\(source\.sourceUrl\)/)
  assert.match(inspector, /再次收藏或录入时可恢复/)
  assert.match(inspector, /card\.candidateRevisionUid/)
  assert.doesNotMatch(inspector, /无法恢复这张单词卡|永久丢失/)
})

test('partial generation warning uses stable outcome fields instead of cleared errors', () => {
  const warningBlock = inspector.match(/const generationState = computed\(\(\) => \{[\s\S]*?\n\}\)/)?.[0] ?? ''
  assert.match(warningBlock, /generationOutcome\s*===\s*['"]partial['"]/)
  assert.match(warningBlock, /warning\s*===\s*['"]markdown_unavailable['"]/)
  assert.match(warningBlock, /主题内容待完善/)
  assert.doesNotMatch(warningBlock, /!props\.card\.generationError/)
  const partialSection = inspector.match(/<section v-if="isPartialMarkdown"[\s\S]*?<\/section>/)?.[0] ?? ''
  assert.match(partialSection, /!selectedTheme\s*\|\|\s*cardOperationPending\s*\|\|\s*themesBlockingError/)
})

test('inspector styles stable editors and narrow screens without horizontal overflow', () => {
  assert.match(inspector, /min-width:\s*0/)
  assert.match(inspector, /overflow-wrap:\s*anywhere/)
  assert.match(inspector, /@media \(min-width:\s*1024px\)/)
  assert.match(inspector, /@media \(min-width:\s*768px\) and \(max-width:\s*1023px\)/)
  assert.match(inspector, /@media \(max-width:\s*767px\)/)
  assert.match(inspector, /180px/)
  assert.match(inspector, /840px/)
  assert.match(inspector, /:deep\(\[id\^="markdown-section-"\]\)[^{]*\{[^}]*scroll-margin-top:/)
  assert.match(inspector, /grid-template-columns:\s*1fr/)
})

test('edit mode uses a wide document workspace with a responsive markdown height', () => {
  assert.match(inspector, /\.card-inspector__document,\s*\.card-inspector__editor-document\s*\{[^}]*width:\s*100%/s)
  assert.match(inspector, /\.card-inspector__document\s*\{[^}]*max-width:\s*840px/s)
  assert.match(inspector, /\.card-inspector__editor-document\s*\{[^}]*max-width:\s*none/s)
  assert.match(markdownEditor, /min-height:\s*clamp\(420px,\s*calc\(100vh - 430px\),\s*720px\)/)
  assert.match(markdownEditor, /@media \(max-width:\s*767px\)[\s\S]*min-height:\s*360px/)
})

test('card detail uses a compact accessible back icon', () => {
  assert.match(inspector, /import\s*\{\s*ArrowLeft\s*\}\s*from\s*'lucide-vue-next'/)
  assert.match(inspector, /class="card-inspector__back"[^>]*aria-label="返回单词库"[^>]*title="返回单词库"/)
  assert.match(inspector, /<ArrowLeft\s+aria-hidden="true"\s*\/>/)
  assert.match(inspector, /\.card-inspector__back\s*\{[^}]*width:\s*34px[^}]*padding:\s*0/s)
})
