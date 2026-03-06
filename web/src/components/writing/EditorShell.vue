<template>
  <div class="writing-root">
    <ToolRail class="toolrail-fixed-overlay" :active-panel="activePanel" @select="onToolSelect" />
    <div class="workspace-layout" :style="layoutStyle">
      <div ref="leftPaneRef" class="left-pane">
        <DocEditor
          v-model:draft-text="draftText"
          :correction-mode="false"
          :submitting="submitting"
          :writing-mode="writingMode"
          :cursor-placement="cursorPlacement"
          :selection-capture-enabled="assistantOpen"
          :errors="displayEditorErrors"
          :active-error-id="activeErrorId"
          :highlight-range="sentenceHighlightRange"
          @submit="onSubmit"
          @clear="onClear"
          @selection-change="onSelectionChange"
          @cursor-placed="cursorPlacement = null"
          @error-click="onEditorErrorClick"
          @fix-error="onInlineFixError"
          @dismiss-error="onDismissError"
        />
      </div>

      <Splitter
        v-if="activePanel !== null"
        class="panel-splitter"
        :min-right="MIN_PANEL_WIDTH"
        :max-right="MAX_PANEL_WIDTH"
        :min-editor="MIN_LEFT_WIDTH"
        @update:width="onDockWidthChange"
        @drag-start="resizing = true"
        @drag-end="onDragEnd"
      />

      <div class="assistant-pane" :class="{ collapsed: activePanel === null }">
        <RightPanel
          ref="rightPanelRef"
          v-if="activePanel !== null"
          :panel="activePanel"
          :title="panelTitle"
          :width="dockWidth"
          :essay="draftText"
          :selection-state="selectionState"
          :selection-dismissed="selectionDismissed"
          :selected-text-pinned="selectedTextPinned"
          :selected-span-pinned="selectedSpanPinned"
          :last-chat-result="lastChatResult"
          :conversation-id="aiConversationId"
          :ai-generating="aiGenerating"
          :writing-mode="writingMode"
          :task-prompt="taskPrompt"
          :ai-note="aiNote"
          :evaluate-result="evaluateResult"
          :active-error-id="activeErrorId"
          :submitting="submitting"
          :evaluate-error="evaluateError"
          :grammar-errors="grammarPanelErrors"
          :grammar-checking="grammarChecking"
          :grammar-check-error="grammarCheckError"
          :grammar-fixed-error-ids="grammarPanelFixedIds"
          :rewrite-suggestions="rewritePanelSuggestions"
          @close="activePanel = null"
          @error-click="onPanelErrorClick"
          @apply-polish="onApplyPolish"
          @replace-sentence="onReplaceSentence"
          @sentence-focus="sentenceHighlightRange = $event"
          @start-polish="onStartPolish"
          @grammar-fix-error="onGrammarFixError"
          @grammar-fix-all="onGrammarFixAll"
          @apply-suggestion="onApplySuggestion"
          @gpt-errors-loaded="onGptErrorsLoaded"
          @gpt-suggestions-loaded="onGptSuggestionsLoaded"
          @retry="onSubmit"
          @start-grammar-check="onStartGrammarCheck"
          @dismiss-selection="onDismissSelection"
          @replace-selection-with="onReplaceSelectionWith"
          @archived="onArchived"
          @load-history-result="onLoadHistoryResult"
          @update:ai-note="aiNote = $event"
          @update:writing-mode="writingMode = $event"
          @update:task-prompt="taskPrompt = $event"
          @ai-note-send="onAiNoteSend"
          @ai-note-stop="onAiNoteStop"
          @ai-chat-cleared="onAiChatCleared"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick, provide } from 'vue'
import type { PanelMode } from './ToolRail.vue'

const props = withDefaults(defineProps<{
  initialWritingMode?: 'free' | 'exam'
  studyStage?: string | null
}>(), {
  initialWritingMode: undefined,
  studyStage: null,
})
import DocEditor from './DocEditor.vue'
import RightPanel from './RightPanel.vue'
import ToolRail from './ToolRail.vue'
import Splitter from './Splitter.vue'
import { getEvaluateTask, submitEvaluateWriting, grammarCheck as grammarCheckApi } from '@/api/writing'
import type { WritingEvaluateResponse, WritingEvaluateTaskStatusResponse, EvaluationDetailResponse, SuggestionErrorItem, SuggestionItem } from '@/api/writing'
import { aiCommand } from '@/api/ai'
import { createDocument } from '@/api/document'
import { showToast } from '@/utils/toast'
import { createWritingSelectionStore, writingSelectionStoreKey } from './useWritingSelectionStore'

const LAYOUT_KEY = 'peai:writing:layout'
const SCROLL_KEY = 'peai:writing:scrollTop'
const DRAFT_KEY = 'peai:writing:draft'
const LEGACY_DRAFT_KEY = 'peai:draft:writing'
const AI_NOTE_DRAFT_KEY = 'peai:writing:aiNoteDraft'
const AI_CHAT_CONVERSATION_ID_KEY = 'peai:writing:aiConversationId'
const WRITING_MODE_KEY = 'peai:writing:mode'
const TASK_PROMPT_KEY = 'peai:writing:taskPrompt'
const EVALUATE_RESULT_KEY = 'peai:writing:evaluateResult'
const SPLIT_RATIO_KEY = 'writing.split.ratio'
const DEFAULT_SPLIT_RATIO = 0.3
const MIN_PANEL_WIDTH = 420
const MAX_PANEL_WIDTH = 1280
const MIN_LEFT_WIDTH = 360

const VALID_PANELS: PanelMode[] = [
  'score', 'rewrite', 'grammarCheck', 'improve', 'explain', 'translate', 'archive', 'aiNote',
]

interface LayoutState {
  rightPanelOpen: boolean
  activePanel: PanelMode | null
}

