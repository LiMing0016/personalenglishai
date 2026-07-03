<template>
  <div class="intensive-workspace-page">
    <LearningIdeTopBar
      brand="StudyingX"
      :sync-status="workspaceStateSaving ? '同步中' : '已同步'"
      @add-module="moduleLibraryOpen = true"
      @command-search="handleLearningCommandSearch"
      @back="goBackToHub"
      @complete="completeLearningSession"
    />

    <LearningModuleLibrary
      :open="moduleLibraryOpen"
      :groups="learningModuleGroups"
      @add-module="handleAddLearningModule"
      @close="moduleLibraryOpen = false"
    />

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
      <template v-if="isOutlineCollapsed">
        <aside
          class="workspace-outline-panel workspace-side-drawer workspace-explorer workspace-panel--collapsed"
          aria-label="目录与学习资产">
          <button
            type="button"
            class="workspace-drawer-rail"
            aria-label="展开左侧学习资源"
            title="展开左侧学习资源"
            @click="toggleOutlineDrawer">
            资源
          </button>
        </aside>
      </template>
      <LearningResourcePanel
        v-else
        class="workspace-outline-panel workspace-side-drawer workspace-explorer"
        :side-panels="learningSidePanelOptions"
        :active-side-panel="activeSidePanel"
        :active-explorer-view="activeResourceExplorerView"
        :project-folders="learningProjectTreeFolders"
        :file-folders="learningCurrentFileTreeFolders"
        :current-file-title="currentFileTitle"
        :current-file-subtitle="currentFileSubtitle"
        :collapsed-folder-ids="collapsedProjectTreeFolderIdList"
        @select-explorer-view="selectResourceExplorerView"
        @select-panel="selectLearningSidePanel"
        @toggle-folder="toggleLearningProjectTreeFolder"
        @open-resource="openLearningProjectTreeResource"
        @empty-action="handleLearningProjectTreeFolderEmptyAction"
      />
      <button
        v-if="!isOutlineCollapsed"
        type="button"
        class="workspace-resizer workspace-resizer--outline"
        aria-label="调整左侧目录宽度"
        title="拖动调整左侧目录宽度"
        @pointerdown="startWorkspaceResize('outline', $event)"
      />

      <section class="workspace-canvas-panel" aria-label="阅读区">
        <WorkspaceTabs
          :tabs="learningWorkspaceTabs"
          :active-tab-id="activeWorkspaceTabId"
          @activate="activateWorkspaceTab"
          @close="closeWorkspaceTab"
          @create="openStandaloneNoteTab"
        />

        <div class="workspace-editor-area" :class="`workspace-editor-area--${activeWorkspaceTabKind}`">
          <section v-if="activeWorkspaceTabKind !== 'pdf'" class="note-document-editor">
            <p>{{ activeWorkspaceTab?.subtitle }}</p>
            <h2>{{ activeWorkspaceTab?.title }}</h2>
            <textarea placeholder="这里是完整笔记编辑区，后续阶段接入真实笔记内容。" />
          </section>

          <template v-else>
            <div class="learning-editor-grid">
              <section class="learning-editor-primary" aria-label="PDF 与精读文本">
                <div class="workspace-editor-toolbar">
                  <div v-if="readingDocument" class="document-view-tabs document-view-tabs--compact" aria-label="原文展示模式">
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
                  :note-anchors="noteAnchors"
                  :active-note-id="activeNoteId"
                  @select-block="selectBlock"
                  @ask-agent="askAgent"
                  @note-selection="startNoteFromPdfSelection"
                  @open-note="openStudyNote"
                  @selection-change="handlePdfSelectionChange"
                  @page-change="handlePdfPageChange"
                />
              </section>

              <aside class="learning-knowledge-column" aria-label="知识卡与图谱">
                <KnowledgeCardView
                  :card="activeLearningKnowledgeCard"
                  @open-block-ref="openLearningBlockReference"
                />
                <BacklinksPanel
                  :backlinks="activeLearningBacklinks"
                  @open-source="openLearningBacklinkSource"
                />
                <LocalGraphPanel
                  :graph="learningKnowledgeGraph"
                  :active-node-id="activeLearningKnowledgeCard.id"
                  @select-node="openLearningGraphNode"
                />
              </aside>
            </div>
          </template>
        </div>

        <LearningOutputDock
          :outputs="learningOutputItems"
          :active-output-id="activeLearningOutputId"
          @select-output="activeLearningOutputId = $event"
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

      <template v-if="isAgentCollapsed">
        <aside
          class="workspace-agent-panel workspace-panel--collapsed"
          aria-label="AI 上下文助手">
          <button
            type="button"
            class="workspace-drawer-rail workspace-drawer-rail--agent"
            aria-label="展开右侧 Agent"
            title="展开右侧 Agent"
            @click="toggleAgentDrawer">
            AI
          </button>
        </aside>
      </template>
      <ContextAssistantPanel
        v-else
        v-model:prompt="agentPrompt"
        v-model:note-title="noteComposer.title"
        v-model:note-content="noteComposer.content"
        v-model:note-agent-prompt="noteAgentPrompt"
        class="workspace-agent-panel"
        :active-mode-label="modeLabels[activeMode]"
        :context-title="selectedPdfText ? '当前选区' : '当前页 / 当前段落'"
        :context-text="agentContextText"
        :messages="learningAssistantMessages"
        :loading="agentAnswerLoading"
        :note-composer-active="noteComposer.mode !== 'idle'"
        :note-selected-text="noteComposer.context?.text || ''"
        :note-context-label="noteComposerContextLabel"
        :note-agent-loading="noteAgentLoading"
        :ai-candidate-content="aiCandidateContent"
        @collapse="toggleAgentDrawer"
        @quick-action="handleLearningAssistantQuickAction"
        @open-citation="jumpToCitation"
        @save-note="saveStudyNote"
        @cancel-note="cancelStudyNoteComposer"
        @ask-note-agent="askAgentToAppendNote"
        @append-ai-candidate="appendAiCandidateToNote"
        @append-agent-answer="appendAgentAnswerToNoteComposer"
        @submit="submitAgentQuestion"
      />
    </main>

    <section v-else class="missing-state">
      <p>{{ workspaceLoading ? 'LOADING TRANSLATION' : 'TRANSLATION NOT FOUND' }}</p>
      <h1>{{ workspaceLoading ? '正在恢复精读材料' : '没有找到这篇精读材料' }}</h1>
      <span>{{ workspaceLoading ? '正在从后端知识底座读取文档结构和学习上下文。' : workspaceLoadError || '可能是知识快照不存在，或者链接里的翻译 ID 不存在。' }}</span>
      <button type="button" @click="goBackToHub">返回</button>
    </section>

    <footer v-if="readingDocument" class="workspace-status-bar workspace-status-bar--ide" aria-label="学习工作台状态">
      <span>{{ totalStudyNoteCount }} 条笔记</span>
      <span>{{ userBookmarks.length }} 个书签</span>
      <span>{{ workspaceStateSaving ? '同步中' : '已同步' }}</span>
      <span>P{{ currentPdfPage }}</span>
      <span>{{ modeLabels[activeMode] }}</span>
      <button type="button" @click="selectSidePanel('assets')">整理队列</button>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  answerTranslationDocumentQuestion,
  downloadTranslationDocumentWithBookmarks,
  getTranslationDocumentFileUrl,
  getTranslationDocumentKnowledge,
  saveTranslationDocumentWorkspaceState,
  type TranslationDocumentStudyNoteDto,
  type TranslationDocumentUserBookmarkDto,
  type TranslationDocumentWorkspaceStateDto,
  type TranslationSourceCitationDto,
} from '@/api/translation'
import BacklinksPanel from '@/components/learning-ide/BacklinksPanel.vue'
import ContextAssistantPanel from '@/components/learning-ide/ContextAssistantPanel.vue'
import KnowledgeCardView from '@/components/learning-ide/KnowledgeCardView.vue'
import LearningIdeTopBar from '@/components/learning-ide/LearningIdeTopBar.vue'
import LearningModuleLibrary from '@/components/learning-ide/LearningModuleLibrary.vue'
import LearningOutputDock from '@/components/learning-ide/LearningOutputDock.vue'
import LearningResourcePanel from '@/components/learning-ide/LearningResourcePanel.vue'
import LocalGraphPanel from '@/components/learning-ide/LocalGraphPanel.vue'
import WorkspaceTabs from '@/components/learning-ide/WorkspaceTabs.vue'
import PdfLearningCanvas from '@/components/translation/PdfLearningCanvas.vue'
import type {
  LearningAssistantMessage,
  LearningResourceExplorerView,
  LearningResourceTreeFolder,
  LearningResourceTreeItem,
  LearningSidePanelOption,
  LearningWorkspaceTab,
} from '@/types/learningIde'
import { showToast } from '@/utils/toast'
import {
  buildLearningModuleGroups,
  demoLearningIdeContext,
  resolveBacklinksForKnowledgeNode,
} from './learningIdeMock'
import {
  buildDocumentSelectionContext,
  buildDocumentParsePages,
  buildIntensiveReadingDocument,
  createTranslationWorkspaceDraftFromParsedDocument,
  loadTranslationWorkspaceDraft,
  saveTranslationWorkspaceDraft,
  type DocumentBlock,
  type DocumentOutlineItem,
  type DocumentSelectionContext,
  type IntensiveReadingDocument,
  type IntensiveAgentMode,
  type TranslationWorkspaceDraft,
} from './translationWorkspaceData'

type AgentMessageRole = 'user' | 'assistant'
type WorkspaceResizeTarget = 'outline' | 'agent'
type StudyNoteStatus = 'draft' | 'saved' | 'reviewing' | 'mastered'
type StudyNoteSource = 'manual' | 'agent'
type OutlineFilterScope = 'all' | 'current' | 'notes'
type WorkspaceSidePanel = 'outline' | 'bookmarks' | 'notes' | 'assets' | 'search'
type WorkspaceTabKind = 'pdf' | 'anchor-note' | 'standalone-note' | 'topic'
type AgentPanelMode = 'agent' | 'note-workbench' | 'note-assistant' | 'topic-organizer'

interface LocalAgentMessage {
  id: string
  role: AgentMessageRole
  content: string
  sourceContext?: DocumentSelectionContext | null
  citations?: TranslationSourceCitationDto[]
}

interface StudyNote {
  id: string
  documentId: string
  bookmarkId: string | null
  pageNumber: number
  blockId: string
  elementId: string
  bbox: string | null
  selectedText: string
  title: string
  content: string
  source: StudyNoteSource
  status: StudyNoteStatus
  tags: string[]
  createdAt: string
  updatedAt: string
}

interface StudyNoteComposerState {
  mode: 'idle' | 'create' | 'edit'
  noteId: string | null
  bookmarkId: string | null
  source: StudyNoteSource
  status: StudyNoteStatus
  title: string
  content: string
  context: DocumentSelectionContext | null
}

interface StudyAssetPipelineColumn {
  id: 'draft' | 'saved' | 'reviewing'
  label: string
  description: string
  tone: string
  notes: StudyNote[]
}

interface OutlineFilterScopeOption {
  id: OutlineFilterScope
  label: string
  count: number
}

interface WorkspaceSidePanelOption {
  id: WorkspaceSidePanel
  label: string
  icon: string
  count: number
}

interface WorkspaceTab {
  id: string
  kind: WorkspaceTabKind
  title: string
  subtitle?: string
  documentId?: string
  noteId?: string
  topicId?: string
  dirty?: boolean
}

type ProjectTreeFolderId =
  | 'sources'
  | 'notes'
  | 'anchor-notes'
  | 'assets'
  | 'review'
  | 'question-bank'
  | 'mistakes'
  | 'prompts'
  | 'pdf-outline'
  | 'file-outline'
  | 'file-bookmarks'
  | 'file-annotations'
  | 'file-references'
  | `file-outline-chapter-${string}`

type ProjectTreeResourceKind =
  | 'pdf'
  | 'outline'
  | 'page'
  | 'bookmark'
  | 'selection'
  | 'reference'
  | 'note'
  | 'anchor-note'
  | 'asset'
  | 'review'
  | 'question-bank'
  | 'mistake'
  | 'prompt'

interface ProjectTreeResource {
  id: string
  kind: ProjectTreeResourceKind
  title: string
  subtitle?: string
  noteId?: string
  tabId?: string
  outlineItemId?: string
  bookmarkId?: string
  pageNumber?: number
  count?: number
}

interface ProjectTreeFolder {
  id: ProjectTreeFolderId
  label: string
  badge: string
  resources: ProjectTreeResource[]
  emptyText: string
  children?: ProjectTreeFolder[]
}

interface DisplayOutlineItem extends DocumentOutlineItem {
  displayLevel: number
  hasChildren?: boolean
  syntheticRoot?: boolean
}

