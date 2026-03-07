import { computed, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import {
  submitEvaluateWriting,
  getEvaluateTask,
  type WritingEvaluateRequest,
  type WritingEvaluateResponse,
  type WritingEvaluateTaskStatusResponse,
} from '@/api/writing'

export function useEvaluateSubmission() {
  const requestId = ref<string | null>(null)
  const pollEnabled = ref(false)
  const evaluateResult = ref<WritingEvaluateResponse | null>(null)
  const evaluateError = ref<string | null>(null)
  const submitting = ref(false)

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
    refetchInterval: computed(() => (pollEnabled.value ? 1200 : false)),
    retry: 2,
  })

  // Watch poll results
  watch(
    () => pollQuery.data.value,
    (task: WritingEvaluateTaskStatusResponse | undefined) => {
      if (!task || !pollEnabled.value) return

      if (task.status === 'succeeded' && task.result) {
        evaluateResult.value = task.result
        pollEnabled.value = false
        submitting.value = false
      } else if (task.status === 'failed') {
        evaluateError.value = task.error || '评估任务失败'
        pollEnabled.value = false
        submitting.value = false
      }
    },
  )

  // Timeout: stop polling after 3 minutes
  let pollStartTime = 0
  watch(pollEnabled, (enabled) => {
    if (enabled) {
      pollStartTime = Date.now()
    }
  })

  watch(
    () => pollQuery.dataUpdatedAt.value,
    () => {
      if (!pollEnabled.value) return
      if (Date.now() - pollStartTime > 180_000) {
        evaluateError.value = '评估超时，请稍后再试'
        pollEnabled.value = false
        submitting.value = false
      }
    },
  )

  function submit(payload: WritingEvaluateRequest) {
    // Reset state
    evaluateResult.value = null
    evaluateError.value = null
    submitting.value = true
    requestId.value = null
    pollEnabled.value = false

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
