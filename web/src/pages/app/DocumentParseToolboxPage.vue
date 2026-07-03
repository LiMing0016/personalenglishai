<template>
  <main class="document-parse-toolbox">
    <aside class="toolbox-sidebar" aria-label="文档解析工具箱">
      <button type="button" class="new-parse-button" :disabled="parseLoading" @click="openFilePicker">
        <span aria-hidden="true">+</span>
        新解析
      </button>

      <section class="toolbox-settings" aria-label="解析设置">
        <h2>系统设置</h2>
        <label>
          <span>解析模型</span>
          <select v-model="selectedModelId" :disabled="parseLoading">
            <option v-for="model in parseModelOptions" :key="model.id" :value="model.id">
              {{ model.label }}
            </option>
          </select>
        </label>
      </section>

      <section class="recent-panel" aria-labelledby="recent-title">
        <div class="recent-header">
          <h2 id="recent-title">最近上传</h2>
          <button type="button" title="选择本地 PDF" aria-label="选择本地 PDF" @click="openFilePicker">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="11" cy="11" r="7" />
              <path d="m16 16 4 4" />
            </svg>
          </button>
        </div>

        <button
          v-for="item in recentItems"
          :key="item.id"
          type="button"
          class="recent-file"
          :class="{ active: parsedResponse?.documentId === item.id }"
          @click="loadDocumentById(item.id)"
        >
          <span class="pdf-badge">PDF</span>
          <span class="recent-file-body">
            <strong>{{ item.fileName }}</strong>
            <small>{{ item.providerLabel }} · {{ item.statusLabel }}</small>
            <small>{{ item.updatedAt }} · {{ item.pageLabel }}</small>
          </span>
        </button>

        <p v-if="recentItems.length === 0" class="recent-empty">上传 PDF 后会出现在这里。</p>
      </section>

      <input
        ref="fileInputRef"
        class="file-input"
        type="file"
        accept=".pdf,application/pdf"
        @change="handleFileInput"
      />
    </aside>

    <section class="source-pane" aria-label="PDF 源文件">
      <header class="pane-header source-header">
        <div>
          <span class="pane-tab">源文件</span>
          <strong>{{ sourceTitle }}</strong>
          <small v-if="sourceFileMeta">{{ sourceFileMeta }}</small>
        </div>
        <button type="button" class="ghost-button" :disabled="parseLoading" @click="openFilePicker">
          选择 PDF
        </button>
      </header>

      <div class="source-viewer">
        <PdfLearningCanvas
          v-if="sourcePdfUrl"
          :document-id="activeDocument?.id || 'toolbox-preview'"
          :title="sourceTitle"
          :src="sourcePdfUrl"
          :blocks="activeDocument?.blocks || []"
          :active-block-id="activeBlockId"
          :page-count="activeDocument?.pageCount"
          :target-page="activePageNumber"
          @select-block="selectBlock"
          @page-change="activePageNumber = $event"
          @ask-agent="showToolboxOnlyMessage"
          @selection-change="showToolboxOnlyMessage"
        />

        <section
          v-else
          class="upload-dropzone"
          :class="{ dragging: isDragging }"
          @dragenter.prevent="isDragging = true"
          @dragover.prevent="isDragging = true"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="handleFileDrop"
        >
          <span class="upload-icon" aria-hidden="true">↑</span>
          <h1>上传 PDF 源文件</h1>
          <p>本地 PaddleOCR 会优先解析前 10 页，之后后台继续处理。</p>
          <button type="button" :disabled="parseLoading" @click="openFilePicker">选择文件</button>
        </section>
      </div>
    </section>

    <section class="parse-pane" aria-label="文档解析结果">
      <header class="model-header">
        <span>解析模型</span>
        <label class="model-select">
          <select v-model="selectedModelId" :disabled="parseLoading">
            <option v-for="model in parseModelOptions" :key="model.id" :value="model.id">
              {{ model.label }}
            </option>
          </select>
        </label>
      </header>

      <div class="parse-tabs">
        <div class="parse-tab-buttons" aria-label="解析视图">
          <button type="button" :class="{ active: activeTab === 'document' }" @click="activeTab = 'document'">
            文档解析
          </button>
          <button type="button" :class="{ active: activeTab === 'json' }" @click="activeTab = 'json'">
            JSON
          </button>
        </div>
        <div class="parse-tools" aria-label="解析工具">
          <button type="button" title="重新上传" aria-label="重新上传" :disabled="parseLoading" @click="openFilePicker">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 3v12" />
              <path d="m7 8 5-5 5 5" />
              <path d="M5 21h14" />
            </svg>
          </button>
          <button type="button" title="刷新结果" aria-label="刷新结果" :disabled="!parsedResponse || parseLoading" @click="refreshNow">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M20 12a8 8 0 1 1-2.3-5.6" />
              <path d="M20 4v6h-6" />
            </svg>
          </button>
          <button type="button" title="复制 JSON" aria-label="复制 JSON" :disabled="!parsedResponse" @click="copyJson">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <rect x="9" y="9" width="10" height="10" rx="2" />
              <path d="M5 15H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1" />
            </svg>
          </button>
        </div>
      </div>

      <div class="parse-body">
        <section v-if="parseLoading" class="parse-status-card">
          <span class="status-dot"></span>
          <div>
            <strong>正在解析材料...</strong>
            <p>本地 PaddleOCR 正在处理 PDF。大文件会先返回首批页面，再后台继续解析。</p>
          </div>
        </section>

        <section v-else-if="parseError" class="parse-error-card">
          <strong>解析失败</strong>
          <p>{{ parseError }}</p>
          <button type="button" @click="openFilePicker">重新选择 PDF</button>
        </section>

        <template v-else-if="activeTab === 'document'">
          <section v-if="parsedResponse" class="parse-summary">
            <span class="status-dot" :class="{ complete: !shouldRefreshActiveResult }"></span>
            <strong>{{ formatDocumentParseToolboxStatus(parsedResponse) }}</strong>
            <span>{{ documentParseDisplayPages.length }} / {{ activeDocument?.pageCount || documentParseDisplayPages.length }} 页</span>
          </section>

          <article
            v-for="page in documentParseDisplayPages"
            :key="page.pageNumber"
            class="parse-page"
            :aria-label="`Page ${page.pageNumber} 文档解析`"
          >
            <header class="parse-page-header">
              <span>Page {{ page.pageNumber }}</span>
              <small>{{ page.blocks.length }} 个解析块 · {{ page.assets.length }} 张图片 · {{ page.textLength }} 字符</small>
            </header>

            <section class="parse-page-content">
              <article
                v-for="block in page.blocks"
                :key="block.id"
                class="parse-block"
                :class="[`parse-block--${block.displayType}`, { active: block.id === activeBlockId }]"
                @click="selectBlock(block.id, block.pageNumber || page.pageNumber)"
              >
                <div class="parse-block-meta">
                  <span>{{ parsedBlockTypeLabel(block.displayType) }}</span>
                  <small v-if="block.confidence !== null && block.confidence !== undefined">
                    confidence {{ Math.round(block.confidence * 100) }}%
                  </small>
                </div>
                <h1 v-if="block.displayType === 'title'">{{ block.text }}</h1>
                <h2 v-else-if="block.displayType === 'heading'">{{ block.text }}</h2>
                <pre v-else-if="block.displayType === 'table' || block.displayType === 'code'">{{ block.text }}</pre>
                <p v-else>{{ block.text }}</p>
              </article>

              <article
                v-for="asset in page.assets"
                :key="asset.id"
                class="parse-image-asset"
              >
                <div class="parse-block-meta">
                  <span>{{ asset.label }}</span>
                  <small v-if="asset.confidence !== null && asset.confidence !== undefined">
                    confidence {{ Math.round(asset.confidence * 100) }}%
                  </small>
                </div>
                <img :src="asset.dataUrl" :alt="`${asset.label} · Page ${asset.pageNumber}`" loading="lazy" />
              </article>
            </section>
          </article>

          <section v-if="!parsedResponse" class="parse-empty-state">
            <strong>等待上传 PDF</strong>
            <span>上传后这里会展示 PaddleOCR 风格的文档解析结果。</span>
          </section>

          <section v-else-if="documentParseDisplayPages.length === 0" class="parse-empty-state">
            <strong>暂无可展示文本</strong>
            <span>后端返回解析块或图片资产后，这里会自动刷新。</span>
          </section>
        </template>

        <pre v-else class="json-viewer">{{ jsonText }}</pre>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'

