import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  buildWorkspaceNoteStats,
  buildWorkspaceRecentNotes,
  deriveWorkspaceRecord,
  filterTranslations,
  hubQuickActions,
  materialCategories,
  myTranslations,
  noteStats,
  recentNotes,
  todayRecommendations,
} from '../src/pages/app/translationHubData.ts'

const pageSource = readFileSync(
  new URL('../src/pages/app/TranslationPage.vue', import.meta.url),
  'utf8',
)
const hubDataSource = readFileSync(
  new URL('../src/pages/app/translationHubData.ts', import.meta.url),
  'utf8',
)

assert.ok(hubQuickActions.length >= 3, 'translation hub should show at least three non-duplicated quick actions')
assert.equal(todayRecommendations.length, 0, 'translation hub should not render mock recommendations')
assert.equal(myTranslations.length, 0, 'translation hub should not render mock translation records')
assert.equal(recentNotes.length, 0, 'translation hub should not render mock recent notes')
assert.ok(noteStats.length >= 6, 'translation hub should show a compact six-metric note summary')

for (const category of ['经济学人', '外刊新闻', '学术期刊', '考试材料', '技术文档', '用户导入']) {
  assert.ok(
    materialCategories.some((item) => item.title === category),
    `translation material library should include ${category}`,
  )
}

const sampleRecords = [
  {
    id: 'pdf-reading',
    title: 'PDF 资料',
    subtitle: '用户导入',
    sourceLabel: 'PDF',
    sourceType: 'pdf' as const,
    mode: 'immersive' as const,
    updatedAt: '刚刚',
    noteCount: 1,
    progress: 20,
    status: 'reading' as const,
  },
  {
    id: 'web-reading',
    title: '网页资料',
    subtitle: '用户导入',
    sourceLabel: 'WEB',
    sourceType: 'web' as const,
    mode: 'immersive' as const,
    updatedAt: '刚刚',
    noteCount: 0,
    progress: 10,
    status: 'reading' as const,
  },
  {
    id: 'text-completed',
    title: '文本资料',
    subtitle: '用户导入',
    sourceLabel: '粘贴文本',
    sourceType: 'text' as const,
    mode: 'exam' as const,
    updatedAt: '昨天 09:00',
    noteCount: 2,
    progress: 100,
    status: 'completed' as const,
  },
]

assert.ok(
  filterTranslations(sampleRecords, { filter: 'reading', query: '', sourceType: 'all' }).every((item) => item.status === 'reading'),
  'reading filter should only return reading translations',
)

assert.ok(
  filterTranslations(sampleRecords, { filter: 'completed', query: '', sourceType: 'all' }).every((item) => item.status === 'completed'),
  'completed filter should only return completed translations',
)

assert.ok(
  filterTranslations(sampleRecords, { filter: 'noted', query: '', sourceType: 'all' }).every((item) => item.noteCount > 0),
  'noted filter should only return translations with notes',
)

assert.ok(
  filterTranslations(sampleRecords, { filter: 'exam', query: '', sourceType: 'all' }).every((item) => item.mode === 'exam'),
  'exam filter should only return exam translation records',
)

assert.deepEqual(
  filterTranslations(sampleRecords, { filter: 'all', query: '', sourceType: 'pdf' }).map((item) => item.id),
  ['pdf-reading'],
  'source type filter should only return the selected file format',
)

const workspaceRecord = deriveWorkspaceRecord(
  {
    id: 'translation-doc-1',
    title: '数据结构 C++',
    subtitle: 'PDF · 514 页',
    sourceLabel: 'PDF',
    sourceType: 'pdf',
    mode: 'immersive',
    updatedAt: '刚刚',
    noteCount: 0,
    progress: 0,
    status: 'reading',
  },
  {
    currentPage: 48,
    updatedAt: '2026-06-25T00:00:00+08:00',
    studyNotes: [
      {
        id: 'note-1',
        documentId: 'translation-doc-1',
        pageNumber: 48,
        blockId: 'p48',
        elementId: 'p48',
        title: '向量初始化',
        content: '记录 vector 相关概念',
        source: 'manual',
        status: 'saved',
        tags: [],
        updatedAt: '2026-06-25T00:00:00+08:00',
      },
    ],
    userBookmarks: [],
    collapsedOutlineItemIds: [],
  },
  514,
)
assert.equal(workspaceRecord.noteCount, 1, 'workspace record should show persisted study note count')
assert.equal(workspaceRecord.progress, 9, 'workspace record should derive progress from current PDF page')
assert.equal(workspaceRecord.updatedAt, '今天 00:00', 'workspace record should show workspace updated time')

