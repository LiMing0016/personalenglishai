<template>
  <Teleport to="body">
    <div class="import-dialog-backdrop" @click.self="requestClose">
      <section
        class="import-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="import-dialog-title"
      >
        <header class="import-dialog__header">
          <div>
            <h2 id="import-dialog-title">导入单词</h2>
            <p>输入文本或添加图片，AI 会整理出候选词。</p>
          </div>
          <button type="button" title="关闭" aria-label="关闭导入单词" :disabled="captureBusy" @click="requestClose">
            <X :size="20" aria-hidden="true" />
          </button>
        </header>

        <div class="import-dialog__body">
          <VocabularyImportComposer
            v-model="rawText"
            :file="selectedFile"
            :disabled="captureBusy"
            @update:file="selectedFile = $event"
            @file-error="fileError = $event"
          />

          <div class="import-dialog__analysis-row">
            <p
              class="import-dialog__state"
              :class="{ 'import-dialog__state--error': requestError || fileError }"
              :role="requestError || fileError ? 'alert' : 'status'"
              aria-live="polite"
            >
              {{ analysisStatusMessage }}
            </p>
            <button
              type="button"
              class="import-dialog__analyze"
              :disabled="!canAnalyze"
              @click="analyzeInput"
            >
              <Sparkles :size="17" aria-hidden="true" />
              {{ analysisState === 'analyzing' ? '分析中' : 'AI 分析' }}
            </button>
          </div>

          <VocabularyThemeSelect
            :catalog="themeCatalog"
            :selected-theme-uid="selectedThemeUid"
            :loading="themesLoading"
            :error="themesError"
            @select="selectTheme"
          />

          <VocabularyTermReview
            :candidates="candidates"
            :warnings="warnings"
            @command="handleReviewCommand"
          />
        </div>

        <footer class="import-dialog__footer">
          <span>{{ footerStatus }}</span>
          <div>
            <button type="button" class="import-dialog__cancel" :disabled="captureBusy" @click="requestClose">取消</button>
            <button
              type="button"
              class="import-dialog__generate"
              :disabled="!canGenerateFromCurrentAnalysis"
              @click="submitCapture"
            >
              <Sparkles :size="18" aria-hidden="true" />
              {{ generateLabel }}
            </button>
          </div>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { Sparkles, X } from 'lucide-vue-next'

import type {
  VocabularyCaptureRequest,
  VocabularyCaptureResponse,
  VocabularyImportAnalysisResponse,
  VocabularyRecognitionWarning,
  VocabularyThemeCatalog,
} from '@/api/vocabulary'
import { isVocabularyCaptureComplete } from '@/features/vocabulary/captureCompletion'
import {
  calculateVocabularyImportFingerprint,
  createImportAnalysisLifecycle,
  importAnalysisStateAfterInputChange,
  isImportAnalysisAbort,
  mapVocabularyImportAnalysisCandidates,
} from '@/features/vocabulary/importAnalysis'
import {
  applySuggestion,
  buildCaptureBatches,
  clearCandidateSelection,
  keepOriginal,
  orchestrateCaptureBatches,
  removeCandidate,
  selectAllReadyCandidates,
  selectedReadyCandidates,
  updateCandidateTerm,
  type ImportCandidate,
} from '@/features/vocabulary/imageRecognition'
import VocabularyImportComposer from './VocabularyImportComposer.vue'
import VocabularyTermReview, { type VocabularyReviewCommand } from './VocabularyTermReview.vue'
import VocabularyThemeSelect from './VocabularyThemeSelect.vue'

type CaptureMutation = {
  isPending: Ref<boolean>
  mutateAsync: (payload: VocabularyCaptureRequest) => Promise<VocabularyCaptureResponse>
}

type ImportAnalysisMutation = {
  isPending: Ref<boolean>
  mutateAsync: (payload: {
    text: string
    file: File | null
    inputFingerprint: string
    signal: AbortSignal
  }) => Promise<VocabularyImportAnalysisResponse>
}

