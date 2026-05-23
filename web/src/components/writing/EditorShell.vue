<template>
  <div class="writing-root">
    <ToolRail
      class="toolrail-fixed-overlay"
      :active-panel="panelStore.activePanel"
      :show-task-prompt="taskPromptViewerState.visible"
      @select="panelStore.selectPanel"
    />
    <div class="workspace-layout" :style="panelStore.layoutStyle">
      <div ref="leftPaneRef" class="left-pane">
        <DocEditor
          v-model:draft-text="draftStore.draftText"
          :correction-mode="false"
          :submitting="evaluateStore.submitting"
          :writing-mode="draftStore.writingMode"
          :cursor-placement="cursorPlacement"
          :selection-capture-enabled="true"
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
        >
          <template #toolbar-extra-actions="{ closeToolbar }">
            <div class="toolbar-extra-separator" />
            <button
              type="button"
              class="toolbar-extra-button"
              @click="openHandwritingImport(closeToolbar)"
            >
              上传手写作文
            </button>
          </template>
        </DocEditor>
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
          :doc-id="draftStore.docId"
          :document-title="archiveDocumentTitle"
          :document-archived="draftStore.archived"
          :archive-busy="archiveBusy"
          :selection-state="selectionState"
          :selection-dismissed="selectionDismissed"
          :selected-text-pinned="selectedTextPinned"
          :selected-span-pinned="selectedSpanPinned"
          :last-chat-result="lastChatResult"
          :conversation-id="draftStore.aiConversationId"
          :ai-generating="aiGenerating"
          :writing-mode="draftStore.writingMode"
          :ai-provider="draftStore.aiProvider"
          :study-stage="props.studyStage"
          :topic-content="effectiveExamTopicContent"
          :task-prompt="effectiveExamTaskPrompt"
          :attachment-image-url="sessionMetadata?.attachmentImageUrl ?? null"
          :ai-note="draftStore.aiNote"
          :evaluate-result="evaluateStore.evaluateResult"
          :active-error-id="activeErrorId"
          :submitting="evaluateStore.submitting"
          :evaluate-error="evaluateStore.evaluateError"
          :exam-max-score="props.examMaxScore"
          :task-type="sessionMetadata?.taskType ?? null"
          :min-words="effectiveExamMinWords"
          :recommended-max-words="effectiveExamRecommendedMaxWords"
          :grammar-errors="grammarStore.grammarPanelErrors"
          :grammar-suggestions="grammarStore.grammarPanelSuggestions"
          :grammar-checking="grammarStore.grammarChecking"
          :grammar-check-error="grammarStore.grammarCheckError"
          :grammar-fixed-error-ids="grammarStore.grammarPanelFixedIds"
          :grammar-trinka-mode="grammarStore.trinkaMode"
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
          @grammar-dismiss-error="grammarStore.dismissError"
          @grammar-trinka-mode-change="grammarStore.setTrinkaMode"
          @clear-trusted-rewrites="grammarStore.clearTrustedRewrites"
          @apply-suggestion="onApplySuggestion"
          @gpt-errors-loaded="grammarStore.setGptErrors"
          @gpt-suggestions-loaded="grammarStore.setGptSuggestions"
          @retry="onSubmit"
          @paragraph-click="onParagraphClick"
          @start-grammar-check="onStartGrammarCheck"
          @dismiss-selection="onDismissSelection"
          @replace-selection-with="onReplaceSelectionWith"
          @writing-coach-apply="onWritingCoachApply"
          @writing-coach-edit-action="onWritingCoachEditAction"
          @update:ai-note="draftStore.aiNote = $event"
          @update:ai-provider="onAiProviderChange"
          @update:writing-mode="draftStore.writingMode = $event"
          @update:task-prompt="draftStore.taskPrompt = $event"
          @archive-document="onArchiveDocument"
          @unarchive-document="onUnarchiveDocument"
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

    <HandwritingImportDialog
      v-model="showHandwritingImportDialog"
      :current-text="draftStore.draftText"
      :ai-provider="draftStore.aiProvider"
      @confirm="onHandwritingImportConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick, provide } from 'vue'
import { useEventListener } from '@vueuse/core'

const emit = defineEmits<{
  back: []
  'archive-status-change': [payload: { docId: string; archived: boolean }]
}>()

const props = withDefaults(defineProps<{
  initialWritingMode?: 'free' | 'exam'
  initialTaskPrompt?: string
  initialDocId?: string | null
  initialExistingContent?: string | null
  examMaxScore?: number | null
  initialSubmitCount?: number
  initialArchived?: boolean
  initialTitle?: string
  studyStage?: string | null
}>(), {
  initialWritingMode: undefined,
  initialTaskPrompt: undefined,
  initialDocId: null,
  initialExistingContent: null,
  examMaxScore: null,
  initialSubmitCount: 0,
  initialArchived: false,
  initialTitle: '',
  studyStage: null,
})

