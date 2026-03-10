<template>
  <div class="writing-root">
    <ToolRail class="toolrail-fixed-overlay" :active-panel="panelStore.activePanel" @select="panelStore.selectPanel" />
    <div class="workspace-layout" :style="panelStore.layoutStyle">
      <div ref="leftPaneRef" class="left-pane">
        <DocEditor
          v-model:draft-text="draftStore.draftText"
          :correction-mode="false"
          :submitting="submitting"
          :writing-mode="draftStore.writingMode"
          :cursor-placement="cursorPlacement"
          :selection-capture-enabled="panelStore.assistantOpen"
          :errors="grammarStore.displayEditorErrors"
          :active-error-id="activeErrorId"
          :highlight-range="sentenceHighlightRange"
          @submit="onSubmit"
          @clear="onClear"
          @selection-change="onSelectionChange"
          @cursor-placed="cursorPlacement = null"
          @error-click="onEditorErrorClick"
          @fix-error="grammarStore.inlineFixError"
          @dismiss-error="grammarStore.dismissError"
          @bubble-action="onBubbleAction"
          @back="onBack"
        />
      </div>

      <Splitter
        v-if="panelStore.activePanel !== null"
        class="panel-splitter"
        :min-right="MIN_PANEL_WIDTH"
        :max-right="MAX_PANEL_WIDTH"
        :min-editor="MIN_LEFT_WIDTH"
        @update:width="panelStore.updateDockWidth"
        @drag-start="panelStore.resizing = true"
        @drag-end="panelStore.finishDrag"
      />

      <div class="assistant-pane" :class="{ collapsed: panelStore.activePanel === null }">
        <RightPanel
          ref="rightPanelRef"
          v-if="panelStore.activePanel !== null"
          :panel="panelStore.activePanel"
          :title="panelStore.panelTitle"
          :width="panelStore.dockWidth"
          :essay="draftStore.draftText"
          :selection-state="selectionState"
          :selection-dismissed="selectionDismissed"
          :selected-text-pinned="selectedTextPinned"
          :selected-span-pinned="selectedSpanPinned"
          :last-chat-result="lastChatResult"
          :conversation-id="draftStore.aiConversationId"
          :ai-generating="aiGenerating"
          :writing-mode="draftStore.writingMode"
          :study-stage="props.studyStage"
          :task-prompt="draftStore.taskPrompt"
          :ai-note="draftStore.aiNote"
          :evaluate-result="grammarStore.evaluateResult"
          :active-error-id="activeErrorId"
          :submitting="submitting"
          :evaluate-error="evaluateError"
          :exam-max-score="props.examMaxScore"
          :grammar-errors="grammarStore.grammarPanelErrors"
          :grammar-checking="grammarStore.grammarChecking"
          :grammar-check-error="grammarStore.grammarCheckError"
          :grammar-fixed-error-ids="grammarStore.grammarPanelFixedIds"
          :rewrite-suggestions="grammarStore.rewritePanelSuggestions"
          :exam-first-write-locked="examFirstWriteLocked"
          @close="panelStore.activePanel = null"
          @error-click="onPanelErrorClick"
          @apply-polish="onApplyPolish"
          @replace-sentence="onReplaceSentence"
          @sentence-focus="sentenceHighlightRange = $event"
          @start-polish="onStartPolish"
          @grammar-fix-error="grammarStore.fixError"
          @grammar-fix-all="grammarStore.fixAll"
          @apply-suggestion="onApplySuggestion"
          @gpt-errors-loaded="grammarStore.setGptErrors"
          @gpt-suggestions-loaded="grammarStore.setGptSuggestions"
          @retry="onSubmit"
          @paragraph-click="onParagraphClick"
          @start-grammar-check="onStartGrammarCheck"
          @dismiss-selection="onDismissSelection"
          @replace-selection-with="onReplaceSelectionWith"
          @update:ai-note="draftStore.aiNote = $event"
          @update:writing-mode="draftStore.writingMode = $event"
          @update:task-prompt="draftStore.taskPrompt = $event"
          @ai-note-send="onAiNoteSend"
          @ai-note-stop="onAiNoteStop"
          @ai-chat-cleared="onAiChatCleared"
        />
      </div>
    </div>

    <!-- 退出确认对话框 -->
    <Teleport to="body">
      <div v-if="showExitDialog" class="exit-overlay" @click.self="onExitCancel">
        <div class="exit-dialog">
          <h3 class="exit-title">退出写作</h3>
          <p class="exit-message">你的作文尚未提交，是否保存为草稿？</p>
          <div class="exit-actions">
            <button class="exit-btn exit-btn-cancel" @click="onExitCancel">取消</button>
            <button class="exit-btn exit-btn-discard" @click="onExitDiscard">不保存</button>
            <button class="exit-btn exit-btn-save" @click="onExitSave">保存并退出</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick, provide } from 'vue'