type AnalysisState = 'idle' | 'analyzing' | 'ready' | 'stale' | 'failed'

const props = defineProps<{
  themeCatalog?: VocabularyThemeCatalog
  themesLoading?: boolean
  themesError?: boolean
  captureMutation: CaptureMutation
  importAnalysisEnabled: boolean
  importAnalysisMutation: ImportAnalysisMutation
}>()

const emit = defineEmits<{
  close: []
  captured: [response: VocabularyCaptureResponse]
}>()

const lifecycle = createImportAnalysisLifecycle()
const rawText = ref('')
const selectedFile = ref<File | null>(null)
const selectedThemeUid = ref('')
const candidates = ref<ImportCandidate[]>([])
const warnings = ref<VocabularyRecognitionWarning[]>([])
const currentFingerprint = ref('')
const lastSuccessfulFingerprint = ref('')
const analysisState = ref<AnalysisState>('idle')
const requestError = ref('')
const fileError = ref('')
const submitting = ref(false)
const analysisElapsedSeconds = ref(0)
let fingerprintRevision = 0
let analysisTimer: ReturnType<typeof setInterval> | null = null
let previousBodyOverflow = ''

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
const hasSelectedUnresolved = computed(() => candidates.value.some(
  (candidate) => candidate.selected && candidate.resolution === 'unresolved',
))
const hasInput = computed(() => Boolean(rawText.value.trim() || selectedFile.value))
const captureBusy = computed(() => submitting.value || props.captureMutation.isPending.value)
const canAnalyze = computed(() => (
  props.importAnalysisEnabled
  && hasInput.value
  && !fileError.value
  && !captureBusy.value
  && analysisState.value !== 'analyzing'
))
const canGenerateFromCurrentAnalysis = computed(() => (
  analysisState.value === 'ready'
  && Boolean(lastSuccessfulFingerprint.value)
  && lastSuccessfulFingerprint.value === currentFingerprint.value
  && Boolean(readyCandidates.value.length)
  && !hasSelectedUnresolved.value
  && Boolean(selectedTheme.value)
  && !props.themesLoading
  && !props.themesError
  && !captureBusy.value
  && !props.importAnalysisMutation.isPending.value
))
const analysisStatusMessage = computed(() => {
  if (fileError.value) return fileError.value
  if (requestError.value) return requestError.value
  if (!props.importAnalysisEnabled) return 'AI 分析暂不可用'
  if (analysisState.value === 'analyzing') {
    return analysisElapsedSeconds.value > 0
      ? `正在分析，已等待 ${analysisElapsedSeconds.value} 秒`
      : '正在分析输入内容'
  }
  if (analysisState.value === 'stale') return '输入已变化，请重新分析'
  if (analysisState.value === 'ready') return `已整理 ${candidates.value.length} 个候选词`
  if (!hasInput.value) return '等待输入'
  return '输入完成后开始分析'
})
const footerStatus = computed(() => {
  if (captureBusy.value) return '正在生成单词卡'
  if (analysisState.value === 'stale') return '当前候选词已过期'
  if (hasSelectedUnresolved.value) return '请先处理疑似拼写错误'
  if (!selectedTheme.value) return '请选择生成主题'
  return readyCandidates.value.length ? `已选择 ${readyCandidates.value.length} 个词` : '尚未选择候选词'
})
const generateLabel = computed(() => {
  if (captureBusy.value) return '生成中'
  return readyCandidates.value.length ? `生成 ${readyCandidates.value.length} 张卡片` : '生成卡片'
})

watch(
  () => props.themeCatalog,
  (catalog) => {
    if (!catalog) {
      selectedThemeUid.value = ''
      return
    }
    const isActive = [...catalog.systemThemes, ...catalog.userThemes].some(
      (theme) => theme.themeUid === selectedThemeUid.value && theme.status === 'active',
    )
    if (!isActive) selectedThemeUid.value = catalog.defaultThemeUid
  },
  { immediate: true },
)

