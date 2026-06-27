<template>
  <div class="pdf-learning-canvas" :class="{ 'geometry-selecting': isGeometrySelecting }">
    <header class="pdf-canvas-toolbar" aria-label="PDF 缩放工具栏">
      <div class="pdf-canvas-controls" aria-label="PDF 缩放控制">
        <button type="button" @click="setScale(scale - 0.1)">-</button>
        <span>{{ Math.round(scale * 100) }}%</span>
        <button type="button" @click="setScale(scale + 0.1)">+</button>
        <button type="button" :class="{ active: zoomMode === 'fit-width' }" @click="fitPageWidth">适宽</button>
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
            :class="['selection-geometry-box', { 'region-selection-box': box.selectionType === 'region' }]"
            :style="box.style" />
          <span
            v-for="box in sourceHighlightBoxes"
            :key="box.id"
            class="citation-highlight-box"
            :style="box.style"
            :title="box.label" />
          <span
            v-for="box in noteAnchorHighlightBoxes"
            :key="box.id"
            class="note-anchor-highlight-box"
            :class="{ 'note-anchor-highlight-box--active': box.active }"
            :style="box.style"
            :title="box.label" />
          <div
            v-if="hasActiveSelection"
            class="selection-action-popover"
            :style="selectionActionStyle"
            role="toolbar"
            aria-label="当前选区操作">
            <span class="selection-action-popover__label">
              {{ selectedSelectionType === 'region' ? '图表区域' : '当前选区' }}
            </span>
            <button type="button" @click="emitNoteSelection">记笔记</button>
            <button type="button" @click="emitAskAgent('解释当前选区')">问 Agent</button>
          </div>
          <div v-if="currentPageNoteAnchors.length" class="note-anchor-stack" aria-label="当前页笔记锚点">
            <button
              v-for="(anchor, index) in currentPageNoteAnchors"
              :key="anchor.id"
              type="button"
              class="note-anchor-pin"
              :class="{ 'note-anchor-pin--active': anchor.active }"
              :style="resolveNoteAnchorPinStyle(anchor, index)"
              aria-label="打开锚点笔记"
              :title="anchor.excerpt || anchor.title"
              @click="emit('openNote', anchor.id)">
              <strong>{{ index + 1 }}</strong>
              <span>{{ anchor.title || '笔记' }}</span>
              <small>{{ anchor.status === 'draft' ? '待整理' : '笔记' }} · Page {{ anchor.pageNumber }}</small>
            </button>
          </div>
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
import {
  selectTextFlowHits,
  type PdfTextHit,
} from '@/utils/pdfGeometrySelection'

type PdfSelectionType = 'text' | 'region'

interface PdfSelectionPayload {
  text: string
  documentId: string
  pageNumber: number
  blockId: string | null
  elementId: string | null
  bbox: string | null
  selectionType: PdfSelectionType
}

interface PdfSourceHighlight {
  pageNumber: number
  bbox: string | null
  label: string
  text?: string | null
}

