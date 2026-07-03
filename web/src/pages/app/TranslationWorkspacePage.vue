<template>
  <div class="intensive-workspace-page">
    <header class="workspace-toolbar">
      <button type="button" class="back-button" @click="goBackToHub">返回</button>

      <div class="document-heading">
        <h1>{{ readingDocument?.title || 'AI 精读工作台' }}</h1>
        <span v-if="readingDocument">
          {{ readingDocument.sourceLabel || sourceTypeLabels[readingDocument.sourceType] }} · {{ readingDocument.parseStatus }} · {{ readingDocument.progress }}% · {{ modeLabels[activeMode] }}
        </span>
      </div>

      <div class="toolbar-actions">
        <button type="button" class="primary-action" @click="showPlaceholderAction('完成学习')">完成学习</button>
      </div>
    </header>

    <main
      v-if="readingDocument && activeBlock && activeInsight"
      ref="workspaceShellRef"
      class="workspace-shell workspace-shell--ide"
      :class="{
        'workspace-shell--outline-collapsed': isOutlineCollapsed,
        'workspace-shell--agent-collapsed': isAgentCollapsed,
      }"
      :style="{
        '--outline-column-width': `${outlineColumnWidth}px`,
        '--agent-column-width': `${agentColumnWidth}px`,
      }">
      <aside
        class="workspace-outline-panel"
        :class="{ 'workspace-panel--collapsed': isOutlineCollapsed }"
        aria-labelledby="outline-title">
        <button
          v-if="isOutlineCollapsed"
          type="button"
          class="workspace-drawer-rail workspace-drawer-rail--outline"
          aria-label="展开左侧目录导航"
          title="展开左侧目录导航"
          @click="toggleOutlineDrawer">
          目录
        </button>

        <div class="outline-header">
          <div>
            <h2 id="outline-title">目录导航</h2>
          </div>
          <button
            type="button"
            class="panel-drawer-toggle"
            aria-label="收起左侧目录导航"
            title="收起左侧目录导航"
            @click="toggleOutlineDrawer">
            收起
          </button>
        </div>

        <nav class="outline-list" aria-label="PDF 页码与目录">
          <section v-if="outlineItems.length > 0" class="outline-page-group">
            <button
              v-for="item in outlineItems"
              :key="item.id"
              type="button"
              class="outline-block-button"
              :class="[`outline-block-button--level-${item.level}`, { active: item.elementId === activeBlock?.elementId || item.elementId === activeBlockId }]"
              @click="selectOutlineItem(item)">
              <span>{{ item.title }}</span>
              <small>Page {{ item.pageNumber }}</small>
            </button>
          </section>

          <section v-else class="outline-page-group">
            <button
              v-for="page in outlinePageItems"
              :key="page"
              type="button"
              class="outline-page-button"
              :class="{ active: page === currentPdfPage }"
              @click="selectOutlinePage(page)">
              <span>Page {{ page }}</span>
            </button>
          </section>
        </nav>
      </aside>

      <button
        v-if="!isOutlineCollapsed"
        type="button"
        class="workspace-resizer workspace-resizer--outline"
        aria-label="调整左侧目录宽度"
        title="拖动调整左侧目录宽度"
        @pointerdown="startWorkspaceResize('outline', $event)"
      />

      <section class="workspace-canvas-panel" aria-labelledby="reader-title">
        <div class="canvas-panel-header">
          <h2 id="reader-title">阅读区</h2>
          <div class="document-view-tabs" aria-label="原文展示模式">
            <button
              type="button"
              :class="{ active: documentView === 'pdf-canvas' }"
              @click="documentView = 'pdf-canvas'">
              PDF 学习画布
            </button>
            <button
              type="button"
              :class="{ active: documentView === 'text' }"
              @click="documentView = 'text'">
              精读文本
            </button>
          </div>
        </div>

        <div v-if="documentView === 'text'" class="parsed-document-shell" aria-label="文档解析结果">
          <div class="parsed-document-toolbar" aria-label="解析结果工具栏">
            <div class="parsed-document-tabs" aria-label="解析结果视图">
              <button type="button" class="active">文档解析</button>
              <button type="button" @click="showPlaceholderAction('JSON 解析结果')">JSON</button>
            </div>
            <div class="parsed-document-status">
              <span>{{ readingDocument.parseStatus }}</span>
              <span>{{ documentParsePages.length }} / {{ readingDocument.pageCount || documentParsePages.length }} 页</span>
            </div>
          </div>

          <div class="parsed-document-scroll">
            <article
              v-for="page in documentParsePages"
              :key="page.pageNumber"
              class="parsed-page-card"
              :aria-label="`Page ${page.pageNumber} 文档解析`">
              <header class="parsed-page-header">
                <span>Page {{ page.pageNumber }}</span>
                <small>{{ page.blocks.length }} 个解析块 · {{ page.textLength }} 字符</small>
              </header>

              <section class="parsed-page-body">
                <article
                  v-for="block in page.blocks"
                  :key="block.id"
                  class="parsed-block"
                  :class="[`parsed-block--${block.displayType}`, { active: block.id === activeBlockId }]"
                  @click="selectOutlineBlock(block.id, block.pageNumber || page.pageNumber)">
                  <div class="parsed-block-meta">
                    <span>{{ parsedBlockTypeLabel(block.displayType) }}</span>
                    <small v-if="block.confidence !== null && block.confidence !== undefined">
                      confidence {{ Math.round(block.confidence * 100) }}%
                    </small>
                  </div>
                  <h1 v-if="block.displayType === 'title'" class="parsed-title">{{ block.text }}</h1>
                  <h2 v-else-if="block.displayType === 'heading'" class="parsed-heading">{{ block.text }}</h2>
                  <pre v-else-if="block.displayType === 'table' || block.displayType === 'code'" class="parsed-preformatted">{{ block.text }}</pre>
                  <p v-else class="parsed-paragraph">{{ block.text }}</p>
                </article>
              </section>
            </article>

            <section v-if="documentParsePages.length === 0" class="parsed-empty-state">
              <strong>暂无可展示的解析文本</strong>
              <span>后台 OCR 完成后，这里会自动刷新展示文档解析结果。</span>
            </section>
          </div>
        </div>

        <PdfLearningCanvas
          v-else
          :document-id="readingDocument.id"
          :title="readingDocument.title"
          :src="readingDocument.pdfPreviewUrl"
          :blocks="readingDocument.blocks"
          :active-block-id="activeBlockId"
          :page-count="readingDocument.pageCount"
          :target-page="targetPdfPage"
          :source-highlight="pdfSourceHighlight"
          @select-block="selectBlock"
          @ask-agent="askAgent"
          @selection-change="handlePdfSelectionChange"
          @page-change="currentPdfPage = $event"
        />
      </section>

      <button
        v-if="!isAgentCollapsed"
        type="button"
        class="workspace-resizer workspace-resizer--agent"
        aria-label="调整右侧 Agent 宽度"
        title="拖动调整右侧 Agent 宽度"
        @pointerdown="startWorkspaceResize('agent', $event)"
      />

      <aside
        class="agent-panel agent-panel--ide workspace-agent-panel"
        :class="{ 'workspace-panel--collapsed': isAgentCollapsed }"
        aria-labelledby="agent-title">
        <button
          v-if="isAgentCollapsed"
          type="button"
          class="workspace-drawer-rail workspace-drawer-rail--agent"
          aria-label="展开右侧 Agent"
          title="展开右侧 Agent"
          @click="toggleAgentDrawer">
          Agent
        </button>

        <div class="agent-header agent-header--ide">
          <div>
            <h2 id="agent-title">Agent</h2>
          </div>
          <span>P{{ activeBlock.order }} · {{ modeLabels[activeMode] }}</span>
          <button
            type="button"
            class="panel-drawer-toggle"
            aria-label="收起右侧 Agent"
            title="收起右侧 Agent"
            @click="toggleAgentDrawer">
            收起
          </button>
        </div>

        <section class="agent-context">
          <p class="answer-label">上下文</p>
          <strong>{{ selectedPdfText ? '当前选区' : '当前页 / 当前段落' }}</strong>
          <small v-if="selectedPdfContext">
            Page {{ selectedPdfContext.pageNumber }} · {{ selectedPdfContext.elementId }}
            <template v-if="selectedPdfSelectionType === 'region'"> · region</template>
          </small>
          <blockquote>{{ agentContextText }}</blockquote>
        </section>

        <section class="agent-answer agent-answer--ide">
          <p class="answer-label">推荐译文</p>
          <p>{{ activeInsight.translation }}</p>
        </section>

        <section class="agent-toolbar" aria-label="Agent 快捷操作">
          <button type="button" @click="askAgent('翻译并解释当前段落')">解释段落</button>
          <button type="button" @click="askAgent('拆解当前段落长难句')">长难句</button>
          <button type="button" @click="askAgent('提取当前段落短语和生词')">提取表达</button>
          <button type="button" @click="askAgent('整理为笔记')">整理笔记</button>
        </section>

        <section class="agent-card agent-card--ide">
          <p class="answer-label">学习资产候选</p>
          <div class="agent-chip-list">
            <span v-for="phrase in activeInsight.phrases" :key="phrase.text">
              {{ phrase.text }}
            </span>
            <span v-for="word in activeInsight.vocabulary" :key="word.text">
              {{ word.text }}
            </span>
            <span v-for="grammar in activeInsight.grammarPoints" :key="grammar.text">
              {{ grammar.text }}
            </span>
          </div>
        </section>

        <label class="agent-note-card">
          <span>学习笔记</span>
          <textarea
            v-model="pageNotes[currentPdfPage]"
            rows="5"
            placeholder="记录当前页或当前选区的理解，也可以让 Agent 整理。"
            @input="persistPageNotes"
          />
        </label>

        <section class="agent-conversation agent-conversation--ide">
          <article v-for="message in agentMessages" :key="message.id" :class="`message message--${message.role}`">
            <strong>{{ message.role === 'assistant' ? 'Agent' : '我' }}</strong>
            <p>{{ message.content }}</p>
            <div v-if="message.citations?.length" class="message-citations" aria-label="引用来源">
              <button
                v-for="citation in message.citations"
                :key="`${citation.chunkId}-${citation.elementId || citation.pageNumber}`"
                type="button"
                @click="jumpToCitation(citation)">
                引用 Page {{ citation.pageNumber || '?' }} · {{ citation.elementId || citation.chunkId }}
              </button>
            </div>
          </article>
        </section>

        <form class="agent-command agent-command--ide" @submit.prevent="submitAgentQuestion">
          <textarea
            v-model="agentPrompt"
            rows="4"
            placeholder="围绕当前段落提问，或让 Agent 整理成笔记..."
          />
          <div class="command-actions">
            <button type="button" @click="saveAsset('note')">保存笔记</button>
            <button type="button" @click="saveAsset('phrase')">加入短语库</button>
            <button type="submit" class="primary-action" :disabled="agentAnswerLoading">
              {{ agentAnswerLoading ? '检索中...' : '发送' }}
            </button>
          </div>
        </form>
      </aside>
    </main>

    <section v-else class="missing-state">
      <p>{{ workspaceLoading ? 'LOADING TRANSLATION' : 'TRANSLATION NOT FOUND' }}</p>
      <h1>{{ workspaceLoading ? '正在恢复精读材料' : '没有找到这篇精读材料' }}</h1>
      <span>{{ workspaceLoading ? '正在从后端知识底座读取文档结构和学习上下文。' : workspaceLoadError || '可能是知识快照不存在，或者链接里的翻译 ID 不存在。' }}</span>
      <button type="button" @click="goBackToHub">返回</button>
    </section>

    <footer v-if="readingDocument" class="asset-bar" aria-label="学习资产">
      <strong>学习资产</strong>
      <button
        v-for="stat in assetStats"
        :key="stat.id"
        type="button"
        @click="showPlaceholderAction(`${stat.label}列表`)">
        {{ stat.label }} <span>{{ stat.value }}</span>
      </button>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  answerTranslationDocumentQuestion,
  getTranslationDocumentFileUrl,
  getTranslationDocumentKnowledge,
  type TranslationSourceCitationDto,
} from '@/api/translation'
import PdfLearningCanvas from '@/components/translation/PdfLearningCanvas.vue'
import { showToast } from '@/utils/toast'
import type { TranslationSourceType } from './translationHubData'
import {
  buildAssetStats,
  buildDocumentSelectionContext,
  buildDocumentParsePages,
  buildIntensiveReadingDocument,
  createTranslationWorkspaceDraftFromParsedDocument,
  loadTranslationWorkspaceDraft,
  saveTranslationWorkspaceDraft,
  type DocumentOutlineItem,
  type DocumentSelectionContext,
  type IntensiveReadingDocument,
  type IntensiveAgentMode,
  type LearningAssetType,
  type TranslationWorkspaceDraft,
} from './translationWorkspaceData'