import { useEventListener } from '@vueuse/core'

const emit = defineEmits<{
  back: []
}>()

const props = withDefaults(defineProps<{
  initialWritingMode?: 'free' | 'exam'
  initialTaskPrompt?: string
  initialDocId?: string | null
  initialExistingContent?: string | null
  examMaxScore?: number | null
  initialSubmitCount?: number
  studyStage?: string | null
}>(), {
  initialWritingMode: undefined,
  initialTaskPrompt: undefined,
  initialDocId: null,
  initialExistingContent: null,
  examMaxScore: null,
  initialSubmitCount: 0,
  studyStage: null,
})

import DocEditor from './DocEditor.vue'
import RightPanel from './RightPanel.vue'
import ToolRail from './ToolRail.vue'
import Splitter from './Splitter.vue'
import { useEvaluateSubmission } from '@/composables/useEvaluateSubmission'
import { aiCommand } from '@/api/ai'
import { createDocument } from '@/api/document'
import { showToast } from '@/utils/toast'
import { createWritingSelectionStore, writingSelectionStoreKey } from './useWritingSelectionStore'
import { resolveErrorSpan, findClosestMatch, shouldUseWordBoundary } from './errorSpanResolver'
import {
  WRITING_STORAGE_KEYS,
  loadEvaluateResult,
  saveEvaluateResult,
} from './editorShellStorage'
import { usePanelStore, MIN_PANEL_WIDTH, MAX_PANEL_WIDTH, MIN_LEFT_WIDTH } from '@/stores/panelStore'
import { useWritingDraftStore } from '@/stores/writingDraftStore'
import { useGrammarStore } from '@/stores/grammarStore'
import { stageCache } from '@/stores/stageCache'
import { getStageConfig } from '@/api/writing'

const panelStore = usePanelStore()
const draftStore = useWritingDraftStore()
const grammarStore = useGrammarStore()
const minWordCount = ref(60)

type RecentMessageDto = { role: 'user' | 'assistant'; content: string }

const leftPaneRef = ref<HTMLElement | null>(null)
const rightPanelRef = ref<{
  focusAiComposer: () => boolean
  getAiRecentMessages?: (max?: number) => RecentMessageDto[]
  isIncludeDraft?: () => boolean
} | null>(null)
const selectionState = ref<{ text: string; start: number; end: number } | null>(null)
const selectionDismissed = ref(false)
const selectedTextPinned = ref('')
const selectedSpanPinned = ref<{ start: number; end: number } | null>(null)
const lastDismissedPinned = ref('')
const lastChatResult = ref<{ displayText: string; replaceText?: string } | null>(null)
const aiDocId = ref('')
const cursorPlacement = ref<{ at: number } | null>(null)
const aiGenerating = ref(false)
let aiAbortController: AbortController | null = null
const {
  submit: evalSubmit,
  cancel: evalCancel,
  clearResult: evalClearResult,
  evaluateResult,
  evaluateError,
  submitting,
} = useEvaluateSubmission()
const activeErrorId = ref<string | null>(null)
const sentenceHighlightRange = ref<{ start: number; end: number } | null>(null)
const examFirstWriteLocked = computed(() =>
  draftStore.writingMode === 'exam' && draftStore.submitCount === 0,
)

