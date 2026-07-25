import type { SentenceAnalysisData } from '../contracts.ts'

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

export function normalizeSentenceAnalysisData(value: unknown): SentenceAnalysisData | null {
  if (!isRecord(value) || typeof value.sentence !== 'string' || !value.sentence.trim()) return null
  return value as unknown as SentenceAnalysisData
}

export function sentenceAnalysisFallback(data: SentenceAnalysisData) {
  return data.translation
    ? `> ${data.sentence}\n\n${data.translation}`
    : `> ${data.sentence}`
}

