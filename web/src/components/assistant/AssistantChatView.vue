<template>
  <section class="assistant-chat-view">
    <div v-if="messages.length === 0" class="empty-state">
      <p class="eyebrow">学习助手</p>
      <h1 class="empty-title">{{ emptyTitle }}</h1>
      <p class="empty-subtitle">{{ emptySubtitle }}</p>
      <AssistantStarterCards @choose="$emit('chooseStarter', $event)" />
    </div>

    <div v-else class="message-list">
      <article
        v-for="message in messages"
        :key="message.id"
        class="message-row"
        :class="`message-row--${message.role}`"
      >
        <div class="message-bubble" :class="{ 'message-bubble--loading': message.status === 'loading' }">
          <div v-if="message.attachments?.length" class="message-attachments">
            <div
              v-for="attachment in message.attachments"
              :key="attachment.id"
              class="message-attachment"
            >
              <img
                v-if="attachment.kind === 'image'"
                :src="previewUrlById(attachment.id, attachment.file)"
                :alt="attachment.name"
                class="message-attachment-thumb"
              />
              <div v-else class="message-attachment-icon">档</div>
              <span class="message-attachment-name">{{ attachment.name }}</span>
            </div>
          </div>
          <div
            v-if="message.role === 'assistant'"
            class="message-content message-content--markdown"
            v-html="renderAssistantMarkdown(message.content)"
          ></div>
          <p v-else class="message-content message-content--plain">{{ message.content }}</p>
          <div
            v-if="message.role === 'assistant' && message.status === 'done'"
            class="message-actions"
          >
            <button
              type="button"
              class="message-action-button"
              aria-label="复制"
              title="复制"
              @click="$emit('copyMessage', message.content)"
            >
              <svg class="message-action-icon" viewBox="0 0 24 24" aria-hidden="true">
                <rect x="9" y="9" width="11" height="11" rx="2" />
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
              </svg>
            </button>
            <button
              type="button"
              class="message-action-button"
              aria-label="重试"
              title="重试"
              @click="$emit('retryMessage', message.id)"
            >
              <svg class="message-action-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M21 12a9 9 0 0 1-15.3 6.4" />
                <path d="M3 12A9 9 0 0 1 18.3 5.6" />
                <path d="M18 2v4h4" />
                <path d="M6 22v-4H2" />
              </svg>
            </button>
          </div>
        </div>
      </article>

      <div v-if="errorMessage" class="inline-error">
        <span>{{ errorMessage }}</span>
        <button
          v-if="canRetry"
          type="button"
          class="retry-button"
          @click="$emit('retry')"
        >
          重试
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount } from 'vue'

import AssistantStarterCards from './AssistantStarterCards.vue'
import { renderAssistantMarkdown } from './markdown.ts'
import type { AssistantMessage } from '@/pages/app/assistantMock.ts'

defineProps<{
  messages: AssistantMessage[]
  errorMessage: string
  canRetry: boolean
  emptyTitle: string
  emptySubtitle: string
}>()

const emit = defineEmits<{
  chooseStarter: [prompt: string]
  copyMessage: [content: string]
  retryMessage: [messageId: string]
  retry: []
}>()

const previewUrls = new Map<string, string>()

function previewUrlById(id: string, file: File) {
  if (!previewUrls.has(id)) {
    previewUrls.set(id, URL.createObjectURL(file))
  }
  return previewUrls.get(id)!
}


onBeforeUnmount(() => {
  for (const url of previewUrls.values()) {
    URL.revokeObjectURL(url)
  }
  previewUrls.clear()
})
</script>

<style scoped>
.assistant-chat-view {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding: 28px 40px 220px;
  box-sizing: border-box;
}

.empty-state {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 0;
  max-width: 920px;
  margin: 0 auto;
  text-align: center;
}

.eyebrow {
  margin: 0 0 10px;
  color: #6ee7b7;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.empty-title {
  margin: 0;
  color: #0f172a;
  font-size: clamp(34px, 5vw, 52px);
  font-weight: 800;
  letter-spacing: -0.04em;
}

.empty-subtitle {
  max-width: 620px;
  margin: 14px 0 32px;
  color: #475569;
  font-size: 17px;
  line-height: 1.65;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: min(920px, 100%);
  margin: 0 auto;
}

.message-row {
  display: flex;
}

.message-row--assistant {
  justify-content: flex-start;
}

.message-row--user {
  justify-content: flex-end;
}

.message-bubble {
  max-width: min(680px, 78%);
  padding: 2px 0;
  border-radius: 0;
  background: transparent;
  border: none;
  box-shadow: none;
}

.message-row--user .message-bubble {
  max-width: min(560px, 70%);
  padding: 10px 14px;
  border-radius: 16px;
  background: #f1f5f9;
}

.message-bubble--loading {
  border-style: dashed;
}

.message-content {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.7;
}

.message-content--plain {
  white-space: pre-wrap;
}

.message-content--markdown :deep(p) {
  margin: 0 0 14px;
}

.message-content--markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.message-content--markdown :deep(h1),
.message-content--markdown :deep(h2),
.message-content--markdown :deep(h3) {
  margin: 22px 0 10px;
  color: #0f172a;
  font-weight: 800;
  line-height: 1.35;
}

.message-content--markdown :deep(h1:first-child),
.message-content--markdown :deep(h2:first-child),
.message-content--markdown :deep(h3:first-child) {
  margin-top: 0;
}

.message-content--markdown :deep(h1) {
  font-size: 22px;
}

.message-content--markdown :deep(h2) {
  font-size: 19px;
}

.message-content--markdown :deep(h3) {
  font-size: 16px;
}

.message-content--markdown :deep(ul) {
  margin: 0 0 14px;
  padding-left: 20px;
}

.message-content--markdown :deep(li) {
  margin: 4px 0;
}

.message-content--markdown :deep(blockquote) {
  margin: 0 0 14px;
  padding-left: 14px;
  border-left: 3px solid #cbd5e1;
  color: #334155;
}

.message-content--markdown :deep(strong) {
  font-weight: 800;
}

.message-content--markdown :deep(code) {
  padding: 2px 5px;
  border-radius: 5px;
  background: #e2e8f0;
  color: #0f172a;
  font-size: 0.92em;
}

.message-content--markdown :deep(hr) {
  margin: 20px 0;
  border: none;
  border-top: 1px solid #e2e8f0;
}

.message-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 10px;
}

.message-action-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  padding: 0;
  cursor: pointer;
}

.message-action-button:hover,
.message-action-button:focus-visible {
  background: #e2e8f0;
  color: #0f172a;
  outline: none;
}

.message-action-icon {
  width: 19px;
  height: 19px;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
  fill: none;
}

.message-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.message-attachment {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  padding: 8px 10px;
  border-radius: 16px;
  background: rgba(148, 163, 184, 0.12);
}

.message-row--user .message-attachment {
  background: #e2e8f0;
}

.message-attachment-thumb,
.message-attachment-icon {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  flex-shrink: 0;
}

.message-attachment-thumb {
  object-fit: cover;
}

.message-attachment-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.14);
  color: inherit;
  font-size: 12px;
  font-weight: 700;
}

.message-attachment-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 600;
}

.inline-error {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  align-self: flex-start;
  padding: 12px 14px;
  border-radius: 14px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 13px;
}

.retry-button {
  border: none;
  border-radius: 999px;
  background: #fee2e2;
  color: #991b1b;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

@media (max-width: 960px) {
  .assistant-chat-view {
    padding: 24px 18px 226px;
  }

  .message-bubble {
    max-width: 100%;
  }
}
</style>
