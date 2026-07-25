<template>
  <aside
    id="learning-asset-canvas"
    class="learning-asset-canvas"
    :style="canvasStyle"
    aria-label="学习资产画布"
    tabindex="-1"
  >
    <button
      type="button"
      class="resize-handle"
      aria-label="调整学习资产画布宽度"
      @keydown="handleResizeKeydown"
      @mousedown.prevent="startResize"
      @touchstart.prevent="startResize"
    ></button>

    <template v-if="draft">
    <header class="canvas-workspace-header" aria-label="学习资产工作区">
      <div class="asset-tab-rail">
        <button
          v-if="drafts.length > 1"
          type="button"
          class="asset-tab-scroll"
          aria-label="向左滚动学习资产标签"
          @click="scrollAssetTabs('left')"
        >
          ‹
        </button>
        <nav ref="assetTabListRef" class="asset-tab-list" aria-label="学习资产标签">
          <template
            v-for="item in drafts"
            :key="item.draftId"
          >
            <form
              v-if="renamingDraftId === item.draftId"
              class="asset-tab asset-tab--active asset-tab--renaming"
              @submit.prevent="commitTabRename"
              @keydown.esc.prevent="cancelTabRename"
            >
              <span class="asset-tab-icon">{{ readAssetTypeIcon(item.type) }}</span>
              <input
                :ref="setRenameInputRef"
                v-model="renamingTitle"
                class="asset-tab-rename-input"
                type="text"
                aria-label="重命名笔记"
                @click.stop
                @dblclick.stop
                @blur="commitTabRename"
              >
              <span
                v-if="readDraftSaveStatus(item.draftId) === 'unsaved'"
                class="asset-status-dot"
                aria-label="未保存"
              ></span>
            </form>
            <button
              v-else
              type="button"
              class="asset-tab"
              :class="{ 'asset-tab--active': item.draftId === activeDraftId }"
              :aria-current="item.draftId === activeDraftId ? 'page' : undefined"
              title="双击重命名"
              @click="selectDraft(item.draftId)"
              @dblclick.stop="startTabRename(item)"
            >
              <span class="asset-tab-icon">{{ readAssetTypeIcon(item.type) }}</span>
              <span class="asset-tab-title">{{ item.title }}</span>
              <span
                v-if="readDraftSaveStatus(item.draftId) === 'unsaved'"
                class="asset-status-dot"
                aria-label="未保存"
              ></span>
            </button>
          </template>
        </nav>
        <button
          v-if="drafts.length > 1"
          type="button"
          class="asset-tab-scroll"
          aria-label="向右滚动学习资产标签"
          @click="scrollAssetTabs('right')"
        >
          ›
        </button>
        <div class="asset-tab-actions">
          <div class="workspace-create asset-tab-create">
            <button
              type="button"
              class="asset-tab-add"
              :aria-expanded="createMenuOpen"
              aria-haspopup="menu"
              aria-label="新建学习资产"
              @click="toggleCreateMenu"
            >
              +
            </button>
            <div v-if="createMenuOpen" class="workspace-create-menu" role="menu">
              <button
                v-for="option in createAssetOptions"
                :key="option.type"
                type="button"
                role="menuitem"
                @click="createDraftAndClose(option.type)"
              >
                <span class="create-option-icon">{{ readAssetTypeIcon(option.type) }}</span>
                <span class="create-option-copy">
                  <span>{{ option.label }}</span>
                  <small>{{ option.hint }}</small>
                </span>
              </button>
            </div>
          </div>
          <button type="button" class="asset-close-button" aria-label="关闭学习资产画布" @click="$emit('close')">×</button>
        </div>
      </div>
    </header>

    <section class="canvas-toolbar" aria-label="画布操作">
      <div class="copilot-entry">
        <button
          type="button"
          class="copilot-button"
          :aria-expanded="copilotMenuOpen"
          :disabled="isOrganizing"
          @click="toggleCopilotMenu"
        >
          {{ isOrganizing ? '整理中' : 'Copilot ✦' }}
        </button>
        <div v-if="copilotMenuOpen" class="copilot-menu" aria-label="学习资产 Copilot">
          <div class="copilot-menu-header">
            <strong>Copilot</strong>
            <span>按当前{{ readAssetTypeLabel(draft.type) }}模板整理</span>
          </div>
          <p>只处理当前资产，不进入左侧学习对话。结果会先生成候选建议。</p>
          <div class="copilot-actions">
            <button type="button" class="primary-action" @click="runCopilotAction('complete')">补全空白</button>
            <button type="button" @click="runCopilotAction('expand')">扩展内容</button>
            <button type="button" @click="runCopilotAction('polish')">润色语句</button>
            <button type="button" @click="runCopilotAction('organize')">按模板整理</button>
            <button type="button" @click="runCopilotAction('format')">优化格式</button>
            <button type="button" @click="runCopilotAction('examples')">生成例句</button>
          </div>
          <form class="copilot-instruction" @submit.prevent="runCopilotAction('custom')">
            <label for="learning-asset-copilot-input">告诉 Copilot 你想怎么整理</label>
            <div>
              <input
                id="learning-asset-copilot-input"
                v-model="copilotInstruction"
                type="text"
                placeholder="把这张卡改成适合复习的版本..."
              >
              <button type="submit" :disabled="!copilotInstruction.trim()" aria-label="提交 Copilot 指令">↵</button>
            </div>
          </form>
        </div>
      </div>
      <span class="auto-save-status" :class="`auto-save-status--${saveStatus}`" aria-live="polite">{{ autoSaveStatusLabel }}</span>
    </section>
    <input
      ref="imageInputRef"
      class="image-input"
      type="file"
      accept="image/*"
      multiple
      aria-label="添加图片到学习资产"
      @change="handleImageFileChange"
    >

    <p v-if="visibleErrorMessage" class="canvas-error">{{ visibleErrorMessage }}</p>

    <div ref="editorShellRef" class="editor-shell">
      <div
        v-if="floatingToolbarVisible"
        class="floating-format-toolbar"
        :style="floatingToolbarStyle"
        aria-label="文字格式工具"
        @mousedown.prevent
      >
        <button type="button" aria-label="加粗" @click="applyInlineFormat('bold')">B</button>
        <button type="button" aria-label="斜体" @click="applyInlineFormat('italic')">I</button>
        <button type="button" aria-label="二级标题" @click="applyInlineFormat('heading')">H2</button>
        <button type="button" aria-label="引用" @click="applyInlineFormat('blockquote')">引</button>
        <button type="button" aria-label="列表" @click="applyInlineFormat('list')">•</button>
        <button type="button" aria-label="行内代码" @click="applyInlineFormat('code')">`</button>
      </div>

      <button
        type="button"
        class="insert-block-button"
        :aria-expanded="insertMenuOpen"
        aria-label="插入内容"
        @mousedown.prevent
        @click="toggleInsertMenu"
      >
        +
      </button>
      <div v-if="insertMenuOpen" class="insert-block-menu" aria-label="插入菜单" @mousedown.prevent>
        <button type="button" @click="openImagePickerFromInsert">图片</button>
        <button type="button" @click="insertMarkdownSnippet('example')">例句块</button>
        <button type="button" @click="insertMarkdownSnippet('divider')">分割线</button>
        <button type="button" @click="insertMarkdownSnippet('table')">表格</button>
      </div>

      <div
        ref="editableMarkdownRef"
        class="markdown-preview markdown-editor"
        contenteditable="true"
        role="textbox"
        aria-multiline="true"
        spellcheck="false"
        aria-label="学习资产 Markdown 正文"
        data-placeholder="在这里整理你的学习笔记"
        @input="handleEditableMarkdownInput"
        @click="handleMarkdownPreviewClick"
        @focus="refreshFloatingToolbar"
        @blur="hideFloatingToolbarSoon"
        @mouseup="refreshFloatingToolbar"
        @keyup="refreshFloatingToolbar"
        @paste="handleImagePaste"
        @drop="handleImageDrop"
        @dragover.prevent
        @dragenter.prevent
      ></div>
    </div>

    <section v-if="candidateMarkdown" class="candidate-panel" aria-label="Copilot 建议">
      <header>
        <div class="candidate-title">
          <strong>Copilot 建议</strong>
          <span>先预览，再决定如何应用</span>
        </div>
        <div>
          <button type="button" @click="$emit('cancelCandidate')">取消候选</button>
          <button type="button" class="apply-button" @click="$emit('applyCandidate', 'fill')">只填空白</button>
          <button type="button" @click="$emit('applyCandidate', 'append')">追加到正文</button>
          <button type="button" @click="$emit('applyCandidate', 'replace')">覆盖正文</button>
        </div>
      </header>
      <div
        class="candidate-preview markdown-preview"
        v-html="renderAssistantMarkdown(candidateMarkdown)"
        @click="handleMarkdownPreviewClick"
      ></div>
    </section>

    </template>

    <template v-else>
      <header class="canvas-workspace-header canvas-workspace-header--empty" aria-label="学习资产画布空态">
        <button type="button" class="asset-close-button" aria-label="关闭学习资产画布" @click="$emit('close')">×</button>
      </header>
      <section class="canvas-empty-state">
        <p class="empty-eyebrow">学习资产画布</p>
        <h2>边问边整理笔记</h2>
        <p>选择左侧内容创建单词卡、语法笔记，或先新建一张空白笔记开始整理。</p>
        <div class="empty-actions">
          <button type="button" class="empty-action-primary" @click="createDraftAndClose('vocabulary')">新建单词卡</button>
          <button type="button" @click="createDraftAndClose('grammar')">新建语法笔记</button>
          <button type="button" @click="createDraftAndClose('sentence')">新建句子笔记</button>
          <button type="button" @click="createDraftAndClose('expression')">新建空白笔记</button>
        </div>
      </section>
    </template>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch, type ComponentPublicInstance } from 'vue'

import { copyMarkdownCodeFromClick, renderAssistantMarkdown } from './markdown.ts'
import {
  learningAssetTypeLabels,
  type LearningAssetCopilotAction,
  type LearningAssetCopilotRequest,
  type LearningAssetDraft,
  type LearningAssetType,
} from '../../types/learningAssets.ts'

const MIN_CANVAS_WIDTH = 360
const MAX_CANVAS_WIDTH = 720
const RESIZE_KEY_STEP = 32
const MAX_INLINE_IMAGE_BYTES = 4 * 1024 * 1024
const SUPPORTED_INLINE_IMAGE_TYPES = new Set(['image/png', 'image/jpeg', 'image/gif', 'image/webp'])

const props = defineProps<{
  draft: LearningAssetDraft | null
  drafts: LearningAssetDraft[]
  activeDraftId: string
  candidateMarkdown: string
  isOrganizing: boolean
  saveStatus: 'unsaved' | 'saving' | 'saved' | 'failed'
  saveStatusByDraftId: Record<string, 'unsaved' | 'saving' | 'saved' | 'failed'>
  errorMessage: string
  widthPx: number
}>()

const emit = defineEmits<{
  close: []
  organize: [request: LearningAssetCopilotRequest]
  selectDraft: [draftId: string]
  renameDraft: [draftId: string, title: string]
  createEmptyDraft: [type: LearningAssetType]
  applyCandidate: [mode: 'replace' | 'append' | 'fill']
  cancelCandidate: []
  'update:title': [title: string]
  'update:contentMarkdown': [contentMarkdown: string]
  'resize:width': [widthPx: number]
}>()

const markdownViewMode = ref<'edit'>('edit')
const createMenuOpen = ref(false)
const copilotMenuOpen = ref(false)
const copilotInstruction = ref('')
const insertMenuOpen = ref(false)
const floatingToolbarVisible = ref(false)
const floatingToolbarStyle = ref({ left: '50%', top: '12px' })
const editorShellRef = ref<HTMLElement | null>(null)
const editableMarkdownRef = ref<HTMLElement | null>(null)
const assetTabListRef = ref<HTMLElement | null>(null)
const renameInputRef = ref<HTMLInputElement | null>(null)
const imageInputRef = ref<HTMLInputElement | null>(null)
const imageErrorMessage = ref('')
const renamingDraftId = ref('')
const renamingTitle = ref('')
const canvasStyle = computed(() => ({
  '--canvas-width': `${props.widthPx}px`,
}))
const visibleErrorMessage = computed(() => imageErrorMessage.value || props.errorMessage)
const createAssetOptions = [
  { type: 'vocabulary', label: '新建单词卡', hint: '单词、短语、搭配' },
  { type: 'grammar', label: '新建语法笔记', hint: '规则、结构、用法' },
  { type: 'sentence', label: '新建句子笔记', hint: '整句、句型、拆解' },
  { type: 'expression', label: '新建空白笔记', hint: '自由整理内容' },
] satisfies Array<{ type: LearningAssetType; label: string; hint: string }>
const autoSaveStatusLabel = computed(() => {
  if (props.saveStatus === 'saving') return '自动保存中'
  if (props.saveStatus === 'saved') return '已自动保存'
  if (props.saveStatus === 'failed') return '自动保存失败'
  return '等待自动保存'
})

let resizeStartX = 0
let resizeStartWidth = 0
let hideToolbarTimer: number | null = null

function clampCanvasWidth(width: number) {
  return Math.min(MAX_CANVAS_WIDTH, Math.max(MIN_CANVAS_WIDTH, Math.round(width)))
}

function readClientX(event: MouseEvent | TouchEvent) {
  return 'touches' in event ? event.touches[0]?.clientX ?? resizeStartX : event.clientX
}

function startResize(event: MouseEvent | TouchEvent) {
  resizeStartX = readClientX(event)
  resizeStartWidth = props.widthPx
  document.body.classList.add('learning-asset-resizing')
  document.addEventListener('mousemove', handleResizeMove)
  document.addEventListener('mouseup', stopResize)
  document.addEventListener('touchmove', handleResizeMove, { passive: false })
  document.addEventListener('touchend', stopResize)
}

function handleResizeMove(event: MouseEvent | TouchEvent) {
  event.preventDefault()
  const clientX = readClientX(event)
  const nextWidth = resizeStartWidth + resizeStartX - clientX
  emit('resize:width', clampCanvasWidth(nextWidth))
}

function stopResize() {
  document.body.classList.remove('learning-asset-resizing')
  document.removeEventListener('mousemove', handleResizeMove)
  document.removeEventListener('mouseup', stopResize)
  document.removeEventListener('touchmove', handleResizeMove)
  document.removeEventListener('touchend', stopResize)
}

function handleResizeKeydown(event: KeyboardEvent) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  event.preventDefault()
  const direction = event.key === 'ArrowLeft' ? 1 : -1
  emit('resize:width', clampCanvasWidth(props.widthPx + direction * RESIZE_KEY_STEP))
}

function readAssetTypeLabel(type: LearningAssetType) {
  return learningAssetTypeLabels[type] ?? '学习笔记'
}

function readAssetTypeIcon(type: LearningAssetType) {
  const icons = {
    vocabulary: 'Aa',
    grammar: 'G',
    sentence: 'S',
    expression: 'N',
  } satisfies Record<LearningAssetType, string>
  return icons[type]
}

function readDraftSaveStatus(draftId: string) {
  return props.saveStatusByDraftId[draftId] ?? 'unsaved'
}

function toggleCreateMenu() {
  createMenuOpen.value = !createMenuOpen.value
  if (createMenuOpen.value) copilotMenuOpen.value = false
}

function selectDraft(draftId: string) {
  emit('selectDraft', draftId)
  createMenuOpen.value = false
}

function setRenameInputRef(element: Element | ComponentPublicInstance | null) {
  renameInputRef.value = element instanceof HTMLInputElement ? element : null
}

async function startTabRename(item: LearningAssetDraft) {
  renamingDraftId.value = item.draftId
  renamingTitle.value = item.title
  emit('selectDraft', item.draftId)
  createMenuOpen.value = false
  await nextTick()
  renameInputRef.value?.focus()
  renameInputRef.value?.select()
}

function commitTabRename() {
  const draftId = renamingDraftId.value
  if (!draftId) return
  const draft = props.drafts.find((item) => item.draftId === draftId)
  const nextTitle = renamingTitle.value.trim()
  renamingDraftId.value = ''
  renamingTitle.value = ''
  if (!draft || !nextTitle || nextTitle === draft.title) return
  emit('renameDraft', draftId, nextTitle)
}

function cancelTabRename() {
  renamingDraftId.value = ''
  renamingTitle.value = ''
}

function scrollAssetTabs(direction: 'left' | 'right') {
  const tabList = assetTabListRef.value
  if (!tabList) return
  const scrollDistance = Math.max(160, Math.round(tabList.clientWidth * 0.72))
  tabList.scrollBy({
    left: direction === 'left' ? -scrollDistance : scrollDistance,
    behavior: 'smooth',
  })
}

function createDraftAndClose(type: LearningAssetType) {
  emit('createEmptyDraft', type)
  createMenuOpen.value = false
}

function toggleCopilotMenu() {
  copilotMenuOpen.value = !copilotMenuOpen.value
  if (copilotMenuOpen.value) createMenuOpen.value = false
}

function runCopilotAction(action: LearningAssetCopilotAction) {
  const instruction = copilotInstruction.value.trim()
  if (action === 'custom' && !instruction) return
  copilotMenuOpen.value = false
  emit('organize', {
    action,
    instruction: instruction || undefined,
  })
  if (action === 'custom') copilotInstruction.value = ''
}

function openImagePicker() {
  imageErrorMessage.value = ''
  imageInputRef.value?.click()
}

function openImagePickerFromInsert() {
  insertMenuOpen.value = false
  openImagePicker()
}

function toggleInsertMenu() {
  insertMenuOpen.value = !insertMenuOpen.value
  clearFloatingToolbarHideTimer()
}

async function applyInlineFormat(format: 'bold' | 'italic' | 'heading' | 'blockquote' | 'list' | 'code') {
  clearFloatingToolbarHideTimer()
  ensureEditorSelection()

  if (format === 'bold') document.execCommand('bold')
  if (format === 'italic') document.execCommand('italic')
  if (format === 'heading') document.execCommand('formatBlock', false, 'h2')
  if (format === 'blockquote') document.execCommand('formatBlock', false, 'blockquote')
  if (format === 'list') document.execCommand('insertUnorderedList')
  if (format === 'code') wrapSelectionWithInlineCode()

  emitMarkdownFromEditable()
  await nextTick()
  refreshFloatingToolbar()
}

async function insertMarkdownSnippet(kind: 'example' | 'divider' | 'table') {
  const draft = props.draft
  if (!draft) return
  const snippets = {
    example: '> 例句：\n>\n> 我的理解：',
    divider: '---',
    table: '| 项目 | 内容 |\n| --- | --- |\n|  |  |',
  } satisfies Record<typeof kind, string>
  const nextMarkdown = appendMarkdownBlock(draft.contentMarkdown, snippets[kind])
  insertMenuOpen.value = false
  markdownViewMode.value = 'edit'
  emit('update:contentMarkdown', nextMarkdown)
  await nextTick()
  syncEditableMarkdownHtml(nextMarkdown)
  editableMarkdownRef.value?.focus()
}

function ensureEditorSelection() {
  const editor = editableMarkdownRef.value
  const selection = window.getSelection()
  if (!editor || isSelectionInsideEditor(selection)) return
  editor.focus()
}

function wrapSelectionWithInlineCode() {
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed || !isSelectionInsideEditor(selection)) return

  const range = selection.getRangeAt(0)
  const code = document.createElement('code')
  code.textContent = range.toString()
  range.deleteContents()
  range.insertNode(code)

  const nextRange = document.createRange()
  nextRange.selectNodeContents(code)
  selection.removeAllRanges()
  selection.addRange(nextRange)
}

function emitMarkdownFromEditable() {
  const editor = editableMarkdownRef.value
  if (!editor) return
  emit('update:contentMarkdown', serializeEditableMarkdown(editor))
}

function refreshFloatingToolbar() {
  if (markdownViewMode.value !== 'edit') {
    floatingToolbarVisible.value = false
    return
  }

  const selection = window.getSelection()
  const editorShell = editorShellRef.value
  if (!selection || !editorShell || selection.rangeCount === 0 || selection.isCollapsed || !isSelectionInsideEditor(selection)) {
    floatingToolbarVisible.value = false
    return
  }

  const rangeRect = selection.getRangeAt(0).getBoundingClientRect()
  if (rangeRect.width === 0 && rangeRect.height === 0) {
    floatingToolbarVisible.value = false
    return
  }

  const shellRect = editorShell.getBoundingClientRect()
  const left = clampNumber(rangeRect.left - shellRect.left + rangeRect.width / 2, 86, shellRect.width - 86)
  const top = Math.max(12, rangeRect.top - shellRect.top - 8)
  floatingToolbarStyle.value = {
    left: `${left}px`,
    top: `${top}px`,
  }
  floatingToolbarVisible.value = true
}

function hideFloatingToolbarSoon() {
  clearFloatingToolbarHideTimer()
  hideToolbarTimer = window.setTimeout(() => {
    floatingToolbarVisible.value = false
    insertMenuOpen.value = false
  }, 160)
}

function clearFloatingToolbarHideTimer() {
  if (!hideToolbarTimer) return
  window.clearTimeout(hideToolbarTimer)
  hideToolbarTimer = null
}

function isSelectionInsideEditor(selection: Selection | null) {
  const editor = editableMarkdownRef.value
  const anchorNode = selection?.anchorNode
  if (!editor || !anchorNode) return false
  return editor.contains(anchorNode.nodeType === Node.ELEMENT_NODE ? anchorNode : anchorNode.parentNode)
}

function clampNumber(value: number, min: number, max: number) {
  if (max < min) return min
  return Math.min(max, Math.max(min, value))
}

async function handleImageFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  await insertImageFiles(input.files)
  input.value = ''
}

async function handleImagePaste(event: ClipboardEvent) {
  const files = readImageFilesFromDataTransfer(event.clipboardData)
  if (files.length === 0) return
  event.preventDefault()
  await insertImageFiles(files)
}

async function handleImageDrop(event: DragEvent) {
  const files = readImageFilesFromDataTransfer(event.dataTransfer)
  if (files.length === 0) return
  event.preventDefault()
  await insertImageFiles(files)
}

function readImageFilesFromDataTransfer(dataTransfer: DataTransfer | null): File[] {
  const droppedFiles = Array.from(dataTransfer?.files ?? []).filter(isSupportedImageFile)
  if (droppedFiles.length > 0) return droppedFiles

  const itemFiles: File[] = []
  for (const item of Array.from(dataTransfer?.items ?? [])) {
    if (item.kind !== 'file') continue
    const file = item.getAsFile()
    if (!file || !isSupportedImageFile(file)) continue
    itemFiles.push(file)
  }
  return itemFiles
}

async function insertImageFiles(files: FileList | File[] | null) {
  const draft = props.draft
  if (!draft) return
  const imageFiles = Array.from(files ?? []).filter(isSupportedImageFile)
  if (imageFiles.length === 0) {
    imageErrorMessage.value = '请选择 PNG、JPG、GIF 或 WebP 图片'
    return
  }

  const oversizedFile = imageFiles.find((file) => file.size > MAX_INLINE_IMAGE_BYTES)
  if (oversizedFile) {
    imageErrorMessage.value = `${oversizedFile.name} 超过 4MB，先压缩后再添加`
    return
  }

  try {
    const imageBlocks = await Promise.all(
      imageFiles.map(async (file) => createMarkdownImage(readImageAltText(file.name), await readFileAsDataUrl(file))),
    )
    const nextMarkdown = appendMarkdownBlock(draft.contentMarkdown, imageBlocks.join('\n\n'))
    imageErrorMessage.value = ''
    markdownViewMode.value = 'edit'
    emit('update:contentMarkdown', nextMarkdown)
    await nextTick()
    syncEditableMarkdownHtml(nextMarkdown)
    editableMarkdownRef.value?.focus()
  } catch {
    imageErrorMessage.value = '图片读取失败，请换一张图片试试'
  }
}

function isSupportedImageFile(file: File): boolean {
  return SUPPORTED_INLINE_IMAGE_TYPES.has(file.type)
}

function readImageAltText(fileName: string): string {
  return sanitizeMarkdownImageAlt(fileName.replace(/\.[^.]+$/, ''))
}

function createMarkdownImage(alt: string, source: string): string {
  return `![${sanitizeMarkdownImageAlt(alt)}](${source})`
}

function sanitizeMarkdownImageAlt(text: string): string {
  return text.replace(/[\r\n[\]()]/g, ' ').replace(/\s+/g, ' ').trim() || '图片'
}

function appendMarkdownBlock(markdown: string, block: string): string {
  const base = markdown.trimEnd()
  return base ? `${base}\n\n${block}` : block
}

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(reader.error ?? new Error('无法读取图片'))
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.readAsDataURL(file)
  })
}

function syncEditableMarkdownFromDraft() {
  syncEditableMarkdownHtml(props.draft?.contentMarkdown ?? '')
}

function syncEditableMarkdownHtml(markdown: string) {
  const editor = editableMarkdownRef.value
  if (!editor) return
  const renderedMarkdown = renderAssistantMarkdown(markdown)
  if (editor.innerHTML !== renderedMarkdown) editor.innerHTML = renderedMarkdown
}

function handleEditableMarkdownInput(event: Event) {
  emit('update:contentMarkdown', serializeEditableMarkdown(event.currentTarget as HTMLElement))
}

function handleMarkdownPreviewClick(event: MouseEvent) {
  void copyMarkdownCodeFromClick(event)
}

function serializeEditableMarkdown(root: HTMLElement): string {
  return Array.from(root.childNodes)
    .map((node) => serializeBlockNode(node))
    .filter(Boolean)
    .join('\n\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function serializeBlockNode(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) return normalizeMarkdownText(node.textContent ?? '')
  if (!(node instanceof HTMLElement)) return ''

  const tagName = node.tagName.toLowerCase()
  if (node.classList.contains('markdown-table-scroll')) {
    return serializeEditableTable(node.querySelector('table'))
  }
  if (node.classList.contains('markdown-code-block')) {
    return serializeEditableCodeBlock(node)
  }

  if (/^h[1-6]$/.test(tagName)) {
    return `${'#'.repeat(Number(tagName.slice(1)))} ${serializeInlineChildren(node).trim()}`.trim()
  }
  if (tagName === 'p') return serializeInlineChildren(node).trim()
  if (tagName === 'br') return ''
  if (tagName === 'hr') return '---'
  if (tagName === 'ul') return serializeEditableList(node, false)
  if (tagName === 'ol') return serializeEditableList(node, true)
  if (tagName === 'blockquote') return serializeEditableBlockquote(node)
  if (tagName === 'table') return serializeEditableTable(node as HTMLTableElement)
  if (tagName === 'pre') return serializeEditableCodeBlock(node)

  const childBlocks = Array.from(node.childNodes)
    .map((child) => serializeBlockNode(child))
    .filter(Boolean)
  return childBlocks.length > 0 ? childBlocks.join('\n\n') : serializeInlineChildren(node).trim()
}

function serializeInlineChildren(element: HTMLElement): string {
  return Array.from(element.childNodes).map((node) => serializeInlineNode(node)).join('')
}

function serializeInlineNode(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent?.replace(/\u00a0/g, ' ') ?? ''
  if (!(node instanceof HTMLElement)) return ''

  const tagName = node.tagName.toLowerCase()
  if (tagName === 'img') {
    return createMarkdownImage(node.getAttribute('alt') ?? '图片', node.getAttribute('src') ?? '')
  }
  if (tagName === 'br') return '\n'
  if (tagName === 'button' && node.hasAttribute('data-markdown-code-copy')) return ''

  const content = serializeInlineChildren(node).trim()
  if (!content) return ''
  if (tagName === 'strong' || tagName === 'b') return `**${content}**`
  if (tagName === 'em' || tagName === 'i') return `*${content}*`
  if (tagName === 'code') return `\`${content}\``
  return content
}

