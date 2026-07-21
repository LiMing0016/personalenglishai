<template>
  <section
    ref="scrollContainerRef"
    class="assistant-chat-view"
    :class="{ 'assistant-chat-view--empty': messages.length === 0 }"
    @scroll.passive="handleScroll"
  >
    <div v-if="messages.length === 0" class="empty-state">
      <h1 class="empty-title">{{ emptyTitle }}</h1>
      <p class="empty-subtitle">{{ emptySubtitle }}</p>
      <AssistantStarterCards
        :selected-goal="selectedGoal"
        @select-goal="$emit('selectGoal', $event)"
        @choose="$emit('chooseStarter', $event)"
      >
        <template #composer>
          <slot name="empty-composer"></slot>
        </template>
      </AssistantStarterCards>
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
          <template v-if="message.role === 'assistant'">
            <div
              :data-learning-message-id="message.id"
              :data-learning-message-role="message.role"
              class="message-content message-content--markdown"
              :class="`message-content--markdown-${markdownTheme}`"
              v-html="renderAssistantMarkdown(message.content)"
              @click="onRenderedMarkdownClick"
              @mouseup="handleLearningAssetSelection(message)"
              @keyup="handleLearningAssetSelection(message)"
            ></div>
            <AssistantBlockRenderer
              v-if="message.parts?.length"
              :blocks="message.parts"
              @action="$emit('chooseStarter', $event)"
            />
          </template>
          <p
            v-else
            :data-learning-message-id="message.id"
            :data-learning-message-role="message.role"
            class="message-content message-content--plain"
            @mouseup="handleLearningAssetSelection(message)"
            @keyup="handleLearningAssetSelection(message)"
          >
            {{ message.content }}
          </p>
          <div
            v-if="message.role === 'assistant' && message.status === 'done'"
            class="message-actions"
          >
            <button
              type="button"
              class="message-action-button"
              aria-label="复制"
              title="复制"
              @mousedown.stop
              @mouseup.stop
              @click.stop="$emit('copyMessage', message.content)"
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
              @mousedown.stop
              @mouseup.stop
              @click.stop="$emit('retryMessage', message.id)"
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
    <LearningAssetSelectionToolbar
      :selected-text="selectionToolbar.selectedText"
      :left="selectionToolbar.left"
      :top="selectionToolbar.top"
      :can-append-to-active="canAppendToLearningAsset"
      @create="emitLearningAssetSelection"
      @append="emitAppendToLearningAsset"
    />
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

import AssistantBlockRenderer from './AssistantBlockRenderer.vue'
import AssistantStarterCards, { type AssistantStarterChoice } from './AssistantStarterCards.vue'
import type { AssistantStarterGoalId } from './AssistantStarterCards.vue'
import LearningAssetSelectionToolbar from './LearningAssetSelectionToolbar.vue'
import { copyMarkdownCodeFromClick, renderAssistantMarkdown } from './markdown.ts'
import type { AssistantMessage } from '@/pages/app/assistantMock.ts'
import { resolveLearningAssetContext, type LearningAssetType } from '@/types/learningAssets.ts'

export interface AssistantLearningAssetSelection {
  selectedText: string
  contextText: string
  messageId: string
  type?: LearningAssetType
}

const props = defineProps<{
  messages: AssistantMessage[]
  errorMessage: string
  canRetry: boolean
  emptyTitle: string
  emptySubtitle: string
  markdownTheme: 'marktext' | 'milkdown'
  canAppendToLearningAsset: boolean
  selectedGoal: AssistantStarterGoalId | null
}>()

const emit = defineEmits<{
  chooseStarter: [choice: AssistantStarterChoice]
  copyMessage: [content: string]
  retryMessage: [messageId: string]
  retry: []
  createLearningAsset: [selection: AssistantLearningAssetSelection]
  appendToLearningAsset: [selection: AssistantLearningAssetSelection]
  selectGoal: [goalId: AssistantStarterGoalId]
}>()

const previewUrls = new Map<string, string>()
const scrollContainerRef = ref<HTMLElement | null>(null)
const shouldAutoFollowMessages = ref(true)
const selectionToolbarWidth = 300
const selectionToolbarHeight = 44
const selectionToolbarGutter = 12
const selectionToolbar = reactive({
  selectedText: '',
  contextText: '',
  messageId: '',
  left: 0,
  top: 0,
})

function previewUrlById(id: string, file: File) {
  if (!previewUrls.has(id)) {
    previewUrls.set(id, URL.createObjectURL(file))
  }
  return previewUrls.get(id)!
}