watch([rawText, selectedFile], () => {
  void refreshCurrentFingerprint()
})

onMounted(() => {
  previousBodyOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  window.addEventListener('keydown', handleKeydown)
  void refreshCurrentFingerprint(false)
})

onBeforeUnmount(() => {
  lifecycle.deactivate()
  stopAnalysisTimer()
  window.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = previousBodyOverflow
})

async function refreshCurrentFingerprint(markStale = true) {
  const revision = ++fingerprintRevision
  lifecycle.invalidate()
  stopAnalysisTimer()
  requestError.value = ''
  if (markStale) {
    analysisState.value = importAnalysisStateAfterInputChange(lastSuccessfulFingerprint.value)
  }
  const fingerprint = await calculateVocabularyImportFingerprint(rawText.value, selectedFile.value)
  if (revision !== fingerprintRevision) return
  currentFingerprint.value = fingerprint
  if (markStale && lastSuccessfulFingerprint.value && fingerprint !== lastSuccessfulFingerprint.value) {
    analysisState.value = 'stale'
  } else if (!hasInput.value && !lastSuccessfulFingerprint.value) {
    analysisState.value = 'idle'
  }
}

async function analyzeInput() {
  if (!canAnalyze.value) return

  requestError.value = ''
  const revision = ++fingerprintRevision
  lifecycle.invalidate()
  const fingerprint = await calculateVocabularyImportFingerprint(rawText.value, selectedFile.value)
  if (revision !== fingerprintRevision || !hasInput.value) return
  currentFingerprint.value = fingerprint

  const request = lifecycle.begin(fingerprint)
  analysisState.value = 'analyzing'
  startAnalysisTimer()

  try {
    const response = await props.importAnalysisMutation.mutateAsync({
      text: rawText.value,
      file: selectedFile.value,
      inputFingerprint: fingerprint,
      signal: request.signal,
    })
    if (!lifecycle.isCurrent(
      request.requestId,
      response.inputFingerprint,
      request.fingerprint,
      currentFingerprint.value,
    )) return

    candidates.value = mapVocabularyImportAnalysisCandidates(response, selectedFile.value?.name ?? null)
    warnings.value = [...response.warnings]
    lastSuccessfulFingerprint.value = response.inputFingerprint
    analysisState.value = 'ready'
  } catch (error) {
    if (isImportAnalysisAbort(error) || request.signal.aborted) return
    requestError.value = publicAnalysisMessage(error)
    analysisState.value = 'failed'
  } finally {
    if (lifecycle.isCurrent(
      request.requestId,
      request.fingerprint,
      request.fingerprint,
      currentFingerprint.value,
    )) stopAnalysisTimer()
  }
}

function selectTheme(themeUid: string) {
  if (activeThemes.value.some((theme) => theme.themeUid === themeUid)) selectedThemeUid.value = themeUid
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
}

async function submitCapture() {
  if (!canGenerateFromCurrentAnalysis.value) return

  requestError.value = ''
  submitting.value = true
  try {
    const batches = buildCaptureBatches({
      candidates: candidates.value,
      themeUid: selectedThemeUid.value,
      sourceContext: '',
    })
    const result = await orchestrateCaptureBatches({
      batches,
      capture: (payload) => props.captureMutation.mutateAsync(payload),
      isComplete: isVocabularyCaptureComplete,
      onBatchComplete: (candidateIds) => {
        const completedIds = new Set(candidateIds)
        candidates.value = candidates.value.filter((candidate) => !completedIds.has(candidate.id))
      },
    })

    if (result.failed) {
      requestError.value = result.error
        ? publicCaptureMessage(result.error)
        : '部分单词生成失败，未完成的候选词已保留'
      return
    }

    const response = { items: result.items }
    emit('captured', response)
    emit('close')
  } catch (error) {
    requestError.value = publicCaptureMessage(error)
  } finally {
    submitting.value = false
  }
}

function requestClose() {
  if (captureBusy.value) return
  lifecycle.deactivate()
  emit('close')
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') requestClose()
}