function loadLayout(): LayoutState {
  try {
    const s = localStorage.getItem(LAYOUT_KEY)
    if (s) {
      const data = JSON.parse(s) as Record<string, unknown>
      const rightPanelOpen = Boolean(data.rightPanelOpen)
      const panel = data.activePanel
      const activePanel =
        typeof panel === 'string' && VALID_PANELS.includes(panel as PanelMode) ? (panel as PanelMode) : null
      return {
        rightPanelOpen: rightPanelOpen && activePanel != null,
        activePanel: rightPanelOpen ? activePanel : null,
      }
    }
  } catch (_) {}
  return { rightPanelOpen: false, activePanel: null }
}

function saveLayout(state: { rightPanelOpen: boolean; activePanel: PanelMode | null }) {
  try {
    localStorage.setItem(LAYOUT_KEY, JSON.stringify(state))
  } catch (_) {}
}

function clampRatio(r: number): number {
  if (!Number.isFinite(r)) return DEFAULT_SPLIT_RATIO
  return Math.min(0.82, Math.max(0.22, r))
}

function createConversationId(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID()
    }
  } catch (_) {}
  return `conv_${Date.now()}_${Math.random().toString(16).slice(2)}`
}

function loadConversationId(): string {
  try {
    const saved = localStorage.getItem(AI_CHAT_CONVERSATION_ID_KEY)?.trim()
    if (saved) return saved
  } catch (_) {}
  return createConversationId()
}

function loadWritingMode(): 'free' | 'exam' {
  try {
    const saved = localStorage.getItem(WRITING_MODE_KEY)?.trim()
    return saved === 'exam' ? 'exam' : 'free'
  } catch (_) {
    return 'free'
  }
}

function loadTaskPrompt(): string {
  try {
    return localStorage.getItem(TASK_PROMPT_KEY) ?? ''
  } catch (_) {
    return ''
  }
}

function computePanelWidthByRatio(ratio: number, viewportWidth = window.innerWidth): number {
  const clampedRatio = clampRatio(ratio)
  const maxByEditor = Math.max(0, viewportWidth - MIN_LEFT_WIDTH)
  const maxPanelByViewport = Math.min(MAX_PANEL_WIDTH, maxByEditor)
  if (maxPanelByViewport <= 0) return 0
  const minPanelByViewport = Math.min(MIN_PANEL_WIDTH, maxPanelByViewport)
  const preferred = Math.round(viewportWidth * clampedRatio)
  return Math.max(minPanelByViewport, Math.min(preferred, maxPanelByViewport))
}

function saveSplitRatio(ratio: number) {
  try {
    localStorage.setItem(SPLIT_RATIO_KEY, String(clampRatio(ratio)))
  } catch (_) {}
}

function loadSplitRatio(): number {
  try {
    const raw = localStorage.getItem(SPLIT_RATIO_KEY)
    if (!raw) return DEFAULT_SPLIT_RATIO
    return clampRatio(Number(raw))
  } catch (_) {
    return DEFAULT_SPLIT_RATIO
  }
}

// ── 评分结果持久化 ──
function saveEvaluateResult(result: WritingEvaluateResponse | null) {
  try {
    if (result) {
      localStorage.setItem(EVALUATE_RESULT_KEY, JSON.stringify(result))
    } else {
      localStorage.removeItem(EVALUATE_RESULT_KEY)
    }
  } catch (_) {}
}

function loadEvaluateResult(): WritingEvaluateResponse | null {
  try {
    const raw = localStorage.getItem(EVALUATE_RESULT_KEY)
    if (!raw) return null
    return JSON.parse(raw) as WritingEvaluateResponse
  } catch (_) {
    return null
  }
}

// 立即保存草稿到 localStorage
function saveDraftNow(text: string) {
  try {
    const payload = { text, updatedAt: Date.now() }
    localStorage.setItem(DRAFT_KEY, JSON.stringify(payload))
    console.log('[draft] saved', { len: text.length, head: text.slice(0, 30) })
  } catch (e) {
    console.error('[draft] save failed', e)
  }
}

// 从 localStorage 读取草稿
function loadDraftNow(): string | null {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    console.log('[draft] load raw', raw)
    if (!raw) return null
    const obj = JSON.parse(raw)
    return typeof obj?.text === 'string' ? obj.text : null
  } catch (e) {
    console.error('[draft] load failed', e)
    return null
  }
}

// 初始化加载草稿（兼容旧存储格式并迁移）
function loadDraft(): string {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (raw) {
      const trimmed = raw.trim()
      if (trimmed.startsWith('{')) {
        try {
          const obj = JSON.parse(trimmed) as { text?: unknown }
          if (typeof obj.text === 'string') return obj.text
        } catch {
          // fall through and treat as legacy
        }
      } else {
        // 旧格式：纯文本，迁移为 JSON
        const text = raw
        localStorage.setItem(DRAFT_KEY, JSON.stringify({ text, updatedAt: Date.now() }))
        return text
      }
    }

    // 若新 key 为空，尝试从旧 key 迁移
    const legacy = localStorage.getItem(LEGACY_DRAFT_KEY)
    if (legacy) {
      let text = legacy
      const trimmedLegacy = legacy.trim()
      if (trimmedLegacy.startsWith('{')) {
        try {
          const obj = JSON.parse(trimmedLegacy) as { text?: unknown }
          if (typeof obj.text === 'string') text = obj.text
        } catch {
          // ignore, 使用原始文本
        }
      }
      // 迁移到新 key，并删除旧 key
      localStorage.setItem(DRAFT_KEY, JSON.stringify({ text, updatedAt: Date.now() }))
      localStorage.removeItem(LEGACY_DRAFT_KEY)
      return text
    }
  } catch {
    // ignore
  }
  return ''
}

type RecentMessageDto = { role: 'user' | 'assistant'; content: string }

