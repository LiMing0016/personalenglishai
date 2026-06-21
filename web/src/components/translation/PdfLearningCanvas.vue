<template>
  <div class="pdf-learning-canvas" :class="{ 'geometry-selecting': isGeometrySelecting }">
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
        <span>可以继续使用左侧解析大纲做定位，并在右侧 Agent 面板完成笔记和提问。请检查后端原文件记录是否存在，或重新上传恢复 PDF 原貌。</span>
      </div>

      <div v-else class="pdf-page-shell">
        <canvas ref="canvasRef" class="pdf-canvas-layer" aria-label="PDF 原貌画布"></canvas>
        <div
          ref="textLayerRef"
          class="pdf-text-layer"
          aria-label="复制文本层"
          @pointerdown="beginGeometrySelection"
          @pointermove="updateGeometrySelection"
          @pointerup="finishGeometrySelection"
          @pointercancel="cancelGeometrySelection" />
        <div class="pdf-annotation-layer" aria-label="PDF 批注层">
          <span
            v-for="box in selectionBoxes"
            :key="box.id"
            class="selection-geometry-box"
            :style="box.style" />
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
import { TextLayerBuilder } from 'pdfjs-dist/web/pdf_viewer.mjs'
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.mjs?url'

import {
  resolveDocumentSelectionContextFromText,
  type DocumentBlock,
} from '@/pages/app/translationWorkspaceData'
import { getToken } from '@/utils/token'

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

interface Point {
  x: number
  y: number
}

interface GeometryRect {
  left: number
  top: number
  right: number
  bottom: number
  width: number
  height: number
}

interface TextSpanHit extends GeometryRect {
  text: string
  centerY: number
}

interface GeometrySelectionBox {
  id: string
  style: Record<string, string>
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
const selectionLinePadding = 4
const selectionMinDragDistance = 2

let resizeObserver: ResizeObserver | null = null
let scrollPageTurnLock = false
let isRenderingPage = false
let needsPageRender = false
let syncScaleFromRender = false
let textLayerBuilder: {
  cancel: () => void
  render: (params: { viewport: any; textContentParams?: Record<string, unknown> }) => Promise<void>
  div: HTMLElement
} | null = null
let textLayerAbortController: AbortController | null = null
let activePointerId: number | null = null

const canvasRef = ref<HTMLCanvasElement | null>(null)
const textLayerRef = ref<HTMLElement | null>(null)
const stageRef = ref<HTMLElement | null>(null)
const pdfDocument = shallowRef<any>(null)
const currentPage = ref(1)
const scale = ref(1)
const zoomMode = ref<ZoomMode>('fit-width')
const selectedText = ref('')
const annotations = ref<PageAnnotation[]>([])
const loadError = ref(false)
const renderedPageCount = ref(0)
const pendingPageScrollPosition = ref<'top' | 'bottom' | null>(null)
const selectionBoxes = ref<GeometrySelectionBox[]>([])
const selectionDragStart = ref<Point | null>(null)
const selectionDragEnd = ref<Point | null>(null)
const isGeometrySelecting = ref(false)

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
  resetTextLayer()
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
  resetTextLayer()
  loadError.value = false
  pdfDocument.value = null
  renderedPageCount.value = 0

  if (!props.src) {
    loadError.value = true
    return
  }

