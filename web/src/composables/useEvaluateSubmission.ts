import { computed, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import {
  submitEvaluateWriting,
  getEvaluateTask,
  type WritingEvaluateRequest,
  type WritingEvaluateResponse,
  type WritingEvaluateTaskStatusResponse,
} from '@/api/writing'

const POLL_INTERVAL_MS = 1200
const POLL_TIMEOUT_MS = 180_000
const MAX_CONSECUTIVE_ERRORS = 5

export function useEvaluateSubmission() {
  const requestId = ref<string | null>(null)
  const pollEnabled = ref(false)
  const evaluateResult = ref<WritingEvaluateResponse | null>(null)
  const evaluateError = ref<string | null>(null)
  const submitting = ref(false)

  let pollStartTime = 0
  let consecutiveErrors = 0

  // Step 1: Mutation to submit essay
  const submitMutation = useMutation({
    mutationFn: (payload: WritingEvaluateRequest) => submitEvaluateWriting(payload),
    onSuccess: (data) => {
      if (!data.requestId) {
        evaluateError.value = '评估任务提交失败：缺少 requestId'
        submitting.value = false
        return
      }
      requestId.value = data.requestId
      pollEnabled.value = true
    },
    onError: (err: Error) => {
      evaluateError.value = err.message || '评估提交失败'
      submitting.value = false
    },
  })

  // Step 2: Poll for task status
  const pollQuery = useQuery({
    queryKey: computed(() => ['evaluateTask', requestId.value]),
    queryFn: () => getEvaluateTask(requestId.value!),
    enabled: computed(() => pollEnabled.value && requestId.value != null),
    refetchInterval: computed(() => (pollEnabled.value ? POLL_INTERVAL_MS : false)),
    retry: 2,
  })

  function stopPolling(error?: string) {
    pollEnabled.value = false
    submitting.value = false
    if (error) evaluateError.value = error
  }

  // Watch poll results — handle success/failure status
  watch(
    () => pollQuery.data.value,
    (task: WritingEvaluateTaskStatusResponse | undefined) => {
      if (!task || !pollEnabled.value) return

      // Reset consecutive error counter on successful data fetch
      consecutiveErrors = 0

      if (task.status === 'succeeded' && task.result) {
        evaluateResult.value = task.result
        stopPolling()
      } else if (task.status === 'failed') {
        stopPolling(task.error || '评估任务失败')
      }
    },
  )

  // Watch poll errors — handle network failures / persistent request errors
  watch(
    () => pollQuery.error.value,
    (err) => {
      if (!err || !pollEnabled.value) return
      consecutiveErrors++
      if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
        stopPolling(`评估轮询连续失败 ${consecutiveErrors} 次，请检查网络后重试`)
      }
    },
  )

  // Timeout: stop polling after POLL_TIMEOUT_MS
  watch(pollEnabled, (enabled) => {
    if (enabled) {
      pollStartTime = Date.now()
      consecutiveErrors = 0
    }
  })

  // Check timeout on both successful fetches AND errors
  watch(
    [() => pollQuery.dataUpdatedAt.value, () => pollQuery.errorUpdatedAt.value],
    () => {
      if (!pollEnabled.value) return
      if (Date.now() - pollStartTime > POLL_TIMEOUT_MS) {
        stopPolling('评估超时，请稍后再试')
      }
    },
  )

  function submit(payload: WritingEvaluateRequest) {
    evaluateResult.value = null
    evaluateError.value = null
    submitting.value = true
    requestId.value = null
    pollEnabled.value = false
    consecutiveErrors = 0

    submitMutation.mutate(payload)
  }

  function cancel() {
    pollEnabled.value = false
    requestId.value = null
    submitting.value = false
  }

  function clearResult() {
    evaluateResult.value = null
    evaluateError.value = null
  }

  return {
    submit,
    cancel,
    clearResult,
    evaluateResult,
    evaluateError,
    submitting,
    isPollFetching: pollQuery.isFetching,
  }
}