import {
  getTranslationDocumentFileUrl,
  getTranslationDocumentKnowledge,
  importTranslationDocument,
  type TranslationDocumentParseResponse,
} from '@/api/translation'
import PdfLearningCanvas from '@/components/translation/PdfLearningCanvas.vue'
import { showToast } from '@/utils/toast'
import {
  buildDocumentParsePages,
  buildIntensiveReadingDocument,
  createTranslationWorkspaceDraftFromParsedDocument,
  type IntensiveReadingDocument,
} from './translationWorkspaceData'
import {
  DEFAULT_DOCUMENT_PARSE_TOOLBOX_MODEL_ID,
  buildDocumentParseToolboxAssetPages,
  buildDocumentParseToolboxRecentItem,
  documentParseToolboxModelOptions,
  formatDocumentParseToolboxStatus,
  shouldRefreshDocumentParseToolboxResult,
  type DocumentParseToolboxModelId,
  type DocumentParseToolboxRecentItem,
} from './documentParseToolboxData'

type ParseTab = 'document' | 'json'

const fileInputRef = ref<HTMLInputElement | null>(null)
const parseModelOptions = documentParseToolboxModelOptions
const selectedModelId = ref<DocumentParseToolboxModelId>(DEFAULT_DOCUMENT_PARSE_TOOLBOX_MODEL_ID)
const selectedFile = ref<File | null>(null)
const localPreviewUrl = ref('')
const parsedResponse = ref<TranslationDocumentParseResponse | null>(null)
const activeDocument = ref<IntensiveReadingDocument | null>(null)
const activeBlockId = ref('')
const activePageNumber = ref(1)
const activeTab = ref<ParseTab>('document')
const parseLoading = ref(false)
const parseError = ref('')
const isDragging = ref(false)
const recentItems = ref<DocumentParseToolboxRecentItem[]>([])
let refreshTimer: number | null = null

