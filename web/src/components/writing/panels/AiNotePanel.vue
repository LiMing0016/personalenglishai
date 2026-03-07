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
      <div v-if="isExamMode" class="task-prompt-box">
        <button type="button" class="task-prompt-toggle" @click="toggleTaskPrompt">
          <span class="task-prompt-title">
            题目 / 要求
            <span v-if="auditStatus === 'confirmed'" class="audit-badge audit-ok">✓ 已审核</span>
            <span v-else-if="draftPrompt.trim() && auditStatus === 'idle'" class="audit-badge audit-pending">待审核</span>
          </span>
          <span class="toggle-arrow">{{ taskPromptExpanded ? '▲' : '▼' }}</span>
        </button>

        <template v-if="taskPromptExpanded">

          <!-- 已确认：显示摘要 -->
          <div v-if="auditStatus === 'confirmed'" class="confirmed-card">
            <div class="confirmed-chips">
              <span v-if="confirmedMeta.format" class="meta-chip chip-format">{{ confirmedMeta.format }}</span>
              <span v-if="confirmedMeta.wordCount" class="meta-chip chip-words">≥{{ confirmedMeta.wordCount }} 词</span>
            </div>
            <p class="confirmed-preview">{{ draftPromptPreview }}</p>
            <button type="button" class="btn-re-edit" @click="resetAudit">重新编辑</button>
          </div>

          <!-- 录入 / 审核中 -->
          <template v-else>
            <textarea
              v-model="draftPrompt"
              class="task-prompt-input"
              rows="3"
              placeholder='示例："请以新年为主题写一封给朋友的信，不少于120词。"'
              @input="onDraftChange"
            />

            <!-- 无效提示 -->
            <div v-if="auditStatus === 'invalid'" class="audit-msg audit-err">
              ❌ {{ auditError }}
            </div>

            <!-- 补全缺失信息 -->
            <div v-if="auditStatus === 'needs_info'" class="audit-fields">
              <template v-if="auditResult?.detectedFormat">
                <p class="field-ok">✅ 写作形式：{{ auditResult.detectedFormat }}</p>
              </template>
              <template v-else>
                <label class="field-label">⚠️ 未检测到写作形式，请选择：</label>
                <select v-model="extraFormat" class="field-select">
                  <option value="">请选择…</option>
                  <option value="应用文·书信/邮件">应用文·书信/邮件</option>
                  <option value="应用文·通知/公告">应用文·通知/公告</option>
                  <option value="应用文·演讲/发言">应用文·演讲/发言</option>
                  <option value="应用文·日记">应用文·日记</option>
                  <option value="议论文">议论文</option>
                  <option value="说明文/介绍文">说明文/介绍文</option>
                </select>
              </template>

              <template v-if="auditResult?.detectedWordCount">
                <p class="field-ok">✅ 字数要求：不少于 {{ auditResult.detectedWordCount }} 词</p>
              </template>
              <template v-else>
                <label class="field-label">⚠️ 未检测到字数要求（选填）：</label>
                <div class="field-row">
                  <span class="field-unit">不少于</span>
                  <input
                    v-model="extraWordCount"
                    type="number"
                    class="field-number"
                    placeholder="120"
                    min="50"
                    max="500"
                  />
                  <span class="field-unit">词</span>
                </div>
              </template>
            </div>

            <div class="audit-actions">
              <button
                v-if="auditStatus !== 'needs_info'"
                type="button"
                class="btn-audit"
                :disabled="!draftPrompt.trim()"
                @click="runAudit"
              >🔍 审核题目</button>
              <template v-if="auditStatus === 'needs_info'">
                <button
                  type="button"
                  class="btn-confirm"
                  :disabled="!canConfirm"
                  @click="confirmPrompt"
                >✓ 确认题目</button>
                <button type="button" class="btn-re-edit-sm" @click="resetAudit">重新编辑</button>
              </template>
            </div>
          </template>

        </template>
      </div>
      <button
        type="button"
        class="draft-toggle"
        :class="{ active: includeDraft }"
        @click="includeDraft = !includeDraft"
      >
        <span class="draft-toggle-dot">{{ includeDraft ? '\u25CF' : '\u25CB' }}</span>
        <span class="draft-toggle-label">{{ includeDraft ? '\u5F15\u7528\u4F5C\u6587' : '\u4E0D\u5F15\u7528\u4F5C\u6587' }}</span>
      </button>
      <div class="composer-input-wrap">
        <div v-if="selectedText" class="composer-selected-text">
          <span class="composer-selected-label">Selected text</span>
          <SelectedTextChip
            :text="selectedText"
            :max-chars="68"
            @dismiss="clearSelectedText"
          />
        </div>
        <div ref="modeMenuRef" class="mode-plus-wrap">
          <button
            type="button"
            class="mode-plus-btn"
            title="写作模式"
            @click.stop="toggleModeMenu"
          >
            +
          </button>
          <div v-if="modeMenuOpen" class="mode-menu" @click.stop>
            <button
              type="button"
              class="mode-menu-item"
              :class="{ active: writingMode === 'free' }"
              @click="selectWritingMode('free')"
            >
              自由写作
            </button>
            <button
              type="button"
              class="mode-menu-item"
              :class="{ active: writingMode === 'exam' }"
              @click="selectWritingMode('exam')"
            >
              考试写作
            </button>
          </div>
        </div>
        <textarea
          ref="composerInputRef"
          :value="modelValue"
          class="composer-input"
          :class="{ 'composer-input--with-selection': !!selectedText }"
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
import { onClickOutside } from '@vueuse/core'
import { writingSelectionStoreKey } from '../useWritingSelectionStore'
import SelectedTextChip from './SelectedTextChip.vue'

