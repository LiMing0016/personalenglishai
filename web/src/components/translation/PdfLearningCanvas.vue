<template>
  <div class="pdf-learning-canvas">
    <header class="pdf-canvas-toolbar" aria-label="PDF 学习画布工具栏">
      <div>
        <p>PDF 学习画布</p>
        <strong>{{ title }}</strong>
      </div>

      <div class="pdf-canvas-controls" aria-label="PDF 页面控制">
        <button type="button" :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">上一页</button>
        <span>第 {{ currentPage }} / {{ resolvedPageCount }} 页</span>
        <button type="button" :disabled="currentPage >= resolvedPageCount" @click="goToPage(currentPage + 1)">下一页</button>
        <button type="button" @click="setScale(scale - 0.1)">-</button>
        <span>{{ Math.round(scale * 100) }}%</span>
        <button type="button" @click="setScale(scale + 0.1)">+</button>
        <button type="button" :class="{ active: zoomMode === 'fit-width' }" @click="fitPageWidth">适宽</button>
        <button type="button" :disabled="!selectedText" @click="copySelectionOrPageText">复制文本层</button>
        <button type="button" :disabled="!selectedText" @click="highlightSelection">高亮选区</button>
        <button type="button" :disabled="!selectedText" @click="emitAskAgent('解释当前选区')">问 Agent</button>
      </div>
    </header>

    <section
      ref="stageRef"
      class="pdf-page-stage"
      aria-label="PDF 原貌与文本层"
      @wheel="handleStageWheel">
      <div v-if="loadError" class="pdf-page-fallback">
        <strong>当前会话 PDF 原貌预览不可用</strong>
        <span>可以继续使用左侧解析大纲做定位，并在右侧 Agent 面板完成笔记和提问。刷新后需要重新上传才能恢复 PDF 原貌。</span>
      </div>

      <div v-else class="pdf-page-shell">
        <canvas ref="canvasRef" class="pdf-canvas-layer" aria-label="PDF 原貌画布"></canvas>
        <div
          class="pdf-text-layer"
          aria-label="复制文本层"
          @mouseup="captureSelection"
          @keyup="captureSelection">
          <span
            v-for="item in textItems"
            :key="item.id"
            class="pdf-text-token"
            :style="item.style">
            {{ item.text }}
          </span>
        </div>
        <div class="pdf-annotation-layer" aria-label="PDF 批注层">
          <button v-if="selectedText" type="button" class="selection-pin" @click="emitAskAgent('解释当前选区')">
            当前选区
          </button>
          <mark
            v-for="annotation in currentPageAnnotations"
            :key="annotation.id"
            class="selection-highlight">
            {{ annotation.label }}
          </mark>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as pdfjsLib from 'pdfjs-dist/build/pdf.mjs'
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.mjs?url'

import type { DocumentBlock } from '@/pages/app/translationWorkspaceData'

interface TextLayerItem {
  id: string
  text: string
  style: Record<string, string>
}

interface PageAnnotation {
  id: string
  page: number
  label: string
  text: string
}

interface PdfSelectionPayload {
  text: string
  documentId: string
  pageNumber: number
  blockId: string | null
  elementId: string | null
  bbox: string | null
}

const props = defineProps<{
  documentId: string
  title: string
  src?: string
  blocks: DocumentBlock[]
  activeBlockId: string
  pageCount?: number
  targetPage?: number
}>()

const emit = defineEmits<{
  selectBlock: [blockId: string]
  askAgent: [prompt: string]
  selectionChange: [payload: PdfSelectionPayload]
  pageChange: [page: number]
}>()

type ZoomMode = 'fit-width' | 'manual'

const minScale = 0.5
const maxScale = 3
const fitWidthGutter = 28

let resizeObserver: ResizeObserver | null = null
let scrollPageTurnLock = false
let isRenderingPage = false
let needsPageRender = false
let syncScaleFromRender = false