function serializeEditableList(list: HTMLElement, ordered: boolean): string {
  return Array.from(list.children)
    .filter((child): child is HTMLElement => child instanceof HTMLElement && child.tagName.toLowerCase() === 'li')
    .map((item, index) => {
      const content = serializeInlineChildren(item).trim()
      return ordered ? `${index + 1}. ${content}` : `- ${content}`
    })
    .join('\n')
}

function serializeEditableBlockquote(blockquote: HTMLElement): string {
  const quote = Array.from(blockquote.childNodes)
    .map((child) => serializeBlockNode(child))
    .filter(Boolean)
    .join('\n\n')
  return quote
    .split('\n')
    .map((line) => `> ${line}`)
    .join('\n')
}

function serializeEditableCodeBlock(codeBlock: HTMLElement): string {
  const language = codeBlock.querySelector('.markdown-code-header span')?.textContent?.trim()
  const code = codeBlock.querySelector('pre code')?.textContent ?? codeBlock.textContent ?? ''
  const fenceLabel = language && language !== 'text' ? language : ''
  return `\`\`\`${fenceLabel}\n${code.trimEnd()}\n\`\`\``
}

function serializeEditableTable(table: HTMLTableElement | null): string {
  if (!table) return ''
  const rows = Array.from(table.querySelectorAll('tr')).map((row) =>
    Array.from(row.querySelectorAll('th,td')).map((cell) => escapeMarkdownTableCell(cell.textContent ?? '')),
  )
  const header = rows[0]
  if (!header || header.length === 0) return ''
  return [
    `| ${header.join(' | ')} |`,
    `| ${header.map(() => '---').join(' | ')} |`,
    ...rows.slice(1).map((row) => `| ${row.join(' | ')} |`),
  ].join('\n')
}

