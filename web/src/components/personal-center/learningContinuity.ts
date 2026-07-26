export interface LearningContinuityHistoryItem {
  essay_preview?: string | null
  created_at?: string | null
  overall_score?: number | null
}

export interface LearningContinuityInput {
  recentItem: LearningContinuityHistoryItem | null
  studyDays: number | null | undefined
}

export interface LearningContinuityModel {
  previous: {
    hasHistory: boolean
    title: string
    description: string
    occurredAt: string | null
  }
  weeklyProgress: {
    completed: number
    total: 5
  }
}

function clampWeeklyProgress(studyDays: number | null | undefined) {
  const safeDays = Number.isFinite(studyDays) ? Math.trunc(studyDays ?? 0) : 0
  return Math.min(Math.max(safeDays, 0), 5)
}

export function buildLearningContinuity(input: LearningContinuityInput): LearningContinuityModel {
  const preview = input.recentItem?.essay_preview?.trim()
  const hasHistory = Boolean(preview && input.recentItem?.created_at)
  const score = input.recentItem?.overall_score

  return {
    previous: hasHistory
      ? {
          hasHistory: true,
          title: preview!,
          description: score == null ? '已完成一次写作练习' : `写作评测 · ${score} 分`,
          occurredAt: input.recentItem?.created_at ?? null,
        }
      : {
          hasHistory: false,
          title: '还没有完成记录',
          description: '完成一次学习后会沉淀在这里',
          occurredAt: null,
        },
    weeklyProgress: {
      completed: clampWeeklyProgress(input.studyDays),
      total: 5,
    },
  }
}