type Mode = 'sm' | 'md' | 'lg'
type WritingMode = 'free' | 'exam'
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
    writingMode?: WritingMode
    taskPrompt?: string
  }>(),
  {
    selectedTextPinned: '',
    selectionDismissed: false,
    isGenerating: false,
    writingMode: 'free',
    taskPrompt: '',
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
  'update:writingMode': [value: WritingMode]
  'update:taskPrompt': [value: string]
}>()

const modes: Mode[] = ['sm', 'md', 'lg']
const mode = ref<Mode>('md')
const messageListRef = ref<HTMLElement | null>(null)
const composerInputRef = ref<HTMLTextAreaElement | null>(null)
const modeMenuRef = ref<HTMLElement | null>(null)
const modeMenuOpen = ref(false)
const includeDraft = ref(false)
const taskPromptExpanded = ref(false)
const messages = ref<ChatMessage[]>([
  { role: 'assistant', text: DEFAULT_ASSISTANT_HINT, at: Date.now() },
])
const lastAssistantPayload = ref('')
const selectionStore = inject(writingSelectionStoreKey, null)
const selectedText = computed(() => selectionStore?.selectedText.value ?? '')
const writingMode = computed<WritingMode>(() => (props.writingMode === 'exam' ? 'exam' : 'free'))
const isExamMode = computed(() => writingMode.value === 'exam')

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

// ── Task-prompt audit ──────────────────────────────────────────────

type AuditStatus = 'idle' | 'invalid' | 'needs_info' | 'confirmed'

const draftPrompt = ref(props.taskPrompt ?? '')
const auditStatus = ref<AuditStatus>(props.taskPrompt ? 'confirmed' : 'idle')
const auditError = ref('')
const auditResult = ref<{ detectedFormat?: string; detectedWordCount?: number } | null>(null)
const extraFormat = ref('')
const extraWordCount = ref('')
const confirmedMeta = ref<{ format?: string; wordCount?: number }>({})

const draftPromptPreview = computed(() => {
  const t = draftPrompt.value.trim()
  return t.length > 60 ? t.slice(0, 60) + '…' : t
})

const canConfirm = computed(() =>
  !!(auditResult.value?.detectedFormat || extraFormat.value.trim())
)

watch(
  () => props.taskPrompt,
  (val) => {
    if (!val) {
      draftPrompt.value = ''
      auditStatus.value = 'idle'
      auditResult.value = null
    }
  }
)

