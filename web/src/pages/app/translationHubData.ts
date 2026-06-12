export type TranslationMode = 'immersive' | 'exam'
export type TranslationStatus = 'reading' | 'completed'
export type TranslationSourceType = 'pdf' | 'web' | 'text' | 'library'
export type TranslationFilter = 'all' | 'reading' | 'completed' | 'noted' | 'exam'

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
  title: string
  source: string
  updatedAt: string
}

export interface TranslationFilterOptions {
  filter: TranslationFilter
  query: string
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

export const myTranslations: TranslationRecord[] = [
  {
    id: 'cefr-companion-volume-1',
    title: 'CEFR Companion Volume 1',
    subtitle: 'Chapter 2 · Education and Learning',
    sourceLabel: 'PDF',
    sourceType: 'pdf',
    mode: 'immersive',
    updatedAt: '今天 10:23',
    noteCount: 12,
    progress: 68,
    status: 'reading',
  },
  {
    id: 'economist-ai-and-jobs',
    title: 'The Economist · AI and Jobs',
    subtitle: 'AI is reshaping work faster than we think',
    sourceLabel: 'Economist 外刊',
    sourceType: 'web',
    mode: 'immersive',
    updatedAt: '昨天 21:15',
    noteCount: 8,
    progress: 42,
    status: 'reading',
  },
  {
    id: 'cet6-reading-dec-2023',
    title: 'CET-6 阅读材料',
    subtitle: '2023 年 12 月 · 第 2 套',
    sourceLabel: 'PDF',
    sourceType: 'pdf',
    mode: 'exam',
    updatedAt: '05-27 09:41',
    noteCount: 21,
    progress: 50,
    status: 'reading',
  },
  {
    id: 'economics-principles',
    title: '经济学原理（节选）',
    subtitle: '中文版附录 · 第 1 章',
    sourceLabel: '粘贴文本',
    sourceType: 'text',
    mode: 'immersive',
    updatedAt: '05-26 18:30',
    noteCount: 15,
    progress: 100,
    status: 'completed',
  },
  {
    id: 'postgrad-reading-text-2',
    title: '考研英语（二）阅读理解 Text 2',
    subtitle: '2022 年真题',
    sourceLabel: 'PDF',
    sourceType: 'pdf',
    mode: 'exam',
    updatedAt: '05-25 16:05',
    noteCount: 6,
    progress: 100,
    status: 'completed',
  },
]

export const todayRecommendations: RecommendationItem[] = [
  {
    id: 'recommend-economist-education-ai',
    source: 'The Economist',
    title: 'How AI could change the future of education',
    meta: '10 分钟 · 难度：中高',
    coverLabel: 'TE',
    tone: 'red',
  },
  {
    id: 'recommend-ft-inflation',
    source: 'Financial Times',
    title: 'Global inflation shows signs of cooling',
    meta: '8 分钟 · 难度：中等',
    coverLabel: 'FT',
    tone: 'sand',
  },
  {
    id: 'recommend-nature-exoplanet',
    source: 'Nature',
    title: 'New exoplanet could support liquid water',
    meta: '12 分钟 · 难度：高',
    coverLabel: 'N',
    tone: 'black',
  },
]

export const noteStats: NoteStat[] = [
  { id: 'translations', label: '翻译篇数', value: '8' },
  { id: 'minutes', label: '阅读时长', value: '320 分钟' },
  { id: 'notes', label: '笔记数量', value: '56' },
  { id: 'terms', label: '重点词汇', value: '128' },
  { id: 'sentences', label: '句子收藏', value: '34' },
  { id: 'cards', label: '复习卡片', value: '22' },
]

export const recentNotes: RecentNote[] = [
  {
    id: 'note-ai-and-jobs',
    title: 'AI and Jobs 重点词汇',
    source: 'The Economist · AI and Jobs',
    updatedAt: '今天 10:20',
  },
  {
    id: 'note-cefr-grammar',
    title: 'Chapter 2 语法笔记',
    source: 'CEFR Companion Volume 1',
    updatedAt: '昨天 22:10',
  },
  {
    id: 'note-cet6-reading',
    title: 'CET-6 阅读技巧总结',
    source: 'CET-6 阅读材料',
    updatedAt: '05-27 09:45',
  },
]

export function filterTranslations(
  items: TranslationRecord[],
  options: TranslationFilterOptions,
): TranslationRecord[] {
  const normalizedQuery = options.query.trim().toLowerCase()

  return items.filter((item) => {
    const matchesFilter =
      options.filter === 'all'
      || (options.filter === 'reading' && item.status === 'reading')
      || (options.filter === 'completed' && item.status === 'completed')
      || (options.filter === 'noted' && item.noteCount > 0)
      || (options.filter === 'exam' && item.mode === 'exam')

    if (!matchesFilter) return false
    if (!normalizedQuery) return true

    return `${item.title} ${item.subtitle} ${item.sourceLabel}`
      .toLowerCase()
      .includes(normalizedQuery)
  })
}
