<template>
  <div class="chat-panel">
    <header class="chat-header">
      <div class="chat-title-wrap">
        <h3 class="chat-title">AI Chat</h3>
        <div class="mode-switch">
          <button
            v-for="m in modes"
            :key="m"
            type="button"
            class="mode-btn"
            :class="{ active: mode === m }"
            @click="mode = m"
          >
            {{ m }}
          </button>
        </div>
      </div>
      <div class="chat-actions">
        <button type="button" class="action-btn" @click="onClear">Clear</button>
        <button type="button" class="action-btn close" @click="$emit('close')">Close</button>
      </div>
    </header>

    <section ref="messageListRef" class="message-list">
      <div
        v-for="(m, idx) in messages"
        :key="`${m.role}-${idx}-${m.at}`"
        class="bubble-row"
        :class="m.role"
      >
        <div class="bubble" v-html="renderMarkdown(m.text)"></div>
      </div>
    </section>

    <footer class="composer">
      <div v-if="selectedText" class="selected-chip">
        <div class="selected-chip-header">
          <span class="selected-chip-title">Selected text</span>
          <button type="button" class="selected-chip-close" @click="clearSelectedText">x</button>
        </div>
        <div class="selected-chip-content">{{ selectedTextPreview }}</div>
      </div>
      <div class="composer-input-wrap">
        <textarea
          ref="composerInputRef"
          :value="modelValue"
          class="composer-input"
          placeholder="Type instruction..."
          rows="3"
          @input="onInput"
          @keydown="onKeydown"
        />
        <button
          type="button"
          class="send-icon-btn"
          :class="{ generating: isGenerating, disabled: !canSend }"
          :disabled="!isGenerating && !canSend"
          :title="isGenerating ? 'Stop' : 'Send'"
          @click="onSendOrStop"
        >
          <span v-if="!isGenerating" class="send-arrow" aria-hidden="true">↑</span>
          <span v-else class="send-stop" aria-hidden="true"></span>
        </button>
      </div>
      <div class="composer-actions">
        <button
          v-if="lastChatResult?.replaceText"
          type="button"
          class="apply-btn"
          :disabled="!canReplaceSelection"
          @click="onReplaceSelection"
        >
          Replace Selection
        </button>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onMounted, ref, watch } from 'vue'
import { writingSelectionStoreKey } from '../useWritingSelectionStore'

type Mode = 'sm' | 'md' | 'lg'
type MessageRole = 'user' | 'assistant'
type ChatMessage = { role: MessageRole; text: string; at: number }
type RecentMessageDto = { role: 'user' | 'assistant'; content: string }

const DEFAULT_ASSISTANT_HINT = 'Send an instruction and I will rewrite it.'
const CHAT_CLEARED_HINT = 'Chat cleared.'
const CHAT_HISTORY_KEY_PREFIX = 'peai:ai-chat:history:'

const props = withDefaults(
  defineProps<{
    modelValue: string
    selectedTextPinned?: string
    selectionDismissed?: boolean
    selectedSpanPinned?: { start: number; end: number } | null
    lastChatResult?: { displayText: string; replaceText?: string } | null
    conversationId?: string
    isGenerating?: boolean
  }>(),
  {
    selectedTextPinned: '',
    selectionDismissed: false,
    isGenerating: false,
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
  stop: []
  'dismiss-selection': []
  'replace-selection-with': [resultText: string]
  close: []
  cleared: []
}>()

const modes: Mode[] = ['sm', 'md', 'lg']
const mode = ref<Mode>('md')
const messageListRef = ref<HTMLElement | null>(null)
const composerInputRef = ref<HTMLTextAreaElement | null>(null)
const messages = ref<ChatMessage[]>([
  { role: 'assistant', text: DEFAULT_ASSISTANT_HINT, at: Date.now() },
])
const lastAssistantPayload = ref('')
const selectionStore = inject(writingSelectionStoreKey, null)
const selectedText = computed(() => selectionStore?.selectedText.value ?? '')
const selectedTextPreview = computed(() => {
  const text = selectedText.value
  if (text.length <= 80) return text
  return `${text.slice(0, 80)}...`
})
const canSend = computed(() => props.modelValue.trim().length > 0)

const canReplaceSelection = computed(
  () => props.lastChatResult?.replaceText && props.selectedSpanPinned != null
)

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function renderMarkdown(text: string): string {
  const escaped = escapeHtml(text)
  return escaped
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.+?)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br/>')
}

