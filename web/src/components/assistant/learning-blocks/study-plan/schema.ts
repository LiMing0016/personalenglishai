import type { StudyPlanData } from '../contracts.ts'

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

export function normalizeStudyPlanData(value: unknown): StudyPlanData | null {
  if (!isRecord(value) || typeof value.title !== 'string' || !value.title.trim()) return null
  return value as unknown as StudyPlanData
}

export function studyPlanFallback(data: StudyPlanData) {
  return data.goal ? `### ${data.title}\n\n${data.goal}` : `### ${data.title}`
}