interface PdfNoteAnchor {
  id: string
  pageNumber: number
  title: string
  excerpt?: string
  bbox?: string | null
  status?: string
  active?: boolean
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

interface TextSpanHit extends GeometryRect, PdfTextHit {}

interface GeometrySelectionBox {
  id: string
  label?: string
  selectionType?: PdfSelectionType
  active?: boolean
  style: Record<string, string>
}

interface CanvasInkScanResult {
  bounds: GeometryRect
  window: GeometryRect
}

const props = defineProps<{
  documentId: string
  title: string
  src?: string
  blocks: DocumentBlock[]
  activeBlockId: string
  pageCount?: number
  targetPage?: number
  sourceHighlight?: PdfSourceHighlight | null
  noteAnchors?: PdfNoteAnchor[]
  activeNoteId?: string | null
}>()

const emit = defineEmits<{
  selectBlock: [blockId: string]
  askAgent: [prompt: string]
  noteSelection: [payload: PdfSelectionPayload]
  openNote: [noteId: string]
  selectionChange: [payload: PdfSelectionPayload]
  pageChange: [page: number]
}>()

type ZoomMode = 'fit-width' | 'manual'

const minScale = 0.5
const maxScale = 3
const fitWidthGutter = 28
const selectionLinePadding = 4
const selectionMinDragDistance = 2
const clickRegionScanWidth = 260
const clickRegionScanHeight = 180
const clickRegionMaxScanWidth = 620
const clickRegionMaxScanHeight = 460
const clickRegionExpansionStep = 80
const clickRegionMinExpandedScanWidth = 420
const clickRegionMinExpandedScanHeight = 300
const clickRegionEdgeTolerance = 10
const clickRegionPadding = 16
const clickRegionMinWidth = 180
const clickRegionMinHeight = 120
const inkPixelStep = 2
const selectionActionPopoverWidth = 252
const selectionActionPopoverOffset = 44
const selectionActionPagePadding = 12

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
const selectedSelectionType = ref<PdfSelectionType>('text')
const latestSelectionPayload = ref<PdfSelectionPayload | null>(null)
const loadError = ref(false)
const renderedPageCount = ref(0)
const pendingPageScrollPosition = ref<'top' | 'bottom' | null>(null)
const selectionBoxes = ref<GeometrySelectionBox[]>([])
const sourceHighlightBoxes = ref<GeometrySelectionBox[]>([])
const selectionDragStart = ref<Point | null>(null)
const selectionDragEnd = ref<Point | null>(null)
const isGeometrySelecting = ref(false)

;(pdfjsLib.GlobalWorkerOptions as { workerSrc: string }).workerSrc = pdfWorkerUrl

const resolvedPageCount = computed(() => {
  return Math.max(1, renderedPageCount.value || props.pageCount || inferPageCountFromBlocks(props.blocks))
})

const hasActiveSelection = computed(() => selectedText.value.trim().length > 0)

const selectionActionStyle = computed<Record<string, string>>(() => {
  const rect = resolveSelectionActionRect()
  if (!rect) {
    return {
      left: `${selectionActionPagePadding}px`,
      top: `${selectionActionPagePadding}px`,
    }
  }

  const layerWidth = textLayerRef.value?.clientWidth
    || canvasRef.value?.clientWidth
    || rect.right + selectionActionPopoverWidth
  const maxLeft = Math.max(selectionActionPagePadding, layerWidth - selectionActionPopoverWidth - selectionActionPagePadding)
  const left = clampNumber(rect.left, selectionActionPagePadding, maxLeft)
  const top = Math.max(selectionActionPagePadding, rect.top - selectionActionPopoverOffset)

  return {
    left: `${formatCssPixel(left)}px`,
    top: `${formatCssPixel(top)}px`,
  }
})

const currentPageNoteAnchors = computed(() => {
  return (props.noteAnchors ?? [])
    .filter((anchor) => anchor.pageNumber === currentPage.value)
    .map((anchor) => ({
      ...anchor,
      active: anchor.active || anchor.id === props.activeNoteId,
    }))
    .sort((left, right) => resolveNoteAnchorTop(left) - resolveNoteAnchorTop(right))
})

const noteAnchorHighlightBoxes = computed<GeometrySelectionBox[]>(() => {
  return currentPageNoteAnchors.value.flatMap((anchor) => {
    if (!anchor.bbox) return []
    return parseBboxToGeometry(anchor.bbox).map((rect, index) => ({
      id: `note-anchor-highlight-${anchor.id}-${index}`,
      label: anchor.title,
      active: anchor.active,
      style: {
        left: `${rect.left}px`,
        top: `${rect.top}px`,
        width: `${rect.width}px`,
        height: `${rect.height}px`,
      },
    }))
  })
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
  applyTargetPage(page)
}, { immediate: true })

watch(() => props.sourceHighlight, () => {
  renderSourceHighlight()
}, { deep: true })

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
    applyTargetPage(props.targetPage)
    currentPage.value = normalizeCanvasPage(currentPage.value)
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
    renderSourceHighlight()
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
  sourceHighlightBoxes.value = []
}