type AgentMessageRole = 'user' | 'assistant'
type WorkspaceResizeTarget = 'outline' | 'agent'

interface LocalAgentMessage {
  id: string
  role: AgentMessageRole
  content: string
  sourceContext?: DocumentSelectionContext | null
  citations?: TranslationSourceCitationDto[]
}

type PdfSelectionType = 'text' | 'region'

interface PdfSelectionPayload {
  text: string
  documentId: string
  pageNumber: number
  blockId: string | null
  elementId: string | null
  bbox: string | null
  selectionType?: PdfSelectionType
}

interface PdfSourceHighlight {
  pageNumber: number
  bbox: string | null
  label: string
  text?: string | null
}

const route = useRoute()
const router = useRouter()
const activeMode = ref<IntensiveAgentMode>('immersive')
const activeBlockId = ref('')
const documentView = ref<'text' | 'pdf-canvas'>('text')
const selectedPdfText = ref('')
const selectedPdfContext = ref<DocumentSelectionContext | null>(null)
const selectedPdfSelectionType = ref<PdfSelectionType | null>(null)
const pdfSourceHighlight = ref<PdfSourceHighlight | null>(null)
const targetPdfPage = ref(1)
const currentPdfPage = ref(1)
const pageNotes = ref<Record<number, string>>({})
const workspaceShellRef = ref<HTMLElement | null>(null)
const readingDocument = ref<IntensiveReadingDocument | null>(null)
const workspaceLoading = ref(false)
const workspaceLoadError = ref('')
let backgroundParseTimer: number | null = null
const outlineColumnWidth = ref(280)
const agentColumnWidth = ref(430)
const activeResizeTarget = ref<WorkspaceResizeTarget | null>(null)
const isOutlineCollapsed = ref(false)
const isAgentCollapsed = ref(false)
const agentPrompt = ref('')
const agentAnswerLoading = ref(false)
const agentMessages = ref<LocalAgentMessage[]>([
  {
    id: 'agent-welcome',
    role: 'assistant',
    content: '选择左侧段落后，我会围绕当前段落解释译文、短语、语法，并帮你整理成学习资产。',
  },
])