const canvasRef = ref<HTMLCanvasElement | null>(null)
const stageRef = ref<HTMLElement | null>(null)
const pdfDocument = shallowRef<any>(null)
const currentPage = ref(1)
const scale = ref(1)
const zoomMode = ref<ZoomMode>('fit-width')
const textItems = ref<TextLayerItem[]>([])
const selectedText = ref('')
const annotations = ref<PageAnnotation[]>([])
const loadError = ref(false)
const renderedPageCount = ref(0)
const pendingPageScrollPosition = ref<'top' | 'bottom' | null>(null)

;(pdfjsLib.GlobalWorkerOptions as { workerSrc: string }).workerSrc = pdfWorkerUrl

const resolvedPageCount = computed(() => {
  return Math.max(1, renderedPageCount.value || props.pageCount || inferPageCountFromBlocks(props.blocks))
})

const currentPageAnnotations = computed(() => {
  return annotations.value.filter((item) => item.page === currentPage.value)
})

onMounted(() => {
  if (typeof ResizeObserver !== 'undefined' && stageRef.value) {
    resizeObserver = new ResizeObserver(() => {
      if (zoomMode.value === 'fit-width') requestPageRender()
    })
    resizeObserver.observe(stageRef.value)
  }
  void loadPdf()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})

watch(() => props.src, () => {
  void loadPdf()
})

watch(currentPage, () => {
  requestPageRender()
})

watch(scale, () => {
  if (syncScaleFromRender || zoomMode.value !== 'manual') return
  requestPageRender()
})

watch(() => props.targetPage, (page) => {
  if (!page || page === currentPage.value) return
  goToPage(page)
})

async function loadPdf() {
  textItems.value = []
  loadError.value = false
  pdfDocument.value = null
  renderedPageCount.value = 0

  if (!props.src) {
    loadError.value = true
    return
  }

  try {
    const loadingTask = pdfjsLib.getDocument({ url: props.src })
    pdfDocument.value = await loadingTask.promise
    renderedPageCount.value = pdfDocument.value.numPages || 0
    currentPage.value = Math.min(currentPage.value, resolvedPageCount.value)
    await nextTick()
    await renderCurrentPage()
  } catch {
    loadError.value = true
  }
}

function requestPageRender() {
  if (isRenderingPage) {
    needsPageRender = true
    return
  }
  void renderCurrentPage()
}

async function renderCurrentPage() {
  if (isRenderingPage) {
    needsPageRender = true
    return
  }

  const pdf = pdfDocument.value
  const canvas = canvasRef.value
  if (!pdf || !canvas) return

  isRenderingPage = true
  try {
    const pageNumber = currentPage.value
    const page = await pdf.getPage(pageNumber)
    const baseViewport = page.getViewport({ scale: 1 })
    const renderScale = zoomMode.value === 'fit-width'
      ? calculateFitWidthScale(baseViewport.width)
      : scale.value
    if (Math.abs(scale.value - renderScale) > 0.01) {
      syncScaleFromRender = true
      scale.value = renderScale
      await nextTick()
      syncScaleFromRender = false
    }
    const viewport = page.getViewport({ scale: renderScale })
    const context = canvas.getContext('2d')
    if (!context) return

    canvas.width = Math.floor(viewport.width)
    canvas.height = Math.floor(viewport.height)
    await page.render({ canvasContext: context, viewport }).promise
    if (pageNumber !== currentPage.value) {
      needsPageRender = true
      return
    }
    await renderTextLayer(page, viewport, renderScale)
    await restorePageScrollPosition()
  } finally {
    syncScaleFromRender = false
    isRenderingPage = false
    if (needsPageRender) {
      needsPageRender = false
      requestPageRender()
    }
  }
}

async function renderTextLayer(page: any, viewport: any, renderScale = scale.value) {
  const content = await page.getTextContent()
  const tokens = (content.items || []) as Array<any>
  textItems.value = tokens.map((item, index) => {
    const transform = pdfjsLib.Util.transform(viewport.transform, item.transform)
    const fontHeight = Math.hypot(transform[2], transform[3])
    return {
      id: `text-${currentPage.value}-${index}`,
      text: item.str,
      style: {
        left: `${transform[4]}px`,
        top: `${transform[5] - fontHeight}px`,
        fontSize: `${Math.max(8, fontHeight)}px`,
        transform: `scaleX(${item.width ? Math.max(0.2, (item.width * renderScale) / Math.max(1, item.str.length * fontHeight * 0.55)) : 1})`,
        transformOrigin: 'left top',
      },
    }
  })
}