interface UserBookmark {
  id: string
  title: string
  pageNumber: number
  level: number
  elementId?: string | null
  bbox?: string | null
  source: 'user_bookmark'
  parentId?: string | null
  order: number
  createdAt: string
  updatedAt: string
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

interface PdfNoteAnchor {
  id: string
  pageNumber: number
  title: string
  excerpt?: string
  bbox?: string | null
  status?: string
  active?: boolean
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
const activeResourceExplorerView = ref<LearningResourceExplorerView>('project')
const activeSidePanel = ref<WorkspaceSidePanel>('outline')
const collapsedProjectTreeFolderIds = ref<Set<ProjectTreeFolderId>>(new Set())
const collapsedOutlineItemIds = ref<Set<string>>(new Set())
const activeOutlineItemId = ref<string | null>(null)
const outlineSearchQuery = ref('')
const outlineFilterScope = ref<OutlineFilterScope>('all')
const agentPrompt = ref('')
const agentAnswerLoading = ref(false)
const userBookmarks = ref<UserBookmark[]>([])
const studyNotes = ref<StudyNote[]>([])
const activeNoteId = ref<string | null>(null)
const noteComposer = ref<StudyNoteComposerState>(createEmptyNoteComposer())
const noteContentInputRef = ref<HTMLTextAreaElement | null>(null)
const noteAgentPrompt = ref('')
const noteAgentLoading = ref(false)
const aiCandidateContent = ref('')
const workspaceTabs = ref<WorkspaceTab[]>([])
const activeWorkspaceTabId = ref<string | null>(null)
const agentPanelMode = ref<AgentPanelMode>('agent')
const workspaceStateSaving = ref(false)
const moduleLibraryOpen = ref(false)
const activeLearningOutputId = ref(demoLearningIdeContext.outputs[0]?.id ?? '')
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

const noteStatusLabels: Record<StudyNoteStatus, string> = {
  draft: '待整理',
  saved: '已沉淀',
  reviewing: '复习中',
  mastered: '已掌握',
}

const outlineColumnMinWidth = 220
const outlineColumnMaxWidth = 440
const agentColumnMinWidth = 340
const agentColumnMaxWidth = 620
const centerColumnMinWidth = 560
const resizerColumnsWidth = 16
let workspaceStateRestoring = false
let workspaceStateSaveTimer: ReturnType<typeof setTimeout> | null = null
let workspaceStateSaveErrorShown = false

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

const userBookmarkOutlineItems = computed<DocumentOutlineItem[]>(() => {
  return userBookmarks.value.map((bookmark) => ({
    id: bookmark.id,
    title: bookmark.title,
    level: bookmark.level,
    pageNumber: bookmark.pageNumber,
    elementId: bookmark.elementId ?? null,
    bbox: bookmark.bbox ?? null,
    source: bookmark.source,
    confidence: null,
  }))
})

const displayOutlineItems = computed<DisplayOutlineItem[]>(() => {
  const document = readingDocument.value
  if (!document || (outlineItems.value.length === 0 && userBookmarkOutlineItems.value.length === 0)) return []

  const firstPage = Math.max(1, Math.min(...document.blocks.map((block) => block.pageNumber || 1)))
  const root: DisplayOutlineItem = {
    id: 'outline-document-root',
    title: document.title,
    level: 1,
    displayLevel: 1,
    pageNumber: firstPage,
    elementId: document.blocks[0]?.elementId ?? document.blocks[0]?.id ?? null,
    bbox: document.blocks[0]?.bbox ?? null,
    source: 'document_root',
    confidence: null,
    syntheticRoot: true,
  }

  const sourceItems = outlineItems.value.filter((item, index) => !isDuplicateDocumentRoot(item, document.title, index))
  let lastNumberedLevel = 2
  const children = sourceItems.map<DisplayOutlineItem>((item) => {
    const explicitLevel = inferDisplayOutlineLevel(item.title)
    const displayLevel = explicitLevel ?? Math.min(6, lastNumberedLevel + 1)
    if (explicitLevel) lastNumberedLevel = displayLevel
    return {
      ...item,
      level: displayLevel,
      displayLevel,
    }
  })

  const bookmarkChildren = userBookmarkOutlineItems.value.map<DisplayOutlineItem>((item) => ({
    ...item,
    level: 3,
    displayLevel: 3,
  }))
  const bookmarkRoot: DisplayOutlineItem[] = bookmarkChildren.length > 0
    ? [{
        id: 'outline-user-bookmark-root',
        title: '我的书签',
        level: 2,
        displayLevel: 2,
        pageNumber: bookmarkChildren[0]?.pageNumber ?? currentPdfPage.value,
        elementId: null,
        bbox: null,
        source: 'user_bookmark_root',
        confidence: null,
        syntheticRoot: true,
      }]
    : []

  return [root, ...children, ...bookmarkRoot, ...bookmarkChildren]
})

const outlineTreeItems = computed<DisplayOutlineItem[]>(() => {
  const items = displayOutlineItems.value
  return items.map((item, index) => ({
    ...item,
    hasChildren: items[index + 1]?.displayLevel > item.displayLevel,
  }))
})

const visibleOutlineItems = computed<DisplayOutlineItem[]>(() => {
  const query = normalizeOutlineQuery(outlineSearchQuery.value)
  const applyCollapse = outlineFilterScope.value === 'all' && !query
  if (!applyCollapse) return outlineTreeItems.value

  const collapsedAncestorLevels: number[] = []
  return outlineTreeItems.value.filter((item) => {
    while (collapsedAncestorLevels.length > 0 && collapsedAncestorLevels[collapsedAncestorLevels.length - 1] >= item.displayLevel) {
      collapsedAncestorLevels.pop()
    }

    const hiddenByAncestor = collapsedAncestorLevels.length > 0
    if (!hiddenByAncestor && item.hasChildren && isOutlineNodeCollapsed(item)) {
      collapsedAncestorLevels.push(item.displayLevel)
    }
    return !hiddenByAncestor
  })
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

const noteCountByElementId = computed(() => {
  const counts = new Map<string, number>()
  for (const note of studyNotes.value) {
    if (!note.elementId) continue
    counts.set(note.elementId, (counts.get(note.elementId) ?? 0) + 1)
  }
  return counts
})

const noteCountByBookmarkId = computed(() => {
  const counts = new Map<string, number>()
  for (const note of studyNotes.value) {
    if (!note.bookmarkId) continue
    counts.set(note.bookmarkId, (counts.get(note.bookmarkId) ?? 0) + 1)
  }
  return counts
})

const noteCountByPage = computed(() => {
  const counts = new Map<number, number>()
  for (const note of studyNotes.value) {
    counts.set(note.pageNumber, (counts.get(note.pageNumber) ?? 0) + 1)
  }
  return counts
})

const outlineFilterScopes = computed<OutlineFilterScopeOption[]>(() => {
  const itemSource = outlineTreeItems.value
  const total = itemSource.length || outlinePageItems.value.length
  const currentCount = itemSource.length
    ? itemSource.filter((item) => item.pageNumber === currentPdfPage.value).length
    : outlinePageItems.value.includes(currentPdfPage.value) ? 1 : 0
  const notesCount = itemSource.length
    ? itemSource.filter((item) => getOutlineItemNoteCount(item) > 0).length
    : outlinePageItems.value.filter((page) => getPageNoteCount(page) > 0).length
  return [
    { id: 'all', label: '全部', count: total },
    { id: 'current', label: '当前页', count: currentCount },
    { id: 'notes', label: '有笔记', count: notesCount },
  ]
})

const filteredOutlineItems = computed<DisplayOutlineItem[]>(() => {
  return visibleOutlineItems.value
    .filter((item) => matchesOutlineScope(item))
    .filter((item) => matchesOutlineSearch(item))
})

const filteredOutlinePageItems = computed<number[]>(() => {
  return outlinePageItems.value
    .filter((page) => matchesPageScope(page))
    .filter((page) => matchesPageSearch(page))
})

const activeUserBookmark = computed(() => {
  const activeId = activeOutlineItemId.value
  if (!activeId) return null
  return userBookmarks.value.find((bookmark) => bookmark.id === activeId) ?? null
})

const noteAnchors = computed<PdfNoteAnchor[]>(() => {
  return studyNotes.value.map((note) => ({
    id: note.id,
    pageNumber: note.pageNumber,
    title: note.title,
    excerpt: note.selectedText || note.content,
    bbox: note.bbox,
    status: note.status,
    active: note.id === activeNoteId.value,
  }))
})

const studyAssetPipeline = computed<StudyAssetPipelineColumn[]>(() => [
  {
    id: 'draft',
    label: '待整理',
    description: 'Agent 生成或还没确认的笔记草稿',
    tone: 'warm',
    notes: studyNotes.value.filter((note) => note.status === 'draft'),
  },
  {
    id: 'saved',
    label: '已沉淀',
    description: '用户主动保存并确认的学习笔记',
    tone: 'green',
    notes: studyNotes.value.filter((note) => note.status === 'saved' || note.status === 'mastered'),
  },
  {
    id: 'reviewing',
    label: '复习中',
    description: '用户手动加入复习的笔记卡',
    tone: 'blue',
    notes: studyNotes.value.filter((note) => note.status === 'reviewing'),
  },
])

const totalStudyNoteCount = computed(() => studyNotes.value.length)

const projectTreeSummary = computed(() => {
  const outlineTotal = outlineTreeItems.value.length || outlinePageItems.value.length
  return `${outlineTotal} 个定位 · ${studyNotes.value.length} 条笔记`
})

const projectTreeFolders = computed<ProjectTreeFolder[]>(() => {
  const document = readingDocument.value
  const anchorNotes = studyNotes.value.filter((note) => note.selectedText || note.bbox || note.bookmarkId)
  const standaloneNotes = studyNotes.value.filter((note) => !note.selectedText && !note.bbox && !note.bookmarkId)
  const reviewingNotes = studyNotes.value.filter((note) => note.status === 'reviewing')
  const assetResources = buildProjectTreeAssetResources()

  return [
    {
      id: 'sources',
      label: '资料',
      badge: document ? '1' : '0',
      resources: document
        ? [{
            id: `project-pdf-${document.id}`,
            kind: 'pdf',
            title: document.title,
            subtitle: `PDF · ${document.pageCount} 页`,
            tabId: `pdf-${document.id}`,
          }]
        : [],
      emptyText: '导入 PDF 建立学习项目',
    },
    {
      id: 'notes',
      label: '笔记',
      badge: String(standaloneNotes.length),
      resources: standaloneNotes.map((note) => buildProjectTreeNoteResource(note, 'note')),
      emptyText: '新建章节笔记',
    },
    {
      id: 'anchor-notes',
      label: '锚点笔记',
      badge: String(anchorNotes.length),
      resources: anchorNotes.map((note) => buildProjectTreeNoteResource(note, 'anchor-note')),
      emptyText: '从 PDF 选区创建锚点笔记',
    },
    {
      id: 'assets',
      label: '学习资产',
      badge: String(assetResources.length),
      resources: assetResources,
      emptyText: '让 Agent 整理当前段落',
    },
    {
      id: 'review',
      label: '复习队列',
      badge: String(reviewingNotes.length),
      resources: reviewingNotes.map((note) => buildProjectTreeNoteResource(note, 'review')),
      emptyText: '把笔记加入复习队列',
    },
    {
      id: 'question-bank',
      label: '题库',
      badge: '0',
      resources: [],
      emptyText: '新建专题后生成题目',
    },
    {
      id: 'mistakes',
      label: '错题本',
      badge: '0',
      resources: [],
      emptyText: '还没有错题记录',
    },
    {
      id: 'prompts',
      label: '提示词',
      badge: '0',
      resources: [],
      emptyText: '保存常用 Agent 提示词',
    },
  ]
})

const currentFileTitle = computed(() => readingDocument.value?.title ?? '未打开文件')

const currentFileSubtitle = computed(() => {
  const document = readingDocument.value
  if (!document) return 'PDF / Word / Markdown 的大纲会显示在这里'
  const sourceLabel = document.sourceLabel || (document.sourceType === 'pdf' ? 'PDF' : '学习资料')
  const pageSummary = document.pageCount ? `${document.pageCount} 页` : `${outlineTreeItems.value.length || outlinePageItems.value.length} 个定位`
  return `${sourceLabel} · ${pageSummary}`
})

const currentFileTreeFolders = computed<ProjectTreeFolder[]>(() => {
  const document = readingDocument.value
  const outlineResources = buildCurrentFileOutlineFolders()

  const bookmarkResources = userBookmarks.value.map<ProjectTreeResource>((bookmark) => ({
    id: `file-bookmark-${bookmark.id}`,
    kind: 'bookmark',
    title: bookmark.title,
    subtitle: `Page ${bookmark.pageNumber}`,
    bookmarkId: bookmark.id,
    pageNumber: bookmark.pageNumber,
  }))

  const annotationResources = studyNotes.value
    .filter((note) => note.selectedText || note.bbox || note.bookmarkId)
    .map<ProjectTreeResource>((note) => ({
      id: `file-annotation-${note.id}`,
      kind: 'selection',
      title: note.title,
      subtitle: `Page ${note.pageNumber} · ${noteStatusLabels[note.status]}`,
      noteId: note.id,
      pageNumber: note.pageNumber,
    }))

  const referenceResources = demoLearningIdeContext.activeKnowledgeCard.blockRefs.map<ProjectTreeResource>((blockRef) => ({
    id: `file-reference-${blockRef.id}`,
    kind: 'reference',
    title: trimResourceTitle(blockRef.excerpt || '块级引用'),
    subtitle: blockRef.pageNumber ? `Page ${blockRef.pageNumber}` : '知识卡引用',
    pageNumber: blockRef.pageNumber,
  }))

  return [
    {
      id: 'file-outline',
      label: document?.sourceType === 'pdf' ? '大纲' : '标题大纲',
      badge: String(outlineTreeItems.value.length || outlinePageItems.value.length),
      resources: outlineResources,
      children: buildChapterOutlineFolders(outlineTreeItems.value),
      emptyText: '当前文件还没有解析出目录',
    },
    {
      id: 'file-bookmarks',
      label: '书签',
      badge: String(bookmarkResources.length),
      resources: bookmarkResources,
      emptyText: '给当前页添加书签',
    },
    {
      id: 'file-annotations',
      label: '标注',
      badge: String(annotationResources.length),
      resources: annotationResources,
      emptyText: '从选区创建标注或笔记',
    },
    {
      id: 'file-references',
      label: '引用',
      badge: String(referenceResources.length),
      resources: referenceResources,
      emptyText: '当前文件暂无块级引用',
    },
  ]
})

function buildCurrentFileOutlineFolders(): ProjectTreeResource[] {
  if (outlineTreeItems.value.length > 0) return []
  return outlinePageItems.value.slice(0, 40).map<ProjectTreeResource>((page) => ({
    id: `file-page-${page}`,
    kind: 'page',
    title: `Page ${page}`,
    subtitle: '自动页面定位',
    pageNumber: page,
    count: getPageNoteCount(page) || undefined,
  }))
}

function buildChapterOutlineFolders(items: DisplayOutlineItem[]): ProjectTreeFolder[] {
  const outlineItemsForChapters = items
    .filter((item) => !item.syntheticRoot)
    .filter((item) => item.source !== 'user_bookmark' && item.source !== 'user_bookmark_root')
    .slice(0, 80)

  if (outlineItemsForChapters.length === 0) return []

  const chapterLevel = Math.min(...outlineItemsForChapters.map((item) => item.displayLevel))
  const chapterFolders: ProjectTreeFolder[] = []
  let activeChapter: ProjectTreeFolder | null = null

  for (const item of outlineItemsForChapters) {
    if (!activeChapter || item.displayLevel <= chapterLevel) {
      activeChapter = {
        id: `file-outline-chapter-${normalizeProjectTreeId(item.id) || normalizeProjectTreeId(item.title)}`,
        label: item.title,
        badge: '1',
        resources: [buildOutlineTreeResource(item, '章节首页')],
        emptyText: '当前章节没有下级目录',
      }
      chapterFolders.push(activeChapter)
      continue
    }

    activeChapter.resources.push(buildOutlineTreeResource(item))
    activeChapter.badge = String(activeChapter.resources.length)
  }

  return chapterFolders
}

function buildOutlineTreeResource(item: DisplayOutlineItem, title = item.title): ProjectTreeResource {
  return {
    id: `file-outline-${item.id}`,
    kind: 'outline',
    title,
    subtitle: `Page ${item.pageNumber || 1}`,
    outlineItemId: item.id,
    pageNumber: item.pageNumber || 1,
    count: getOutlineItemNoteCount(item) || undefined,
  }
}

const sidePanelOptions = computed<WorkspaceSidePanelOption[]>(() => [
  { id: 'outline', label: '目录', icon: '目', count: outlineTreeItems.value.length || outlinePageItems.value.length },
  { id: 'bookmarks', label: '书签', icon: '签', count: userBookmarks.value.length },
  { id: 'notes', label: '笔记', icon: '记', count: studyNotes.value.length },
  { id: 'assets', label: '资产', icon: '资', count: totalStudyNoteCount.value },
  { id: 'search', label: '搜索', icon: '搜', count: filteredOutlineItems.value.length || filteredOutlinePageItems.value.length },
])

const learningModuleGroups = computed(() => buildLearningModuleGroups(demoLearningIdeContext.moduleCatalog))

const learningSidePanelOptions = computed<LearningSidePanelOption[]>(() => {
  return sidePanelOptions.value.map((panel) => ({
    id: panel.id,
    label: panel.label,
    count: panel.count,
  }))
})

const collapsedProjectTreeFolderIdList = computed<string[]>(() => Array.from(collapsedProjectTreeFolderIds.value))

const learningProjectTreeFolders = computed<LearningResourceTreeFolder[]>(() => {
  return toLearningResourceTreeFolders(projectTreeFolders.value)
})

const learningCurrentFileTreeFolders = computed<LearningResourceTreeFolder[]>(() => {
  return toLearningResourceTreeFolders(currentFileTreeFolders.value)
})

function toLearningResourceTreeFolders(folders: ProjectTreeFolder[]): LearningResourceTreeFolder[] {
  return folders.map((folder) => ({
    id: folder.id,
    label: folder.label,
    badge: folder.badge,
    emptyText: folder.emptyText,
    resources: folder.resources.map((resource) => ({
      id: resource.id,
      kind: resource.kind,
      title: resource.title,
      subtitle: resource.subtitle,
      count: resource.count,
    })),
    children: folder.children ? toLearningResourceTreeFolders(folder.children) : undefined,
  }))
}

const learningWorkspaceTabs = computed<LearningWorkspaceTab[]>(() => {
  return workspaceTabs.value.map((tab) => ({
    id: tab.id,
    kind: tab.kind,
    title: tab.title,
    subtitle: tab.subtitle,
    dirty: tab.dirty,
  }))
})

const activeLearningKnowledgeCard = computed(() => demoLearningIdeContext.activeKnowledgeCard)

const activeLearningBacklinks = computed(() => {
  return resolveBacklinksForKnowledgeNode(demoLearningIdeContext, activeLearningKnowledgeCard.value.id)
})

const learningKnowledgeGraph = computed(() => demoLearningIdeContext.graph)

const learningOutputItems = computed(() => demoLearningIdeContext.outputs)

const learningAssistantMessages = computed<LearningAssistantMessage[]>(() => {
  return agentMessages.value.map((message) => ({
    id: message.id,
    role: message.role,
    content: message.content,
    citations: message.citations,
  }))
})

// Kept during the shell split so bookmark, outline, and note actions can be reattached to focused components next.
const retainedLearningIdeBindings = computed(() => ({
  outlineFilterScopes: outlineFilterScopes.value,
  projectTreeSummary: projectTreeSummary.value,
  studyAssetPipeline: studyAssetPipeline.value,
  actions: [
    isProjectTreeFolderCollapsed,
    getProjectTreeResourceIcon,
    jumpToUserBookmark,
    selectOutlineItem,
    isOutlineItemActive,
    toggleOutlineNode,
    createUserBookmark,
    renameActiveUserBookmark,
    deleteActiveUserBookmark,
    exportWorkspaceBookmarks,
    startNoteFromAgentMessage,
    editStudyNote,
    updateStudyNoteStatus,
    resolveNoteBookmarkLabel,
  ],
}))
void retainedLearningIdeBindings

const noteComposerContextLabel = computed(() => {
  const context = noteComposer.value.context
  if (!context) return '未选择来源'
  return `Page ${context.pageNumber} · ${context.elementId || context.blockId}`
})

const activeWorkspaceTab = computed(() => {
  return workspaceTabs.value.find((tab) => tab.id === activeWorkspaceTabId.value) ?? workspaceTabs.value[0] ?? null
})

const activeWorkspaceTabKind = computed<WorkspaceTabKind>(() => activeWorkspaceTab.value?.kind ?? 'pdf')

watch(readingDocument, (document) => {
  if (!document) {
    workspaceTabs.value = []
    activeWorkspaceTabId.value = null
    return
  }

  const pdfTabId = `pdf-${document.id}`
  const existingTabs = workspaceTabs.value.filter((tab) => tab.id !== pdfTabId)
  const pdfTab: WorkspaceTab = {
    id: pdfTabId,
    kind: 'pdf',
    title: document.title,
    subtitle: 'PDF',
    documentId: document.id,
  }
  workspaceTabs.value = [pdfTab, ...existingTabs]
  if (!activeWorkspaceTabId.value) activeWorkspaceTabId.value = pdfTabId

  if (document?.sourceType === 'pdf') {
    documentView.value = 'pdf-canvas'
  }
  if (workspaceStateRestoring) return
  syncDocumentDefaultPage()
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
  if (workspaceStateSaveTimer) {
    clearTimeout(workspaceStateSaveTimer)
    workspaceStateSaveTimer = null
    void persistWorkspaceState()
  }
})

async function goBackToHub() {
  await flushWorkspaceStateSave()
  void router.push('/app/translation')
}

async function completeLearningSession() {
  await flushWorkspaceStateSave()
  void router.push('/app/translation')
}

function activateWorkspaceTab(tabId: string) {
  const tab = workspaceTabs.value.find((item) => item.id === tabId)
  if (!tab) return
  activeWorkspaceTabId.value = tab.id
  if (tab.kind === 'pdf') {
    agentPanelMode.value = 'agent'
    documentView.value = 'pdf-canvas'
  } else if (tab.kind === 'anchor-note' || tab.kind === 'standalone-note') {
    agentPanelMode.value = 'note-assistant'
  } else {
    agentPanelMode.value = 'topic-organizer'
  }
}

function closeWorkspaceTab(tabId: string) {
  const nextTabs = workspaceTabs.value.filter((tab) => tab.id !== tabId)
  workspaceTabs.value = nextTabs
  if (activeWorkspaceTabId.value !== tabId) return
  activeWorkspaceTabId.value = nextTabs[0]?.id ?? null
  if (!activeWorkspaceTabId.value) agentPanelMode.value = 'agent'
}

function openStandaloneNoteTab() {
  const tabId = `standalone-note-${Date.now()}`
  workspaceTabs.value.push({
    id: tabId,
    kind: 'standalone-note',
    title: 'Untitled Note',
    subtitle: '独立笔记',
    dirty: true,
  })
  activateWorkspaceTab(tabId)
}

function openTopicTab(title = '排序算法') {
  const tabId = `topic-${Date.now()}`
  workspaceTabs.value.push({
    id: tabId,
    kind: 'topic',
    title,
    subtitle: '专题',
  })
  activateWorkspaceTab(tabId)
}

function openImportPdfEntry() {
  showToast('PDF 导入入口将在下一阶段接入当前上传流程', 'info')
}

function handleLearningCommandSearch(query: string) {
  const normalizedQuery = query.trim()
  if (!normalizedQuery) return
  outlineSearchQuery.value = normalizedQuery
  activeSidePanel.value = 'search'
  showToast(`已在当前学习项目中搜索：${normalizedQuery}`, 'info')
}

function handleAddLearningModule(moduleId: string) {
  moduleLibraryOpen.value = false
  const module = demoLearningIdeContext.moduleCatalog.find((item) => item.id === moduleId)
  showToast(`${module?.label ?? '学习工具'} 已加入当前工作台视图`, 'success')
}

function selectResourceExplorerView(view: LearningResourceExplorerView) {
  activeResourceExplorerView.value = view
  if (isOutlineCollapsed.value) {
    isOutlineCollapsed.value = false
  }
}

function selectLearningSidePanel(panelId: string) {
  activeResourceExplorerView.value = 'file'
  selectSidePanel(panelId as WorkspaceSidePanel)
}

function toggleLearningProjectTreeFolder(folderId: string) {
  toggleProjectTreeFolder(folderId as ProjectTreeFolderId)
}

function openLearningProjectTreeResource(resource: LearningResourceTreeItem) {
  const originalResource = collectProjectTreeResources([...projectTreeFolders.value, ...currentFileTreeFolders.value])
    .find((item) => item.id === resource.id)
  if (originalResource) {
    openProjectTreeResource(originalResource)
    return
  }
  openTopicTab(resource.title)
}

function handleLearningProjectTreeFolderEmptyAction(folder: LearningResourceTreeFolder) {
  const originalFolder = findProjectTreeFolderById([...projectTreeFolders.value, ...currentFileTreeFolders.value], folder.id)
  if (originalFolder) {
    handleProjectTreeFolderEmptyAction(originalFolder)
    return
  }
  openTopicTab(folder.label)
}

function collectProjectTreeResources(folders: ProjectTreeFolder[]): ProjectTreeResource[] {
  return folders.flatMap((folder) => [
    ...folder.resources,
    ...collectProjectTreeResources(folder.children ?? []),
  ])
}

function findProjectTreeFolderById(folders: ProjectTreeFolder[], folderId: string): ProjectTreeFolder | null {
  for (const folder of folders) {
    if (folder.id === folderId) return folder
    const matchedChild = findProjectTreeFolderById(folder.children ?? [], folderId)
    if (matchedChild) return matchedChild
  }
  return null
}

function openLearningBlockReference(blockRefId: string) {
  const blockRef = activeLearningKnowledgeCard.value.blockRefs.find((item) => item.id === blockRefId)
  if (!blockRef) return
  if (blockRef.pageNumber) {
    selectOutlinePage(blockRef.pageNumber)
  }
  pdfSourceHighlight.value = {
    pageNumber: blockRef.pageNumber ?? currentPdfPage.value,
    bbox: blockRef.bbox ?? null,
    label: activeLearningKnowledgeCard.value.title,
    text: blockRef.excerpt,
  }
}

function openLearningBacklinkSource(sourceId: string) {
  const backlink = activeLearningBacklinks.value.find((item) => item.sourceId === sourceId)
  if (!backlink) return
  if (backlink.blockRef.pageNumber) {
    selectOutlinePage(backlink.blockRef.pageNumber)
  }
  askAgent(`围绕 ${backlink.title} 解释它和 ${activeLearningKnowledgeCard.value.title} 的关系`)
}

function openLearningGraphNode(nodeId: string) {
  const node = learningKnowledgeGraph.value.nodes.find((item) => item.id === nodeId)
  if (!node) return
  if (node.type === 'knowledge-card') {
    showToast(`已聚焦知识卡：${node.label}`, 'info')
    return
  }
  askAgent(`解释知识图谱节点：${node.label}`)
}

function handleLearningAssistantQuickAction(prompt: string) {
  if (prompt === '加入知识卡') {
    showToast('当前选区已关联到知识卡视图', 'success')
    return
  }
  if (prompt === '整理笔记') {
    startNoteFromActiveBlock()
    return
  }
  askAgent(prompt)
}

function buildProjectTreeNoteResource(note: StudyNote, kind: Extract<ProjectTreeResourceKind, 'note' | 'anchor-note' | 'review'>): ProjectTreeResource {
  return {
    id: `project-${kind}-${note.id}`,
    kind,
    title: note.title,
    subtitle: `Page ${note.pageNumber} · ${noteStatusLabels[note.status]}`,
    noteId: note.id,
    count: note.tags.length || undefined,
  }
}

function buildProjectTreeAssetResources(): ProjectTreeResource[] {
  const assets = new Map<string, ProjectTreeResource>()

  for (const note of studyNotes.value) {
    for (const tag of note.tags) {
      const key = tag.trim()
      if (!key) continue
      const existing = assets.get(key)
      assets.set(key, {
        id: `project-asset-${normalizeProjectTreeId(key)}`,
        kind: 'asset',
        title: key,
        subtitle: existing ? '多条笔记关联' : `来自 ${note.title}`,
        count: (existing?.count ?? 0) + 1,
      })
    }
  }

  if (assets.size === 0 && activeInsight.value) {
    for (const candidate of [
      ...activeInsight.value.phrases,
      ...activeInsight.value.vocabulary,
      ...activeInsight.value.grammarPoints,
    ].slice(0, 6)) {
      const title = candidate.text.trim()
      if (!title) continue
      assets.set(title, {
        id: `project-asset-candidate-${normalizeProjectTreeId(title)}`,
        kind: 'asset',
        title,
        subtitle: '当前段落候选',
      })
    }
  }

  return Array.from(assets.values()).slice(0, 12)
}

function openProjectTreeResource(resource: ProjectTreeResource) {
  if (resource.kind === 'pdf') {
    const pdfTabId = resource.tabId ?? (readingDocument.value ? `pdf-${readingDocument.value.id}` : null)
    if (pdfTabId) activateWorkspaceTab(pdfTabId)
    documentView.value = 'pdf-canvas'
    agentPanelMode.value = 'agent'
    return
  }

  if (resource.kind === 'outline' && resource.outlineItemId) {
    const outlineItem = findDisplayOutlineItemById(resource.outlineItemId)
    if (outlineItem) {
      selectOutlineItem(outlineItem)
      documentView.value = 'pdf-canvas'
      return
    }
  }

  if ((resource.kind === 'page' || resource.kind === 'reference') && resource.pageNumber) {
    selectOutlinePage(resource.pageNumber)
    documentView.value = 'pdf-canvas'
    return
  }

  if (resource.kind === 'bookmark' && resource.bookmarkId) {
    const bookmark = userBookmarks.value.find((item) => item.id === resource.bookmarkId)
    if (bookmark) {
      jumpToUserBookmark(bookmark)
      return
    }
  }

  if (resource.kind === 'selection' && resource.noteId) {
    const note = studyNotes.value.find((item) => item.id === resource.noteId)
    if (note) {
      jumpToStudyNote(note)
      return
    }
  }

  if ((resource.kind === 'anchor-note' || resource.kind === 'note' || resource.kind === 'review') && resource.noteId) {
    openStudyNote(resource.noteId)
    return
  }

  if (resource.kind === 'asset') {
    openTopicTab(resource.title)
    return
  }

  if (resource.kind === 'question-bank') {
    openTopicTab('题库')
    return
  }

  if (resource.kind === 'mistake') {
    openTopicTab('错题本')
    return
  }

  if (resource.kind === 'prompt') {
    openTopicTab('提示词')
  }
}

function handleProjectTreeFolderEmptyAction(folder: ProjectTreeFolder) {
  if (folder.id === 'sources') {
    openImportPdfEntry()
    return
  }
  if (folder.id === 'notes') {
    openStandaloneNoteTab()
    return
  }
  if (folder.id === 'anchor-notes') {
    startNoteFromActiveBlock()
    return
  }
  if (folder.id === 'assets') {
    askAgent('整理当前段落为笔记草稿')
    return
  }
  if (folder.id === 'file-outline') {
    activeSidePanel.value = 'outline'
    return
  }
  if (folder.id === 'file-bookmarks') {
    createUserBookmark()
    return
  }
  if (folder.id === 'file-annotations') {
    startNoteFromActiveBlock()
    return
  }
  if (folder.id === 'file-references') {
    openTopicTab('块级引用')
    return
  }
  openTopicTab(folder.label)
}

function isProjectTreeFolderCollapsed(folderId: ProjectTreeFolderId) {
  return collapsedProjectTreeFolderIds.value.has(folderId)
}

function toggleProjectTreeFolder(folderId: ProjectTreeFolderId) {
  const nextCollapsedIds = new Set(collapsedProjectTreeFolderIds.value)
  if (nextCollapsedIds.has(folderId)) {
    nextCollapsedIds.delete(folderId)
  } else {
    nextCollapsedIds.add(folderId)
  }
  collapsedProjectTreeFolderIds.value = nextCollapsedIds
}

function getProjectTreeResourceIcon(kind: ProjectTreeResourceKind) {
  const icons: Record<ProjectTreeResourceKind, string> = {
    pdf: 'PDF',
    outline: '目',
    page: 'P',
    bookmark: '签',
    selection: '标',
    reference: '引',
    note: 'MD',
    'anchor-note': '锚',
    asset: '资',
    review: '复',
    'question-bank': '题',
    mistake: '错',
    prompt: '提',
  }
  return icons[kind]
}

function normalizeProjectTreeId(value: string) {
  return value.trim().toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9\u4e00-\u9fa5-]/g, '')
}

function trimResourceTitle(value: string, maxLength = 28) {
  const normalized = value.replace(/\s+/g, ' ').trim()
  if (normalized.length <= maxLength) return normalized
  return `${normalized.slice(0, maxLength)}...`
}

async function flushWorkspaceStateSave() {
  if (workspaceStateSaveTimer) {
    clearTimeout(workspaceStateSaveTimer)
    workspaceStateSaveTimer = null
  }
  await persistWorkspaceState()
}

async function restoreWorkspaceDocument(id: string, silent = false) {
  workspaceStateRestoring = true
  if (!silent) {
    readingDocument.value = null
    activeBlockId.value = ''
    activeOutlineItemId.value = null
    workspaceLoadError.value = ''
    studyNotes.value = []
    userBookmarks.value = []
    activeNoteId.value = null
    targetPdfPage.value = 1
    currentPdfPage.value = 1
    collapsedOutlineItemIds.value = new Set()
    noteComposer.value = createEmptyNoteComposer()
  }
  if (!id) {
    workspaceLoadError.value = '缺少翻译 ID。'
    workspaceStateRestoring = false
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
    const restoredDraft = applyLocalDraftDisplayOverrides(draft, localDraft)
    readingDocument.value = buildIntensiveReadingDocument(restoredDraft)
    if (typeof window !== 'undefined') {
      saveTranslationWorkspaceDraft(window.localStorage, restoredDraft)
    }
    if (!silent) {
      restoreWorkspaceState(persisted.workspaceState ?? null)
      focusRouteStudyNote()
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
      restoreWorkspaceState(localDraft.workspaceState ?? null)
      focusRouteStudyNote()
      workspaceLoadError.value = ''
      scheduleBackgroundParseRefresh(id, localDraft.ocrStatus)
      return
    }
    workspaceLoadError.value = '后端知识快照不存在，且没有可兼容恢复的本地草稿。'
  } finally {
    workspaceStateRestoring = false
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

function syncDocumentDefaultPage() {
  targetPdfPage.value = activeBlock.value?.pageNumber || 1
  currentPdfPage.value = targetPdfPage.value
}

function syncActiveBlockToPdfPage(page: number) {
  const document = readingDocument.value
  if (!document) return
  const pageBlock = document.blocks.find((block) => (block.pageNumber || 1) === page)
  if (pageBlock) {
    activeBlockId.value = pageBlock.id
  }
}

function applyLocalDraftDisplayOverrides(
  draft: TranslationWorkspaceDraft,
  localDraft: TranslationWorkspaceDraft | null,
): TranslationWorkspaceDraft {
  if (!localDraft?.title) return draft
  return {
    ...draft,
    title: localDraft.title,
  }
}

function focusRouteStudyNote() {
  const noteId = parseRouteNoteId(route.query.noteId)
  if (!noteId) return
  openStudyNote(noteId)
}

function parseRouteNoteId(value: unknown): string | null {
  if (typeof value === 'string') return value.trim() || null
  if (Array.isArray(value)) {
    const first = value.find((item): item is string => typeof item === 'string' && item.trim().length > 0)
    return first?.trim() ?? null
  }
  return null
}

function restoreWorkspaceState(state: TranslationDocumentWorkspaceStateDto | null) {
  if (!state) return
  userBookmarks.value = normalizeWorkspaceBookmarks(state.userBookmarks ?? [])
  studyNotes.value = normalizeWorkspaceNotes(state.studyNotes ?? [])
  collapsedOutlineItemIds.value = new Set((state.collapsedOutlineItemIds ?? []).filter(Boolean))
  activeOutlineItemId.value = state.activeOutlineItemId ?? null
  activeNoteId.value = state.activeNoteId ?? null

  const document = readingDocument.value
  if (document && state.activeBlockId) {
    const restoredBlock = document.blocks.find((block) => block.id === state.activeBlockId || block.elementId === state.activeBlockId)
    if (restoredBlock) {
      activeBlockId.value = restoredBlock.id
    }
  }

  const restoredPage = normalizePageNumber(state.currentPage ?? currentPdfPage.value)
  if (document) {
    const restoredBlock = document.blocks.find((block) => block.id === activeBlockId.value)
    if (!restoredBlock || (restoredBlock.pageNumber || 1) !== restoredPage) {
      syncActiveBlockToPdfPage(restoredPage)
    }
  }
  currentPdfPage.value = restoredPage
  targetPdfPage.value = restoredPage
}

function scheduleWorkspaceStateSave() {
  if (workspaceStateRestoring || !readingDocument.value) return
  if (workspaceStateSaveTimer) {
    clearTimeout(workspaceStateSaveTimer)
  }
  workspaceStateSaveTimer = setTimeout(() => {
    workspaceStateSaveTimer = null
    void persistWorkspaceState()
  }, 450)
}

async function persistWorkspaceState() {
  const document = readingDocument.value
  if (!document || workspaceStateRestoring) return
  workspaceStateSaving.value = true
  try {
    await saveTranslationDocumentWorkspaceState(document.id, buildWorkspaceStatePayload())
    workspaceStateSaveErrorShown = false
  } catch (error) {
    console.warn('[TranslationWorkspace] save workspace state failed', error)
    if (!workspaceStateSaveErrorShown) {
      workspaceStateSaveErrorShown = true
      showToast('学习状态暂时未同步，请稍后重试', 'error')
    }
  } finally {
    workspaceStateSaving.value = false
  }
}

function buildWorkspaceStatePayload(): TranslationDocumentWorkspaceStateDto {
  const now = new Date().toISOString()
  return {
    userBookmarks: userBookmarks.value.map(toWorkspaceBookmarkDto),
    studyNotes: studyNotes.value.map(toWorkspaceStudyNoteDto),
    collapsedOutlineItemIds: Array.from(collapsedOutlineItemIds.value),
    currentPage: currentPdfPage.value,
    activeBlockId: activeBlockId.value || null,
    activeOutlineItemId: activeOutlineItemId.value,
    activeNoteId: activeNoteId.value,
    updatedAt: now,
  }
}

function normalizeWorkspaceBookmarks(bookmarks: TranslationDocumentUserBookmarkDto[]): UserBookmark[] {
  return bookmarks
    .filter((bookmark) => bookmark && bookmark.id && bookmark.title)
    .map((bookmark, index) => ({
      id: bookmark.id,
      title: bookmark.title,
      pageNumber: normalizePageNumber(bookmark.pageNumber),
      level: normalizeBookmarkLevel(bookmark.level),
      elementId: bookmark.elementId ?? null,
      bbox: bookmark.bbox ?? null,
      source: 'user_bookmark' as const,
      parentId: bookmark.parentId ?? null,
      order: bookmark.order ?? index + 1,
      createdAt: bookmark.createdAt ?? new Date().toISOString(),
      updatedAt: bookmark.updatedAt ?? bookmark.createdAt ?? new Date().toISOString(),
    }))
}

function normalizeWorkspaceNotes(notes: TranslationDocumentStudyNoteDto[]): StudyNote[] {
  const document = readingDocument.value
  return notes
    .filter((note) => note && note.id && note.title)
    .map((note) => {
      const pageNumber = normalizePageNumber(note.pageNumber)
      const fallbackBlock = document?.blocks.find((block) => block.id === note.blockId || block.elementId === note.elementId)
        ?? document?.blocks.find((block) => (block.pageNumber || 1) === pageNumber)
        ?? document?.blocks[0]
      return {
        id: note.id,
        documentId: note.documentId || document?.id || '',
        bookmarkId: note.bookmarkId ?? null,
        pageNumber,
        blockId: note.blockId || fallbackBlock?.id || 'selection',
        elementId: note.elementId || fallbackBlock?.elementId || fallbackBlock?.id || 'selection',
        bbox: note.bbox ?? fallbackBlock?.bbox ?? null,
        selectedText: note.selectedText ?? '',
        title: note.title,
        content: note.content ?? '',
        source: normalizeStudyNoteSource(note.source),
        status: normalizeStudyNoteStatus(note.status),
        tags: Array.isArray(note.tags) ? note.tags : [],
        createdAt: note.createdAt ?? new Date().toISOString(),
        updatedAt: note.updatedAt ?? note.createdAt ?? new Date().toISOString(),
      }
    })
}

function toWorkspaceBookmarkDto(bookmark: UserBookmark): TranslationDocumentUserBookmarkDto {
  return {
    id: bookmark.id,
    title: bookmark.title,
    pageNumber: bookmark.pageNumber,
    level: bookmark.level,
    elementId: bookmark.elementId ?? null,
    bbox: bookmark.bbox ?? null,
    source: bookmark.source,
    parentId: bookmark.parentId ?? null,
    order: bookmark.order,
    createdAt: bookmark.createdAt,
    updatedAt: bookmark.updatedAt,
  }
}

function toWorkspaceStudyNoteDto(note: StudyNote): TranslationDocumentStudyNoteDto {
  return {
    id: note.id,
    documentId: note.documentId,
    bookmarkId: note.bookmarkId,
    pageNumber: note.pageNumber,
    blockId: note.blockId,
    elementId: note.elementId,
    bbox: note.bbox,
    selectedText: note.selectedText,
    title: note.title,
    content: note.content,
    source: note.source,
    status: note.status,
    tags: note.tags,
    createdAt: note.createdAt,
    updatedAt: note.updatedAt,
  }
}

function normalizeStudyNoteStatus(status: string | undefined): StudyNoteStatus {
  if (status === 'draft' || status === 'saved' || status === 'reviewing' || status === 'mastered') {
    return status
  }
  return 'saved'
}

function normalizeStudyNoteSource(source: string | undefined): StudyNoteSource {
  return source === 'agent' ? 'agent' : 'manual'
}

function normalizePageNumber(pageNumber: number | null | undefined) {
  const value = Number(pageNumber)
  return Number.isFinite(value) ? Math.max(1, Math.floor(value)) : 1
}

function normalizeBookmarkLevel(level: number | null | undefined) {
  const value = Number(level)
  return Number.isFinite(value) ? Math.max(1, Math.min(6, Math.floor(value))) : 3
}

function selectBlock(blockId: string) {
  activeBlockId.value = blockId
  activeOutlineItemId.value = null
  clearPdfSelection()
  scheduleWorkspaceStateSave()
}

function selectOutlinePage(page: number) {
  currentPdfPage.value = page
  targetPdfPage.value = page
  activeOutlineItemId.value = null
  clearPdfSelection()
  const firstBlock = readingDocument.value?.blocks.find((block) => (block.pageNumber || 1) === page)
  if (firstBlock) activeBlockId.value = firstBlock.id
  scheduleWorkspaceStateSave()
}

function selectSidePanel(panel: WorkspaceSidePanel) {
  activeSidePanel.value = panel
  if (isOutlineCollapsed.value) {
    isOutlineCollapsed.value = false
  }
}

function jumpToUserBookmark(bookmark: UserBookmark) {
  activeOutlineItemId.value = bookmark.id
  targetPdfPage.value = bookmark.pageNumber
  currentPdfPage.value = bookmark.pageNumber
  documentView.value = 'pdf-canvas'
  if (bookmark.elementId) {
    const block = readingDocument.value?.blocks.find((item) => item.id === bookmark.elementId || item.elementId === bookmark.elementId)
    if (block) activeBlockId.value = block.id
  } else {
    syncActiveBlockToPdfPage(bookmark.pageNumber)
  }
  clearPdfSelection()
  scheduleWorkspaceStateSave()
}

function selectOutlineItem(item: DocumentOutlineItem) {
  const page = item.pageNumber || 1
  const document = readingDocument.value
  activeOutlineItemId.value = item.source === 'user_bookmark_root' ? null : item.id
  if ((item as DisplayOutlineItem).syntheticRoot) {
    selectOutlinePage(page)
    return
  }
  const targetBlock = document?.blocks.find((block) => {
    return block.id === item.elementId || block.elementId === item.elementId
  }) ?? document?.blocks.find((block) => (block.pageNumber || 1) === page)
  if (targetBlock) {
    activeBlockId.value = targetBlock.id
  }
  targetPdfPage.value = page
  currentPdfPage.value = page
  clearPdfSelection()
  scheduleWorkspaceStateSave()
}

function selectOutlineBlock(blockId: string, page: number) {
  activeBlockId.value = blockId
  activeOutlineItemId.value = null
  targetPdfPage.value = page
  currentPdfPage.value = page
  clearPdfSelection()
  scheduleWorkspaceStateSave()
}

function handlePdfPageChange(page: number) {
  currentPdfPage.value = normalizePageNumber(page)
  syncActiveBlockToPdfPage(page)
  scheduleWorkspaceStateSave()
}

function isOutlineItemActive(item: DocumentOutlineItem) {
  return item.id === activeOutlineItemId.value
    || item.elementId === activeBlock.value?.elementId
    || item.elementId === activeBlockId.value
    || (!item.elementId && item.pageNumber === currentPdfPage.value)
}

function getOutlineItemNoteCount(item: DocumentOutlineItem) {
  if (item.source === 'user_bookmark_root') {
    return studyNotes.value.filter((note) => !!note.bookmarkId).length
  }
  if (item.source === 'user_bookmark') {
    return noteCountByBookmarkId.value.get(item.id) ?? 0
  }
  if (item.elementId) {
    return noteCountByElementId.value.get(item.elementId) ?? 0
  }
  return getPageNoteCount(item.pageNumber)
}

function getPageNoteCount(page: number) {
  return noteCountByPage.value.get(page) ?? 0
}

function isOutlineNodeCollapsed(item: DisplayOutlineItem) {
  return collapsedOutlineItemIds.value.has(item.id)
}

function toggleOutlineNode(item: DisplayOutlineItem) {
  if (!item.hasChildren) return
  const nextCollapsedIds = new Set(collapsedOutlineItemIds.value)
  if (nextCollapsedIds.has(item.id)) {
    nextCollapsedIds.delete(item.id)
  } else {
    nextCollapsedIds.add(item.id)
  }
  collapsedOutlineItemIds.value = nextCollapsedIds
  scheduleWorkspaceStateSave()
}

function createUserBookmark() {
  const document = readingDocument.value
  if (!document) return
  const context = resolveAgentSourceContext()
  if (!context) {
    showToast('请先定位到 PDF 页或段落', 'info')
    return
  }
  const now = new Date().toISOString()
  const bookmark: UserBookmark = {
    id: `user-bookmark-${Date.now()}`,
    title: buildDefaultBookmarkTitle(context.text),
    pageNumber: context.pageNumber,
    level: 3,
    elementId: context.elementId,
    bbox: context.bbox,
    source: 'user_bookmark',
    parentId: activeOutlineItemId.value,
    order: userBookmarks.value.length + 1,
    createdAt: now,
    updatedAt: now,
  }
  userBookmarks.value = [...userBookmarks.value, bookmark]
  activeOutlineItemId.value = bookmark.id
  targetPdfPage.value = bookmark.pageNumber
  currentPdfPage.value = bookmark.pageNumber
  showToast('已添加到我的书签', 'success')
  scheduleWorkspaceStateSave()
}

function renameActiveUserBookmark() {
  const bookmark = activeUserBookmark.value
  if (!bookmark || typeof window === 'undefined') return
  const nextTitle = window.prompt('重命名书签', bookmark.title)?.trim()
  if (!nextTitle || nextTitle === bookmark.title) return
  const now = new Date().toISOString()
  userBookmarks.value = userBookmarks.value.map((item) => item.id === bookmark.id
    ? { ...item, title: nextTitle, updatedAt: now }
    : item)
  showToast('书签已重命名', 'success')
  scheduleWorkspaceStateSave()
}

function deleteActiveUserBookmark() {
  const bookmark = activeUserBookmark.value
  if (!bookmark) return
  if (typeof window !== 'undefined' && !window.confirm(`删除书签「${bookmark.title}」？笔记会保留。`)) {
    return
  }
  userBookmarks.value = userBookmarks.value.filter((item) => item.id !== bookmark.id)
  studyNotes.value = studyNotes.value.map((note) => note.bookmarkId === bookmark.id
    ? { ...note, bookmarkId: null, updatedAt: new Date().toISOString() }
    : note)
  activeOutlineItemId.value = null
  showToast('书签已删除，相关笔记已保留', 'success')
  scheduleWorkspaceStateSave()
}

async function exportWorkspaceBookmarks() {
  const document = readingDocument.value
  if (!document) return
  try {
    await persistWorkspaceState()
    const { blob, fileName } = await downloadTranslationDocumentWithBookmarks(document.id)
    downloadBlob(blob, fileName)
    showToast('已导出带书签 PDF', 'success')
  } catch (error) {
    console.warn('[TranslationWorkspace] export bookmarked PDF failed', error)
    showToast('导出带书签 PDF 失败', 'error')
  }
}

function downloadBlob(blob: Blob, fileName: string) {
  if (typeof window === 'undefined' || typeof document === 'undefined') return
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  URL.revokeObjectURL(url)
}

function buildDefaultBookmarkTitle(text: string) {
  const currentOutline = activeOutlineItemId.value ? findDisplayOutlineItemById(activeOutlineItemId.value) : null
  if (currentOutline && currentOutline.source !== 'user_bookmark_root') {
    return currentOutline.title.length > 28 ? `${currentOutline.title.slice(0, 28)}...` : currentOutline.title
  }
  return buildDefaultNoteTitle(text)
}

function findDisplayOutlineItemById(id: string) {
  return outlineTreeItems.value.find((item) => item.id === id) ?? null
}

function matchesOutlineScope(item: DocumentOutlineItem) {
  if (outlineFilterScope.value === 'current') return item.pageNumber === currentPdfPage.value
  if (outlineFilterScope.value === 'notes') return getOutlineItemNoteCount(item) > 0
  return true
}

function matchesPageScope(page: number) {
  if (outlineFilterScope.value === 'current') return page === currentPdfPage.value
  if (outlineFilterScope.value === 'notes') return getPageNoteCount(page) > 0
  return true
}

function matchesOutlineSearch(item: DocumentOutlineItem) {
  const query = normalizeOutlineQuery(outlineSearchQuery.value)
  if (!query) return true
  const noteCount = getOutlineItemNoteCount(item)
  return normalizeOutlineQuery(item.title).includes(query)
    || String(item.pageNumber).includes(query)
    || (noteCount > 0 && ('笔记'.includes(query) || 'note'.includes(query)))
    || (item.pageNumber === currentPdfPage.value && '当前页'.includes(query))
}

function matchesPageSearch(page: number) {
  const query = normalizeOutlineQuery(outlineSearchQuery.value)
  if (!query) return true
  const noteCount = getPageNoteCount(page)
  return String(page).includes(query)
    || (noteCount > 0 && ('笔记'.includes(query) || 'note'.includes(query)))
    || (page === currentPdfPage.value && '当前页'.includes(query))
}

function normalizeOutlineQuery(value: string) {
  return value.trim().toLowerCase()
}

function isDuplicateDocumentRoot(item: DocumentOutlineItem, documentTitle: string, index: number) {
  if (index > 2) return false
  const itemTitle = normalizeOutlineTitleForCompare(item.title)
  const currentTitle = normalizeOutlineTitleForCompare(documentTitle)
  if (!itemTitle || !currentTitle) return false
  const looksLikeParsedCoverTitle = index === 0
    && item.pageNumber <= 2
    && item.level <= 1
    && !inferDisplayOutlineLevel(item.title)
  return item.pageNumber <= 2
    && (looksLikeParsedCoverTitle || itemTitle === currentTitle || currentTitle.includes(itemTitle) || itemTitle.includes(currentTitle))
}

function inferDisplayOutlineLevel(title: string): number | null {
  const text = title.trim()
  if (!text) return null
  if (/^第[一二三四五六七八九十百千万0-9]+[章节篇部]/.test(text)) return 2
  if (/^chapter\s+\d+/i.test(text) || /^unit\s+\d+/i.test(text)) return 2

  const sectionMatch = text.match(/^§?\s*(\d+(?:\.\d+){0,5})/)
  if (sectionMatch?.[1]) {
    const depth = sectionMatch[1].split('.').filter(Boolean).length
    return Math.max(2, Math.min(6, depth + 1))
  }

  if (/^[□■▪●·•-]\s*\S+/.test(text)) return null
  return null
}

function normalizeOutlineTitleForCompare(value: string) {
  return value
    .replace(/\.[a-z0-9]+$/i, '')
    .replace(/[()\[\]（）【】_\-+\s·.]/g, '')
    .toLowerCase()
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
    const answer = await requestAgentAnswerForContext(selectedQuestion, sourceContext, currentBlock)
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

async function askAgentToAppendNote(question: string) {
  const selectedQuestion = question.trim()
  const composer = noteComposer.value
  const sourceContext = composer.context ?? resolveAgentSourceContext()
  const currentBlock = activeBlock.value
  if (composer.mode === 'idle') {
    showToast('请先打开一个笔记', 'info')
    return
  }
  if (!selectedQuestion || !sourceContext || !currentBlock || noteAgentLoading.value) return

  noteAgentLoading.value = true
  agentMessages.value.push({
    id: `user-note-${Date.now()}`,
    role: 'user',
    content: selectedQuestion,
    sourceContext,
  })
  try {
    const answer = await requestAgentAnswerForContext(selectedQuestion, sourceContext, currentBlock)
    aiCandidateContent.value = answer.answer.trim()
    agentMessages.value.push({
      id: `assistant-note-${Date.now()}`,
      role: 'assistant',
      content: answer.answer,
      sourceContext,
      citations: answer.citations,
    })
    noteAgentPrompt.value = ''
    showToast('Agent 已生成候选补充', 'success')
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Agent 回答失败'
    agentMessages.value.push({
      id: `assistant-note-error-${Date.now()}`,
      role: 'assistant',
      content: `暂时无法补充笔记：${message}`,
      sourceContext,
    })
    showToast('Agent 补充失败，请稍后重试', 'error')
  } finally {
    noteAgentLoading.value = false
  }
}

function appendAiCandidateToNote() {
  appendAgentAnswerToNoteComposer(aiCandidateContent.value)
  aiCandidateContent.value = ''
}

async function requestAgentAnswerForContext(
  question: string,
  sourceContext: DocumentSelectionContext | null,
  fallbackBlock: DocumentBlock,
) {
  const document = readingDocument.value
  if (!document) throw new Error('缺少当前文档')
  return answerTranslationDocumentQuestion(document.id, {
    question,
    selectedText: sourceContext?.text ?? fallbackBlock.text,
    pageNumber: sourceContext?.pageNumber ?? fallbackBlock.pageNumber,
    elementId: sourceContext?.elementId ?? fallbackBlock.elementId ?? fallbackBlock.id,
    bbox: sourceContext?.bbox ?? fallbackBlock.bbox ?? null,
    mode: activeMode.value,
  })
}

function appendAgentAnswerToNoteComposer(content: string) {
  const normalizedContent = content.trim()
  if (!normalizedContent) return
  if (noteComposer.value.mode === 'idle') {
    showToast('请先打开一个笔记', 'info')
    return
  }
  const currentContent = noteComposer.value.content.trimEnd()
  noteComposer.value.content = `${currentContent}${currentContent ? '\n\n' : ''}Agent 补充：\n${normalizedContent}`
  focusNoteComposer()
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
  scheduleWorkspaceStateSave()
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

function createEmptyNoteComposer(): StudyNoteComposerState {
  return {
    mode: 'idle',
    noteId: null,
    bookmarkId: null,
    source: 'manual',
    status: 'saved',
    title: '',
    content: '',
    context: null,
  }
}

function startNoteFromPdfSelection(payload: PdfSelectionPayload) {
  agentPanelMode.value = 'note-workbench'
  isAgentCollapsed.value = false
  const context = resolveNoteContextFromPdfSelection(payload)
  openNoteComposer({
    context,
    title: buildDefaultNoteTitle(payload.text),
    content: '',
    source: 'manual',
    status: 'saved',
  })
}

function startNoteFromActiveBlock() {
  const context = resolveAgentSourceContext()
  if (!context) {
    showToast('请先选择一段内容', 'info')
    return
  }
  openNoteComposer({
    context,
    title: buildDefaultNoteTitle(context.text),
    content: '',
    source: 'manual',
    status: 'saved',
  })
}

function startNoteFromAgentMessage(message: LocalAgentMessage) {
  const context = message.sourceContext ?? resolveAgentSourceContext()
  if (!context) {
    showToast('缺少笔记来源位置', 'info')
    return
  }
  openNoteComposer({
    context,
    title: buildDefaultNoteTitle(message.content),
    content: message.content,
    source: 'agent',
    status: 'draft',
  })
}

function openNoteComposer(input: {
  context: DocumentSelectionContext
  title: string
  content: string
  source: StudyNoteSource
  status: StudyNoteStatus
}) {
  activeNoteId.value = null
  activeSidePanel.value = 'notes'
  isAgentCollapsed.value = false
  agentPanelMode.value = 'note-workbench'
  if (isOutlineCollapsed.value) {
    isOutlineCollapsed.value = false
  }
  const bookmarkId = resolveActiveBookmarkId(input.context)
  noteAgentPrompt.value = ''
  aiCandidateContent.value = ''
  noteComposer.value = {
    mode: 'create',
    noteId: null,
    bookmarkId,
    source: input.source,
    status: input.status,
    title: input.title,
    content: input.content,
    context: input.context,
  }
  documentView.value = 'pdf-canvas'
  focusNoteComposer()
}

function editStudyNote(note: StudyNote) {
  activeNoteId.value = note.id
  noteAgentPrompt.value = ''
  aiCandidateContent.value = ''
  agentPanelMode.value = 'note-workbench'
  isAgentCollapsed.value = false
  noteComposer.value = {
    mode: 'edit',
    noteId: note.id,
    bookmarkId: note.bookmarkId,
    source: note.source,
    status: note.status,
    title: note.title,
    content: note.content,
    context: {
      documentId: note.documentId,
      pageNumber: note.pageNumber,
      blockId: note.blockId,
      elementId: note.elementId,
      bbox: note.bbox,
      text: note.selectedText,
    },
  }
  focusNoteComposer()
}

function focusNoteComposer() {
  void nextTick(() => {
    noteContentInputRef.value?.focus()
  })
}

function resolveActiveBookmarkId(context: DocumentSelectionContext): string | null {
  const activeItem = activeOutlineItemId.value ? findDisplayOutlineItemById(activeOutlineItemId.value) : null
  if (activeItem && activeItem.source !== 'user_bookmark_root' && !activeItem.syntheticRoot && activeItem.pageNumber === context.pageNumber) {
    return activeItem.id
  }
  const matchedUserBookmark = userBookmarks.value.find((bookmark) => {
    return bookmark.pageNumber === context.pageNumber
      && (!!bookmark.elementId && bookmark.elementId === context.elementId)
  })
  return matchedUserBookmark?.id ?? null
}

function saveStudyNote() {
  const composer = noteComposer.value
  const context = composer.context
  const title = composer.title.trim()
  const content = composer.content.trim()
  if (!context) {
    showToast('请先选择笔记来源', 'info')
    return
  }
  if (!title || !content) {
    showToast('请补充笔记标题和内容', 'info')
    return
  }

  const now = new Date().toISOString()
  const bookmarkId = composer.bookmarkId ?? resolveActiveBookmarkId(context)
  if (composer.mode === 'edit' && composer.noteId) {
    studyNotes.value = studyNotes.value.map((note) => note.id === composer.noteId
      ? {
          ...note,
          bookmarkId,
          title,
          content,
          status: composer.status,
          source: composer.source,
          selectedText: context.text,
          pageNumber: context.pageNumber,
          blockId: context.blockId,
          elementId: context.elementId,
          bbox: context.bbox,
          updatedAt: now,
        }
      : note)
    activeNoteId.value = composer.noteId
  } else {
    const note: StudyNote = {
      id: `study-note-${Date.now()}`,
      documentId: context.documentId,
      bookmarkId,
      pageNumber: context.pageNumber,
      blockId: context.blockId,
      elementId: context.elementId,
      bbox: context.bbox,
      selectedText: context.text,
      title,
      content,
      source: composer.source,
      status: composer.status,
      tags: inferNoteTags(title, context.text),
      createdAt: now,
      updatedAt: now,
    }
    studyNotes.value = [note, ...studyNotes.value]
    activeNoteId.value = note.id
  }
  noteComposer.value = createEmptyNoteComposer()
  noteAgentPrompt.value = ''
  aiCandidateContent.value = ''
  agentPanelMode.value = 'agent'
  showToast(composer.status === 'draft' ? '已生成待整理笔记' : '已保存为学习笔记', 'success')
  scheduleWorkspaceStateSave()
}

function cancelStudyNoteComposer() {
  noteComposer.value = createEmptyNoteComposer()
  noteAgentPrompt.value = ''
  aiCandidateContent.value = ''
  agentPanelMode.value = 'agent'
}

function updateStudyNoteStatus(noteId: string, status: StudyNoteStatus) {
  const now = new Date().toISOString()
  studyNotes.value = studyNotes.value.map((note) => note.id === noteId
    ? { ...note, status, updatedAt: now }
    : note)
  const statusLabel: Record<StudyNoteStatus, string> = {
    draft: '待整理',
    saved: '已沉淀',
    reviewing: '复习中',
    mastered: '已掌握',
  }
  showToast(`已移动到${statusLabel[status]}`, 'success')
  scheduleWorkspaceStateSave()
}

function openStudyNote(noteId: string) {
  const note = studyNotes.value.find((item) => item.id === noteId)
  if (!note) return
  activeNoteId.value = note.id
  jumpToStudyNote(note)
}

function resolveNoteBookmarkLabel(note: StudyNote) {
  if (!note.bookmarkId) return ''
  const userBookmark = userBookmarks.value.find((bookmark) => bookmark.id === note.bookmarkId)
  if (userBookmark) return userBookmark.title
  const outlineItem = findDisplayOutlineItemById(note.bookmarkId)
  return outlineItem?.title ?? '已绑定定位'
}

function jumpToStudyNote(note: StudyNote) {
  activeBlockId.value = note.blockId
  activeOutlineItemId.value = note.bookmarkId
  targetPdfPage.value = note.pageNumber
  currentPdfPage.value = note.pageNumber
  documentView.value = 'pdf-canvas'
  selectedPdfText.value = note.selectedText
  selectedPdfSelectionType.value = note.bbox ? 'text' : null
  selectedPdfContext.value = {
    documentId: note.documentId,
    pageNumber: note.pageNumber,
    blockId: note.blockId,
    elementId: note.elementId,
    bbox: note.bbox,
    text: note.selectedText,
  }
  pdfSourceHighlight.value = note.bbox
    ? {
        pageNumber: note.pageNumber,
        bbox: note.bbox,
        label: '笔记来源',
        text: note.selectedText,
      }
    : null
  scheduleWorkspaceStateSave()
}

function resolveNoteContextFromPdfSelection(payload: PdfSelectionPayload): DocumentSelectionContext {
  const document = readingDocument.value
  const fallbackBlock = document?.blocks.find((block) => {
    return block.id === payload.blockId || block.elementId === payload.elementId
  }) ?? activeBlock.value
  return {
    documentId: payload.documentId,
    pageNumber: payload.pageNumber,
    blockId: payload.blockId ?? fallbackBlock?.id ?? 'selection',
    elementId: payload.elementId ?? fallbackBlock?.elementId ?? fallbackBlock?.id ?? 'selection',
    bbox: payload.bbox,
    text: payload.text,
  }
}

function buildDefaultNoteTitle(text: string) {
  const normalized = text.replace(/\s+/g, ' ').trim()
  if (!normalized) return '新的学习笔记'
  return normalized.length > 24 ? `${normalized.slice(0, 24)}...` : normalized
}

function inferNoteTags(title: string, text: string) {
  const source = `${title} ${text}`.toLowerCase()
  const tags: string[] = []
  if (source.includes('o(') || source.includes('复杂度')) tags.push('复杂度')
  if (source.includes('sort') || source.includes('排序')) tags.push('排序')
  if (source.includes('公式') || source.includes('n²') || source.includes('n^2')) tags.push('公式')
  return tags
}

</script>

<style scoped>
.intensive-workspace-page {
  --ide-bg: #f3f6fa;
  --ide-panel: #ffffff;
  --ide-panel-2: #f8fafc;
  --ide-panel-3: #eef7f6;
  --ide-border: #d9e2ec;
  --ide-text: #102033;
  --ide-muted: #667085;
  --ide-accent: #0f8f89;
  --reader-bg: #f5f8fb;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 0;
  height: 100vh;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  background: var(--ide-bg);
  color: var(--ide-text);
}

.workspace-toolbar,
.workspace-ide-titlebar,
.workspace-shell,
.workspace-status-bar {
  width: 100%;
  margin: 0;
}

.workspace-ide-titlebar {
  display: grid;
  grid-template-columns: minmax(220px, auto) minmax(280px, 520px) auto;
  gap: 16px;
  align-items: center;
  min-height: 52px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-bg);
  color: var(--ide-text);
}

.workspace-brand {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.workspace-brand__mark {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid rgba(45, 212, 191, 0.45);
  border-radius: 9px;
  background: rgba(45, 212, 191, 0.14);
  color: var(--ide-accent);
  font-weight: 900;
}

.workspace-brand strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-brand strong {
  display: block;
  color: var(--ide-text);
  font-size: 14px;
}

.workspace-command-center input {
  width: 100%;
  min-height: 34px;
  border: 1px solid var(--ide-border);
  border-radius: 999px;
  background: #ffffff;
  color: var(--ide-text);
  padding: 0 16px;
}

.workspace-command-center input::placeholder {
  color: var(--ide-muted);
}

.workspace-titlebar-actions {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.workspace-toolbar {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

button,
input,
textarea {
  font: inherit;
}

.back-button,
.toolbar-actions button,
.activity-button,
.reader-status span,
.inline-actions button,
.agent-card-actions button,
.command-actions button,
.side-drawer-panel button,
.workspace-status-bar button,
.study-note-panel button,
.message-save-note,
.message-append-note,
.missing-state button {
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
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

.back-button {
  display: grid;
  width: 32px;
  min-height: 32px;
  padding: 0;
  place-items: center;
}

.back-button-icon {
  font-size: 18px;
  line-height: 1;
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

.document-heading {
  min-width: 0;
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
    minmax(220px, var(--outline-column-width, 300px))
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
    minmax(220px, var(--outline-column-width, 300px))
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
  border: 1px solid var(--ide-border);
  border-radius: 0;
  background: var(--ide-panel);
  overflow: hidden;
}

.workspace-outline-panel {
  grid-column: 1;
}

.workspace-explorer {
  background: var(--ide-panel);
  color: var(--ide-text);
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
  border-left: 0;
}

.workspace-canvas-panel {
  border-radius: 0;
  background: var(--reader-bg);
}

.workspace-agent-panel {
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
  background: var(--ide-panel);
  color: var(--ide-text);
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

.workspace-outline-panel.workspace-panel--collapsed {
  position: relative;
  z-index: 1;
  border: 0;
  background: transparent;
  overflow: visible;
}

.activity-button {
  position: relative;
  display: grid;
  width: 34px;
  height: 34px;
  min-height: 34px;
  padding: 0;
  place-items: center;
  border-color: transparent;
  border-radius: 8px;
  color: #526071;
  cursor: pointer;
}

.activity-button.active,
.activity-button:hover,
.activity-button:focus-visible {
  border-color: rgba(45, 212, 191, 0.45);
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
}

.activity-button__icon {
  font-size: 14px;
  font-weight: 950;
  line-height: 1;
}

.activity-button small {
  position: absolute;
  right: -3px;
  top: -3px;
  min-width: 16px;
  padding: 1px 4px;
  border: 1px solid var(--ide-bg);
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 9px;
  font-weight: 950;
  line-height: 1.2;
}

.workspace-drawer-rail {
  display: grid;
  width: 100%;
  min-width: 0;
  height: 100%;
  place-items: center;
  border: 0;
  border-radius: 0;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
  writing-mode: vertical-rl;
}

.workspace-drawer-rail:hover,
.workspace-drawer-rail:focus-visible {
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
  transform: none;
}

.workspace-drawer-rail--outline {
  position: absolute;
  top: 12px;
  left: 8px;
  z-index: 13;
  width: 34px;
  min-width: 34px;
  height: 34px;
  border: 1px solid var(--ide-border);
  border-radius: 9px;
  background: var(--ide-panel-2);
  box-shadow: 0 8px 20px rgb(15 23 42 / 14%);
  color: var(--ide-accent);
  font-size: 16px;
  writing-mode: horizontal-tb;
}

.workspace-drawer-rail--outline:hover,
.workspace-drawer-rail--outline:focus-visible {
  border-color: rgba(45, 212, 191, 0.45);
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
}

.workspace-drawer-rail__icon {
  line-height: 1;
}

.panel-drawer-toggle {
  flex: 0 0 auto;
  min-height: 28px;
  padding: 0 8px;
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.panel-drawer-toggle:hover {
  border-color: rgba(45, 212, 191, 0.45);
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
}

.workspace-outline-panel {
  position: relative;
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  background: var(--ide-panel);
}

.outline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px 10px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel);
}

.outline-header h2 {
  margin: 0;
  color: var(--ide-text);
  font-size: 15px;
  line-height: 1.2;
  font-weight: 800;
}

.outline-heading-main {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.outline-heading-main p,
.outline-heading-main span {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.outline-heading-main p {
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
}

.outline-heading-main span {
  color: #748094;
  font-size: 12px;
  font-weight: 650;
}

.workspace-explorer {
  background: var(--ide-panel);
}

.workspace-explorer .outline-header {
  border-color: var(--ide-border);
  background: var(--ide-panel);
}

.workspace-explorer .outline-heading-main p,
.workspace-explorer .outline-heading-main span {
  color: var(--ide-muted);
}

.workspace-explorer .outline-header h2 {
  color: var(--ide-text);
  letter-spacing: 0.08em;
}

.workspace-resource-actions {
  display: grid;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel);
}

.workspace-resource-actions button {
  min-width: 0;
  border: 1px solid transparent;
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  cursor: pointer;
}

.workspace-resource-actions button:hover,
.workspace-resource-actions button:focus-visible {
  border-color: rgba(45, 212, 191, 0.4);
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
  outline: none;
}

.workspace-resource-actions {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.workspace-resource-actions button {
  min-height: 34px;
  padding: 0 8px;
  text-align: center;
  font-size: 12px;
  font-weight: 900;
}

.side-drawer-switcher {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 4px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-bg);
}

.side-drawer-switcher button {
  min-height: 28px;
  padding: 0 6px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--ide-muted);
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.side-drawer-switcher button.active,
.side-drawer-switcher button:hover,
.side-drawer-switcher button:focus-visible {
  border-color: rgba(45, 212, 191, 0.42);
  background: rgba(45, 212, 191, 0.14);
  color: var(--ide-accent);
}

.project-tree-shell {
  grid-row: 4;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  background: var(--ide-panel);
}

.project-tree-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel-2);
}

.project-tree-toolbar div {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.project-tree-toolbar span,
.project-tree-toolbar small {
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 800;
}

.project-tree-toolbar strong {
  min-width: 0;
  overflow: hidden;
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-tree-toolbar small {
  flex: 0 0 auto;
  color: #0f766e;
}

.project-tree {
  display: grid;
  align-content: start;
  gap: 7px;
  min-height: 0;
  padding: 10px 8px 16px;
  overflow: auto;
  scrollbar-color: #cbd5e1 transparent;
  scrollbar-width: thin;
}

.project-tree::-webkit-scrollbar {
  width: 8px;
}

.project-tree::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: 999px;
  background: #cbd5e1;
  background-clip: content-box;
}

.project-tree-folder {
  display: grid;
  gap: 4px;
}

.project-tree-folder__header {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  min-height: 32px;
  align-items: center;
  gap: 6px;
  border: 0;
  border-radius: 8px;
  padding: 0 8px;
  background: transparent;
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 950;
  text-align: left;
  cursor: pointer;
}

.project-tree-folder__header:hover,
.project-tree-folder__header:focus-visible {
  background: #eefaf7;
  color: #0f766e;
  outline: none;
}

.project-tree-folder__header span:not(.project-tree-folder__chevron) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-tree-folder__header small {
  min-width: 24px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #eef2f7;
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 900;
  text-align: center;
}

.project-tree-folder__chevron {
  display: inline-grid;
  place-items: center;
  color: #8b98aa;
  font-size: 18px;
  line-height: 1;
  transform: rotate(90deg);
  transition: transform 0.16s ease;
}

.project-tree-folder.is-collapsed .project-tree-folder__chevron {
  transform: rotate(0deg);
}

.project-tree-folder__body {
  display: grid;
  gap: 3px;
  padding-left: 22px;
}

.project-tree-resource {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  min-height: 38px;
  align-items: center;
  gap: 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 5px 7px;
  background: transparent;
  color: var(--ide-text);
  text-align: left;
  cursor: pointer;
}

.project-tree-resource:hover,
.project-tree-resource:focus-visible {
  border-color: rgba(20, 184, 166, 0.28);
  background: #eefaf7;
  outline: none;
}

.project-tree-resource__icon {
  display: inline-grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 1px solid #d8e2ee;
  border-radius: 7px;
  background: #ffffff;
  color: #0f766e;
  font-size: 10px;
  font-weight: 950;
}

.project-tree-resource__main {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.project-tree-resource strong,
.project-tree-resource small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-tree-resource strong {
  color: inherit;
  font-size: 12px;
  font-weight: 850;
}

.project-tree-resource small {
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 750;
}

.project-tree-resource mark {
  min-width: 20px;
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(20, 184, 166, 0.14);
  color: #0f766e;
  font-size: 11px;
  font-weight: 950;
  text-align: center;
}

.project-tree-empty {
  min-height: 34px;
  border: 1px dashed #cdd9e6;
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-muted);
  font-size: 12px;
  font-weight: 850;
  cursor: pointer;
}

.project-tree-empty:hover,
.project-tree-empty:focus-visible {
  border-color: rgba(20, 184, 166, 0.42);
  background: #eefaf7;
  color: #0f766e;
  outline: none;
}

.project-tree-outline {
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid var(--ide-border);
}

.project-tree-outline .project-tree-folder__body {
  gap: 7px;
  padding-left: 0;
}

.project-tree-outline .outline-controls {
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  padding: 8px;
  background: var(--ide-panel-2);
}

.project-tree-outline .outline-list {
  grid-row: auto;
  overflow: visible;
  padding: 2px 0 6px;
}

.side-drawer-panel {
  grid-row: 3 / 5;
  display: grid;
  align-content: start;
  gap: 8px;
  min-height: 0;
  padding: 10px;
  overflow: auto;
}

.side-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 4px;
}

.side-section-heading strong {
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 950;
}

.side-section-heading button,
.side-empty-action {
  min-height: 30px;
  padding: 0 10px;
  cursor: pointer;
}

.side-list-card,
.side-asset-card {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
  cursor: pointer;
}

.side-list-card:hover,
.side-list-card.active,
.side-asset-card:hover,
.side-asset-card.active {
  border-color: #5eead4;
  background: rgba(45, 212, 191, 0.12);
  box-shadow: inset 3px 0 0 #14b8a6;
}

.side-list-card strong,
.side-list-card span,
.side-asset-card strong,
.side-asset-card span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-list-card strong,
.side-asset-card strong {
  color: var(--ide-text);
  font-size: 12px;
  font-weight: 950;
}

.side-list-card span,
.side-asset-card span {
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 800;
}

.side-empty-action {
  border-style: dashed !important;
  background: var(--ide-panel-2) !important;
  color: var(--ide-accent) !important;
  font-size: 12px;
  font-weight: 950;
}

.side-asset-board {
  gap: 10px;
}

.side-asset-group {
  display: grid;
  gap: 7px;
  padding: 9px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
}

.side-asset-group--warm {
  border-color: rgba(253, 230, 138, 0.36);
  background: rgba(253, 230, 138, 0.08);
}

.side-asset-group--green {
  border-color: rgba(187, 247, 208, 0.3);
  background: rgba(45, 212, 191, 0.08);
}

.side-asset-group--blue {
  border-color: rgba(191, 219, 254, 0.28);
  background: rgba(96, 165, 250, 0.08);
}

.side-asset-group header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.side-asset-group header span {
  color: var(--ide-text);
  font-size: 12px;
  font-weight: 950;
}

.side-asset-group header small {
  min-width: 22px;
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--ide-panel);
  color: var(--ide-accent);
  font-size: 11px;
  font-weight: 950;
  text-align: center;
}

.side-asset-group p {
  margin: 0;
  color: var(--ide-muted);
  font-size: 11px;
  line-height: 1.45;
}

.outline-controls {
  display: grid;
  gap: 8px;
  padding: 10px 12px 12px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-bg);
}

.outline-search {
  display: grid;
  gap: 5px;
}

.outline-search span {
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 750;
}

.outline-search input {
  min-height: 36px;
  width: 100%;
  min-width: 0;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  padding: 0 11px;
  background: #ffffff;
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 700;
}

.outline-search input:focus {
  border-color: #5eead4;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
  outline: none;
}

.outline-filter-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.outline-filter-tabs button {
  display: flex;
  min-width: 0;
  min-height: 30px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-muted);
  font-size: 12px;
  font-weight: 750;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, color 0.16s ease;
}

.outline-filter-tabs button.active {
  border-color: rgba(45, 212, 191, 0.5);
  background: rgba(45, 212, 191, 0.14);
  color: var(--ide-accent);
}

.outline-filter-tabs small {
  color: inherit;
  font-size: 11px;
  opacity: 0.78;
}

.outline-quick-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.outline-quick-actions button {
  min-width: 0;
  min-height: 30px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.outline-quick-actions button:hover,
.outline-quick-actions button:focus-visible {
  border-color: rgba(45, 212, 191, 0.5);
  background: rgba(45, 212, 191, 0.14);
  color: var(--ide-accent);
  outline: none;
}

.outline-quick-actions button.danger:hover,
.outline-quick-actions button.danger:focus-visible {
  border-color: rgba(248, 113, 113, 0.5);
  background: rgba(248, 113, 113, 0.14);
  color: #fca5a5;
}

.outline-list {
  grid-row: 4;
  min-height: 0;
  overflow: auto;
  padding: 9px 8px 14px;
  scrollbar-color: #cbd5e1 transparent;
  scrollbar-width: thin;
}

.outline-list::-webkit-scrollbar {
  width: 8px;
}

.outline-list::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: 999px;
  background: #cbd5e1;
  background-clip: content-box;
}

.outline-list::-webkit-scrollbar-track {
  background: transparent;
}

.outline-page-group {
  display: grid;
  gap: 1px;
  margin-bottom: 10px;
}

.outline-tree-row {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  align-items: center;
  gap: 2px;
}

.outline-tree-row--level-1 {
  padding-left: 0;
}

.outline-tree-row--level-2 {
  padding-left: 12px;
}

.outline-tree-row--level-3 {
  padding-left: 24px;
}

.outline-tree-row--level-4 {
  padding-left: 36px;
}

.outline-tree-row--level-5 {
  padding-left: 48px;
}

.outline-tree-row--level-6 {
  padding-left: 60px;
}

.outline-tree-row.is-document-root {
  margin: 0 4px 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #edf2f7;
}

.outline-node-toggle {
  display: grid;
  width: 22px;
  height: 28px;
  min-height: 0;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #8b98aa;
  cursor: pointer;
}

.outline-node-toggle span {
  display: inline-block;
  font-size: 20px;
  font-weight: 800;
  line-height: 1;
  transform: rotate(90deg);
  transition: transform 0.16s ease, color 0.16s ease;
}

.outline-tree-row.is-collapsed .outline-node-toggle span {
  transform: rotate(0deg);
}

.outline-node-toggle:hover,
.outline-node-toggle:focus-visible {
  background: #eefaf7;
  color: #0f766e;
  transform: none;
}

.outline-node-toggle.is-placeholder {
  pointer-events: none;
  visibility: hidden;
}

.outline-page-button,
.outline-block-button {
  display: grid;
  width: 100%;
  min-width: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ide-muted);
  text-align: left;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;
}

.outline-page-button {
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  font-weight: 750;
}

.outline-block-button {
  --outline-rail-x: 5px;
  position: relative;
  grid-template-columns: minmax(0, 1fr) max-content;
  align-items: center;
  column-gap: 8px;
  min-height: 34px;
  padding: 6px 9px 6px 14px;
}

.outline-block-button::before {
  position: absolute;
  top: 7px;
  bottom: 7px;
  left: var(--outline-rail-x);
  width: 2px;
  border-radius: 999px;
  background: #44515f;
  content: '';
}

.outline-block-button::after {
  position: absolute;
  top: 8px;
  bottom: 8px;
  left: calc(var(--outline-rail-x) - 1px);
  width: 3px;
  border-radius: 999px;
  background: #14b8a6;
  content: '';
  opacity: 0;
  transition: opacity 0.16s ease;
}

.outline-block-button--level-1 {
  --outline-rail-x: 5px;
  padding-left: 14px;
}

.outline-block-button--level-1::before {
  background: #14b8a6;
}

.outline-block-button--level-2 {
  --outline-rail-x: 5px;
  padding-left: 14px;
}

.outline-block-button--level-3 {
  --outline-rail-x: 5px;
  padding-left: 14px;
}

.outline-block-button--level-4 {
  --outline-rail-x: 5px;
  padding-left: 14px;
}

.outline-block-button--level-5 {
  --outline-rail-x: 5px;
  padding-left: 14px;
}

.outline-block-button--level-6 {
  --outline-rail-x: 5px;
  padding-left: 14px;
}

.outline-block-button.is-document-root {
  margin: 0;
  border-bottom: 0;
  border-radius: 0;
  padding: 8px 6px 8px 14px;
  color: #0f766e;
}

.outline-block-button.is-document-root::before {
  top: 10px;
  bottom: 13px;
}

.outline-block-button.is-document-root::after {
  display: none;
}

.outline-block-button.is-document-root .outline-item-title {
  font-size: 13px;
  font-weight: 850;
}

.outline-block-button.is-user-bookmark-root {
  color: #fbbf24;
}

.outline-block-button.is-user-bookmark-root::before {
  background: #f59e0b;
}

.outline-block-button.is-user-bookmark {
  color: #334155;
}

.outline-block-button.is-user-bookmark::before {
  background: #f59e0b;
}

.outline-block-button.is-user-bookmark.active {
  background: rgba(245, 158, 11, 0.14);
  color: #fbbf24;
  box-shadow: inset 0 0 0 1px rgba(251, 146, 60, 0.38);
}

.outline-block-button.is-user-bookmark.active::after {
  background: #f97316;
}

.outline-page-button:hover,
.outline-block-button:hover {
  background: #f8fafc;
  color: var(--ide-text);
}

.outline-page-button.active,
.outline-block-button.active {
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
  box-shadow: inset 0 0 0 1px rgba(45, 212, 191, 0.42);
}

.outline-block-button.is-current-page:not(.active) {
  background: rgba(56, 189, 248, 0.08);
  color: #0369a1;
}

.outline-block-button.has-notes::before {
  background: #f59e0b;
}

.outline-page-button.has-notes {
  box-shadow: inset 3px 0 0 #f59e0b;
}

.outline-block-button.active::after,
.outline-block-button.is-current-page:not(.active)::after {
  opacity: 1;
}

.outline-block-button.is-current-page:not(.active)::after {
  background: #38bdf8;
}

.outline-block-button.active::before {
  background: #14b8a6;
}

.outline-page-button span,
.outline-block-button .outline-item-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.outline-item-title {
  color: inherit;
  font-size: 13px;
  font-weight: 750;
  line-height: 1.3;
}

.outline-block-button--level-2 .outline-item-title {
  font-size: 13.5px;
  font-weight: 800;
}

.outline-block-button--level-3 .outline-item-title {
  font-size: 13px;
  font-weight: 760;
}

.outline-block-button--level-4 .outline-item-title,
.outline-block-button--level-5 .outline-item-title,
.outline-block-button--level-6 .outline-item-title {
  color: var(--ide-muted);
  font-size: 12.5px;
  font-weight: 720;
}

.outline-item-meta {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  justify-content: flex-end;
}

.outline-page-button small,
.outline-block-button small,
.outline-item-meta mark {
  color: #7a8796;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.outline-item-meta mark {
  display: inline-flex;
  min-height: 18px;
  align-items: center;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(56, 189, 248, 0.16);
  color: #7dd3fc;
}

.outline-item-meta .note-count {
  background: rgba(245, 158, 11, 0.14);
  color: #fbbf24;
}

.outline-empty-state {
  display: grid;
  gap: 5px;
  margin: 2px 4px;
  padding: 16px 12px;
  border: 1px dashed var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-muted);
}

.outline-empty-state strong {
  color: var(--ide-text);
  font-size: 13px;
}

.outline-empty-state span {
  font-size: 12px;
  line-height: 1.5;
}

.workspace-canvas-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
}

.workspace-tabs {
  display: flex;
  min-width: 0;
  min-height: 54px;
  align-items: end;
  gap: 2px;
  padding: 10px 12px 0;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel);
  overflow-x: auto;
}

.workspace-tab {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-rows: auto auto;
  column-gap: 8px;
  min-width: 150px;
  max-width: 280px;
  height: 44px;
  padding: 7px 12px 8px;
  border: 0;
  border-radius: 8px 8px 0 0;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  text-align: left;
  cursor: pointer;
}

.workspace-tab span,
.workspace-tab strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-tab span {
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 900;
}

.workspace-tab strong {
  color: inherit;
  font-size: 13px;
}

.workspace-tab small {
  grid-row: 1 / 3;
  grid-column: 2;
  align-self: center;
  color: var(--ide-muted);
}

.workspace-tab.active {
  border-top: 2px solid var(--ide-accent);
  background: var(--ide-panel-3);
  color: var(--ide-text);
}

.workspace-tab.active span {
  color: var(--ide-muted);
}

.workspace-tab--new {
  display: grid;
  min-width: 42px;
  width: 42px;
  place-items: center;
  color: var(--ide-muted);
  text-align: center;
  font-size: 20px;
}

.workspace-editor-area {
  display: grid;
  min-height: 0;
  background: var(--reader-bg);
}

.learning-editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 340px);
  gap: 12px;
  min-height: 0;
  padding: 12px;
}