const leftPaneRef = ref<HTMLElement | null>(null)
const rightPanelRef = ref<{
  focusAiComposer: () => boolean
  getAiRecentMessages?: (max?: number) => RecentMessageDto[]
  isIncludeDraft?: () => boolean
} | null>(null)
const activePanel = ref<PanelMode | null>(null)
const splitRatio = ref(DEFAULT_SPLIT_RATIO)
const dockWidth = ref(480)
const draftText = ref(loadDraft())
const selectionState = ref<{ text: string; start: number; end: number } | null>(null)
const selectionDismissed = ref(false)
const selectedTextPinned = ref('')
const selectedSpanPinned = ref<{ start: number; end: number } | null>(null)
const lastDismissedPinned = ref('')
const lastChatResult = ref<{ displayText: string; replaceText?: string } | null>(null)
const aiDocId = ref('')
const cursorPlacement = ref<{ at: number } | null>(null)
const aiNote = ref('')
const aiConversationId = ref(loadConversationId())
const aiGenerating = ref(false)
const writingMode = ref<'free' | 'exam'>(props.initialWritingMode ?? loadWritingMode())
const taskPrompt = ref(loadTaskPrompt())
let aiAbortController: AbortController | null = null
const submitting = ref(false)
const evaluateError = ref<string | null>(null)
let evaluatePollToken = 0
const resizing = ref(false)
const archivedList = ref<unknown[]>([])
const evaluateResult = ref<WritingEvaluateResponse | null>(null)
const activeErrorId = ref<string | null>(null)
const sentenceHighlightRange = ref<{ start: number; end: number } | null>(null)
const evaluatedText = ref<string | null>(null)

type CorrectionError = WritingEvaluateResponse['errors'][number]

// ── 实时语法检查状态 ──
const grammarErrors = ref<CorrectionError[]>([])
const grammarCheckActive = ref(true)
const grammarChecking = ref(false)
const grammarCheckError = ref<string | null>(null)
const grammarFixedErrorIds = ref<Set<string>>(new Set())
const grammarReChecked = ref(false)  // 评分后重检完成，切换到实时结果
const gptErrors = ref<CorrectionError[]>([])  // GPT 复检的硬性错误（转换为 CorrectionError 格式）
const gptSuggestionErrors = ref<CorrectionError[]>([])  // GPT 软性建议（转换为 CorrectionError 格式，用于编辑器下划线）
let grammarCheckAbortController: AbortController | null = null
let grammarCheckTimer: ReturnType<typeof setTimeout> | null = null

// 语法面板：重检完成显示实时结果，否则显示评分错误（若有），最后回落到实时结果
const grammarPanelErrors = computed(() => {
  if (grammarReChecked.value && grammarErrors.value.length > 0) {
    return grammarErrors.value
  }
  if (!grammarReChecked.value && evaluateResult.value?.errors?.length) {
    return evaluateResult.value.errors.filter((e) => e.category !== 'suggestion')
  }
  return grammarErrors.value
})
const grammarPanelFixedIds = computed(() => grammarFixedErrorIds.value)

const selectionStore = createWritingSelectionStore()

provide(writingSelectionStoreKey, selectionStore)

// 编辑器错误显示：重检结果 > 评分错误 > 实时语法，+ GPT 复检错误
const displayEditorErrors = computed(() => {
  let base: CorrectionError[] | undefined
  // 1. 重检完成 → 显示实时结果
  if (grammarReChecked.value && grammarErrors.value.length > 0) {
    const fixed = grammarFixedErrorIds.value
    base = grammarErrors.value.filter((e) => !fixed.has(e.id))
  }
  // 2. 评分结果存在（未重检）→ 显示评分错误
  else if (!grammarReChecked.value && evaluateResult.value?.errors?.length) {
    base = evaluateResult.value.errors
  }
  // 3. 实时语法检查 → 显示语法检查错误
  else if (grammarErrors.value.length > 0) {
    const fixed = grammarFixedErrorIds.value
    base = grammarErrors.value.filter((e) => !fixed.has(e.id))
  }
  // 4. 合并 GPT 复检错误 + GPT 软性建议下划线
  const extras = [...gptErrors.value, ...gptSuggestionErrors.value]
  if (extras.length > 0) {
    return [...(base ?? []), ...extras]
  }
  return base
})

const rewritePanelSuggestions = computed(() => {
  const fromEvaluate = (evaluateResult.value?.errors ?? []).filter((e) => e.category === 'suggestion')
  if (gptSuggestionErrors.value.length === 0) return fromEvaluate

  const merged = [...fromEvaluate]
  const seen = new Set(merged.map((e) => e.id))
  for (const s of gptSuggestionErrors.value) {
    if (seen.has(s.id)) continue
    merged.push(s)
    seen.add(s.id)
  }
  return merged
})

const assistantOpen = computed(() => activePanel.value === 'aiNote')
const layoutStyle = computed(() => ({
  '--rightWidth': activePanel.value !== null ? `${dockWidth.value}px` : '0px',
  '--splitter-width': activePanel.value !== null ? '8px' : '0px',
}))

function onSelectionChange(payload: { text: string; start: number; end: number } | null) {
  selectionState.value = payload
}

function isNodeInsideLeftPane(node: Node | null, root: HTMLElement): boolean {
  if (!node) return false
  const element = node.nodeType === Node.TEXT_NODE ? node.parentElement : (node as Element)
  return !!element && root.contains(element)
}

function getLeftPaneSelectionText(): string {
  const root = leftPaneRef.value
  if (!root) return ''
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) return ''
  if (!isNodeInsideLeftPane(selection.anchorNode, root)) return ''
  if (!isNodeInsideLeftPane(selection.focusNode, root)) return ''
  const rawText = selection.toString().trim()
  return rawText
}

function syncSelectionStoreFromLeftMouseup() {
  const text = getLeftPaneSelectionText()
  if (!text) {
    selectionStore.clear()
    return
  }
  selectionStore.setSelectedText(text)
  nextTick(() => {
    rightPanelRef.value?.focusAiComposer()
  })
}

const panelTitle = computed(() => {
  const t: Record<PanelMode, string> = {
    score: '作文评价',
    grammarCheck: '语法检查',
    rewrite: 'AI 改写',
    improve: '提升',
    explain: '解释',
    translate: '翻译',
    archive: '归档',
    aiNote: 'AI 助手',
  }
  return activePanel.value != null ? t[activePanel.value] : ''
})