const modeLabels: Record<IntensiveAgentMode, string> = {
  immersive: '沉浸精读',
  foreign: '外刊精读',
  exam: '考试精读',
  technical: '技术文档',
}

const sourceTypeLabels: Record<TranslationSourceType, string> = {
  pdf: 'PDF',
  web: 'WEB',
  text: 'TXT',
  library: 'LIB',
}

const outlineColumnMinWidth = 220
const outlineColumnMaxWidth = 440
const agentColumnMinWidth = 340
const agentColumnMaxWidth = 620
const centerColumnMinWidth = 560
const resizerColumnsWidth = 16

const activeBlock = computed(() => {
  const document = readingDocument.value
  if (!document) return null
  if (!activeBlockId.value) {
    activeBlockId.value = document.blocks[0]?.id ?? ''
  }
  return document.blocks.find((block) => block.id === activeBlockId.value) ?? document.blocks[0] ?? null
})

const activeInsight = computed(() => {
  const document = readingDocument.value
  const block = activeBlock.value
  if (!document || !block) return null
  return document.insights.find((insight) => insight.blockId === block.id) ?? null
})

const agentContextText = computed(() => {
  return selectedPdfContext.value?.text || selectedPdfText.value || activeBlock.value?.text || ''
})

const outlineItems = computed<DocumentOutlineItem[]>(() => {
  return readingDocument.value?.outline ?? []
})

const outlinePageItems = computed<number[]>(() => {
  const document = readingDocument.value
  if (!document) return []
  const pages = new Set<number>()
  for (const block of document.blocks) {
    pages.add(block.pageNumber || 1)
  }
  return Array.from(pages).sort((left, right) => left - right)
})

const documentParsePages = computed(() => {
  return readingDocument.value ? buildDocumentParsePages(readingDocument.value.blocks) : []
})

const pageNotesStorageKey = computed(() => {
  return readingDocument.value ? `peai:translation-workbench-notes:${readingDocument.value.id}` : ''
})

watch(readingDocument, (document) => {
  if (document?.sourceType === 'pdf') {
    documentView.value = 'pdf-canvas'
  }
  targetPdfPage.value = activeBlock.value?.pageNumber || 1
  currentPdfPage.value = targetPdfPage.value
  restorePageNotes()
}, { immediate: true })

watch(
  () => String(route.params.id ?? ''),
  (id) => {
    clearBackgroundParseRefresh()
    void restoreWorkspaceDocument(id)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  clearBackgroundParseRefresh()
  stopWorkspaceResize()
})

const assetStats = computed(() => {
  const document = readingDocument.value
  return document ? buildAssetStats(document) : []
})

function goBackToHub() {
  void router.push('/app/translation')
}

async function restoreWorkspaceDocument(id: string, silent = false) {
  if (!silent) {
    readingDocument.value = null
    activeBlockId.value = ''
    workspaceLoadError.value = ''
  }
  if (!id) {
    workspaceLoadError.value = '缺少翻译 ID。'
    return
  }

  if (!silent) workspaceLoading.value = true
  try {
    const persisted = await getTranslationDocumentKnowledge(id)
    const localDraft = loadLocalWorkspaceDraft(id)
    const draft = createTranslationWorkspaceDraftFromParsedDocument(
      {
        mode: localDraft?.mode ?? restoreTranslationMode(),
        pdfPreviewUrl: resolvePersistedPdfPreviewUrl(id, persisted.fileUrl, localDraft?.pdfPreviewUrl),
      },
      persisted,
    )
    activeMode.value = draft.mode
    readingDocument.value = buildIntensiveReadingDocument(draft)
    if (typeof window !== 'undefined') {
      saveTranslationWorkspaceDraft(window.localStorage, draft)
    }
    workspaceLoadError.value = ''
    scheduleBackgroundParseRefresh(id, draft.ocrStatus)
  } catch {
    if (silent) {
      scheduleBackgroundParseRefresh(id, readingDocument.value?.ocrStatus)
      return
    }
    const localDraft = loadLocalWorkspaceDraft(id)
    if (localDraft) {
      activeMode.value = localDraft.mode
      readingDocument.value = buildIntensiveReadingDocument({
        ...localDraft,
        pdfPreviewUrl: localDraft.pdfPreviewUrl || getTranslationDocumentFileUrl(id),
      })
      workspaceLoadError.value = ''
      scheduleBackgroundParseRefresh(id, localDraft.ocrStatus)
      return
    }
    workspaceLoadError.value = '后端知识快照不存在，且没有可兼容恢复的本地草稿。'
  } finally {
    if (!silent) workspaceLoading.value = false
  }
}

