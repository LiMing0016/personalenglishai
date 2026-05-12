export type WritingDashboardRange = '7d' | '14d' | '30d' | 'year' | 'all' | 'custom'
export type WritingDashboardMode = 'all' | 'free' | 'exam'

export interface WritingDashboardCustomRange {
  start: string
  end: string
}

export interface WritingOverviewTrendPoint {
  date: string
  essayCount: number
  submissionCount: number
  averageScore: number
  bestScore: number
}

export interface WritingOverviewSummary {
  totalEssays: number
  totalSubmissions: number
  averageScore: number
  bestScore: number
}

export interface WritingOverviewData {
  scopeLabel: string
  summary: WritingOverviewSummary
  trend: WritingOverviewTrendPoint[]
  insight: string
}

export interface DailyWritingPrompt {
  id: string
  level: string
  genre: string
  title: string
  description: string
  wordRange: string
  estimatedMinutes: number
}

export interface WritingEssayScoreTrendPoint {
  essayNo: number
  title: string
  mode: 'free' | 'exam'
  score: number
  scoredAt: string
  delta: number
  aiSuggestion: string
}

export interface WritingScoreDistributionBucket {
  key: string
  label: string
  stage: string
  min: number | null
  max: number
  count: number
  percent: number
  color: string
  backgroundColor: string
}

export interface WritingScoreBand {
  key: string
  label: string
  min: number
  max: number
  color: string
}

export const dashboardRangeOptions: Array<{ value: WritingDashboardRange; label: string }> = [
  { value: '7d', label: '近7天' },
  { value: '14d', label: '近14天' },
  { value: '30d', label: '近30天' },
  { value: 'year', label: '近1年' },
  { value: 'all', label: '全部' },
]

export const dashboardModeOptions: Array<{ value: WritingDashboardMode; label: string }> = [
  { value: 'all', label: '全部' },
  { value: 'free', label: '自由' },
  { value: 'exam', label: '考试' },
]

export const overviewSparkLines = {
  essays: [18, 22, 20, 24, 27, 25, 30, 34, 32, 38, 41, 48],
  submissions: [10, 13, 15, 14, 18, 21, 19, 24, 26, 29, 31, 36],
  average: [66, 68, 67, 70, 72, 71, 74, 75, 76, 77, 78, 79],
  best: [76, 78, 80, 79, 84, 86, 85, 88, 89, 90, 91, 92],
}

export const mockWritingOverview: WritingOverviewData = {
  scopeLabel: '近30天 · 全部模式 · 按每篇最新评分',
  summary: {
    totalEssays: 2,
    totalSubmissions: 1,
    averageScore: 78,
    bestScore: 92,
  },
  trend: [
    { date: '05-10', essayCount: 0, submissionCount: 0, averageScore: 64, bestScore: 76 },
    { date: '05-17', essayCount: 1, submissionCount: 0, averageScore: 68, bestScore: 80 },
    { date: '05-24', essayCount: 0, submissionCount: 1, averageScore: 72, bestScore: 84 },
    { date: '05-31', essayCount: 1, submissionCount: 0, averageScore: 74, bestScore: 88 },
    { date: '06-07', essayCount: 0, submissionCount: 0, averageScore: 77, bestScore: 90 },
    { date: '06-14', essayCount: 0, submissionCount: 0, averageScore: 78, bestScore: 92 },
  ],
  insight: '本周期评分样本偏少，建议完成 3 次以上评分后观察趋势。',
}

export const mockDailyWritingPrompts: DailyWritingPrompt[] = [
  {
    id: 'technology-life',
    level: '中等',
    genre: '议论文',
    title: '科技让生活更美好吗？',
    description: '科技发展在带来便利的同时，也可能带来新的问题。你认为科技让生活更美好吗？为什么？',
    wordRange: '120-180 词',
    estimatedMinutes: 30,
  },
  {
    id: 'phone-time-chart',
    level: '中等',
    genre: '图表作文',
    title: '手机使用时间变化趋势图',
    description: '根据图表描述不同年龄段人群每天使用手机的平均时间，并分析原因和影响。',
    wordRange: '120-180 词',
    estimatedMinutes: 30,
  },
  {
    id: 'school-suggestion',
    level: '简单',
    genre: '应用文',
    title: '给校报投稿：校园活动建议',
    description: '你是学生会成员，请给校报写一封信，建议增加一个校园活动，并说明理由。',
    wordRange: '80-120 词',
    estimatedMinutes: 20,
  },
]

