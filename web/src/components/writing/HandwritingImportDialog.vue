<template>
  <Teleport to="body">
    <div v-if="modelOpen" class="handwriting-overlay" @click.self="handleClose">
      <div class="handwriting-dialog" role="dialog" aria-modal="true" aria-labelledby="handwriting-import-title">
        <header class="handwriting-header">
          <div>
            <p class="handwriting-kicker">Writing</p>
            <h2 id="handwriting-import-title" class="handwriting-title">手写导入</h2>
          </div>
          <button
            class="handwriting-icon-button"
            type="button"
            :disabled="confirmBusy"
            @click="handleClose"
          >
            ×
          </button>
        </header>

        <section class="handwriting-body">
          <div class="handwriting-panel">
            <div class="handwriting-panel-head">
              <h3>1. 选择图片</h3>
              <p>支持 PNG / JPG / WEBP 等图片格式</p>
            </div>
            <div class="handwriting-uploader">
              <input
                ref="fileInputRef"
                class="handwriting-file-input"
                type="file"
                accept="image/*"
                @change="handleFileChange"
              />
              <button class="handwriting-primary-button" type="button" @click="openFilePicker">
                选择图片
              </button>
              <button
                class="handwriting-secondary-button"
                type="button"
                :disabled="!selectedFile || confirmBusy"
                @click="clearSelectedFile"
              >
                清空
              </button>
              <button
                class="handwriting-primary-button"
                type="button"
                :disabled="!selectedFile || isRecognizing || confirmBusy"
                @click="startRecognition"
              >
                {{ isRecognizing ? '识别中...' : '开始识别' }}
              </button>
            </div>

            <div v-if="selectedFile" class="handwriting-meta">
              <div class="handwriting-file-name">{{ selectedFile.name }}</div>
              <div class="handwriting-file-size">{{ formatFileSize(selectedFile.size) }}</div>
            </div>

            <div v-if="previewUrl" class="handwriting-image-preview">
              <img :src="previewUrl" alt="手写图片预览" />
            </div>
          </div>

          <div class="handwriting-panel">
            <div class="handwriting-panel-head">
              <h3>2. 识别与预览</h3>
              <p>识别结果会在下方预览，确认后再写入文档</p>
            </div>

            <div class="handwriting-status" :class="statusClass">
              <span class="handwriting-status-dot" />
              <span>{{ statusLabel }}</span>
            </div>

            <div v-if="recognitionError" class="handwriting-error">{{ recognitionError }}</div>

            <div v-if="normalizedText" class="handwriting-result-grid">
              <label class="handwriting-field">
                <span>识别文本</span>
                <textarea :value="recognizedTextPreview" readonly rows="4" />
              </label>
              <label class="handwriting-field">
                <span>规范文本</span>
                <textarea :value="normalizedText" readonly rows="4" />
              </label>
              <label class="handwriting-field handwriting-field-wide">
                <span>导入预览</span>
                <textarea :value="previewText" readonly rows="5" />
              </label>
            </div>

            <div v-if="confidenceLabel" class="handwriting-confidence">
              置信度 {{ confidenceLabel }}
            </div>
          </div>
        </section>

        <footer class="handwriting-footer">
          <div class="handwriting-strategy" :class="{ 'is-disabled': !normalizedText }">
            <button
              type="button"
              class="handwriting-chip"
              :class="{ active: strategy === 'replace' }"
              :disabled="!normalizedText || confirmBusy"
              @click="strategy = 'replace'"
            >
              替换
            </button>
            <button
              type="button"
              class="handwriting-chip"
              :class="{ active: strategy === 'append' }"
              :disabled="!normalizedText || confirmBusy"
              @click="strategy = 'append'"
            >
              追加
            </button>
          </div>

          <div class="handwriting-actions">
            <button
              v-if="isRecognizing"
              type="button"
              class="handwriting-secondary-button"
              @click="cancelRecognition"
            >
              取消识别
            </button>
            <button
              v-else
              type="button"
              class="handwriting-secondary-button"
              :disabled="confirmBusy"
              @click="handleClose"
            >
              取消
            </button>
            <button
              type="button"
              class="handwriting-primary-button"
              :disabled="confirmDisabled"
              @click="confirmImport"
            >
              {{ confirmLabel }}
            </button>
          </div>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  recognizeHandwritingImage,
  type RecognizeHandwritingImageResponse,
  type WritingAiProvider,
} from '@/api/writing'
import {
  buildHandwritingImportConfirmPayload,
  createHandwritingImportPreview,
  createHandwritingImportRunGate,
  formatHandwritingConfidence,
  isHandwritingImportDisabled,
  normalizeHandwritingText,
  type HandwritingImportStrategy,
  type HandwritingImportConfirmPayload,
} from './handwritingImportHelpers'