const selectionStore = createWritingSelectionStore()
provide(writingSelectionStoreKey, selectionStore)

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
  return selection.toString().trim()
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

watch(selectionState, (cur) => {
  const curText = cur?.text?.trim() ?? ''
  if (curText) {
    if (selectionDismissed.value && curText === lastDismissedPinned.value) return
    selectedTextPinned.value = cur!.text
    selectedSpanPinned.value = { start: cur!.start, end: cur!.end }
    selectionDismissed.value = false
  }
})

// Scroll save
const onLeftPaneScroll = () => {
  try {
    if (leftPaneRef.value) localStorage.setItem(WRITING_STORAGE_KEYS.scrollTop, String(leftPaneRef.value.scrollTop))
  } catch (_) {}
}

onMounted(async () => {
  // Snapshot evaluate result from localStorage BEFORE resetAll clears it.
  // resetAll sets evaluateResult to null, whose watch callback would wipe
  // localStorage during the subsequent await (Vue flushes watchers between ticks).
  const preloadedDocId = props.initialDocId?.trim()
    || sessionStorage.getItem('peai:writing:docId')?.trim()
    || null
  const cachedEvalResult = loadEvaluateResult(preloadedDocId)

  // 重置旧文档状态，防止残留评价/语法数据
  grammarStore.resetAll()
  panelStore.activePanel = null

  const layout = panelStore.initLayout()

  // ── Single-channel hydration ──
  const targetDocId = preloadedDocId

  if (targetDocId) {
    // Has docId (refresh or navigation): always hydrate from backend
    // to guarantee server content is available even in a fresh session.
    await draftStore.hydrateByDocId(targetDocId)
  } else {
    // New document (no docId): use props as startup parameters
    draftStore.init({
      initialWritingMode: props.initialWritingMode,
      initialTaskPrompt: props.initialTaskPrompt,
      initialDocId: props.initialDocId,
      initialSubmitCount: props.initialSubmitCount,
    })

    // Apply initial content from prop if available
    if (props.initialExistingContent && (!draftStore.draftText || !draftStore.draftText.trim())) {
      draftStore.draftText = props.initialExistingContent
    }
  }

  aiDocId.value = draftStore.docId ?? ''

  // Restore evaluate result from pre-loaded cache (survives resetAll + watch flush)
  const savedResult = cachedEvalResult ?? loadEvaluateResult(draftStore.docId)
  if (savedResult && !evaluateResult.value) {
    evaluateResult.value = savedResult
    grammarStore.setEvaluateResult(savedResult)
    if (layout.activePanel === 'score') {
      panelStore.activePanel = 'score'
    }
  }

  // Restore grammar cache
  grammarStore.restoreFromCache()

  // Fetch min word count for current stage (non-blocking)
  const stage = stageCache.value
  if (stage && stage !== '__error__') {
    getStageConfig(stage)
      .then((cfg) => { minWordCount.value = cfg.minWordCount ?? 60 })
      .catch(() => {})
  }

  // Wait for TipTap to initialize and normalize text, then re-sync evaluatedText
  // to prevent the draftText watch from clearing the restored evaluate result.
  await nextTick()
  if (evaluateResult.value) {
    draftStore.evaluatedText = draftStore.draftText
  }

  try {
    const s = localStorage.getItem(WRITING_STORAGE_KEYS.scrollTop)
    if (s != null && leftPaneRef.value) {
      const top = Number(s)
      if (Number.isFinite(top)) leftPaneRef.value.scrollTop = top
    }
  } catch (_) {}
})