watch(selectionState, (cur) => {
  const curText = cur?.text?.trim() ?? ''
  if (curText) {
    if (selectionDismissed.value && curText === lastDismissedPinned.value) return
    selectedTextPinned.value = cur!.text
    selectedSpanPinned.value = { start: cur!.start, end: cur!.end }
    selectionDismissed.value = false
  }
})

// 草稿保存：基于 draftText，写入 JSON { text, updatedAt }
let draftSaveTimeout: ReturnType<typeof setTimeout> | null = null
watch(
  () => draftText.value,
  (v) => {
    console.log('[draft] watch fired', { len: (v ?? '').length, head: (v ?? '').slice(0, 30) })
    if (draftSaveTimeout !== null) clearTimeout(draftSaveTimeout)
    draftSaveTimeout = setTimeout(() => {
      saveDraftNow(v ?? '')
      draftSaveTimeout = null
    }, 500)
  },
  { immediate: false }
)

// AI 助手输入框保存：基于 aiNote，写入 JSON { text, updatedAt }
function saveAiNoteDraftNow(text: string) {
  try {
    const payload = { text, updatedAt: Date.now() }
    localStorage.setItem(AI_NOTE_DRAFT_KEY, JSON.stringify(payload))
    console.log('[aiNoteDraft] saved', { len: text.length, head: text.slice(0, 30) })
  } catch (e) {
    console.error('[aiNoteDraft] save failed', e)
  }
}

function loadAiNoteDraftNow(): string | null {
  try {
    const raw = localStorage.getItem(AI_NOTE_DRAFT_KEY)
    console.log('[aiNoteDraft] load', raw)
    if (!raw) return null
    const obj = JSON.parse(raw)
    return typeof obj?.text === 'string' ? obj.text : null
  } catch (e) {
    console.error('[aiNoteDraft] load failed', e)
    return null
  }
}

let aiNoteSaveTimeout: ReturnType<typeof setTimeout> | null = null
watch(
  () => aiNote.value,
  (v) => {
    console.log('[aiNoteDraft] watch fired', { len: (v ?? '').length, head: (v ?? '').slice(0, 30) })
    if (aiNoteSaveTimeout !== null) clearTimeout(aiNoteSaveTimeout)
    aiNoteSaveTimeout = setTimeout(() => {
      saveAiNoteDraftNow(v ?? '')
      aiNoteSaveTimeout = null
    }, 400)
  },
  { immediate: false }
)

let scrollSaveTimeout: ReturnType<typeof setTimeout> | null = null
function onLeftPaneScroll() {
  if (scrollSaveTimeout) return
  scrollSaveTimeout = setTimeout(() => {
    try {
      if (leftPaneRef.value) localStorage.setItem(SCROLL_KEY, String(leftPaneRef.value.scrollTop))
    } catch (_) {}
    scrollSaveTimeout = null
  }, 200)
}

function saveLayoutState() {
  saveLayout({
    rightPanelOpen: activePanel.value !== null,
    activePanel: activePanel.value,
  })
}

onMounted(async () => {
  const layout = loadLayout()
  activePanel.value = layout.activePanel
  splitRatio.value = loadSplitRatio()
  dockWidth.value = computePanelWidthByRatio(splitRatio.value)
  
  // 恢复草稿（仅在当前为空时）
  const saved = loadDraftNow()
  console.log('[draft] onMounted', { savedLen: saved?.length ?? 0 })
  if (saved && (!draftText.value || draftText.value.trim() === '')) {
    await nextTick()
    draftText.value = saved
    console.log('[draft] restored', { len: saved.length })
  }
  
  // 恢复评分结果
  const savedResult = loadEvaluateResult()
  if (savedResult && !evaluateResult.value) {
    evaluateResult.value = savedResult
    evaluatedText.value = draftText.value
    if (layout.activePanel === 'score') {
      activePanel.value = 'score'
    }
  }

  // 恢复 AI 助手输入框（仅在当前为空时）
  console.log('[aiNoteDraft] before', aiNote.value)
  const savedAiNote = loadAiNoteDraftNow()
  if (savedAiNote && (!aiNote.value || aiNote.value.trim() === '')) {
    await nextTick()
    aiNote.value = savedAiNote
    console.log('[aiNoteDraft] after', aiNote.value)
    console.log('[aiNoteDraft] restored', { len: savedAiNote.length })
  }
  
  nextTick(() => {
    try {
      const s = localStorage.getItem(SCROLL_KEY)
      if (s != null && leftPaneRef.value) {
        const top = Number(s)
        if (Number.isFinite(top)) leftPaneRef.value.scrollTop = top
      }
    } catch (_) {}
    leftPaneRef.value?.addEventListener('scroll', onLeftPaneScroll)
  })

  leftPaneRef.value?.addEventListener('mouseup', syncSelectionStoreFromLeftMouseup)

  const onResize = () => {
    dockWidth.value = computePanelWidthByRatio(splitRatio.value)
  }
  window.addEventListener('resize', onResize)
  ;(window as Window & { __writingResizeHandler?: () => void }).__writingResizeHandler = onResize
})

onBeforeUnmount(() => {
  evaluatePollToken += 1
  leftPaneRef.value?.removeEventListener('scroll', onLeftPaneScroll)
  leftPaneRef.value?.removeEventListener('mouseup', syncSelectionStoreFromLeftMouseup)
  if (scrollSaveTimeout) clearTimeout(scrollSaveTimeout)
  if (draftSaveTimeout) clearTimeout(draftSaveTimeout)
  if (aiNoteSaveTimeout) clearTimeout(aiNoteSaveTimeout)
  grammarCheckAbortController?.abort()
  if (grammarCheckTimer) clearTimeout(grammarCheckTimer)
  const handler = (window as Window & { __writingResizeHandler?: () => void }).__writingResizeHandler
  if (handler) {
    window.removeEventListener('resize', handler)
    delete (window as Window & { __writingResizeHandler?: () => void }).__writingResizeHandler
  }
})

