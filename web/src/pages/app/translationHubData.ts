import type {
  TranslationDocumentStudyNoteDto,
  TranslationDocumentWorkspaceStateDto,
} from '@/api/translation'

export type TranslationMode = 'immersive' | 'exam'
export type TranslationStatus = 'reading' | 'completed'
export type TranslationSourceType = 'pdf' | 'web' | 'text' | 'library'
export type TranslationFilter = 'all' | 'reading' | 'completed' | 'noted' | 'exam'
export type TranslationSourceFilter = 'all' | TranslationSourceType

export interface HubQuickAction {
  id: string
  title: string
  description: string
  meta: string
  actionLabel: string
  tone: 'primary' | 'mint' | 'amber'
  target: 'create' | 'materials' | 'notes'
}

export interface MaterialCategory {
  id: string
  title: string
  icon: string
  description: string
  ability: string
  difficulty: '中等' | '高' | '自定义'
  readingTime: string
  countLabel: string
  tone: 'mint' | 'blue' | 'amber' | 'violet' | 'teal' | 'neutral'
}

export interface TranslationRecord {
  id: string
  title: string
  subtitle: string
  sourceLabel: string
  sourceType: TranslationSourceType
  mode: TranslationMode
  updatedAt: string
  noteCount: number
  progress: number
  status: TranslationStatus
  activeNoteId?: string | null
}

export interface RecommendationItem {
  id: string
  source: string
  title: string
  meta: string
  coverLabel: string
  tone: 'red' | 'sand' | 'black'
}

export interface NoteStat {
  id: string
  label: string
  value: string
}

export interface RecentNote {
  id: string
  documentId?: string | null
  title: string
  source: string
  updatedAt: string
}

export interface WorkspaceRecentNoteSource {
  document: TranslationRecord
  studyNotes: TranslationDocumentStudyNoteDto[]
}

export interface TranslationFilterOptions {
  filter: TranslationFilter
  query: string
  sourceType?: TranslationSourceFilter
}

export const hubQuickActions: HubQuickAction[] = [
  {
    id: 'create-translation',
    title: '导入一篇新材料',
    description: '上传 PDF / TXT / MD，或粘贴文章，进入沉浸式翻译。',
    meta: '适合自选材料',
    actionLabel: '新建翻译',
    tone: 'primary',
    target: 'create',
  },
  {
    id: 'browse-materials',
    title: '从素材库开始',
    description: '按阅读、写作素材、考试拆题等目标挑选材料。',
    meta: '适合不知道读什么',
    actionLabel: '浏览素材',
    tone: 'mint',
    target: 'materials',
  },
  {
    id: 'review-notes',
    title: '整理最近笔记',
    description: '回看本周重点词汇、句型收藏和复习卡片。',
    meta: '适合复盘巩固',
    actionLabel: '查看笔记',
    tone: 'amber',
    target: 'notes',
  },
]

export const materialCategories: MaterialCategory[] = [
  {
    id: 'economist',
    title: '经济学人',
    icon: 'DOC',
    description: '深度报道与评论，洞察全球趋势',
    ability: '写作素材 · 观点表达',
    difficulty: '中等',
    readingTime: '10-20 分钟',
    countLabel: '124 篇素材',
    tone: 'mint',
  },
  {
    id: 'foreign-news',
    title: '外刊新闻',
    icon: 'WEB',
    description: '精选国际主流媒体，每日更新',
    ability: '泛读 · 新闻词汇',
    difficulty: '中等',
    readingTime: '5-15 分钟',
    countLabel: '386 篇素材',
    tone: 'blue',
  },
  {
    id: 'journals',
    title: '学术期刊',
    icon: 'BOOK',
    description: '学术观点与研究论文，拓展专业视野',
    ability: '长难句 · 术语积累',
    difficulty: '高',
    readingTime: '15-30 分钟',
    countLabel: '217 篇素材',
    tone: 'amber',
  },
  {
    id: 'exam-materials',
    title: '考试材料',
    icon: 'TASK',
    description: '四六级、考研等真题与模拟题',
    ability: '考试拆题 · 精读',
    difficulty: '中等',
    readingTime: '15-25 分钟',
    countLabel: '562 篇素材',
    tone: 'violet',
  },
  {
    id: 'technical-docs',
    title: '技术文档',
    icon: 'DEV',
    description: '技术文章与文档，提升专业英语',
    ability: '术语 · 结构化阅读',
    difficulty: '高',
    readingTime: '10-25 分钟',
    countLabel: '198 篇素材',
    tone: 'teal',
  },
  {
    id: 'user-imports',
    title: '用户导入',
    icon: 'UP',
    description: '上传或粘贴你的材料，开始翻译学习',
    ability: '自选材料 · 笔记沉淀',
    difficulty: '自定义',
    readingTime: '由材料决定',
    countLabel: '我的导入',
    tone: 'neutral',
  },
]