// Event listeners (auto-cleanup via useEventListener)
useEventListener(leftPaneRef, 'scroll', onLeftPaneScroll)
useEventListener(leftPaneRef, 'mouseup', syncSelectionStoreFromLeftMouseup)
useEventListener(window, 'resize', () => panelStore.recalcDockWidth())
// Flush debounced draft on refresh/close to avoid losing last 500ms of typing
useEventListener(window, 'beforeunload', () => draftStore.flushDraft())

onBeforeUnmount(() => {
  evalCancel()
  grammarStore.destroy()
})

watch(() => panelStore.activePanel, (newPanel, oldPanel) => {
  if (oldPanel === 'rewrite' && newPanel !== 'rewrite') {
    sentenceHighlightRange.value = null
  }
  panelStore.saveState()
}, { flush: 'post' })

// Sync evaluateResult from composable to grammar store
watch(evaluateResult, (result) => {
  grammarStore.setEvaluateResult(result)
  if (result) {
    draftStore.evaluatedText = draftStore.draftText
    saveEvaluateResult(result, draftStore.docId)
    if (evalResultFromSubmit) {
      draftStore.submitCount++
      panelStore.activePanel = 'score'
      showToast('评估完成', 'success')
      evalResultFromSubmit = false
    }
  } else {
    draftStore.evaluatedText = null
    activeErrorId.value = null
    saveEvaluateResult(null, draftStore.docId)
  }
})

watch(evaluateError, (err) => {
  if (err && evalResultFromSubmit) {
    showToast(err, 'error')
    evalResultFromSubmit = false
  }
})

watch(() => draftStore.draftText, (newText) => {
  if (draftStore.isHydrating) return
  if (evaluateResult.value && draftStore.evaluatedText !== null && newText !== draftStore.evaluatedText) {
    evalClearResult()
  }
  if (!examFirstWriteLocked.value) {
    grammarStore.scheduleGrammarCheck()
  }
})

function onEditorErrorClick(errorId: string) {
  activeErrorId.value = activeErrorId.value === errorId ? null : errorId
  if (activeErrorId.value) {
    panelStore.activePanel = 'grammarCheck'
  }
}

function onPanelErrorClick(errorId: string) {
  activeErrorId.value = activeErrorId.value === errorId ? null : errorId
}

function onApplyPolish(payload: { errorId: string; polished: string }) {
  const errors = evaluateResult.value?.errors
  if (!errors) return
  const err = errors.find((e) => e.id === payload.errorId)
  if (!err?.original) return

  const text = draftStore.draftText
  const resolved = resolveErrorSpan(err, text)
  if (!resolved) {
    showToast(`无法定位「${err.original.slice(0, 20)}…」，可能已被修改`, 'info')
    return
  }

  draftStore.draftText = text.slice(0, resolved.start) + payload.polished + text.slice(resolved.end)
  draftStore.evaluatedText = draftStore.draftText
  showToast('已替换', 'success')
}

function onReplaceSentence(payload: { start: number; end: number; original: string; replacement: string }) {
  const text = draftStore.draftText
  let start = Math.max(0, Math.min(payload.start, text.length))
  let end = Math.max(0, Math.min(payload.end, text.length))
  if (end < start) { const tmp = start; start = end; end = tmp }

  const directSlice = text.slice(start, end)
  if (start >= end || directSlice !== payload.original) {
    const fallback = findClosestMatch(text, payload.original, start, true, shouldUseWordBoundary(payload.original))
    if (fallback < 0) {
      showToast('原句已被修改，无法定位替换', 'info')
      return
    }
    start = fallback
    end = fallback + payload.original.length
  }

  draftStore.draftText = text.slice(0, start) + payload.replacement + text.slice(end)
  draftStore.evaluatedText = draftStore.draftText
  showToast('已替换', 'success')
}