const selectedModel = computed(() => {
  return parseModelOptions.find((model) => model.id === selectedModelId.value) ?? parseModelOptions[0]
})

const sourcePdfUrl = computed(() => {
  return activeDocument.value?.pdfPreviewUrl || localPreviewUrl.value
})

const sourceTitle = computed(() => {
  return activeDocument.value?.title || selectedFile.value?.name || '选择 PDF 开始解析'
})

const sourceFileMeta = computed(() => {
  if (selectedFile.value) return formatFileSize(selectedFile.value.size)
  const response = parsedResponse.value
  if (!response) return ''
  return `${response.pageCount || 0} 页 · ${formatDocumentParseToolboxStatus(response)}`
})

const documentParsePages = computed(() => {
  return activeDocument.value ? buildDocumentParsePages(activeDocument.value.blocks) : []
})

const documentParseAssetPages = computed(() => {
  return parsedResponse.value ? buildDocumentParseToolboxAssetPages(parsedResponse.value.assets ?? []) : []
})

const documentParseDisplayPages = computed(() => {
  const pages = new Map<number, {
    pageNumber: number
    blocks: ReturnType<typeof buildDocumentParsePages>[number]['blocks']
    textLength: number
    assets: ReturnType<typeof buildDocumentParseToolboxAssetPages>[number]['assets']
  }>()

  for (const page of documentParsePages.value) {
    pages.set(page.pageNumber, {
      ...page,
      assets: [],
    })
  }

  for (const assetPage of documentParseAssetPages.value) {
    const page = pages.get(assetPage.pageNumber) ?? {
      pageNumber: assetPage.pageNumber,
      blocks: [],
      textLength: 0,
      assets: [],
    }
    page.assets = assetPage.assets
    pages.set(assetPage.pageNumber, page)
  }

  return Array.from(pages.values()).sort((left, right) => left.pageNumber - right.pageNumber)
})

const jsonText = computed(() => {
  return parsedResponse.value ? JSON.stringify(parsedResponse.value, null, 2) : '暂无解析 JSON'
})

const shouldRefreshActiveResult = computed(() => {
  return parsedResponse.value ? shouldRefreshDocumentParseToolboxResult(parsedResponse.value) : false
})

onBeforeUnmount(() => {
  clearResultRefresh()
  revokeLocalPreview()
})

function openFilePicker() {
  fileInputRef.value?.click()
}

function handleFileInput(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  void beginParsing(file)
}

function handleFileDrop(event: DragEvent) {
  isDragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (!file) return
  void beginParsing(file)
}