type HandwritingImportConfirmedPayload = HandwritingImportConfirmPayload

const props = withDefaults(defineProps<{
  modelValue: boolean
  currentText?: string
  aiProvider?: WritingAiProvider | null
}>(), {
  modelValue: false,
  currentText: '',
  aiProvider: null,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [payload: HandwritingImportConfirmedPayload]
  cancelled: []
}>()

const modelOpen = computed(() => props.modelValue)
const fileInputRef = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const previewUrl = ref('')
const recognitionResult = ref<RecognizeHandwritingImageResponse | null>(null)
const recognitionError = ref('')
const recognitionStatus = ref<'idle' | 'ready' | 'recognizing' | 'error'>('idle')
const confirmBusy = ref(false)
const strategy = ref<HandwritingImportStrategy>('replace')
const gate = createHandwritingImportRunGate()
const abortController = ref<AbortController | null>(null)

const normalizedText = computed(() =>
  normalizeHandwritingText(
    recognitionResult.value?.normalizedText ?? recognitionResult.value?.recognizedText,
  ),
)
const recognizedTextPreview = computed(() =>
  normalizeHandwritingText(recognitionResult.value?.recognizedText ?? normalizedText.value),
)
const confidenceLabel = computed(() =>
  formatHandwritingConfidence(recognitionResult.value?.confidence ?? null),
)
const previewText = computed(() =>
  createHandwritingImportPreview(props.currentText ?? '', normalizedText.value, strategy.value).combinedText,
)
const isRecognizing = computed(() => recognitionStatus.value === 'recognizing')
const confirmDisabled = computed(() =>
  confirmBusy.value || recognitionStatus.value !== 'ready' || isHandwritingImportDisabled(normalizedText.value),
)
const confirmLabel = computed(() => {
  if (confirmBusy.value) return '导入中...'
  if (strategy.value === 'append') return '确认追加'
  return '确认替换'
})
const statusLabel = computed(() => {
  if (confirmBusy.value) return '正在保存识别结果'
  if (recognitionStatus.value === 'recognizing') return '正在识别图片'
  if (recognitionStatus.value === 'ready') return '识别完成'
  if (recognitionStatus.value === 'error') return '识别失败'
  return '等待选择图片'
})
const statusClass = computed(() => recognitionStatus.value)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      resetSession()
    } else {
      releasePreviewUrl()
      abortActiveRecognition()
      gate.reset()
    }
  },
)

onBeforeUnmount(() => {
  releasePreviewUrl()
  abortActiveRecognition()
  gate.reset()
})

function openFilePicker() {
  if (confirmBusy.value) return
  fileInputRef.value?.click()
}

function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }
  return `${size.toFixed(size >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`
}