function onApplySuggestion(payload: { original: string; suggestion: string }) {
  const text = draftStore.draftText
  const resolved = resolveErrorSpan(
    { original: payload.original, span: { start: 0, end: Math.min(text.length, payload.original.length) } },
    text,
  )
  if (!resolved) {
    showToast('无法定位原文，可能已被修改', 'info')
    return
  }
  draftStore.draftText = text.slice(0, resolved.start) + payload.suggestion + text.slice(resolved.end)
  showToast('已替换', 'success')
}

function onBubbleAction(action: 'explain' | 'rewrite' | 'translate') {
  const panelMap: Record<string, import('./ToolRail.vue').PanelMode> = {
    explain: 'explain',
    rewrite: 'rewrite',
    translate: 'translate',
  }
  panelStore.activePanel = panelMap[action] ?? 'aiNote'
}

function onParagraphClick(offset: number) {
  cursorPlacement.value = { at: offset }
}

function onStartPolish() {
  panelStore.activePanel = 'rewrite'
}

function onStartGrammarCheck() {
  panelStore.activePanel = 'grammarCheck'
}


function onSubmit() {
  // ── Grammar fix gate ──
  // Exempt: exam first write (grammar panel is locked, user can't fix)
  if (!examFirstWriteLocked.value) {
    if (grammarStore.grammarChecking || grammarStore.hasUncheckedChanges) {
      showToast('语法检查进行中，请稍候', 'info')
      return
    }
    if (grammarStore.unfixedFixableCount > 0) {
      showToast('请先修正语法错误后再提交', 'error')
      panelStore.activePanel = 'grammarCheck'
      return
    }
  }

  // ── Word count gate ──
  const wordCount = draftStore.draftText.trim().split(/\s+/).filter(Boolean).length
  if (wordCount < minWordCount.value) {
    showToast(`作文至少需要 ${minWordCount.value} 个单词才能提交（当前 ${wordCount} 个）`, 'error')
    return
  }

  grammarStore.pauseForSubmit()

  const normalizedMode = draftStore.writingMode === 'exam' ? 'exam' : 'free'
  const examTaskPrompt =
    normalizedMode === 'exam' ? draftStore.taskPrompt.trim() || undefined : undefined

  wrappedEvalSubmit({
    essay: draftStore.draftText.trim(),
    aiHint: draftStore.aiNote.trim() || undefined,
    mode: normalizedMode,
    taskPrompt: examTaskPrompt,
    lang: 'en',
    documentId: draftStore.docId || undefined,
  })
}

let evalResultFromSubmit = false
const origEvalSubmit = evalSubmit
const wrappedEvalSubmit = (...args: Parameters<typeof origEvalSubmit>) => {
  evalResultFromSubmit = true
  return origEvalSubmit(...args)
}

function onDismissSelection() {
  lastDismissedPinned.value = selectedTextPinned.value
  selectionDismissed.value = true
  selectedSpanPinned.value = null
}

// ── 退出确认 ──

const showExitDialog = ref(false)

function onBack() {
  const hasContent = draftStore.draftText.trim().length > 0
  if (hasContent) {
    showExitDialog.value = true
  } else {
    doExit(false)
  }
}

async function onExitSave() {
  showExitDialog.value = false
  // 保存内容到后端
  const docId = draftStore.docId
  const content = draftStore.draftText ?? ''
  const revision = draftStore.docRevision ?? 1
  if (docId && content.trim()) {
    try {
      const { saveDocumentContent } = await import('@/api/document')
      const res = await saveDocumentContent(docId, content, revision)
      draftStore.docRevision = res.latestRevision
    } catch (e) {
      console.warn('[EditorShell] save to backend failed', e)
    }
  }
  // 清除本地草稿（后端已保存）
  draftStore.clearAll()
  grammarStore.resetAll()
  evalClearResult()
  emit('back')
}

function onExitDiscard() {
  showExitDialog.value = false
  // 只清本地草稿，后端保留上次保存的版本
  draftStore.clearAll()
  grammarStore.resetAll()
  evalClearResult()
  emit('back')
}

function onExitCancel() {
  showExitDialog.value = false
}

