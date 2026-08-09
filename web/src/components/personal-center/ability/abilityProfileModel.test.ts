import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildAbilityOverviewModel,
  buildUnavailableAbilityDetail,
  type AbilityModuleKey,
} from './abilityProfileModel.ts'
import { buildWritingAbilityDetail } from './abilityProfileModel.ts'

const moduleKeys: AbilityModuleKey[] = [
  'writing',
  'vocabulary',
  'reading',
  'listening',
  'speaking',
]

test('总览固定展示五项英语能力且不包含学习助手', () => {
  const overview = buildAbilityOverviewModel(null)
  assert.deepEqual(overview.modules.map((item) => item.key), moduleKeys)
  assert.equal(overview.overallLevelLabel, '待形成')
  assert.equal(overview.coverageCount, 0)
})

test('写作评测只形成待校准证据，不由前端生成 CEFR', () => {
  const overview = buildAbilityOverviewModel({
    taskScore: 68,
    coherenceScore: 72,
    grammarScore: 61,
    vocabularyScore: 64,
    structureScore: 70,
    varietyScore: 58,
    assessedScore: 66,
    confidence: 0.7,
    sampleCount: 4,
    updatedAt: '2026-08-09T12:00:00+08:00',
  })
  const writing = overview.modules.find((item) => item.key === 'writing')
  assert.equal(overview.coverageCount, 1)
  assert.equal(writing?.levelLabel, '待校准')
  assert.equal(writing?.evidenceState, 'collecting')
  assert.equal(writing?.evidenceCount, 4)
  assert.equal(overview.modules.find((item) => item.key === 'vocabulary')?.levelLabel, '待测')
})

test('写作详情复用六项真实能力并保留原始 0-100 口径', () => {
  const detail = buildWritingAbilityDetail(
    {
      taskScore: 68,
      coherenceScore: 72,
      grammarScore: 61,
      vocabularyScore: 64,
      structureScore: 70,
      varietyScore: 58,
      assessedScore: 66,
      confidence: 0.7,
      sampleCount: 4,
      updatedAt: '2026-08-09T12:00:00+08:00',
    },
    {
      scope: { range: 'all', mode: 'all', scorePolicy: 'latest', start: '2026-01-01', end: '2026-08-09', granularity: 'month' },
      overview: { summary: { totalEssays: 4, totalSubmissions: 5, averageScore: 66, bestScore: 75 }, trend: [], insight: '结构稳定，继续提升表达。' },
      growth: {
        essayScoreTrend: [{ essayNo: 1, title: 'Campus life', mode: 'free', score: 66, scoredAt: '2026-08-09T12:00:00+08:00', delta: 4, aiSuggestion: '加强衔接' }],
        scoreDistribution: [], scoreBands: [], highScorePercent: 0, scoreScatter: [],
        monthlyGoal: { done: 1, target: 3, remaining: 2 },
        streak: { currentDays: 1, bestDays: 2, activeDays: 2 },
        insight: '结构稳定，继续提升表达。',
      },
    },
    {
      avgContentQuality: 67,
      avgTaskAchievement: 68,
      avgStructureScore: 70,
      avgVocabularyScore: 64,
      avgGrammarScore: 61,
      avgExpressionScore: 58,
      totalGrammarErrors: 8,
      totalSpellingErrors: 2,
      totalVocabularyErrors: 4,
    },
  )

  assert.equal(detail.levelLabel, '待校准')
  assert.deepEqual(detail.subskills.map((item) => item.value), [68, 72, 61, 64, 70, 58])
  assert.equal(detail.evidence[0]?.title, 'Campus life')
  assert.equal(detail.history[0]?.score, 66)
  assert.match(detail.sourceSummary, /4 次写作评测/)
})

test('写作详情允许 Dashboard 或统计接口部分失败', () => {
  const detail = buildWritingAbilityDetail(null, null, null)
  assert.equal(detail.levelLabel, '待测')
  assert.equal(detail.subskills.every((item) => item.value == null), true)
  assert.deepEqual(detail.evidence, [])
  assert.deepEqual(detail.history, [])
})

test('写作详情下一步继续写作而不是链接当前详情', () => {
  const detail = buildWritingAbilityDetail(
    {
      taskScore: 68,
      coherenceScore: 72,
      grammarScore: 61,
      vocabularyScore: 64,
      structureScore: 70,
      varietyScore: 58,
      assessedScore: 66,
      confidence: 0.7,
      sampleCount: 1,
      updatedAt: '2026-08-09T12:00:00+08:00',
    },
    null,
    null,
  )

  assert.equal(detail.actionTo, '/app/writing/mode')
  assert.equal(detail.actionLabel, '继续写作练习')
})

test('未接入模块沿用统一详情结构但不生成能力结论', () => {
  const detail = buildUnavailableAbilityDetail('vocabulary')
  assert.equal(detail.title, '词汇能力')
  assert.equal(detail.levelLabel, '待测')
  assert.equal(detail.evidenceState, 'unmeasured')
  assert.deepEqual(detail.subskills.map((item) => item.label), [
    '识别理解',
    '主动回忆',
    '语境运用',
  ])
  assert.equal(detail.subskills.every((item) => item.value == null), true)
  assert.equal(detail.actionTo, '/app/vocabulary?tab=modes')
})
