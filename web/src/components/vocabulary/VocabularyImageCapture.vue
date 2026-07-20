<template>
  <section class="image-capture" aria-label="图片识别">
    <input
      ref="uploadInput"
      class="image-capture__input"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      @change="selectFile"
    >
    <input
      ref="cameraInput"
      class="image-capture__input"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      capture="environment"
      @change="selectFile"
    >

    <div v-if="!file" class="image-capture__empty">
      <p>上传单词表、笔记或教材照片</p>
      <div>
        <button type="button" @click="uploadInput?.click()">选择图片</button>
        <button type="button" @click="cameraInput?.click()">拍照识别</button>
      </div>
      <small>支持 JPG、PNG、WEBP，最大 10 MB</small>
    </div>

    <div v-else class="image-capture__selected">
      <div class="image-capture__preview">
        <img v-if="previewUrl" :src="previewUrl" :alt="`待识别图片：${displayFileName}`">
      </div>
      <div class="image-capture__summary">
        <strong :title="displayFileName">{{ displayFileName }}</strong>
        <p v-if="response">
          识别到 {{ response.items.length }} 个候选词，{{ unresolvedCount }} 个拼写待确认
        </p>
        <p v-else>图片已就绪</p>
        <div>
          <button type="button" :disabled="recognizing" @click="recognize">
            {{ recognizing ? '识别中...' : response ? '重新识别' : '开始识别' }}
          </button>
          <button type="button" :disabled="recognizing" @click="uploadInput?.click()">更换图片</button>
        </div>
      </div>
    </div>

    <p v-if="errorMessage" class="image-capture__error" role="alert">{{ errorMessage }}</p>
    <p class="image-capture__status" role="status" aria-live="polite">
      {{ recognizing ? '正在识别图片中的单词' : '' }}
    </p>

    <details v-if="response?.rawText" class="image-capture__raw-text">
      <summary>查看识别原文</summary>
      <pre>{{ response.rawText }}</pre>
    </details>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, type Ref } from 'vue'

import type { VocabularyImageRecognitionResponse } from '@/api/vocabulary'
import { getVocabularyImageFileError } from '@/features/vocabulary/imageRecognition'

type ImageRecognitionMutation = {
  isPending: Ref<boolean>
  mutateAsync: (payload: { file: File, signal: AbortSignal }) => Promise<VocabularyImageRecognitionResponse>
}

const props = defineProps<{
  mutation: ImageRecognitionMutation
}>()

const emit = defineEmits<{
  recognized: [payload: { response: VocabularyImageRecognitionResponse, file: File }]
  failed: [message: string]
  recognizing: [active: boolean]
}>()

const uploadInput = ref<HTMLInputElement | null>(null)
const cameraInput = ref<HTMLInputElement | null>(null)
const file = ref<File | null>(null)
const previewUrl = ref('')
const response = ref<VocabularyImageRecognitionResponse | null>(null)
const recognizing = ref(false)
const errorMessage = ref('')
let latestRequestId = 0
let controller: AbortController | null = null

const displayFileName = computed(() => cleanFileName(file.value?.name ?? ''))
const unresolvedCount = computed(() => response.value?.items.filter(
  (item) => item.status === 'suspected_typo',
).length ?? 0)

function selectFile(event: Event) {
  const input = event.target as HTMLInputElement
  const nextFile = input.files?.[0] ?? null
  input.value = ''
  if (!nextFile) return

  const validationError = getVocabularyImageFileError(nextFile)
  if (validationError) {
    errorMessage.value = validationError
    emit('failed', validationError)
    return
  }

  cancelRecognition()
  releasePreview()
  file.value = nextFile
  previewUrl.value = URL.createObjectURL(nextFile)
  response.value = null
  errorMessage.value = ''
}

