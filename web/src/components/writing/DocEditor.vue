<template>
  <div class="doc-editor">
    <div class="doc-canvas">
      <header class="doc-header">
        <button type="button" class="back-link" title="返回总览" @click="emit('back')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        <span class="doc-title">{{ docTitle }}</span>
        <div class="doc-actions">
          <span class="word-count">{{ wordCount }} 词</span>
          <button type="button" class="btn btn-secondary" :disabled="submitting" @click="clear">
            清空
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="!hasContent || correctionMode || submitting"
            @click="submit"
          >
            {{ submitting ? '提交中...' : '提交' }}
          </button>
        </div>
      </header>
      <div
        ref="editorEl"
        class="doc-content"
        :class="{ empty: !draftText.trim() && !correctionMode && !hasErrors, locked: correctionMode }"
        :contenteditable="!correctionMode"
        data-placeholder="在此输入英文作文..."
        @input="onInput"
        @paste="onPaste"
        @click="onEditorClick"
      />
    </div>

    <!-- 行内替换弹窗 -->
    <Teleport to="body">
      <div
        v-if="popup.visible"
        ref="popupEl"
        class="inline-fix-popup"
        :style="{ top: popup.top + 'px', left: popup.left + 'px' }"
        @mousedown.stop
      >
        <div v-if="popup.suggestion" class="popup-suggestion" @click="onPopupApply">
          <span class="popup-label">{{ popupTypeLabel }}</span>
          <span class="popup-replacement">{{ popup.suggestion }}</span>
        </div>
        <div v-if="popup.isDeletion" class="popup-suggestion popup-deletion" @click="onPopupApply">
          <span class="popup-label">{{ popupTypeLabel }}</span>
          <span class="popup-replacement deletion-text">删除此处</span>
        </div>
        <div class="popup-dismiss" @click="onPopupDismiss">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
          <span>Dismiss</span>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { buildHighlightedHtml, type ErrorSpan, type HighlightRange } from './buildHighlightedHtml'
import {
  getCurrentCursorOffset as getCursorOffset,
  getSelectionState as getEditorSelectionState,
  setCursorAt as setEditorCursorAt,
  type SelectionState,
} from './docEditorSelection'

const props = defineProps<{
  draftText: string
  correctionMode: boolean
  submitting?: boolean
  cursorPlacement?: { at: number } | null
  selectionCaptureEnabled?: boolean
  errors?: ErrorSpan[]
  activeErrorId?: string | null
  writingMode?: 'free' | 'exam'
  highlightRange?: HighlightRange | null
}>()

const emit = defineEmits<{
  'update:draftText': [value: string]
  'selection-change': [payload: SelectionState | null]
  'cursor-placed': []
  submit: []
  clear: []
  'error-click': [errorId: string]
  'fix-error': [errorId: string]
  'dismiss-error': [errorId: string]
  back: []
}>()

const editorEl = ref<HTMLDivElement | null>(null)
const popupEl = ref<HTMLDivElement | null>(null)

// ── 行内替换弹窗状态 ──
const popup = ref<{
  visible: boolean
  errorId: string
  type: string
  original: string
  suggestion: string | null
  isDeletion: boolean
  top: number
  left: number
}>({
  visible: false,
  errorId: '',
  type: '',
  original: '',
  suggestion: null,
  isDeletion: false,
  top: 0,
  left: 0,
})

const ERROR_TYPE_LABELS: Record<string, string> = {
  spelling: 'Spelling',
  morphology: 'Grammar',
  subject_verb: 'Grammar',
  tense: 'Grammar',
  syntax: 'Grammar',
  article: 'Article',
  preposition: 'Preposition',
  word_choice: 'Word Choice',
  part_of_speech: 'Grammar',
  collocation: 'Collocation',
  punctuation: 'Punctuation',
  logic: 'Logic',
  plural: 'Grammar',
  countability: 'Grammar',
  comparative: 'Grammar',
  register_style: 'Style',
  clarity: 'Clarity',
  redundancy: 'Redundancy',
}