function scrollToBottom() {
  nextTick(() => {
    if (!messageListRef.value) return
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  })
}

function defaultMessages(): ChatMessage[] {
  return [{ role: 'assistant', text: DEFAULT_ASSISTANT_HINT, at: Date.now() }]
}

function historyStorageKey(conversationId?: string): string | null {
  const id = conversationId?.trim()
  return id ? `${CHAT_HISTORY_KEY_PREFIX}${id}` : null
}

function loadMessagesFromStorage(conversationId?: string): ChatMessage[] | null {
  const key = historyStorageKey(conversationId)
  if (!key) return null
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Array<{ role?: unknown; text?: unknown; at?: unknown }>
    if (!Array.isArray(parsed)) return null
    const restored = parsed
      .map((m) => {
        const role = m?.role === 'user' ? 'user' : (m?.role === 'assistant' ? 'assistant' : null)
        const text = typeof m?.text === 'string' ? m.text.trim() : ''
        const at = typeof m?.at === 'number' && Number.isFinite(m.at) ? m.at : Date.now()
        if (!role || !text) return null
        return { role, text, at } as ChatMessage
      })
      .filter((m): m is ChatMessage => Boolean(m))
    return restored.length ? restored : null
  } catch (_) {
    return null
  }
}

function saveMessagesToStorage(conversationId: string | undefined, list: ChatMessage[]) {
  const key = historyStorageKey(conversationId)
  if (!key) return
  try {
    localStorage.setItem(key, JSON.stringify(list))
  } catch (_) {}
}

function removeMessagesFromStorage(conversationId?: string) {
  const key = historyStorageKey(conversationId)
  if (!key) return
  try {
    localStorage.removeItem(key)
  } catch (_) {}
}

function restoreMessages(conversationId?: string) {
  messages.value = loadMessagesFromStorage(conversationId) ?? defaultMessages()
  lastAssistantPayload.value = [...messages.value].reverse().find((m) => m.role === 'assistant')?.text ?? ''
  scrollToBottom()
}

watch(
  () => props.lastChatResult?.displayText ?? '',
  (val) => {
    if (!val || val === lastAssistantPayload.value) return
    lastAssistantPayload.value = val
    messages.value.push({ role: 'assistant', text: val, at: Date.now() })
    scrollToBottom()
  }
)

watch(
  () => props.conversationId ?? '',
  (conversationId) => {
    restoreMessages(conversationId)
  },
  { immediate: true }
)

watch(
  messages,
  (list) => {
    saveMessagesToStorage(props.conversationId, list)
  },
  { deep: true }
)

watch(
  () => messages.value.length,
  () => {
    scrollToBottom()
  },
  { flush: 'post' }
)

function onInput(e: Event) {
  const value = (e.target as HTMLTextAreaElement).value
  emit('update:modelValue', value)
}

function onSend() {
  const text = props.modelValue.trim()
  if (!text) return
  messages.value.push({ role: 'user', text, at: Date.now() })
  emit('send')
  emit('update:modelValue', '')
  scrollToBottom()
}

function onSendOrStop() {
  if (props.isGenerating) {
    emit('stop')
    return
  }
  if (!canSend.value) return
  onSend()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    if (props.isGenerating) return
    if (!canSend.value) return
    onSend()
  }
}

function onReplaceSelection() {
  if (props.lastChatResult?.replaceText) {
    emit('replace-selection-with', props.lastChatResult.replaceText)
  }
}

function onClear() {
  removeMessagesFromStorage(props.conversationId)
  messages.value = [{ role: 'assistant', text: CHAT_CLEARED_HINT, at: Date.now() }]
  lastAssistantPayload.value = ''
  emit('update:modelValue', '')
  emit('cleared')
}

function clearSelectedText() {
  selectionStore?.clear()
}

function setComposerText(text: string) {
  emit('update:modelValue', text)
  focusComposer(text.length, text.length)
}

