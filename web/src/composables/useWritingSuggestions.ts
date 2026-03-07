import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import {
  fetchWritingSuggestions,
  type SuggestionErrorItem,
  type SuggestionItem,
} from '@/api/writing'

export function useWritingSuggestions(
  essayText: () => string,
  enabled: () => boolean,
) {
  // Snapshot of text to fetch suggestions for.
  // null = no fetch yet; set when we want to trigger a fetch.
  const fetchText = ref<string | null>(null)

  // Auto-trigger on first enable
  watch(enabled, (show) => {
    if (show && fetchText.value === null) {
      const text = essayText()?.trim()
      if (text) fetchText.value = text
    }
  }, { immediate: true })

  const query = useQuery({
    queryKey: computed(() => ['writingSuggestions', fetchText.value]),
    queryFn: () => fetchWritingSuggestions(fetchText.value!),
    enabled: computed(() => fetchText.value != null && enabled()),
    staleTime: 5 * 60 * 1000,
  })

  const suggestions = computed<SuggestionItem[]>(() => {
    const text = essayText()
    const raw = query.data.value?.suggestions ?? []
    return raw.filter((item) => {
      const original = item.original?.trim() ?? ''
      const suggestion = item.suggestion?.trim() ?? ''
      if (!original || !suggestion) return false
      if (original === suggestion) return false
      return text.includes(original)
    })
  })

  const gptHardErrors = computed<SuggestionErrorItem[]>(() => {
    const text = essayText()
    const raw = query.data.value?.errors ?? []
    return raw.filter(
      (e) => e.original && e.suggestion && e.original !== e.suggestion && text.includes(e.original),
    )
  })

  const loaded = computed(() => query.isSuccess.value && fetchText.value != null)

  const canReload = computed(() => {
    const text = essayText()?.trim() ?? ''
    return text !== (fetchText.value ?? '')
  })

  function reload() {
    const text = essayText()?.trim()
    if (!text || text === fetchText.value) return
    fetchText.value = text
  }

  return {
    suggestions,
    gptHardErrors,
    isLoading: query.isFetching,
    error: computed(() => query.error.value ? '获取建议失败，请重试' : null),
    loaded,
    canReload,
    reload,
    refetch: () => query.refetch(),
  }
}