function doExit(clearDraft: boolean) {
  if (clearDraft) {
    draftStore.clearAll()
    grammarStore.resetAll()
    evalClearResult()
  }
  emit('back')
}

function onClear() {
  selectionState.value = null
  selectionDismissed.value = false
  selectedTextPinned.value = ''
  selectedSpanPinned.value = null
  lastDismissedPinned.value = ''
  lastChatResult.value = null
  aiDocId.value = ''
  cursorPlacement.value = null
  evalClearResult()
  activeErrorId.value = null
  draftStore.clearCurrentDraftContent()
  grammarStore.resetAll()
}

function onReplaceSelectionWith(resultText: string) {
  const span = selectedSpanPinned.value
  if (!span) {
    showToast('无选中范围，请先选中要替换的文本', 'info')
    return
  }
  const { start, end } = span
  const s = draftStore.draftText
  if (start < 0 || end > s.length || start > end) {
    showToast('Selected range is invalid', 'info')
    return
  }
  draftStore.draftText = s.slice(0, start) + resultText + s.slice(end)
  cursorPlacement.value = { at: start + resultText.length }
  lastChatResult.value = null
  showToast('已替换选中内容', 'success')
}

function getRecentAiMessages(max = 8): RecentMessageDto[] {
  return rightPanelRef.value?.getAiRecentMessages?.(max) ?? []
}

function onAiChatCleared() {
  draftStore.resetConversation()
  lastChatResult.value = null
  aiAbortController?.abort()
  aiAbortController = null
  aiGenerating.value = false
}

async function onAiNoteSend() {
  if (aiGenerating.value) return
  const instruction = draftStore.aiNote.trim()
  const selectedText = selectionStore.selectedText.value.trim()
  const hasSelectedText = Boolean(selectedText)
  const wantsDraft = rightPanelRef.value?.isIncludeDraft?.() ?? false
  const hasDraftText = Boolean(draftStore.draftText.trim())
  const normalizedMode = draftStore.writingMode === 'exam' ? 'exam' : 'free'
  const examTaskPrompt =
    normalizedMode === 'exam' ? draftStore.taskPrompt.trim() || undefined : undefined
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
    conversationId: draftStore.aiConversationId,
  })
  if (!instruction) {
    showToast('请输入需求', 'info')
    return
  }
  aiAbortController = new AbortController()
  aiGenerating.value = true
  try {
    if (!aiDocId.value && draftStore.docId) {
      aiDocId.value = draftStore.docId
    }
    if (!aiDocId.value) {
      const created = await createDocument({
        title: 'Untitled',
        content: draftStore.draftText,
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
        conversationId: draftStore.aiConversationId,
        selectedText: selectedText || undefined,
        draftText: wantsDraft ? (draftStore.draftText || undefined) : undefined,
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

/* 退出确认对话框 */
.exit-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  animation: exitFadeIn 0.15s ease;
}

@keyframes exitFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.exit-dialog {
  width: 90%;
  max-width: 400px;
  background: #fff;
  border-radius: 14px;
  padding: 28px 24px 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  animation: exitSlideUp 0.2s ease;
}

@keyframes exitSlideUp {
  from { transform: translateY(12px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.exit-title {
  font-size: 17px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 8px;
}

.exit-message {
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 20px;
  line-height: 1.5;
}

.exit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.exit-btn {
  padding: 8px 18px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 8px;
  cursor: pointer;
  border: none;
  transition: all 0.15s;
}

.exit-btn-cancel {
  color: #6b7280;
  background: #f3f4f6;
}
.exit-btn-cancel:hover {
  background: #e5e7eb;
}

.exit-btn-discard {
  color: #ef4444;
  background: #fef2f2;
}
.exit-btn-discard:hover {
  background: #fee2e2;
}

.exit-btn-save {
  color: #fff;
  background: #047857;
}
.exit-btn-save:hover {
  background: #065f46;
}
</style>