function renderSourceHighlight() {
  const highlight = props.sourceHighlight
  if (!highlight?.bbox || highlight.pageNumber !== currentPage.value) {
    sourceHighlightBoxes.value = []
    return
  }

  sourceHighlightBoxes.value = parseBboxToGeometry(highlight.bbox).map((rect, index) => ({
    id: `citation-highlight-${highlight.pageNumber}-${index}`,
    label: highlight.label,
    style: {
      left: `${rect.left}px`,
      top: `${rect.top}px`,
      width: `${rect.width}px`,
      height: `${rect.height}px`,
    },
  }))
}

function resolveNoteAnchorTop(anchor: PdfNoteAnchor) {
  if (!anchor.bbox) return Number.MAX_SAFE_INTEGER
  return parseBboxToGeometry(anchor.bbox)[0]?.top ?? Number.MAX_SAFE_INTEGER
}

function resolveNoteAnchorPinStyle(anchor: PdfNoteAnchor, index: number): Record<string, string> {
  const rect = anchor.bbox ? parseBboxToGeometry(anchor.bbox)[0] : null
  const fallbackTop = 54 + index * 46
  return {
    top: `${Math.max(18, rect ? rect.top + rect.height / 2 : fallbackTop)}px`,
    transform: rect ? 'translateY(-50%)' : 'none',
  }
}

function parseBboxToGeometry(bbox: string): GeometryRect[] {
  const trimmed = bbox.trim()
  if (!trimmed) return []

  const parsedRects = parseJsonBboxToGeometry(trimmed)
  if (parsedRects.length > 0) return parsedRects

  const numbers = normalizeBboxNumbers(trimmed)
  if (numbers.length >= 8) return [createRectFromPoints(numbers)]
  if (numbers.length >= 4) return [createRectFromXywh(numbers[0], numbers[1], numbers[2], numbers[3])]
  return []
}

function parseJsonBboxToGeometry(bbox: string): GeometryRect[] {
  try {
    const parsed = JSON.parse(bbox) as unknown
    if (Array.isArray(parsed)) {
      if (parsed.every((point) => Array.isArray(point) && point.length >= 2)) {
        const pointNumbers = parsed.flat().map(Number).filter(Number.isFinite)
        return pointNumbers.length >= 4 ? [createRectFromPoints(pointNumbers)] : []
      }
      const numbers = parsed.map(Number).filter(Number.isFinite)
      if (numbers.length >= 4) {
        return numbers.length >= 8
          ? [createRectFromPoints(numbers)]
          : [createRectFromXywh(numbers[0], numbers[1], numbers[2], numbers[3])]
      }
    }
    if (parsed && typeof parsed === 'object') {
      const candidate = parsed as Record<string, unknown>
      const x = Number(candidate.x ?? candidate.left)
      const y = Number(candidate.y ?? candidate.top)
      const width = Number(candidate.width)
      const height = Number(candidate.height)
      if ([x, y, width, height].every(Number.isFinite)) return [createRectFromXywh(x, y, width, height)]
    }
  } catch {
    return []
  }
  return []
}

function normalizeBboxNumbers(bbox: string) {
  return (bbox.match(/-?\d+(?:\.\d+)?/g) ?? [])
    .map(Number)
    .filter(Number.isFinite)
}

function createRectFromPoints(numbers: number[]): GeometryRect {
  const xs: number[] = []
  const ys: number[] = []
  for (let index = 0; index + 1 < numbers.length; index += 2) {
    xs.push(numbers[index])
    ys.push(numbers[index + 1])
  }
  return createRectFromBounds(Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys))
}

function createRectFromXywh(x: number, y: number, width: number, height: number): GeometryRect {
  return createRectFromBounds(
    width >= 0 ? x : x + width,
    height >= 0 ? y : y + height,
    width >= 0 ? x + width : x,
    height >= 0 ? y + height : y,
  )
}

