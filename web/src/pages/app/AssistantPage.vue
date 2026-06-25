<template>
  <div
    class="assistant-page"
    :style="assistantPageStyle"
    :class="{
      'assistant-page--drawer-open': assistantDrawerOpen,
      'assistant-page--learning-canvas-open': Boolean(learningAssetDraft),
    }"
  >
    <AssistantSidebar
      v-if="assistantDrawerOpen"
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

    <div class="assistant-main">
      <header class="main-header">
        <span class="main-title">{{ pageTitle }}</span>
        <span v-if="isLoadingConversations" class="loading-label">同步中</span>
        <div class="header-spacer"></div>
        <div class="markdown-theme-control" aria-label="助手输出风格">
          <button
            type="button"
            class="markdown-theme-button"
            :class="{ 'markdown-theme-button--active': markdownTheme === 'marktext' }"
            @click="setMarkdownTheme('marktext')"
          >
            MarkText
          </button>
          <button
            type="button"
            class="markdown-theme-button"
            :class="{ 'markdown-theme-button--active': markdownTheme === 'milkdown' }"
            @click="setMarkdownTheme('milkdown')"
          >
            Milkdown
          </button>
        </div>
      </header>

      <AssistantChatView
        :messages="activeConversation.messages"
        :error-message="errorMessage"
        :can-retry="canRetry"
      :empty-title="emptyTitle"
      :empty-subtitle="emptySubtitle"
      :markdown-theme="markdownTheme"
      :can-append-to-learning-asset="Boolean(learningAssetDraft)"
      @choose-starter="applyStarter"
      @copy-message="handleCopyMessage"
      @retry-message="handleRetryAssistantMessage"
      @retry="retryLastMessage"
      @create-learning-asset="handleCreateLearningAsset"
      @append-to-learning-asset="handleAppendToLearningAsset"
      />

      <div class="composer-dock" :class="{ composerDocked }">
        <AssistantComposer
          :model-value="composerText"
          :loading="isSending"
          :attachments="composerAttachments"
          :assistant-mode="assistantMode"
          @update:model-value="composerText = $event"
          @add-files="handleFileSelect"
          @remove-attachment="removeAttachment"
          @set-assistant-mode="setAssistantMode"
          @send="sendMessage"
        />
      </div>
    </div>

    <LearningAssetCanvas
      v-if="learningAssetDraft"
      :draft="learningAssetDraft"
      :drafts="learningAssetDrafts"
      :active-draft-id="activeLearningAssetDraftId"
      :candidate-markdown="learningAssetCandidateMarkdown"
      :is-organizing="isLearningAssetOrganizing"
      :is-saving="isLearningAssetSaving"
      :save-status="learningAssetSaveStatus"
      :save-status-by-draft-id="learningAssetSaveStatusByDraftId"
      :error-message="learningAssetError"
      :width-px="learningAssetCanvasWidth"
      @close="closeLearningAssetCanvas"
      @organize="handleOrganizeLearningAsset"
      @save="handleSaveLearningAsset"
      @select-draft="setActiveLearningAssetDraft"
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
import { computed, inject, onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AssistantChatView from '@/components/assistant/AssistantChatView.vue'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'
import LearningAssetCanvas from '@/components/assistant/LearningAssetCanvas.vue'
import AssistantSidebar from '@/components/assistant/AssistantSidebar.vue'
import { assistantApi, type AssistantArchiveSettingsDto } from '@/api/assistant'
import {
  createLearningNote,
  getLearningNote,
  organizeLearningAssetMarkdown,
  updateLearningNote,
} from '@/api/learningNotes'
import type { LearningNoteDto, LearningNotePayload } from '@/api/learningNotes'
import { showToast } from '@/utils/toast'
import type { AssistantLearningAssetSelection } from '@/components/assistant/AssistantChatView.vue'
import type { AssistantAttachmentSource } from './assistantAttachmentRules.ts'
import {
  PENDING_ASSISTANT_PROMPT_KEY,
  PENDING_ASSISTANT_SELECTION_KEY,
  type PendingAssistantSelection,
  parsePendingAssistantSelection,
} from './assistantMessageActions.ts'
import {
  readAssistantMarkdownTheme,
  writeAssistantMarkdownTheme,
  type AssistantMarkdownTheme,
} from './assistantMarkdownTheme.ts'
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
const emptyTitle = '今天想练什么？'
const emptySubtitle = '我可以陪你做英语评价、表达润色、题目设计和词句讲解。'
const composerDocked = true
const LEARNING_ASSET_CANVAS_WIDTH_KEY = 'peai:assistant:learning-asset-canvas-width'
const LEARNING_NOTE_QUERY_KEY = 'learningNote'
const LEARNING_ASSET_AUTO_SAVE_DELAY_MS = 1200
const MIN_LEARNING_ASSET_CANVAS_WIDTH = 360
const MAX_LEARNING_ASSET_CANVAS_WIDTH = 720
const route = useRoute()
const router = useRouter()
const fallbackAssistantDrawerOpen = ref(false)
const injectedAssistantDrawerOpen = inject<Ref<boolean> | null>('assistantDrawerOpen', null)
const assistantDrawerOpen = injectedAssistantDrawerOpen ?? fallbackAssistantDrawerOpen
const folderDialogMode = ref<'create' | 'move' | null>(null)
const pendingMoveConversationId = ref<string | null>(null)
const newFolderName = ref('')
const markdownTheme = ref<AssistantMarkdownTheme>(readAssistantMarkdownTheme())
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

function setMarkdownTheme(theme: AssistantMarkdownTheme) {
  markdownTheme.value = theme
  writeAssistantMarkdownTheme(theme)
}

function applyPendingAssistantPrompt(prompt: string, selection?: PendingAssistantSelection | null) {
  composerText.value = prompt
  if (selection) {
    setPendingSelection(selection)
  }
}

function closeAssistantDrawer() {
  assistantDrawerOpen.value = false
}

function restoreLearningAssetDraft(conversationId: string) {
  clearLearningAssetAutoSaveTimer()
  const workspace = learningAssetDraftStore.readWorkspace(conversationId)
  learningAssetDrafts.value = workspace?.drafts ?? []
  activeLearningAssetDraftId.value = workspace?.activeDraftId ?? ''
  learningAssetCandidateMarkdownByDraftId.value = {}
  learningAssetError.value = ''
  learningAssetSaveStatusByDraftId.value = buildLearningAssetSaveStatusMap(learningAssetDrafts.value)
}

function persistLearningAssetDraft(
  nextDraft: LearningAssetDraft,
  options: { queueAutoSave?: boolean } = {},
) {
  const draftIndex = learningAssetDrafts.value.findIndex((draft) => draft.draftId === nextDraft.draftId)
  const nextDrafts = [...learningAssetDrafts.value]
  if (draftIndex >= 0) {
    nextDrafts[draftIndex] = nextDraft
  } else {
    nextDrafts.push(nextDraft)
  }
  learningAssetDrafts.value = nextDrafts
  activeLearningAssetDraftId.value = nextDraft.draftId
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
  if (!draft?.noteUid) {
    if (draft) setLearningAssetSaveStatus(draft.draftId, 'unsaved')
    return
  }
  setLearningAssetSaveStatus(draft.draftId, 'unsaved')
  learningAssetAutoSaveTimer = setTimeout(() => {
    learningAssetAutoSaveTimer = null
    void saveLearningAssetDraft({ showSuccessToast: false })
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
  persistLearningAssetDraft(draft)
  learningAssetCandidateMarkdownByDraftId.value = {
    ...learningAssetCandidateMarkdownByDraftId.value,
    [draft.draftId]: '',
  }
  learningAssetError.value = ''
  setLearningAssetSaveStatus(draft.draftId, 'unsaved')
  showToast(`已打开${draft.type === 'grammar' ? '语法笔记' : '单词卡'}画布`, 'success')
}

function handleAppendToLearningAsset(selection: AssistantLearningAssetSelection) {
  if (!learningAssetDraft.value) return
  const nextContent = appendSelectedTextMarkdown(learningAssetDraft.value.contentMarkdown, selection.selectedText)
  updateLearningAssetContent(nextContent)
  showToast('已加入当前笔记', 'success')
}

function createEmptyLearningAssetDraft(type: LearningAssetType) {
  clearLearningAssetRouteQuery()
  const title = type === 'grammar' ? '未命名语法笔记' : '未命名单词'
  const draft = createLearningAssetDraft({
    conversationId: activeConversationId.value,
    type,
    title,
    selectedText: title,
    contextText: '',
  })
  persistLearningAssetDraft(draft)
  setLearningAssetSaveStatus(draft.draftId, 'unsaved')
}

function setActiveLearningAssetDraft(draftId: string) {
  if (!learningAssetDrafts.value.some((draft) => draft.draftId === draftId)) return
  activeLearningAssetDraftId.value = draftId
  persistLearningAssetWorkspace()
}

function closeLearningAssetCanvas() {
  clearLearningAssetAutoSaveTimer()
  clearLearningAssetRouteQuery()
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

async function handleSaveLearningAsset() {
  await saveLearningAssetDraft({ showSuccessToast: true })
}

async function saveLearningAssetDraft({ showSuccessToast }: { showSuccessToast: boolean }) {
  if (!learningAssetDraft.value || isLearningAssetSaving.value) return
  const draft = learningAssetDraft.value
  const payload = buildLearningNotePayload(draft)
  if (!payload) {
    learningAssetError.value = '单词和正文不能为空'
    setLearningAssetSaveStatus(draft.draftId, 'failed')
    return
  }

  clearLearningAssetAutoSaveTimer()
  isLearningAssetSaving.value = true
  setLearningAssetSaveStatus(draft.draftId, 'saving')
  learningAssetError.value = ''
  try {
    const saved = draft.noteUid
      ? await updateLearningNote(draft.noteUid, payload)
      : await createLearningNote(payload)
    const savedDraft = {
      ...draft,
      noteUid: saved.noteUid,
      title: saved.title,
      contentMarkdown: saved.contentMarkdown,
      structuredPayload: saved.structuredPayload,
      updatedAt: Date.now(),
    }
    persistLearningAssetDraft(savedDraft)
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
})

onBeforeUnmount(() => {
  clearLearningAssetAutoSaveTimer()
  window.removeEventListener('peai:assistant:use-prompt', handlePendingPromptEvent)
})

watch(activeConversationId, (conversationId) => {
  if (readRouteLearningNoteUid(route.query[LEARNING_NOTE_QUERY_KEY])) return
  restoreLearningAssetDraft(conversationId)
}, { immediate: true })

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

function buildConversationGroups(items: typeof conversations.value) {
  const now = Date.now()
  const dayMs = 24 * 60 * 60 * 1000

  const groups = [
    {
      label: '今天',
      conversations: items.filter((conversation) => now - conversation.updatedAt < dayMs),
    },
    {
      label: '最近 7 天',
      conversations: items.filter(
        (conversation) => now - conversation.updatedAt >= dayMs && now - conversation.updatedAt < dayMs * 7,
      ),
    },
    {
      label: '更早',
      conversations: items.filter((conversation) => now - conversation.updatedAt >= dayMs * 7),
    },
  ]

  return groups.filter((group) => group.conversations.length > 0)
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
  buildConversationGroups(filteredConversations.value.filter((conversation) => (
    conversation.projectId === null || conversation.projectId === undefined
  ))),
)

const archivedConversationGroups = computed(() =>
  buildConversationGroups(filteredArchivedConversations.value),
)

const folderConversationGroups = computed(() =>
  projects.value.map((folder) => {
    const folderConversations = filteredConversations.value.filter(
      (conversation) => conversation.projectId === folder.id,
    )
    return {
      id: folder.id,
      name: folder.name,
      conversationCount: folderConversations.length,
      groups: buildConversationGroups(folderConversations),
    }
  }),
)
</script>

<style scoped>
.assistant-page {
  --app-rail-width: 64px;
  --assistant-sidebar-width: 280px;
  --assistant-sidebar-current-width: 0px;
  --learning-canvas-width: 420px;
  --learning-canvas-current-width: 0px;
  display: flex;
  flex: 1;
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  background: #f8fafc;
}

:global(.app-layout--rail-collapsed) .assistant-page {
  --app-rail-width: 0px;
}

.assistant-page--drawer-open {
  --assistant-sidebar-current-width: var(--assistant-sidebar-width);
}

.assistant-page--learning-canvas-open {
  --learning-canvas-current-width: var(--learning-canvas-width);
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

.markdown-theme-control {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid #dbe3ea;
  border-radius: 999px;
  background: #ffffff;
}

.markdown-theme-button {
  min-width: 78px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #64748b;
  padding: 7px 11px;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.markdown-theme-button:hover,
.markdown-theme-button:focus-visible {
  color: #0f172a;
  outline: none;
}

.markdown-theme-button--active {
  background: #dcfce7;
  color: #047857;
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

  .markdown-theme-control {
    gap: 2px;
  }

  .markdown-theme-button {
    min-width: auto;
    padding: 7px 9px;
  }

  .composer-dock {
    left: calc(var(--app-rail-width) + var(--assistant-sidebar-current-width) + 1px);
    right: 0;
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
