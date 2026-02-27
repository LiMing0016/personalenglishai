<template>
  <div class="doc-editor">
    <div class="doc-canvas">
      <header class="doc-header">
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
        :class="{ empty: !draftText.trim() && !correctionMode, locked: correctionMode }"
        :contenteditable="!correctionMode"
        data-placeholder="在此输入英文作文..."
        @input="onInput"
        @paste="onPaste"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'

export type SelectionState = { text: string; start: number; end: number }

const props = defineProps<{
  draftText: string
  correctionMode: boolean
  submitting?: boolean
  cursorPlacement?: { at: number } | null
  /** 仅当为 true 时注册选区监听并 emit；关闭时移除监听 */
  selectionCaptureEnabled?: boolean
}>()

const emit = defineEmits<{
  'update:draftText': [value: string]
  'selection-change': [payload: SelectionState | null]
  'cursor-placed': []
  submit: []
  clear: []
}>()

const editorEl = ref<HTMLDivElement | null>(null)

const wordCount = computed(() => {
  const t = props.draftText.trim()
  if (!t) return 0
  return t.split(/\s+/).filter(Boolean).length
})

const hasContent = computed(() => props.draftText.trim().length > 0)

watch(
  () => props.draftText,
  (val) => {
    if (!editorEl.value) return
    if (editorEl.value.innerText !== val) editorEl.value.innerText = val
  },
  { flush: 'sync' }
)

watch(
  () => props.correctionMode,
  (locked) => {
    if (editorEl.value) editorEl.value.contentEditable = String(!locked)
  }
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
  }
)

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
  { immediate: true }
)

onMounted(() => {
  if (editorEl.value && editorEl.value.innerText !== props.draftText) {
    editorEl.value.innerText = props.draftText
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
  if (editorEl.value) emit('update:draftText', editorEl.value.innerText ?? '')
}

function onPaste(e: ClipboardEvent) {
  e.preventDefault()
  const text = e.clipboardData?.getData('text/plain') ?? ''
  document.execCommand?.('insertText', false, text)
  onInput()
}

function submit() {
  emit('submit')
}

function clear() {
  emit('clear')
  if (editorEl.value) {
    editorEl.value.innerText = ''
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