function onRenderedMarkdownClick(event: MouseEvent) {
  void copyMarkdownCodeFromClick(event)
}

function handleScroll() {
  const container = scrollContainerRef.value
  if (!container) return
  const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight
  shouldAutoFollowMessages.value = distanceFromBottom < 96
}

function scrollToConversationBottom() {
  const container = scrollContainerRef.value
  if (!container) return
  requestAnimationFrame(() => {
    container.scrollTop = container.scrollHeight
  })
}

function handleLearningAssetSelection(message: AssistantMessage) {
  window.setTimeout(() => syncAssistantSelection(message), 0)
}

function handleDocumentSelectionChange() {
  window.setTimeout(() => syncAssistantSelection(), 0)
}

function syncAssistantSelection(fallbackMessage?: AssistantMessage) {
  const selection = window.getSelection()
  const selectedText = selection?.toString().trim() ?? ''
  if (!selection || selection.isCollapsed || !selectedText) {
    clearLearningAssetSelection()
    return
  }

  const range = selection.rangeCount > 0 ? selection.getRangeAt(0) : null
  const anchorElement = selection.anchorNode instanceof Element
    ? selection.anchorNode
    : selection.anchorNode?.parentElement
  const contentElement = anchorElement?.closest<HTMLElement>('[data-learning-message-id]')
  const messageId = contentElement?.dataset.learningMessageId ?? fallbackMessage?.id
  const message = props.messages.find((item) => item.id === messageId) ?? fallbackMessage

  if (!range || !contentElement || !message) {
    clearLearningAssetSelection()
    return
  }

  const rect = range.getBoundingClientRect()
  selectionToolbar.selectedText = selectedText
  selectionToolbar.contextText = resolveLearningAssetContext({
    selectedText,
    contextText: readSelectionContextText(range, contentElement, anchorElement),
  })
  selectionToolbar.messageId = message.id
  selectionToolbar.left = Math.max(
    selectionToolbarGutter,
    Math.min(rect.left, window.innerWidth - selectionToolbarWidth),
  )
  selectionToolbar.top = resolveSelectionToolbarTop(rect)
}

function resolveSelectionToolbarTop(rect: DOMRect) {
  const below = rect.bottom + 8
  if (below + selectionToolbarHeight <= window.innerHeight - selectionToolbarGutter) {
    return below
  }
  return Math.max(selectionToolbarGutter, rect.top - selectionToolbarHeight - 8)
}

function readSelectionContextText(
  range: Range,
  contentElement: HTMLElement,
  anchorElement?: Element | null,
) {
  const commonAncestor = range.commonAncestorContainer instanceof Element
    ? range.commonAncestorContainer
    : range.commonAncestorContainer.parentElement
  const contextElement = findSelectionContextElement(anchorElement, contentElement)
    ?? findSelectionContextElement(commonAncestor, contentElement)
  return contextElement?.textContent?.trim() || ''
}

function findSelectionContextElement(
  startElement: Element | null | undefined,
  contentElement: HTMLElement,
) {
  if (!startElement || !contentElement.contains(startElement)) return null

  const tableRow = startElement.closest<HTMLElement>('tr')
  if (tableRow && contentElement.contains(tableRow)) return tableRow

  const block = startElement.closest<HTMLElement>('p, li, blockquote, h1, h2, h3, h4, h5, h6, td, th')
  if (block && contentElement.contains(block)) return block

  return null
}

function clearLearningAssetSelection() {
  selectionToolbar.selectedText = ''
  selectionToolbar.contextText = ''
  selectionToolbar.messageId = ''
}

function emitLearningAssetSelection(type: LearningAssetType) {
  if (!selectionToolbar.selectedText || !selectionToolbar.messageId) return
  emit('createLearningAsset', {
    selectedText: selectionToolbar.selectedText,
    contextText: selectionToolbar.contextText,
    messageId: selectionToolbar.messageId,
    type,
  })
  window.getSelection()?.removeAllRanges()
  clearLearningAssetSelection()
}

function emitAppendToLearningAsset() {
  if (!selectionToolbar.selectedText || !selectionToolbar.messageId || !props.canAppendToLearningAsset) return
  emit('appendToLearningAsset', {
    selectedText: selectionToolbar.selectedText,
    contextText: selectionToolbar.contextText,
    messageId: selectionToolbar.messageId,
  })
  window.getSelection()?.removeAllRanges()
  clearLearningAssetSelection()
}

watch(
  () => props.messages.map((message) => `${message.id}:${message.status}:${message.content.length}`).join('|'),
  async () => {
    if (!shouldAutoFollowMessages.value) return
    await nextTick()
    scrollToConversationBottom()
  },
  { flush: 'post' },
)

