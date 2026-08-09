import type { AbilityProfile } from '@/api/user'
import type {
  WritingDashboardResponse,
  WritingStatsResponse,
} from '@/api/writing'

export type AbilityModuleKey =
  | 'writing'
  | 'vocabulary'
  | 'reading'
  | 'listening'
  | 'speaking'

export type AbilityEvidenceState =
  | 'unmeasured'
  | 'collecting'
  | 'sufficient'
  | 'stale'
  | 'unavailable'

export interface AbilityModuleSummary {
  key: AbilityModuleKey
  title: string
  levelLabel: string
  evidenceState: AbilityEvidenceState
  evidenceLabel: string
  evidenceCount: number
  actionLabel: string
  actionTo: string
}

export type AbilityPriorityAction =
  | { label: string; type: 'module'; key: AbilityModuleKey }
  | { label: string; type: 'route'; to: string }

export interface AbilityOverviewModel {
  overallLevelLabel: '待形成'
  coverageCount: number
  coverageTotal: 5
  confidenceLabel: '暂无' | '较低' | '中等' | '较高'
  confidenceSteps: 0 | 1 | 2 | 3
  modules: AbilityModuleSummary[]
  priorityText: string
  priorityAction: AbilityPriorityAction
  recentEvidence: { label: string; detail: string; timeLabel: string } | null
}

export interface AbilitySubskill {
  key: string
  label: string
  value: number | null
  valueLabel: string
  max: 100
  confidenceLabel: string
}

export interface AbilityModuleDetail extends AbilityModuleSummary {
  diagnosis: string
  trendLabel: string
  subskills: AbilitySubskill[]
  findings: Array<{ tone: 'strength' | 'focus'; text: string }>
  evidence: Array<{ id: string; title: string; scoreLabel: string; timeLabel: string }>
  history: Array<{ id: string; label: string; score: number; delta: number }>
  sourceSummary: string
}

const WRITING_MODE_TO = '/app/writing/mode'

const writingDimensions = [
  ['taskScore', '任务完成'],
  ['coherenceScore', '连贯衔接'],
  ['grammarScore', '语法准确'],
  ['vocabularyScore', '词汇丰富'],
  ['structureScore', '篇章结构'],
  ['varietyScore', '表达多样'],
] as const

const MODULE_TITLES: Record<AbilityModuleKey, string> = {
  writing: '写作',
  vocabulary: '词汇',
  reading: '阅读',
  listening: '听力',
  speaking: '口语',
}

const unavailableModules = {
  vocabulary: {
    title: '词汇能力',
    subskills: ['识别理解', '主动回忆', '语境运用'],
    actionLabel: '进入单词学习',
    actionTo: '/app/vocabulary?tab=modes',
  },
  reading: {
    title: '阅读能力',
    subskills: ['信息定位', '篇章理解', '推断分析'],
    actionLabel: '导入阅读材料',
    actionTo: '/app/translation',
  },
  listening: {
    title: '听力能力',
    subskills: ['语音辨识', '信息理解', '语篇理解'],
    actionLabel: '进入听力学习',
    actionTo: '/app/listening',
  },
  speaking: {
    title: '口语能力',
    subskills: ['发音', '流利度', '表达组织'],
    actionLabel: '进入口语学习',
    actionTo: '/app/speaking',
  },
} as const

function confidencePresentation(value: number | null | undefined) {
  if (value == null) return { label: '暂无' as const, steps: 0 as const }
  if (value >= 0.8) return { label: '较高' as const, steps: 3 as const }
  if (value >= 0.5) return { label: '中等' as const, steps: 2 as const }
  return { label: '较低' as const, steps: 1 as const }
}

function hasWritingEvidence(profile: AbilityProfile | null): boolean {
  return Boolean(
    profile
    && (profile.sampleCount ?? 0) > 0
    && [
      profile.taskScore,
      profile.coherenceScore,
      profile.grammarScore,
      profile.vocabularyScore,
      profile.structureScore,
      profile.varietyScore,
    ].some((value) => value != null),
  )
}

function unmeasuredModule(key: Exclude<AbilityModuleKey, 'writing'>): AbilityModuleSummary {
  return {
    key,
    title: MODULE_TITLES[key],
    levelLabel: '待测',
    evidenceState: 'unmeasured',
    evidenceLabel: '无证据',
    evidenceCount: 0,
    actionLabel: '等待开放',
    actionTo: '',
  }
}

function writingModule(profile: AbilityProfile | null): AbilityModuleSummary {
  const evidenceExists = hasWritingEvidence(profile)
  const evidenceCount = evidenceExists ? profile?.sampleCount ?? 0 : 0
  return {
    key: 'writing',
    title: MODULE_TITLES.writing,
    levelLabel: evidenceExists ? '待校准' : '待测',
    evidenceState: evidenceExists ? 'collecting' : 'unmeasured',
    evidenceLabel: evidenceExists ? `已收集 ${evidenceCount} 次评测` : '无证据',
    evidenceCount,
    actionLabel: evidenceExists ? '查看详情' : '开始评测',
    actionTo: evidenceExists ? '' : WRITING_MODE_TO,
  }
}