.learning-editor-primary {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: #ffffff;
}

.learning-knowledge-column {
  display: grid;
  align-content: start;
  gap: 12px;
  min-width: 0;
  min-height: 0;
  overflow: auto;
}

.workspace-editor-toolbar {
  display: flex;
  justify-content: flex-end;
  min-height: 44px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel);
}

.note-document-editor {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 12px;
  min-height: 0;
  padding: 24px;
  background: var(--reader-bg);
}

.note-document-editor p,
.note-document-editor h2 {
  margin: 0;
}

.note-document-editor p {
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.note-document-editor h2 {
  color: var(--ide-text);
  font-size: 24px;
}

.note-document-editor textarea {
  width: 100%;
  min-height: 0;
  resize: none;
  border: 1px solid var(--ide-border);
  border-radius: 10px;
  padding: 16px;
  background: #ffffff;
  color: var(--ide-text);
}

.workspace-canvas-panel :deep(.pdf-learning-canvas) {
  min-height: 0;
  height: 100%;
}

.document-reader,
.agent-panel,
.missing-state {
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel);
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
  border-bottom: 1px solid var(--ide-border);
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
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-bg);
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
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-bg);
}

.document-view-tabs--compact {
  gap: 4px;
  padding: 3px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: #ffffff;
}

.document-view-tabs button {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
}

