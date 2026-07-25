import { computed, type Ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { AxiosError } from 'axios'

import {
  analyzeVocabularyImport,
  captureVocabulary,
  deleteVocabularyCard,
  getVocabularyCard,
  listVocabularyCards,
  listVocabularyRevisions,
  regenerateVocabularyCard,
  resolveVocabularyConflict,
  retryVocabularyCard,
  updateVocabularyCard,
  type VocabularyCardFilters,
  type VocabularyCoreContent,
  type RegenerateVocabularyCardRequest,
  type ResolveVocabularyConflictRequest,
  type UpdateVocabularyCardRequest,
} from '@/api/vocabulary'
import { isVocabularyGenerationActive } from '@/features/vocabulary/generationPolling'

const POLL_INTERVAL_MS = 2000
const VOCABULARY_CORE_MAX_SCALAR_LENGTH = 2_000
const VOCABULARY_CORE_MAX_MEANINGS = 30

export function shouldRetryVocabularyCardQuery(failureCount: number, error: unknown): boolean {
  const status = (error as AxiosError | null | undefined)?.response?.status
  if (status === 403 || status === 404) return false
  return failureCount < 3
}

export interface VocabularyCardDraftIdentity {
  cardUid: string
  activeRevisionUid: string | null
}

export function shouldResetVocabularyCardDraft(
  previous: VocabularyCardDraftIdentity | undefined,
  next: VocabularyCardDraftIdentity,
): boolean {
  return !previous
    || previous.cardUid !== next.cardUid
    || previous.activeRevisionUid !== next.activeRevisionUid
}

export function selectVocabularyThemeUid(
  activeThemes: Array<{ themeUid: string }>,
  defaultThemeUid: string,
  preferredThemeUid?: string | null,
): string {
  if (preferredThemeUid && activeThemes.some((theme) => theme.themeUid === preferredThemeUid)) {
    return preferredThemeUid
  }
  if (activeThemes.some((theme) => theme.themeUid === defaultThemeUid)) {
    return defaultThemeUid
  }
  return activeThemes[0]?.themeUid ?? ''
}

export function isVocabularyV1Revision(
  contentFormatVersion: number | null | undefined,
  compatibilityContent: unknown,
): boolean {
  const content = asLegacyRecord(compatibilityContent)
  return contentFormatVersion != null
    && (content?.schemaVersion === 1 || content?.schemaVersion === 2)
    && typeof content.term === 'string'
    && Array.isArray(content?.phonetics)
    && Array.isArray(content?.senses)
}

function asLegacyRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

function boundedVocabularyScalar(value: string): string {
  return value.slice(0, VOCABULARY_CORE_MAX_SCALAR_LENGTH)
}

function normalizedLegacyPartOfSpeech(value: unknown): string {
  if (typeof value !== 'string' || !value.trim()) return 'unknown'
  return boundedVocabularyScalar(value.trim().toLowerCase())
}

function splitLegacyMeaning(definition: string) {
  const value = definition.trim()
  const fullWidth = value.indexOf('；')
  const ascii = value.indexOf(';')
  const delimiter = fullWidth < 0
    ? ascii
    : ascii < 0 ? fullWidth : Math.min(fullWidth, ascii)
  return delimiter < 0
    ? { definitionEn: boundedVocabularyScalar(value), definitionZh: '' }
    : {
        definitionEn: boundedVocabularyScalar(value.slice(0, delimiter).trim()),
        definitionZh: boundedVocabularyScalar(value.slice(delimiter + 1).trim()),
      }
}

function legacyMeanings(value: unknown) {
  if (!Array.isArray(value)) return []
  return value
    .filter((definition): definition is string => typeof definition === 'string' && Boolean(definition.trim()))
    .slice(0, VOCABULARY_CORE_MAX_MEANINGS)
    .map(splitLegacyMeaning)
}

export function projectLegacyVocabularyCore(
  term: string,
  value: unknown,
): VocabularyCoreContent | null {
  const content = asLegacyRecord(value)
  if (!content) return null

  const phonetic = typeof content.phonetic === 'string' && content.phonetic.trim()
    ? boundedVocabularyScalar(content.phonetic)
    : ''
  const hasPartOfSpeech = typeof content.partOfSpeech === 'string' && Boolean(content.partOfSpeech.trim())
  const hasDefinitionArray = Array.isArray(content.definitions)
  const meanings = legacyMeanings(content.definitions)

  return {
    schemaVersion: 1,
    term,
    phonetics: phonetic
      ? [{ region: 'other', text: phonetic, audioUrl: null }]
      : [],
    senses: hasPartOfSpeech || hasDefinitionArray
      ? [{
          partOfSpeech: normalizedLegacyPartOfSpeech(content.partOfSpeech),
          meanings,
        }]
      : [],
  }
}

export function useVocabularyCards(
  filters: Ref<VocabularyCardFilters>,
  selectedCardUid: Ref<string | null>,
) {
  const queryClient = useQueryClient()

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
    retry: shouldRetryVocabularyCardQuery,
    refetchInterval: (query) => query.state.data && isVocabularyGenerationActive(query.state.data)
      ? POLL_INTERVAL_MS
      : false,
  })

  const revisionsQuery = useQuery({
    queryKey: computed(() => ['vocabulary', 'card', selectedCardUid.value, 'revisions']),
    queryFn: () => listVocabularyRevisions(selectedCardUid.value!),
    enabled: computed(() => Boolean(
      selectedCardUid.value && detailQuery.data.value?.cardUid === selectedCardUid.value,
    )),
    retry: shouldRetryVocabularyCardQuery,
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
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['vocabulary', 'cards'] }),
        queryClient.invalidateQueries({ queryKey: ['vocabulary', 'themes'] }),
      ])
    },
  })

  const importAnalysisMutation = useMutation({
    mutationFn: analyzeVocabularyImport,
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
    mutationFn: ({ cardUid, ...payload }: { cardUid: string } & RegenerateVocabularyCardRequest) => (
      regenerateVocabularyCard(cardUid, payload)
    ),
    onSuccess: async (_, variables) => invalidateCardQueries(variables.cardUid),
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
    listQuery,
    detailQuery,
    revisionsQuery,
    captureMutation,
    importAnalysisMutation,
    updateMutation,
    deleteMutation,
    regenerateMutation,
    retryMutation,
    resolveConflictMutation,
  }
}