const WRITING_FORMATS = [
  { label: '应用文·书信/邮件', pattern: /信|letter|email|邮件|写信/i },
  { label: '应用文·通知/公告', pattern: /通知|公告|notice|announcement/i },
  { label: '应用文·演讲/发言', pattern: /演讲|发言|speech/i },
  { label: '应用文·日记', pattern: /日记|diary/i },
  { label: '议论文', pattern: /议论|观点|看法|opinion|discuss|argue/i },
  { label: '说明文/介绍文', pattern: /介绍|描述|explain|describe|introduce/i },
]

function onDraftChange() {
  if (auditStatus.value === 'invalid' || auditStatus.value === 'needs_info') {
    auditStatus.value = 'idle'
    auditResult.value = null
    auditError.value = ''
  }
}

function runAudit() {
  const text = draftPrompt.value.trim()
  if (!text) return

  // 乱码检测：有效字符（字母+汉字）占比
  const validChars = (text.match(/[a-zA-Z\u4e00-\u9fa5]/g) || []).length
  const ratio = validChars / text.length

  if (text.length < 8 || ratio < 0.35) {
    auditStatus.value = 'invalid'
    auditError.value = '题目内容看起来不是有效的写作要求，请用中文或英文描述考试题目。'
    return
  }

  let detectedFormat: string | undefined
  for (const { label, pattern } of WRITING_FORMATS) {
    if (pattern.test(text)) { detectedFormat = label; break }
  }

  const wcMatch = text.match(/(\d+)\s*(词|字|words?)/i)
  const detectedWordCount = wcMatch ? parseInt(wcMatch[1]) : undefined

  auditResult.value = { detectedFormat, detectedWordCount }
  extraFormat.value = ''
  extraWordCount.value = ''
  auditStatus.value = 'needs_info'
}

function confirmPrompt() {
  if (!auditResult.value) return
  const format = auditResult.value.detectedFormat || extraFormat.value.trim()
  const wordCount = auditResult.value.detectedWordCount
    || (extraWordCount.value ? parseInt(extraWordCount.value) : undefined)

  confirmedMeta.value = { format, wordCount }

  const lines: string[] = []
  if (format) lines.push(`【写作形式】${format}`)
  lines.push(`【写作要求】${draftPrompt.value.trim()}`)
  if (wordCount) lines.push(`【字数要求】不少于 ${wordCount} 词`)

  auditStatus.value = 'confirmed'
  emit('update:taskPrompt', lines.join('\n'))
}

function resetAudit() {
  auditStatus.value = 'idle'
  auditResult.value = null
  auditError.value = ''
  extraFormat.value = ''
  extraWordCount.value = ''
  emit('update:taskPrompt', '')
}

function toggleModeMenu() {
  modeMenuOpen.value = !modeMenuOpen.value
}

function selectWritingMode(nextMode: WritingMode) {
  emit('update:writingMode', nextMode)
  if (nextMode === 'exam') {
    taskPromptExpanded.value = true
  }
  modeMenuOpen.value = false
}