.document-view-tabs--compact button {
  min-height: 28px;
  padding: 0 9px;
  border-radius: 6px;
  font-size: 12px;
}

.document-view-tabs button.active {
  border-color: rgba(45, 212, 191, 0.55);
  background: rgba(45, 212, 191, 0.16);
  color: var(--ide-accent);
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
    linear-gradient(rgba(15, 23, 42, 0.035) 31px, transparent 31px) 0 0 / 100% 32px,
    var(--reader-bg);
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
  background: rgba(45, 212, 191, 0.12);
}

.ide-gutter {
  display: grid;
  align-content: start;
  gap: 5px;
  padding: 18px 12px 18px 18px;
  border-right: 1px solid var(--ide-border);
  color: var(--ide-muted);
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
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 900;
  text-transform: uppercase;
}

.ide-block-meta button,
.agent-toolbar button {
  min-height: 26px;
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.ide-block-meta button {
  padding: 0 8px;
}

.source-text--ide {
  max-width: 860px;
  color: var(--ide-text);
  font-size: 17px;
  line-height: 1.95;
}

.reader-heading,
.agent-header,
.document-summary,
.inline-actions,
.command-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.reader-heading h2,
.agent-header h2 {
  margin: 4px 0 0;
  color: var(--ide-text);
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
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
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
  color: var(--ide-text);
  font-size: 18px;
}

.document-summary p {
  margin: 5px 0 0;
  color: var(--ide-muted);
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
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel);
  cursor: pointer;
}