export const myTranslations: TranslationRecord[] = []

export const todayRecommendations: RecommendationItem[] = []

export const noteStats: NoteStat[] = [
  { id: 'translations', label: '翻译篇数', value: '0' },
  { id: 'minutes', label: '阅读时长', value: '0 分钟' },
  { id: 'notes', label: '笔记数量', value: '0' },
  { id: 'terms', label: '重点词汇', value: '0' },
  { id: 'sentences', label: '句子收藏', value: '0' },
  { id: 'cards', label: '复习卡片', value: '0' },
]

export const recentNotes: RecentNote[] = []

export function filterTranslations(
  items: TranslationRecord[],
  options: TranslationFilterOptions,
): TranslationRecord[] {
  const normalizedQuery = options.query.trim().toLowerCase()

  return items.filter((item) => {
    const matchesSourceType = !options.sourceType || options.sourceType === 'all' || item.sourceType === options.sourceType
    const matchesFilter =
      options.filter === 'all'
      || (options.filter === 'reading' && item.status === 'reading')
      || (options.filter === 'completed' && item.status === 'completed')
      || (options.filter === 'noted' && item.noteCount > 0)
      || (options.filter === 'exam' && item.mode === 'exam')

    if (!matchesSourceType || !matchesFilter) return false
    if (!normalizedQuery) return true

    return `${item.title} ${item.subtitle} ${item.sourceLabel}`
      .toLowerCase()
      .includes(normalizedQuery)
  })
}

export function deriveWorkspaceRecord(
  record: TranslationRecord,
  workspaceState: TranslationDocumentWorkspaceStateDto | null | undefined,
  pageCount?: number | null,
): TranslationRecord {
  if (!workspaceState) return record
  const currentPage = normalizePositiveInteger(workspaceState.currentPage ?? null)
  const resolvedPageCount = normalizePositiveInteger(pageCount ?? null)
  const progress = currentPage && resolvedPageCount
    ? Math.min(100, Math.max(0, Math.round((currentPage / resolvedPageCount) * 100)))
    : record.progress

  return {
    ...record,
    updatedAt: formatHubDateTime(workspaceState.updatedAt) ?? record.updatedAt,
    noteCount: workspaceState.studyNotes?.length ?? 0,
    activeNoteId: workspaceState.activeNoteId ?? resolveLatestStudyNoteId(workspaceState.studyNotes ?? []),
    progress,
    status: progress >= 100 ? 'completed' : record.status,
  }
}

export function buildWorkspaceRecentNotes(sources: WorkspaceRecentNoteSource[], limit = 3): RecentNote[] {
  return sources
    .flatMap((source) => source.studyNotes.map((note) => ({
      id: note.id,
      documentId: note.documentId || source.document.id,
      title: note.title,
      source: `${source.document.title} · Page ${note.pageNumber || 1}`,
      updatedAt: formatHubDateTime(note.updatedAt) ?? source.document.updatedAt,
      sortKey: Date.parse(note.updatedAt ?? '') || 0,
    })))
    .sort((left, right) => right.sortKey - left.sortKey)
    .slice(0, limit)
    .map(({ sortKey: _sortKey, ...note }) => note)
}

export function buildWorkspaceNoteStats(records: TranslationRecord[], notes: RecentNote[]): NoteStat[] {
  const totalNotes = records.reduce((sum, record) => sum + record.noteCount, 0)
  const completedCount = records.filter((record) => record.status === 'completed').length
  return noteStats.map((stat) => {
    if (stat.id === 'translations') return { ...stat, value: String(records.length) }
    if (stat.id === 'notes') return { ...stat, value: String(totalNotes) }
    if (stat.id === 'cards') return { ...stat, value: String(notes.length) }
    if (stat.id === 'minutes') return { ...stat, value: `${Math.max(0, records.length * 20)} 分钟` }
    if (stat.id === 'sentences') return { ...stat, value: String(completedCount) }
    return stat
  })
}

function normalizePositiveInteger(value: number | null): number | null {
  if (!Number.isFinite(value)) return null
  return Math.max(1, Math.floor(Number(value)))
}

function resolveLatestStudyNoteId(notes: TranslationDocumentStudyNoteDto[]): string | null {
  return [...notes]
    .sort((left, right) => (Date.parse(right.updatedAt ?? '') || 0) - (Date.parse(left.updatedAt ?? '') || 0))[0]
    ?.id ?? null
}

function formatHubDateTime(value: string | null | undefined): string | null {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  const now = new Date()
  const sameDay = date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate()
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  const isYesterday = date.getFullYear() === yesterday.getFullYear()
    && date.getMonth() === yesterday.getMonth()
    && date.getDate() === yesterday.getDate()
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  if (sameDay) return `今天 ${time}`
  if (isYesterday) return `昨天 ${time}`
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${time}`
}
