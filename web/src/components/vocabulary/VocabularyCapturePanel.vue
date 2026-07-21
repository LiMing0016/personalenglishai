<template>
  <section class="vocabulary-capture-panel" aria-labelledby="capture-heading">
    <header class="capture-header">
      <div>
        <h2 id="capture-heading">导入单词</h2>
        <span>{{ candidates.length }} 个候选词</span>
      </div>
      <div class="capture-mode" role="group" aria-label="导入方式">
        <button
          type="button"
          :disabled="captureBusy"
          :aria-pressed="mode === 'text'"
          @click="mode = 'text'"
        >
          文本录入
        </button>
        <button
          v-if="imageRecognitionEnabled"
          type="button"
          :disabled="captureBusy"
          :aria-pressed="mode === 'image'"
          @click="mode = 'image'"
        >
          图片识别
        </button>
      </div>
    </header>

    <form @submit.prevent="submitCapture">
      <VocabularyThemeSelect
        :catalog="themeCatalog"
        :selected-theme-uid="selectedThemeUid"
        :loading="themesLoading"
        :error="themesError"
        @select="selectTheme"
      />

      <VocabularyTextCapture
        v-show="mode === 'text'"
        v-model="rawTerms"
        @terms="syncManualCandidates"
      />
      <VocabularyImageCapture
        v-if="imageRecognitionEnabled"
        v-show="mode === 'image'"
        ref="imageCaptureRef"
        :mutation="imageRecognitionMutation"
        :disabled="captureBusy"
        @recognized="mergeImageCandidates"
        @failed="requestError = $event"
        @clear-error="requestError = ''"
        @recognizing="imageRecognizing = $event"
      />

      <VocabularyTermReview
        :candidates="candidates"
        :warnings="warnings"
        @command="handleReviewCommand"
      />

      <details class="capture-context">
        <summary>来源语境（可选）</summary>
        <label>
          <span>记录句子、笔记或材料来源</span>
          <textarea v-model="sourceContext" rows="3" maxlength="5000"></textarea>
        </label>
      </details>

      <div class="capture-actions">
        <p v-if="requestError" class="capture-message capture-message--error" role="alert">
          {{ requestError }}
        </p>
        <p v-else class="capture-message" role="status" aria-live="polite">
          {{ submitStatus }}
        </p>
        <button type="submit" class="capture-submit" :disabled="submitDisabled">
          {{ submitLabel }}
        </button>
      </div>
    </form>

    <ul v-if="outcomes.length" class="capture-outcomes" aria-label="录入结果">
      <li v-for="(item, index) in outcomes" :key="`${item.term}-${item.action}-${index}`">
        <strong>{{ item.term }}</strong>
        <span>{{ outcomeLabel(item.action) }}</span>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch, type Ref } from 'vue'

import {
  type VocabularyCaptureRequest,
  type VocabularyCaptureResponse,
  type VocabularyImageRecognitionResponse,
  type VocabularyRecognitionWarning,
  type VocabularyThemeCatalog,
} from '@/api/vocabulary'
import { isVocabularyCaptureComplete } from '@/features/vocabulary/captureCompletion'
import {
  applySuggestion,
  buildCaptureBatches,
  clearCandidateSelection,
  keepOriginal,
  mergeRecognitionCandidateState,
  orchestrateCaptureBatches,
  reconcileManualCandidates,
  removeCandidate,
  selectAllReadyCandidates,
  selectedReadyCandidates,
  updateCandidateTerm,
  type ImportCandidate,
} from '@/features/vocabulary/imageRecognition'
import { vocabularyProductEvents } from '@/features/vocabulary/productEvents'
import VocabularyImageCapture from './VocabularyImageCapture.vue'
import VocabularyTermReview, { type VocabularyReviewCommand } from './VocabularyTermReview.vue'
import VocabularyTextCapture from './VocabularyTextCapture.vue'
import VocabularyThemeSelect from './VocabularyThemeSelect.vue'

