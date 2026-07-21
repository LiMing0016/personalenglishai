<template>
  <div
    class="assistant-page"
    :style="assistantPageStyle"
    :class="{
      'assistant-page--drawer-open': assistantDrawerOpen,
      'assistant-page--learning-canvas-open': learningCanvasVisible,
      'assistant-page--compact-learning-canvas': compactLearningCanvas,
      'assistant-page--sidebar-constrained': sidebarConstrained,
      'assistant-page--resizing-sidebar': assistantSidebarResizing,
    }"
  >
    <AssistantSidebar
      :collapsed="!assistantDrawerOpen"
      :requestOpenSidebar="openAssistantDrawer"
      :search-value="searchText"
      :groups="conversationGroups"
      :archived-groups="archivedConversationGroups"
      :folder-groups="folderConversationGroups"
      :active-conversation-id="activeConversationId"
      :folders="projects"
      :archive-dir="archiveDirDraft"
      :default-archive-dir="archiveSettings?.defaultArchiveDir ?? ''"
      :archive-dir-saving="isSavingArchiveDir"
      @new-conversation="createConversation"
      @update:search-value="searchText = $event"
      @close-sidebar="closeAssistantDrawer"
      @select-conversation="selectConversation"
      @rename-conversation="handleRenameConversation"
      @archive-conversation="handleArchiveConversation"
      @restore-conversation="handleRestoreConversation"
      @delete-conversation="handleDeleteConversation"
      @share-conversation="handleShareConversation"
      @pin-conversation="setConversationPinned"
      @move-conversation-to-folder="handleMoveConversationToFolder"
      @create-folder="openCreateFolderOnlyDialog"
      @create-folder-and-move="openCreateFolderDialog"
      @save-archive-dir="handleSaveArchiveDir"
    />

    <button
      v-if="assistantDrawerOpen && !sidebarConstrained"
      type="button"
      class="assistant-sidebar-resize-handle"
      role="separator"
      aria-label="调整助手栏宽度"
      aria-orientation="vertical"
      :aria-valuemin="MIN_ASSISTANT_SIDEBAR_WIDTH"
      :aria-valuemax="MAX_ASSISTANT_SIDEBAR_WIDTH"
      :aria-valuenow="assistantSidebarWidth"
      title="左右拖动调整助手栏宽度"
      @pointerdown="startAssistantSidebarResize"
      @keydown.left.prevent="resizeAssistantSidebarBy(-16)"
      @keydown.right.prevent="resizeAssistantSidebarBy(16)"
      @keydown.home.prevent="setAssistantSidebarWidth(MIN_ASSISTANT_SIDEBAR_WIDTH)"
      @keydown.end.prevent="setAssistantSidebarWidth(MAX_ASSISTANT_SIDEBAR_WIDTH)"
    ></button>

    <button
      v-if="sidebarConstrained && assistantDrawerOpen"
      type="button"
      class="assistant-sidebar-scrim"
      aria-label="收起侧边栏"
      @click="closeAssistantDrawer"
    ></button>

    <div class="assistant-main">
      <header class="main-header">
        <span class="main-title">{{ pageTitle }}</span>
        <span v-if="isLoadingConversations" class="loading-label">同步中</span>
        <div class="header-spacer"></div>
        <button
          v-if="compactLearningCanvas && activeConversation.messages.length > 0 && learningCanvasAvailable"
          ref="learningResultsButtonRef"
          type="button"
          class="learning-results-button"
          aria-controls="learning-asset-canvas"
          :aria-expanded="learningCanvasVisible"
          @click="openCompactLearningCanvas"
        >
          学习成果
          <span v-if="learningAssetDrafts.length" class="learning-results-count">
            {{ learningAssetDrafts.length }}
          </span>
        </button>
      </header>

      <AssistantChatView
        :messages="activeConversation.messages"
        :error-message="errorMessage"
        :can-retry="canRetry"
        :empty-title="emptyTitle"
        :empty-subtitle="emptySubtitle"
        :markdown-theme="markdownTheme"
        :can-append-to-learning-asset="Boolean(learningAssetDraft)"
        :selected-goal="selectedStarterGoal"
        @choose-starter="handleChooseStarter"
        @select-goal="handleSelectStarterGoal"
        @copy-message="handleCopyMessage"
        @retry-message="handleRetryAssistantMessage"
        @retry="retryLastMessage"
        @create-learning-asset="handleCreateLearningAsset"
        @append-to-learning-asset="handleAppendToLearningAsset"
      >
        <template #empty-composer>
          <AssistantComposer
            v-if="activeConversation.messages.length === 0"
            ref="emptyComposerRef"
            class="empty-state-composer"
            :model-value="composerText"
            :loading="isSending"
            :attachments="composerAttachments"
            :assistant-mode="assistantMode"
            :placeholder="learningCanvasAvailable ? '' : undefined"
            @update:model-value="composerText = $event"
            @add-files="handleFileSelect"
            @remove-attachment="removeAttachment"
            @set-assistant-mode="handleSetAssistantMode"
            @send="sendMessage"
          />
        </template>
      </AssistantChatView>

      <div v-if="activeConversation.messages.length > 0" class="composer-dock" :class="{ composerDocked }">
        <AssistantComposer
          :model-value="composerText"
          :loading="isSending"
          :attachments="composerAttachments"
          :assistant-mode="assistantMode"
          :placeholder="learningCanvasAvailable ? '' : undefined"
          @update:model-value="composerText = $event"
          @add-files="handleFileSelect"
          @remove-attachment="removeAttachment"
          @set-assistant-mode="handleSetAssistantMode"
          @send="sendMessage"
        />
      </div>
    </div>

    <button
      v-if="compactLearningCanvas && learningCanvasVisible"
      type="button"
      class="learning-canvas-scrim"
      aria-label="关闭学习成果"
      @click="closeCompactLearningCanvas"
    ></button>

    <LearningAssetCanvas
      v-if="learningCanvasVisible"
      :draft="learningAssetDraft"
      :drafts="learningAssetDrafts"
      :active-draft-id="activeLearningAssetDraftId"
      :candidate-markdown="learningAssetCandidateMarkdown"
      :is-organizing="isLearningAssetOrganizing"
      :save-status="learningAssetSaveStatus"
      :save-status-by-draft-id="learningAssetSaveStatusByDraftId"
      :error-message="learningAssetError"
      :width-px="learningAssetCanvasWidth"
      @close="handleLearningCanvasClose"
      @organize="handleOrganizeLearningAsset"
      @select-draft="setActiveLearningAssetDraft"
      @rename-draft="renameLearningAssetDraft"
      @create-empty-draft="createEmptyLearningAssetDraft"
      @apply-candidate="applyLearningAssetCandidate"
      @cancel-candidate="learningAssetCandidateMarkdown = ''"
      @update:title="updateLearningAssetTitle"
      @update:content-markdown="updateLearningAssetContent"
      @resize:width="setLearningAssetCanvasWidth"
    />

    <div v-if="folderDialogMode" class="folder-dialog-backdrop" role="presentation">
      <form class="folder-dialog" @submit.prevent="handleSubmitFolderDialog">
        <h2 class="folder-dialog-title">创建文件夹</h2>
        <p class="folder-dialog-copy">{{ folderDialogCopy }}</p>
        <input
          v-model="newFolderName"
          class="folder-dialog-input"
          type="text"
          placeholder="文件夹名称"
          autofocus
        />
        <div class="folder-dialog-actions">
          <button type="button" class="folder-dialog-button" @click="closeCreateFolderDialog">
            取消
          </button>
          <button type="submit" class="folder-dialog-button folder-dialog-button--primary">
            {{ folderDialogMode === 'move' ? '创建并移动' : '创建文件夹' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AssistantChatView from '@/components/assistant/AssistantChatView.vue'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'
import type { AssistantStarterGoalId } from '@/components/assistant/AssistantStarterCards.vue'
import LearningAssetCanvas from '@/components/assistant/LearningAssetCanvas.vue'
import AssistantSidebar from '@/components/assistant/AssistantSidebar.vue'
import { assistantApi, type AssistantArchiveSettingsDto } from '@/api/assistant'
import {
  createLearningNote,
  EmptyApiDataError,
  getLearningNote,
  organizeLearningAssetMarkdown,
  updateLearningNote,
} from '@/api/learningNotes'
import type { LearningNoteDto, LearningNotePayload } from '@/api/learningNotes'
import { showToast } from '@/utils/toast'
import type { AssistantLearningAssetSelection } from '@/components/assistant/AssistantChatView.vue'
import type { AssistantAttachmentSource } from './assistantAttachmentRules.ts'
import type { AssistantMode } from './assistantMock.ts'
import {
  PENDING_ASSISTANT_PROMPT_KEY,
  PENDING_ASSISTANT_SELECTION_KEY,
  type PendingAssistantSelection,
  parsePendingAssistantSelection,
} from './assistantMessageActions.ts'
import {
  readAssistantMarkdownTheme,
  type AssistantMarkdownTheme,
} from './assistantMarkdownTheme.ts'
import {
  DEFAULT_ASSISTANT_SIDEBAR_WIDTH,
  MAX_ASSISTANT_SIDEBAR_WIDTH,
  MIN_ASSISTANT_SIDEBAR_WIDTH,
  buildAssistantConversationGroups,
  clampAssistantSidebarWidth,
  shouldAutoCollapseAssistantSidebar,
  shouldUseCompactLearningCanvas,
} from './assistantSidebarState.ts'
import { createAssistantState } from './assistantState.ts'
import { createLearningAssetDraftStore, type LearningAssetWorkspace } from './learningAssetDraftStore.ts'
import {
  createLearningAssetDraft,
  type LearningAssetCopilotRequest,
  type LearningAssetDraft,
  type LearningAssetType,
} from '../../types/learningAssets.ts'

type LearningAssetSaveStatus = 'unsaved' | 'saving' | 'saved' | 'failed'

const {
  conversations,
  archivedConversations,
  projects,
  activeConversationId,
  activeConversation,
  isLoadingConversations,
  composerText,
  composerAttachments,
  assistantMode,
  searchText,
  isSending,
  errorMessage,
  canRetry,
  applyStarter,
  addAttachments,
  removeAttachment,
  setAssistantMode,
  loadRemoteState,
  createConversation,
  selectConversation,
  renameConversation,
  setConversationPinned,
  archiveConversation,
  restoreConversation,
  deleteConversation,
  moveConversation,
  shareConversation,
  createProject,
  sendMessage,
  retryLastMessage,
  retryAssistantMessage,
  setPendingSelection,
} = createAssistantState({ remote: true })

const pageTitle = '学习助手'
const emptyTitle = '今天想完成什么？'
const emptySubtitle = '先选一个学习目标，再把内容发给我。'
const composerDocked = true
const LEARNING_ASSET_CANVAS_WIDTH_KEY = 'peai:assistant:learning-asset-canvas-width'
const LEARNING_NOTE_QUERY_KEY = 'learningNote'
const LEARNING_ASSET_AUTO_SAVE_DELAY_MS = 1200
const MIN_LEARNING_ASSET_CANVAS_WIDTH = 360
const MAX_LEARNING_ASSET_CANVAS_WIDTH = 720
const learningAssetEmptyTitles = {
  vocabulary: '未命名单词',
  grammar: '未命名语法笔记',
  sentence: '未命名句子笔记',
  expression: '未命名笔记',
} satisfies Record<LearningAssetType, string>
const learningAssetToastLabels = {
  vocabulary: '单词卡',
  grammar: '语法笔记',
  sentence: '句子笔记',
  expression: '空白笔记',
} satisfies Record<LearningAssetType, string>
const route = useRoute()
const router = useRouter()
const injectedAssistantDrawerOpen = inject<Ref<boolean> | null>('assistantDrawerOpen', null)
const assistantDrawerOpen = ref(injectedAssistantDrawerOpen?.value ?? false)
const viewportWidth = ref(readViewportWidth())
const assistantSidebarWidth = ref(DEFAULT_ASSISTANT_SIDEBAR_WIDTH)
const assistantSidebarResizing = ref(false)
let assistantSidebarResizePageLeft = 0
if (injectedAssistantDrawerOpen) {
  watch(injectedAssistantDrawerOpen, (value) => {
    assistantDrawerOpen.value = value
  })
}
const folderDialogMode = ref<'create' | 'move' | null>(null)
const pendingMoveConversationId = ref<string | null>(null)
const newFolderName = ref('')
const markdownTheme = ref<AssistantMarkdownTheme>(readAssistantMarkdownTheme())
const selectedStarterGoal = ref<AssistantStarterGoalId | null>(null)
const emptyComposerRef = ref<InstanceType<typeof AssistantComposer> | null>(null)
const learningResultsButtonRef = ref<HTMLButtonElement | null>(null)
const archiveSettings = ref<AssistantArchiveSettingsDto | null>(null)
const archiveDirDraft = ref('')
const isSavingArchiveDir = ref(false)
const learningAssetDraftStore = createLearningAssetDraftStore()
const learningAssetDrafts = ref<LearningAssetDraft[]>([])
const activeLearningAssetDraftId = ref('')
const learningAssetDraft = computed(() =>
  learningAssetDrafts.value.find((draft) => draft.draftId === activeLearningAssetDraftId.value)
    ?? learningAssetDrafts.value[0]
    ?? null,
)
const learningCanvasAvailable = computed(() => assistantMode.value === 'learning' || Boolean(learningAssetDraft.value))
const compactLearningCanvas = computed(() => shouldUseCompactLearningCanvas(viewportWidth.value))
const compactLearningCanvasOpen = ref(false)
const learningCanvasVisible = computed(() => (
  learningCanvasAvailable.value
  && (!compactLearningCanvas.value || compactLearningCanvasOpen.value)
))
const sidebarConstrained = computed(() => shouldAutoCollapseAssistantSidebar({
  learningCanvasOpen: learningCanvasVisible.value,
  viewportWidth: viewportWidth.value,
}))
const learningAssetCandidateMarkdownByDraftId = ref<Record<string, string>>({})
const learningAssetCandidateMarkdown = computed({
  get() {
    return activeLearningAssetDraftId.value
      ? learningAssetCandidateMarkdownByDraftId.value[activeLearningAssetDraftId.value] ?? ''
      : ''
  },
  set(value: string) {
    if (!activeLearningAssetDraftId.value) return
    learningAssetCandidateMarkdownByDraftId.value = {
      ...learningAssetCandidateMarkdownByDraftId.value,
      [activeLearningAssetDraftId.value]: value,
    }
  },
})
const isLearningAssetOrganizing = ref(false)
const isLearningAssetSaving = ref(false)
const learningAssetSaveStatusByDraftId = ref<Record<string, LearningAssetSaveStatus>>({})
const learningAssetSaveStatus = computed(() =>
  activeLearningAssetDraftId.value
    ? learningAssetSaveStatusByDraftId.value[activeLearningAssetDraftId.value] ?? 'unsaved'
    : 'unsaved',
)
const learningAssetError = ref('')
const learningAssetCanvasWidth = ref(readLearningAssetCanvasWidth())

let learningAssetAutoSaveTimer: ReturnType<typeof setTimeout> | null = null
let loadingLearningNoteUid = ''

const assistantPageStyle = computed(() => ({
  '--assistant-sidebar-width': `${assistantSidebarWidth.value}px`,
  '--learning-canvas-width': `${learningAssetCanvasWidth.value}px`,
}))

const folderDialogCopy = computed(() =>
  folderDialogMode.value === 'move'
    ? '输入文件夹名称，当前对话会移动到这个文件夹。'
    : '文件夹可以用来整理对话，让相关学习内容更容易找回。',
)

function handleFileSelect(files: File[], source: AssistantAttachmentSource) {
  addAttachments(files, source)
}

function focusEmptyComposer() {
  void nextTick(() => emptyComposerRef.value?.focus())
}

function handleSelectStarterGoal(goalId: AssistantStarterGoalId) {
  selectedStarterGoal.value = goalId
  focusEmptyComposer()
}

function handleChooseStarter(prompt: string) {
  applyStarter(prompt)
  focusEmptyComposer()
}

function handleSetAssistantMode(mode: AssistantMode) {
  setAssistantMode(mode)
  if (mode === 'learning') {
    learningAssetError.value = ''
    openCompactLearningCanvas()
  }
}

function openCompactLearningCanvas() {
  if (!compactLearningCanvas.value) return
  compactLearningCanvasOpen.value = true
  void nextTick(() => document.getElementById('learning-asset-canvas')?.focus())
}

function closeCompactLearningCanvas() {
  if (!compactLearningCanvasOpen.value) return
  compactLearningCanvasOpen.value = false
  void nextTick(() => learningResultsButtonRef.value?.focus())
}

function handleLearningCanvasClose() {
  if (compactLearningCanvas.value) {
    closeCompactLearningCanvas()
    return
  }
  closeLearningAssetCanvas()
}

function handleLearningCanvasEscape(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !compactLearningCanvas.value || !learningCanvasVisible.value) return
  event.preventDefault()
  closeCompactLearningCanvas()
}

function applyPendingAssistantPrompt(prompt: string, selection?: PendingAssistantSelection | null) {
  composerText.value = prompt
  if (selection) {
    setPendingSelection(selection)
  }
}

function closeAssistantDrawer() {
  assistantDrawerOpen.value = false
  if (injectedAssistantDrawerOpen) {
    injectedAssistantDrawerOpen.value = false
  }
}

function openAssistantDrawer() {
  assistantDrawerOpen.value = true
  if (injectedAssistantDrawerOpen) {
    injectedAssistantDrawerOpen.value = true
  }
}

function readViewportWidth() {
  return typeof window === 'undefined' ? Number.POSITIVE_INFINITY : window.innerWidth
}

function handleViewportResize() {
  viewportWidth.value = window.innerWidth
}

function setAssistantSidebarWidth(width: number) {
  assistantSidebarWidth.value = clampAssistantSidebarWidth(width)
}

function resizeAssistantSidebarBy(delta: number) {
  setAssistantSidebarWidth(assistantSidebarWidth.value + delta)
}

function handleAssistantSidebarPointerMove(event: PointerEvent) {
  setAssistantSidebarWidth(event.clientX - assistantSidebarResizePageLeft)
}

function stopAssistantSidebarResize() {
  assistantSidebarResizing.value = false
  window.removeEventListener('pointermove', handleAssistantSidebarPointerMove)
  window.removeEventListener('pointerup', stopAssistantSidebarResize)
  window.removeEventListener('pointercancel', stopAssistantSidebarResize)
}

function startAssistantSidebarResize(event: PointerEvent) {
  if (event.button !== 0) return
  const target = event.currentTarget as HTMLElement
  assistantSidebarResizePageLeft = target.closest('.assistant-page')?.getBoundingClientRect().left ?? 0
  assistantSidebarResizing.value = true
  event.preventDefault()
  handleAssistantSidebarPointerMove(event)
  window.addEventListener('pointermove', handleAssistantSidebarPointerMove)
  window.addEventListener('pointerup', stopAssistantSidebarResize)
  window.addEventListener('pointercancel', stopAssistantSidebarResize)
}

watch(sidebarConstrained, (constrained) => {
  if (constrained) {
    closeAssistantDrawer()
  }
}, { immediate: true })

function restoreLearningAssetDraft(conversationId: string) {
  clearLearningAssetAutoSaveTimer()
  const workspace = learningAssetDraftStore.readWorkspace(conversationId)
  learningAssetDrafts.value = workspace?.drafts ?? []
  activeLearningAssetDraftId.value = workspace?.activeDraftId ?? ''
  learningAssetCandidateMarkdownByDraftId.value = {}
  learningAssetError.value = ''
  learningAssetSaveStatusByDraftId.value = buildLearningAssetSaveStatusMap(learningAssetDrafts.value)
  const activeDraft = learningAssetDrafts.value.find((draft) => draft.draftId === activeLearningAssetDraftId.value)
    ?? learningAssetDrafts.value[0]
  if (activeDraft && !activeDraft.noteUid) {
    queueAutoSaveLearningAsset(activeDraft)
  }
}

function persistLearningAssetDraft(
  nextDraft: LearningAssetDraft,
  options: { queueAutoSave?: boolean; preserveActiveDraft?: boolean } = {},
) {
  const draftIndex = learningAssetDrafts.value.findIndex((draft) => draft.draftId === nextDraft.draftId)
  const nextDrafts = [...learningAssetDrafts.value]
  if (draftIndex >= 0) {
    nextDrafts[draftIndex] = nextDraft
  } else {
    nextDrafts.push(nextDraft)
  }
  learningAssetDrafts.value = nextDrafts
  if (!options.preserveActiveDraft) {
    activeLearningAssetDraftId.value = nextDraft.draftId
  }
  persistLearningAssetWorkspace()
  if (options.queueAutoSave) {
    queueAutoSaveLearningAsset(nextDraft)
  }
}

function persistLearningAssetWorkspace() {
  const workspace = buildLearningAssetWorkspace()
  if (!workspace) return
  learningAssetDraftStore.saveWorkspace(workspace)
}

function buildLearningAssetWorkspace(): LearningAssetWorkspace | null {
  if (learningAssetDrafts.value.length === 0) return null
  const activeDraftId = learningAssetDrafts.value.some((draft) => draft.draftId === activeLearningAssetDraftId.value)
    ? activeLearningAssetDraftId.value
    : learningAssetDrafts.value[0].draftId
  return {
    conversationId: activeConversationId.value,
    activeDraftId,
    drafts: learningAssetDrafts.value,
  }
}

function buildLearningAssetSaveStatusMap(drafts: LearningAssetDraft[]) {
  return drafts.reduce<Record<string, LearningAssetSaveStatus>>((acc, draft) => {
    acc[draft.draftId] = draft.noteUid ? 'saved' : 'unsaved'
    return acc
  }, {})
}

function setLearningAssetSaveStatus(draftId: string, status: LearningAssetSaveStatus) {
  learningAssetSaveStatusByDraftId.value = {
    ...learningAssetSaveStatusByDraftId.value,
    [draftId]: status,
  }
}

function clearLearningAssetAutoSaveTimer() {
  if (!learningAssetAutoSaveTimer) return
  clearTimeout(learningAssetAutoSaveTimer)
  learningAssetAutoSaveTimer = null
}

function queueAutoSaveLearningAsset(draft = learningAssetDraft.value) {
  clearLearningAssetAutoSaveTimer()
  if (!draft) return
  setLearningAssetSaveStatus(draft.draftId, 'unsaved')
  learningAssetAutoSaveTimer = setTimeout(() => {
    learningAssetAutoSaveTimer = null
    void saveLearningAssetDraft({ showSuccessToast: false, draftId: draft.draftId })
  }, LEARNING_ASSET_AUTO_SAVE_DELAY_MS)
}

function clampLearningAssetCanvasWidth(width: number) {
  return Math.min(
    MAX_LEARNING_ASSET_CANVAS_WIDTH,
    Math.max(MIN_LEARNING_ASSET_CANVAS_WIDTH, Math.round(width)),
  )
}

function readLearningAssetCanvasWidth() {
  if (typeof localStorage === 'undefined') return 420
  const parsedWidth = Number(localStorage.getItem(LEARNING_ASSET_CANVAS_WIDTH_KEY))
  return Number.isFinite(parsedWidth) ? clampLearningAssetCanvasWidth(parsedWidth) : 420
}

function setLearningAssetCanvasWidth(width: number) {
  const nextWidth = clampLearningAssetCanvasWidth(width)
  learningAssetCanvasWidth.value = nextWidth
  if (typeof localStorage === 'undefined') return
  localStorage.setItem(LEARNING_ASSET_CANVAS_WIDTH_KEY, String(nextWidth))
}

function handleCreateLearningAsset(selection: AssistantLearningAssetSelection) {
  clearLearningAssetRouteQuery()
  const draft = createLearningAssetDraft({
    conversationId: activeConversationId.value,
    messageId: selection.messageId,
    type: selection.type ?? 'vocabulary',
    title: selection.selectedText,
    selectedText: selection.selectedText,
    contextText: selection.contextText,
  })
  persistLearningAssetDraft(draft, { queueAutoSave: true })
  learningAssetCandidateMarkdownByDraftId.value = {
    ...learningAssetCandidateMarkdownByDraftId.value,
    [draft.draftId]: '',
  }
  learningAssetError.value = ''
  setLearningAssetSaveStatus(draft.draftId, 'unsaved')
  openCompactLearningCanvas()
  showToast(`已打开${learningAssetToastLabels[draft.type]}画布`, 'success')
}

function handleAppendToLearningAsset(selection: AssistantLearningAssetSelection) {
  if (!learningAssetDraft.value) return
  const nextContent = appendSelectedTextMarkdown(learningAssetDraft.value.contentMarkdown, selection.selectedText)
  updateLearningAssetContent(nextContent)
  showToast('已加入当前笔记', 'success')
}

function createEmptyLearningAssetDraft(type: LearningAssetType) {
  clearLearningAssetRouteQuery()
  const title = learningAssetEmptyTitles[type]
  const draft = createLearningAssetDraft({
    conversationId: activeConversationId.value,
    type,
    title,
    selectedText: title,
    contextText: '',
  })
  persistLearningAssetDraft(draft, { queueAutoSave: true })
  setLearningAssetSaveStatus(draft.draftId, 'unsaved')
  openCompactLearningCanvas()
  showToast(`已新建${learningAssetToastLabels[type]}`, 'success')
}

function setActiveLearningAssetDraft(draftId: string) {
  if (!learningAssetDrafts.value.some((draft) => draft.draftId === draftId)) return
  activeLearningAssetDraftId.value = draftId
  persistLearningAssetWorkspace()
}

function renameLearningAssetDraft(draftId: string, title: string) {
  const draft = learningAssetDrafts.value.find((item) => item.draftId === draftId)
  const normalizedTitle = title.trim()
  if (!draft || !normalizedTitle) return
  persistLearningAssetDraft({
    ...draft,
    title: normalizedTitle,
    updatedAt: Date.now(),
  }, {
    queueAutoSave: true,
    preserveActiveDraft: draft.draftId !== activeLearningAssetDraftId.value,
  })
}

function closeLearningAssetCanvas() {
  clearLearningAssetAutoSaveTimer()
  clearLearningAssetRouteQuery()
  if (assistantMode.value === 'learning') {
    setAssistantMode('default')
  }
  learningAssetDrafts.value = []
  activeLearningAssetDraftId.value = ''
  learningAssetCandidateMarkdownByDraftId.value = {}
  learningAssetSaveStatusByDraftId.value = {}
  learningAssetError.value = ''
}

function updateLearningAssetTitle(title: string) {
  if (!learningAssetDraft.value) return
  persistLearningAssetDraft({
    ...learningAssetDraft.value,
    title,
    updatedAt: Date.now(),
  }, { queueAutoSave: true })
}

function updateLearningAssetContent(contentMarkdown: string) {
  if (!learningAssetDraft.value) return
  persistLearningAssetDraft({
    ...learningAssetDraft.value,
    contentMarkdown,
    updatedAt: Date.now(),
  }, { queueAutoSave: true })
}

async function handleOrganizeLearningAsset(request: LearningAssetCopilotRequest) {
  const draft = learningAssetDraft.value
  if (!draft || isLearningAssetOrganizing.value) return
  isLearningAssetOrganizing.value = true
  learningAssetError.value = ''
  try {
    const result = await organizeLearningAssetMarkdown({
      type: draft.type,
      title: draft.title,
      selectedText: draft.selectedText,
      contextText: draft.contextText,
      currentMarkdown: draft.contentMarkdown,
      mode: request.action === 'format' ? 'format' : 'create',
      action: request.action,
      instruction: request.instruction,
    })
    learningAssetCandidateMarkdownByDraftId.value = {
      ...learningAssetCandidateMarkdownByDraftId.value,
      [draft.draftId]: result.candidateMarkdown,
    }
  } catch (error) {
    learningAssetError.value = readApiErrorMessage(error, 'Copilot 处理失败')
  } finally {
    isLearningAssetOrganizing.value = false
  }
}

function applyLearningAssetCandidate(mode: 'replace' | 'append' | 'fill') {
  if (!learningAssetDraft.value || !learningAssetCandidateMarkdown.value) return
  const candidateMarkdown = learningAssetCandidateMarkdown.value.trim()
  const nextContent = mode === 'append'
    ? appendLearningAssetMarkdown(learningAssetDraft.value.contentMarkdown, candidateMarkdown)
    : candidateMarkdown
  updateLearningAssetContent(nextContent)
  learningAssetCandidateMarkdown.value = ''
}

function appendLearningAssetMarkdown(currentMarkdown: string, candidateMarkdown: string) {
  const base = currentMarkdown.trimEnd()
  const supplement = [
    '---',
    '',
    '## Copilot 补充',
    '',
    candidateMarkdown,
  ].join('\n')
  return base ? `${base}\n\n${supplement}` : supplement
}

function appendSelectedTextMarkdown(currentMarkdown: string, selectedText: string) {
  const normalizedSelectedText = selectedText.trim()
  if (!normalizedSelectedText) return currentMarkdown
  const base = currentMarkdown.trimEnd()
  return base ? `${base}\n\n${normalizedSelectedText}` : normalizedSelectedText
}

function buildLearningNotePayload(draft: LearningAssetDraft): LearningNotePayload | null {
  const title = draft.title.trim()
  const contentMarkdown = draft.contentMarkdown.trim()
  if (!title || !contentMarkdown) return null
  return {
    type: draft.type,
    title,
    contentMarkdown,
    structuredPayload: draft.structuredPayload,
    sourceConversationId: draft.sourceConversationId,
    sourceMessageId: draft.sourceMessageId,
    sourceText: draft.sourceText,
  }
}

function readLearningAssetDraftById(draftId?: string) {
  return draftId
    ? learningAssetDrafts.value.find((draft) => draft.draftId === draftId) ?? null
    : learningAssetDraft.value
}

async function saveLearningAssetDraft({ showSuccessToast, draftId }: { showSuccessToast: boolean; draftId?: string }) {
  const draft = readLearningAssetDraftById(draftId)
  if (!draft || isLearningAssetSaving.value) return
  const payload = buildLearningNotePayload(draft)
  if (!payload) {
    learningAssetError.value = '标题和正文不能为空'
    setLearningAssetSaveStatus(draft.draftId, 'failed')
    return
  }

  clearLearningAssetAutoSaveTimer()
  isLearningAssetSaving.value = true
  setLearningAssetSaveStatus(draft.draftId, 'saving')
  learningAssetError.value = ''
  try {
    const saved = await saveLearningAssetPayload(draft, payload)
    const savedDraft = {
      ...draft,
      noteUid: saved.noteUid,
      title: saved.title,
      contentMarkdown: saved.contentMarkdown,
      structuredPayload: saved.structuredPayload,
      updatedAt: Date.now(),
    }
    persistLearningAssetDraft(savedDraft, {
      preserveActiveDraft: Boolean(draftId && draftId !== activeLearningAssetDraftId.value),
    })
    setLearningAssetSaveStatus(savedDraft.draftId, 'saved')
    if (showSuccessToast) {
      showToast('学习资产已保存', 'success')
    }
  } catch (error) {
    learningAssetError.value = readApiErrorMessage(error, '保存学习资产失败')
    setLearningAssetSaveStatus(draft.draftId, 'failed')
  } finally {
    isLearningAssetSaving.value = false
  }
}

async function saveLearningAssetPayload(
  draft: LearningAssetDraft,
  payload: LearningNotePayload,
): Promise<LearningNoteDto> {
  if (!draft.noteUid) {
    return createLearningNote(payload)
  }

  try {
    return await updateLearningNote(draft.noteUid, payload)
  } catch (error) {
    if (!(error instanceof EmptyApiDataError)) {
      throw error
    }

    return {
      ...payload,
      noteUid: draft.noteUid,
      status: 'active',
      updatedAt: new Date().toISOString(),
    }
  }
}

function createLearningAssetDraftFromNote(note: LearningNoteDto): LearningAssetDraft {
  return {
    draftId: note.noteUid,
    noteUid: note.noteUid,
    type: note.type,
    title: note.title,
    contentMarkdown: note.contentMarkdown,
    structuredPayload: note.structuredPayload ?? null,
    sourceConversationId: note.sourceConversationId || activeConversationId.value,
    sourceMessageId: note.sourceMessageId,
    sourceText: note.sourceText,
    selectedText: note.title,
    contextText: note.sourceText || '',
    updatedAt: Date.now(),
  }
}

async function openLearningAssetFromNoteUid(noteUid: string) {
  const normalizedNoteUid = noteUid.trim()
  if (!normalizedNoteUid || loadingLearningNoteUid === normalizedNoteUid) return
  loadingLearningNoteUid = normalizedNoteUid
  learningAssetError.value = ''
  try {
    const note = await getLearningNote(normalizedNoteUid)
    const draft = createLearningAssetDraftFromNote(note)
    persistLearningAssetDraft(draft)
    learningAssetCandidateMarkdownByDraftId.value = {
      ...learningAssetCandidateMarkdownByDraftId.value,
      [draft.draftId]: '',
    }
    setLearningAssetSaveStatus(draft.draftId, 'saved')
    openCompactLearningCanvas()
    showToast('已打开学习资产画布', 'success')
  } catch (error) {
    learningAssetError.value = readApiErrorMessage(error, '打开学习资产失败')
  } finally {
    loadingLearningNoteUid = ''
  }
}

function clearLearningAssetRouteQuery() {
  if (!route.query[LEARNING_NOTE_QUERY_KEY]) return
  const nextQuery = { ...route.query }
  delete nextQuery[LEARNING_NOTE_QUERY_KEY]
  void router.replace({ query: nextQuery })
}

onMounted(() => {
  handleViewportResize()
  void loadRemoteState()
  void loadArchiveSettings()
  const routeLearningNoteUid = readRouteLearningNoteUid(route.query[LEARNING_NOTE_QUERY_KEY])
  if (routeLearningNoteUid) {
    void openLearningAssetFromNoteUid(routeLearningNoteUid)
  }
  const pendingPrompt = sessionStorage.getItem(PENDING_ASSISTANT_PROMPT_KEY)
  const pendingSelection = parsePendingAssistantSelection(
    sessionStorage.getItem(PENDING_ASSISTANT_SELECTION_KEY),
  )
  if (pendingPrompt) {
    applyPendingAssistantPrompt(pendingPrompt, pendingSelection)
    sessionStorage.removeItem(PENDING_ASSISTANT_PROMPT_KEY)
    sessionStorage.removeItem(PENDING_ASSISTANT_SELECTION_KEY)
  }
  window.addEventListener('peai:assistant:use-prompt', handlePendingPromptEvent)
  window.addEventListener('resize', handleViewportResize)
  document.addEventListener('keydown', handleLearningCanvasEscape)
})

onBeforeUnmount(() => {
  clearLearningAssetAutoSaveTimer()
  stopAssistantSidebarResize()
  window.removeEventListener('peai:assistant:use-prompt', handlePendingPromptEvent)
  window.removeEventListener('resize', handleViewportResize)
  document.removeEventListener('keydown', handleLearningCanvasEscape)
})

watch(activeConversationId, (conversationId) => {
  selectedStarterGoal.value = null
  compactLearningCanvasOpen.value = false
  if (readRouteLearningNoteUid(route.query[LEARNING_NOTE_QUERY_KEY])) return
  restoreLearningAssetDraft(conversationId)
}, { immediate: true })

watch(learningCanvasAvailable, (available) => {
  if (!available) compactLearningCanvasOpen.value = false
})

watch(() => route.query[LEARNING_NOTE_QUERY_KEY], (value) => {
  const noteUid = readRouteLearningNoteUid(value)
  if (noteUid) {
    void openLearningAssetFromNoteUid(noteUid)
  }
})

function readRouteLearningNoteUid(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  return typeof raw === 'string' ? raw.trim() : ''
}

function handlePendingPromptEvent(event: Event) {
  const detail = (event as CustomEvent<string | { prompt?: string; selection?: PendingAssistantSelection }>).detail
  const prompt = typeof detail === 'string' ? detail : detail?.prompt
  if (typeof prompt !== 'string' || !prompt.trim()) return
  const selection = typeof detail === 'string' ? null : detail.selection
  applyPendingAssistantPrompt(prompt, selection)
  sessionStorage.removeItem(PENDING_ASSISTANT_PROMPT_KEY)
  sessionStorage.removeItem(PENDING_ASSISTANT_SELECTION_KEY)
}

async function handleRenameConversation(id: string) {
  const conversation = conversations.value.find((item) => item.id === id)
  const nextTitle = window.prompt('重命名对话', conversation?.title ?? '')
  if (nextTitle === null) return
  try {
    await renameConversation(id, nextTitle)
    showToast('已重命名', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '重命名失败', 'error')
  }
}

async function handleArchiveConversation(id: string) {
  try {
    await archiveConversation(id)
    showToast('已归档', 'success')
  } catch (error) {
    showToast(readApiErrorMessage(error, '归档失败'), 'error')
  }
}

async function loadArchiveSettings() {
  try {
    const settings = await assistantApi.getArchiveSettings()
    archiveSettings.value = settings
    archiveDirDraft.value = settings.archiveDir
  } catch {
    archiveSettings.value = null
    archiveDirDraft.value = ''
  }
}

async function handleSaveArchiveDir(value: string) {
  if (isSavingArchiveDir.value) return
  isSavingArchiveDir.value = true
  try {
    const settings = await assistantApi.updateArchiveSettings(value)
    archiveSettings.value = settings
    archiveDirDraft.value = settings.archiveDir
    showToast('归档目录已保存', 'success')
  } catch (error) {
    showToast(readApiErrorMessage(error, '归档目录保存失败'), 'error')
  } finally {
    isSavingArchiveDir.value = false
  }
}

async function handleRestoreConversation(id: string) {
  try {
    await restoreConversation(id)
    showToast('已取消归档', 'success')
  } catch (error) {
    showToast(readApiErrorMessage(error, '取消归档失败'), 'error')
  }
}

async function handleDeleteConversation(id: string) {
  if (!window.confirm('删除后当前列表将不再显示这个对话。确定删除吗？')) return
  try {
    await deleteConversation(id)
    showToast('已删除', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '删除失败', 'error')
  }
}

async function handleShareConversation(id: string) {
  try {
    const share = await shareConversation(id)
    const url = `${window.location.origin}${share.sharePath}`
    await navigator.clipboard?.writeText(url)
    showToast('分享链接已复制', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '分享失败', 'error')
  }
}

async function handleCopyMessage(content: string) {
  try {
    if (!navigator.clipboard) {
      throw new Error('Clipboard unavailable')
    }
    await navigator.clipboard?.writeText(content)
    showToast('已复制', 'success')
  } catch {
    showToast('复制失败', 'error')
  }
}

async function handleRetryAssistantMessage(messageId: string) {
  try {
    await retryAssistantMessage(messageId)
  } catch (error) {
    showToast(error instanceof Error ? error.message : '重试失败', 'error')
  }
}

async function handleMoveConversationToFolder(id: string, folderId: number | null) {
  try {
    await moveConversation(id, folderId)
    showToast(folderId === null ? '已移出文件夹' : '已移动到文件夹', 'success')
  } catch (error) {
    showToast(error instanceof Error ? error.message : '移动失败', 'error')
  }
}

function openCreateFolderOnlyDialog() {
  folderDialogMode.value = 'create'
  pendingMoveConversationId.value = null
  newFolderName.value = ''
}

function openCreateFolderDialog(id: string) {
  folderDialogMode.value = 'move'
  pendingMoveConversationId.value = id
  newFolderName.value = ''
}

function closeCreateFolderDialog() {
  folderDialogMode.value = null
  pendingMoveConversationId.value = null
  newFolderName.value = ''
}

async function handleSubmitFolderDialog() {
  const name = newFolderName.value.trim()
  if (!folderDialogMode.value || !name) return

  try {
    const existing = projects.value.find((project) => project.name === name)
    const folder = existing ?? await createProject(name)

    if (folderDialogMode.value === 'move') {
      const conversationId = pendingMoveConversationId.value
      if (!conversationId) return
      await moveConversation(conversationId, folder.id)
      showToast('已移动到文件夹', 'success')
    } else {
      showToast('已创建文件夹', 'success')
    }

    closeCreateFolderDialog()
  } catch (error) {
    showToast(error instanceof Error ? error.message : '创建文件夹失败', 'error')
  }
}

function readApiErrorMessage(error: unknown, fallback: string) {
  const responseMessage = (error as { response?: { data?: { message?: string } } })?.response?.data?.message
  if (typeof responseMessage === 'string' && responseMessage.trim()) {
    return responseMessage
  }
  return error instanceof Error && error.message ? error.message : fallback
}

const filteredConversations = computed(() => {
  const keyword = searchText.value.trim()
  return keyword
    ? conversations.value.filter((conversation) =>
        `${conversation.title} ${conversation.summary}`.includes(keyword),
      )
    : conversations.value
})

const filteredArchivedConversations = computed(() => {
  const keyword = searchText.value.trim()
  return keyword
    ? archivedConversations.value.filter((conversation) =>
        `${conversation.title} ${conversation.summary}`.includes(keyword),
      )
    : archivedConversations.value
})

const conversationGroups = computed(() =>
  buildAssistantConversationGroups(filteredConversations.value.filter((conversation) => (
    conversation.projectId === null || conversation.projectId === undefined
  ))),
)

const archivedConversationGroups = computed(() =>
  buildAssistantConversationGroups(filteredArchivedConversations.value),
)

const folderConversationGroups = computed(() =>
  projects.value.map((folder) => {
    const folderConversations = filteredConversations.value.filter(
      (conversation) => conversation.projectId === folder.id,
    )
    const groups = buildAssistantConversationGroups(folderConversations)
    return {
      id: folder.id,
      name: folder.name,
      conversationCount: groups.reduce((total, group) => total + group.conversations.length, 0),
      groups,
    }
  }),
)
</script>

<style scoped>
.assistant-page {
  --app-rail-width: 0px;
  --assistant-sidebar-width: 218px;
  --assistant-sidebar-collapsed-width: 72px;
  --assistant-sidebar-current-width: var(--assistant-sidebar-collapsed-width);
  --learning-canvas-width: 420px;
  --learning-canvas-current-width: 0px;
  position: relative;
  display: flex;
  flex: 1;
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  background: #f8fafc;
}

.assistant-page--drawer-open {
  --assistant-sidebar-current-width: var(--assistant-sidebar-width);
}

.assistant-sidebar-resize-handle {
  position: absolute;
  top: 0;
  bottom: 0;
  left: calc(var(--assistant-sidebar-width) - 5px);
  z-index: 45;
  width: 10px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: col-resize;
  touch-action: none;
}

.assistant-sidebar-resize-handle::after {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 4px;
  width: 2px;
  background: #cbd5e1;
  content: '';
  transition: background-color 140ms ease;
}

.assistant-sidebar-resize-handle:hover::after,
.assistant-sidebar-resize-handle:focus-visible::after,
.assistant-page--resizing-sidebar .assistant-sidebar-resize-handle::after {
  background: #10b981;
}

.assistant-sidebar-resize-handle:focus-visible {
  outline: none;
}

.assistant-page--resizing-sidebar {
  cursor: col-resize;
  user-select: none;
}

.assistant-page--sidebar-constrained.assistant-page--drawer-open {
  --assistant-sidebar-current-width: var(--assistant-sidebar-collapsed-width);
}

.assistant-page--sidebar-constrained.assistant-page--drawer-open :deep(.assistant-sidebar) {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 60;
  box-shadow: 20px 0 48px rgba(15, 23, 42, 0.18);
}

.assistant-sidebar-scrim {
  position: fixed;
  inset: 0;
  z-index: 50;
  border: 0;
  background: rgba(15, 23, 42, 0.24);
  cursor: default;
}

.assistant-page--learning-canvas-open {
  --learning-canvas-current-width: var(--learning-canvas-width);
}

.assistant-page--compact-learning-canvas {
  --learning-canvas-current-width: 0px;
}

.assistant-main {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  height: 100%;
  flex-direction: column;
  position: relative;
  box-sizing: border-box;
  background: #f8fafc;
}

.main-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 56px;
  min-height: 56px;
  padding: 0 24px 0 28px;
}

