<template>
  <div class="doc-editor">
    <div class="doc-canvas">
      <header class="doc-header">
        <button type="button" class="back-link" title="返回总览" @click="emit('back')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        <span class="doc-title">{{ docTitle }}</span>
        <div class="doc-actions">
          <span class="word-count" :title="`${sentenceCount} 句 · ${paragraphCount} 段 · 均句长 ${avgSentenceLength}`">
            {{ wordCount }} 词 · {{ sentenceCount }} 句 · {{ paragraphCount }} 段
          </span>
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
          <div class="toolbar-menu-wrap" ref="toolbarMenuRef">
            <button type="button" class="btn-menu" title="格式与设置" @click="toolbarOpen = !toolbarOpen">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/></svg>
            </button>
            <EditorToolbar v-if="toolbarOpen" :editor="editor" class="toolbar-dropdown" />
          </div>
        </div>
      </header>
      <EditorContent
        :editor="editor"
        class="doc-content-wrapper"
        :class="{ locked: correctionMode }"
      />
    </div>

    <!-- 选中文本浮动菜单 -->
    <Teleport to="body">
      <div
        v-if="bubbleVisible"
        class="bubble-menu"
        :style="{ top: bubblePos.top + 'px', left: bubblePos.left + 'px' }"
        @mousedown.prevent
      >
        <button type="button" class="bubble-btn" @click="onBubbleAction('explain')">
          <span class="bubble-icon">?</span> 解释
        </button>
        <button type="button" class="bubble-btn" @click="onBubbleAction('rewrite')">
          <span class="bubble-icon">↑</span> 改写
        </button>
        <button type="button" class="bubble-btn" @click="onBubbleAction('translate')">
          <span class="bubble-icon">⇄</span> 翻译
        </button>
      </div>
    </Teleport>

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
        <div class="popup-actions-row">
          <div class="popup-dismiss" @click="onPopupDismiss">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
            <span>Dismiss</span>
          </div>
          <span class="popup-shortcut-hint">Tab 接受 · Esc 忽略</span>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick, shallowRef } from 'vue'
import { useEventListener } from '@vueuse/core'
import { Editor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import CharacterCount from '@tiptap/extension-character-count'
import { TextStyle, Color, FontFamily, FontSize } from '@tiptap/extension-text-style'
import Underline from '@tiptap/extension-underline'
import EditorToolbar from './EditorToolbar.vue'
import type { ErrorSpan, HighlightRange } from './buildHighlightedHtml'
import {
  createErrorHighlightPlugin,
  setErrorHighlightState,
} from './tiptap/errorHighlightPlugin'
import type { SelectionState } from './docEditorSelection'

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
  'bubble-action': [action: 'explain' | 'rewrite' | 'translate']
}>()

const popupEl = ref<HTMLDivElement | null>(null)
const toolbarOpen = ref(false)
const toolbarMenuRef = ref<HTMLElement | null>(null)

// ── 选中文本浮动菜单 ──
const bubbleVisible = ref(false)
const bubblePos = ref({ top: 0, left: 0 })

function updateBubbleMenu() {
  const ed = editor.value
  if (!ed || props.correctionMode) {
    bubbleVisible.value = false
    return
  }
  const { from, to } = ed.state.selection
  if (from === to) {
    bubbleVisible.value = false
    return
  }
  const selectedText = ed.state.doc.textBetween(from, to, '\n\n').trim()
  if (!selectedText || selectedText.length < 2) {
    bubbleVisible.value = false
    return
  }
  // 获取选区坐标
  const view = ed.view
  const start = view.coordsAtPos(from)
  const end = view.coordsAtPos(to)
  const top = Math.min(start.top, end.top) + window.scrollY - 44
  let left = (start.left + end.left) / 2 + window.scrollX - 80
  left = Math.max(8, Math.min(left, window.innerWidth - 240))
  bubblePos.value = { top, left }
  bubbleVisible.value = true
}

// 防止 editor update 事件回写时触发 watch
let ignoreNextUpdate = false

// ── TipTap Editor ──

const editor = shallowRef<Editor | undefined>(undefined)