function toggleTaskPrompt() {
  taskPromptExpanded.value = !taskPromptExpanded.value
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

function isIncludeDraft(): boolean {
  return includeDraft.value
}

defineExpose<{
  setComposerText: (text: string) => void
  focusComposer: () => void
  getRecentMessages: (max?: number) => RecentMessageDto[]
  isIncludeDraft: () => boolean
}>({
  setComposerText,
  focusComposer: () => focusComposer(),
  getRecentMessages,
  isIncludeDraft,
})

onClickOutside(modeMenuRef, () => {
  modeMenuOpen.value = false
})

onMounted(() => {
  restoreMessages(props.conversationId)
  taskPromptExpanded.value = props.writingMode === 'exam'
})

watch(
  () => props.writingMode,
  (nextMode) => {
    if (nextMode === 'exam') {
      taskPromptExpanded.value = true
    }
  }
)

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
.draft-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid #d1d5db;
  background: #f9fafb;
  border-radius: 999px;
  padding: 3px 10px 3px 7px;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  margin-bottom: 8px;
  transition: all 0.15s ease;
}
.draft-toggle:hover {
  border-color: #9ca3af;
}
.draft-toggle.active {
  background: #ecfdf5;
  border-color: #6ee7b7;
  color: #047857;
}
.draft-toggle-dot {
  font-size: 10px;
  line-height: 1;
}
.draft-toggle-label {
  line-height: 1;
}
.composer-selected-text {
  position: absolute;
  top: 8px;
  left: 52px;
  right: 58px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.composer-selected-label {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
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
  padding-left: 52px;
  padding-right: 58px;
}
.composer-input--with-selection {
  padding-top: 42px;
}
.mode-plus-wrap {
  position: absolute;
  left: 10px;
  bottom: 10px;
  z-index: 2;
}
.mode-plus-btn {
  width: 32px;
  height: 32px;
  border: 1px solid #d1d5db;
  border-radius: 999px;
  background: #fff;
  color: #4b5563;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}
.mode-menu {
  position: absolute;
  left: 0;
  bottom: 38px;
  width: 136px;
  padding: 4px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12);
}
.mode-menu-item {
  width: 100%;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #111827;
  font-size: 13px;
  text-align: left;
  padding: 8px 10px;
  cursor: pointer;
}
.mode-menu-item:hover {
  background: #f3f4f6;
}
.mode-menu-item.active {
  background: #ecfdf5;
  color: #047857;
  font-weight: 600;
}
.task-prompt-box {
  border: 1px solid #d1d5db;
  background: #f9fafb;
  border-radius: 10px;
  padding: 8px 10px;
  margin-bottom: 8px;
}
.task-prompt-toggle {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #374151;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}
.task-prompt-title {
  display: flex;
  align-items: center;
  gap: 6px;
}
.toggle-arrow {
  font-size: 10px;
  color: #9ca3af;
}
.audit-badge {
  display: inline-block;
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 10px;
}
.audit-ok   { background: #d1fae5; color: #065f46; }
.audit-pending { background: #fef9c3; color: #92400e; }

/* Confirmed card */
.confirmed-card {
  margin-top: 8px;
  padding: 8px 10px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
}
.confirmed-chips {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}
.meta-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}
.chip-format { background: #dbeafe; color: #1e40af; }
.chip-words  { background: #ede9fe; color: #5b21b6; }
.confirmed-preview {
  margin: 4px 0 8px;
  font-size: 12px;
  color: #374151;
  line-height: 1.5;
}
.btn-re-edit {
  font-size: 12px;
  color: #047857;
  background: none;
  border: 1px solid #047857;
  border-radius: 6px;
  padding: 3px 10px;
  cursor: pointer;
}
.btn-re-edit:hover { background: #ecfdf5; }

/* Textarea */
.task-prompt-input {
  width: 100%;
  margin-top: 8px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.4;
  color: #111827;
  background: #fff;
  resize: vertical;
  box-sizing: border-box;
}
.task-prompt-input::placeholder { color: #9ca3af; }

/* Error / needs_info messages */
.audit-msg {
  margin-top: 6px;
  font-size: 12px;
  padding: 6px 10px;
  border-radius: 6px;
}
.audit-err { background: #fee2e2; color: #991b1b; }

/* Missing fields */
.audit-fields {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field-ok {
  margin: 0;
  font-size: 12px;
  color: #065f46;
}
.field-label {
  font-size: 12px;
  color: #92400e;
  display: block;
}
.field-select {
  margin-top: 4px;
  width: 100%;
  padding: 6px 8px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
}
.field-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}
.field-unit { font-size: 12px; color: #6b7280; }
.field-number {
  width: 70px;
  padding: 5px 8px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  text-align: center;
}

/* Action buttons */
.audit-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}
.btn-audit {
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #374151;
  background: #f3f4f6;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  cursor: pointer;
}
.btn-audit:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-audit:not(:disabled):hover { background: #e5e7eb; }
.btn-confirm {
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #047857;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
.btn-confirm:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-confirm:not(:disabled):hover { background: #065f46; }
.btn-re-edit-sm {
  padding: 5px 10px;
  font-size: 12px;
  color: #6b7280;
  background: none;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  cursor: pointer;
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