const popupTypeLabel = computed(() => ERROR_TYPE_LABELS[popup.value.type] ?? 'Rephrase')

function closePopup() {
  popup.value.visible = false
}

function showPopupForError(errorId: string, markEl: HTMLElement) {
  const err = props.errors?.find((e) => e.id === errorId)
  if (!err) return

  const hasSuggestion = err.suggestion != null && err.suggestion.trim() !== ''
  const isDeletion = !hasSuggestion && !!err.original?.trim()

  // 没有建议也不是删除操作，不弹
  if (!hasSuggestion && !isDeletion) return
  // 建议和原文相同，不弹
  if (hasSuggestion && err.suggestion!.trim() === (err.original ?? '').trim()) return

  const rect = markEl.getBoundingClientRect()
  const popupTop = rect.bottom + window.scrollY + 6
  let popupLeft = rect.left + window.scrollX

  // 防止超出右侧视口
  const maxLeft = window.innerWidth - 280
  if (popupLeft > maxLeft) popupLeft = maxLeft

  popup.value = {
    visible: true,
    errorId,
    type: err.type,
    original: err.original ?? '',
    suggestion: hasSuggestion ? err.suggestion! : null,
    isDeletion,
    top: popupTop,
    left: popupLeft,
  }
}

function onPopupApply() {
  if (!popup.value.errorId) return
  emit('fix-error', popup.value.errorId)
  closePopup()
}

function onPopupDismiss() {
  if (!popup.value.errorId) return
  emit('dismiss-error', popup.value.errorId)
  closePopup()
}

function onDocumentClick(e: MouseEvent) {
  if (!popup.value.visible) return
  const target = e.target as Node
  if (popupEl.value?.contains(target)) return
  closePopup()
}

const wordCount = computed(() => {
  const t = props.draftText.trim()
  if (!t) return 0
  return t.split(/\s+/).filter(Boolean).length
})

const hasContent = computed(() => props.draftText.trim().length > 0)
const docTitle = computed(() => (props.writingMode === 'exam' ? '考试写作' : '自由写作'))

const hasErrors = computed(() => (props.errors?.length ?? 0) > 0)

const hasHighlight = computed(() => hasErrors.value || !!props.highlightRange)

const highlightedHtml = computed(() => {
  if (!hasHighlight.value) return ''
  return buildHighlightedHtml(props.draftText, props.errors ?? [], props.highlightRange)
})

// 记录上一次渲染是否为高亮模式，避免重复设置
let lastRenderedHighlighted = false

// 用户正在输入时跳过高亮重渲染，避免光标跳转
let userIsTyping = false
let typingTimer: ReturnType<typeof setTimeout> | null = null

watch(
  () => [props.draftText, hasHighlight.value] as const,
  ([val, highlighted]) => {
    if (!editorEl.value) return
    if (highlighted) {
      // 用户正在输入时不重渲染高亮，等打字停止后再渲染
      if (userIsTyping) return
      const html = highlightedHtml.value
      const cursorOffset = getCurrentCursorOffset()
      editorEl.value.innerHTML = html
      lastRenderedHighlighted = true
      if (cursorOffset !== null) {
        nextTick(() => {
          if (editorEl.value) setCursorAt(editorEl.value, cursorOffset)
        })
      }
      nextTick(updateActiveErrorMark)
    } else {
      if (editorEl.value.innerText !== val) {
        editorEl.value.innerText = val
      }
      lastRenderedHighlighted = false
    }
  },
  { flush: 'sync' },
)

watch(
  () => props.activeErrorId,
  () => {
    if (hasHighlight.value) nextTick(updateActiveErrorMark)
  },
)