const workspaceNotes = buildWorkspaceRecentNotes([{
  document: workspaceRecord,
  studyNotes: [
    {
      id: 'note-1',
      documentId: 'translation-doc-1',
      pageNumber: 48,
      blockId: 'p48',
      elementId: 'p48',
      title: '向量初始化',
      content: '记录 vector 相关概念',
      source: 'manual',
      status: 'saved',
      tags: [],
      updatedAt: '2026-06-25T00:00:00+08:00',
    },
  ],
}])
assert.equal(workspaceNotes[0]?.title, '向量初始化', 'recent notes should prefer persisted workspace notes')
assert.equal(workspaceNotes[0]?.source, '数据结构 C++ · Page 48', 'recent notes should point back to the source document and page')

const workspaceStats = buildWorkspaceNoteStats([workspaceRecord], workspaceNotes)
assert.equal(workspaceStats.find((item) => item.id === 'notes')?.value, '1', 'note stats should reflect workspace notes')

const economistMatches = filterTranslations(sampleRecords, { filter: 'all', query: 'PDF', sourceType: 'all' })
assert.ok(economistMatches.length > 0, 'keyword search should match titles and descriptions')
assert.ok(
  economistMatches.every((item) => `${item.title} ${item.subtitle} ${item.sourceLabel}`.toLowerCase().includes('pdf')),
  'keyword search should filter by title, subtitle, or source label',
)

for (const requiredCopy of [
  '翻译中心',
  '找到素材，继续阅读，整理你的双语学习笔记',
  '学习入口',
  '素材库',
  '我的翻译',
  '今日推荐',
  '我的笔记摘要',
  '最近笔记',
  '新建翻译',
]) {
  assert.ok(pageSource.includes(requiredCopy), `translation hub page should render ${requiredCopy}`)
}

assert.ok(
  pageSource.includes('refreshCreatedTranslationsFromKnowledge'),
  'translation hub should refresh local drafts with backend workspaceState after returning from the workspace',
)
assert.ok(
  pageSource.includes('workspaceRecentNotes'),
  'translation hub should render recent notes from persisted workspaceState',
)
assert.ok(
  pageSource.includes('openTranslationNotes(item)'),
  'translation hub note count should open the workspace note surface',
)
assert.ok(
  pageSource.includes("query: { noteId:"),
  'translation hub should pass a note id when opening a previous note',
)
assert.ok(
  pageSource.includes('activeSourceType'),
  'translation hub should expose a format filter state',
)
assert.ok(
  pageSource.includes('全部格式'),
  'translation hub should render an all-format option',
)
assert.ok(
  pageSource.includes('@dblclick="startInlineTitleRename(item)"'),
  'translation hub should let users double-click an imported title to rename it',
)
assert.ok(
  pageSource.includes('commitInlineTitleRename'),
  'translation hub inline rename should save the edited title',
)
assert.ok(
  pageSource.includes('cancelInlineTitleRename'),
  'translation hub inline rename should support canceling the edit',
)
assert.ok(
  !pageSource.includes('@click="renameTranslationRecord(item)"'),
  'translation hub should not keep rename as a separate row action',
)

assert.ok(!pageSource.includes('id="continue-title"'), 'translation hub should not duplicate history as a continue-learning section')
assert.ok(!pageSource.includes('continue-grid'), 'translation hub should not render recent translation cards above my translations')
assert.ok(!pageSource.includes('translateEssay'), 'translation hub should not call the writing translation API')
assert.ok(!pageSource.includes('翻译练习'), 'translation hub should replace the old translation practice hero')
for (const mockTitle of ['CEFR Companion Volume 1', 'The Economist · AI and Jobs', 'CET-6 阅读材料', '经济学原理（节选）']) {
  assert.ok(!hubDataSource.includes(mockTitle), `translation hub data should remove mock title ${mockTitle}`)
}

console.log('translation-hub-data-ok')