async function beginParsing(file: File) {
  if (!isSupportedPdf(file)) {
    showToast('请上传 PDF 文件', 'error')
    return
  }

  clearResultRefresh()
  parseError.value = ''
  parseLoading.value = true
  activeTab.value = 'document'
  selectedFile.value = file
  parsedResponse.value = null
  activeDocument.value = null
  activeBlockId.value = ''
  activePageNumber.value = 1
  setLocalPreview(file)

  try {
    const parsed = await importTranslationDocument(
      file,
      'immersive',
      selectedModel.value.parseMode,
      selectedModel.value.provider,
    )
    applyParsedDocument(parsed)
    scheduleResultRefresh(parsed)
    showToast('首批文档解析结果已返回', 'success')
  } catch (error) {
    parseError.value = error instanceof Error ? error.message : '文档解析失败，请稍后重试。'
    showToast('文档解析失败', 'error')
  } finally {
    parseLoading.value = false
  }
}

function applyParsedDocument(parsed: TranslationDocumentParseResponse) {
  parsedResponse.value = parsed
  const pdfPreviewUrl = parsed.fileUrl || getTranslationDocumentFileUrl(parsed.documentId)
  const draft = createTranslationWorkspaceDraftFromParsedDocument(
    {
      mode: 'immersive',
      pdfPreviewUrl,
    },
    parsed,
  )
  activeDocument.value = buildIntensiveReadingDocument(draft)
  activeBlockId.value = activeDocument.value.blocks[0]?.id ?? ''
  activePageNumber.value = activeDocument.value.blocks[0]?.pageNumber ?? 1
  upsertRecentItem(parsed)
}

function upsertRecentItem(parsed: TranslationDocumentParseResponse) {
  const item = buildDocumentParseToolboxRecentItem(parsed, formatCurrentTime())
  recentItems.value = [
    item,
    ...recentItems.value.filter((recent) => recent.id !== item.id),
  ].slice(0, 8)
}

async function loadDocumentById(documentId: string) {
  if (!documentId || parseLoading.value) return
  clearResultRefresh()
  parseError.value = ''
  try {
    const parsed = await getTranslationDocumentKnowledge(documentId)
    applyParsedDocument(parsed)
    scheduleResultRefresh(parsed)
  } catch (error) {
    parseError.value = error instanceof Error ? error.message : '读取解析结果失败。'
    showToast('读取解析结果失败', 'error')
  }
}

async function refreshNow() {
  const documentId = parsedResponse.value?.documentId
  if (!documentId || parseLoading.value) return
  await loadDocumentById(documentId)
}

function scheduleResultRefresh(parsed: TranslationDocumentParseResponse) {
  clearResultRefresh()
  if (!shouldRefreshDocumentParseToolboxResult(parsed) || typeof window === 'undefined') return
  refreshTimer = window.setTimeout(() => {
    refreshTimer = null
    void refreshNow()
  }, 8000)
}

function clearResultRefresh() {
  if (!refreshTimer || typeof window === 'undefined') return
  window.clearTimeout(refreshTimer)
  refreshTimer = null
}

function selectBlock(blockId: string, pageNumber?: number) {
  activeBlockId.value = blockId
  if (pageNumber) activePageNumber.value = pageNumber
}

function showToolboxOnlyMessage() {
  showToast('工具箱先聚焦文档解析验收，问答稍后接入。', 'info')
}

async function copyJson() {
  if (!parsedResponse.value) return
  try {
    await navigator.clipboard.writeText(jsonText.value)
    showToast('JSON 已复制', 'success')
  } catch {
    showToast('当前浏览器不允许复制，请手动选择 JSON。', 'info')
  }
}

function setLocalPreview(file: File) {
  revokeLocalPreview()
  if (typeof URL === 'undefined') return
  localPreviewUrl.value = URL.createObjectURL(file)
}

function revokeLocalPreview() {
  if (!localPreviewUrl.value || typeof URL === 'undefined') return
  URL.revokeObjectURL(localPreviewUrl.value)
  localPreviewUrl.value = ''
}

function isSupportedPdf(file: File) {
  return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
}

function formatCurrentTime() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hour = String(now.getHours()).padStart(2, '0')
  const minute = String(now.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

function formatFileSize(size: number) {
  if (size <= 0) return ''
  const mb = size / 1024 / 1024
  if (mb >= 1) return `${mb.toFixed(mb >= 100 ? 0 : 2)}MB`
  return `${Math.max(1, Math.round(size / 1024))}KB`
}

function parsedBlockTypeLabel(type: string) {
  const labels: Record<string, string> = {
    title: '标题',
    heading: '小标题',
    paragraph: '段落',
    list: '列表',
    table: '表格',
    quote: '引用',
    code: '代码',
    question: '题目',
    option: '选项',
  }
  return labels[type] ?? '段落'
}
</script>

<style scoped>
.document-parse-toolbox {
  display: grid;
  grid-template-columns: 268px minmax(420px, 1fr) minmax(420px, 1fr);
  height: 100vh;
  min-height: 0;
  overflow: hidden;
  background: #f6f8fb;
  color: #0f172a;
}

button,
select {
  font: inherit;
}

.toolbox-sidebar,
.source-pane,
.parse-pane {
  min-width: 0;
  min-height: 0;
}

.toolbox-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 22px 14px;
  border-right: 1px solid #dfe7f1;
  background: #ffffff;
}

