import { computed, type Ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import {
  captureVocabulary,
  deleteVocabularyCard,
  getVocabularyCard,
  listVocabularyCards,
  listVocabularyRevisions,
  listVocabularyTemplates,
  regenerateVocabularyCard,
  resolveVocabularyConflict,
  retryVocabularyCard,
  updateVocabularyCard,
  type VocabularyCardFilters,
  type ResolveVocabularyConflictRequest,
  type UpdateVocabularyCardRequest,
} from '@/api/vocabulary'
import { isVocabularyGenerationActive } from '@/features/vocabulary/generationPolling'

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
    refetchInterval: (query) => query.state.data?.items.some(isVocabularyGenerationActive)
      ? POLL_INTERVAL_MS
      : false,
  })

  const detailQuery = useQuery({
    queryKey: computed(() => ['vocabulary', 'card', selectedCardUid.value]),
    queryFn: () => getVocabularyCard(selectedCardUid.value!),
    enabled: computed(() => Boolean(selectedCardUid.value)),
    refetchInterval: (query) => query.state.data && isVocabularyGenerationActive(query.state.data)
      ? POLL_INTERVAL_MS
      : false,
  })

  const revisionsQuery = useQuery({
    queryKey: computed(() => ['vocabulary', 'card', selectedCardUid.value, 'revisions']),
    queryFn: () => listVocabularyRevisions(selectedCardUid.value!),
    enabled: computed(() => Boolean(selectedCardUid.value)),
  })

  async function invalidateCardQueries(cardUid?: string) {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['vocabulary', 'cards'] }),
      queryClient.invalidateQueries({ queryKey: ['vocabulary', 'card'] }),
      ...(cardUid
        ? [queryClient.invalidateQueries({ queryKey: ['vocabulary', 'card', cardUid, 'revisions'] })]
        : [queryClient.invalidateQueries({ queryKey: ['vocabulary', 'card'] })]),
    ])
  }

  const captureMutation = useMutation({
    mutationFn: captureVocabulary,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['vocabulary', 'cards'] })
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ cardUid, payload }: { cardUid: string, payload: UpdateVocabularyCardRequest }) => updateVocabularyCard(cardUid, payload),
    onSuccess: async (_, variables) => invalidateCardQueries(variables.cardUid),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteVocabularyCard,
    onSuccess: async (_, cardUid) => {
      queryClient.removeQueries({ queryKey: ['vocabulary', 'card', cardUid], exact: true })
      queryClient.removeQueries({ queryKey: ['vocabulary', 'card', cardUid, 'revisions'], exact: true })
      await queryClient.invalidateQueries({ queryKey: ['vocabulary', 'cards'] })
    },
  })

  const regenerateMutation = useMutation({
    mutationFn: regenerateVocabularyCard,
    onSuccess: async (_, cardUid) => invalidateCardQueries(cardUid),
  })

  const retryMutation = useMutation({
    mutationFn: retryVocabularyCard,
    onSuccess: async (_, cardUid) => invalidateCardQueries(cardUid),
  })

  const resolveConflictMutation = useMutation({
    mutationFn: ({ cardUid, revisionUid, payload }: { cardUid: string, revisionUid: string, payload: ResolveVocabularyConflictRequest }) => resolveVocabularyConflict(cardUid, revisionUid, payload),
    onSuccess: async (_, variables) => invalidateCardQueries(variables.cardUid),
  })

  return {
    templateQuery,
    listQuery,
    detailQuery,
    revisionsQuery,
    captureMutation,
    updateMutation,
    deleteMutation,
    regenerateMutation,
    retryMutation,
    resolveConflictMutation,
  }
}