function createRectFromBounds(left: number, top: number, right: number, bottom: number): GeometryRect {
  return {
    left,
    top,
    right,
    bottom,
    width: Math.max(1, right - left),
    height: Math.max(1, bottom - top),
  }
}

function resolveSelectionActionRect(): GeometryRect | null {
  const rects = selectionBoxes.value
    .map((box) => parseSelectionBoxStyle(box.style))
    .filter((rect): rect is GeometryRect => Boolean(rect))

  if (rects.length === 0) return null

  return createRectFromBounds(
    Math.min(...rects.map((rect) => rect.left)),
    Math.min(...rects.map((rect) => rect.top)),
    Math.max(...rects.map((rect) => rect.right)),
    Math.max(...rects.map((rect) => rect.bottom)),
  )
}

function parseSelectionBoxStyle(style: Record<string, string>): GeometryRect | null {
  const left = parseCssPixelValue(style.left)
  const top = parseCssPixelValue(style.top)
  const width = parseCssPixelValue(style.width)
  const height = parseCssPixelValue(style.height)
  if (![left, top, width, height].every(Number.isFinite)) return null
  return createRectFromXywh(left, top, width, height)
}

function parseCssPixelValue(value: string | undefined) {
  const numericValue = Number.parseFloat(value ?? '')
  return Number.isFinite(numericValue) ? numericValue : 0
}

function formatCssPixel(value: number) {
  return Math.round(value * 100) / 100
}

function clampNumber(value: number, minValue: number, maxValue: number) {
  return Math.min(Math.max(value, minValue), maxValue)
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
  selectedSelectionType.value = 'text'
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
  selectedSelectionType.value = 'text'
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
    const point = selectionDragEnd.value ?? selectionDragStart.value
    if (point && createClickRegionSelection(point)) return
    clearSelectionPayload()
    return
  }

  const start = selectionDragStart.value
  const end = selectionDragEnd.value
  if (!start || !end) return

  const hits = findTextSpanHits(selectionRect, start, end)
  const text = buildSelectedTextFromHits(hits)
  if (hits.length === 0 || !text.trim()) {
    selectionBoxes.value = [{
      id: `region-selection-${currentPage.value}`,
      selectionType: 'region',
      style: {
        left: `${selectionRect.left}px`,
        top: `${selectionRect.top}px`,
        width: `${selectionRect.width}px`,
        height: `${selectionRect.height}px`,
      },
    }]
    applySelectionPayload('图表/图片区选区', formatGeometryRectBbox(selectionRect), 'region')
    return
  }

  selectionBoxes.value = mergeCharacterHitsIntoBoxes(hits).map((hit, index) => ({
    id: `selection-box-${currentPage.value}-${index}`,
    selectionType: 'text',
    style: {
      left: `${hit.left}px`,
      top: `${hit.top}px`,
      width: `${hit.width}px`,
      height: `${hit.height}px`,
    },
  }))
  applySelectionPayload(text, formatSelectionBbox(hits), 'text')
}

function clearSelectionPayload() {
  selectedText.value = ''
  selectedSelectionType.value = 'text'
  selectionBoxes.value = []
  applySelectionPayload('', null, 'text')
}