type CaptureMutation = {
  isPending: Ref<boolean>
  mutateAsync: (payload: VocabularyCaptureRequest) => Promise<VocabularyCaptureResponse>
}

type ImageRecognitionMutation = {
  isPending: Ref<boolean>
  mutateAsync: (payload: { file: File, signal: AbortSignal }) => Promise<VocabularyImageRecognitionResponse>
}

const props = defineProps<{
  themeCatalog?: VocabularyThemeCatalog
  themesLoading?: boolean
  themesError?: boolean
  captureMutation: CaptureMutation
  imageRecognitionEnabled: boolean
  imageRecognitionMutation: ImageRecognitionMutation
}>()

const emit = defineEmits<{
  captured: [response: VocabularyCaptureResponse]
}>()

const mode = ref<'text' | 'image'>('text')
const rawTerms = ref('')
const candidates = ref<ImportCandidate[]>([])
const warnings = ref<VocabularyRecognitionWarning[]>([])
const sourceContext = ref('')
const selectedThemeUid = ref('')
const outcomes = ref<VocabularyCaptureResponse['items']>([])
const requestError = ref('')
const submitting = ref(false)
const imageRecognizing = ref(false)
const imageCaptureRef = ref<{ deactivate: () => void } | null>(null)
let manualCandidateSequence = 0
const recognitionBaselines = new Map<string, { candidateCount: number, suspectedCount: number }>()

const activeThemes = computed(() => {
  const catalog = props.themeCatalog
  return catalog
    ? [...catalog.systemThemes, ...catalog.userThemes].filter((theme) => theme.status === 'active')
    : []
})
const selectedTheme = computed(() => activeThemes.value.find(
  (theme) => theme.themeUid === selectedThemeUid.value,
))
const readyCandidates = computed(() => selectedReadyCandidates(candidates.value))
const selectedCount = computed(() => readyCandidates.value.length)
const hasSelectedUnresolved = computed(() => candidates.value.some(
  (candidate) => candidate.selected && candidate.resolution === 'unresolved',
))
const captureBusy = computed(() => submitting.value || props.captureMutation.isPending.value)
const recognitionBusy = computed(() => imageRecognizing.value || props.imageRecognitionMutation.isPending.value)
const submitDisabled = computed(() => (
  !selectedCount.value
  || hasSelectedUnresolved.value
  || !selectedTheme.value
  || props.themesLoading
  || props.themesError
  || captureBusy.value
  || recognitionBusy.value
))
const submitLabel = computed(() => {
  if (captureBusy.value) return '生成中...'
  if (!selectedCount.value) return '生成卡片'
  return `生成 ${selectedCount.value} 张卡片`
})
const submitStatus = computed(() => {
  if (props.themesLoading) return '主题加载中...'
  if (props.themesError) return '主题加载失败'
  if (!selectedTheme.value) return '暂无可用主题'
  if (hasSelectedUnresolved.value) return '请先处理已选择的疑似拼写错误'
  return ''
})

watch(
  () => props.themeCatalog,
  (catalog) => {
    if (!catalog) {
      selectedThemeUid.value = ''
      return
    }
    const selectedThemeIsActive = [...catalog.systemThemes, ...catalog.userThemes].some(
      (theme) => theme.themeUid === selectedThemeUid.value && theme.status === 'active',
    )
    if (!selectedThemeIsActive) selectedThemeUid.value = catalog.defaultThemeUid
  },
  { immediate: true },
)

watch(mode, (nextMode, previousMode) => {
  if (previousMode === 'image' && nextMode !== 'image') leaveImageMode()
})

watch(() => props.imageRecognitionEnabled, (enabled) => {
  if (!enabled && mode.value === 'image') mode.value = 'text'
})

function leaveImageMode() {
  imageCaptureRef.value?.deactivate()
  requestError.value = ''
}

function selectTheme(themeUid: string) {
  if (activeThemes.value.some((theme) => theme.themeUid === themeUid)) selectedThemeUid.value = themeUid
}