onMounted(() => {
  const errorPlugin = createErrorHighlightPlugin()

  editor.value = new Editor({
    content: textToHtml(props.draftText),
    extensions: [
      StarterKit.configure({
        heading: false,
        strike: false,
        code: false,
        codeBlock: false,
        blockquote: false,
        bulletList: false,
        orderedList: false,
        listItem: false,
        horizontalRule: false,
        underline: false,
      }),
      TextStyle,
      Color,
      FontFamily,
      FontSize,
      Underline,
      Placeholder.configure({
        placeholder: '在此输入英文作文...',
      }),
      CharacterCount,
    ],
    editable: !props.correctionMode,
    editorProps: {
      attributes: {
        class: 'doc-content',
        spellcheck: 'false',
      },
      // 纯文本粘贴：每个换行都视为段落分隔
      transformPastedText(text) {
        return text.replace(/\r\n/g, '\n').replace(/\n+/g, '\n\n')
      },
      // HTML 粘贴（OneNote/Word 等）：合并连续非空 <p> 为一个段落
      transformPastedHTML(html) {
        return mergeParagraphsInHtml(html)
      },
      handleClick(_view, _pos, event) {
        const target = event.target as HTMLElement
        const mark = target.closest('mark[data-error-ids]') as HTMLElement | null
        if (mark) {
          const ids = mark.getAttribute('data-error-ids')?.split(',') ?? []
          if (ids.length > 0) {
            emit('error-click', ids[0])
            showPopupForError(ids[0], mark)
          }
          return true
        }
        closePopup()
        return false
      },
    },
    onUpdate({ editor: ed }) {
      if (ignoreNextUpdate) {
        ignoreNextUpdate = false
        return
      }
      const text = ed.getText({ blockSeparator: '\n\n' })
      emit('update:draftText', text)
      closePopup()
      bubbleVisible.value = false
    },
    onSelectionUpdate({ editor: ed }) {
      updateBubbleMenu()
      if (!props.selectionCaptureEnabled) return
      const { from, to } = ed.state.selection
      if (from === to) {
        emit('selection-change', null)
        return
      }
      const text = ed.state.doc.textBetween(from, to, '\n\n')
      if (!text.trim()) {
        emit('selection-change', null)
        return
      }
      // 计算纯文本 offset
      const beforeFrom = ed.state.doc.textBetween(0, from, '\n\n')
      const start = beforeFrom.length
      emit('selection-change', {
        text,
        start,
        end: start + text.length,
      })
    },
  })

  // Register the ProseMirror plugin
  editor.value.registerPlugin(errorPlugin)

  // 初始化错误高亮
  updateErrorDecorations()
})

useEventListener(document, 'click', onDocumentClick, true)
useEventListener(document, 'keydown', onDocumentKeydown, true)

onBeforeUnmount(() => {
  editor.value?.destroy()
})

// ── 错误高亮更新 ──

function updateErrorDecorations() {
  const ed = editor.value
  if (!ed?.view) return
  setErrorHighlightState(ed.view, {
    errors: props.errors ?? [],
    activeErrorId: props.activeErrorId ?? null,
    highlightRange: props.highlightRange ?? null,
  })
}

watch(() => [props.errors, props.activeErrorId, props.highlightRange] as const, () => {
  nextTick(updateErrorDecorations)
}, { deep: true })

// ── 外部文本同步 ──

watch(() => props.draftText, (newText) => {
  const ed = editor.value
  if (!ed) return
  const currentText = ed.getText({ blockSeparator: '\n\n' })
  if (currentText === newText) return

  // 外部文本变化（如替换操作），更新编辑器内容
  ignoreNextUpdate = true
  const { from } = ed.state.selection
  ed.commands.setContent(
    textToHtml(newText),
    { emitUpdate: false },
  )
  // 尝试恢复光标位置
  const docSize = ed.state.doc.content.size
  const safePos = Math.min(from, docSize - 1)
  if (safePos > 0) {
    ed.commands.setTextSelection(safePos)
  }
  nextTick(updateErrorDecorations)
})

// ── 可编辑状态 ──

watch(() => props.correctionMode, (locked) => {
  editor.value?.setEditable(!locked)
})

// ── 光标定位 ──