watch(
  () => props.highlightRange,
  (range) => {
    if (!editorEl.value) return
    // Re-render HTML with updated highlight
    if (hasHighlight.value) {
      const html = highlightedHtml.value
      const cursorOffset = getCurrentCursorOffset()
      editorEl.value.innerHTML = html
      lastRenderedHighlighted = true
      if (cursorOffset !== null) {
        nextTick(() => {
          if (editorEl.value) setCursorAt(editorEl.value, cursorOffset)
        })
      }
    } else if (!range) {
      // Highlight removed, restore plain text
      editorEl.value.innerText = props.draftText
      lastRenderedHighlighted = false
    }
    if (range) {
      nextTick(() => {
        const el = editorEl.value?.querySelector('.sentence-hl')
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
    }
  },
  { deep: true },
)

watch(
  () => props.correctionMode,
  (locked) => {
    if (editorEl.value) editorEl.value.contentEditable = String(!locked)
  },
)

watch(
  () => props.cursorPlacement,
  (placement) => {
    if (!placement || !editorEl.value) return
    nextTick(() => {
      if (editorEl.value && setCursorAt(editorEl.value, placement.at)) {
        emit('cursor-placed')
      }
    })
  },
)

function updateActiveErrorMark() {
  if (!editorEl.value) return
  editorEl.value.querySelectorAll('mark.err-active').forEach((el) => el.classList.remove('err-active'))
  if (!props.activeErrorId) return
  const marks = editorEl.value.querySelectorAll('mark[data-error-ids]')
  for (const mark of marks) {
    const ids = mark.getAttribute('data-error-ids')?.split(',') ?? []
    if (ids.includes(props.activeErrorId)) {
      mark.classList.add('err-active')
      mark.scrollIntoView({ behavior: 'smooth', block: 'center' })
      break
    }
  }
}

function onEditorClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  const mark = target.closest('mark[data-error-ids]') as HTMLElement | null
  if (mark) {
    const ids = mark.getAttribute('data-error-ids')?.split(',') ?? []
    if (ids.length > 0) {
      emit('error-click', ids[0])
      showPopupForError(ids[0], mark)
    }
  } else {
    closePopup()
  }
}

function setCursorAt(el: HTMLElement, charOffset: number): boolean {
  return setEditorCursorAt(el, charOffset)
}

function getCurrentCursorOffset(): number | null {
  return getCursorOffset(editorEl.value)
}

function addSelectionListeners() {
  document.addEventListener('mouseup', onSelectionChange, true)
  document.addEventListener('selectionchange', onSelectionChange, true)
  document.addEventListener('keyup', onSelectionChange, true)
}

function removeSelectionListeners() {
  document.removeEventListener('mouseup', onSelectionChange, true)
  document.removeEventListener('selectionchange', onSelectionChange, true)
  document.removeEventListener('keyup', onSelectionChange, true)
}

watch(
  () => props.selectionCaptureEnabled,
  (enabled) => {
    if (enabled) addSelectionListeners()
    else removeSelectionListeners()
  },
  { immediate: true },
)

onMounted(() => {
  if (editorEl.value) {
    if (hasErrors.value) {
      editorEl.value.innerHTML = highlightedHtml.value
      lastRenderedHighlighted = true
    } else if (editorEl.value.innerText !== props.draftText) {
      editorEl.value.innerText = props.draftText
    }
  }
  document.addEventListener('click', onDocumentClick, true)
  // 左侧面板滚动时关闭弹窗
  const scrollParent = editorEl.value?.closest('.left-pane')
  if (scrollParent) scrollParent.addEventListener('scroll', closePopup)
})

onBeforeUnmount(() => {
  removeSelectionListeners()
  if (typingTimer) clearTimeout(typingTimer)
  document.removeEventListener('click', onDocumentClick, true)
  const scrollParent = editorEl.value?.closest('.left-pane')
  if (scrollParent) scrollParent.removeEventListener('scroll', closePopup)
})

function getSelectionState(): SelectionState | null {
  return getEditorSelectionState(editorEl.value, props.correctionMode)
}

function onSelectionChange() {
  const state = getSelectionState()
  emit('selection-change', state)
}

function onInput() {
  if (!editorEl.value) return
  const text = editorEl.value.innerText ?? ''

  closePopup()

  // 标记用户正在输入，阻止 watch 立即重渲染高亮
  userIsTyping = true
  if (typingTimer) clearTimeout(typingTimer)

  if (lastRenderedHighlighted) {
    // 用户在高亮模式下编辑，保存光标位置后切换回纯文本模式
    const cursorOffset = getCurrentCursorOffset()
    emit('update:draftText', text)
    nextTick(() => {
      if (editorEl.value) {
        editorEl.value.innerText = text
        lastRenderedHighlighted = false
        if (cursorOffset !== null) {
          setCursorAt(editorEl.value, cursorOffset)
        }
      }
    })
  } else {
    emit('update:draftText', text)
  }

  // 停止输入 600ms 后恢复高亮渲染
  typingTimer = setTimeout(() => {
    userIsTyping = false
    if (hasErrors.value && editorEl.value) {
      const html = highlightedHtml.value
      const cursorOffset = getCurrentCursorOffset()
      editorEl.value.innerHTML = html
      lastRenderedHighlighted = true
      if (cursorOffset !== null) {
        nextTick(() => {
          if (editorEl.value) setCursorAt(editorEl.value, cursorOffset)
        })
      }
      nextTick(updateActiveErrorMark)
    }
  }, 600)
}

function onPaste(e: ClipboardEvent) {
  e.preventDefault()
  const text = e.clipboardData?.getData('text/plain') ?? ''
  const sel = window.getSelection()
  if (sel && sel.rangeCount > 0) {
    const range = sel.getRangeAt(0)
    range.deleteContents()
    const textNode = document.createTextNode(text)
    range.insertNode(textNode)
    range.setStartAfter(textNode)
    range.collapse(true)
    sel.removeAllRanges()
    sel.addRange(range)
  }
  onInput()
}

function submit() {
  emit('submit')
}

function clear() {
  emit('clear')
  if (editorEl.value) {
    editorEl.value.innerText = ''
    lastRenderedHighlighted = false
    emit('update:draftText', '')
  }
}
</script>

<style scoped>
.doc-editor {
  min-height: 100%;
  background: #f5f6f7;
}
.doc-canvas {
  max-width: 820px;
  margin: 0 auto;
  padding: 48px 56px;
  box-sizing: border-box;
}
.doc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.back-link {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  color: #6b7280;
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
  flex-shrink: 0;
}
.back-link:hover {
  background: #f3f4f6;
  color: #111827;
}
.doc-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}
.doc-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.word-count {
  font-size: 13px;
  color: #6b7280;
}
.btn {
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-primary {
  background: #047857;
  color: #fff;
}
.btn-primary:hover:not(:disabled) {
  background: #065f46;
}
.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}
.btn-secondary:hover:not(:disabled) {
  background: #e5e7eb;
}
.doc-content {
  user-select: text;
  white-space: pre-wrap;
  word-break: normal;
  overflow-wrap: break-word;
  line-height: 1.8;
  font-size: 18px;
  min-height: calc(100vh - 160px);
  padding-bottom: 160px;
  color: #1a1a1a;
  outline: none;
  font-family: Georgia, 'Times New Roman', serif;
  letter-spacing: 0.01em;
  box-sizing: border-box;
}
.doc-content.empty::before {
  content: attr(data-placeholder);
  color: #9ca3af;
}
.doc-content.locked {
  cursor: default;
  background: transparent;
}
.doc-content:focus {
  outline: none;
}
</style>