function createClickRegionSelection(point: Point) {
  if (isPointOnTextSpan(point)) return false

  const inkBounds = findCanvasInkBoundsNearPoint(point)
  if (!inkBounds) return false

  const rect = createClickRegionRect(inkBounds)
  selectionBoxes.value = [{
    id: `region-selection-${currentPage.value}-click`,
    selectionType: 'region',
    style: {
      left: `${rect.left}px`,
      top: `${rect.top}px`,
      width: `${rect.width}px`,
      height: `${rect.height}px`,
    },
  }]
  applySelectionPayload('图表/图片区选区', formatGeometryRectBbox(rect), 'region')
  return true
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

function isPointOnTextSpan(point: Point) {
  const textLayer = textLayerRef.value
  if (!textLayer) return false

  const layerRect = textLayer.getBoundingClientRect()
  const spans = Array.from(textLayer.querySelectorAll('.textLayer span'))
  return spans.some((span) => {
    if (!(span instanceof HTMLElement)) return false
    if (span.getAttribute('role') === 'img') return false
    if (!span.textContent?.trim()) return false
    const rect = span.getBoundingClientRect()
    const left = rect.left - layerRect.left - 1
    const right = rect.right - layerRect.left + 1
    const top = rect.top - layerRect.top - 1
    const bottom = rect.bottom - layerRect.top + 1
    return point.x >= left && point.x <= right && point.y >= top && point.y <= bottom
  })
}

function findCanvasInkBoundsNearPoint(point: Point): GeometryRect | null {
  const canvas = canvasRef.value
  const textLayer = textLayerRef.value
  const context = canvas?.getContext('2d')
  if (!canvas || !textLayer || !context || canvas.width <= 0 || canvas.height <= 0) return null

  const layerWidth = Math.max(1, textLayer.clientWidth || canvas.getBoundingClientRect().width)
  const layerHeight = Math.max(1, textLayer.clientHeight || canvas.getBoundingClientRect().height)
  const maxScanWidth = Math.min(clickRegionMaxScanWidth, layerWidth)
  const maxScanHeight = Math.min(clickRegionMaxScanHeight, layerHeight)
  let scanWidth = Math.min(clickRegionScanWidth, maxScanWidth)
  let scanHeight = Math.min(clickRegionScanHeight, maxScanHeight)
  let result = sampleCanvasInkBounds(point, scanWidth, scanHeight, canvas, context, layerWidth, layerHeight)
  if (!result) return null

  while (shouldExpandClickRegion(result, scanWidth, scanHeight, maxScanWidth, maxScanHeight)) {
    const nextScanWidth = Math.min(maxScanWidth, scanWidth + clickRegionExpansionStep)
    const nextScanHeight = Math.min(maxScanHeight, scanHeight + clickRegionExpansionStep)
    if (nextScanWidth === scanWidth && nextScanHeight === scanHeight) break

    scanWidth = nextScanWidth
    scanHeight = nextScanHeight
    const expanded = sampleCanvasInkBounds(point, scanWidth, scanHeight, canvas, context, layerWidth, layerHeight)
    if (expanded) result = expanded
  }

  return result.bounds
}

function sampleCanvasInkBounds(
  point: Point,
  scanWidth: number,
  scanHeight: number,
  canvas: HTMLCanvasElement,
  context: CanvasRenderingContext2D,
  layerWidth: number,
  layerHeight: number,
): CanvasInkScanResult | null {
  const scanWindow = createScanWindowAroundPoint(point, scanWidth, scanHeight, layerWidth, layerHeight)
  const scaleX = canvas.width / layerWidth
  const scaleY = canvas.height / layerHeight
  const sampleLeft = Math.max(0, Math.floor(scanWindow.left * scaleX))
  const sampleTop = Math.max(0, Math.floor(scanWindow.top * scaleY))
  const sampleWidth = Math.max(1, Math.min(canvas.width - sampleLeft, Math.ceil(scanWindow.width * scaleX)))
  const sampleHeight = Math.max(1, Math.min(canvas.height - sampleTop, Math.ceil(scanWindow.height * scaleY)))

  let imageData: ImageData
  try {
    imageData = context.getImageData(sampleLeft, sampleTop, sampleWidth, sampleHeight)
  } catch {
    return null
  }

  let minX = Number.POSITIVE_INFINITY
  let minY = Number.POSITIVE_INFINITY
  let maxX = Number.NEGATIVE_INFINITY
  let maxY = Number.NEGATIVE_INFINITY
  const data = imageData.data
  for (let y = 0; y < sampleHeight; y += inkPixelStep) {
    for (let x = 0; x < sampleWidth; x += inkPixelStep) {
      const offset = (y * sampleWidth + x) * 4
      if (!isVisibleInkPixel(data[offset], data[offset + 1], data[offset + 2], data[offset + 3])) continue
      minX = Math.min(minX, x)
      minY = Math.min(minY, y)
      maxX = Math.max(maxX, x)
      maxY = Math.max(maxY, y)
    }
  }

  if (!Number.isFinite(minX) || !Number.isFinite(minY)) return null
  const cssScaleX = layerWidth / canvas.width
  const cssScaleY = layerHeight / canvas.height
  return {
    bounds: createRectFromBounds(
      (sampleLeft + minX) * cssScaleX,
      (sampleTop + minY) * cssScaleY,
      (sampleLeft + maxX + inkPixelStep) * cssScaleX,
      (sampleTop + maxY + inkPixelStep) * cssScaleY,
    ),
    window: scanWindow,
  }
}

function createScanWindowAroundPoint(
  point: Point,
  scanWidth: number,
  scanHeight: number,
  layerWidth: number,
  layerHeight: number,
): GeometryRect {
  const width = Math.min(scanWidth, layerWidth)
  const height = Math.min(scanHeight, layerHeight)
  const left = clampCoordinate(point.x - width / 2, Math.max(0, layerWidth - width))
  const top = clampCoordinate(point.y - height / 2, Math.max(0, layerHeight - height))
  return createRectFromBounds(left, top, left + width, top + height)
}

function shouldExpandClickRegion(
  result: CanvasInkScanResult,
  scanWidth: number,
  scanHeight: number,
  maxScanWidth: number,
  maxScanHeight: number,
) {
  const targetWidth = Math.min(clickRegionMinExpandedScanWidth, maxScanWidth)
  const targetHeight = Math.min(clickRegionMinExpandedScanHeight, maxScanHeight)
  if (scanWidth < targetWidth || scanHeight < targetHeight) return true
  return isInkNearScanEdge(result.bounds, result.window)
}

function isInkNearScanEdge(bounds: GeometryRect, scanWindow: GeometryRect) {
  return bounds.left <= scanWindow.left + clickRegionEdgeTolerance
    || bounds.top <= scanWindow.top + clickRegionEdgeTolerance
    || bounds.right >= scanWindow.right - clickRegionEdgeTolerance
    || bounds.bottom >= scanWindow.bottom - clickRegionEdgeTolerance
}

function isVisibleInkPixel(red: number, green: number, blue: number, alpha: number) {
  if (alpha < 24) return false
  const luminance = 0.299 * red + 0.587 * green + 0.114 * blue
  const colorRange = Math.max(red, green, blue) - Math.min(red, green, blue)
  return luminance < 215 || (colorRange > 32 && luminance < 242)
}

function createClickRegionRect(bounds: GeometryRect): GeometryRect {
  const padded = createRectFromBounds(
    bounds.left - clickRegionPadding,
    bounds.top - clickRegionPadding,
    bounds.right + clickRegionPadding,
    bounds.bottom + clickRegionPadding,
  )
  const width = Math.max(clickRegionMinWidth, padded.width)
  const height = Math.max(clickRegionMinHeight, padded.height)
  const centerX = padded.left + padded.width / 2
  const centerY = padded.top + padded.height / 2
  return clampRectToTextLayer(createRectFromBounds(
    centerX - width / 2,
    centerY - height / 2,
    centerX + width / 2,
    centerY + height / 2,
  ))
}

function clampRectToTextLayer(rect: GeometryRect): GeometryRect {
  const textLayer = textLayerRef.value
  const layerWidth = Math.max(1, textLayer?.clientWidth || canvasRef.value?.width || rect.right)
  const layerHeight = Math.max(1, textLayer?.clientHeight || canvasRef.value?.height || rect.bottom)
  const width = Math.min(rect.width, layerWidth)
  const height = Math.min(rect.height, layerHeight)
  const left = clampCoordinate(rect.left, Math.max(0, layerWidth - width))
  const top = clampCoordinate(rect.top, Math.max(0, layerHeight - height))
  return createRectFromBounds(left, top, left + width, top + height)
}

function findTextSpanHits(selectionRect: GeometryRect, start: Point, end: Point): TextSpanHit[] {
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
      centerX: spanRect.left - layerRect.left + spanRect.width / 2,
      centerY: spanRect.top - layerRect.top + spanRect.height / 2,
    }
    if (!isSpanInsideSelectionVerticalBand(spanHit, selectionRect)) continue

    const characterHits = findTextCharacterHits(span, layerRect)
    hits.push(...(characterHits.length > 0 ? characterHits : [spanHit]))
  }

  return selectTextFlowHits(hits, start, end) as TextSpanHit[]
}