function escapeMarkdownTableCell(text: string): string {
  return normalizeMarkdownText(text).replace(/\|/g, '\\|')
}

function normalizeMarkdownText(text: string): string {
  return text.replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim()
}

watch(
  [markdownViewMode, () => props.draft?.contentMarkdown ?? ''],
  async () => {
    if (markdownViewMode.value !== 'edit') return
    await nextTick()
    if (document.activeElement === editableMarkdownRef.value) return
    syncEditableMarkdownFromDraft()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  stopResize()
  clearFloatingToolbarHideTimer()
})
</script>

<style scoped>
.learning-asset-canvas {
  position: relative;
  display: flex;
  flex: 0 0 var(--canvas-width);
  width: var(--canvas-width);
  max-width: 720px;
  min-width: 360px;
  height: 100vh;
  min-height: 0;
  flex-direction: column;
  border-left: 1px solid #dbe3ea;
  background: #ffffff;
  box-sizing: border-box;
}

.resize-handle {
  position: absolute;
  top: 0;
  bottom: 0;
  left: -10px;
  z-index: 5;
  width: 20px;
  border: 0;
  border-radius: 0;
  background: transparent;
  cursor: col-resize;
}

.resize-handle::after {
  position: absolute;
  top: 50%;
  left: 9px;
  width: 3px;
  height: 46px;
  border-radius: 999px;
  background: #cbd5e1;
  content: '';
  transform: translateY(-50%);
}

