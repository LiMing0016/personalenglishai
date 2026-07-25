<template>
  <div class="import-composer" :class="{ 'import-composer--disabled': disabled }">
    <textarea
      :value="modelValue"
      rows="5"
      maxlength="20000"
      :disabled="disabled"
      placeholder="输入或粘贴单词，也可以添加一张图片"
      aria-label="输入要分析的单词"
      @input="updateText"
      @paste="handlePaste"
    ></textarea>

    <div v-if="previewUrl && file" class="import-composer__attachment">
      <img :src="previewUrl" :alt="displayFileName">
      <div>
        <strong>{{ displayFileName }}</strong>
        <span>{{ formattedFileSize }}</span>
      </div>
      <button type="button" title="移除图片" aria-label="移除图片" :disabled="disabled" @click="removeFile">
        <X :size="17" aria-hidden="true" />
      </button>
    </div>

    <footer>
      <input
        ref="fileInput"
        class="import-composer__file-input"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        :disabled="disabled"
        @change="selectFile"
      >
      <button
        type="button"
        class="import-composer__add"
        title="添加图片"
        aria-label="添加图片"
        :disabled="disabled"
        @click="fileInput?.click()"
      >
        <Plus :size="19" aria-hidden="true" />
      </button>
      <span><ImageIcon :size="15" aria-hidden="true" /> 支持 JPG、PNG、WEBP，最大 10 MB</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Image as ImageIcon, Plus, X } from 'lucide-vue-next'

import { getVocabularyImageFileError } from '@/features/vocabulary/imageRecognition'

const props = withDefaults(defineProps<{
  modelValue: string
  file: File | null
  disabled?: boolean
}>(), {
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:file': [file: File | null]
  'file-error': [message: string]
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const previewUrl = ref('')
const displayFileName = computed(() => props.file?.name.trim() || '粘贴的图片')

const formattedFileSize = computed(() => {
  if (!props.file) return ''
  if (props.file.size < 1024 * 1024) return `${Math.max(1, Math.round(props.file.size / 1024))} KB`
  return `${(props.file.size / 1024 / 1024).toFixed(1)} MB`
})

watch(
  () => props.file,
  (file) => {
    releasePreview()
    if (file) previewUrl.value = URL.createObjectURL(file)
  },
  { immediate: true },
)

onBeforeUnmount(releasePreview)

function updateText(event: Event) {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
}

function selectFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  input.value = ''
  if (file) acceptFile(file)
}

function handlePaste(event: ClipboardEvent) {
  const imageItem = [...(event.clipboardData?.items ?? [])]
    .find((item) => item.kind === 'file' && item.type.startsWith('image/'))
  const file = imageItem?.getAsFile()
  if (file) acceptFile(file)
}

function acceptFile(file: File) {
  const error = getVocabularyImageFileError(file)
  if (error) {
    emit('file-error', error)
    return
  }
  emit('file-error', '')
  emit('update:file', file)
}

function removeFile() {
  emit('file-error', '')
  emit('update:file', null)
}

function releasePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}
</script>

<style scoped>
.import-composer {
  overflow: hidden;
  border: 1px solid #a7c7b8;
  border-radius: 7px;
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.07);
}

.import-composer:focus-within {
  border-color: #059669;
  box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.12);
}

.import-composer--disabled {
  opacity: 0.72;
}

textarea {
  box-sizing: border-box;
  display: block;
  width: 100%;
  min-height: 128px;
  resize: vertical;
  border: 0;
  outline: 0;
  background: transparent;
  color: #0f172a;
  font: inherit;
  font-size: 14px;
  line-height: 1.65;
  padding: 13px 14px 10px;
}

textarea::placeholder {
  color: #94a3b8;
}

.import-composer__attachment {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) 32px;
  align-items: center;
  gap: 10px;
  margin: 0 10px 8px;
  padding: 8px;
  border: 1px solid #dce7e1;
  border-radius: 6px;
  background: #f8fafc;
}

.import-composer__attachment img {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 5px;
}

.import-composer__attachment div {
  min-width: 0;
}

.import-composer__attachment strong,
.import-composer__attachment span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.import-composer__attachment strong {
  color: #0f172a;
  font-size: 13px;
}

.import-composer__attachment span {
  margin-top: 3px;
  color: #64748b;
  font-size: 11px;
}

.import-composer__attachment button,
.import-composer__add {
  display: grid;
  place-items: center;
  border: 1px solid #dce7e1;
  border-radius: 6px;
  background: #ffffff;
  color: #475569;
  cursor: pointer;
}

.import-composer__attachment button {
  width: 32px;
  height: 32px;
}

footer {
  display: flex;
  min-height: 48px;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  border-top: 1px solid #edf2f7;
}

.import-composer__file-input {
  display: none;
}

.import-composer__add {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  color: #047857;
}

button:hover:not(:disabled),
button:focus-visible {
  border-color: #059669;
  color: #047857;
  outline: none;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

footer span {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
  color: #64748b;
  font-size: 11px;
}

@media (max-width: 520px) {
  footer span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