function scheduleBackgroundParseRefresh(id: string, ocrStatus?: string) {
  clearBackgroundParseRefresh()
  if (ocrStatus !== 'PARTIAL' || typeof window === 'undefined') return
  backgroundParseTimer = window.setTimeout(() => {
    backgroundParseTimer = null
    void restoreWorkspaceDocument(id, true)
  }, 8000)
}

function clearBackgroundParseRefresh() {
  if (!backgroundParseTimer || typeof window === 'undefined') return
  window.clearTimeout(backgroundParseTimer)
  backgroundParseTimer = null
}

function resolvePersistedPdfPreviewUrl(
  documentId: string,
  persistedFileUrl?: string | null,
  localFileUrl?: string,
) {
  return persistedFileUrl || localFileUrl || getTranslationDocumentFileUrl(documentId)
}

function loadLocalWorkspaceDraft(id: string): TranslationWorkspaceDraft | null {
  if (typeof window === 'undefined') return null
  return loadTranslationWorkspaceDraft(window.localStorage, id)
}

function restoreTranslationMode() {
  return activeMode.value === 'exam' ? 'exam' : 'immersive'
}

function selectBlock(blockId: string) {
  activeBlockId.value = blockId
  clearPdfSelection()
}

function selectOutlinePage(page: number) {
  currentPdfPage.value = page
  targetPdfPage.value = page
  clearPdfSelection()
  const firstBlock = readingDocument.value?.blocks.find((block) => (block.pageNumber || 1) === page)
  if (firstBlock) activeBlockId.value = firstBlock.id
}

function selectOutlineItem(item: DocumentOutlineItem) {
  const page = item.pageNumber || 1
  const document = readingDocument.value
  const targetBlock = document?.blocks.find((block) => {
    return block.id === item.elementId || block.elementId === item.elementId
  }) ?? document?.blocks.find((block) => (block.pageNumber || 1) === page)
  if (targetBlock) {
    activeBlockId.value = targetBlock.id
  }
  targetPdfPage.value = page
  currentPdfPage.value = page
  clearPdfSelection()
}

function selectOutlineBlock(blockId: string, page: number) {
  activeBlockId.value = blockId
  targetPdfPage.value = page
  currentPdfPage.value = page
  clearPdfSelection()
}

function handlePdfSelectionChange(payload: PdfSelectionPayload) {
  const selectionType = payload.selectionType ?? 'text'
  const displayText = payload.text.trim() || (payload.selectionType === 'region' ? '图表/图片区选区' : '')
  selectedPdfSelectionType.value = displayText ? selectionType : null
  selectedPdfText.value = displayText
  if (displayText) {
    pdfSourceHighlight.value = null
  }
  selectedPdfContext.value = displayText && (payload.elementId || payload.bbox || payload.selectionType === 'region')
    ? {
        documentId: payload.documentId,
        pageNumber: payload.pageNumber,
        blockId: payload.blockId ?? payload.elementId ?? 'region-selection',
        elementId: payload.elementId ?? 'region-selection',
        bbox: payload.bbox,
        text: displayText,
      }
    : null
}

function clearPdfSelection() {
  selectedPdfText.value = ''
  selectedPdfContext.value = null
  selectedPdfSelectionType.value = null
  pdfSourceHighlight.value = null
}

function restorePageNotes() {
  if (typeof window === 'undefined' || !pageNotesStorageKey.value) return
  const raw = window.localStorage.getItem(pageNotesStorageKey.value)
  if (!raw) {
    pageNotes.value = {}
    return
  }
  try {
    pageNotes.value = JSON.parse(raw) as Record<number, string>
  } catch {
    pageNotes.value = {}
  }
}

function persistPageNotes() {
  if (typeof window === 'undefined' || !pageNotesStorageKey.value) return
  window.localStorage.setItem(pageNotesStorageKey.value, JSON.stringify(pageNotes.value))
}

function toggleOutlineDrawer() {
  isOutlineCollapsed.value = !isOutlineCollapsed.value
  stopWorkspaceResize()
}

function toggleAgentDrawer() {
  isAgentCollapsed.value = !isAgentCollapsed.value
  stopWorkspaceResize()
}

function startWorkspaceResize(target: WorkspaceResizeTarget, event: PointerEvent) {
  if ((target === 'outline' && isOutlineCollapsed.value) || (target === 'agent' && isAgentCollapsed.value)) {
    return
  }
  activeResizeTarget.value = target
  event.preventDefault()
  document.body.classList.add('translation-workspace-resizing')
  window.addEventListener('pointermove', resizeWorkspacePanels)
  window.addEventListener('pointerup', stopWorkspaceResize)
}

function resizeWorkspacePanels(event: PointerEvent) {
  const shell = workspaceShellRef.value
  const target = activeResizeTarget.value
  if (!shell || !target) return

  const rect = shell.getBoundingClientRect()
  const shellWidth = rect.width

  if (target === 'outline') {
    const maxOutlineWidth = Math.min(
      outlineColumnMaxWidth,
      shellWidth - agentColumnWidth.value - centerColumnMinWidth - resizerColumnsWidth,
    )
    outlineColumnWidth.value = clamp(
      event.clientX - rect.left,
      outlineColumnMinWidth,
      Math.max(outlineColumnMinWidth, maxOutlineWidth),
    )
    return
  }

  const maxAgentWidth = Math.min(
    agentColumnMaxWidth,
    shellWidth - outlineColumnWidth.value - centerColumnMinWidth - resizerColumnsWidth,
  )
  agentColumnWidth.value = clamp(
    rect.right - event.clientX,
    agentColumnMinWidth,
    Math.max(agentColumnMinWidth, maxAgentWidth),
  )
}