function releasePreviewUrl() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function clearSelectedFile() {
  if (confirmBusy.value) return
  releasePreviewUrl()
  selectedFile.value = null
  recognitionResult.value = null
  recognitionError.value = ''
  recognitionStatus.value = 'idle'
  strategy.value = 'replace'
  abortActiveRecognition()
  gate.reset()
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function resetSession() {
  clearSelectedFile()
  confirmBusy.value = false
}

function handleClose() {
  if (confirmBusy.value) {
    return
  }
  if (isRecognizing.value) {
    cancelRecognition()
    return
  }
  emit('update:modelValue', false)
  emit('cancelled')
}

function abortActiveRecognition() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
}

function cancelRecognition() {
  if (!isRecognizing.value) {
    return
  }
  gate.cancel()
  abortActiveRecognition()
  recognitionStatus.value = 'idle'
  recognitionError.value = ''
  emit('update:modelValue', false)
  emit('cancelled')
}

async function handleFileChange(event: Event) {
  if (confirmBusy.value) {
    return
  }
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  if (!file) {
    clearSelectedFile()
    return
  }

  releasePreviewUrl()
  selectedFile.value = file
  recognitionResult.value = null
  recognitionError.value = ''
  recognitionStatus.value = 'idle'
  strategy.value = 'replace'
  gate.reset()
  previewUrl.value = URL.createObjectURL(file)
}

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(reader.error ?? new Error('无法读取图片'))
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.readAsDataURL(file)
  })
}

async function startRecognition() {
  if (confirmBusy.value) {
    return
  }
  if (!selectedFile.value) {
    recognitionError.value = '请先选择图片'
    recognitionStatus.value = 'error'
    return
  }

  const runId = gate.start()
  abortActiveRecognition()
  const controller = new AbortController()
  abortController.value = controller

  recognitionError.value = ''
  recognitionStatus.value = 'recognizing'
  recognitionResult.value = null

  try {
    const imageBase64 = await readFileAsDataUrl(selectedFile.value)
    const response = await recognizeHandwritingImage(
      {
        imageBase64,
        aiProvider: props.aiProvider ?? undefined,
      },
      { signal: controller.signal },
    )

    if (!gate.canApply(runId)) {
      return
    }

    const normalized = normalizeHandwritingText(
      response.normalizedText ?? response.recognizedText,
    )
    recognitionResult.value = {
      imageUrl: response.imageUrl ?? imageBase64,
      recognizedText: response.recognizedText ?? normalized,
      normalizedText: normalized,
      confidence: response.confidence ?? null,
    }

    if (isHandwritingImportDisabled(normalized)) {
      recognitionStatus.value = 'error'
      recognitionError.value = '未识别到可用文本'
      return
    }

    strategy.value = 'replace'
    recognitionStatus.value = 'ready'
  } catch (error) {
    if (!gate.canApply(runId)) {
      return
    }

    if (controller.signal.aborted) {
      recognitionStatus.value = 'idle'
      recognitionError.value = ''
      return
    }

    recognitionStatus.value = 'error'
    recognitionError.value = error instanceof Error ? error.message : '识别失败，请重试'
  } finally {
    if (abortController.value === controller) {
      abortController.value = null
    }
  }
}

async function confirmImport() {
  if (confirmDisabled.value || !recognitionResult.value) {
    return
  }

  const importedText = normalizedText.value
  if (isHandwritingImportDisabled(importedText)) {
    recognitionStatus.value = 'error'
    recognitionError.value = '未识别到可用文本'
    return
  }

  confirmBusy.value = true
  recognitionError.value = ''

  try {
    const payload = buildHandwritingImportConfirmPayload({
      sourceText: props.currentText ?? '',
      recognizedText: recognitionResult.value.recognizedText ?? importedText,
      normalizedText: importedText,
      imageUrl: recognitionResult.value.imageUrl ?? null,
      mode: strategy.value,
    })
    emit('confirm', payload)
    emit('update:modelValue', false)
  } catch (error) {
    recognitionStatus.value = 'error'
    recognitionError.value = error instanceof Error ? error.message : '导入失败，请重试'
  } finally {
    confirmBusy.value = false
  }
}
</script>

