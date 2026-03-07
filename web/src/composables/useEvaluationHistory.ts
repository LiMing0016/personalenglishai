import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import {
  getEvaluationHistory,
  getEvaluationDetail,
  type EvaluationDetailResponse,
} from '@/api/writing'

export function useEvaluationHistory(pageSize = 10) {
  const page = ref(0)

  const historyQuery = useQuery({
    queryKey: computed(() => ['evaluationHistory', page.value, pageSize]),
    queryFn: () => getEvaluationHistory(page.value, pageSize),
    placeholderData: (prev) => prev,
  })

  const totalPages = computed(() => {
    const total = historyQuery.data.value?.total ?? 0
    return Math.ceil(total / pageSize)
  })

  function setPage(p: number) {
    page.value = p
  }

  return {
    page,
    totalPages,
    items: computed(() => historyQuery.data.value?.items ?? []),
    total: computed(() => historyQuery.data.value?.total ?? 0),
    isLoading: historyQuery.isLoading,
    isFetching: historyQuery.isFetching,
    setPage,
  }
}

export function useEvaluationDetail(id: () => number | null) {
  const query = useQuery({
    queryKey: computed(() => ['evaluationDetail', id()]),
    queryFn: () => getEvaluationDetail(id()!),
    enabled: computed(() => id() != null),
    staleTime: 5 * 60 * 1000, // 详情 5 分钟内不重新请求
  })

  return {
    data: query.data as typeof query.data & { value: EvaluationDetailResponse | undefined },
    isLoading: query.isLoading,
    isFetching: query.isFetching,
  }
}