export function buildAbilityOverviewModel(
  profile: AbilityProfile | null,
): AbilityOverviewModel {
  const writing = writingModule(profile)
  const evidenceExists = hasWritingEvidence(profile)
  const confidence = confidencePresentation(profile?.confidence)

  return {
    overallLevelLabel: '待形成',
    coverageCount: evidenceExists ? 1 : 0,
    coverageTotal: 5,
    confidenceLabel: confidence.label,
    confidenceSteps: confidence.steps,
    modules: [
      writing,
      unmeasuredModule('vocabulary'),
      unmeasuredModule('reading'),
      unmeasuredModule('listening'),
      unmeasuredModule('speaking'),
    ],
    priorityText: evidenceExists
      ? '继续积累写作评测，校准能力画像。'
      : '完成首次写作评测，开始形成能力画像。',
    priorityAction: evidenceExists
      ? { label: '查看写作详情', type: 'module', key: 'writing' }
      : { label: '开始写作评测', type: 'route', to: WRITING_MODE_TO },
    recentEvidence: evidenceExists && profile?.updatedAt
      ? {
          label: '最近写作评测',
          detail: `已收集 ${profile.sampleCount ?? 0} 次评测`,
          timeLabel: profile.updatedAt,
        }
      : null,
  }
}

export function buildWritingAbilityDetail(
  profile: AbilityProfile | null,
  dashboard: WritingDashboardResponse | null,
  stats: WritingStatsResponse | null,
): AbilityModuleDetail {
  const summary = writingModule(profile)
  const scoreTrend = dashboard?.growth?.essayScoreTrend ?? []
  const subskills: AbilitySubskill[] = writingDimensions.map(([key, label]) => {
    const value = profile?.[key] ?? null
    return {
      key,
    label,
      value,
    valueLabel: value == null ? '暂无' : `${value}`,
    max: 100,
    confidenceLabel: confidencePresentation(profile?.confidence).label,
    }
  })
  const scoredSubskills = subskills.filter((item): item is AbilitySubskill & { value: number } => (
    item.value != null
  ))
  const strength = scoredSubskills.reduce<AbilitySubskill & { value: number } | null>(
    (current, item) => !current || item.value > current.value ? item : current,
    null,
  )
  const focus = scoredSubskills.reduce<AbilitySubskill & { value: number } | null>(
    (current, item) => !current || item.value < current.value ? item : current,
    null,
  )
  const aggregateErrorCount = stats
    ? stats.totalGrammarErrors + stats.totalSpellingErrors + stats.totalVocabularyErrors
    : null

  return {
    ...summary,
    actionLabel: '继续写作练习',
    actionTo: WRITING_MODE_TO,
    diagnosis: dashboard?.overview?.insight || (summary.evidenceState === 'collecting'
      ? '写作证据正在收集与校准中，暂不生成 CEFR 等级。'
      : '完成写作评测后，这里会展示写作能力证据。'),
    trendLabel: dashboard?.overview.insight ?? '暂无趋势数据',
    subskills,
    findings: [
      ...(strength ? [{ tone: 'strength' as const, text: `${strength.label}是当前相对稳定的能力。` }] : []),
      ...(focus ? [{ tone: 'focus' as const, text: `优先提升${focus.label}。` }] : []),
    ],
    evidence: scoreTrend.map((item) => ({
      id: String(item.essayNo),
      title: item.title,
      scoreLabel: `${item.score}`,
      timeLabel: formatWritingDate(item.scoredAt),
    })),
    history: scoreTrend.map((item) => ({
      id: String(item.essayNo),
      label: item.title,
      score: item.score,
      delta: item.delta,
    })),
    sourceSummary: profile?.sampleCount != null
      ? `来源：${profile.sampleCount} 次写作评测、写作趋势${aggregateErrorCount == null ? '' : `与 ${aggregateErrorCount} 项聚合错误统计`}。`
      : '来源：暂无写作评测样本。',
  }
}

export function buildUnavailableAbilityDetail(
  key: Exclude<AbilityModuleKey, 'writing'>,
): AbilityModuleDetail {
  const config = unavailableModules[key]

  return {
    key,
    title: config.title,
    levelLabel: '待测',
    evidenceState: 'unmeasured',
    evidenceLabel: '暂无有效证据',
    evidenceCount: 0,
    actionLabel: config.actionLabel,
    actionTo: config.actionTo,
    diagnosis: '该模块尚未完成有效评估，当前不生成能力结论。',
    trendLabel: '暂无趋势数据',
    subskills: config.subskills.map((label, index) => ({
      key: `${key}-${index + 1}`,
      label,
      value: null,
      valueLabel: '暂无',
      max: 100,
      confidenceLabel: '暂无',
    })),
    findings: [],
    evidence: [],
    history: [],
    sourceSummary: '来源：暂无有效评估样本。',
  }
}

function formatWritingDate(value: string | null | undefined): string {
  if (!value || Number.isNaN(new Date(value).getTime())) return '时间未知'
  return /^\d{4}-\d{2}-\d{2}/.test(value) ? value.slice(0, 10) : value
}