.new-parse-button,
.ghost-button,
.upload-dropzone button,
.parse-error-card button {
  min-height: 42px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #334155;
  font-weight: 900;
  cursor: pointer;
}

.new-parse-button {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-start;
  width: 100%;
  padding: 0 14px;
}

.new-parse-button span {
  color: #5468ff;
  font-size: 22px;
  line-height: 1;
}

.new-parse-button:disabled,
.ghost-button:disabled,
.parse-tools button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.toolbox-settings {
  display: grid;
  gap: 12px;
  padding: 14px 0 18px;
  border-top: 1px solid #e5edf6;
  border-bottom: 1px solid #e5edf6;
}

.toolbox-settings h2,
.recent-header h2 {
  margin: 0;
  color: #26364d;
  font-size: 15px;
  font-weight: 900;
}

.toolbox-settings label {
  display: grid;
  gap: 7px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.toolbox-settings select,
.model-select select {
  width: 100%;
  min-height: 38px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #f8fbff;
  color: #334155;
  font-weight: 800;
}

.recent-panel {
  min-height: 0;
}

.recent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.recent-header button,
.parse-tools button {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  cursor: pointer;
}

.recent-header button svg,
.parse-tools button svg {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.recent-file {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.recent-file.active,
.recent-file:hover {
  background: #eef4ff;
}

.pdf-badge {
  display: inline-grid;
  place-items: center;
  width: 31px;
  height: 36px;
  border-radius: 7px;
  background: #fb7185;
  color: #ffffff;
  font-size: 11px;
  font-weight: 900;
}

.recent-file-body {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.recent-file-body strong {
  overflow: hidden;
  color: #26364d;
  font-size: 13px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-file-body small,
.recent-empty {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.file-input {
  display: none;
}

.source-pane,
.parse-pane {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  border-right: 1px solid #dfe7f1;
  background: #f8fafc;
}

.parse-pane {
  grid-template-rows: auto auto minmax(0, 1fr);
  border-right: 0;
  background: #ffffff;
}

.pane-header,
.model-header,
.parse-tabs {
  border-bottom: 1px solid #dfe7f1;
  background: #ffffff;
}

.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 84px;
  padding: 0 28px;
}

.pane-header div {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.pane-header strong {
  overflow: hidden;
  color: #1e293b;
  font-size: 15px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pane-header small {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 800;
}

.pane-tab {
  width: fit-content;
  padding: 9px 14px;
  border-radius: 8px 8px 0 0;
  background: #eaf0ff;
  color: #5468ff;
  font-size: 13px;
  font-weight: 900;
}

.source-viewer {
  min-height: 0;
  overflow: hidden;
}

.source-viewer :deep(.pdf-learning-canvas) {
  height: 100%;
}

.source-viewer :deep(.pdf-canvas-toolbar p) {
  color: #0f9488;
}

.upload-dropzone {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 16px;
  height: 100%;
  margin: 28px;
  border: 2px dashed #cbd8e8;
  border-radius: 10px;
  background: #ffffff;
  text-align: center;
}

.upload-dropzone.dragging {
  border-color: #0f9488;
  background: #e8fbf5;
}

.upload-icon {
  display: grid;
  place-items: center;
  width: 70px;
  height: 70px;
  border: 3px solid #8aa0b8;
  border-radius: 50%;
  color: #64748b;
  font-size: 42px;
  font-weight: 700;
}

.upload-dropzone h1 {
  margin: 0;
  font-size: 26px;
  letter-spacing: 0;
}

.upload-dropzone p {
  max-width: 430px;
  margin: 0;
  color: #64748b;
  line-height: 1.7;
}

.upload-dropzone button,
.parse-error-card button {
  padding: 0 18px;
  border-color: #0f9488;
  background: #0f9488;
  color: #ffffff;
}

.model-header {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 52px;
  padding: 0 28px;
}

.model-header span {
  color: #64748b;
  font-size: 13px;
  font-weight: 900;
}

.model-select {
  width: min(300px, 58%);
}

.parse-tabs {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: 0 28px;
}

.parse-tab-buttons {
  display: flex;
  align-items: center;
  gap: 10px;
}

.parse-tab-buttons button {
  min-height: 38px;
  padding: 0 16px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #334155;
  font-weight: 900;
  cursor: pointer;
}

.parse-tab-buttons button.active {
  background: #eaf0ff;
  color: #5468ff;
}

.parse-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.parse-body {
  min-height: 0;
  overflow: auto;
  padding: 34px 44px 80px;
}

.parse-summary,
.parse-status-card,
.parse-error-card,
.parse-empty-state {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  max-width: 780px;
  margin: 0 auto 22px;
  padding: 14px 16px;
  border: 1px solid #dfe7f1;
  border-radius: 8px;
  background: #ffffff;
}

.parse-summary {
  align-items: center;
  color: #0f9488;
  font-size: 13px;
  font-weight: 900;
}

.parse-summary span:last-child {
  margin-left: auto;
  color: #64748b;
}

.status-dot {
  flex: 0 0 auto;
  width: 9px;
  height: 9px;
  margin-top: 7px;
  border-radius: 99px;
  background: #0f9488;
  box-shadow: 0 0 0 6px rgba(15, 148, 136, 0.1);
}

.status-dot.complete {
  background: #22c55e;
}

.parse-status-card strong,
.parse-error-card strong,
.parse-empty-state strong {
  display: block;
  margin-bottom: 4px;
  color: #26364d;
  font-weight: 900;
}

.parse-status-card p,
.parse-error-card p,
.parse-empty-state span {
  margin: 0;
  color: #64748b;
  line-height: 1.7;
}

.parse-error-card {
  display: grid;
  color: #b42318;
}

.parse-page {
  max-width: 780px;
  margin: 0 auto 24px;
  border: 1px solid #e4eaf2;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.06);
}

.parse-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid #edf2f7;
  color: #0f9488;
  font-size: 13px;
  font-weight: 900;
}

.parse-page-header small {
  color: #94a3b8;
}

.parse-page-content {
  padding: 22px 26px 30px;
}

.parse-block {
  padding: 14px 16px;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
}

.parse-block:hover,
.parse-block.active {
  border-color: rgba(15, 148, 136, 0.28);
  background: #f4fffb;
}

.parse-image-asset {
  margin-top: 18px;
  padding: 14px;
  border: 1px solid rgba(168, 85, 247, 0.36);
  border-radius: 8px;
  background: #fbf7ff;
}

.parse-image-asset img {
  display: block;
  width: 100%;
  max-height: 720px;
  object-fit: contain;
  border: 1px solid #eadcff;
  border-radius: 6px;
  background: #ffffff;
}

.parse-block-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 900;
  text-transform: uppercase;
}

.parse-block h1,
.parse-block h2,
.parse-block p,
.parse-block pre {
  margin: 0;
  letter-spacing: 0;
}

.parse-block h1 {
  color: #111827;
  font-size: 31px;
  line-height: 1.28;
}

.parse-block h2 {
  color: #1f2937;
  font-size: 22px;
  line-height: 1.35;
}

.parse-block p,
.parse-block pre {
  color: #26364d;
  font-size: 16px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.parse-block pre,
.json-viewer {
  overflow: auto;
  border-radius: 8px;
  background: #0f172a;
  color: #e5edf6;
  font-family: "SFMono-Regular", Consolas, monospace;
}

.parse-block pre {
  padding: 14px;
}

.json-viewer {
  max-width: 920px;
  min-height: calc(100vh - 230px);
  margin: 0 auto;
  padding: 20px;
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 1180px) {
  .document-parse-toolbox {
    grid-template-columns: 220px minmax(360px, 1fr) minmax(360px, 1fr);
  }

  .parse-body {
    padding: 24px;
  }
}

@media (max-width: 920px) {
  .document-parse-toolbox {
    grid-template-columns: 1fr;
    grid-template-rows: auto minmax(420px, 1fr) minmax(420px, 1fr);
    overflow: auto;
  }

  .toolbox-sidebar {
    flex-direction: row;
    align-items: start;
    border-right: 0;
    border-bottom: 1px solid #dfe7f1;
  }

  .recent-panel {
    flex: 1;
  }
}
</style>