  try {
    const token = getToken()
    const loadingTask = pdfjsLib.getDocument({
      url: props.src,
      httpHeaders: token ? { Authorization: `Bearer ${token}` } : undefined,
      withCredentials: true,
    })
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
  const textLayer = textLayerRef.value
  if (!pdf || !canvas || !textLayer) return

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
  const textLayer = textLayerRef.value
  if (!textLayer) return

  resetTextLayer()
  textLayer.style.width = `${viewport.width}px`
  textLayer.style.height = `${viewport.height}px`
  textLayer.style.setProperty('--total-scale-factor', `${renderScale}`)

  textLayerAbortController = new AbortController()
  textLayerBuilder = new TextLayerBuilder({
    pdfPage: page,
    abortSignal: textLayerAbortController.signal,
    onAppend: (layerDiv: HTMLElement) => {
      layerDiv.style.width = `${viewport.width}px`
      layerDiv.style.height = `${viewport.height}px`
      layerDiv.style.setProperty('--total-scale-factor', `${renderScale}`)
      textLayer.append(layerDiv)
    },
  })
  await textLayerBuilder.render({
    viewport,
    textContentParams: {
      includeMarkedContent: true,
      disableNormalization: true,
    },
  })
}

function resetTextLayer() {
  textLayerBuilder?.cancel()
  textLayerBuilder = null
  textLayerAbortController?.abort()
  textLayerAbortController = null
  textLayerRef.value?.replaceChildren()
  textLayerRef.value?.style.removeProperty('--total-scale-factor')
  cancelGeometrySelection()
}

function calculateFitWidthScale(pageWidth: number) {
  const stage = stageRef.value
  if (!stage || pageWidth <= 0) return scale.value

  const style = window.getComputedStyle(stage)
  const horizontalPadding = Number.parseFloat(style.paddingLeft) + Number.parseFloat(style.paddingRight)
  const availableWidth = Math.max(1, stage.clientWidth - horizontalPadding - fitWidthGutter)
  return clampScale(availableWidth / pageWidth)
}

function beginGeometrySelection(event: PointerEvent) {
  if (event.button !== 0) return
  const point = getTextLayerPoint(event)
  if (!point) return

  event.preventDefault()
  window.getSelection()?.removeAllRanges()
  activePointerId = event.pointerId
  isGeometrySelecting.value = true
  selectionDragStart.value = point
  selectionDragEnd.value = point
  selectedText.value = ''
  selectionBoxes.value = []
  textLayerRef.value?.setPointerCapture?.(event.pointerId)
}

function updateGeometrySelection(event: PointerEvent) {
  if (!isGeometrySelecting.value || activePointerId !== event.pointerId) return
  const point = getTextLayerPoint(event)
  if (!point) return

  event.preventDefault()
  selectionDragEnd.value = point
  updateSelectionFromGeometry()
}

function finishGeometrySelection(event: PointerEvent) {
  if (!isGeometrySelecting.value || activePointerId !== event.pointerId) return

  event.preventDefault()
  const point = getTextLayerPoint(event)
  if (point) selectionDragEnd.value = point
  updateSelectionFromGeometry()
  textLayerRef.value?.releasePointerCapture?.(event.pointerId)
  isGeometrySelecting.value = false
  activePointerId = null
  window.getSelection()?.removeAllRanges()
}

function cancelGeometrySelection(event?: PointerEvent) {
  if (event && activePointerId !== event.pointerId) return
  if (event && activePointerId !== null) {
    textLayerRef.value?.releasePointerCapture?.(event.pointerId)
  }
  isGeometrySelecting.value = false
  activePointerId = null
  selectionDragStart.value = null
  selectionDragEnd.value = null
  selectionBoxes.value = []
  selectedText.value = ''
}

function getTextLayerPoint(event: PointerEvent): Point | null {
  const textLayer = textLayerRef.value
  if (!textLayer) return null

  const rect = textLayer.getBoundingClientRect()
  return {
    x: clampCoordinate(event.clientX - rect.left, rect.width),
    y: clampCoordinate(event.clientY - rect.top, rect.height),
  }
}

function updateSelectionFromGeometry() {
  const selectionRect = getCurrentSelectionRect()
  if (!selectionRect) return

  if (selectionRect.width < selectionMinDragDistance && selectionRect.height < selectionMinDragDistance) {
    selectedText.value = ''
    selectionBoxes.value = []
    applySelectionPayload('', null)
    return
  }

  const hits = findTextSpanHits(selectionRect)
  const text = buildSelectedTextFromHits(hits)
  selectionBoxes.value = mergeCharacterHitsIntoBoxes(hits).map((hit, index) => ({
    id: `selection-box-${currentPage.value}-${index}`,
    style: {
      left: `${hit.left}px`,
      top: `${hit.top}px`,
      width: `${hit.width}px`,
      height: `${hit.height}px`,
    },
  }))
  applySelectionPayload(text, formatSelectionBbox(hits))
}

function getCurrentSelectionRect(): GeometryRect | null {
  const start = selectionDragStart.value
  const end = selectionDragEnd.value
  if (!start || !end) return null

  const left = Math.min(start.x, end.x)
  const right = Math.max(start.x, end.x)
  const top = Math.min(start.y, end.y)
  const bottom = Math.max(start.y, end.y)
  return {
    left,
    top,
    right,
    bottom,
    width: right - left,
    height: bottom - top,
  }
}

function findTextSpanHits(selectionRect: GeometryRect): TextSpanHit[] {
  const textLayer = textLayerRef.value
  if (!textLayer) return []

  const layerRect = textLayer.getBoundingClientRect()
  const spans = Array.from(textLayer.querySelectorAll('.textLayer span'))
  const hits: TextSpanHit[] = []
  for (const span of spans) {
    if (!(span instanceof HTMLElement)) continue
    if (span.getAttribute('role') === 'img') continue

    const text = span.textContent ?? ''
    if (!text.trim()) continue

    const spanRect = span.getBoundingClientRect()
    const spanHit: TextSpanHit = {
      text,
      left: spanRect.left - layerRect.left,
      top: spanRect.top - layerRect.top,
      right: spanRect.right - layerRect.left,
      bottom: spanRect.bottom - layerRect.top,
      width: spanRect.width,
      height: spanRect.height,
      centerY: spanRect.top - layerRect.top + spanRect.height / 2,
    }
    if (!isSpanInsideSelection(spanHit, selectionRect)) continue

    const characterHits = findTextCharacterHits(span, selectionRect, layerRect)
    hits.push(...(characterHits.length > 0 ? characterHits : [spanHit]))
  }

  return hits.sort(compareVisualOrder)
}

function findTextCharacterHits(span: HTMLElement, selectionRect: GeometryRect, layerRect: DOMRect): TextSpanHit[] {
  const textNode = getSpanTextNode(span)
  if (!textNode || !textNode.data.trim()) {
    return clipSpanTextByGeometry(span, selectionRect, layerRect)
  }

  const range = document.createRange()
  const hits: TextSpanHit[] = []
  try {
    for (let index = 0; index < textNode.data.length; index += 1) {
      range.setStart(textNode, index)
      range.setEnd(textNode, index + 1)
      const rect = range.getBoundingClientRect()
      if (rect.width <= 0 || rect.height <= 0) continue

      const hit: TextSpanHit = {
        text: textNode.data[index] ?? '',
        left: rect.left - layerRect.left,
        top: rect.top - layerRect.top,
        right: rect.right - layerRect.left,
        bottom: rect.bottom - layerRect.top,
        width: rect.width,
        height: rect.height,
        centerY: rect.top - layerRect.top + rect.height / 2,
      }
      if (isSpanInsideSelection(hit, selectionRect)) hits.push(hit)
    }
  } finally {
    range.detach?.()
  }
  return hits
}

function getSpanTextNode(span: HTMLElement): Text | null {
  for (const node of Array.from(span.childNodes)) {
    if (node.nodeType === Node.TEXT_NODE) return node as Text
  }
  return null
}

function clipSpanTextByGeometry(span: HTMLElement, selectionRect: GeometryRect, layerRect: DOMRect): TextSpanHit[] {
  const text = span.textContent ?? ''
  if (!text.trim()) return []

  const rect = span.getBoundingClientRect()
  const spanLeft = rect.left - layerRect.left
  const spanRight = rect.right - layerRect.left
  const spanWidth = Math.max(1, spanRight - spanLeft)
  const startRatio = clampRatio((selectionRect.left - spanLeft) / spanWidth)
  const endRatio = clampRatio((selectionRect.right - spanLeft) / spanWidth)
  const startIndex = Math.max(0, Math.floor(text.length * Math.min(startRatio, endRatio)))
  const endIndex = Math.min(text.length, Math.ceil(text.length * Math.max(startRatio, endRatio)))
  const clippedText = text.slice(startIndex, endIndex)
  if (!clippedText.trim()) return []

  return [{
    text: clippedText,
    left: Math.max(spanLeft, selectionRect.left),
    top: rect.top - layerRect.top,
    right: Math.min(spanRight, selectionRect.right),
    bottom: rect.bottom - layerRect.top,
    width: Math.max(1, Math.min(spanRight, selectionRect.right) - Math.max(spanLeft, selectionRect.left)),
    height: rect.height,
    centerY: rect.top - layerRect.top + rect.height / 2,
  }]
}

function isSpanInsideSelection(hit: TextSpanHit, selectionRect: GeometryRect) {
  const horizontalIntersects = hit.right >= selectionRect.left && hit.left <= selectionRect.right
  if (!horizontalIntersects) return false

  if (selectionRect.height <= Math.max(selectionLinePadding * 2, hit.height * 0.9)) {
    return hit.centerY >= selectionRect.top - selectionLinePadding
      && hit.centerY <= selectionRect.bottom + selectionLinePadding
  }

  const overlapWidth = Math.max(0, Math.min(hit.right, selectionRect.right) - Math.max(hit.left, selectionRect.left))
  const overlapHeight = Math.max(0, Math.min(hit.bottom, selectionRect.bottom) - Math.max(hit.top, selectionRect.top))
  const hitArea = Math.max(1, hit.width * hit.height)
  return (overlapWidth * overlapHeight) / hitArea >= 0.18
}

function compareVisualOrder(left: TextSpanHit, right: TextSpanHit) {
  const topDelta = left.top - right.top
  if (Math.abs(topDelta) > Math.max(6, Math.min(left.height, right.height) * 0.6)) return topDelta
  return left.left - right.left
}

function buildSelectedTextFromHits(hits: TextSpanHit[]) {
  if (hits.length === 0) return ''

  const lines: TextSpanHit[][] = []
  for (const hit of hits) {
    const previousLine = lines[lines.length - 1]
    const previousHit = previousLine?.[0]
    if (!previousLine || !previousHit || Math.abs(hit.centerY - previousHit.centerY) > Math.max(6, hit.height * 0.7)) {
      lines.push([hit])
    } else {
      previousLine.push(hit)
    }
  }

  return lines
    .map((line) => joinVisualLine(line.sort((left, right) => left.left - right.left)))
    .filter(Boolean)
    .join('\n')
    .trim()
}

function joinVisualLine(line: TextSpanHit[]) {
  let text = ''
  let previous: TextSpanHit | null = null
  for (const hit of line) {
    const value = hit.text
    if (!value) continue

    const shouldInsertSpace = previous
      && hit.left - previous.right > Math.max(2, previous.height * 0.15)
      && /[A-Za-z0-9]$/.test(text)
      && /^[A-Za-z0-9]/.test(value)
    text += `${shouldInsertSpace ? ' ' : ''}${value}`
    previous = hit
  }
  return text.replace(/[ \t]+/g, ' ').trim()
}

function mergeCharacterHitsIntoBoxes(hits: TextSpanHit[]) {
  const boxes: TextSpanHit[] = []
  for (const hit of hits) {
    const previous = boxes[boxes.length - 1]
    const canMerge = previous
      && Math.abs(hit.centerY - previous.centerY) <= Math.max(5, hit.height * 0.45)
      && hit.left - previous.right <= Math.max(3, hit.height * 0.25)
    if (canMerge) {
      previous.text += hit.text
      previous.right = Math.max(previous.right, hit.right)
      previous.bottom = Math.max(previous.bottom, hit.bottom)
      previous.width = previous.right - previous.left
      previous.height = Math.max(previous.height, hit.height)
      previous.centerY = previous.top + previous.height / 2
    } else {
      boxes.push({ ...hit })
    }
  }
  return boxes
}

function formatSelectionBbox(hits: TextSpanHit[]) {
  if (hits.length === 0) return null

  const left = Math.min(...hits.map((hit) => hit.left))
  const top = Math.min(...hits.map((hit) => hit.top))
  const right = Math.max(...hits.map((hit) => hit.right))
  const bottom = Math.max(...hits.map((hit) => hit.bottom))
  return [left, top, right - left, bottom - top]
    .map((value) => Number(value.toFixed(2)))
    .join(',')
}

function applySelectionPayload(text: string, bbox: string | null) {
  const payload = resolveSelectionPayload(text.trim(), bbox)
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

function resolveSelectionPayload(text: string, bbox: string | null = null): PdfSelectionPayload {
  const context = resolveDocumentSelectionContextFromText({
    documentId: props.documentId,
    blocks: props.blocks,
    pageNumber: currentPage.value,
    activeBlockId: props.activeBlockId,
    selectedText: text,
  })
  return {
    text,
    documentId: props.documentId,
    pageNumber: context?.pageNumber ?? currentPage.value,
    blockId: context?.blockId ?? null,
    elementId: context?.elementId ?? null,
    bbox: bbox ?? context?.bbox ?? null,
  }
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

function clampCoordinate(value: number, maxValue: number) {
  return Math.min(Math.max(0, value), Math.max(0, maxValue))
}

function clampRatio(value: number) {
  return Math.min(Math.max(0, value), 1)
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

.pdf-learning-canvas.geometry-selecting {
  cursor: text;
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
  z-index: 1;
  overflow: clip;
  color: transparent;
  line-height: 1;
  text-align: initial;
  user-select: none;
  cursor: text;
  pointer-events: auto;
  -webkit-text-size-adjust: none;
  -moz-text-size-adjust: none;
  text-size-adjust: none;
}

.pdf-text-layer :deep(.textLayer) {
  position: absolute;
  inset: 0;
  overflow: clip;
  color: transparent;
  line-height: 1;
  text-align: initial;
  user-select: none;
  cursor: text;
  pointer-events: auto;
  transform-origin: 0 0;
}

.pdf-text-layer :deep(.textLayer :is(span, br)) {
  position: absolute;
  color: transparent;
  cursor: text;
  white-space: pre;
  transform-origin: 0% 0%;
  user-select: none;
}

.pdf-text-layer :deep(.textLayer > :not(.markedContent)),
.pdf-text-layer :deep(.textLayer .markedContent span:not(.markedContent)) {
  z-index: 1;
  --font-height: 0;
  font-size: calc(var(--total-scale-factor) * var(--font-height));
  --scale-x: 1;
  --rotate: 0deg;
  transform: rotate(var(--rotate)) scaleX(var(--scale-x));
}

.pdf-text-layer :deep(.textLayer .markedContent) {
  display: contents;
}

.pdf-text-layer :deep(.textLayer span[role="img"]) {
  cursor: default;
  user-select: none;
}

.pdf-text-layer :deep(.textLayer .endOfContent) {
  position: absolute;
  z-index: 0;
  inset: 100% 0 0;
  display: block;
  cursor: default;
  user-select: none;
}

.pdf-text-layer :deep(.textLayer.selecting .endOfContent) {
  top: 0;
}

.pdf-annotation-layer {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
}

.selection-geometry-box {
  position: absolute;
  z-index: 1;
  border-radius: 2px;
  background: rgb(20 184 166 / 30%);
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