watch(activePanel, (newPanel, oldPanel) => {
  if (oldPanel === 'rewrite' && newPanel !== 'rewrite') {
    sentenceHighlightRange.value = null
  }
  saveLayoutState()
}, { flush: 'post' })

watch(
  writingMode,
  (val) => {
    try {
      localStorage.setItem(WRITING_MODE_KEY, val)
    } catch (_) {}
  },
  { immediate: true }
)

watch(
  taskPrompt,
  (val) => {
    try {
      localStorage.setItem(TASK_PROMPT_KEY, val)
    } catch (_) {}
  },
  { immediate: true }
)

function onDockWidthChange(width: number) {
  const viewportWidth = window.innerWidth
  const maxByEditor = Math.max(0, viewportWidth - MIN_LEFT_WIDTH)
  const maxPanelByViewport = Math.min(MAX_PANEL_WIDTH, maxByEditor)
  const minPanelByViewport = Math.min(MIN_PANEL_WIDTH, maxPanelByViewport)
  dockWidth.value = Math.max(minPanelByViewport, Math.min(width, maxPanelByViewport))
  splitRatio.value = clampRatio(dockWidth.value / window.innerWidth)
}

function onDragEnd() {
  resizing.value = false
  saveSplitRatio(splitRatio.value)
}

function onToolSelect(mode: PanelMode) {
  if (activePanel.value === mode) {
    activePanel.value = null
    return
  }
  activePanel.value = mode
}

function onStartGrammarCheck() {
  activePanel.value = 'grammarCheck'
}

// 评分完成时记住被评分的文本，文本变化时自动清除评分结果
watch(evaluateResult, (result) => {
  if (result) {
    evaluatedText.value = draftText.value
    saveEvaluateResult(result)
  } else {
    evaluatedText.value = null
    activeErrorId.value = null
    saveEvaluateResult(null)
  }
})

watch(draftText, (newText) => {
  if (evaluateResult.value && evaluatedText.value !== null && newText !== evaluatedText.value) {
    evaluateResult.value = null
  }
  // 触发实时语法检查
  scheduleGrammarCheck()
})

function onEditorErrorClick(errorId: string) {
  activeErrorId.value = activeErrorId.value === errorId ? null : errorId
  if (activeErrorId.value) {
    activePanel.value = 'grammarCheck'
  }
}

// ── 实时语法检查 ──

function scheduleGrammarCheck() {
  if (!grammarCheckActive.value || submitting.value) return
  const text = draftText.value.trim()
  if (text.length < 10) {
    grammarErrors.value = []
    grammarCheckError.value = null
    return
  }
  // Abort previous
  if (grammarCheckTimer) clearTimeout(grammarCheckTimer)
  grammarCheckAbortController?.abort()
  grammarCheckTimer = setTimeout(() => {
    runGrammarCheck()
  }, 800)
}

async function runGrammarCheck() {
  const text = draftText.value.replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim()
  if (!text || text.length < 10) return
  grammarCheckAbortController = new AbortController()
  grammarChecking.value = true
  grammarCheckError.value = null
  try {
    const res = await grammarCheckApi({ text }, { signal: grammarCheckAbortController.signal })
    grammarErrors.value = res.errors ?? []
    grammarFixedErrorIds.value = new Set()
    gptErrors.value = []  // 重检时清空 GPT 错误，GPT 会在无错误时重新调用
    gptSuggestionErrors.value = []
    if (evaluateResult.value) grammarReChecked.value = true
  } catch (e: any) {
    if (e?.name === 'CanceledError' || e?.name === 'AbortError' || e?.code === 'ERR_CANCELED') return
    grammarErrors.value = []
    grammarCheckError.value = e?.message ?? '语法检查失败'
  } finally {
    grammarChecking.value = false
  }
}

function onGrammarFixError(errorId: string) {
  // 查找错误来源：语法面板合并了评分错误和实时语法错误
  const panelErrors = grammarPanelErrors.value
  const err = panelErrors.find((e) => e.id === errorId)
  if (!err || !err.original || !hasValidSuggestion(err)) return
  if (grammarFixedErrorIds.value.has(errorId)) return

  const text = draftText.value
  const resolved = resolveErrorSpan(err, text)
  if (!resolved) {
    showToast(`无法定位「${err.original.slice(0, 20)}…」，可能已被修改`, 'info')
    return
  }
  const { start, end } = resolved
  const suggestion = err.suggestion!

  grammarFixedErrorIds.value = new Set([...grammarFixedErrorIds.value, errorId])

  const newText = text.slice(0, start) + suggestion + text.slice(end)
  evaluatedText.value = newText
  grammarCheckActive.value = true
  draftText.value = newText
}

function onGrammarFixAll() {
  const panelErrors = grammarPanelErrors.value
  const unfixed = panelErrors
    .filter((e) => !grammarFixedErrorIds.value.has(e.id) && e.original && hasValidSuggestion(e))

  let text = draftText.value
  const newFixed = new Set(grammarFixedErrorIds.value)

  const resolved: { err: typeof unfixed[number]; start: number; end: number }[] = []
  for (const err of unfixed) {
    const pos = resolveErrorSpan(err, text)
    if (pos) resolved.push({ err, ...pos })
  }
  resolved.sort((a, b) => b.start - a.start)

  for (const { err, start, end } of resolved) {
    text = text.slice(0, start) + err.suggestion! + text.slice(end)
    newFixed.add(err.id)
  }

  evaluatedText.value = text
  grammarCheckActive.value = true
  draftText.value = text
  grammarFixedErrorIds.value = newFixed
}

// ── 行内弹窗替换/忽略 ──

