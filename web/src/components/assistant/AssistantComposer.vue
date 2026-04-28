<template>
  <div class="assistant-composer">
    <input
      ref="fileInputRef"
      type="file"
      class="file-input"
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
      <div class="composer-controls">
        <div class="action-menu-wrap">
          <button
            type="button"
            class="attach-button"
            :aria-expanded="menuOpen"
            aria-haspopup="menu"
            @click="toggleMenu"
          >
            +
          </button>
          <div v-if="menuOpen" class="action-menu" role="menu">
            <button type="button" class="action-menu-item" role="menuitem" @click="chooseFiles">
              <span class="menu-icon">↥</span>
              <span>上传照片和文件</span>
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
        <span class="attach-label">更多</span>
      </div>

      <div class="composer-input-zone">
        <div v-if="assistantMode === 'exam'" class="mode-strip">
          <span class="mode-pill">考试模式</span>
          <button type="button" class="mode-clear" @click="$emit('setAssistantMode', 'default')">取消</button>
        </div>
        <textarea
          :value="modelValue"
          class="composer-input"
          placeholder="有问题，尽管问。你也可以上传文件或照片再一起发送。"
          rows="1"
          @input="$emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
          @keydown="handleKeydown"
        />
      </div>

      <button
        type="button"
        class="send-button"
        :disabled="loading || (!modelValue.trim() && attachments.length === 0)"
        @click="$emit('send')"
      >
        {{ loading ? '发送中...' : '发送' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'

import type { AssistantAttachment, AssistantMode } from '@/pages/app/assistantMock.ts'

const props = defineProps<{
  modelValue: string
  loading: boolean
  attachments: AssistantAttachment[]
  assistantMode: AssistantMode
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  addFiles: [files: File[]]
  removeAttachment: [id: string]
  setAssistantMode: [mode: AssistantMode]
  send: []
}>()

const fileInputRef = ref<HTMLInputElement | null>(null)
const menuOpen = ref(false)
const previewUrls = new Map<string, string>()

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

function onFileChange(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files ?? [])
  if (files.length > 0) {
    emit('addFiles', files)
  }
  ;(event.target as HTMLInputElement).value = ''
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
  gap: 14px;
  width: min(980px, calc(100% - 32px));
  margin: 0 auto;
  padding: 14px 16px 16px;
  border: 1px solid #dbe3ea;
  border-radius: 28px;
  background: #ffffff;
  box-shadow: 0 24px 50px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(18px);
  box-sizing: border-box;
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
  align-items: flex-end;
  gap: 12px;
  min-height: 96px;
}

.composer-controls {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.action-menu-wrap {
  position: relative;
}

.attach-button {
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 50%;
  background: #ecfdf5;
  color: #047857;
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
}

.action-menu {
  position: absolute;
  left: 0;
  bottom: calc(100% + 12px);
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

.attach-label {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 600;
}

.composer-input-zone {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.mode-strip {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mode-pill {
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 700;
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
  min-height: 84px;
  border: none;
  resize: none;
  background: transparent;
  color: #0f172a;
  font-size: 16px;
  line-height: 1.75;
  outline: none;
}

.composer-input::placeholder {
  color: #94a3b8;
}

.send-button {
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #d1fae5 0%, #6ee7b7 100%);
  color: #064e3b;
  padding: 12px 20px;
  font-size: 14px;
  font-weight: 700;
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
    width: calc(100% - 24px);
  }

  .composer-shell {
    flex-wrap: wrap;
  }

  .composer-controls {
    flex-direction: row;
    width: 100%;
    justify-content: space-between;
  }

  .send-button {
    width: 100%;
  }
}
</style>