.document-block.active {
  border-color: #6ee7dc;
  background: rgba(45, 212, 191, 0.1);
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
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 800;
}

.source-text {
  margin: 0;
  color: var(--ide-text);
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
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel);
}

.translation-card {
  padding: 13px;
  background: rgba(45, 212, 191, 0.1);
}

.translation-card span,
.insight-grid span,
.inline-note span {
  display: inline-block;
  margin-bottom: 7px;
  color: var(--ide-muted);
  font-size: 12px;
  font-weight: 900;
}

.translation-card p {
  margin: 0;
  color: var(--ide-text);
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
  background: var(--ide-panel);
  color: var(--ide-text);
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
  border-color: var(--ide-border);
  background: var(--ide-panel);
}

.agent-header--ide h2 {
  color: var(--ide-text);
}

.agent-header--ide span {
  color: var(--ide-muted);
}

.note-workbench-panel {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  gap: 14px;
  min-height: 0;
  padding: 16px;
  overflow: auto;
}

.note-workbench-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.note-workbench-header p,
.note-workbench-header strong {
  margin: 0;
}

.note-workbench-header strong {
  color: var(--ide-text);
  font-size: 18px;
}

.note-workbench-header button,
.note-workbench-tabs button,
.ai-candidate-card button {
  min-height: 34px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
  color: var(--ide-text);
  font-weight: 900;
  cursor: pointer;
}