function onInlineFixError(errorId: string) {
  // 先在语法面板错误中查找
  const panelErr = grammarPanelErrors.value.find((e) => e.id === errorId)
  if (panelErr) {
    onGrammarFixError(errorId)
    return
  }
  // 再在 GPT 错误中查找
  const gptErr = gptErrors.value.find((e) => e.id === errorId)
    ?? gptSuggestionErrors.value.find((e) => e.id === errorId)
  if (gptErr && gptErr.original && gptErr.suggestion != null) {
    const text = draftText.value
    const resolved = resolveErrorSpan(gptErr, text)
    if (!resolved) return
    const newText = text.slice(0, resolved.start) + gptErr.suggestion + text.slice(resolved.end)
    evaluatedText.value = newText
    draftText.value = newText
  }
}

function onDismissError(errorId: string) {
  // 将错误加入已修复集合，使其从面板和编辑器中消失
  grammarFixedErrorIds.value = new Set([...grammarFixedErrorIds.value, errorId])
  // 也从 GPT 错误中移除
  gptErrors.value = gptErrors.value.filter((e) => e.id !== errorId)
  gptSuggestionErrors.value = gptSuggestionErrors.value.filter((e) => e.id !== errorId)
}

function onGptErrorsLoaded(errors: SuggestionErrorItem[]) {
  // 将 GPT 硬性错误转为 CorrectionError 格式，带 span（通过 indexOf 定位）
  const text = draftText.value
  const converted: CorrectionError[] = []
  for (const e of errors) {
    const idx = findClosestMatch(text, e.original, 0, true, shouldUseWordBoundary(e.original))
    if (idx === -1) continue
    converted.push({
      id: e.id,
      type: e.type as any,
      category: 'error',
      severity: (e.severity === 'minor' ? 'minor' : 'major') as 'minor' | 'major',
      span: { start: idx, end: idx + e.original.length },
      original: e.original,
      suggestion: e.suggestion,
      reason: e.reason,
    })
  }
  gptErrors.value = converted
}

function onGptSuggestionsLoaded(suggestions: SuggestionItem[]) {
  const text = draftText.value
  const converted: CorrectionError[] = []
  for (const s of suggestions) {
    const idx = findClosestMatch(text, s.original, 0, true, shouldUseWordBoundary(s.original))
    if (idx === -1) continue
    converted.push({
      id: s.id,
      type: s.type as any,
      category: 'suggestion',
      severity: 'minor',
      span: { start: idx, end: idx + s.original.length },
      original: s.original,
      suggestion: s.suggestion,
      reason: s.reason,
    })
  }
  gptSuggestionErrors.value = converted
}

function onApplySuggestion(payload: { original: string; suggestion: string }) {
  const text = draftText.value
  const resolved = resolveErrorSpan(
    {
      original: payload.original,
      span: { start: 0, end: Math.min(text.length, payload.original.length) },
    },
    text,
  )
  if (!resolved) {
    showToast('无法定位原文，可能已被修改', 'info')
    return
  }
  draftText.value = text.slice(0, resolved.start) + payload.suggestion + text.slice(resolved.end)
  showToast('已替换', 'success')
}

function onPanelErrorClick(errorId: string) {
  activeErrorId.value = activeErrorId.value === errorId ? null : errorId
}

function onApplyPolish(payload: { errorId: string; polished: string }) {
  const errors = evaluateResult.value?.errors
  if (!errors) return
  const err = errors.find((e) => e.id === payload.errorId)
  if (!err?.original) return

  const text = draftText.value
  const resolved = resolveErrorSpan(err, text)
  if (!resolved) {
    showToast(`无法定位「${err.original.slice(0, 20)}…」，可能已被修改`, 'info')
    return
  }
  const { start, end } = resolved

  const newText = text.slice(0, start) + payload.polished + text.slice(end)
  draftText.value = newText
  evaluatedText.value = newText
  showToast('已替换', 'success')
}

function onReplaceSentence(payload: { original: string; replacement: string }) {
  const text = draftText.value
  const idx = text.indexOf(payload.original)
  if (idx === -1) {
    showToast('原句已被修改，无法定位替换', 'info')
    return
  }
  const newText = text.slice(0, idx) + payload.replacement + text.slice(idx + payload.original.length)
  draftText.value = newText
  evaluatedText.value = newText
  showToast('已替换', 'success')
}

function onStartPolish() {
  activePanel.value = 'rewrite'
}