function calculateFitWidthScale(pageWidth: number) {
  const stage = stageRef.value
  if (!stage || pageWidth <= 0) return scale.value

  const style = window.getComputedStyle(stage)
  const horizontalPadding = Number.parseFloat(style.paddingLeft) + Number.parseFloat(style.paddingRight)
  const availableWidth = Math.max(1, stage.clientWidth - horizontalPadding - fitWidthGutter)
  return clampScale(availableWidth / pageWidth)
}

function captureSelection() {
  const selection = typeof window === 'undefined' ? '' : window.getSelection()?.toString().trim() || ''
  const payload = resolveSelectionPayload(selection)
  selectedText.value = payload.text
  if (payload.blockId) emit('selectBlock', payload.blockId)
  emit('selectionChange', payload)
}

async function copySelectionOrPageText() {
  const text = selectedText.value || props.blocks.filter((block) => (block.pageNumber || 1) === currentPage.value).map((block) => block.text).join('\n\n')
  if (!text || typeof navigator === 'undefined' || !navigator.clipboard) return
  await navigator.clipboard.writeText(text)
}

function highlightSelection() {
  const text = selectedText.value.trim()
  if (!text) return
  annotations.value.unshift({
    id: `annotation-${Date.now()}`,
    page: currentPage.value,
    label: '高亮选区',
    text,
  })
}

function emitAskAgent(prompt: string) {
  const suffix = selectedText.value ? `：${selectedText.value}` : ''
  emit('askAgent', `${prompt}${suffix}`)
}

function resolveSelectionPayload(text: string): PdfSelectionPayload {
  const block = resolveSelectedBlock(text)
  return {
    text,
    documentId: props.documentId,
    pageNumber: block?.pageNumber ?? currentPage.value,
    blockId: block?.id ?? null,
    elementId: block?.elementId ?? block?.id ?? null,
    bbox: block?.bbox ?? null,
  }
}

function resolveSelectedBlock(text: string): DocumentBlock | null {
  const normalizedSelection = normalizeSelectionText(text)
  const pageBlocks = props.blocks.filter((block) => (block.pageNumber || 1) === currentPage.value)
  const activeBlock = pageBlocks.find((block) => block.id === props.activeBlockId)
  if (!normalizedSelection) {
    return activeBlock ?? pageBlocks[0] ?? null
  }
  return pageBlocks.find((block) => {
    const normalizedBlock = normalizeSelectionText(block.text)
    return normalizedBlock.includes(normalizedSelection) || normalizedSelection.includes(normalizedBlock)
  }) ?? activeBlock ?? pageBlocks[0] ?? null
}

function normalizeSelectionText(value: string) {
  return value.replace(/\s+/g, ' ').trim()
}

function goToPage(page: number) {
  currentPage.value = Math.min(Math.max(1, page), resolvedPageCount.value)
  emit('pageChange', currentPage.value)
}