<!-- 非 scoped：行内弹窗 Teleport 到 body + 动态 innerHTML 中的 <mark> 元素 -->
<style>
.doc-content mark[data-error-ids] {
  background: transparent;
  border-radius: 2px;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s;
  padding: 1px 0;
}

/* ── 红色系（拼写/语法类客观错误） ── */
.doc-content mark.err-spelling,
.doc-content mark.err-morphology,
.doc-content mark.err-subject_verb,
.doc-content mark.err-tense,
.doc-content mark.err-syntax {
  background: rgba(239, 68, 68, 0.13);
  border-bottom: 2px solid #ef4444;
}
/* ── 橙色系（用词/搭配/冠词/介词） ── */
.doc-content mark.err-word_choice,
.doc-content mark.err-part_of_speech,
.doc-content mark.err-collocation,
.doc-content mark.err-article,
.doc-content mark.err-preposition {
  background: rgba(245, 158, 11, 0.13);
  border-bottom: 2px solid #f59e0b;
}
/* ── 蓝紫色系（标点/逻辑） ── */
.doc-content mark.err-punctuation,
.doc-content mark.err-logic {
  background: rgba(139, 92, 246, 0.13);
  border-bottom: 2px solid #8b5cf6;
}
/* ── 紫色系（AI 建议：搭配/用词/语体/清晰度/冗余） ── */
.doc-content mark.err-register_style,
.doc-content mark.err-clarity,
.doc-content mark.err-redundancy {
  background: rgba(139, 92, 246, 0.10);
  border-bottom: 2px dotted #8b5cf6;
}
/* GPT 复检硬性错误类型 */
.doc-content mark.err-plural,
.doc-content mark.err-countability,
.doc-content mark.err-comparative {
  background: rgba(239, 68, 68, 0.13);
  border-bottom: 2px solid #ef4444;
}
/* ── 旧 type 兼容（来自缓存/历史数据） ── */
.doc-content mark.err-grammar {
  background: rgba(239, 68, 68, 0.13);
  border-bottom: 2px solid #ef4444;
}
.doc-content mark.err-expression {
  background: rgba(139, 92, 246, 0.13);
  border-bottom: 2px solid #8b5cf6;
}
.doc-content mark.err-coherence {
  background: rgba(59, 130, 246, 0.13);
  border-bottom: 2px solid #3b82f6;
}
.doc-content mark.err-format {
  background: rgba(107, 114, 128, 0.13);
  border-bottom: 2px solid #6b7280;
}

