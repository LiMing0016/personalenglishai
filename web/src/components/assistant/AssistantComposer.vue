<template>
  <div
    class="assistant-composer"
    :class="{ 'assistant-composer--dragging': draggingOver }"
    @click="focusInput"
    @dragenter.prevent="handleDragEnter"
    @dragover.prevent="handleDragOver"
    @dragleave="handleDragLeave"
    @drop.prevent="handleDrop"
    @paste="handlePaste"
  >
    <input
      ref="fileInputRef"
      type="file"
      class="file-input"
      :accept="assistantAttachmentAccept"
      multiple
      @change="onFileChange"
    />

    <div v-if="attachments.length" class="attachment-strip">
      <div
        v-for="attachment in previewAttachments"
        :key="attachment.id"
        class="attachment-pill"
        :class="`attachment-pill--${attachment.kind}`"
      >
        <img
          v-if="attachment.kind === 'image' && attachment.previewUrl"
          :src="attachment.previewUrl"
          :alt="attachment.name"
          class="attachment-thumb"
        />
        <div v-else class="attachment-icon">{{ attachment.kind === 'image' ? '图' : '档' }}</div>
        <div class="attachment-copy">
          <span class="attachment-name">{{ attachment.name }}</span>
          <span class="attachment-meta">{{ formatAttachmentMeta(attachment.size, attachment.kind) }}</span>
        </div>
        <button
          type="button"
          class="attachment-remove"
          aria-label="移除附件"
          @click="$emit('removeAttachment', attachment.id)"
        >
          ×
        </button>
      </div>
    </div>

    <div class="composer-shell">
      <div class="composer-input-zone">
        <textarea
          ref="textareaRef"
          :value="modelValue"
          class="composer-input"
          :placeholder="visibleComposerPlaceholder"
          rows="1"
          @input="$emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
          @keydown="handleKeydown"
        />
      </div>

      <div class="composer-bottom">
        <div class="composer-bottom-left">
          <div class="action-menu-wrap">
            <button
              type="button"
              class="attach-button"
              :aria-expanded="menuOpen"
              aria-haspopup="menu"
              aria-label="打开更多功能"
              @click="toggleMenu"
            >
              +
            </button>
            <div v-if="menuOpen" class="action-menu" role="menu">
              <button type="button" class="action-menu-item" role="menuitem" @click="chooseFiles">
                <span class="menu-icon">↥</span>
                <span>添加照片和文件</span>
              </button>
              <button
                type="button"
                class="action-menu-item"
                :class="{ active: assistantMode === 'learning' }"
                role="menuitemcheckbox"
                :aria-checked="assistantMode === 'learning'"
                @click="toggleLearningMode"
              >
                <span class="menu-icon">✦</span>
                <span class="menu-copy">
                  <span class="menu-title">学习模式</span>
                  <span class="menu-subtitle">边问边整理笔记</span>
                </span>
              </button>
              <button
                type="button"
                class="action-menu-item"
                :class="{ active: assistantMode === 'exam' }"
                role="menuitemcheckbox"
                :aria-checked="assistantMode === 'exam'"
                @click="toggleExamMode"
              >
                <span class="menu-icon">✓</span>
                <span>考试模式</span>
              </button>
            </div>
          </div>

          <div
            v-if="assistantMode !== 'default'"
            class="mode-strip"
            :class="`mode-strip--${assistantMode}`"
          >
            <span class="mode-pill">{{ activeModeLabel }}</span>
            <span v-if="assistantMode === 'learning'" class="mode-hint">边问边整理笔记</span>
            <button type="button" class="mode-clear" @click="$emit('setAssistantMode', 'default')">取消</button>
          </div>
        </div>

        <button
          type="button"
          class="send-button"
          aria-label="发送"
          :disabled="loading || (!modelValue.trim() && attachments.length === 0)"
          @click="$emit('send')"
        >
          {{ loading ? '…' : '↑' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'

import { assistantAttachmentAccept, type AssistantAttachmentSource } from '@/pages/app/assistantAttachmentRules.ts'
import type { AssistantAttachment, AssistantMode } from '@/pages/app/assistantMock.ts'
import { extractImageFilesFromClipboardData } from './assistantClipboardFiles.ts'

const props = defineProps<{
  modelValue: string
  loading: boolean
  attachments: AssistantAttachment[]
  assistantMode: AssistantMode
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  addFiles: [files: File[], source: AssistantAttachmentSource]
  removeAttachment: [id: string]
  setAssistantMode: [mode: AssistantMode]
  send: []
}>()

const fileInputRef = ref<HTMLInputElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const menuOpen = ref(false)
const draggingOver = ref(false)
const previewUrls = new Map<string, string>()

const activeModeLabel = computed(() => {
  if (props.assistantMode === 'learning') return '学习模式 ✦'
  if (props.assistantMode === 'exam') return '考试模式'
  return ''
})

const composerPlaceholder = computed(() => {
  if (props.assistantMode === 'learning') {
    return '进入学习模式：问问题、选内容，我会帮你沉淀成学习笔记。'
  }
  if (props.assistantMode === 'exam') {
    return '考试模式：告诉我题目、材料或你的答案。'
  }
  return '有问题，尽管问。你也可以上传文件或照片再一起发送。'
})

const visibleComposerPlaceholder = computed(() => props.placeholder ?? composerPlaceholder.value)

const previewAttachments = computed(() =>
  props.attachments.map((attachment) => {
    if (attachment.kind === 'image' && !previewUrls.has(attachment.id)) {
      previewUrls.set(attachment.id, URL.createObjectURL(attachment.file))
    }
    return {
      ...attachment,
      previewUrl: previewUrls.get(attachment.id) ?? '',
    }
  }),
)

onBeforeUnmount(() => {
  for (const url of previewUrls.values()) {
    URL.revokeObjectURL(url)
  }
  previewUrls.clear()
})

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
    return
  }
  event.preventDefault()
  emit('send')
}

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function chooseFiles() {
  menuOpen.value = false
  fileInputRef.value?.click()
}