function handleStageWheel(event: WheelEvent) {
  const stage = stageRef.value
  if (!stage || scrollPageTurnLock || Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return

  const maxScrollTop = Math.max(0, stage.scrollHeight - stage.clientHeight)
  const nextScrollTop = Math.min(maxScrollTop, Math.max(0, stage.scrollTop + event.deltaY))
  const canScrollPage = hasScrollablePage(stage)

  if (canScrollPage && Math.abs(nextScrollTop - stage.scrollTop) > 0.5) {
    event.preventDefault()
    stage.scrollTop = nextScrollTop
    return
  }

  const atBottom = stage.scrollTop >= maxScrollTop - 8
  const atTop = stage.scrollTop <= 8
  if (event.deltaY > 0 && (!canScrollPage || atBottom)) {
    event.preventDefault()
    turnPageFromScroll('next')
  } else if (event.deltaY < 0 && (!canScrollPage || atTop)) {
    event.preventDefault()
    turnPageFromScroll('previous')
  }
}

function turnPageFromScroll(direction: 'next' | 'previous') {
  const nextPage = direction === 'next' ? currentPage.value + 1 : currentPage.value - 1
  if (nextPage < 1 || nextPage > resolvedPageCount.value) return

  scrollPageTurnLock = true
  pendingPageScrollPosition.value = direction === 'next' ? 'top' : 'bottom'
  goToPage(nextPage)

  window.setTimeout(() => {
    scrollPageTurnLock = false
  }, 260)
}

async function restorePageScrollPosition() {
  const stage = stageRef.value
  const position = pendingPageScrollPosition.value
  if (!stage || !position) return

  await nextTick()
  stage.scrollTop = position === 'bottom'
    ? Math.max(0, stage.scrollHeight - stage.clientHeight)
    : 0
  pendingPageScrollPosition.value = null
}

function hasScrollablePage(stage: HTMLElement) {
  return stage.scrollHeight > stage.clientHeight + 8
}

function setScale(nextScale: number) {
  zoomMode.value = 'manual'
  scale.value = clampScale(Number(nextScale.toFixed(1)))
}

function fitPageWidth() {
  zoomMode.value = 'fit-width'
  requestPageRender()
}

function clampScale(nextScale: number) {
  return Math.min(maxScale, Math.max(minScale, Number(nextScale.toFixed(2))))
}

function inferPageCountFromBlocks(blocks: DocumentBlock[]) {
  return blocks.reduce((max, block) => Math.max(max, block.pageNumber || 1), 1)
}
</script>

<style scoped>
.pdf-learning-canvas {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  background: #f8fafc;
}

.pdf-canvas-toolbar {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 14px;
  border-bottom: 1px solid #d9e2ec;
  background: #ffffff;
}

.pdf-canvas-toolbar p {
  margin: 0;
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.pdf-canvas-toolbar strong {
  display: block;
  max-width: 420px;
  overflow: hidden;
  color: #111827;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pdf-canvas-controls {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.pdf-canvas-controls button,
.pdf-canvas-controls button {
  min-height: 30px;
  padding: 0 9px;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #ffffff;
  color: #344054;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.pdf-canvas-controls button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.pdf-canvas-controls button.active {
  border-color: #0f8f89;
  background: #ecfdf5;
  color: #0f766e;
}

.pdf-canvas-controls span {
  color: #475467;
  font-size: 12px;
  font-weight: 800;
}

.pdf-page-stage {
  display: grid;
  justify-items: center;
  align-items: start;
  min-height: 0;
  overflow: auto;
  padding: 14px;
  background: #3f4548;
}

.pdf-page-shell {
  position: relative;
  display: inline-block;
  width: fit-content;
  min-width: 0;
  min-height: 0;
  background: #ffffff;
  box-shadow: 0 18px 45px rgb(15 23 42 / 28%);
}

.pdf-canvas-layer {
  display: block;
  background: #ffffff;
}

.pdf-text-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  user-select: text;
}

.pdf-text-token {
  position: absolute;
  color: transparent;
  line-height: 1;
  white-space: pre;
  cursor: text;
}

.pdf-text-token::selection {
  background: rgb(20 184 166 / 32%);
  color: transparent;
}

.pdf-annotation-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.selection-pin {
  position: absolute;
  top: 12px;
  right: 12px;
  min-height: 30px;
  border: 1px solid #14b8a6;
  border-radius: 999px;
  background: #ecfdf5;
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
  pointer-events: auto;
}

.selection-highlight {
  position: absolute;
  top: 54px;
  right: 12px;
  max-width: 220px;
  padding: 5px 9px;
  border-radius: 999px;
  background: rgb(20 184 166 / 18%);
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.pdf-page-fallback {
  display: grid;
  place-content: center;
  gap: 8px;
  min-height: 560px;
  padding: 28px;
  border: 1px dashed #94a3b8;
  border-radius: 8px;
  background: #ffffff;
  color: #667085;
  text-align: center;
}

.pdf-page-fallback strong {
  color: #111827;
}
</style>
