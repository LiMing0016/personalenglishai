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
        <button type="button" class="primary-action" @click="completeLearningSession">完成学习</button>
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
          <div class="outline-heading-main">
            <p>学习导航</p>
            <h2 id="outline-title">目录导航</h2>
            <span>{{ outlineSummary }}</span>
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

        <section class="outline-controls" aria-label="目录筛选">
          <label class="outline-search">
            <span>搜索目录</span>
            <input
              v-model="outlineSearchQuery"
              type="search"
              placeholder="章节、页码、笔记..."
              aria-label="搜索目录"
            />
          </label>

          <div class="outline-filter-tabs" aria-label="目录范围">
            <button
              v-for="scope in outlineFilterScopes"
              :key="scope.id"
              type="button"
              :class="{ active: outlineFilterScope === scope.id }"
              @click="outlineFilterScope = scope.id">
              {{ scope.label }}
              <small>{{ scope.count }}</small>
            </button>
          </div>

          <div class="outline-quick-actions" aria-label="书签操作">
            <button type="button" @click="createUserBookmark">添加书签</button>
            <button type="button" @click="exportWorkspaceBookmarks">导出 PDF</button>
            <button v-if="activeUserBookmark" type="button" @click="renameActiveUserBookmark">重命名</button>
            <button v-if="activeUserBookmark" type="button" class="danger" @click="deleteActiveUserBookmark">删除</button>
          </div>
        </section>

        <nav class="outline-list" aria-label="PDF 页码与目录">
          <section v-if="displayOutlineItems.length > 0 && filteredOutlineItems.length > 0" class="outline-page-group">
            <div
              v-for="item in filteredOutlineItems"
              :key="item.id"
              class="outline-tree-row"
              :class="[
                `outline-tree-row--level-${item.displayLevel}`,
                {
                  active: !item.syntheticRoot && isOutlineItemActive(item),
                  'is-current-page': !item.syntheticRoot && item.pageNumber === currentPdfPage,
                  'has-notes': getOutlineItemNoteCount(item) > 0,
                  'is-document-root': item.syntheticRoot,
                  'is-user-bookmark': item.source === 'user_bookmark',
                  'is-user-bookmark-root': item.source === 'user_bookmark_root',
                  'is-collapsed': item.hasChildren && isOutlineNodeCollapsed(item),
                },
              ]">
              <button
                type="button"
                class="outline-node-toggle"
                :class="{ 'is-placeholder': !item.hasChildren }"
                :aria-label="isOutlineNodeCollapsed(item) ? `展开 ${item.title}` : `收起 ${item.title}`"
                :aria-expanded="item.hasChildren ? !isOutlineNodeCollapsed(item) : undefined"
                :disabled="!item.hasChildren"
                @click.stop="toggleOutlineNode(item)">
                <span>›</span>
              </button>

              <button
                type="button"
                class="outline-block-button"
                :class="[
                  `outline-block-button--level-${item.displayLevel}`,
                  {
                    active: !item.syntheticRoot && isOutlineItemActive(item),
                    'is-current-page': !item.syntheticRoot && item.pageNumber === currentPdfPage,
                    'has-notes': getOutlineItemNoteCount(item) > 0,
                    'is-document-root': item.syntheticRoot,
                    'is-user-bookmark': item.source === 'user_bookmark',
                    'is-user-bookmark-root': item.source === 'user_bookmark_root',
                  },
                ]"
                @click="selectOutlineItem(item)">
                <span class="outline-item-title">{{ item.title }}</span>
                <span class="outline-item-meta">
                  <small v-if="item.source === 'user_bookmark_root'">自定义</small>
                  <small v-else-if="item.syntheticRoot">全文目录</small>
                  <small v-else-if="item.source === 'user_bookmark'">Page {{ item.pageNumber }} · 我的</small>
                  <small v-else>Page {{ item.pageNumber }}</small>
                  <mark v-if="!item.syntheticRoot && item.pageNumber === currentPdfPage">当前</mark>
                  <mark v-if="getOutlineItemNoteCount(item) > 0" class="note-count">
                    {{ getOutlineItemNoteCount(item) }} 记
                  </mark>
                </span>
              </button>
            </div>
          </section>

          <section v-else-if="outlineItems.length === 0 && userBookmarks.length === 0 && filteredOutlinePageItems.length > 0" class="outline-page-group">
            <button
              v-for="page in filteredOutlinePageItems"
              :key="page"
              type="button"
              class="outline-page-button"
              :class="{ active: page === currentPdfPage, 'has-notes': getPageNoteCount(page) > 0 }"
              @click="selectOutlinePage(page)">
              <span>Page {{ page }}</span>
              <small v-if="getPageNoteCount(page) > 0">{{ getPageNoteCount(page) }} 条笔记</small>
            </button>
          </section>

          <section v-else class="outline-empty-state">
            <strong>没有匹配的目录</strong>
            <span>换个关键词，或切回全部范围。</span>
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

        <div v-if="documentView === 'text'" class="ide-reader-surface" role="list" aria-label="原文段落列表">
          <article
            v-for="block in readingDocument.blocks"
            :key="block.id"
            role="listitem"
            class="ide-document-block"
            :class="{ active: block.id === activeBlockId }"
            @click="selectOutlineBlock(block.id, block.pageNumber || 1)">
            <aside class="ide-gutter" aria-label="段落定位">
              <span>P{{ block.order }}</span>
              <small v-if="block.pageNumber">Page {{ block.pageNumber }}</small>
            </aside>

            <div class="ide-source-cell">
              <div class="ide-block-meta">
                <span>{{ block.type === 'heading' ? 'Heading' : 'Paragraph' }}</span>
                <button type="button" @click.stop="askAgent('解释当前段落')">Ask</button>
                <button type="button" @click.stop="askAgent('翻译当前段落')">Translate</button>
                <button type="button" @click.stop="startNoteFromActiveBlock">Note</button>
              </div>
              <p class="source-text source-text--ide">{{ block.text }}</p>
            </div>
          </article>
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
          @select-block="selectBlock"
          @ask-agent="askAgent"
          @note-selection="startNoteFromPdfSelection"
          @open-note="openStudyNote"
          @selection-change="handlePdfSelectionChange"
          @page-change="handlePdfPageChange"
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
          <button type="button" @click="startNoteFromActiveBlock">记笔记</button>
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

        <section class="study-note-panel" aria-label="本段笔记">
          <header class="study-note-panel__header">
            <div>
              <p class="answer-label">本段笔记</p>
              <strong>{{ activeBlockNotes.length }} 条</strong>
            </div>
            <button type="button" @click="startNoteFromActiveBlock">新建</button>
          </header>

          <form
            v-if="noteComposer.mode !== 'idle'"
            class="study-note-composer"
            @submit.prevent="saveStudyNote">
            <div class="study-note-source">
              <span>{{ noteComposer.source === 'agent' ? 'Agent 草稿' : '手动笔记' }}</span>
              <small>{{ noteComposerContextLabel }}</small>
            </div>
            <input
              v-model="noteComposer.title"
              type="text"
              placeholder="笔记标题"
              aria-label="笔记标题"
            />
            <textarea
              v-model="noteComposer.content"
              rows="5"
              placeholder="写下你的理解，或编辑 Agent 整理后的内容。"
              aria-label="笔记内容"
            />
            <div class="study-note-composer__actions">
              <button type="button" @click="cancelStudyNoteComposer">取消</button>
              <button type="submit" class="primary-action">
                {{ noteComposer.status === 'draft' ? '保存草稿' : '保存笔记' }}
              </button>
            </div>
          </form>

          <div v-else-if="activeBlockNotes.length === 0" class="study-note-empty">
            <span>当前段落还没有笔记</span>
            <small>选中文本或点击新建，把理解沉淀到学习资产管道。</small>
          </div>

          <div v-else class="study-note-list">
            <article
              v-for="note in activeBlockNotes"
              :key="note.id"
              class="study-note-card"
              :class="[`study-note-card--${note.status}`, { active: note.id === activeNoteId }]">
              <div class="study-note-card__meta">
                <span>{{ note.source === 'agent' ? 'Agent' : '我' }}</span>
                <small>Page {{ note.pageNumber }}</small>
                <small v-if="note.bookmarkId">{{ resolveNoteBookmarkLabel(note) }}</small>
                <mark>{{ noteStatusLabels[note.status] }}</mark>
              </div>
              <h3>{{ note.title }}</h3>
              <p>{{ note.content }}</p>
              <blockquote v-if="note.selectedText">{{ note.selectedText }}</blockquote>
              <div v-if="note.tags.length" class="study-note-tags">
                <span v-for="tag in note.tags" :key="tag">{{ tag }}</span>
              </div>
              <div class="study-note-card__actions">
                <button type="button" @click="jumpToStudyNote(note)">定位</button>
                <button type="button" @click="editStudyNote(note)">编辑</button>
                <button v-if="note.status === 'draft'" type="button" @click="updateStudyNoteStatus(note.id, 'saved')">确认沉淀</button>
                <button v-if="note.status !== 'reviewing'" type="button" @click="updateStudyNoteStatus(note.id, 'reviewing')">加入复习</button>
                <button v-else type="button" @click="updateStudyNoteStatus(note.id, 'mastered')">标记掌握</button>
              </div>
            </article>
          </div>
        </section>

        <section class="agent-conversation agent-conversation--ide">
          <article v-for="message in agentMessages" :key="message.id" :class="`message message--${message.role}`">
            <strong>{{ message.role === 'assistant' ? 'Agent' : '我' }}</strong>
            <p>{{ message.content }}</p>
            <button
              v-if="message.role === 'assistant' && message.id !== 'agent-welcome'"
              type="button"
              class="message-save-note"
              @click="startNoteFromAgentMessage(message)">
              保存为笔记
            </button>
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
            <button type="button" @click="startNoteFromActiveBlock">新建笔记</button>
            <button type="button" @click="askAgent('整理当前段落为笔记草稿')">Agent 整理</button>
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

    <footer v-if="readingDocument" class="asset-pipeline" aria-label="学习资产管道">
      <header class="asset-pipeline__heading">
        <div>
          <strong>学习资产管道</strong>
          <span>{{ totalStudyNoteCount }} 条笔记 · {{ userBookmarks.length }} 个书签 · {{ workspaceStateSaving ? '同步中' : '已加入当前文档' }}</span>
        </div>
        <button type="button" @click="showPlaceholderAction('全部资产抽屉')">查看全部资产</button>
      </header>

      <section
        v-for="column in studyAssetPipeline"
        :key="column.id"
        class="asset-pipeline-column"
        :class="`asset-pipeline-column--${column.tone}`">
        <div class="asset-pipeline-column__title">
          <span>{{ column.label }}</span>
          <small>{{ column.notes.length }}</small>
        </div>
        <p>{{ column.description }}</p>
        <div class="asset-pipeline-cards">
          <article
            v-for="note in column.notes.slice(0, 3)"
            :key="note.id"
            class="asset-pipeline-card"
            :class="{ active: note.id === activeNoteId }"
            @click="openStudyNote(note.id)">
            <strong>{{ note.title }}</strong>
            <span>{{ note.source === 'agent' ? 'Agent' : '我' }} · Page {{ note.pageNumber }}</span>
          </article>
          <button
            v-if="column.notes.length === 0"
            type="button"
            class="asset-pipeline-empty"
            @click="column.id === 'draft' ? askAgent('整理当前段落为笔记草稿') : startNoteFromActiveBlock()">
            {{ column.id === 'draft' ? '让 Agent 整理' : '新增笔记' }}
          </button>
        </div>
      </section>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
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
import PdfLearningCanvas from '@/components/translation/PdfLearningCanvas.vue'
import { showToast } from '@/utils/toast'
import type { TranslationSourceType } from './translationHubData'
import {
  buildDocumentSelectionContext,
  buildIntensiveReadingDocument,
  createTranslationWorkspaceDraftFromParsedDocument,
  loadTranslationWorkspaceDraft,
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
const outlineColumnWidth = ref(280)
const agentColumnWidth = ref(430)
const activeResizeTarget = ref<WorkspaceResizeTarget | null>(null)
const isOutlineCollapsed = ref(false)
const isAgentCollapsed = ref(false)
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
const workspaceStateSaving = ref(false)
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

const outlineSummary = computed(() => {
  const total = outlineTreeItems.value.length || outlinePageItems.value.length
  const noteTotal = studyNotes.value.length
  return `Page ${currentPdfPage.value} · ${total} 个定位 · ${noteTotal} 条笔记`
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

const activeBlockNotes = computed(() => {
  const block = activeBlock.value
  if (!block) return []
  return studyNotes.value.filter((note) => note.blockId === block.id
    || note.elementId === block.elementId
    || (!!activeOutlineItemId.value && note.bookmarkId === activeOutlineItemId.value))
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

const noteComposerContextLabel = computed(() => {
  const context = noteComposer.value.context
  if (!context) return '未选择来源'
  return `Page ${context.pageNumber} · ${context.elementId || context.blockId}`
})

watch(readingDocument, (document) => {
  if (document?.sourceType === 'pdf') {
    documentView.value = 'pdf-canvas'
  }
  targetPdfPage.value = activeBlock.value?.pageNumber || 1
  currentPdfPage.value = targetPdfPage.value
}, { immediate: true })

watch(
  () => String(route.params.id ?? ''),
  (id) => {
    void restoreWorkspaceDocument(id)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
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

async function flushWorkspaceStateSave() {
  if (workspaceStateSaveTimer) {
    clearTimeout(workspaceStateSaveTimer)
    workspaceStateSaveTimer = null
  }
  await persistWorkspaceState()
}

async function restoreWorkspaceDocument(id: string) {
  workspaceStateRestoring = true
  readingDocument.value = null
  activeBlockId.value = ''
  activeOutlineItemId.value = null
  workspaceLoadError.value = ''
  studyNotes.value = []
  userBookmarks.value = []
  activeNoteId.value = null
  collapsedOutlineItemIds.value = new Set()
  noteComposer.value = createEmptyNoteComposer()
  if (!id) {
    workspaceLoadError.value = '缺少翻译 ID。'
    workspaceStateRestoring = false
    return
  }

  workspaceLoading.value = true
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
    readingDocument.value = buildIntensiveReadingDocument(applyLocalDraftDisplayOverrides(draft, localDraft))
    restoreWorkspaceState(persisted.workspaceState ?? null)
    focusRouteStudyNote()
  } catch {
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
      return
    }
    workspaceLoadError.value = '后端知识快照不存在，且没有可兼容恢复的本地草稿。'
  } finally {
    workspaceStateRestoring = false
    workspaceLoading.value = false
  }
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
  const bookmarkId = resolveActiveBookmarkId(input.context)
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
}

function editStudyNote(note: StudyNote) {
  activeNoteId.value = note.id
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
  showToast(composer.status === 'draft' ? '已生成待整理笔记' : '已保存为学习笔记', 'success')
  scheduleWorkspaceStateSave()
}

function cancelStudyNoteComposer() {
  noteComposer.value = createEmptyNoteComposer()
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
.asset-pipeline {
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
input,
textarea {
  font: inherit;
}

.back-button,
.toolbar-actions button,
.reader-status span,
.inline-actions button,
.agent-card-actions button,
.command-actions button,
.asset-pipeline button,
.study-note-panel button,
.message-save-note,
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
  position: relative;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  background: #fbfcfe;
}

.outline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px 10px;
  border-bottom: 1px solid #e7edf3;
  background: linear-gradient(180deg, #ffffff 0%, #fbfcfe 100%);
}

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
  color: #172033;
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

.outline-controls {
  display: grid;
  gap: 8px;
  padding: 10px 12px 12px;
  border-bottom: 1px solid #edf2f7;
  background: #fbfcfe;
}

.outline-search {
  display: grid;
  gap: 5px;
}

.outline-search span {
  color: #667085;
  font-size: 11px;
  font-weight: 750;
}

.outline-search input {
  min-height: 36px;
  width: 100%;
  min-width: 0;
  border: 1px solid #d8e1ea;
  border-radius: 8px;
  padding: 0 11px;
  background: #ffffff;
  color: #172033;
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
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #56657a;
  font-size: 12px;
  font-weight: 750;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, color 0.16s ease;
}

.outline-filter-tabs button.active {
  border-color: #99f6e4;
  background: #f0fdfa;
  color: #0f766e;
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
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.outline-quick-actions button:hover,
.outline-quick-actions button:focus-visible {
  border-color: #14b8a6;
  background: #ecfdf5;
  color: #0f766e;
  outline: none;
}

.outline-quick-actions button.danger:hover,
.outline-quick-actions button.danger:focus-visible {
  border-color: #fecaca;
  background: #fff1f2;
  color: #be123c;
}

.outline-list {
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
  color: #334155;
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
  background: #e3ebf3;
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
  color: #8a5a00;
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
  background: #fff7ed;
  color: #9a3412;
  box-shadow: inset 0 0 0 1px rgba(251, 146, 60, 0.38);
}

.outline-block-button.is-user-bookmark.active::after {
  background: #f97316;
}

.outline-page-button:hover,
.outline-block-button:hover {
  background: #f6faf9;
  color: #0f766e;
}

.outline-page-button.active,
.outline-block-button.active {
  background: #f0fdfa;
  color: #0f766e;
  box-shadow: inset 0 0 0 1px rgba(45, 212, 191, 0.42);
}

.outline-block-button.is-current-page:not(.active) {
  background: #f8fbff;
  color: #1f5f67;
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
  color: #475569;
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
  background: #e0f7ff;
  color: #0369a1;
}

.outline-item-meta .note-count {
  background: #fffbeb;
  color: #b45309;
}

.outline-empty-state {
  display: grid;
  gap: 5px;
  margin: 2px 4px;
  padding: 16px 12px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
  color: #667085;
}

.outline-empty-state strong {
  color: #111827;
  font-size: 13px;
}

.outline-empty-state span {
  font-size: 12px;
  line-height: 1.5;
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
.command-actions {
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

.study-note-panel {
  display: grid;
  gap: 10px;
  padding: 13px 18px;
  border-bottom: 1px solid #edf1f6;
  background: #ffffff;
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
  color: #111827;
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
  background: #f0fdfa;
}

.study-note-source span {
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.study-note-source small {
  min-width: 0;
  overflow: hidden;
  color: #667085;
  font-size: 11px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.study-note-composer input,
.study-note-composer textarea {
  width: 100%;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  padding: 10px;
  background: #f8fafc;
  color: #111827;
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

.study-note-composer__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.study-note-empty {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
}

.study-note-empty span {
  color: #111827;
  font-size: 13px;
  font-weight: 900;
}

.study-note-empty small {
  color: #667085;
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
  border: 1px solid #dbe5ee;
  border-radius: 8px;
  background: #ffffff;
}

.study-note-card.active {
  border-color: #14b8a6;
  box-shadow: inset 3px 0 0 #14b8a6;
}

.study-note-card--draft {
  background: #fffbeb;
}

.study-note-card--reviewing {
  background: #eff6ff;
}

.study-note-card--mastered {
  background: #f0fdf4;
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
  color: #0f766e;
}

.study-note-card__meta small {
  color: #667085;
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
  background: #ecfdf5;
  color: #0f766e;
  font-size: 11px;
  font-weight: 900;
}

.study-note-card__actions button {
  font-size: 12px;
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

.message-save-note {
  justify-self: start;
  min-height: 28px;
  padding: 5px 9px;
  color: #0f766e;
  font-size: 12px;
  cursor: pointer;
}

.agent-command {
  display: grid;
  gap: 8px;
}

.asset-pipeline {
  display: grid;
  grid-template-columns: minmax(180px, 0.75fr) repeat(3, minmax(220px, 1fr));
  gap: 12px;
  min-height: 116px;
  padding: 10px;
  border: 1px solid #dbe5ee;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
}

.asset-pipeline__heading {
  display: grid;
  align-content: space-between;
  gap: 10px;
  min-width: 0;
  padding: 10px 12px;
  border-right: 1px solid #edf1f6;
}

.asset-pipeline__heading div {
  display: grid;
  gap: 4px;
}

.asset-pipeline__heading strong {
  color: #111827;
  font-size: 15px;
  line-height: 1.3;
}

.asset-pipeline__heading span {
  color: #667085;
  font-size: 12px;
  font-weight: 800;
}

.asset-pipeline__heading button {
  justify-self: start;
  min-height: 30px;
  padding: 0 10px;
  cursor: pointer;
}

.asset-pipeline-column {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 6px;
  min-width: 0;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.asset-pipeline-column--warm {
  border-color: #fde68a;
  background: #fffbeb;
}

.asset-pipeline-column--green {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.asset-pipeline-column--blue {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.asset-pipeline-column__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.asset-pipeline-column__title span {
  color: #111827;
  font-size: 13px;
  font-weight: 900;
}

.asset-pipeline-column__title small {
  min-width: 24px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #ffffff;
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
  text-align: center;
}

.asset-pipeline-column p {
  margin: 0;
  color: #667085;
  font-size: 11px;
  line-height: 1.45;
}

.asset-pipeline-cards {
  display: flex;
  min-width: 0;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.asset-pipeline-card,
.asset-pipeline-empty {
  flex: 0 0 170px;
  min-width: 0;
  min-height: 54px;
  cursor: pointer;
}

.asset-pipeline-card {
  display: grid;
  align-content: center;
  gap: 3px;
  padding: 8px 9px;
  border: 1px solid #dbe5ee;
  border-radius: 8px;
  background: #ffffff;
}

.asset-pipeline-card.active {
  border-color: #14b8a6;
  box-shadow: inset 3px 0 0 #14b8a6;
}

.asset-pipeline-card strong,
.asset-pipeline-card span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-pipeline-card strong {
  color: #111827;
  font-size: 12px;
}

.asset-pipeline-card span {
  color: #667085;
  font-size: 11px;
  font-weight: 800;
}

.asset-pipeline-empty {
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: rgb(255 255 255 / 60%);
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
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

  .asset-pipeline {
    grid-template-columns: 1fr;
    overflow: auto;
  }

  .asset-pipeline__heading {
    border-right: 0;
    border-bottom: 1px solid #edf1f6;
  }
}

@media (max-width: 760px) {
  .intensive-workspace-page {
    padding: 16px 14px 112px;
  }

  .asset-pipeline,
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

  .asset-pipeline {
    overflow-x: auto;
  }

  .asset-pipeline-cards {
    flex-direction: column;
  }

  .asset-pipeline-card,
  .asset-pipeline-empty {
    flex-basis: auto;
  }
}
</style>