function startAnalysisTimer() {
  stopAnalysisTimer()
  analysisElapsedSeconds.value = 0
  analysisTimer = setInterval(() => {
    analysisElapsedSeconds.value += 1
  }, 1000)
}

function stopAnalysisTimer() {
  if (analysisTimer) clearInterval(analysisTimer)
  analysisTimer = null
}

function publicAnalysisMessage(error: unknown) {
  const status = typeof error === 'object' && error && 'response' in error
    ? (error as { response?: { status?: number } }).response?.status
    : undefined
  const code = typeof error === 'object' && error && 'code' in error
    ? (error as { code?: string }).code
    : undefined
  if (status === 400) return '输入已变化，请重新分析'
  if (status === 504 || code === 'ECONNABORTED') return 'AI 分析超时，请重试'
  return 'AI 分析失败，请重试'
}

function publicCaptureMessage(error: unknown) {
  return error instanceof Error && error.message.trim() ? error.message : '生成失败，请重试'
}
</script>

<style scoped>
.import-dialog-backdrop {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.44);
}

.import-dialog {
  display: grid;
  width: min(720px, 100%);
  max-height: calc(100vh - 40px);
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
  border: 1px solid #dce7e1;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.24);
}

.import-dialog__header,
.import-dialog__footer,
.import-dialog__analysis-row,
.import-dialog__footer > div {
  display: flex;
  align-items: center;
}

.import-dialog__header {
  justify-content: space-between;
  gap: 16px;
  min-height: 76px;
  padding: 0 20px;
  border-bottom: 1px solid #edf2f7;
}

.import-dialog__header h2,
.import-dialog__header p {
  margin: 0;
}

.import-dialog__header h2 {
  color: #0f172a;
  font-size: 20px;
}

.import-dialog__header p {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.import-dialog__header button {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid #dce7e1;
  border-radius: 6px;
  background: #ffffff;
  color: #475569;
  cursor: pointer;
}

.import-dialog__body {
  display: grid;
  min-height: 0;
  grid-auto-rows: max-content;
  gap: 15px;
  overflow: auto;
  padding: 18px 20px;
}

.import-dialog__analysis-row {
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.import-dialog__state {
  min-width: 0;
  margin: 0;
  color: #64748b;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.import-dialog__state--error {
  color: #b91c1c;
}

.import-dialog__analyze,
.import-dialog__generate,
.import-dialog__cancel {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border-radius: 6px;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  padding: 0 14px;
  cursor: pointer;
  white-space: nowrap;
}

.import-dialog__analyze,
.import-dialog__generate {
  border: 1px solid #059669;
  background: #059669;
  color: #ffffff;
}

.import-dialog__footer {
  justify-content: space-between;
  gap: 16px;
  min-height: 68px;
  padding: 0 20px;
  border-top: 1px solid #edf2f7;
}

.import-dialog__footer > span {
  min-width: 0;
  color: #64748b;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.import-dialog__footer > div {
  flex: 0 0 auto;
  gap: 9px;
}

.import-dialog__cancel {
  border: 1px solid #dce7e1;
  background: #ffffff;
  color: #475569;
}

button:hover:not(:disabled),
button:focus-visible {
  outline: none;
  filter: brightness(0.96);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

@media (max-width: 620px) {
  .import-dialog-backdrop {
    place-items: end stretch;
    padding: 10px;
  }

  .import-dialog {
    width: 100%;
    max-height: calc(100vh - 20px);
  }

  .import-dialog__header,
  .import-dialog__body,
  .import-dialog__footer {
    padding-right: 14px;
    padding-left: 14px;
  }

  .import-dialog__footer {
    align-items: stretch;
    flex-direction: column;
    padding-top: 12px;
    padding-bottom: 12px;
  }

  .import-dialog__footer > div,
  .import-dialog__cancel,
  .import-dialog__generate {
    width: 100%;
  }

  .import-dialog__footer > div > button {
    flex: 1;
  }
}
</style>