function focusComposer(start?: number, end?: number) {
  nextTick(() => {
    const el = composerInputRef.value
    if (!el) return
    el.focus()
    const cursorStart = start ?? el.value.length
    const cursorEnd = end ?? cursorStart
    el.setSelectionRange(cursorStart, cursorEnd)
  })
}

function getRecentMessages(max = 8): RecentMessageDto[] {
  const normalized = messages.value
    .filter((m) => {
      const t = m.text.trim()
      if (!t) return false
      if (m.role === 'assistant' && (t === DEFAULT_ASSISTANT_HINT || t === CHAT_CLEARED_HINT)) return false
      return true
    })
    .map((m) => ({ role: m.role, content: m.text.trim() as string }))
  if (max <= 0) return normalized
  return normalized.slice(-max)
}

defineExpose<{
  setComposerText: (text: string) => void
  focusComposer: () => void
  getRecentMessages: (max?: number) => RecentMessageDto[]
}>({
  setComposerText,
  focusComposer: () => focusComposer(),
  getRecentMessages,
})

onMounted(() => {
  restoreMessages(props.conversationId)
})
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: #f8fafc;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px var(--assistant-safe-padding-right, 16px) 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.chat-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.chat-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}
.mode-switch {
  display: flex;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  overflow: hidden;
}
.mode-btn {
  border: none;
  background: #fff;
  color: #4b5563;
  font-size: 12px;
  padding: 4px 8px;
  cursor: pointer;
}
.mode-btn.active {
  background: #047857;
  color: #fff;
}
.chat-actions {
  display: flex;
  gap: 8px;
}
.action-btn {
  border: 1px solid #d1d5db;
  background: #fff;
  color: #374151;
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 8px;
  cursor: pointer;
}
.action-btn.close {
  border-color: #e5e7eb;
}
.message-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow-y: scroll;
  padding: 14px var(--assistant-safe-padding-right, 16px) 14px 14px;
  background: #f8fafc;
}
.bubble-row {
  display: flex;
  width: 100%;
  margin-bottom: 10px;
}
.bubble-row.user {
  justify-content: flex-end;
}
.bubble-row.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: 88%;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  color: #111827;
  background: #fff;
  border: 1px solid #e5e7eb;
  word-break: break-word;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  box-sizing: border-box;
}
.bubble-row.assistant .bubble {
  width: 100%;
  max-width: 100%;
  align-self: stretch;
}
.bubble-row.user .bubble {
  background: #047857;
  color: #fff;
  border-color: #047857;
  max-width: 70%;
}
.composer {
  flex-shrink: 0;
  border-top: 1px solid #e5e7eb;
  background: #fff;
  padding: 10px var(--assistant-safe-padding-right, 16px) 12px 12px;
}
.selected-chip {
  border: 1px solid #d1d5db;
  background: #f9fafb;
  border-radius: 10px;
  padding: 8px 10px;
  margin-bottom: 8px;
}
.selected-chip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.selected-chip-title {
  font-size: 12px;
  font-weight: 700;
  color: #111827;
}
.selected-chip-close {
  border: none;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
  padding: 0;
}
.selected-chip-content {
  margin-top: 6px;
  font-size: 13px;
  color: #374151;
  line-height: 1.4;
  word-break: break-word;
}
.composer-input {
  width: 100%;
  min-height: 72px;
  max-height: 140px;
  resize: vertical;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.45;
  box-sizing: border-box;
}
.composer-input-wrap {
  position: relative;
}
.composer-input-wrap .composer-input {
  padding-right: 58px;
}
.composer-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.send-icon-btn,
.apply-btn {
  border: none;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.send-icon-btn {
  position: absolute;
  right: 10px;
  bottom: 10px;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #047857;
  color: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.12);
}
.send-icon-btn.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.send-icon-btn.generating {
  background: #111827;
}
.send-arrow {
  font-size: 18px;
  line-height: 1;
  transform: translateY(-1px);
}
.send-stop {
  width: 10px;
  height: 10px;
  background: currentColor;
  border-radius: 2px;
}
.apply-btn {
  background: #f3f4f6;
  color: #374151;
}
.apply-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 1100px) {
  .message-list {
    padding-right: var(--assistant-safe-padding-right, 16px);
  }
  .composer {
    padding-right: var(--assistant-safe-padding-right, 16px);
  }
}
</style>

