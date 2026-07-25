import type { VocabularyCoreContent } from '@/api/vocabulary'

export interface VocabularyHeaderSenseSummary {
  partOfSpeech: string
  meaning: string
}

export function buildVocabularyHeaderSenseSummaries(
  core: VocabularyCoreContent,
  limit = 2,
): VocabularyHeaderSenseSummary[] {
  const summaries: VocabularyHeaderSenseSummary[] = []
  const seenPartsOfSpeech = new Set<string>()

  for (const sense of core.senses) {
    const partOfSpeech = sense.partOfSpeech.trim()
    const meaning = sense.meanings
      .map((item) => item.definitionZh.trim() || item.definitionEn.trim())
      .find(Boolean)

    if (!partOfSpeech || !meaning) continue

    const partOfSpeechKey = partOfSpeech.toLocaleLowerCase()
    if (seenPartsOfSpeech.has(partOfSpeechKey)) continue

    seenPartsOfSpeech.add(partOfSpeechKey)
    summaries.push({ partOfSpeech, meaning })
    if (summaries.length >= limit) break
  }

  return summaries
}
