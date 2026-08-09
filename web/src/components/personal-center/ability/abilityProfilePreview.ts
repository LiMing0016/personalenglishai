import type { AbilityProfile } from '@/api/user'
import type {
  WritingDashboardResponse,
  WritingStatsResponse,
} from '@/api/writing'

export const PREVIEW_ABILITY_PROFILE: AbilityProfile = {
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
}

export const PREVIEW_WRITING_DASHBOARD: WritingDashboardResponse = {
  scope: {
    range: 'all',
    mode: 'all',
    scorePolicy: 'latest',
    start: '2026-01-01',
    end: '2026-08-09',
    granularity: 'month',
  },
  overview: {
    summary: {
      totalEssays: 4,
      totalSubmissions: 5,
      averageScore: 66,
      bestScore: 75,
    },
    trend: [],
    insight: '结构稳定，继续提升表达。',
  },
  growth: {
    essayScoreTrend: [
      {
        essayNo: 1,
        title: 'A weekend plan',
        mode: 'free',
        score: 62,
        scoredAt: '2026-08-02T12:00:00+08:00',
        delta: 0,
        aiSuggestion: '加强衔接',
      },
      {
        essayNo: 2,
        title: 'Campus life',
        mode: 'free',
        score: 66,
        scoredAt: '2026-08-09T12:00:00+08:00',
        delta: 4,
        aiSuggestion: '丰富表达',
      },
    ],
    scoreDistribution: [],
    scoreBands: [],
    highScorePercent: 0,
    scoreScatter: [],
    monthlyGoal: { done: 1, target: 3, remaining: 2 },
    streak: { currentDays: 1, bestDays: 2, activeDays: 2 },
    insight: '结构稳定，继续提升表达。',
  },
}

export const PREVIEW_WRITING_STATS: WritingStatsResponse = {
  avgContentQuality: 67,
  avgTaskAchievement: 68,
  avgStructureScore: 70,
  avgVocabularyScore: 64,
  avgGrammarScore: 61,
  avgExpressionScore: 58,
  totalGrammarErrors: 8,
  totalSpellingErrors: 2,
  totalVocabularyErrors: 4,
}