.note-workbench-header button:hover,
.note-workbench-tabs button:hover,
.note-workbench-tabs button.active,
.ai-candidate-card button:hover:not(:disabled) {
  border-color: rgba(45, 212, 191, 0.45);
  background: rgba(45, 212, 191, 0.14);
  color: var(--ide-accent);
}

.note-workbench-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.note-workbench-composer {
  min-height: 0;
  padding: 0;
  overflow: visible;
}

.ai-candidate-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #fed7aa;
  border-radius: 10px;
  background: #fff7ed;
}

.ai-candidate-card blockquote {
  margin: 0;
  color: #7c2d12;
  font-size: 13px;
  line-height: 1.6;
}

.ai-candidate-card button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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

.study-note-panel {
  display: grid;
  gap: 10px;
  padding: 13px 18px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel);
}

.study-note-panel--composer-active {
  border-bottom-color: #99f6e4;
  background: linear-gradient(180deg, rgba(45, 212, 191, 0.12) 0%, var(--ide-panel) 100%);
  box-shadow: inset 3px 0 0 #14b8a6;
}

.study-note-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.study-note-panel__header strong {
  display: block;
  margin-top: 2px;
  color: var(--ide-text);
  font-size: 15px;
}

.study-note-panel__header button,
.study-note-composer__actions button,
.study-note-card__actions button {
  min-height: 30px;
  padding: 0 10px;
  cursor: pointer;
}

