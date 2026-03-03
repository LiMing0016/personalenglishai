<template>
  <div class="doc-editor">
    <div class="doc-canvas">
      <header class="doc-header">
        <router-link to="/app" class="back-link" title="返回总览">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
        </router-link>
        <span class="doc-title">自由写作</span>
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { buildHighlightedHtml, type ErrorSpan } from './buildHighlightedHtml'

export type SelectionState = { text: string; start: number; end: number }

const props = defineProps<{
  draftText: string
  correctionMode: boolean
  submitting?: boolean
  cursorPlacement?: { at: number } | null
  selectionCaptureEnabled?: boolean
  errors?: ErrorSpan[]
  activeErrorId?: string | null
}>()

const emit = defineEmits<{
  'update:draftText': [value: string]
  'selection-change': [payload: SelectionState | null]
  'cursor-placed': []
  submit: []
  clear: []
  'error-click': [errorId: string]
}>()

const editorEl = ref<HTMLDivElement | null>(null)

const wordCount = computed(() => {
  const t = props.draftText.trim()
  if (!t) return 0
  return t.split(/\s+/).filter(Boolean).length
})

const hasContent = computed(() => props.draftText.trim().length > 0)

const hasErrors = computed(() => (props.errors?.length ?? 0) > 0)

const highlightedHtml = computed(() => {
  if (!hasErrors.value || !props.errors) return ''
  return buildHighlightedHtml(props.draftText, props.errors)
})

// 记录上一次渲染是否为高亮模式，避免重复设置
let lastRenderedHighlighted = false

watch(
  () => [props.draftText, hasErrors.value] as const,
  ([val, highlighted]) => {
    if (!editorEl.value) return
    if (highlighted) {
      const html = highlightedHtml.value
      editorEl.value.innerHTML = html
      lastRenderedHighlighted = true
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
    if (hasErrors.value) nextTick(updateActiveErrorMark)
  },
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
    }
  }
}

function setCursorAt(el: HTMLElement, charOffset: number): boolean {
  const sel = window.getSelection()
  if (!sel) return false
  const range = document.createRange()
  let passed = 0
  const walk = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null)
  let node: Text | null = walk.nextNode() as Text | null
  while (node) {
    const len = node.textContent?.length ?? 0
    if (passed + len >= charOffset) {
      range.setStart(node, charOffset - passed)
      range.collapse(true)
      sel.removeAllRanges()
      sel.addRange(range)
      el.focus()
      return true
    }
    passed += len
    node = walk.nextNode() as Text | null
  }
  if (passed > 0) {
    const last = walk.currentNode as Text
    if (last) {
      range.setStart(last, last.textContent?.length ?? 0)
      range.collapse(true)
      sel.removeAllRanges()
      sel.addRange(range)
      el.focus()
      return true
    }
  }
  return false
}

function getCurrentCursorOffset(): number | null {
  const el = editorEl.value
  if (!el) return null
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) return null
  const range = sel.getRangeAt(0)
  if (!el.contains(range.startContainer)) return null
  try {
    const preRange = document.createRange()
    preRange.selectNodeContents(el)
    preRange.setEnd(range.startContainer, range.startOffset)
    return preRange.toString().length
  } catch {
    return null
  }
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
})

onBeforeUnmount(() => {
  removeSelectionListeners()
})

function getSelectionState(): SelectionState | null {
  const el = editorEl.value
  if (!el || props.correctionMode) return null
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) return null
  const range = sel.getRangeAt(0)
  const text = range.toString()
  const trimmed = text.trim()
  if (!trimmed || trimmed.length < 2) return null
  const anchorNode = sel.anchorNode
  const focusNode = sel.focusNode
  const inside =
    anchorNode != null &&
    focusNode != null &&
    el.contains(anchorNode) &&
    el.contains(focusNode)
  if (!inside) return null
  try {
    const startRange = document.createRange()
    startRange.selectNodeContents(el)
    startRange.setEnd(range.startContainer, range.startOffset)
    const start = startRange.toString().length
    const end = start + text.length
    return { text, start, end }
  } catch {
    return { text, start: 0, end: text.length }
  }
}

function onSelectionChange() {
  const state = getSelectionState()
  emit('selection-change', state)
}

function onInput() {
  if (!editorEl.value) return
  const text = editorEl.value.innerText ?? ''

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
  word-break: break-word;
  overflow-wrap: anywhere;
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

<!-- 非 scoped：动态 innerHTML 中的 <mark> 元素无法匹配 scoped 样式 -->
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
  border-bottom-style: dashed;
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
</style>
