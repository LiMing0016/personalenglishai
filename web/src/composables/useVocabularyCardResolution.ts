import { computed, type Ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'

import type { DictionaryLanguage } from '@/api/dictionary'
import {
  getVocabularyCard,
  resolveVocabularyCard,
} from '@/api/vocabulary'
import { shouldRetryVocabularyCardQuery } from '@/composables/useVocabularyCards'

export function mapDictionaryLanguageToCardLanguage(_language: DictionaryLanguage): 'en' {
  return 'en'
}

export function normalizeVocabularyResolutionTerm(term: string): string {
  return term
    .normalize('NFKC')
    .trim()
    .toLocaleLowerCase('en-US')
    .replace(/\s+/gu, ' ')
}

export function useVocabularyCardResolution(
  term: Ref<string>,
  language: Ref<DictionaryLanguage>,
) {
  const normalizedTerm = computed(() => normalizeVocabularyResolutionTerm(term.value))
  const cardLanguage = computed(() => mapDictionaryLanguageToCardLanguage(language.value))

  const resolutionQuery = useQuery({
    queryKey: computed(() => [
      'vocabulary',
      'card-resolution',
      cardLanguage.value,
      normalizedTerm.value,
    ]),
    queryFn: () => resolveVocabularyCard(normalizedTerm.value, cardLanguage.value),
    enabled: computed(() => Boolean(normalizedTerm.value)),
    retry: shouldRetryVocabularyCardQuery,
  })

  const resolvedCardUid = computed(() => resolutionQuery.data.value?.cardUid ?? null)
  const detailQuery = useQuery({
    queryKey: computed(() => ['vocabulary', 'card', resolvedCardUid.value]),
    queryFn: () => getVocabularyCard(resolvedCardUid.value!),
    enabled: computed(() => Boolean(resolvedCardUid.value)),
    retry: shouldRetryVocabularyCardQuery,
  })

  const card = computed(() => detailQuery.data.value ?? null)
  const found = computed(() => Boolean(resolutionQuery.data.value?.found && resolvedCardUid.value))
  const error = computed(() => resolutionQuery.error.value ?? detailQuery.error.value ?? null)
  const isLoading = computed(() => (
    resolutionQuery.isLoading.value
    || (found.value && detailQuery.isLoading.value)
  ))

  async function retry() {
    if (resolutionQuery.isError.value) {
      return resolutionQuery.refetch()
    }
    return detailQuery.refetch()
  }

  return {
    normalizedTerm,
    cardLanguage,
    resolvedCardUid,
    resolutionQuery,
    detailQuery,
    card,
    found,
    error,
    isLoading,
    retry,
  }
}
