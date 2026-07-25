import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import {
  copyVocabularyTheme,
  createVocabularyTheme,
  deleteVocabularyTheme,
  disableVocabularyTheme,
  listVocabularyThemes,
  setDefaultVocabularyTheme,
  updateVocabularyTheme,
  type UpdateVocabularyThemeRequest,
} from '@/api/vocabulary'

const VOCABULARY_THEMES_QUERY_KEY = ['vocabulary', 'themes'] as const

export function useVocabularyThemes() {
  const queryClient = useQueryClient()

  const themesQuery = useQuery({
    queryKey: VOCABULARY_THEMES_QUERY_KEY,
    queryFn: listVocabularyThemes,
  })

  const invalidateThemeQueries = () => queryClient.invalidateQueries({
    queryKey: VOCABULARY_THEMES_QUERY_KEY,
  })

  const invalidateThemeAndCardQueries = () => Promise.all([
    invalidateThemeQueries(),
    queryClient.invalidateQueries({ queryKey: ['vocabulary', 'cards'] }),
  ])

  const createMutation = useMutation({
    mutationFn: createVocabularyTheme,
    onSuccess: invalidateThemeQueries,
  })

  const updateMutation = useMutation({
    mutationFn: ({ themeUid, payload }: {
      themeUid: string
      payload: UpdateVocabularyThemeRequest
    }) => updateVocabularyTheme(themeUid, payload),
    onSuccess: invalidateThemeQueries,
  })

  const copyMutation = useMutation({
    mutationFn: copyVocabularyTheme,
    onSuccess: invalidateThemeQueries,
  })

  const defaultMutation = useMutation({
    mutationFn: setDefaultVocabularyTheme,
    onSuccess: invalidateThemeAndCardQueries,
  })

  const disableMutation = useMutation({
    mutationFn: disableVocabularyTheme,
    onSuccess: invalidateThemeAndCardQueries,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteVocabularyTheme,
    onSuccess: invalidateThemeAndCardQueries,
  })

  return {
    themesQuery,
    createMutation,
    updateMutation,
    copyMutation,
    defaultMutation,
    disableMutation,
    deleteMutation,
  }
}
