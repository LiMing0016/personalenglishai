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

export interface AbilityOverviewModel {
  overallLevelLabel: '待形成'
  coverageCount: number
  coverageTotal: 5
  confidenceLabel: '暂无' | '较低' | '中等' | '较高'
  confidenceSteps: 0 | 1 | 2 | 3
  modules: AbilityModuleSummary[]
  priorityText: string
  priorityAction: { label: string; to: string }
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

type AbilityProfileInput = AbilityProfile & { confidence?: number | null }

const WRITING_DETAIL_TO = '/app/me?tab=profile&ability=writing'
const WRITING_MODE_TO = '/app/writing/mode'

const MODULE_TITLES: Record<AbilityModuleKey, string> = {
  writing: '写作',
  vocabulary: '词汇',
  reading: '阅读',
  listening: '听力',
  speaking: '口语',
}

function confidencePresentation(value: number | null | undefined) {
  if (value == null) return { label: '暂无' as const, steps: 0 as const }
  if (value >= 0.8) return { label: '较高' as const, steps: 3 as const }
  if (value >= 0.5) return { label: '中等' as const, steps: 2 as const }
  return { label: '较低' as const, steps: 1 as const }
}

function hasWritingEvidence(profile: AbilityProfileInput | null): boolean {
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

function writingModule(profile: AbilityProfileInput | null): AbilityModuleSummary {
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
    actionTo: evidenceExists ? WRITING_DETAIL_TO : WRITING_MODE_TO,
  }
}

export function buildAbilityOverviewModel(
  profile: AbilityProfileInput | null,
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
      ? { label: '查看写作详情', to: WRITING_DETAIL_TO }
      : { label: '开始写作评测', to: WRITING_MODE_TO },
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
  profile: AbilityProfileInput | null,
  dashboard: WritingDashboardResponse | null,
  stats: WritingStatsResponse | null,
): AbilityModuleDetail {
  const summary = writingModule(profile)
  const scoreTrend = dashboard?.growth.essayScoreTrend ?? []
  const subskillDefinitions: Array<[string, string, number | null | undefined]> = [
    ['task', '任务完成', profile?.taskScore],
    ['coherence', '连贯衔接', profile?.coherenceScore],
    ['grammar', '语法准确', profile?.grammarScore],
    ['vocabulary', '词汇运用', profile?.vocabularyScore],
    ['structure', '篇章结构', profile?.structureScore],
    ['variety', '表达多样性', profile?.varietyScore],
  ]
  const subskills: AbilitySubskill[] = subskillDefinitions.map(([key, label, value]) => ({
    key,
    label,
    value: value ?? null,
    valueLabel: value == null ? '暂无' : `${value}`,
    max: 100,
    confidenceLabel: confidencePresentation(profile?.confidence).label,
  }))

  return {
    ...summary,
    diagnosis: summary.evidenceState === 'collecting'
      ? '写作证据正在收集与校准中，暂不生成 CEFR 等级。'
      : '完成写作评测后，这里会展示写作能力证据。',
    trendLabel: dashboard?.overview.insight ?? '暂无趋势数据',
    subskills,
    findings: stats?.avgGrammarScore != null
      ? [{ tone: 'focus', text: '继续通过写作评测积累可用证据。' }]
      : [],
    evidence: scoreTrend.map((item) => ({
      id: String(item.essayNo),
      title: item.title,
      scoreLabel: `${item.score}`,
      timeLabel: item.scoredAt,
    })),
    history: scoreTrend.map((item) => ({
      id: String(item.essayNo),
      label: item.title,
      score: item.score,
      delta: item.delta,
    })),
    sourceSummary: '来源：写作评测、写作趋势与写作统计。',
  }
}