function syncManualCandidates(terms: string[]) {
  candidates.value = reconcileManualCandidates(
    candidates.value,
    terms,
    () => `manual:${++manualCandidateSequence}`,
  )
}

function mergeImageCandidates(payload: { response: VocabularyImageRecognitionResponse, file: File }) {
  requestError.value = ''
  const merged = mergeRecognitionCandidateState(candidates.value, payload.response, payload.file.name)
  candidates.value = merged.candidates
  warnings.value = merged.warnings
  const batchCandidates = candidates.value.filter(
    (candidate) => candidate.source === 'ocr_image' && candidate.sourceBatchId === payload.response.traceId,
  )
  recognitionBaselines.set(payload.response.traceId, {
    candidateCount: batchCandidates.length,
    suspectedCount: batchCandidates.filter((candidate) => candidate.status === 'suspected_typo').length,
  })
}

function handleReviewCommand(command: VocabularyReviewCommand) {
  switch (command.type) {
    case 'select_all':
      candidates.value = selectAllReadyCandidates(candidates.value)
      break
    case 'clear_selection':
      candidates.value = clearCandidateSelection(candidates.value)
      break
    case 'toggle_selected':
      candidates.value = candidates.value.map((candidate) => candidate.id === command.candidateId
        ? { ...candidate, selected: command.selected }
        : candidate)
      break
    case 'update_term':
      candidates.value = updateCandidateTerm(candidates.value, command.candidateId, command.term)
      break
    case 'apply_suggestion':
      candidates.value = applySuggestion(candidates.value, command.candidateId, command.suggestion)
      break
    case 'keep_original':
      candidates.value = keepOriginal(candidates.value, command.candidateId)
      break
    case 'remove':
      candidates.value = removeCandidate(candidates.value, command.candidateId)
      break
  }
  syncRawTermsFromCandidates()
}

async function submitCapture() {
  if (submitDisabled.value) return

  requestError.value = ''
  outcomes.value = []
  submitting.value = true

  try {
    const batches = buildCaptureBatches({
      candidates: candidates.value,
      themeUid: selectedThemeUid.value,
      sourceContext: sourceContext.value,
    })
    const confirmedRecognitionTraces = new Set<string>()

    const result = await orchestrateCaptureBatches({
      batches,
      capture: (payload) => {
        const traceId = payload.source.type === 'ocr_image'
          ? String(payload.source.metadata.recognitionTraceId ?? '')
          : ''
        if (traceId && !confirmedRecognitionTraces.has(traceId)) {
          confirmedRecognitionTraces.add(traceId)
          const current = candidates.value.filter(
            (candidate) => candidate.source === 'ocr_image' && candidate.sourceBatchId === traceId,
          )
          const baseline = recognitionBaselines.get(traceId) ?? {
            candidateCount: current.length,
            suspectedCount: current.filter((candidate) => candidate.status === 'suspected_typo').length,
          }
          void vocabularyProductEvents.candidatesConfirmed({
            traceId,
            candidateCount: baseline.candidateCount,
            suspectedCount: baseline.suspectedCount,
            selectedCount: current.filter((candidate) => candidate.selected).length,
            editedCount: current.filter((candidate) => candidate.resolution === 'suggestion_applied').length,
            removedCount: Math.max(0, baseline.candidateCount - current.length),
            resolutionCount: current.filter((candidate) => candidate.resolution !== 'unresolved').length,
          })
        }
        return props.captureMutation.mutateAsync(payload)
      },
      isComplete: isVocabularyCaptureComplete,
      onBatchComplete: (candidateIds) => {
        const completedIds = new Set(candidateIds)
        candidates.value = candidates.value.filter((candidate) => !completedIds.has(candidate.id))
      },
      onAllComplete: (response) => {
        sourceContext.value = ''
        warnings.value = []
        emit('captured', response)
      },
    })

    outcomes.value = result.items
    syncRawTermsFromCandidates()
    if (result.failed) {
      requestError.value = result.error
        ? publicCaptureMessage(result.error)
        : '部分单词未能沉淀，未完成的候选词已保留'
    }
  } catch (error) {
    requestError.value = publicCaptureMessage(error)
  } finally {
    submitting.value = false
  }
}