// 在所有匹配中找到最接近 hintPos 的位置
function isWordChar(ch: string): boolean {
  return /[A-Za-z0-9'_/-]/.test(ch)
}

function shouldUseWordBoundary(needle: string): boolean {
  if (!needle) return false
  return isWordChar(needle[0]) && isWordChar(needle[needle.length - 1])
}

function isWholeWordMatch(text: string, start: number, len: number): boolean {
  if (start < 0 || len <= 0) return false
  const end = start + len
  const leftOk = start === 0 || !isWordChar(text[start - 1])
  const rightOk = end >= text.length || !isWordChar(text[end])
  return leftOk && rightOk
}

function findClosestMatch(
  text: string,
  needle: string,
  hintPos: number,
  caseSensitive = true,
  wholeWordOnly = false,
): number {
  const haystack = caseSensitive ? text : text.toLowerCase()
  const target = caseSensitive ? needle : needle.toLowerCase()
  let bestIdx = -1
  let bestDist = Infinity
  let searchFrom = 0
  while (searchFrom <= haystack.length - target.length) {
    const idx = haystack.indexOf(target, searchFrom)
    if (idx === -1) break
    if (wholeWordOnly && !isWholeWordMatch(text, idx, needle.length)) {
      searchFrom = idx + 1
      continue
    }
    const dist = Math.abs(idx - hintPos)
    if (dist < bestDist) {
      bestDist = dist
      bestIdx = idx
    }
    searchFrom = idx + 1
  }
  return bestIdx
}

function resolveErrorSpan(
  err: { original?: string; span: { start: number; end: number } },
  text: string,
): { start: number; end: number } | null {
  const { start, end } = err.span
  if (!err.original) return null

  const wholeWord = shouldUseWordBoundary(err.original)

  // 1. span 有效且文本匹配（需要时必须是整词）
  if (start >= 0 && end <= text.length && start < end && text.slice(start, end) === err.original) {
    if (!wholeWord || isWholeWordMatch(text, start, end - start)) {
      return { start, end }
    }
  }

  // 2. 精确搜索（优先最接近原始 span 位置的匹配）
  const idx = findClosestMatch(text, err.original, start, true, wholeWord)
  if (idx !== -1) {
    return { start: idx, end: idx + err.original.length }
  }

  // 3. 忽略大小写搜索（优先最接近原始 span 位置的匹配）
  const idxLower = findClosestMatch(text, err.original, start, false, wholeWord)
  if (idxLower !== -1) {
    return { start: idxLower, end: idxLower + err.original.length }
  }

  // 4. 规范化空白后搜索（合并多余空格）
  const normText = text.replace(/\s+/g, ' ')
  const normOrig = err.original.replace(/\s+/g, ' ')
  const idxNorm = normText.indexOf(normOrig)
  if (idxNorm !== -1) {
    let origPos = 0
    let normPos = 0
    while (normPos < idxNorm && origPos < text.length) {
      if (/\s/.test(text[origPos]) && normPos > 0 && normText[normPos - 1] === ' ') {
        origPos++
        continue
      }
      origPos++
      normPos++
    }
    const matchStart = origPos
    let matchLen = 0
    let remaining = normOrig.length
    let p = origPos
    while (remaining > 0 && p < text.length) {
      if (/\s/.test(text[p]) && matchLen > 0 && /\s/.test(text[p - 1])) {
        p++
        continue
      }
      p++
      remaining--
      matchLen = p - matchStart
    }

    if (!wholeWord || isWholeWordMatch(text, matchStart, matchLen)) {
      return { start: matchStart, end: matchStart + matchLen }
    }
  }

  return null
}

function hasValidSuggestion(err: { original?: string; suggestion?: string }): boolean {
  if (err.suggestion == null) return false
  const s = err.suggestion.trim()
  if (/^n\/?a$/i.test(s)) return false
  // 空字符串表示删除，是有效操作
  if (s === '' && err.original?.trim()) return true
  if (!s) return false
  if (err.original && s === err.original.trim()) return false
  return true
}


function onLoadHistoryResult(detail: EvaluationDetailResponse) {
  evaluateResult.value = detail.result
  if (detail.essayText) {
    draftText.value = detail.essayText
  }
  activePanel.value = 'score'
}

function onArchived() {
  archivedList.value.push({
    draft: draftText.value,
    aiNote: aiNote.value,
    at: new Date().toISOString(),
  })
  showToast('Archived', 'success')
}

async function onSubmit() {
  submitting.value = true
  evaluateResult.value = null
  evaluateError.value = null
  // 暂停语法检查
  grammarCheckActive.value = false
  grammarErrors.value = []
  gptErrors.value = []
  gptSuggestionErrors.value = []
  grammarCheckError.value = null
  grammarReChecked.value = false
  grammarCheckAbortController?.abort()
  if (grammarCheckTimer) clearTimeout(grammarCheckTimer)
  const currentPollToken = ++evaluatePollToken
  const normalizedMode = writingMode.value === 'exam' ? 'exam' : 'free'
  const examTaskPrompt =
    normalizedMode === 'exam' ? taskPrompt.value.trim() || undefined : undefined
  try {
    const submitRes = await submitEvaluateWriting({
      essay: draftText.value.trim(),
      aiHint: aiNote.value.trim() || undefined,
      mode: normalizedMode,
      taskPrompt: examTaskPrompt,
      lang: 'en',
    })
    if (!submitRes.requestId) {
      throw new Error('评估任务提交失败：缺少 requestId')
    }

    const startedAt = Date.now()
    const pollIntervalMs = 1200
    const pollTimeoutMs = 180000

    let task: WritingEvaluateTaskStatusResponse | null = null
    while (Date.now() - startedAt < pollTimeoutMs) {
      if (currentPollToken !== evaluatePollToken) {
        return
      }

      task = await getEvaluateTask(submitRes.requestId)
      if (task.status === 'succeeded' && task.result) {
        evaluateResult.value = task.result
        break
      }
      if (task.status === 'failed') {
        throw new Error(task.error || '评估任务失败')
      }

      await new Promise((resolve) => setTimeout(resolve, pollIntervalMs))
    }

    if (!task || task.status !== 'succeeded' || !task.result) {
      throw new Error('评估超时，请稍后再试')
    }

    activePanel.value = 'score'
    showToast('评估完成', 'success')
  } catch (e) {
    const msg = e instanceof Error ? e.message : '评估失败'
    evaluateError.value = msg
    showToast(msg, 'error')
  } finally {
    if (currentPollToken === evaluatePollToken) {
      submitting.value = false
    }
  }
}

function onDismissSelection() {
  lastDismissedPinned.value = selectedTextPinned.value
  selectionDismissed.value = true
  selectedSpanPinned.value = null
}

function onClear() {
  draftText.value = ''
  aiNote.value = ''
  selectionState.value = null
  selectionDismissed.value = false
  selectedTextPinned.value = ''
  selectedSpanPinned.value = null
  lastDismissedPinned.value = ''
  lastChatResult.value = null
  aiDocId.value = ''
  cursorPlacement.value = null
  evaluateResult.value = null
  activeErrorId.value = null
  // 清空语法检查
  grammarErrors.value = []
  grammarCheckError.value = null
  grammarFixedErrorIds.value = new Set()
  gptErrors.value = []
  gptSuggestionErrors.value = []
  grammarCheckActive.value = true
  grammarReChecked.value = false
  grammarCheckAbortController?.abort()
  if (grammarCheckTimer) clearTimeout(grammarCheckTimer)
  try {
    localStorage.removeItem(DRAFT_KEY)
    localStorage.removeItem(LEGACY_DRAFT_KEY)
    localStorage.removeItem(AI_NOTE_DRAFT_KEY)
    localStorage.removeItem(EVALUATE_RESULT_KEY)
    console.log('[draft] cleared')
    console.log('[aiNoteDraft] cleared')
  } catch (_) {}
}

function onReplaceSelectionWith(resultText: string) {
  const span = selectedSpanPinned.value
  if (!span) {
    showToast('无选中范围，请先选中要替换的文本', 'info')
    return
  }
  const { start, end } = span
  const s = draftText.value
  if (start < 0 || end > s.length || start > end) {
    showToast('Selected range is invalid', 'info')
    return
  }
  const newDraft = s.slice(0, start) + resultText + s.slice(end)
  draftText.value = newDraft
  cursorPlacement.value = { at: start + resultText.length }
  lastChatResult.value = null
  showToast('已替换选中内容', 'success')
}

function getRecentAiMessages(max = 8): RecentMessageDto[] {
  return rightPanelRef.value?.getAiRecentMessages?.(max) ?? []
}

function onAiChatCleared() {
  aiConversationId.value = createConversationId()
  lastChatResult.value = null
  aiAbortController?.abort()
  aiAbortController = null
  aiGenerating.value = false
}

watch(
  aiConversationId,
  (val) => {
    try {
      if (val?.trim()) localStorage.setItem(AI_CHAT_CONVERSATION_ID_KEY, val.trim())
    } catch (_) {}
  },
  { immediate: true }
)

async function onAiNoteSend() {
  if (aiGenerating.value) return
  const instruction = aiNote.value.trim()
  const selectedText = selectionStore.selectedText.value.trim()
  const hasSelectedText = Boolean(selectedText)
  const wantsDraft = rightPanelRef.value?.isIncludeDraft?.() ?? false
  const hasDraftText = Boolean(draftText.value.trim())
  const normalizedMode = writingMode.value === 'exam' ? 'exam' : 'free'
  const examTaskPrompt =
    normalizedMode === 'exam' ? taskPrompt.value.trim() || undefined : undefined
  const contextScope = hasSelectedText ? 'selection' : 'auto'
  const actionOrigin = 'chat_input'
  const recentMessages = getRecentAiMessages(8)
  console.log('[AI SEND]', {
    docId: aiDocId.value,
    instruction,
    hasSelectedText,
    hasDraftText,
    wantsDraft,
    writingMode: normalizedMode,
    hasTaskPrompt: Boolean(examTaskPrompt),
    contextScope,
    actionOrigin,
    recentMessagesCount: recentMessages.length,
    conversationId: aiConversationId.value,
  })
  if (!instruction) {
    showToast('请输入需求', 'info')
    return
  }
  aiAbortController = new AbortController()
  aiGenerating.value = true
  try {
    if (!aiDocId.value) {
      const created = await createDocument({
        title: 'Untitled',
        content: draftText.value,
      })
      aiDocId.value = created.docId
    }

    const res = await aiCommand({
      apiVersion: 1,
      intent: 'chat',
      mode: 'md',
      instruction,
      constraints: {
        contextScope,
        actionOrigin,
        includeDraft: wantsDraft ? true : undefined,
        mode: normalizedMode,
        taskPrompt: examTaskPrompt,
        conversationId: aiConversationId.value,
        selectedText: selectedText || undefined,
        draftText: wantsDraft ? (draftText.value || undefined) : undefined,
        recentMessages: recentMessages.length ? recentMessages : undefined,
      },
      contextRefs: {
        docId: aiDocId.value,
      },
    }, {
      signal: aiAbortController.signal,
    })

    if (res.apply !== '') {
      lastChatResult.value = {
        displayText: res.apply,
        replaceText: res.replaceSelectionText,
      }
    } else {
      lastChatResult.value = null
    }
    showToast('已发送', 'success')
  } catch (e) {
    const canceled = Boolean((e as { canceled?: boolean } | null)?.canceled)
    if (canceled) {
      showToast('已停止生成', 'info')
      return
    }
    showToast(e instanceof Error ? e.message : '发送失败', 'error')
  } finally {
    aiAbortController = null
    aiGenerating.value = false
  }
}

function onAiNoteStop() {
  if (!aiGenerating.value) return
  aiAbortController?.abort()
}
</script>

<style scoped>
:global(:root) {
  --rail-width: 52px;
  --rail-gap: 16px;
  --rail-safe: calc(var(--rail-width) + var(--rail-gap) + 12px);
}

.writing-root {
  height: 100dvh;
  overflow-y: hidden;
  overflow-x: hidden;
  box-sizing: border-box;
  background: #f3f4f6;
  transform: none;
}
.workspace-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--splitter-width) var(--rightWidth);
  min-width: 0;
  height: 100%;
  overflow: hidden;
  transform: none;
}
.left-pane {
  grid-column: 1;
  min-width: 0;
  overflow-y: scroll;
  overflow-x: hidden;
  padding-right: var(--rail-safe);
  box-sizing: border-box;
  scrollbar-gutter: stable;
  user-select: text;
  pointer-events: auto;
  background: #f7f7f8;
  transform: none;
}



.assistant-pane {
  grid-column: 3;
  position: relative;
  min-width: 0;
  overflow: hidden;
  padding-right: var(--rail-safe);
  box-sizing: border-box;
  border-left: 1px solid #e5e7eb;
  background: #f9fafb;
  --assistant-safe-padding-right: var(--rail-safe);
  transform: none;
}
.assistant-pane.collapsed {
  border-left: none;
  background: transparent;
}
.panel-splitter {
  grid-column: 2;
  height: 100%;
}
.toolrail-fixed-overlay {
  position: fixed;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 999;
  pointer-events: auto;
}

@media (max-width: 1100px) {
  .workspace-layout {
    grid-template-columns: minmax(0, 1fr) var(--splitter-width) var(--rightWidth);
  }
  .assistant-pane {
    width: auto;
    min-width: 0;
    max-width: none;
  }
  .assistant-pane.collapsed {
    width: 0;
    min-width: 0;
    max-width: 0;
    border-left: none;
  }
  .panel-splitter {
    display: none;
  }
}
</style>