function findTextCharacterHits(span: HTMLElement, layerRect: DOMRect): TextSpanHit[] {
  const textNode = getSpanTextNode(span)
  if (!textNode || !textNode.data.trim()) {
    return buildSpanFallbackHit(span, layerRect)
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
        centerX: rect.left - layerRect.left + rect.width / 2,
        centerY: rect.top - layerRect.top + rect.height / 2,
      }
      hits.push(hit)
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

function buildSpanFallbackHit(span: HTMLElement, layerRect: DOMRect): TextSpanHit[] {
  const text = span.textContent ?? ''
  if (!text.trim()) return []

  const rect = span.getBoundingClientRect()
  const spanLeft = rect.left - layerRect.left
  const spanRight = rect.right - layerRect.left
  return [{
    text,
    left: spanLeft,
    top: rect.top - layerRect.top,
    right: spanRight,
    bottom: rect.bottom - layerRect.top,
    width: Math.max(1, spanRight - spanLeft),
    height: rect.height,
    centerX: spanLeft + Math.max(1, spanRight - spanLeft) / 2,
    centerY: rect.top - layerRect.top + rect.height / 2,
  }]
}

function isSpanInsideSelectionVerticalBand(hit: TextSpanHit, selectionRect: GeometryRect) {
  const verticalPadding = Math.max(selectionLinePadding, hit.height * 0.55)
  return hit.bottom >= selectionRect.top - verticalPadding
    && hit.top <= selectionRect.bottom + verticalPadding
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

function formatGeometryRectBbox(rect: GeometryRect) {
  return [rect.left, rect.top, rect.width, rect.height]
    .map((value) => Number(value.toFixed(2)))
    .join(',')
}

function applySelectionPayload(text: string, bbox: string | null, selectionType: PdfSelectionType) {
  const payload = resolveSelectionPayload(text.trim(), bbox, selectionType)
  selectedText.value = payload.text
  selectedSelectionType.value = payload.selectionType
  latestSelectionPayload.value = payload.text ? payload : null
  if (payload.blockId) emit('selectBlock', payload.blockId)
  emit('selectionChange', payload)
}

function emitAskAgent(prompt: string) {
  const suffix = selectedText.value ? `：${selectedText.value}` : ''
  emit('askAgent', `${prompt}${suffix}`)
}

function emitNoteSelection() {
  const payload = latestSelectionPayload.value
  if (!payload) return
  emit('noteSelection', payload)
}

function resolveSelectionPayload(
  text: string,
  bbox: string | null = null,
  selectionType: PdfSelectionType = 'text',
): PdfSelectionPayload {
  if (selectionType === 'region') {
    return {
      text,
      documentId: props.documentId,
      pageNumber: currentPage.value,
      blockId: null,
      elementId: null,
      bbox,
      selectionType: 'region',
    }
  }

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
    selectionType,
  }
}

function normalizeCanvasPage(page: number) {
  return Math.min(Math.max(1, page), resolvedPageCount.value)
}

function applyTargetPage(page: number | undefined) {
  if (!page) return
  const nextPage = normalizeCanvasPage(page)
  if (nextPage === currentPage.value) return
  currentPage.value = nextPage
}

function goToPage(page: number) {
  currentPage.value = normalizeCanvasPage(page)
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

function inferPageCountFromBlocks(blocks: DocumentBlock[]) {
  return blocks.reduce((max, block) => Math.max(max, block.pageNumber || 1), 1)
}
</script>

<style scoped>
.pdf-learning-canvas {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  background: #f5f8fb;
}

.pdf-learning-canvas.geometry-selecting {
  cursor: text;
}

.pdf-canvas-toolbar {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  padding: 6px 10px;
  border-bottom: 1px solid #d9e2ec;
  background: #ffffff;
}

.pdf-canvas-controls {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.pdf-canvas-controls button,
.pdf-canvas-controls button {
  min-height: 28px;
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

.region-selection-box {
  border: 2px solid rgb(20 184 166 / 92%);
  border-radius: 3px;
  background: rgb(20 184 166 / 24%);
  box-shadow: 0 0 0 2px rgb(20 184 166 / 12%);
}

.citation-highlight-box {
  position: absolute;
  z-index: 2;
  border: 2px solid #f97316;
  border-radius: 3px;
  background: rgb(249 115 22 / 18%);
  box-shadow: 0 0 0 2px rgb(249 115 22 / 14%);
  pointer-events: none;
}

.note-anchor-highlight-box {
  position: absolute;
  z-index: 2;
  border-radius: 3px;
  background: rgb(250 204 21 / 24%);
  box-shadow: inset 3px 0 0 rgb(20 184 166 / 75%);
  pointer-events: none;
}

.note-anchor-highlight-box--active {
  background: rgb(20 184 166 / 22%);
  box-shadow:
    inset 3px 0 0 #0f766e,
    0 0 0 1px rgb(15 118 110 / 28%);
}

.selection-action-popover {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: min(320px, calc(100% - 24px));
  min-height: 32px;
  padding: 4px;
  border: 1px solid #14b8a6;
  border-radius: 10px;
  background: rgb(255 255 255 / 96%);
  color: #0f766e;
  box-shadow: 0 10px 24px rgb(15 23 42 / 14%);
  font-size: 12px;
  font-weight: 900;
  pointer-events: auto;
}

.selection-action-popover__label {
  min-width: 0;
  padding: 0 6px;
  overflow: hidden;
  color: #0f766e;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selection-action-popover button {
  min-height: 24px;
  padding: 0 8px;
  border: 1px solid #ccfbf1;
  border-radius: 7px;
  background: #ecfdf5;
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.selection-action-popover button:hover,
.selection-action-popover button:focus-visible {
  border-color: #14b8a6;
  background: #d9fbef;
  transform: none;
}

.note-anchor-stack {
  position: absolute;
  inset: 0;
  z-index: 4;
  pointer-events: none;
}

.note-anchor-pin {
  position: absolute;
  right: 10px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  column-gap: 7px;
  gap: 2px;
  width: min(158px, calc(100% - 20px));
  min-width: 0;
  padding: 6px 8px;
  border: 1px solid #f3d58a;
  border-radius: 8px;
  background: rgb(255 251 235 / 96%);
  color: #92400e;
  text-align: left;
  box-shadow: 0 8px 18px rgb(15 23 42 / 10%);
  cursor: pointer;
}

.note-anchor-pin--active {
  border-color: #14b8a6;
  background: rgb(236 253 245 / 98%);
  color: #0f766e;
  box-shadow: 0 0 0 2px rgb(20 184 166 / 18%), 0 8px 18px rgb(15 23 42 / 12%);
}

.note-anchor-pin strong {
  grid-row: span 2;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: rgb(250 204 21 / 36%);
  color: #92400e;
  font-size: 12px;
  font-weight: 900;
}

.note-anchor-pin span,
.note-anchor-pin small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-anchor-pin span {
  font-size: 12px;
  font-weight: 900;
}

.note-anchor-pin small {
  color: #b45309;
  font-size: 11px;
  font-weight: 800;
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