async function recognize() {
  const selectedFile = file.value
  if (!selectedFile || recognizing.value) return

  controller?.abort()
  controller = new AbortController()
  const requestId = ++latestRequestId
  recognizing.value = true
  errorMessage.value = ''
  emit('recognizing', true)

  try {
    const nextResponse = await props.mutation.mutateAsync({ file: selectedFile, signal: controller.signal })
    if (requestId !== latestRequestId) return
    response.value = nextResponse
    emit('recognized', { response: nextResponse, file: selectedFile })
  } catch (error) {
    if (requestId !== latestRequestId || isAbortError(error)) return
    const message = publicMessage(error)
    errorMessage.value = message
    emit('failed', message)
  } finally {
    if (requestId === latestRequestId) {
      recognizing.value = false
      emit('recognizing', false)
    }
  }
}

function cancelRecognition() {
  latestRequestId += 1
  controller?.abort()
  controller = null
  if (recognizing.value) {
    recognizing.value = false
    emit('recognizing', false)
  }
}

function releasePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

function cleanFileName(value: string) {
  return value.split(/[\\/]/u).pop()?.replace(/[\u0000-\u001f\u007f]/gu, '').trim() || 'image'
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
    || (typeof error === 'object' && error !== null && 'code' in error && error.code === 'ERR_CANCELED')
}

function publicMessage(error: unknown) {
  return error instanceof Error && error.message.trim() ? error.message : '图片识别失败，请重试'
}

onBeforeUnmount(() => {
  cancelRecognition()
  releasePreview()
})

defineExpose({ cancelRecognition })
</script>

<style scoped>
.image-capture { display: grid; min-width: 0; gap: 10px; }
.image-capture__input { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); clip-path: inset(50%); white-space: nowrap; }
.image-capture__empty { display: grid; min-height: 150px; place-items: center; align-content: center; gap: 10px; border: 1px dashed #a7c7b8; border-radius: 6px; background: #f8fafc; color: #475569; padding: 16px; text-align: center; }
.image-capture__empty p, .image-capture__empty small { margin: 0; }
.image-capture__empty p { color: #0f172a; font-size: 14px; font-weight: 800; }
.image-capture__empty small { color: #64748b; font-size: 12px; }
.image-capture__empty div, .image-capture__summary div { display: flex; flex-wrap: wrap; gap: 8px; }
.image-capture button { min-height: 34px; border: 1px solid #a7c7b8; border-radius: 6px; background: #fff; color: #047857; font: inherit; font-size: 13px; font-weight: 800; padding: 0 12px; cursor: pointer; }
.image-capture button:first-child { border-color: #059669; background: #059669; color: #fff; }
.image-capture button:disabled { cursor: not-allowed; opacity: .55; }
.image-capture__selected { display: grid; grid-template-columns: minmax(180px, 280px) minmax(0, 1fr); gap: 14px; align-items: center; }
.image-capture__preview { display: grid; width: 100%; max-height: 220px; aspect-ratio: 4 / 3; place-items: center; overflow: hidden; border: 1px solid #dce7e1; border-radius: 6px; background: #f8fafc; }
.image-capture__preview img { display: block; width: 100%; height: 100%; max-height: 220px; object-fit: contain; }
.image-capture__summary { display: grid; min-width: 0; gap: 7px; }
.image-capture__summary strong { overflow: hidden; color: #0f172a; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.image-capture__summary p { margin: 0; color: #64748b; font-size: 13px; }
.image-capture__error { margin: 0; color: #b91c1c; font-size: 13px; }
.image-capture__status { min-height: 18px; margin: 0; color: #047857; font-size: 12px; }
.image-capture__raw-text { border-top: 1px solid #edf2f7; color: #475569; font-size: 12px; padding-top: 8px; }
.image-capture__raw-text summary { color: #047857; cursor: pointer; font-weight: 800; }
.image-capture__raw-text pre { max-height: 160px; margin: 8px 0 0; overflow: auto; border-radius: 6px; background: #f8fafc; padding: 10px; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 620px) { .image-capture__selected { grid-template-columns: 1fr; }.image-capture__preview { max-width: 100%; }.image-capture__summary div { flex-direction: column; }.image-capture button { width: 100%; } }
</style>