<style scoped>
.handwriting-overlay {
  position: fixed;
  inset: 0;
  z-index: 2500;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.58);
  backdrop-filter: blur(10px);
}

.handwriting-dialog {
  width: min(1040px, 100%);
  max-height: calc(100vh - 48px);
  overflow: auto;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.98));
  box-shadow: 0 32px 64px rgba(15, 23, 42, 0.24);
}

.handwriting-header,
.handwriting-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
}

.handwriting-header {
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.handwriting-footer {
  border-top: 1px solid rgba(148, 163, 184, 0.18);
}

.handwriting-kicker {
  margin: 0 0 4px;
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #64748b;
}

.handwriting-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
  color: #0f172a;
}

.handwriting-icon-button {
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 12px;
  background: #e2e8f0;
  color: #334155;
  font-size: 24px;
  cursor: pointer;
}

.handwriting-body {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  gap: 18px;
  padding: 24px;
}

.handwriting-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.handwriting-panel-head h3 {
  margin: 0 0 4px;
  font-size: 16px;
  color: #0f172a;
}

.handwriting-panel-head p {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

.handwriting-uploader {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.handwriting-file-input {
  display: none;
}

.handwriting-primary-button,
.handwriting-secondary-button,
.handwriting-chip {
  border: 0;
  border-radius: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, background-color 0.15s ease, opacity 0.15s ease;
}

.handwriting-primary-button,
.handwriting-secondary-button {
  padding: 11px 16px;
  font-size: 14px;
}

.handwriting-primary-button {
  color: #fff;
  background: linear-gradient(135deg, #0f766e, #155e75);
}

.handwriting-primary-button:disabled,
.handwriting-secondary-button:disabled,
.handwriting-chip:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.handwriting-secondary-button {
  background: #e2e8f0;
  color: #334155;
}

.handwriting-chip {
  padding: 10px 14px;
  min-width: 84px;
  color: #334155;
  background: #e2e8f0;
}

.handwriting-chip.active {
  color: #0f172a;
  background: #bae6fd;
  box-shadow: inset 0 0 0 1px rgba(14, 165, 233, 0.18);
}

.handwriting-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #475569;
}

.handwriting-file-name {
  font-weight: 600;
  color: #0f172a;
  word-break: break-all;
}

.handwriting-image-preview {
  overflow: hidden;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.22);
}

.handwriting-image-preview img {
  display: block;
  width: 100%;
  height: auto;
}

.handwriting-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 13px;
  color: #334155;
  background: #e2e8f0;
}

.handwriting-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
}

.handwriting-status.recognizing {
  color: #155e75;
  background: #cffafe;
}

.handwriting-status.ready {
  color: #166534;
  background: #dcfce7;
}

.handwriting-status.error {
  color: #b91c1c;
  background: #fee2e2;
}

.handwriting-error {
  padding: 10px 12px;
  border-radius: 12px;
  color: #b91c1c;
  background: #fff1f2;
  border: 1px solid rgba(244, 63, 94, 0.18);
  font-size: 13px;
}

.handwriting-result-grid {
  display: grid;
  gap: 12px;
}

.handwriting-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.handwriting-field span {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.handwriting-field textarea {
  width: 100%;
  min-height: 120px;
  resize: vertical;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: #f8fafc;
  color: #0f172a;
  font: inherit;
  line-height: 1.5;
  white-space: pre-wrap;
}

.handwriting-field-wide textarea {
  min-height: 150px;
}

.handwriting-confidence {
  font-size: 13px;
  color: #64748b;
}

.handwriting-strategy {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.handwriting-strategy.is-disabled {
  opacity: 0.7;
}

.handwriting-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 900px) {
  .handwriting-body {
    grid-template-columns: 1fr;
  }
  .handwriting-footer,
  .handwriting-header {
    flex-direction: column;
    align-items: stretch;
  }
  .handwriting-actions {
    justify-content: flex-end;
  }
}
</style>
