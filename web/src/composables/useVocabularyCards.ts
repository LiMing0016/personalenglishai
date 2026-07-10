import { computed, type Ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import {
  captureVocabulary,
  getVocabularyCard,
  listVocabularyCards,
  listVocabularyTemplates,
  type VocabularyCardFilters,
} from '@/api/vocabulary'

const POLL_INTERVAL_MS = 2000

export function useVocabularyCards(
  filters: Ref<VocabularyCardFilters>,
  selectedCardUid: Ref<string | null>,
) {
  const queryClient = useQueryClient()

  const templateQuery = useQuery({
    queryKey: ['vocabulary', 'templates'],
    queryFn: listVocabularyTemplates,
    staleTime: 300_000,
  })

  const listQuery = useQuery({
    queryKey: computed(() => ['vocabulary', 'cards', filters.value]),
    queryFn: () => listVocabularyCards(filters.value),
    refetchInterval: (query) => query.state.data?.items.some((item) => item.status === 'generating')
      ? POLL_INTERVAL_MS
      : false,
  })

  const detailQuery = useQuery({
    queryKey: computed(() => ['vocabulary', 'card', selectedCardUid.value]),
    queryFn: () => getVocabularyCard(selectedCardUid.value!),
    enabled: computed(() => Boolean(selectedCardUid.value)),
    refetchInterval: (query) => query.state.data?.status === 'generating'
      ? POLL_INTERVAL_MS
      : false,
  })

  const captureMutation = useMutation({
    mutationFn: captureVocabulary,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['vocabulary', 'cards'] })
    },
  })

  return { templateQuery, listQuery, detailQuery, captureMutation }
}