.resize-handle:hover::after,
.resize-handle:focus-visible::after {
  background: #047857;
}

.resize-handle:focus-visible {
  outline: none;
}

:global(.learning-asset-resizing) {
  cursor: col-resize;
  user-select: none;
}

.canvas-toolbar,
.candidate-panel header,
.candidate-panel header div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.canvas-workspace-header {
  position: relative;
  display: flex;
  min-height: 54px;
  flex: 0 0 auto;
  flex-direction: column;
  border-bottom: 1px solid #e2e8f0;
  background: #fbfdff;
  box-sizing: border-box;
}

.canvas-workspace-header--empty {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 4;
  min-height: 0;
  border-bottom: 0;
  background: transparent;
}

.asset-tab-rail {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
}

.workspace-create {
  position: relative;
}

.asset-close-button,
.asset-tab-add,
.asset-tab-scroll {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #f1f5f9;
  color: #334155;
  font-size: 21px;
  font-weight: 800;
  cursor: pointer;
}

.asset-tab-add:hover,
.asset-tab-add:focus-visible,
.asset-tab-add[aria-expanded='true'] {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
  outline: none;
}

.asset-tab-scroll {
  width: 28px;
  height: 34px;
  border-color: #e2e8f0;
  background: #ffffff;
  color: #64748b;
  font-size: 20px;
}