import DocEditor from './DocEditor.vue'
import HandwritingImportDialog from './HandwritingImportDialog.vue'
import RightPanel from './RightPanel.vue'
import ToolRail from './ToolRail.vue'
import Splitter from './Splitter.vue'
import { useEvaluateSubmission } from '@/composables/useEvaluateSubmission'
import { assistantApi, assistantChatStream } from '@/api/assistant'
import type { AssistantWritingCoachContext } from '@/api/assistant'
import type { WritingCoachEditAction, WritingPatch } from '@/types/assistantRequest'
import { archiveDocument, createDocument, saveDocumentContent, unarchiveDocument } from '@/api/document'
import { showToast } from '@/utils/toast'
import { createWritingSelectionStore, writingSelectionStoreKey } from './useWritingSelectionStore'
import { resolveErrorSpan, findClosestMatch, shouldUseWordBoundary } from './errorSpanResolver'
import { WRITING_STORAGE_KEYS } from './editorShellStorage'
import { usePanelStore, MIN_PANEL_WIDTH, MAX_PANEL_WIDTH, MIN_LEFT_WIDTH } from '@/stores/panelStore'
import { useWritingDraftStore } from '@/stores/writingDraftStore'
import { useGrammarStore } from '@/stores/grammarStore'
import { useEvaluateStore } from '@/stores/evaluateStore'
import { stageCache } from '@/stores/stageCache'
import { getActiveRubric, getStageConfig, getWritingSessionMetadata, rewriteApply } from '@/api/writing'
import type {
  PolishTier,
  WritingAiProvider,
  WritingSessionMetadataResponse,
} from '@/api/writing'
import { resolveTaskPromptViewerState } from './taskPromptViewerState'
import { applyWritingPatch } from './writingPatchApplicator'

const panelStore = usePanelStore()
const draftStore = useWritingDraftStore()
const grammarStore = useGrammarStore()
const evaluateStore = useEvaluateStore()
const minWordCount = ref(60)
const sessionMetadata = ref<WritingSessionMetadataResponse | null>(null)
const aiProviderLabels: Record<WritingAiProvider, string> = {
  openai: 'OpenAI',
  kimi: 'Kimi',
  qwen: '千问',
}

type RecentMessageDto = { role: 'user' | 'assistant'; content: string }
type WritingCoachToolDto = { key: string; label: string; prompt: string }

const leftPaneRef = ref<HTMLElement | null>(null)
const rightPanelRef = ref<{
  setAiComposerText?: (text: string) => boolean
  focusAiComposer: () => boolean
  getAiRecentMessages?: (max?: number) => RecentMessageDto[]
  isIncludeDraft?: () => boolean
  getAiSelectedTool?: () => WritingCoachToolDto
} | null>(null)
const selectionState = ref<{ text: string; start: number; end: number } | null>(null)
const selectionDismissed = ref(false)
const selectedTextPinned = ref('')
const selectedSpanPinned = ref<{ start: number; end: number } | null>(null)
const lastDismissedPinned = ref('')
const lastChatResult = ref<{ displayText: string; replaceText?: string } | null>(null)
const aiDocId = ref('')
const cursorPlacement = ref<{ at: number } | null>(null)
const showHandwritingImportDialog = ref(false)
const archiveBusy = ref(false)
const aiGenerating = ref(false)
let aiAbortController: AbortController | null = null
const {
  submit: evalSubmit,
  cancel: evalCancel,
  clearResult: composableClearResult,
  evaluateResult: composableEvalResult,
  evaluateError: composableEvalError,
  submitting: composableSubmitting,
} = useEvaluateSubmission()
const activeErrorId = ref<string | null>(null)
const sentenceHighlightRange = ref<{ start: number; end: number } | null>(null)
const examFirstWriteLocked = computed(() =>
  draftStore.writingMode === 'exam' && draftStore.submitCount === 0,
)
const effectiveExamTaskPrompt = computed(() => {
  const draftPrompt = draftStore.taskPrompt?.trim()
  if (draftPrompt) return draftPrompt
  const metadataPrompt = sessionMetadata.value?.promptText?.trim()
  if (metadataPrompt) return metadataPrompt
  const initialPrompt = props.initialTaskPrompt?.trim()
  return initialPrompt || ''
})
const effectiveExamTopicContent = computed(() => {
  const metadataTopicContent = effectiveExamPromptMetadata.value?.topicContent?.trim()
  if (metadataTopicContent) return metadataTopicContent
  const metadataTopic = sessionMetadata.value?.topicTitle?.trim()
  if (metadataTopic) return metadataTopic
  const parsed = effectiveExamTaskPrompt.value ? parseExamPromptMetadata(effectiveExamTaskPrompt.value) : null
  return parsed?.topicContent?.trim() || parsed?.topicTitle?.trim() || ''
})
const archiveDocumentTitle = computed(() => {
  const title = draftStore.title?.trim()
  if (title) return title
  const topic = effectiveExamTopicContent.value.trim()
  if (topic) return topic
  return draftStore.taskPrompt.trim().split(/\n/)[0] || '未命名作文'
})
const effectiveExamPromptMetadata = computed(() => {
  const prompt = effectiveExamTaskPrompt.value
  if (!prompt) return null
  return parseExamPromptMetadata(prompt)
})
const effectiveExamMinWords = computed(() =>
  sessionMetadata.value?.minWords ?? effectiveExamPromptMetadata.value?.minWords ?? null,
)
const effectiveExamRecommendedMaxWords = computed(() =>
  sessionMetadata.value?.recommendedMaxWords ?? effectiveExamPromptMetadata.value?.recommendedMaxWords ?? null,
)
const taskPromptViewerState = computed(() =>
  resolveTaskPromptViewerState({
    writingMode: draftStore.writingMode,
    taskPrompt: effectiveExamTaskPrompt.value,
    activePanel: panelStore.activePanel,
  }),
)

