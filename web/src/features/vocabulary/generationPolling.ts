type GenerationState = {
  status: string
  generationStatus: string | null
}

export function isVocabularyGenerationActive(state: GenerationState): boolean {
  return state.status === 'generating'
    || state.generationStatus === 'pending'
    || state.generationStatus === 'running'
}