function syncRawTermsFromCandidates() {
  rawTerms.value = candidates.value
    .filter((candidate) => candidate.source === 'manual')
    .map((candidate) => candidate.term)
    .join('\n')
}

function publicCaptureMessage(error: unknown) {
  return error instanceof Error && error.message.trim() ? error.message : '录入失败，请重试'
}

function outcomeLabel(action: string) {
  const labels: Record<string, string> = {
    created: '已收下',
    source_merged: '已存在，已追加来源',
    needs_review: '待确认',
    rejected: '已拒绝',
  }
  return labels[action] ?? action
}
</script>

<style scoped>
.vocabulary-capture-panel { box-sizing: border-box; min-width: 0; padding: 16px; border: 1px solid #dce7e1; border-radius: 8px; background: #fff; }
.capture-header, .capture-header > div, .capture-mode, .capture-actions, .capture-outcomes li { display: flex; align-items: center; gap: 10px; }
.capture-header { justify-content: space-between; }
.capture-header h2 { margin: 0; color: #0f172a; font-size: 18px; }
.capture-header span { color: #64748b; font-size: 12px; }
.capture-mode { flex: 0 0 auto; padding: 3px; border: 1px solid #dce7e1; border-radius: 7px; background: #f8fafc; }
.capture-mode button { min-width: 58px; min-height: 30px; border: 0; border-radius: 5px; background: transparent; color: #64748b; font: inherit; font-size: 13px; font-weight: 800; cursor: pointer; }
.capture-mode button:disabled { cursor: not-allowed; opacity: .55; }
.capture-mode button[aria-pressed='true'] { background: #fff; color: #047857; box-shadow: 0 1px 3px rgba(15, 23, 42, .12); }
form { display: grid; min-width: 0; gap: 14px; margin-top: 14px; }
.capture-context { border-top: 1px solid #edf2f7; padding-top: 10px; }
.capture-context summary { width: fit-content; color: #475569; font-size: 13px; font-weight: 800; cursor: pointer; }
.capture-context label { display: grid; gap: 6px; margin-top: 9px; color: #64748b; font-size: 12px; }
.capture-context textarea { box-sizing: border-box; width: 100%; resize: vertical; border: 1px solid #dce7e1; border-radius: 6px; background: #f8fafc; color: #0f172a; font: inherit; line-height: 1.5; padding: 9px 10px; }
.capture-context textarea:focus { border-color: #14b8a6; outline: none; box-shadow: 0 0 0 3px rgba(20, 184, 166, .12); }
.capture-actions { justify-content: space-between; min-width: 0; }
.capture-message { min-width: 0; margin: 0; color: #64748b; font-size: 12px; overflow-wrap: anywhere; }
.capture-message--error { color: #b91c1c; }
.capture-submit { flex: 0 0 auto; min-height: 36px; border: 0; border-radius: 6px; background: #059669; color: #fff; font: inherit; font-size: 13px; font-weight: 800; padding: 0 14px; cursor: pointer; white-space: nowrap; }
.capture-submit:disabled { cursor: not-allowed; opacity: .55; }
.capture-outcomes { display: grid; gap: 0; margin: 12px 0 0; padding: 0; border-top: 1px solid #edf2f7; list-style: none; }
.capture-outcomes li { justify-content: space-between; min-width: 0; min-height: 32px; color: #334155; font-size: 13px; }
.capture-outcomes strong { min-width: 0; overflow-wrap: anywhere; }
.capture-outcomes span { flex: 0 0 auto; color: #047857; }
@media (max-width: 620px) { .capture-header, .capture-actions { align-items: stretch; flex-direction: column; }.capture-header > div { justify-content: space-between; }.capture-mode { width: 100%; }.capture-mode button { flex: 1; }.capture-submit { width: 100%; } }
</style>
