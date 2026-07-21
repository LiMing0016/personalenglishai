import type { VocabCardData } from '../contracts.ts'

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

export function normalizeVocabCardData(value: unknown): VocabCardData | null {
  if (!isRecord(value) || typeof value.word !== 'string' || !value.word.trim()) return null
  return value as unknown as VocabCardData
}

export function vocabCardFallback(data: VocabCardData) {
  const meaning = data.meanings?.find((item) => item.text.trim())?.text
  return meaning ? `**${data.word}**：${meaning}` : `**${data.word}**`
}