.asset-tab-scroll:hover,
.asset-tab-scroll:focus-visible {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
  outline: none;
}

.asset-close-button:hover,
.asset-close-button:focus-visible {
  background: #e2e8f0;
  outline: none;
}

.canvas-workspace-header--empty .asset-close-button {
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.workspace-create-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 35;
  display: grid;
  width: 216px;
  gap: 3px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.16);
  padding: 6px;
}

.workspace-create-menu button {
  display: grid;
  min-height: 42px;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #334155;
  padding: 6px 8px;
  cursor: pointer;
  text-align: left;
}

.workspace-create-menu button:hover,
.workspace-create-menu button:focus-visible {
  background: #f0fdf4;
  color: #047857;
  outline: none;
}

.create-option-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: #eef2f7;
  color: #334155;
  font-size: 11px;
  font-weight: 950;
}

.create-option-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.create-option-copy span {
  overflow: hidden;
  color: #0f172a;
  font-size: 13px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.create-option-copy small {
  overflow: hidden;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-tab-list {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  gap: 6px;
  overflow-x: auto;
  padding: 0;
  scroll-behavior: smooth;
  scrollbar-width: thin;
}

.asset-tab-actions {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.asset-tab {
  display: inline-grid;
  min-width: 128px;
  max-width: 220px;
  height: 34px;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 7px;
  border: 1px solid #dbe3ea;
  border-radius: 7px;
  background: #ffffff;
  color: #334155;
  margin: 0;
  padding: 0 9px;
  cursor: pointer;
  text-align: left;
}

.asset-tab:hover,
.asset-tab:focus-visible {
  border-color: #94a3b8;
  outline: none;
}

.asset-tab--active {
  border-color: #047857;
  box-shadow: inset 0 -2px 0 #047857;
}

.asset-tab--renaming {
  box-shadow: inset 0 -2px 0 #047857;
  cursor: text;
}

.asset-tab-icon {
  color: #64748b;
  font-size: 11px;
  font-weight: 950;
}

.asset-tab-title {
  min-width: 0;
  overflow: hidden;
  color: #0f172a;
  font-size: 13px;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-tab-rename-input {
  min-width: 0;
  width: 100%;
  border: 0;
  background: transparent;
  color: #0f172a;
  font: inherit;
  font-size: 13px;
  font-weight: 850;
  outline: none;
}

.asset-tab-rename-input::selection {
  background: #bfdbfe;
}

.asset-status-dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #f59e0b;
}

.canvas-empty-state {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  justify-content: center;
  padding: 36px 38px;
  background:
    linear-gradient(180deg, rgba(239, 246, 255, 0.72), rgba(255, 255, 255, 0) 42%),
    #ffffff;
  box-sizing: border-box;
}

.empty-eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
}

.canvas-empty-state h2 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
  line-height: 1.2;
}

.canvas-empty-state p:not(.empty-eyebrow) {
  max-width: 320px;
  margin: 14px 0 0;
  color: #64748b;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.7;
}

.empty-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.empty-actions button {
  min-height: 36px;
  border: 1px solid #dbe3ea;
  border-radius: 7px;
  background: #ffffff;
  color: #334155;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 850;
  cursor: pointer;
}

.empty-actions button:hover,
.empty-actions button:focus-visible {
  border-color: #047857;
  color: #047857;
  outline: none;
}

.empty-actions .empty-action-primary {
  border-color: #047857;
  background: #047857;
  color: #ffffff;
}

.empty-actions .empty-action-primary:hover,
.empty-actions .empty-action-primary:focus-visible {
  background: #065f46;
  color: #ffffff;
}

.canvas-toolbar {
  position: relative;
  flex-wrap: wrap;
  padding: 12px 18px;
  border-bottom: 1px solid #e2e8f0;
}

.copilot-entry {
  position: relative;
}

.canvas-toolbar .copilot-button {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.canvas-toolbar .copilot-button:hover,
.canvas-toolbar .copilot-button:focus-visible,
.canvas-toolbar .copilot-button[aria-expanded='true'] {
  border-color: #2563eb;
  outline: none;
}

.copilot-menu {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  z-index: 35;
  width: min(360px, calc(var(--canvas-width) - 36px));
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.16);
  padding: 12px;
}

.copilot-menu-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.copilot-menu-header strong {
  color: #0f172a;
  font-size: 14px;
}

.copilot-menu-header span,
.copilot-menu p,
.copilot-instruction label {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.copilot-menu p {
  margin: 8px 0 10px;
}

.copilot-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.canvas-toolbar .copilot-actions button {
  min-height: 34px;
  justify-content: center;
  padding: 0 8px;
  font-size: 12px;
}

.canvas-toolbar .copilot-actions .primary-action {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #2563eb;
}

.copilot-instruction {
  display: grid;
  gap: 6px;
  margin-top: 12px;
}

.copilot-instruction div {
  display: flex;
  gap: 6px;
}

.copilot-instruction input {
  flex: 1;
  min-width: 0;
  border: 1px solid #dbe3ea;
  border-radius: 7px;
  background: #f8fafc;
  color: #0f172a;
  padding: 0 10px;
  font-size: 13px;
  outline: none;
}

.copilot-instruction input:focus {
  border-color: #2563eb;
  background: #ffffff;
}

.canvas-toolbar .copilot-instruction button {
  min-width: 34px;
  padding: 0;
  border-color: #047857;
  background: #047857;
  color: #ffffff;
}

.canvas-toolbar button,
.candidate-panel button {
  min-height: 34px;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  background: #ffffff;
  color: #334155;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.canvas-toolbar button:disabled,
.candidate-panel button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.candidate-panel .apply-button {
  border-color: #047857;
  background: #047857;
  color: #ffffff;
}

.auto-save-status {
  display: inline-flex;
  align-items: center;
  margin-left: auto;
  min-height: 28px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  padding: 0 9px;
  font-size: 12px;
  font-weight: 800;
}

.auto-save-status--saving {
  background: #eff6ff;
  color: #2563eb;
}

.auto-save-status--saved {
  background: #ecfdf5;
  color: #047857;
}

.auto-save-status--failed {
  background: #fef2f2;
  color: #991b1b;
}

.image-input {
  display: none;
}

.canvas-error {
  margin: 12px 18px 0;
  border-radius: 6px;
  background: #fef2f2;
  color: #991b1b;
  padding: 10px 12px;
  font-size: 13px;
}

.editor-shell {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  border-bottom: 1px solid #e2e8f0;
  background: #fbfdff;
}

.floating-format-toolbar {
  position: absolute;
  z-index: 35;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: 1px solid #dbe3ea;
  border-radius: 7px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.14);
  padding: 4px;
  transform: translate(-50%, -100%);
}

.floating-format-toolbar button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 28px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #334155;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
}

.floating-format-toolbar button:hover,
.floating-format-toolbar button:focus-visible {
  background: #ecfdf5;
  color: #047857;
  outline: none;
}

.insert-block-button {
  position: absolute;
  top: 20px;
  left: 14px;
  z-index: 25;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid #dbe3ea;
  border-radius: 7px;
  background: #ffffff;
  color: #64748b;
  font-size: 19px;
  font-weight: 700;
  cursor: pointer;
  opacity: 0.72;
}

.insert-block-button:hover,
.insert-block-button:focus-visible,
.insert-block-button[aria-expanded='true'] {
  border-color: #047857;
  color: #047857;
  opacity: 1;
  outline: none;
}

.insert-block-menu {
  position: absolute;
  top: 56px;
  left: 14px;
  z-index: 30;
  display: grid;
  width: 132px;
  gap: 2px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14);
  padding: 6px;
}