function toggleExamMode() {
  emit('setAssistantMode', props.assistantMode === 'exam' ? 'default' : 'exam')
  menuOpen.value = false
}

function toggleLearningMode() {
  emit('setAssistantMode', props.assistantMode === 'learning' ? 'default' : 'learning')
  menuOpen.value = false
}

function onFileChange(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files ?? [])
  if (files.length > 0) {
    emit('addFiles', files, 'picker')
  }
  ;(event.target as HTMLInputElement).value = ''
}

function focusInput(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  if (target?.closest('button, input, textarea, [role="menu"], .attachment-remove')) {
    return
  }
  textareaRef.value?.focus()
}

function handlePaste(event: ClipboardEvent) {
  const files = extractImageFilesFromClipboardData(event.clipboardData)
  if (files.length === 0) {
    return
  }

  event.preventDefault()
  emit('addFiles', files, 'paste')
}

function handleDragEnter() {
  draggingOver.value = true
}

function handleDragOver() {
  draggingOver.value = true
}

function handleDragLeave(event: DragEvent) {
  const currentTarget = event.currentTarget as HTMLElement | null
  const relatedTarget = event.relatedTarget as Node | null
  if (currentTarget && relatedTarget && currentTarget.contains(relatedTarget)) {
    return
  }
  draggingOver.value = false
}

function handleDrop(event: DragEvent) {
  draggingOver.value = false
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (files.length > 0) {
    emit('addFiles', files, 'drop')
  }
}

function formatAttachmentMeta(size: number, kind: AssistantAttachment['kind']) {
  const sizeKb = size > 0 ? `${Math.max(1, Math.round(size / 1024))} KB` : '未知大小'
  return kind === 'image' ? `图片 · ${sizeKb}` : `文件 · ${sizeKb}`
}
</script>

<style scoped>
.assistant-composer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: min(980px, 100%);
  margin: 0 auto;
  padding: 10px 12px 12px;
  border: 1px solid #dbe3ea;
  border-radius: 24px;
  background: #ffffff;
  box-shadow: 0 24px 50px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(18px);
  box-sizing: border-box;
}

.assistant-composer--dragging {
  border-color: #10b981;
  background: #f0fdf4;
  box-shadow: 0 24px 54px rgba(4, 120, 87, 0.18);
}

.file-input {
  display: none;
}

.attachment-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.attachment-pill {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  max-width: 100%;
  padding: 10px 12px;
  border: 1px solid #dbe3ea;
  border-radius: 18px;
  background: #f8fafc;
}

.attachment-thumb,
.attachment-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  flex-shrink: 0;
}

.attachment-thumb {
  object-fit: cover;
}

.attachment-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(148, 163, 184, 0.22);
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.attachment-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.attachment-name {
  overflow: hidden;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-meta {
  color: #94a3b8;
  font-size: 11px;
}

.attachment-remove {
  border: none;
  background: transparent;
  color: #cbd5e1;
  font-size: 18px;
  cursor: pointer;
}

.composer-shell {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
}

.composer-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 38px;
}

.composer-bottom-left {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.action-menu-wrap {
  position: relative;
}

.attach-button {
  width: 36px;
  height: 36px;
  border: 1px solid transparent;
  border-radius: 50%;
  background: #f8fafc;
  color: #334155;
  font-size: 26px;
  line-height: 1;
  cursor: pointer;
}

.attach-button:hover,
.attach-button:focus-visible,
.attach-button[aria-expanded='true'] {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
  outline: none;
}

.action-menu {
  position: absolute;
  left: 0;
  bottom: calc(100% + 8px);
  display: flex;
  width: 220px;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
  border: 1px solid #dbe3ea;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.16);
  z-index: 20;
}

.action-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #0f172a;
  padding: 11px 12px;
  font-size: 14px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}

.action-menu-item:hover,
.action-menu-item.active {
  background: #ecfdf5;
  color: #047857;
}

.menu-icon {
  width: 18px;
  color: currentColor;
  font-weight: 800;
  text-align: center;
}

.menu-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.menu-title {
  color: currentColor;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.15;
}

.menu-subtitle {
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
}

.action-menu-item:hover .menu-subtitle,
.action-menu-item.active .menu-subtitle {
  color: #047857;
}

.composer-input-zone {
  width: 100%;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.mode-strip {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  border-radius: 999px;
  background: #f8fafc;
  padding: 2px 4px 2px 2px;
}

.mode-pill {
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 700;
}

.mode-strip--learning .mode-pill {
  background: #eff6ff;
  color: #2563eb;
}

.mode-hint {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mode-clear {
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.composer-input {
  width: 100%;
  min-height: 42px;
  border: none;
  resize: none;
  background: transparent;
  color: #0f172a;
  font-size: 16px;
  line-height: 1.6;
  outline: none;
}

.composer-input::placeholder {
  color: #94a3b8;
}

.send-button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 999px;
  background: #047857;
  color: #ffffff;
  padding: 0;
  font-size: 24px;
  font-weight: 800;
  line-height: 1;
  cursor: pointer;
  transition: transform 0.15s ease, opacity 0.15s ease;
}

.send-button:hover:not(:disabled) {
  transform: translateY(-1px);
}

.send-button:disabled {
  cursor: default;
  opacity: 0.55;
}

@media (max-width: 960px) {
  .assistant-composer {
    width: 100%;
  }

  .composer-bottom {
    align-items: flex-end;
  }
}
</style>