function stopWorkspaceResize() {
  activeResizeTarget.value = null
  if (typeof document !== 'undefined') {
    document.body.classList.remove('translation-workspace-resizing')
  }
  if (typeof window !== 'undefined') {
    window.removeEventListener('pointermove', resizeWorkspacePanels)
    window.removeEventListener('pointerup', stopWorkspaceResize)
  }
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function showPlaceholderAction(label: string) {
  showToast(`${label} 即将接入`, 'info')
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

function askAgent(question: string) {
  agentPrompt.value = question
  void submitAgentQuestion()
}

async function submitAgentQuestion() {
  const question = agentPrompt.value.trim()
  const document = readingDocument.value
  if (!question || !activeBlock.value || !document || agentAnswerLoading.value) return

  const currentBlock = activeBlock.value
  const sourceContext = resolveAgentSourceContext()
  const selectedQuestion = question
  agentPrompt.value = ''
  agentMessages.value.push({
    id: `user-${Date.now()}`,
    role: 'user',
    content: selectedQuestion,
    sourceContext,
  })
  agentAnswerLoading.value = true
  try {
    const answer = await answerTranslationDocumentQuestion(document.id, {
      question: selectedQuestion,
      selectedText: sourceContext?.text ?? currentBlock.text,
      pageNumber: sourceContext?.pageNumber ?? currentBlock.pageNumber,
      elementId: sourceContext?.elementId ?? currentBlock.elementId ?? currentBlock.id,
      bbox: sourceContext?.bbox ?? currentBlock.bbox ?? null,
      mode: activeMode.value,
    })
    agentMessages.value.push({
      id: `assistant-${Date.now()}`,
      role: 'assistant',
      content: answer.answer,
      sourceContext,
      citations: answer.citations,
    })
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Agent 回答失败'
    agentMessages.value.push({
      id: `assistant-error-${Date.now()}`,
      role: 'assistant',
      content: `暂时无法基于资料生成回答：${message}`,
      sourceContext,
    })
    showToast('Agent 回答失败，请稍后重试', 'error')
  } finally {
    agentAnswerLoading.value = false
  }
}

function resolveAgentSourceContext(): DocumentSelectionContext | null {
  if (selectedPdfContext.value) return selectedPdfContext.value
  const document = readingDocument.value
  const block = activeBlock.value
  if (!document || !block) return null
  return buildDocumentSelectionContext(document.id, block)
}

function jumpToCitation(citation: TranslationSourceCitationDto) {
  const document = readingDocument.value
  if (!document) return
  const pageNumber = citation.pageNumber || currentPdfPage.value || 1
  const matchedBlock = document.blocks.find((block) => {
    return block.elementId === citation.elementId || block.id === citation.elementId
  })
  if (matchedBlock) {
    activeBlockId.value = matchedBlock.id
  }
  const resolvedElementId = citation.elementId ?? matchedBlock?.elementId ?? matchedBlock?.id ?? citation.chunkId
  targetPdfPage.value = pageNumber
  currentPdfPage.value = pageNumber
  documentView.value = 'pdf-canvas'
  selectedPdfText.value = citation.quote
  selectedPdfSelectionType.value = 'text'
  pdfSourceHighlight.value = buildCitationHighlight(citation, pageNumber)
  selectedPdfContext.value = {
    documentId: document.id,
    pageNumber,
    blockId: matchedBlock?.id ?? resolvedElementId,
    elementId: resolvedElementId,
    bbox: citation.bbox,
    text: citation.quote,
  }
}

function buildCitationHighlight(
  citation: TranslationSourceCitationDto,
  pageNumber: number,
): PdfSourceHighlight | null {
  if (!citation.bbox) return null
  return {
    pageNumber,
    bbox: citation.bbox,
    label: '引用定位',
    text: citation.quote,
  }
}

function saveAsset(type: LearningAssetType) {
  const typeLabels: Record<LearningAssetType, string> = {
    vocabulary: '生词',
    phrase: '短语',
    sentence: '句型',
    grammar: '语法',
    note: '笔记',
    'review-card': '复习卡',
  }
  showToast(`已保存到${typeLabels[type]}，后续会接入持久化资产库`, 'success')
}
</script>

<style scoped>
.intensive-workspace-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 10px;
  height: 100vh;
  min-height: 0;
  padding: 10px 12px 8px;
  overflow: hidden;
  background: #f5f7fa;
  color: #102033;
}

.workspace-toolbar,
.workspace-shell,
.asset-bar {
  width: 100%;
  margin: 0;
}

.workspace-toolbar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
}

button,
textarea {
  font: inherit;
}

.back-button,
.toolbar-actions button,
.reader-status span,
.inline-actions button,
.agent-card-actions button,
.command-actions button,
.asset-bar button,
.missing-state button {
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #344054;
  font-weight: 800;
}

.back-button,
.toolbar-actions button,
.command-actions button,
.missing-state button {
  min-height: 38px;
  padding: 0 13px;
  cursor: pointer;
}

.primary-action {
  border-color: #0f8f89 !important;
  background: #0f8f89 !important;
  color: #ffffff !important;
}

.document-heading p,
.reader-heading p,
.agent-header p,
.answer-label,
.missing-state p {
  margin: 0;
  color: #667085;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0;
}