export const mockGrowthDashboard = {
  scoreBands: [
    { key: 'under-60', label: '需要补基础', min: 0, max: 60, color: '#F7D8D4' },
    { key: '60-70', label: '基础建立', min: 60, max: 70, color: '#F3E0BD' },
    { key: '70-80', label: '稳定提升', min: 70, max: 80, color: '#E7E8C8' },
    { key: '80-90', label: '良好', min: 80, max: 90, color: '#D7EADD' },
    { key: '90-100', label: '优秀', min: 90, max: 100, color: '#D8E6F2' },
  ] satisfies WritingScoreBand[],
  essayScoreTrend: [
    {
      essayNo: 1,
      title: '大学英语四级写作真题 2025-04 第1套',
      mode: 'exam',
      score: 58,
      scoredAt: '2026-04-13 20:10',
      delta: 0,
      aiSuggestion: '先补充基础句型，减少主谓一致和时态错误。',
    },
    {
      essayNo: 2,
      title: '旅行的意义',
      mode: 'free',
      score: 66,
      scoredAt: '2026-04-18 21:35',
      delta: 8,
      aiSuggestion: '段落结构更清楚了，继续练习理由展开。',
    },
    {
      essayNo: 3,
      title: '手机使用时间变化趋势图',
      mode: 'exam',
      score: 72,
      scoredAt: '2026-04-24 19:22',
      delta: 6,
      aiSuggestion: '图表描述较完整，注意增加趋势对比词。',
    },
    {
      essayNo: 4,
      title: 'My View on Artificial Intelligence',
      mode: 'free',
      score: 78,
      scoredAt: '2026-04-30 22:06',
      delta: 6,
      aiSuggestion: '观点表达更稳定，可以增加复杂句。',
    },
    {
      essayNo: 5,
      title: '科技让生活更美好吗？',
      mode: 'exam',
      score: 82,
      scoredAt: '2026-05-04 20:18',
      delta: 4,
      aiSuggestion: '句式复杂度提升明显，继续练习观点展开。',
    },
    {
      essayNo: 6,
      title: '给校报投稿：校园活动建议',
      mode: 'exam',
      score: 88,
      scoredAt: '2026-05-08 21:41',
      delta: 6,
      aiSuggestion: '应用文语气自然，注意结尾呼应建议。',
    },
    {
      essayNo: 7,
      title: '自由写作 2026-05-11 20:33',
      mode: 'free',
      score: 92,
      scoredAt: '2026-05-11 23:18',
      delta: 4,
      aiSuggestion: '论证连贯性很好，可以继续挑战更正式的学术表达。',
    },
  ] satisfies WritingEssayScoreTrendPoint[],
  scoreDistribution: [
    { key: 'under-60', label: '<60', stage: '需要补基础', min: null, max: 60, count: 1, percent: 9, color: '#D97A72', backgroundColor: '#F7D8D4' },
    { key: '60-70', label: '60-70', stage: '基础建立', min: 60, max: 70, count: 1, percent: 9, color: '#D49A45', backgroundColor: '#F3E0BD' },
    { key: '70-80', label: '70-80', stage: '稳定提升', min: 70, max: 80, count: 2, percent: 18, color: '#A7B45F', backgroundColor: '#E7E8C8' },
    { key: '80-90', label: '80-90', stage: '良好', min: 80, max: 90, count: 5, percent: 46, color: '#63AE86', backgroundColor: '#D7EADD' },
    { key: '90-100', label: '90-100', stage: '优秀', min: 90, max: 100, count: 2, percent: 18, color: '#6999C2', backgroundColor: '#D8E6F2' },
  ] satisfies WritingScoreDistributionBucket[],
  streakDays: 12,
  bestStreakDays: 15,
  monthlyGoalDone: 2,
  monthlyGoalTotal: 3,
}

export const mockAbilityDashboard = {
  level: {
    currentLevel: 'B1+',
    targetLevel: 'B2',
    progressToNext: 72,
    basisText: 'CEFR 参考等级，基于最近 5 次评分估算',
    gapText: '距离 B2 还差 8 分',
    focus: ['复杂句', '观点展开', '学术表达'],
  },
  growthItems: [
    { label: '语法准确性', delta: 7 },
    { label: '结构连贯', delta: 5 },
    { label: '词汇丰富度', delta: 3 },
    { label: '句式复杂度', delta: -1 },
  ],
  trend: [
    { date: '05-10', overall: 64, vocabulary: 60, grammar: 62, coherence: 66, sentence: 58 },
    { date: '05-17', overall: 68, vocabulary: 64, grammar: 66, coherence: 70, sentence: 60 },
    { date: '05-24', overall: 72, vocabulary: 70, grammar: 72, coherence: 73, sentence: 67 },
    { date: '05-31', overall: 75, vocabulary: 73, grammar: 78, coherence: 76, sentence: 70 },
    { date: '06-07', overall: 77, vocabulary: 75, grammar: 80, coherence: 78, sentence: 72 },
    { date: '06-14', overall: 82, vocabulary: 78, grammar: 84, coherence: 81, sentence: 74 },
  ],
  diagnostics: [
    { label: '冠词错误', count: 32, tone: 'danger' },
    { label: '时态错误', count: 24, tone: 'warning' },
    { label: '主谓一致', count: 18, tone: 'warning' },
    { label: '介词搭配', count: 12, tone: 'success' },
  ],
  metrics: [
    { label: '高级词占比', value: '18%', percent: 18 },
    { label: '重复词比例', value: '9%', percent: 9 },
    { label: '平均句长', value: '16.8 词', percent: 68 },
  ],
}

export const mockTopicStyleDashboard = {
  topics: [
    { label: 'education', weight: 5 },
    { label: 'environment', weight: 4 },
    { label: 'technology', weight: 4 },
    { label: 'campus life', weight: 3 },
    { label: 'culture', weight: 3 },
    { label: 'travel', weight: 2 },
    { label: 'friendship', weight: 2 },
  ],
  genres: [
    { label: '议论文', percent: 45 },
    { label: '应用文', percent: 25 },
    { label: '图表作文', percent: 18 },
    { label: '书信', percent: 12 },
  ],
  nextPrompt: {
    title: 'Technology and Education',
    reason: '科技类话题练习较少，适合补齐主题覆盖。',
    level: '中等',
  },
}