.insert-block-menu button {
  min-height: 32px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #334155;
  padding: 0 9px;
  font-size: 13px;
  font-weight: 850;
  cursor: pointer;
  text-align: left;
}

.insert-block-menu button:hover,
.insert-block-menu button:focus-visible {
  background: #f0fdf4;
  color: #047857;
  outline: none;
}

.markdown-editor {
  flex: 1;
  min-height: 0;
  width: 100%;
  border: 0;
  background: #fbfdff;
  color: #0f172a;
  padding-left: 54px;
  outline: none;
  box-sizing: border-box;
  caret-color: #047857;
  cursor: text;
}

.markdown-editor:focus-visible {
  box-shadow: inset 0 0 0 2px rgba(4, 120, 87, 0.12);
}

.markdown-editor:empty::before {
  color: #94a3b8;
  content: attr(data-placeholder);
}

.markdown-editor :deep(.markdown-code-copy) {
  display: none;
}

.markdown-preview {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border-bottom: 1px solid #e2e8f0;
  background: #ffffff;
  color: #0f172a;
  padding: 20px 22px 28px;
  box-sizing: border-box;
  font-size: 15px;
  line-height: 1.75;
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3),
.markdown-preview :deep(h4) {
  margin: 18px 0 10px;
  color: #0f172a;
  line-height: 1.25;
}