.document-heading h1 {
  max-width: 820px;
  margin: 0;
  overflow: hidden;
  color: #111827;
  font-size: 22px;
  line-height: 1.15;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-heading span {
  display: block;
  max-width: 820px;
  overflow: hidden;
  color: #526071;
  font-size: 13px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.workspace-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 20px;
  min-height: 0;
  align-items: stretch;
}

.workspace-shell--ide {
  grid-template-columns:
    minmax(220px, var(--outline-column-width, 280px))
    8px
    minmax(560px, 1fr)
    8px
    minmax(340px, var(--agent-column-width, 430px));
  align-items: stretch;
  gap: 0;
  width: 100%;
  height: 100%;
}

.workspace-shell--outline-collapsed {
  grid-template-columns:
    44px
    0
    minmax(560px, 1fr)
    8px
    minmax(340px, var(--agent-column-width, 430px));
}

.workspace-shell--agent-collapsed {
  grid-template-columns:
    minmax(220px, var(--outline-column-width, 280px))
    8px
    minmax(560px, 1fr)
    0
    44px;
}

.workspace-shell--outline-collapsed.workspace-shell--agent-collapsed {
  grid-template-columns: 44px 0 minmax(560px, 1fr) 0 44px;
}

.workspace-outline-panel,
.workspace-canvas-panel,
.workspace-agent-panel {
  min-height: 0;
  height: 100%;
  min-width: 0;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  overflow: hidden;
}

.workspace-outline-panel {
  grid-column: 1;
}

.workspace-resizer--outline {
  grid-column: 2;
}

.workspace-canvas-panel {
  grid-column: 3;
}

.workspace-resizer--agent {
  grid-column: 4;
}

.workspace-agent-panel {
  grid-column: 5;
}

.workspace-outline-panel {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
}

.workspace-canvas-panel {
  border-radius: 0;
}

.workspace-agent-panel {
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
}

.workspace-resizer {
  position: relative;
  z-index: 5;
  min-width: 8px;
  height: 100%;
  padding: 0;
  border: 0;
  background:
    linear-gradient(90deg, transparent 0, transparent 3px, #cbd5e1 3px, #cbd5e1 5px, transparent 5px);
  cursor: col-resize;
}

.workspace-resizer::after {
  position: absolute;
  inset: 0 -5px;
  content: '';
}

.workspace-resizer:hover,
.workspace-resizer:focus-visible {
  background:
    linear-gradient(90deg, transparent 0, transparent 2px, #14b8a6 2px, #14b8a6 6px, transparent 6px);
  outline: none;
}

:global(.translation-workspace-resizing) {
  cursor: col-resize;
  user-select: none;
}

.workspace-panel--collapsed {
  display: grid !important;
  grid-template-rows: minmax(0, 1fr) !important;
  place-items: stretch;
  padding: 0 !important;
}

.workspace-panel--collapsed > :not(.workspace-drawer-rail) {
  display: none !important;
}

.workspace-drawer-rail {
  display: grid;
  width: 100%;
  min-width: 0;
  height: 100%;
  place-items: center;
  border: 0;
  border-radius: 0;
  background: #ffffff;
  color: #344054;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
  writing-mode: vertical-rl;
}

.workspace-drawer-rail:hover,
.workspace-drawer-rail:focus-visible {
  background: #ecfdf5;
  color: #0f766e;
  transform: none;
}

.panel-drawer-toggle {
  flex: 0 0 auto;
  min-height: 28px;
  padding: 0 8px;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #ffffff;
  color: #344054;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.panel-drawer-toggle:hover {
  border-color: #14b8a6;
  background: #ecfdf5;
  color: #0f766e;
}

.workspace-outline-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.outline-header,
.canvas-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #e2e8f0;
}

.outline-header h2,
.canvas-panel-header h2 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  line-height: 1.2;
  font-weight: 900;
}

.outline-list {
  min-height: 0;
  overflow: auto;
  padding: 8px;
}

.outline-page-group {
  display: grid;
  gap: 4px;
  margin-bottom: 10px;
}

.outline-page-button,
.outline-block-button {
  display: grid;
  width: 100%;
  min-width: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #344054;
  text-align: left;
  cursor: pointer;
}

.outline-page-button {
  grid-template-columns: minmax(0, 1fr);
  align-items: center;
  padding: 8px 9px;
  font-weight: 900;
}

.outline-block-button {
  gap: 2px;
  padding: 7px 9px 7px 18px;
}

.outline-page-button:hover,
.outline-block-button:hover,
.outline-page-button.active,
.outline-block-button.active {
  background: #ecfdf5;
  color: #0f766e;
}

.outline-page-button span,
.outline-block-button span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.outline-page-button small,
.outline-block-button small {
  color: #667085;
  font-size: 11px;
  font-weight: 800;
}

.workspace-canvas-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.workspace-canvas-panel :deep(.pdf-learning-canvas) {
  min-height: 0;
  height: 100%;
}

.document-reader,
.agent-panel,
.missing-state {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.document-reader {
  min-width: 0;
  padding: 18px;
}

.document-reader--ide {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  height: calc(100vh - 184px);
  min-height: 620px;
  padding: 0;
  overflow: hidden;
}

.ide-pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid #e2e8f0;
}

.ide-pane-header p {
  margin: 0;
  color: #667085;
  font-size: 12px;
  font-weight: 900;
}

.ide-pane-header h2 {
  margin: 3px 0 0;
  color: #111827;
  font-size: 20px;
  line-height: 1.2;
  font-weight: 900;
}

.document-pathbar {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  border-bottom: 1px solid #edf1f6;
  background: #f8fafc;
}

.document-pathbar strong {
  min-width: 0;
  overflow: hidden;
  color: #111827;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-pathbar small {
  min-width: 0;
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-view-tabs {
  display: flex;
  gap: 8px;
  padding: 10px 18px;
  border-bottom: 1px solid #edf1f6;
  background: #ffffff;
}

.document-view-tabs button {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #ffffff;
  color: #344054;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
}

.document-view-tabs button.active {
  border-color: #0f8f89;
  background: #ecfdf5;
  color: #0f766e;
}

.document-badge--compact {
  flex: 0 0 auto;
  width: auto;
  height: 26px;
  min-width: 38px;
  padding: 0 9px;
  border-radius: 6px;
  font-size: 12px;
}

.ide-reader-surface {
  min-height: 0;
  overflow: auto;
  padding: 14px 0 24px;
  background:
    linear-gradient(#ffffff 31px, transparent 31px) 0 0 / 100% 32px,
    #ffffff;
}

.parsed-document-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  overflow: hidden;
  background: #f6f8fb;
}

.parsed-document-toolbar {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 18px;
  border-bottom: 1px solid #e2e8f0;
  background: #ffffff;
}

.parsed-document-tabs {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.parsed-document-tabs button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #344054;
  font-size: 14px;
  font-weight: 900;
  cursor: pointer;
}

.parsed-document-tabs button.active {
  border-color: #dbeafe;
  background: #eef4ff;
  color: #2563eb;
}

.parsed-document-status {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: #667085;
  font-size: 12px;
  font-weight: 900;
}

.parsed-document-status span {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.parsed-document-scroll {
  min-height: 0;
  overflow: auto;
  padding: 24px 0 42px;
}

.parsed-page-card {
  width: min(920px, calc(100% - 48px));
  min-width: 0;
  margin: 0 auto 24px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.05);
}

.parsed-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 18px;
  border-bottom: 1px solid #edf1f6;
  color: #667085;
  font-size: 12px;
  font-weight: 900;
}

.parsed-page-header small {
  min-width: 0;
  overflow: hidden;
  color: #98a2b3;
  font-size: 12px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.parsed-page-body {
  padding: 40px 58px 54px;
}

.parsed-block {
  min-width: 0;
  margin: 0 0 18px;
  padding: 9px 12px;
  border-left: 3px solid transparent;
  border-radius: 6px;
  cursor: pointer;
}

.parsed-block:hover {
  background: #f8fafc;
}

.parsed-block.active {
  border-left-color: #0f8f89;
  background: #f0fdfa;
}

.parsed-block-meta {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
  color: #98a2b3;
  font-size: 11px;
  font-weight: 900;
}

.parsed-block-meta span,
.parsed-block-meta small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.parsed-title,
.parsed-heading,
.parsed-paragraph,
.parsed-preformatted {
  margin: 0;
  color: #202733;
  letter-spacing: 0;
}

.parsed-title {
  font-size: 34px;
  line-height: 1.28;
  font-weight: 900;
}

.parsed-heading {
  font-size: 24px;
  line-height: 1.35;
  font-weight: 900;
}

.parsed-paragraph {
  font-size: 18px;
  line-height: 2.05;
  font-weight: 600;
  white-space: pre-wrap;
}

.parsed-preformatted {
  overflow: auto;
  padding: 12px;
  border-radius: 6px;
  background: #f8fafc;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.parsed-empty-state {
  display: grid;
  width: min(620px, calc(100% - 48px));
  margin: 70px auto 0;
  gap: 8px;
  place-items: center;
  padding: 36px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
  color: #667085;
  text-align: center;
}

.parsed-empty-state strong {
  color: #111827;
  font-size: 18px;
}

.ide-document-block {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 0;
  border-left: 3px solid transparent;
  cursor: pointer;
}

.ide-document-block:hover {
  background: #f8fafc;
}

.ide-document-block.active {
  border-left-color: #0f8f89;
  background: #f0fdfa;
}

.ide-gutter {
  display: grid;
  align-content: start;
  gap: 5px;
  padding: 18px 12px 18px 18px;
  border-right: 1px solid #edf1f6;
  color: #667085;
  text-align: right;
}

.ide-gutter span {
  color: #0f766e;
  font-size: 13px;
  font-weight: 900;
}

.ide-gutter small {
  font-size: 11px;
  font-weight: 800;
}

.ide-source-cell {
  min-width: 0;
  padding: 14px 18px 18px;
}

.ide-block-meta {
  display: flex;
  min-height: 28px;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  opacity: 0;
  transition: opacity 0.16s ease;
}

.ide-document-block.active .ide-block-meta,
.ide-document-block:hover .ide-block-meta {
  opacity: 1;
}

.ide-block-meta span {
  margin-right: auto;
  color: #667085;
  font-size: 11px;
  font-weight: 900;
  text-transform: uppercase;
}

.ide-block-meta button,
.agent-toolbar button {
  min-height: 26px;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #ffffff;
  color: #344054;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.ide-block-meta button {
  padding: 0 8px;
}

.source-text--ide {
  max-width: 860px;
  color: #111827;
  font-size: 17px;
  line-height: 1.95;
}

.reader-heading,
.agent-header,
.document-summary,
.inline-actions,
.command-actions,
.asset-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.reader-heading h2,
.agent-header h2 {
  margin: 4px 0 0;
  color: #111827;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 900;
}

.reader-status {
  display: flex;
  gap: 8px;
}

.reader-status span {
  padding: 7px 10px;
  font-size: 12px;
}

.document-summary {
  justify-content: flex-start;
  margin: 16px 0;
  padding: 14px;
  border: 1px solid #edf1f6;
  border-radius: 8px;
  background: #f8fafc;
}

.document-badge {
  display: grid;
  place-items: center;
  width: 56px;
  height: 66px;
  border-radius: 8px;
  background: #dbeafe;
  color: #2563eb;
  font-weight: 900;
}

.document-badge--pdf { background: #dbeafe; color: #2563eb; }
.document-badge--web { background: #ccfbf1; color: #0f766e; }
.document-badge--text { background: #ffedd5; color: #c2410c; }
.document-badge--library { background: #ede9fe; color: #7c3aed; }

.document-summary h3 {
  margin: 0;
  color: #111827;
  font-size: 18px;
}

.document-summary p {
  margin: 5px 0 0;
  color: #667085;
  font-weight: 700;
}

.block-list {
  display: grid;
  gap: 12px;
}

.document-block {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  gap: 12px;
  padding: 14px;
  border: 1px solid #edf1f6;
  border-radius: 8px;
  background: #ffffff;
  cursor: pointer;
}

.document-block.active {
  border-color: #6ee7dc;
  background: #fbfffd;
  box-shadow: 0 12px 30px rgba(15, 143, 137, 0.08);
}

.block-label span {
  display: block;
  color: #0f766e;
  font-size: 13px;
  font-weight: 900;
}

.block-label small {
  display: block;
  margin-top: 6px;
  color: #667085;
  font-size: 11px;
  font-weight: 800;
}

.source-text {
  margin: 0;
  color: #1f2937;
  font-size: 16px;
  line-height: 1.85;
}

.learning-layer {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.translation-card,
.insight-grid section,
.inline-note textarea,
.agent-answer,
.agent-card,
.agent-command textarea {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.translation-card {
  padding: 13px;
  background: #f0fdfa;
}

.translation-card span,
.insight-grid span,
.inline-note span {
  display: inline-block;
  margin-bottom: 7px;
  color: #667085;
  font-size: 12px;
  font-weight: 900;
}

.translation-card p {
  margin: 0;
  color: #1f2937;
  line-height: 1.7;
  font-weight: 700;
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.insight-grid section {
  min-width: 0;
  padding: 12px;
}

.chip-list,
.agent-chip-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.chip-list button,
.agent-chip-list span {
  min-height: 26px;
  padding: 0 9px;
  border: 0;
  border-radius: 999px;
  background: #e7f7f3;
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.inline-note {
  display: grid;
}

.inline-note textarea,
.agent-command textarea {
  width: 100%;
  padding: 11px;
  color: #1f2937;
  line-height: 1.6;
  resize: vertical;
  box-sizing: border-box;
}

.inline-actions {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.inline-actions button,
.agent-card-actions button {
  min-height: 32px;
  padding: 0 10px;
  cursor: pointer;
}

.agent-panel {
  position: sticky;
  top: 18px;
  display: grid;
  gap: 12px;
  max-height: calc(100vh - 120px);
  overflow: auto;
  padding: 16px;
}

.agent-panel--ide {
  display: grid;
  grid-template-rows: auto auto auto auto auto auto minmax(0, 1fr) auto;
  height: 100%;
  min-height: 0;
  max-height: none;
  padding: 0;
  overflow: hidden;
}

.agent-header--ide,
.agent-context,
.agent-answer--ide,
.agent-card--ide,
.agent-command--ide {
  border-bottom: 1px solid #edf1f6;
}

.agent-header--ide {
  padding: 16px 18px;
}

.agent-context {
  padding: 13px 18px;
  background: #fbfffd;
}

.agent-context blockquote {
  display: -webkit-box;
  margin: 7px 0 0;
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  line-height: 1.7;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.agent-context small {
  display: block;
  margin-top: 5px;
  color: #667085;
  font-size: 11px;
  font-weight: 800;
}

.agent-answer--ide,
.agent-card--ide {
  padding: 13px 18px;
  border-right: 0;
  border-left: 0;
  border-radius: 0;
}

.agent-note-card {
  display: grid;
  gap: 8px;
  padding: 13px 18px;
  border-bottom: 1px solid #edf1f6;
  background: #ffffff;
}

.agent-note-card span {
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.agent-note-card textarea {
  width: 100%;
  min-height: 104px;
  resize: vertical;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  padding: 10px;
  background: #f8fafc;
  color: #111827;
  font: inherit;
  line-height: 1.55;
}

.agent-answer--ide {
  background: #ffffff;
}

.agent-toolbar {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 12px 18px;
  border-bottom: 1px solid #edf1f6;
  background: #f8fafc;
}

.agent-toolbar button {
  padding: 0 10px;
}

.agent-conversation--ide {
  min-height: 0;
  overflow: auto;
  padding: 12px 18px;
  background: #ffffff;
}

.agent-command--ide {
  padding: 12px 18px 16px;
  background: #f8fafc;
}

.agent-command--ide textarea {
  min-height: 96px;
  background: #ffffff;
}

.agent-header {
  align-items: flex-start;
}

.agent-header span {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  padding: 0 9px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
}

.agent-answer,
.agent-card {
  padding: 13px;
}

.agent-answer {
  background: #f8fffd;
}

.agent-answer h3 {
  margin: 7px 0;
  color: #111827;
  font-size: 15px;
  line-height: 1.45;
}

.agent-answer p,
.agent-card p {
  margin: 0;
  color: #344054;
  line-height: 1.65;
}

.capability-list {
  display: grid;
  gap: 8px;
}

.capability-list article {
  display: grid;
  gap: 3px;
  padding: 10px;
  border-radius: 8px;
  background: #f8fafc;
}

.capability-list strong {
  color: #111827;
  font-size: 13px;
}

.capability-list span {
  color: #667085;
  font-size: 12px;
  line-height: 1.45;
}

.agent-card-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.agent-conversation {
  display: grid;
  gap: 8px;
}

.message {
  padding: 10px;
  border-radius: 8px;
  background: #f8fafc;
}

.message--assistant {
  background: #ecfdf5;
}

.message strong {
  display: block;
  margin-bottom: 4px;
  color: #111827;
  font-size: 12px;
}

.message p {
  margin: 0;
  color: #344054;
  font-size: 13px;
  line-height: 1.55;
}

.message-citations {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.message-citations button {
  min-height: 28px;
  padding: 5px 8px;
  border: 1px solid #b7e4db;
  border-radius: 8px;
  background: #f0fdfa;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.message-citations button:hover {
  border-color: #0f766e;
  background: #ccfbf1;
}

.agent-command {
  display: grid;
  gap: 8px;
}

.asset-bar {
  position: static;
  transform: none;
  z-index: 20;
  min-height: 42px;
  padding: 6px 10px;
  border: 1px solid #dbe5ee;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
  justify-content: flex-start;
}

.asset-bar strong {
  color: #111827;
  font-size: 13px;
}

.asset-bar button {
  min-height: 30px;
  padding: 0 10px;
  cursor: pointer;
}

.asset-bar span {
  color: #0f766e;
}

.missing-state {
  width: min(620px, calc(100% - 48px));
  margin: 90px auto 0;
  padding: 34px;
  text-align: center;
}

.missing-state h1 {
  margin: 8px 0;
  color: #111827;
  font-size: 30px;
}

.missing-state span {
  display: block;
  margin-bottom: 18px;
  color: #667085;
  line-height: 1.6;
}

button:hover {
  transform: translateY(-1px);
}

.workspace-resizer:hover {
  transform: none;
}

button:focus-visible,
textarea:focus-visible {
  outline: 3px solid rgba(20, 184, 166, 0.24);
  outline-offset: 2px;
}

@media (max-width: 1180px) {
  .workspace-toolbar,
  .workspace-shell {
    grid-template-columns: 1fr;
  }

  .workspace-resizer {
    display: none;
  }

  .workspace-outline-panel,
  .workspace-canvas-panel,
  .workspace-agent-panel {
    grid-column: 1;
  }

  .workspace-shell--ide,
  .workspace-shell--outline-collapsed,
  .workspace-shell--agent-collapsed,
  .workspace-shell--outline-collapsed.workspace-shell--agent-collapsed {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    justify-content: flex-start;
  }

  .agent-panel {
    position: static;
    max-height: none;
  }
}

@media (max-width: 760px) {
  .intensive-workspace-page {
    padding: 16px 14px 112px;
  }

  .asset-bar,
  .reader-heading,
  .document-summary,
  .agent-header,
  .command-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .document-block,
  .insight-grid {
    grid-template-columns: 1fr;
  }

  .asset-bar {
    overflow-x: auto;
    align-items: center;
    flex-direction: row;
  }
}
</style>