.study-note-composer {
  display: grid;
  gap: 9px;
}

.study-note-source {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 9px;
  border-radius: 8px;
  background: rgba(45, 212, 191, 0.1);
}

.study-note-source span {
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.study-note-source small {
  min-width: 0;
  overflow: hidden;
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.study-note-selected-text {
  max-height: 92px;
  margin: 0;
  overflow: auto;
  padding: 9px 10px;
  border: 1px solid #ccfbf1;
  border-left: 3px solid #14b8a6;
  border-radius: 8px;
  background: #ffffff;
  color: var(--ide-muted);
  font-size: 12px;
  font-weight: 800;
  line-height: 1.6;
}

.study-note-composer input,
.study-note-composer textarea {
  width: 100%;
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  padding: 10px;
  background: #ffffff;
  color: var(--ide-text);
  font: inherit;
  line-height: 1.55;
}

.study-note-composer input {
  min-height: 38px;
  font-weight: 800;
}

.study-note-composer textarea {
  min-height: 104px;
  resize: vertical;
}

.study-note-panel--composer-active .study-note-composer textarea {
  min-height: 168px;
  background: #ffffff;
}

.note-agent-compose {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
}

.note-agent-compose__quick,
.note-agent-compose__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.note-agent-compose__quick {
  flex-wrap: wrap;
}

.note-agent-compose button {
  min-height: 28px;
  padding: 0 10px;
  border-color: var(--ide-border);
  background: var(--ide-panel-2);
  color: var(--ide-accent);
  font-size: 12px;
  font-weight: 900;
}

.note-agent-compose button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.note-agent-compose textarea {
  min-height: 58px;
  border-color: var(--ide-border);
  background: #ffffff;
  font-size: 12px;
}

.note-agent-compose__actions {
  justify-content: space-between;
}

.note-agent-compose__actions span {
  min-width: 0;
  color: var(--ide-muted);
  font-size: 11px;
  font-weight: 800;
}

.study-note-composer__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.study-note-empty {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px dashed var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
}

.study-note-empty span {
  color: var(--ide-text);
  font-size: 13px;
  font-weight: 900;
}

.study-note-empty small {
  color: var(--ide-muted);
  line-height: 1.5;
}

.study-note-list {
  display: grid;
  gap: 9px;
  max-height: 320px;
  overflow: auto;
}

.study-note-card {
  display: grid;
  gap: 7px;
  padding: 11px;
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  background: var(--ide-panel-2);
}

.study-note-card.active {
  border-color: #14b8a6;
  box-shadow: inset 3px 0 0 #14b8a6;
}

.study-note-card--draft {
  background: rgba(253, 230, 138, 0.08);
}

.study-note-card--reviewing {
  background: rgba(96, 165, 250, 0.08);
}

.study-note-card--mastered {
  background: rgba(45, 212, 191, 0.08);
}

.study-note-card__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.study-note-card__meta span,
.study-note-card__meta small,
.study-note-card__meta mark {
  font-size: 11px;
  font-weight: 900;
}

.study-note-card__meta span {
  color: var(--ide-accent);
}

.study-note-card__meta small {
  color: var(--ide-muted);
}

.study-note-card__meta mark {
  margin-left: auto;
  padding: 3px 6px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
}

.study-note-card h3 {
  margin: 0;
  color: #111827;
  font-size: 14px;
  line-height: 1.35;
}

.study-note-card p {
  margin: 0;
  color: #344054;
  font-size: 13px;
  line-height: 1.55;
}

.study-note-card blockquote {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  padding-left: 9px;
  border-left: 3px solid #f59e0b;
  color: #667085;
  font-size: 12px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.study-note-tags,
.study-note-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.study-note-tags span {
  padding: 3px 7px;
  border-radius: 999px;
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
  font-size: 11px;
  font-weight: 900;
}

.study-note-card__actions button {
  font-size: 12px;
}

.agent-answer--ide {
  background: var(--ide-panel);
}

.agent-toolbar {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-bg);
}

.agent-toolbar button {
  padding: 0 10px;
}

.agent-conversation--ide {
  min-height: 0;
  overflow: auto;
  padding: 12px 18px;
  background: var(--ide-panel);
}

.agent-command--ide {
  padding: 12px 18px 16px;
  background: var(--ide-bg);
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
  background: var(--ide-panel);
}

.agent-answer h3 {
  margin: 7px 0;
  color: var(--ide-text);
  font-size: 15px;
  line-height: 1.45;
}

.agent-answer p,
.agent-card p {
  margin: 0;
  color: var(--ide-text);
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
  background: var(--ide-panel-2);
}

.capability-list strong {
  color: #111827;
  font-size: 13px;
}

.capability-list span {
  color: var(--ide-muted);
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
  background: var(--ide-panel-2);
}

.message--assistant {
  background: rgba(45, 212, 191, 0.1);
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
  background: rgba(45, 212, 191, 0.1);
  color: var(--ide-accent);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.message-citations button:hover {
  border-color: #0f766e;
  background: #ccfbf1;
}

.message-save-note,
.message-append-note {
  justify-self: start;
  min-height: 28px;
  margin-top: 8px;
  padding: 5px 9px;
  color: #0f766e;
  font-size: 12px;
  cursor: pointer;
}

.message-append-note {
  margin-left: 6px;
  border-color: #99f6e4;
  background: rgba(45, 212, 191, 0.1);
}

.agent-command {
  display: grid;
  gap: 8px;
}

.agent-context,
.agent-answer--ide,
.agent-card--ide,
.study-note-panel,
.agent-toolbar,
.agent-conversation--ide,
.agent-command--ide,
.message,
.capability-list article,
.study-note-card,
.study-note-empty,
.note-agent-compose {
  border-color: var(--ide-border);
  background: var(--ide-panel);
  color: var(--ide-text);
}

.agent-context blockquote,
.agent-answer h3,
.agent-answer p,
.agent-card p,
.capability-list strong,
.message strong,
.message p,
.study-note-panel__header strong,
.study-note-empty span,
.study-note-card h3,
.study-note-card p {
  color: var(--ide-text);
}

.agent-context small,
.capability-list span,
.study-note-empty small,
.study-note-card blockquote,
.study-note-card__meta small,
.note-agent-compose__actions span {
  color: var(--ide-muted);
}

.study-note-panel--composer-active {
  border-bottom-color: rgba(45, 212, 191, 0.42);
  background: linear-gradient(180deg, rgba(45, 212, 191, 0.12) 0%, var(--ide-panel) 100%);
}

.study-note-source,
.study-note-selected-text,
.study-note-composer input,
.study-note-composer textarea,
.study-note-panel--composer-active .study-note-composer textarea,
.note-agent-compose textarea,
.agent-command--ide textarea,
.inline-note textarea,
.agent-command textarea {
  border-color: var(--ide-border);
  background: #ffffff;
  color: var(--ide-text);
}

.study-note-source span,
.study-note-card__meta span,
.study-note-tags span,
.message-citations button,
.message-save-note,
.message-append-note {
  color: var(--ide-accent);
}

.study-note-source small,
.study-note-selected-text {
  color: var(--ide-muted);
}

.note-agent-compose button,
.study-note-panel__header button,
.study-note-composer__actions button,
.study-note-card__actions button,
.agent-toolbar button,
.agent-card-actions button,
.message-citations button,
.message-save-note,
.message-append-note {
  border-color: var(--ide-border);
  background: var(--ide-panel-2);
}

.message--assistant,
.study-note-card--draft,
.study-note-card--reviewing,
.study-note-card--mastered,
.message-citations button,
.message-append-note,
.study-note-tags span {
  background: rgba(45, 212, 191, 0.1);
}

.agent-header span,
.study-note-card__meta mark {
  background: rgba(37, 99, 235, 0.18);
  color: #93c5fd;
}

.chip-list button,
.agent-chip-list span {
  background: rgba(45, 212, 191, 0.12);
  color: var(--ide-accent);
}

.workspace-status-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 34px;
  padding: 0 10px;
  border: 0;
  border-radius: 0;
  background: #0f766e;
  color: #ffffff;
  box-shadow: none;
}

.workspace-status-bar span {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  color: #ffffff;
  font-size: 12px;
  font-weight: 850;
  white-space: nowrap;
}

.workspace-status-bar span + span::before {
  width: 4px;
  height: 4px;
  margin-right: 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.24);
  content: '';
}

.workspace-status-bar button {
  min-height: 26px;
  margin-left: auto;
  padding: 0 10px;
  border-color: rgba(15, 143, 137, 0.28);
  background: rgba(255, 255, 255, 0.9);
  color: #0f766e;
  cursor: pointer;
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
input:focus-visible,
textarea:focus-visible {
  outline: 3px solid rgba(20, 184, 166, 0.24);
  outline-offset: 2px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 1440px) {
  .workspace-shell--ide {
    grid-template-columns:
      minmax(190px, 240px)
      6px
      minmax(360px, 1fr)
      6px
      minmax(300px, 340px);
  }

  .workspace-shell--outline-collapsed {
    grid-template-columns:
      44px
      0
      minmax(360px, 1fr)
      6px
      minmax(300px, 340px);
  }

  .workspace-shell--agent-collapsed {
    grid-template-columns:
      minmax(190px, 240px)
      6px
      minmax(360px, 1fr)
      0
      40px;
  }

  .workspace-shell--outline-collapsed.workspace-shell--agent-collapsed {
    grid-template-columns: 44px 0 minmax(360px, 1fr) 0 40px;
  }

  .activity-button {
    width: 32px;
    height: 32px;
    min-height: 32px;
  }

  .workspace-tab {
    min-width: 120px;
  }

  .learning-editor-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .learning-knowledge-column {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    overflow: visible;
  }

  .workspace-resizer {
    min-width: 6px;
  }

  .workspace-resizer::after {
    inset: 0 -4px;
  }

  .side-drawer-switcher {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    padding: 7px 8px;
  }

  .side-drawer-switcher button {
    padding: 0 4px;
    font-size: 11px;
  }

  .project-tree-toolbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .project-tree {
    padding: 8px 6px 12px;
  }

  .project-tree-folder__body {
    padding-left: 14px;
  }

  .project-tree-resource {
    grid-template-columns: 28px minmax(0, 1fr);
  }

  .project-tree-resource mark {
    display: none;
  }

  .project-tree-outline .project-tree-folder__body {
    padding-left: 0;
  }

  .outline-controls {
    padding: 8px;
    gap: 7px;
  }

  .outline-filter-tabs {
    grid-template-columns: 1fr;
  }

  .outline-quick-actions {
    grid-template-columns: 1fr;
  }

  .outline-list {
    padding: 8px;
  }

  .outline-block-button {
    gap: 4px;
    padding: 7px 7px;
  }

  .outline-item-meta {
    justify-content: flex-start;
  }

  .agent-header--ide {
    padding: 10px 12px;
  }

  .agent-panel--ide {
    grid-template-rows: auto auto auto auto minmax(0, 1fr) auto;
  }
}

@media (max-width: 1180px) {
  .workspace-command-center {
    max-width: min(520px, 52vw);
  }

  .toolbar-actions {
    justify-content: flex-start;
  }

  .agent-panel {
    min-width: 0;
  }

}

@media (max-width: 760px) {
  .intensive-workspace-page {
    padding: 16px 14px 112px;
  }

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

  .agent-panel {
    position: static;
    max-height: none;
  }

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

  .workspace-status-bar {
    overflow-x: auto;
  }
}
</style>
