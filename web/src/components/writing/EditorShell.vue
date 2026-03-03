<template>
  <div class="writing-root">
    <ToolRail class="toolrail-fixed-overlay" :active-panel="activePanel" @select="onToolSelect" />
    <div class="workspace-layout" :style="layoutStyle">
      <div ref="leftPaneRef" class="left-pane">
        <DocEditor
          v-model:draft-text="draftText"
          :correction-mode="correctionMode"
          :submitting="submitting"
          :cursor-placement="cursorPlacement"
          :selection-capture-enabled="assistantOpen"
          :errors="displayEditorErrors"
          :active-error-id="activeErrorId"
          @submit="onSubmit"
          @clear="onClear"
          @selection-change="onSelectionChange"
          @cursor-placed="cursorPlacement = null"
          @error-click="onEditorErrorClick"
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
          :fixed-error-ids="fixedErrorIds"
          @close="activePanel = null"
          @start-fix="onStartFix"
          @fix-error="onFixError"
          @fix-all="onFixAll"
          @exit-correction="onExitCorrection"
          @error-click="onPanelErrorClick"
          @apply-polish="onApplyPolish"
          @start-polish="onStartPolish"
          @retry="onSubmit"
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
import { getEvaluateTask, submitEvaluateWriting } from '@/api/writing'
import type { WritingEvaluateResponse, WritingEvaluateTaskStatusResponse, EvaluationDetailResponse } from '@/api/writing'
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
  'score', 'rewrite', 'revise', 'improve', 'explain', 'translate', 'archive', 'aiNote',
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
const correctionMode = ref(false)
const submitting = ref(false)
const evaluateError = ref<string | null>(null)
let evaluatePollToken = 0
const resizing = ref(false)
const archivedList = ref<unknown[]>([])
const evaluateResult = ref<WritingEvaluateResponse | null>(null)
const activeErrorId = ref<string | null>(null)
const evaluatedText = ref<string | null>(null)

// ── 订正会话状态 ──
type CorrectionError = WritingEvaluateResponse['errors'][number]
const correctionErrors = ref<CorrectionError[] | null>(null)
const fixedErrorIds = ref<Set<string>>(new Set())
const correctionActive = computed(() => correctionErrors.value !== null)

const selectionStore = createWritingSelectionStore()

provide(writingSelectionStoreKey, selectionStore)

// 订正中过滤掉已修复的错误（高亮实时消失）
const displayEditorErrors = computed(() => {
  const errors = evaluateResult.value?.errors
  if (!errors) return undefined
  if (!correctionActive.value) return errors
  const fixed = fixedErrorIds.value
  return errors.filter((e) => !fixed.has(e.id))
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
    rewrite: 'AI 改写',
    revise: '订正',
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
  const handler = (window as Window & { __writingResizeHandler?: () => void }).__writingResizeHandler
  if (handler) {
    window.removeEventListener('resize', handler)
    delete (window as Window & { __writingResizeHandler?: () => void }).__writingResizeHandler
  }
})

watch(activePanel, () => saveLayoutState(), { flush: 'post' })

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
  // 订正中替换文本会触发此 watcher，但不应清除 evaluateResult
  if (correctionActive.value) return
  if (evaluateResult.value && evaluatedText.value !== null && newText !== evaluatedText.value) {
    evaluateResult.value = null
  }
})

function onEditorErrorClick(errorId: string) {
  activeErrorId.value = activeErrorId.value === errorId ? null : errorId
  if (activeErrorId.value) {
    activePanel.value = 'score'
  }
}

function onPanelErrorClick(errorId: string) {
  activeErrorId.value = activeErrorId.value === errorId ? null : errorId
}

function onApplyPolish(payload: { errorId: string; polished: string }) {
  const errors = correctionErrors.value ?? evaluateResult.value?.errors
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

  const delta = payload.polished.length - (end - start)

  // 先标记 + 调整 span，再更新文本（DocEditor watch 是 sync）
  fixedErrorIds.value = new Set([...fixedErrorIds.value, payload.errorId])

  if (correctionErrors.value) {
    for (const other of correctionErrors.value) {
      if (other.id === payload.errorId) continue
      if (fixedErrorIds.value.has(other.id)) continue
      if (other.span.start >= end) {
        other.span.start += delta
        other.span.end += delta
      } else if (other.span.start > start) {
        other.span.end += delta
      }
    }
  }

  const newText = text.slice(0, start) + payload.polished + text.slice(end)
  draftText.value = newText
  evaluatedText.value = newText
  showToast('已替换', 'success')
}