const selectionStore = createWritingSelectionStore()
provide(writingSelectionStoreKey, selectionStore)

function normalizeEvaluateSnapshot(text?: string | null) {
  return (text ?? '').replace(/\s+/g, ' ').trim()
}

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
  const preloadedDocId = props.initialDocId?.trim()
    || sessionStorage.getItem('peai:writing:docId')?.trim()
    || null

  // 重置旧文档状态，防止残留评价/语法数据
  grammarStore.resetAll()
  evaluateStore.resetAll()
  panelStore.activePanel = null

  const layout = panelStore.initLayout()

  // ── Single-channel hydration ──
  if (preloadedDocId) {
    await draftStore.hydrateByDocId(preloadedDocId)
  } else {
    draftStore.init({
      initialWritingMode: props.initialWritingMode,
      initialTaskPrompt: props.initialTaskPrompt,
      initialDocId: props.initialDocId,
      initialTitle: props.initialTitle,
      initialSubmitCount: props.initialSubmitCount,
      initialArchived: props.initialArchived,
    })
    if (props.initialExistingContent && (!draftStore.draftText || !draftStore.draftText.trim())) {
      draftStore.draftText = props.initialExistingContent
    }
  }

  aiDocId.value = draftStore.docId ?? ''

  if (draftStore.docId) {
    getWritingSessionMetadata(draftStore.docId)
      .then((meta) => {
        sessionMetadata.value = meta
        if (!draftStore.taskPrompt.trim() && meta.promptText?.trim()) {
          draftStore.taskPrompt = meta.promptText.trim()
        }
      })
      .catch(() => { sessionMetadata.value = null })
  } else {
    sessionMetadata.value = null
  }

  // Restore evaluate state from sessionStorage (single source of truth)
  evaluateStore.docScope = draftStore.docId
  evaluateStore.restore(draftStore.docId)
  if (evaluateStore.evaluateResult && layout.activePanel === 'score') {
    panelStore.activePanel = 'score'
  }

  // Restore grammar cache, but do not reuse stale evaluate errors as grammar source.
  grammarStore.clearEvaluateErrorSource()
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
  if (evaluateStore.evaluateResult) {
    evaluateStore.evaluatedText = normalizeEvaluateSnapshot(draftStore.draftText)
  }

  if (!examFirstWriteLocked.value && draftStore.draftText.trim().length >= 10) {
    grammarStore.scheduleGrammarCheck()
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
// Flush debounced draft on refresh/close to avoid losing the last buffered edits
useEventListener(window, 'beforeunload', () => draftStore.flushAll())
useEventListener(window, 'pagehide', () => draftStore.flushAll())
useEventListener(document, 'visibilitychange', () => {
  if (document.visibilityState === 'hidden') {
    draftStore.flushAll()
  }
})

onBeforeUnmount(() => {
  evalCancel()
  grammarStore.destroy()
  // Do NOT reset evaluateStore here — it should survive component remount for refresh
})

watch(() => panelStore.activePanel, (newPanel, oldPanel) => {
  if (oldPanel === 'rewrite' && newPanel !== 'rewrite') {
    sentenceHighlightRange.value = null
  }
  panelStore.saveState()
}, { flush: 'post' })

watch(() => effectiveExamTaskPrompt.value, (taskPrompt) => {
  if (!taskPrompt.trim() && panelStore.activePanel === 'taskPrompt') {
    panelStore.activePanel = null
  }
})

// Sync composable → evaluateStore (single source of truth)
watch(composableEvalResult, (result) => {
  evaluateStore.setResult(result)
  if (result) {
    grammarStore.useEvaluateErrorsForPanels()
    evaluateStore.evaluatedText = normalizeEvaluateSnapshot(draftStore.draftText)
    if (evaluateStore.resultFromSubmit) {
      draftStore.submitCount++
      panelStore.activePanel = 'score'
      showToast('评估完成', 'success')
      evaluateStore.resultFromSubmit = false
    }
  } else {
    activeErrorId.value = null
  }
})

watch(composableEvalError, (err) => {
  evaluateStore.evaluateError = err
  if (err && evaluateStore.resultFromSubmit) {
    showToast(err, 'error')
    evaluateStore.resultFromSubmit = false
    grammarStore.grammarCheckActive = true
  }
})

watch(composableSubmitting, (val) => {
  evaluateStore.submitting = val
})

watch(() => draftStore.draftText, (newText) => {
  if (draftStore.isHydrating) return
  if (
    evaluateStore.evaluateResult
    && evaluateStore.evaluatedText !== null
    && normalizeEvaluateSnapshot(newText) !== normalizeEvaluateSnapshot(evaluateStore.evaluatedText)
  ) {
    composableClearResult()
    evaluateStore.clearResult()
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
  const errors = evaluateStore.evaluateResult?.errors
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
  evaluateStore.evaluatedText = normalizeEvaluateSnapshot(draftStore.draftText)
  showToast('已替换', 'success')
}

async function onReplaceSentence(payload: { start: number; end: number; original: string; replacement: string; tier: PolishTier }) {
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

  let trustedRecordApplied = false
  if ((payload.tier === 'advanced' || payload.tier === 'perfect') && draftStore.docId) {
    try {
      const rewrite = await rewriteApply({
        docId: draftStore.docId,
        essay: text,
        start,
        end,
        original: payload.original,
        replacement: payload.replacement,
        tier: payload.tier,
      })
      if (rewrite.trusted && rewrite.record) {
        grammarStore.registerTrustedRewrite(rewrite.record)
        trustedRecordApplied = true
      }
    } catch {
      // Fallback to plain replace; trust state can be rebuilt on next successful apply.
    }
  }

  draftStore.draftText = text.slice(0, start) + payload.replacement + text.slice(end)
  evaluateStore.evaluatedText = normalizeEvaluateSnapshot(draftStore.draftText)
  showToast(trustedRecordApplied ? '已替换，并登记为进阶润色句' : '已替换', 'success')
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

type EditorBubbleAction =
  | 'explain'
  | 'rewrite'
  | 'translate'
  | 'topic_check'
  | 'polish_expression'
  | 'expand_segment'
  | 'simplify_segment'
  | 'replace_suggestion'

function onBubbleAction(action: EditorBubbleAction) {
  if (action === 'explain' || action === 'rewrite' || action === 'translate') {
    const panelMap: Record<typeof action, import('./ToolRail.vue').PanelMode> = {
      explain: 'explain',
      rewrite: 'rewrite',
      translate: 'translate',
    }
    panelStore.activePanel = panelMap[action]
    return
  }

  panelStore.activePanel = 'aiNote'
  const selectedText = selectionState.value?.text?.trim() || selectionStore.selectedText.value.trim()
  const instructionMap: Record<Exclude<EditorBubbleAction, 'explain' | 'rewrite' | 'translate'>, string> = {
    topic_check: `请检查这段内容是否偏题，并说明它是否服务于题目中心任务：\n\n${selectedText}`,
    polish_expression: `请在不改变原意和题目方向的前提下，润色这段表达：\n\n${selectedText}`,
    expand_segment: `请扩写这段内容，让理由或例子更充分，但不要偏离题目：\n\n${selectedText}`,
    simplify_segment: `请把这段内容改得更适合当前学段，表达更清楚，不要改变原意：\n\n${selectedText}`,
    replace_suggestion: `请给出一版可以替换当前选区的建议，并说明为什么更切题：\n\n${selectedText}`,
  }
  nextTick(() => {
    rightPanelRef.value?.setAiComposerText?.(instructionMap[action])
    rightPanelRef.value?.focusAiComposer()
  })
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


function parseExamPromptMetadata(taskPrompt: string) {
  const lines = taskPrompt
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)

  const metadataPrefixes = [
    "题目要求（润色后必须继续严格对齐）：",
    "图画信息：",
    "材料信息：",
    "体裁：",
    "字数要求：",
    "写作要求：",
    "满分分值：",
  ]

  const findField = (prefix: string) => {
    const line = lines.find((item) => item.startsWith(prefix))
    return line ? line.slice(prefix.length).trim() : null
  }

  const topicLabelIndex = lines.findIndex((line) => line === "题目要求（润色后必须继续严格对齐）：")
  const explicitTopic =
    topicLabelIndex >= 0 && topicLabelIndex + 1 < lines.length ? lines[topicLabelIndex + 1] : null
  const topicTitle = explicitTopic
    || (lines.find((line) => !metadataPrefixes.some((prefix) => line.startsWith(prefix))) ?? null)
  const imageDescription = findField("图画信息：")
  const materialText = findField("材料信息：")
  const genre = findField("体裁：")
  const wordRange = findField("字数要求：")?.replace(/词$/u, "") ?? null

  let minWords: number | null = null
  let recommendedMaxWords: number | null = null
  if (wordRange) {
    const compact = wordRange.replace(/\s+/g, "")
    const rangeMatch = compact.match(/^(\d+)[-~至](\d+)$/)
    const singleMatch = compact.match(/^(\d+)$/)
    if (rangeMatch) {
      minWords = Number(rangeMatch[1])
      recommendedMaxWords = Number(rangeMatch[2])
    } else if (singleMatch) {
      minWords = Number(singleMatch[1])
      recommendedMaxWords = Number(singleMatch[1])
    }
  }

  return {
    topicTitle,
    topicContent: imageDescription || materialText || topicTitle,
    imageDescription,
    materialText,
    genre,
    minWords,
    recommendedMaxWords,
  }
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
    normalizedMode === 'exam' ? effectiveExamTaskPrompt.value || undefined : undefined
  const parsedExamMetadata = examTaskPrompt ? parseExamPromptMetadata(examTaskPrompt) : null

  wrappedEvalSubmit({
    essay: draftStore.draftText.trim(),
    aiHint: draftStore.aiNote.trim() || undefined,
    aiProvider: draftStore.aiProvider,
    mode: normalizedMode,
    taskPrompt: examTaskPrompt,
    lang: 'en',
    documentId: draftStore.docId || undefined,
    studyStage: normalizedMode === 'exam' ? (sessionMetadata.value?.studyStage ?? props.studyStage ?? undefined) : undefined,
    topicTitle: normalizedMode === 'exam' ? (sessionMetadata.value?.topicTitle ?? parsedExamMetadata?.topicTitle ?? undefined) : undefined,
    genre: normalizedMode === 'exam' ? (sessionMetadata.value?.genre ?? parsedExamMetadata?.genre ?? undefined) : undefined,
    examType: normalizedMode === 'exam' ? (sessionMetadata.value?.examType ?? props.studyStage ?? undefined) : undefined,
    taskType: normalizedMode === 'exam' ? (sessionMetadata.value?.taskType ?? undefined) : undefined,
    minWords: normalizedMode === 'exam' ? (sessionMetadata.value?.minWords ?? parsedExamMetadata?.minWords ?? undefined) : undefined,
    recommendedMaxWords: normalizedMode === 'exam' ? (sessionMetadata.value?.recommendedMaxWords ?? parsedExamMetadata?.recommendedMaxWords ?? undefined) : undefined,
    maxScore: normalizedMode === 'exam' ? (sessionMetadata.value?.maxScore ?? props.examMaxScore ?? undefined) : undefined,
  })
}

const wrappedEvalSubmit = (...args: Parameters<typeof evalSubmit>) => {
  evaluateStore.resultFromSubmit = true
  return evalSubmit(...args)
}

function onDismissSelection() {
  lastDismissedPinned.value = selectedTextPinned.value
  selectionDismissed.value = true
  selectedSpanPinned.value = null
}

function openHandwritingImport(closeToolbar?: () => void) {
  closeToolbar?.()
  showHandwritingImportDialog.value = true
}

function onHandwritingImportConfirm(payload: {
  mode: 'replace' | 'append'
  combinedText: string
}) {
  draftStore.draftText = payload.combinedText
  cursorPlacement.value = { at: payload.combinedText.length }
  showToast(
    payload.mode === 'append' ? '已追加手写识别内容' : '已替换为手写识别内容',
    'success',
  )
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

async function saveCurrentDocumentContent() {
  const docId = draftStore.docId
  const content = draftStore.draftText ?? ''
  const revision = draftStore.docRevision ?? 1
  if (docId && content.trim()) {
    const res = await saveDocumentContent(docId, content, revision)
    draftStore.docRevision = res.latestRevision
  }
}

async function onArchiveDocument() {
  if (!draftStore.docId) {
    showToast('当前作文尚未创建文档，无法归档', 'info')
    return
  }
  archiveBusy.value = true
  try {
    await saveCurrentDocumentContent()
    await archiveDocument(draftStore.docId)
    draftStore.archived = true
    emit('archive-status-change', { docId: draftStore.docId, archived: true })
    showToast('已归档到作文资产', 'success')
  } catch (e) {
    console.warn('[EditorShell] archive document failed', e)
    showToast('归档失败，请稍后重试', 'error')
  } finally {
    archiveBusy.value = false
  }
}

async function onUnarchiveDocument() {
  if (!draftStore.docId) return
  archiveBusy.value = true
  try {
    await unarchiveDocument(draftStore.docId)
    draftStore.archived = false
    emit('archive-status-change', { docId: draftStore.docId, archived: false })
    showToast('已取消归档', 'success')
  } catch (e) {
    console.warn('[EditorShell] unarchive document failed', e)
    showToast('取消归档失败，请稍后重试', 'error')
  } finally {
    archiveBusy.value = false
  }
}

async function onExitSave() {
  showExitDialog.value = false
  try {
    await saveCurrentDocumentContent()
  } catch (e) {
    console.warn('[EditorShell] save to backend failed', e)
  }

  const scope = draftStore.docId
  evalCancel()
  composableClearResult()
  grammarStore.resetAll()
  evaluateStore.resetAll()
  void grammarStore.clearTrustedRewrites()
  grammarStore.clearAllCaches(scope)
  // 保存退出：保留评价结果缓存，下次进入同一文档时可恢复
  // 清除本地草稿（后端已保存）
  draftStore.clearAll()
  emit('back')
}

function onExitDiscard() {
  showExitDialog.value = false
  const scope = draftStore.docId
  evalCancel()
  composableClearResult()
  grammarStore.resetAll()
  evaluateStore.resetAll()
  void grammarStore.clearTrustedRewrites()
  grammarStore.clearAllCaches(scope)
  // 放弃退出：保留评价结果和 recentFixes，只丢弃本次未保存的修改
  // 只清本地草稿，后端保留上次保存的版本
  draftStore.clearAll()
  emit('back')
}

function onExitCancel() {
  showExitDialog.value = false
}

function doExit(clearDraft: boolean) {
  if (clearDraft) {
    const scope = draftStore.docId
    evalCancel()
    composableClearResult()
    grammarStore.resetAll()
    evaluateStore.resetAll()
    void grammarStore.clearTrustedRewrites()
    grammarStore.clearAllCaches(scope)
    evaluateStore.clearAllCaches(scope)
    draftStore.clearAll()
  }
  emit('back')
}

function onClear() {
  const scope = draftStore.docId
  selectionState.value = null
  selectionDismissed.value = false
  selectedTextPinned.value = ''
  selectedSpanPinned.value = null
  lastDismissedPinned.value = ''
  lastChatResult.value = null
  aiDocId.value = ''
  cursorPlacement.value = null

  evalCancel()
  composableClearResult()
  evaluateStore.resetAll()
  evaluateStore.clearAllCaches(scope)
  activeErrorId.value = null
  draftStore.clearCurrentDraftContent()
  grammarStore.resetAll()
  void grammarStore.clearTrustedRewrites()
  grammarStore.clearAllCaches(scope)
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

function onWritingCoachApply(payload: { type: 'replace_selection' | 'append_text' | 'replace_all'; text: string }) {
  const text = payload.text.trim()
  if (!text) {
    showToast('建议内容为空', 'info')
    return
  }
  if (payload.type === 'replace_selection') {
    onReplaceSelectionWith(text)
    return
  }
  if (payload.type === 'append_text') {
    const current = draftStore.draftText
    const separator = current.trim() ? '\n\n' : ''
    draftStore.draftText = `${current}${separator}${text}`
    cursorPlacement.value = { at: draftStore.draftText.length }
    showToast('已追加到正文末尾', 'success')
    return
  }
  const confirmed = window.confirm('替换全文会覆盖当前正文，确定应用这版终稿吗？')
  if (!confirmed) return
  draftStore.draftText = text
  cursorPlacement.value = { at: text.length }
  showToast('已替换全文', 'success')
}

function onWritingCoachEditAction(action: WritingCoachEditAction) {
  const patch = action.patch ?? legacyEditActionToPatch(action)
  if (!patch) {
    showToast('建议内容为空', 'info')
    return
  }

  if (patch.op === 'replace_document') {
    const confirmed = window.confirm('替换全文会覆盖当前正文，确定应用这版终稿吗？')
    if (!confirmed) return
  }

  const result = applyWritingPatch(draftStore.draftText, patch)
  if (result.status === 'success') {
    draftStore.draftText = result.nextText
    cursorPlacement.value = { at: result.cursorAt }
    lastChatResult.value = null
    showToast(`已${result.preview.operationLabel}`, 'success')
  } else if (result.status === 'ambiguous') {
    showToast(result.message, 'info')
  } else {
    showToast(result.message, result.status === 'duplicate' ? 'info' : 'error')
  }
}

function legacyEditActionToPatch(action: WritingCoachEditAction): WritingPatch | null {
  const text = action.text.trim()
  if (!text) return null

  if (action.type === 'append_paragraph') {
    return {
      op: 'append_paragraph',
      text,
      reason: action.reason,
    }
  }

  const selectedText = action.target?.selectedText?.trim() || selectedTextPinned.value.trim()
  if (action.type === 'replace_selection' && action.target?.range) {
    return {
      op: 'replace_selection',
      range: action.target.range,
      originalText: selectedText,
      newText: text,
      reason: action.reason,
    }
  }

  if (action.type === 'insert_after_selection' && selectedText) {
    return {
      op: 'insert_after_anchor',
      anchorText: selectedText,
      insertText: text,
      reason: action.reason,
    }
  }

  return {
    op: 'append_paragraph',
    text,
    reason: action.reason,
  }
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

function onAiProviderChange(provider: WritingAiProvider) {
  if (draftStore.aiProvider === provider) return
  draftStore.setAiProvider(provider)
  lastChatResult.value = null
  aiAbortController?.abort()
  aiAbortController = null
  aiGenerating.value = false
  showToast(`已切换到 ${aiProviderLabels[provider] ?? provider}`, 'success')
}

async function ensureWritingAssistantConversation(): Promise<string> {
  const current = draftStore.aiConversationId.trim()
  if (current.startsWith('conv-')) return current
  const title = draftStore.writingMode === 'exam' ? '写作教练：考试写作' : '写作教练：自由写作'
  const conversation = await assistantApi.createConversation({ title })
  draftStore.aiConversationId = conversation.id
  return conversation.id
}

function buildWritingCoachPrompt(options: {
  instruction: string
  selectedTool: WritingCoachToolDto
  includeDraft: boolean
  taskPrompt?: string
  selectedText?: string
  recentMessages: RecentMessageDto[]
}) {
  const lines: string[] = [
    '[写作教练 Copilot 请求]',
    `- 入口: writing_copilot`,
    `- 当前能力: ${options.selectedTool.label} (${options.selectedTool.key})`,
    `- 写作模式: ${draftStore.writingMode === 'exam' ? '考试写作' : '自由写作'}`,
    `- 学段/目标: ${props.studyStage || '未指定'}`,
  ]
  if (options.taskPrompt) {
    lines.push('', '[作文题目]', truncateForAssistant(options.taskPrompt, 1200))
  }
  if (options.selectedText) {
    lines.push('', '[用户当前选区]', truncateForAssistant(options.selectedText, 1200))
  }
  if (options.includeDraft && draftStore.draftText.trim()) {
    lines.push('', '[当前作文全文]', truncateForAssistant(draftStore.draftText, 6000))
  }
  if (options.recentMessages.length > 0) {
    lines.push('', '[写作教练面板近期对话]')
    for (const message of options.recentMessages) {
      lines.push(`${message.role === 'user' ? '用户' : '教练'}: ${truncateForAssistant(message.content, 600)}`)
    }
  }
  lines.push('', '[用户本轮问题]', options.instruction)
  return lines.join('\n')
}

function truncateForAssistant(text: string, max: number): string {
  const normalized = text.trim()
  if (normalized.length <= max) return normalized
  return `${normalized.slice(0, max).trimEnd()}\n...[已截断 ${normalized.length - max} 字]`
}

function hasPriorTopicAnalysis(messages: RecentMessageDto[]): boolean {
  return messages.some((message) =>
    message.role === 'assistant'
    && /题目主旨|中心任务|必答点|偏题风险/.test(message.content),
  )
}

function buildWritingCoachContext(options: {
  selectedTool: WritingCoachToolDto
  includeDraft: boolean
  taskPrompt?: string
  selectedText?: string
  recentMessages: RecentMessageDto[]
  rubric?: NonNullable<AssistantWritingCoachContext['rubric']>
}): AssistantWritingCoachContext {
  const metadata = options.taskPrompt ? parseExamPromptMetadata(options.taskPrompt) : null
  const questionMaterials = [metadata?.imageDescription, metadata?.materialText].filter(Boolean).join('\n')
  const imageDescriptions = [metadata?.imageDescription].filter((value): value is string => Boolean(value?.trim()))
  const includeDraftText = options.includeDraft && draftStore.draftText.trim()
  return {
    schemaVersion: 'writing_coach_input_v1',
    action: options.selectedTool.key as AssistantWritingCoachContext['action'],
    writingMode: draftStore.writingMode === 'exam' ? 'exam' : 'free',
    studyStage: props.studyStage ?? null,
    taskType: sessionMetadata.value?.taskType ?? null,
    essayQuestion: options.taskPrompt ?? metadata?.topicTitle ?? null,
    questionMaterials: questionMaterials || metadata?.topicContent || null,
    imageDescriptions,
    essayGenre: sessionMetadata.value?.genre ?? metadata?.genre ?? null,
    minWords: effectiveExamMinWords.value,
    maxWords: effectiveExamRecommendedMaxWords.value,
    draftText: includeDraftText ? draftStore.draftText : null,
    selectedText: options.selectedText ?? null,
    includeDraft: options.includeDraft,
    topicAnalysisDone: hasPriorTopicAnalysis(options.recentMessages),
    rubric: options.rubric ?? {
      rubricKey: '',
      rubricVersion: '',
      rubricText: '',
      rubricFocus: [],
    },
  }
}

async function resolveWritingCoachRubricContext(
  mode: 'free' | 'exam',
): Promise<NonNullable<AssistantWritingCoachContext['rubric']>> {
  try {
    const rubric = await getActiveRubric({
      stage: props.studyStage ?? undefined,
      mode,
    })
    const rubricFocus = rubric.dimensions
      .map((dimension) => dimension.display_name || dimension.dimension_key)
      .filter((value) => value.trim().length > 0)
    const rubricText = rubric.dimensions
      .map((dimension) => {
        const criteria = dimension.levels
          .map((level) => `${level.level}: ${level.criteria}`)
          .filter(Boolean)
          .slice(0, 2)
          .join(' / ')
        return criteria ? `${dimension.display_name || dimension.dimension_key}: ${criteria}` : ''
      })
      .filter(Boolean)
      .slice(0, 4)
      .join('\n')
    return {
      rubricKey: rubric.rubric_key,
      rubricVersion: '',
      rubricFocus,
      rubricText,
    }
  } catch (error) {
    console.warn('[WritingCoach] load active rubric failed', error)
    return {
      rubricKey: '',
      rubricVersion: '',
      rubricText: '',
      rubricFocus: [],
    }
  }
}

async function onAiNoteSend() {
  if (aiGenerating.value) return
  const instruction = draftStore.aiNote.trim()
  const selectedText = selectionStore.selectedText.value.trim()
  const hasSelectedText = Boolean(selectedText)
  const wantsDraft = rightPanelRef.value?.isIncludeDraft?.() ?? false
  const selectedTool = rightPanelRef.value?.getAiSelectedTool?.() ?? { key: 'coach', label: '写作教练', prompt: '' }
  const hasDraftText = Boolean(draftStore.draftText.trim())
  const normalizedMode = draftStore.writingMode === 'exam' ? 'exam' : 'free'
  const examTaskPrompt =
    normalizedMode === 'exam' ? effectiveExamTaskPrompt.value || undefined : undefined
  const contextScope = hasSelectedText ? 'selection_and_message' : 'message_only'
  const recentMessages = getRecentAiMessages(8)
  console.log('[AI SEND]', {
    docId: aiDocId.value,
    instruction,
    selectedTool,
    hasSelectedText,
    hasDraftText,
    wantsDraft,
    writingMode: normalizedMode,
    hasTaskPrompt: Boolean(examTaskPrompt),
    contextScope,
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

    const conversationId = await ensureWritingAssistantConversation()
    const prompt = buildWritingCoachPrompt({
      instruction,
      selectedTool,
      includeDraft: wantsDraft,
      taskPrompt: examTaskPrompt,
      selectedText: selectedText || undefined,
      recentMessages,
    })
    const rubric = await resolveWritingCoachRubricContext(normalizedMode)
    const writingCoachContext = buildWritingCoachContext({
      selectedTool,
      includeDraft: wantsDraft,
      taskPrompt: examTaskPrompt,
      selectedText: selectedText || undefined,
      recentMessages,
      rubric,
    })
    const res = await assistantChatStream(
      {
        input: prompt,
        conversationId,
        studyStage: props.studyStage ?? undefined,
        assistantMode: normalizedMode === 'exam' ? 'exam' : 'default',
        intent: 'first_draft_coach',
        scope: contextScope,
        selection: hasSelectedText
          ? {
              text: selectedText,
              source: 'writing_editor',
              documentId: aiDocId.value,
              range: selectedSpanPinned.value
                ? { start: selectedSpanPinned.value.start, end: selectedSpanPinned.value.end }
                : undefined,
            }
          : undefined,
        writingCoachContext,
        attachments: [],
      },
      {},
      { signal: aiAbortController.signal },
    )

    if (res.reply.trim() !== '') {
      lastChatResult.value = {
        displayText: res.reply,
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
  display: flex;
  flex-direction: column;
}
.workspace-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) var(--splitter-width) var(--rightWidth);
  min-width: 0;
  flex: 1 1 auto;
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