.markdown-preview :deep(h1) {
  font-size: 28px;
}

.markdown-preview :deep(h2) {
  font-size: 22px;
}

.markdown-preview :deep(h3) {
  font-size: 18px;
}

.markdown-preview :deep(p) {
  margin: 0 0 14px;
}

.markdown-preview :deep(strong) {
  font-weight: 850;
}

.markdown-preview :deep(code) {
  border-radius: 5px;
  background: #eef2f7;
  color: #0f172a;
  padding: 2px 5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 0.94em;
}

.markdown-preview :deep(.markdown-image) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 14px 0 18px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.markdown-preview :deep(ul),
.markdown-preview :deep(ol) {
  margin: 0 0 16px;
  padding-left: 22px;
}

.markdown-preview :deep(li) {
  margin: 4px 0;
}

.markdown-preview :deep(blockquote) {
  margin: 14px 0;
  border-left: 3px solid #94a3b8;
  color: #475569;
  padding: 2px 0 2px 14px;
}

.markdown-preview :deep(hr) {
  margin: 20px 0;
  border: 0;
  border-top: 1px solid #e2e8f0;
}

.markdown-preview :deep(.markdown-table-scroll) {
  max-width: 100%;
  margin: 14px 0 18px;
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.markdown-preview :deep(table) {
  width: 100%;
  min-width: 520px;
  border-collapse: collapse;
  background: #ffffff;
}

.markdown-preview :deep(th),
.markdown-preview :deep(td) {
  border-bottom: 1px solid #e2e8f0;
  padding: 10px 12px;
  text-align: left;
  vertical-align: top;
}

.markdown-preview :deep(th) {
  background: #f8fafc;
  color: #334155;
  font-weight: 850;
}

.markdown-preview :deep(tr:last-child td) {
  border-bottom: 0;
}

.markdown-preview :deep(.markdown-code-block) {
  margin: 14px 0 18px;
  overflow: hidden;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.markdown-preview :deep(.markdown-code-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 34px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #64748b;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 800;
}

.markdown-preview :deep(.markdown-code-copy) {
  border: 1px solid #dbe3ea;
  border-radius: 999px;
  background: #ffffff;
  color: #334155;
  padding: 4px 9px;
  font-size: 12px;
  cursor: pointer;
}

.markdown-preview :deep(pre) {
  margin: 0;
  overflow: auto;
  padding: 14px;
}

.markdown-preview :deep(pre code) {
  background: transparent;
  color: #0f172a;
  padding: 0;
}

.candidate-panel {
  flex: 0 0 240px;
  min-height: 180px;
  overflow: hidden;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
}

.candidate-panel header {
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #e2e8f0;
}

.candidate-panel .candidate-title {
  display: grid;
  gap: 3px;
}

.candidate-panel strong {
  color: #0f172a;
  font-size: 13px;
}

.candidate-panel .candidate-title span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.candidate-preview {
  height: calc(100% - 59px);
  border-bottom: 0;
  background: #f8fafc;
  padding: 14px;
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 1024px) {
  .learning-asset-canvas {
    position: fixed;
    inset: 0 0 0 auto;
    z-index: 65;
    width: min(100vw, 420px);
    max-width: 100vw;
    min-width: 0;
  }

  .resize-handle {
    display: none;
  }
}
</style>