onMounted(() => {
  document.addEventListener('selectionchange', handleDocumentSelectionChange)
  scrollToConversationBottom()
})

onBeforeUnmount(() => {
  document.removeEventListener('selectionchange', handleDocumentSelectionChange)
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
  justify-content: flex-start;
  width: min(920px, 100%);
  min-height: 0;
  max-width: 920px;
  margin: clamp(72px, 10vh, 118px) auto 0;
  text-align: center;
}

.assistant-chat-view--empty {
  padding-bottom: 48px;
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
  margin: 12px 0 24px;
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

.message-content {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.7;
  overflow-wrap: anywhere;
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
.message-content--markdown :deep(h3),
.message-content--markdown :deep(h4),
.message-content--markdown :deep(h5),
.message-content--markdown :deep(h6) {
  margin: 22px 0 10px;
  color: #0f172a;
  font-weight: 800;
  line-height: 1.35;
}

.message-content--markdown :deep(h1:first-child),
.message-content--markdown :deep(h2:first-child),
.message-content--markdown :deep(h3:first-child),
.message-content--markdown :deep(h4:first-child),
.message-content--markdown :deep(h5:first-child),
.message-content--markdown :deep(h6:first-child) {
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

.message-content--markdown :deep(h4),
.message-content--markdown :deep(h5),
.message-content--markdown :deep(h6) {
  font-size: 15px;
}

.message-content--markdown :deep(ul),
.message-content--markdown :deep(ol) {
  margin: 0 0 14px;
  padding-left: 20px;
}

.message-content--markdown :deep(li > ul),
.message-content--markdown :deep(li > ol) {
  margin: 6px 0 2px;
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

.message-content--markdown :deep(a) {
  color: #047857;
  font-weight: 700;
  text-decoration: underline;
  text-decoration-color: #a7f3d0;
  text-decoration-thickness: 1.5px;
  text-underline-offset: 3px;
}

.message-content--markdown :deep(a:hover) {
  color: #065f46;
  text-decoration-color: currentColor;
}

.message-content--markdown :deep(a:focus-visible) {
  border-radius: 4px;
  outline: 3px solid rgba(16, 185, 129, 0.28);
  outline-offset: 2px;
}

.message-content--markdown :deep(del) {
  color: #64748b;
}

.message-content--markdown :deep(.contains-task-list) {
  list-style: none;
  padding-left: 0;
}

.message-content--markdown :deep(.task-list-item) {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.message-content--markdown :deep(.task-list-item input) {
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
  margin: 5px 0 0;
  accent-color: #059669;
}

.message-content--markdown :deep(.task-list-item:has(input:checked)) {
  color: #64748b;
  text-decoration: line-through;
  text-decoration-color: #cbd5e1;
}

.message-content--markdown :deep(.task-list-item input:checked) {
  accent-color: #059669;
  opacity: 1;
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

.message-content--markdown :deep(.markdown-table-scroll) {
  max-width: 100%;
  overflow-x: auto;
  margin: 14px 0 18px;
  border-radius: 10px;
  scrollbar-color: #94a3b8 transparent;
  scrollbar-width: thin;
}

.message-content--markdown :deep(.markdown-table-scroll:focus-visible) {
  outline: 3px solid rgba(16, 185, 129, 0.24);
  outline-offset: 3px;
}

.message-content--markdown :deep(table) {
  width: 100%;
  min-width: 520px;
  border-collapse: collapse;
  font-size: 14px;
  line-height: 1.55;
}

.message-content--markdown :deep(th),
.message-content--markdown :deep(td) {
  border-bottom: 1px solid #e2e8f0;
  padding: 11px 13px;
  text-align: left;
  vertical-align: top;
}

.message-content--markdown :deep(th) {
  color: #0f172a;
  font-weight: 800;
}

.message-content--markdown :deep(td:first-child),
.message-content--markdown :deep(th:first-child) {
  font-weight: 800;
}

.message-content--markdown :deep(.markdown-code-block) {
  margin: 14px 0 18px;
  overflow: hidden;
  border: 1px solid #dbe3ea;
  border-radius: 12px;
  background: #ffffff;
  color: #0f172a;
}

.message-content--markdown :deep(.markdown-code-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
  padding: 8px 12px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.message-content--markdown :deep(.markdown-code-copy) {
  height: 26px;
  border: 1px solid #dbe3ea;
  border-radius: 999px;
  background: #ffffff;
  color: #334155;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 750;
  cursor: pointer;
}

.message-content--markdown :deep(.markdown-code-copy:hover),
.message-content--markdown :deep(.markdown-code-copy--copied) {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #2563eb;
}

.message-content--markdown :deep(.markdown-code-block pre) {
  margin: 0;
  overflow-x: auto;
  padding: 13px 14px;
  white-space: pre;
}

.message-content--markdown :deep(.markdown-code-block--wrap pre) {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.message-content--markdown :deep(.markdown-code-block code) {
  background: transparent;
  padding: 0;
  color: inherit;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.message-content--markdown :deep(.markdown-image) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 14px auto 18px;
  border-radius: 12px;
}

.message-content--markdown-marktext :deep(h1),
.message-content--markdown-marktext :deep(h2) {
  padding-bottom: 8px;
  border-bottom: 1px solid #e2e8f0;
}

.message-content--markdown-marktext :deep(table) {
  background: #ffffff;
}

.message-content--markdown-marktext :deep(th) {
  background: #f8fafc;
}

.message-content--markdown-milkdown {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message-content--markdown-milkdown :deep(p),
.message-content--markdown-milkdown :deep(ul),
.message-content--markdown-milkdown :deep(ol),
.message-content--markdown-milkdown :deep(blockquote),
.message-content--markdown-milkdown :deep(.markdown-table-scroll) {
  margin: 0;
  padding: 14px 16px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

.message-content--markdown-milkdown :deep(ul),
.message-content--markdown-milkdown :deep(ol) {
  padding-left: 34px;
}

.message-content--markdown-milkdown :deep(blockquote) {
  border-left: 4px solid #14b8a6;
  color: #0f172a;
}

.message-content--markdown-milkdown :deep(h1),
.message-content--markdown-milkdown :deep(h2),
.message-content--markdown-milkdown :deep(h3),
.message-content--markdown-milkdown :deep(h4),
.message-content--markdown-milkdown :deep(h5),
.message-content--markdown-milkdown :deep(h6) {
  margin: 12px 0 0;
  padding: 0 2px;
}

.message-content--markdown-milkdown :deep(h1:first-child),
.message-content--markdown-milkdown :deep(h2:first-child),
.message-content--markdown-milkdown :deep(h3:first-child),
.message-content--markdown-milkdown :deep(h4:first-child),
.message-content--markdown-milkdown :deep(h5:first-child),
.message-content--markdown-milkdown :deep(h6:first-child) {
  margin-top: 0;
}

.message-content--markdown-milkdown :deep(th) {
  background: #ecfdf5;
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

@media (max-width: 1024px) {
  .assistant-chat-view {
    padding: 20px 18px 210px;
  }

  .assistant-chat-view--empty {
    padding-bottom: 36px;
  }

  .message-bubble {
    max-width: 100%;
  }

  .message-row--user .message-bubble {
    max-width: min(620px, 88%);
  }

  .message-content--markdown :deep(:not(pre) > code) {
    overflow-wrap: anywhere;
    word-break: break-word;
  }
}

@media (max-width: 520px) {
  .message-content--markdown :deep(.markdown-table-scroll--cards) {
    overflow: visible;
  }

  .message-content--markdown :deep(.markdown-table--responsive-cards) {
    display: block;
    min-width: 0;
    background: transparent;
  }

  .message-content--markdown :deep(.markdown-table--responsive-cards thead) {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    clip-path: inset(50%);
    white-space: nowrap;
  }

  .message-content--markdown :deep(.markdown-table--responsive-cards tbody),
  .message-content--markdown :deep(.markdown-table--responsive-cards tr) {
    display: block;
  }

  .message-content--markdown :deep(.markdown-table--responsive-cards tr) {
    overflow: hidden;
    margin-bottom: 10px;
    border: 1px solid #dbe3ea;
    border-radius: 12px;
    background: #ffffff;
  }

  .message-content--markdown :deep(.markdown-table--responsive-cards tr:last-child) {
    margin-bottom: 0;
  }

  .message-content--markdown :deep(.markdown-table--responsive-cards td) {
    display: grid;
    grid-template-columns: minmax(76px, 34%) minmax(0, 1fr);
    gap: 10px;
    width: 100%;
    border-bottom: 1px solid #e2e8f0;
    padding: 10px 12px;
    overflow-wrap: anywhere;
  }

  .message-content--markdown :deep(.markdown-table--responsive-cards td:last-child) {
    border-bottom: 0;
  }

  .message-content--markdown :deep(.markdown-table--responsive-cards td::before) {
    content: attr(data-label);
    color: #64748b;
    font-size: 12px;
    font-weight: 800;
  }
}
</style>