watch(() => props.cursorPlacement, (placement) => {
  if (!placement || !editor.value) return
  const ed = editor.value
  // 将纯文本 offset 转换为 ProseMirror position
  const pos = textOffsetToPos(ed, placement.at)
  if (pos > 0) {
    ed.commands.setTextSelection(pos)
    ed.commands.focus()
    emit('cursor-placed')
  }
})

// ── 句子高亮滚动 ──

watch(() => props.highlightRange, (range) => {
  if (!range) return
  nextTick(() => {
    const el = editor.value?.view.dom.querySelector('.sentence-hl')
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}, { deep: true })

// ── 计算属性 ──

const wordCount = computed(() => {
  const t = props.draftText.trim()
  if (!t) return 0
  return t.split(/\s+/).filter(Boolean).length
})

const sentenceCount = computed(() => {
  const t = props.draftText.trim()
  if (!t) return 0
  const matches = t.match(/[^.!?]*[.!?]+/g)
  return matches ? matches.length : (t ? 1 : 0)
})

const paragraphCount = computed(() => {
  const t = props.draftText.trim()
  if (!t) return 0
  return t.split(/\n+/).filter((p) => p.trim().length > 0).length
})

const avgSentenceLength = computed(() => {
  if (sentenceCount.value === 0) return '0'
  return (wordCount.value / sentenceCount.value).toFixed(1)
})

const hasContent = computed(() => props.draftText.trim().length > 0)
const docTitle = computed(() => (props.writingMode === 'exam' ? '考试写作' : '自由写作'))

// ── 行内替换弹窗 ──

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
  spelling: 'Spelling', morphology: 'Grammar', subject_verb: 'Grammar',
  tense: 'Grammar', syntax: 'Grammar', article: 'Article',
  preposition: 'Preposition', word_choice: 'Word Choice',
  part_of_speech: 'Grammar', collocation: 'Collocation',
  punctuation: 'Punctuation', logic: 'Logic', plural: 'Grammar',
  countability: 'Grammar', comparative: 'Grammar', register_style: 'Style',
  clarity: 'Clarity', redundancy: 'Redundancy',
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

  if (!hasSuggestion && !isDeletion) return
  if (hasSuggestion && err.suggestion!.trim() === (err.original ?? '').trim()) return

  const rect = markEl.getBoundingClientRect()
  const popupTop = rect.bottom + window.scrollY + 6
  let popupLeft = rect.left + window.scrollX
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
  const target = e.target as Node
  // 关闭行内弹窗
  if (popup.value.visible && !popupEl.value?.contains(target)) {
    closePopup()
  }
  // 关闭工具栏菜单
  if (toolbarOpen.value && !toolbarMenuRef.value?.contains(target)) {
    toolbarOpen.value = false
  }
}

function onDocumentKeydown(e: KeyboardEvent) {
  if (!popup.value.visible) return
  if (e.key === 'Tab') {
    e.preventDefault()
    onPopupApply()
  } else if (e.key === 'Escape') {
    e.preventDefault()
    onPopupDismiss()
  }
}

// ── 工具函数 ──

/**
 * 合并粘贴 HTML 中连续的非空 <p>，只在空 <p>（段落间距）处分段。
 * OneNote/Word 复制的 HTML 每行一个 <p>，需要合并为连续段落。
 */
function mergeParagraphsInHtml(html: string): string {
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')
  const body = doc.body
  if (!body) return html

  const result: string[] = []
  let currentParts: string[] = []
  const BLOCK_TAGS = new Set([
    'p', 'div', 'section', 'article', 'main', 'aside',
    'blockquote', 'pre',
    'ul', 'ol', 'li',
    'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  ])
  const HEADING_TAGS = new Set(['h1', 'h2', 'h3', 'h4', 'h5', 'h6'])
  const IGNORE_TAGS = new Set(['script', 'style', 'meta', 'link', 'svg', 'canvas'])

  function normalizeInlineWhitespace(text: string): string {
    return text
      .replace(/\u00a0/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
  }

  function flushParagraph() {
    if (currentParts.length > 0) {
      result.push(`<p>${escapeHtml(currentParts.join(' '))}</p>`)
      currentParts = []
    }
  }

  function processNode(node: Node) {
    if (node.nodeType === Node.TEXT_NODE) {
      const text = normalizeInlineWhitespace(node.textContent ?? '')
      if (text) currentParts.push(text)
      return
    }

    if (node.nodeType !== Node.ELEMENT_NODE) return

    const el = node as HTMLElement
    const tag = el.tagName?.toLowerCase()
    if (!tag || IGNORE_TAGS.has(tag)) return

    if (tag === 'br') {
      // 连续 <br><br> 视为段落分隔；单个 <br> 视为行内空白。
      const next = el.nextSibling as HTMLElement | null
      if (next?.nodeType === Node.ELEMENT_NODE && next.tagName?.toLowerCase() === 'br') {
        flushParagraph()
      }
      return
    }

    if (HEADING_TAGS.has(tag)) {
      flushParagraph()
      const title = normalizeInlineWhitespace(el.textContent ?? '')
      if (title) result.push(`<p>${escapeHtml(title)}</p>`)
      return
    }

    if (BLOCK_TAGS.has(tag)) {
      const text = normalizeInlineWhitespace(el.textContent ?? '')
      if (text === '') {
        // 空块级节点 = 段落分隔
        flushParagraph()
        return
      }

      const hasDirectBlockChild = Array.from(el.children).some((child) =>
        BLOCK_TAGS.has(child.tagName.toLowerCase()),
      )

      if (hasDirectBlockChild) {
        for (const child of Array.from(el.childNodes)) {
          processNode(child)
        }
      } else {
        // 每个非空块级元素独立成段
        flushParagraph()
        currentParts.push(text)
        flushParagraph()
      }
      return
    }

    // 行内容器：继续递归，拿纯文本。
    for (const child of Array.from(el.childNodes)) {
      processNode(child)
    }
  }

  for (const node of Array.from(body.childNodes)) {
    processNode(node)
  }
  flushParagraph()

  return result.length > 0 ? result.join('') : html
}
function textToHtml(text: string): string {
  if (!text) return '<p></p>'
  // 空行（\n\n+）分段，单个 \n 当作同一段落内的空格
  return text
    .split(/\n{2,}/)
    .map((para) => `<p>${escapeHtml(para.replace(/\n/g, ' '))}</p>`)
    .join('')
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function textOffsetToPos(ed: Editor, offset: number): number {
  const doc = ed.state.doc
  let charsSeen = 0
  let pos = 0
  for (let i = 0; i < doc.content.childCount; i++) {
    const child = doc.content.child(i)
    if (i > 0) charsSeen += 2 // \n\n separator between paragraphs
    const childText = child.textContent
    if (charsSeen + childText.length >= offset) {
      return pos + 1 + (offset - charsSeen) // +1 for paragraph open tag
    }
    charsSeen += childText.length
    pos += child.nodeSize
  }
  return doc.content.size
}

function onBubbleAction(action: 'explain' | 'rewrite' | 'translate') {
  bubbleVisible.value = false
  emit('bubble-action', action)
}

function submit() {
  emit('submit')
}

function clear() {
  emit('clear')
  editor.value?.commands.clearContent()
  emit('update:draftText', '')
}
</script>

<style scoped>
.doc-editor {
  min-height: 100%;
  background: #f5f6f7;
}
.doc-canvas {
  max-width: 1120px;
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
  border: none;
  background: transparent;
  cursor: pointer;
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
/* ── 工具栏菜单按钮 ── */
.toolbar-menu-wrap {
  position: relative;
}
.btn-menu {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: #f3f4f6;
  color: #374151;
  cursor: pointer;
  transition: background 0.15s;
}
.btn-menu:hover {
  background: #e5e7eb;
}
.toolbar-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 6px;
  z-index: 100;
}

.doc-content-wrapper.locked :deep(.ProseMirror) {
  cursor: default;
}
</style>

<!-- 非 scoped：TipTap 编辑器内部 + Teleport 弹窗样式 -->
<style>
.doc-content-wrapper .ProseMirror {
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

/* 段落：默认不留外边距；段与段之间提供间距 */
.doc-content-wrapper .ProseMirror p {
  margin: 0;
  min-height: 1em;
}
.doc-content-wrapper .ProseMirror p + p {
  margin-top: 0.9em;
}

/* Placeholder */
.doc-content-wrapper .ProseMirror p.is-editor-empty:first-child::before {
  content: attr(data-placeholder);
  float: left;
  color: #9ca3af;
  pointer-events: none;
  height: 0;
}

/* ── 错误下划线 ── */
.doc-content-wrapper mark[data-error-ids] {
  background: transparent;
  border-radius: 2px;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s;
  padding: 1px 0;
}

/* 红色系（拼写/语法类客观错误） */
.doc-content-wrapper mark.err-spelling,
.doc-content-wrapper mark.err-morphology,
.doc-content-wrapper mark.err-subject_verb,
.doc-content-wrapper mark.err-tense,
.doc-content-wrapper mark.err-syntax {
  background: rgba(239, 68, 68, 0.13);
  border-bottom: 2px solid #ef4444;
}
/* 橙色系（用词/搭配/冠词/介词） */
.doc-content-wrapper mark.err-word_choice,
.doc-content-wrapper mark.err-part_of_speech,
.doc-content-wrapper mark.err-collocation,
.doc-content-wrapper mark.err-article,
.doc-content-wrapper mark.err-preposition {
  background: rgba(245, 158, 11, 0.13);
  border-bottom: 2px solid #f59e0b;
}
/* 蓝紫色系（标点/逻辑） */
.doc-content-wrapper mark.err-punctuation,
.doc-content-wrapper mark.err-logic {
  background: rgba(139, 92, 246, 0.13);
  border-bottom: 2px solid #8b5cf6;
}
/* 紫色系（AI 建议） */
.doc-content-wrapper mark.err-register_style,
.doc-content-wrapper mark.err-clarity,
.doc-content-wrapper mark.err-redundancy {
  background: rgba(139, 92, 246, 0.10);
  border-bottom: 2px dotted #8b5cf6;
}
/* GPT 复检硬性错误类型 */
.doc-content-wrapper mark.err-plural,
.doc-content-wrapper mark.err-countability,
.doc-content-wrapper mark.err-comparative {
  background: rgba(239, 68, 68, 0.13);
  border-bottom: 2px solid #ef4444;
}
/* 旧 type 兼容 */
.doc-content-wrapper mark.err-grammar {
  background: rgba(239, 68, 68, 0.13);
  border-bottom: 2px solid #ef4444;
}
.doc-content-wrapper mark.err-expression {
  background: rgba(139, 92, 246, 0.13);
  border-bottom: 2px solid #8b5cf6;
}
.doc-content-wrapper mark.err-coherence {
  background: rgba(59, 130, 246, 0.13);
  border-bottom: 2px solid #3b82f6;
}
.doc-content-wrapper mark.err-format {
  background: rgba(107, 114, 128, 0.13);
  border-bottom: 2px solid #6b7280;
}

/* 严重程度 */
.doc-content-wrapper mark.err-major {
  border-bottom-width: 3px;
  border-bottom-style: wavy;
}
.doc-content-wrapper mark.err-minor {
  border-bottom-style: solid;
}

/* 选中/聚焦的错误 */
.doc-content-wrapper mark.err-active {
  background: rgba(251, 191, 36, 0.3) !important;
  box-shadow: 0 0 0 2px #fbbf24;
}

/* 悬停 */
.doc-content-wrapper mark[data-error-ids]:hover {
  filter: brightness(0.96);
}

/* 句子高亮 */
.doc-content-wrapper .sentence-hl {
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
.popup-actions-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #f3f4f6;
}
.popup-dismiss {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  cursor: pointer;
  font-size: 13px;
  color: #6b7280;
  transition: background 0.12s, color 0.12s;
}
.popup-dismiss:hover {
  background: #fef2f2;
  color: #dc2626;
}
.popup-shortcut-hint {
  font-size: 11px;
  color: #9ca3af;
  padding-right: 12px;
  white-space: nowrap;
}

/* ── BubbleMenu ── */
.bubble-menu {
  position: absolute;
  z-index: 1000;
  display: flex;
  gap: 2px;
  background: #1f2937;
  border-radius: 8px;
  padding: 4px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  animation: popup-in 0.1s ease-out;
}
.bubble-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  font-size: 13px;
  font-weight: 500;
  color: #e5e7eb;
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.12s, color 0.12s;
  white-space: nowrap;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
.bubble-btn:hover {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}
.bubble-icon {
  font-size: 14px;
  line-height: 1;
}
</style>