/* 严重程度 */
.doc-content mark.err-major {
  border-bottom-width: 3px;
  border-bottom-style: wavy;
}
.doc-content mark.err-minor {
  border-bottom-style: solid;
}

/* 选中/聚焦的错误 */
.doc-content mark.err-active {
  background: rgba(251, 191, 36, 0.3) !important;
  box-shadow: 0 0 0 2px #fbbf24;
}

/* 悬停 */
.doc-content mark[data-error-ids]:hover {
  filter: brightness(0.96);
}

/* ── 句子高亮 ── */
.doc-content .sentence-hl {
  background: rgba(59, 130, 246, 0.2);
  border-bottom: 2px solid #2563eb;
  border-radius: 3px;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.2);
  transition: background 0.2s;
}

/* ── 行内替换弹窗 ── */
.inline-fix-popup {
  position: absolute;
  z-index: 1000;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.13), 0 1px 4px rgba(0, 0, 0, 0.08);
  min-width: 160px;
  max-width: 320px;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  animation: popup-in 0.12s ease-out;
}
@keyframes popup-in {
  from { opacity: 0; transform: translateY(-4px); }
  to   { opacity: 1; transform: translateY(0); }
}
.popup-suggestion {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.12s;
}
.popup-suggestion:hover {
  background: #f0fdf4;
}
.popup-label {
  font-size: 12px;
  color: #6b7280;
  font-weight: 500;
  letter-spacing: 0.02em;
}
.popup-replacement {
  font-size: 16px;
  font-weight: 600;
  color: #059669;
  line-height: 1.4;
}
.popup-deletion .popup-replacement.deletion-text {
  color: #dc2626;
  font-size: 14px;
}
.popup-dismiss {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  cursor: pointer;
  font-size: 13px;
  color: #6b7280;
  border-top: 1px solid #f3f4f6;
  transition: background 0.12s, color 0.12s;
}
.popup-dismiss:hover {
  background: #fef2f2;
  color: #dc2626;
}
</style>