function onStartFix() {
  const errors = evaluateResult.value?.errors
  if (!errors?.length) return
  // 深拷贝 errors 快照
  correctionErrors.value = JSON.parse(JSON.stringify(errors))
  fixedErrorIds.value = new Set()
  correctionMode.value = true
  activePanel.value = 'revise'
}

function onStartPolish() {
  activePanel.value = 'rewrite'
}

function resolveErrorSpan(
  err: { original?: string; span: { start: number; end: number } },
  text: string,
): { start: number; end: number } | null {
  const { start, end } = err.span
  // 1. span 有效且文本匹配 → 直接用
  if (start >= 0 && end <= text.length && start < end && text.slice(start, end) === err.original) {
    return { start, end }
  }
  if (!err.original) return null
  // 2. 精确搜索
  const idx = text.indexOf(err.original)
  if (idx !== -1) {
    return { start: idx, end: idx + err.original.length }
  }
  // 3. 忽略大小写搜索
  const lowerText = text.toLowerCase()
  const lowerOrig = err.original.toLowerCase()
  const idxLower = lowerText.indexOf(lowerOrig)
  if (idxLower !== -1) {
    return { start: idxLower, end: idxLower + err.original.length }
  }
  // 4. 规范化空白后搜索（合并多余空格）
  const normText = text.replace(/\s+/g, ' ')
  const normOrig = err.original.replace(/\s+/g, ' ')
  const idxNorm = normText.indexOf(normOrig)
  if (idxNorm !== -1) {
    // 映射回原文位置：逐字符对齐
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
    return { start: matchStart, end: matchStart + matchLen }
  }
  return null
}

function hasValidSuggestion(err: { original?: string; suggestion?: string }): boolean {
  const s = err.suggestion?.trim()
  if (!s || /^n\/?a$/i.test(s)) return false
  if (err.original && s === err.original.trim()) return false
  return true
}

function onFixError(errorId: string) {
  if (!correctionErrors.value) return
  const err = correctionErrors.value.find((e) => e.id === errorId)
  if (!err || !err.original || !hasValidSuggestion(err)) return
  if (fixedErrorIds.value.has(errorId)) return

  const text = draftText.value
  const resolved = resolveErrorSpan(err, text)
  if (!resolved) {
    showToast(`无法定位「${err.original.slice(0, 20)}…」，可能已被修改`, 'info')
    return
  }
  const { start, end } = resolved

  const suggestion = err.suggestion!
  const delta = suggestion.length - (end - start)

  // 先标记已修复 + 调整 span（在 draftText 变化之前，因为 DocEditor watch 是 sync）
  fixedErrorIds.value = new Set([...fixedErrorIds.value, errorId])

  for (const other of correctionErrors.value) {
    if (other.id === errorId) continue
    if (fixedErrorIds.value.has(other.id)) continue
    if (other.span.start >= end) {
      other.span.start += delta
      other.span.end += delta
    } else if (other.span.start > start) {
      other.span.end += delta
    }
  }

  // 最后更新文本（触发 DocEditor sync watch 时 fixedErrorIds 已是最新）
  const newText = text.slice(0, start) + suggestion + text.slice(end)
  draftText.value = newText
  evaluatedText.value = newText
}

function onFixAll() {
  if (!correctionErrors.value) return
  const unfixed = correctionErrors.value
    .filter((e) => !fixedErrorIds.value.has(e.id) && e.category !== 'suggestion' && e.original && hasValidSuggestion(e))

  let text = draftText.value
  const newFixed = new Set(fixedErrorIds.value)

  // 按 span.start 降序：从后往前替换，天然不需要偏移调整
  // 但 span 可能已漂移，需要用 resolveErrorSpan 重新定位
  // 先解析全部位置，再按 start 降序排列替换
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

  draftText.value = text
  evaluatedText.value = text
  fixedErrorIds.value = newFixed
}

function onExitCorrection() {
  correctionErrors.value = null
  fixedErrorIds.value = new Set()
  correctionMode.value = false
  evaluatedText.value = draftText.value
  activePanel.value = 'score'
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
  // 重置订正/润色状态
  correctionErrors.value = null
  fixedErrorIds.value = new Set()
  correctionMode.value = false
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
  correctionMode.value = false
  evaluateResult.value = null
  activeErrorId.value = null
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
  const contextScope = hasSelectedText ? 'selection' : (wantsDraft ? 'auto' : 'none')
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