.loading-label {
  padding: 3px 8px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 12px;
  font-weight: 600;
}

.main-title {
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.header-spacer {
  flex: 1;
}

.learning-results-button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 34px;
  padding: 7px 12px;
  border: 1px solid #bbf7d0;
  border-radius: 999px;
  background: #ffffff;
  color: #047857;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.learning-results-button:hover,
.learning-results-button:focus-visible,
.learning-results-button[aria-expanded='true'] {
  border-color: #6ee7b7;
  background: #ecfdf5;
  outline: none;
}

.learning-results-count {
  display: inline-flex;
  min-width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #047857;
  color: #ffffff;
  padding: 0 6px;
  box-sizing: border-box;
  font-size: 11px;
}

.learning-canvas-scrim {
  position: fixed;
  inset: 0;
  z-index: 64;
  border: none;
  background: rgba(15, 23, 42, 0.28);
  cursor: default;
}

.composer-dock {
  position: fixed;
  left: calc(var(--app-rail-width) + var(--assistant-sidebar-current-width) + 1px);
  right: var(--learning-canvas-current-width);
  bottom: 0;
  z-index: 40;
  padding: 18px 24px max(6px, env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(248, 250, 252, 0) 0%, rgba(248, 250, 252, 0.88) 34%, #f8fafc 100%);
}

@media (max-width: 960px) {
  .assistant-page {
    height: 100vh;
    min-height: 100vh;
  }

  .main-header {
    padding: 0 18px;
  }

  .composer-dock {
    left: calc(var(--app-rail-width) + var(--assistant-sidebar-current-width) + 1px);
    right: var(--learning-canvas-current-width);
    padding: 14px 12px max(4px, env(safe-area-inset-bottom));
  }
}

.folder-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.36);
}

.folder-dialog {
  width: min(420px, 100%);
  padding: 22px;
  border: 1px solid #dbe3ea;
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.22);
  box-sizing: border-box;
}

.folder-dialog-title {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.folder-dialog-copy {
  margin: 8px 0 16px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.folder-dialog-input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #dbe3ea;
  border-radius: 12px;
  background: #f8fafc;
  color: #0f172a;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}

.folder-dialog-input:focus {
  border-color: #10b981;
  background: #ffffff;
}

.folder-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.folder-dialog-button {
  border: none;
  border-radius: 999px;
  background: #f1f5f9;
  color: #334155;
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.folder-dialog-button--primary {
  background: #047857;
  color: #ffffff;
}
</style>
