import {
  normalizeLearningAssetType,
  type LearningAssetDraft,
} from '../../types/learningAssets.ts'

export const LEARNING_ASSET_DRAFT_STORAGE_KEY = 'peai:learning-asset-drafts:v1'

export interface LearningAssetWorkspace {
  conversationId: string
  activeDraftId: string
  drafts: LearningAssetDraft[]
}

interface PersistedLearningAssetDraftState {
  draftsByConversationId?: Record<string, LearningAssetDraft>
  workspacesByConversationId: Record<string, LearningAssetWorkspace>
}

interface CreateLearningAssetDraftStoreOptions {
  storage?: Storage
  storageKey?: string
}

function fallbackStorage(): Storage | undefined {
  try {
    return globalThis.localStorage
  } catch {
    return undefined
  }
}

function readState(storage: Storage | undefined, storageKey: string): PersistedLearningAssetDraftState {
  if (!storage) return { workspacesByConversationId: {} }
  try {
    const raw = storage.getItem(storageKey)
    if (!raw) return { workspacesByConversationId: {} }
    const parsed = JSON.parse(raw) as Partial<PersistedLearningAssetDraftState>
    const migratedWorkspaces = sanitizeDraftMap(parsed.draftsByConversationId)
    const workspacesByConversationId = {
      ...migratedWorkspaces,
      ...sanitizeWorkspaceMap(parsed.workspacesByConversationId),
    }
    return {
      workspacesByConversationId,
    }
  } catch {
    return { workspacesByConversationId: {} }
  }
}

function sanitizeDraftMap(value: unknown): Record<string, LearningAssetWorkspace> {
  if (!value || typeof value !== 'object') return {}
  const entries = Object.entries(value as Record<string, unknown>)
  return entries.reduce<Record<string, LearningAssetWorkspace>>((acc, [conversationId, draft]) => {
    const normalized = sanitizeDraft(draft)
    if (normalized && normalized.sourceConversationId === conversationId) {
      acc[conversationId] = {
        conversationId,
        activeDraftId: normalized.draftId,
        drafts: [normalized],
      }
    }
    return acc
  }, {})
}

function sanitizeWorkspaceMap(value: unknown): Record<string, LearningAssetWorkspace> {
  if (!value || typeof value !== 'object') return {}
  const entries = Object.entries(value as Record<string, unknown>)
  return entries.reduce<Record<string, LearningAssetWorkspace>>((acc, [conversationId, workspace]) => {
    const normalized = sanitizeWorkspace(workspace, conversationId)
    if (normalized) acc[conversationId] = normalized
    return acc
  }, {})
}

function sanitizeWorkspace(value: unknown, fallbackConversationId?: string): LearningAssetWorkspace | null {
  const workspace = value as Partial<LearningAssetWorkspace> | null
  if (!workspace || typeof workspace !== 'object') return null
  const conversationId = typeof workspace.conversationId === 'string' && workspace.conversationId.trim()
    ? workspace.conversationId
    : fallbackConversationId
  if (!conversationId) return null
  const drafts = Array.isArray(workspace.drafts)
    ? workspace.drafts
      .map((draft) => sanitizeDraft(draft, conversationId))
      .filter((draft): draft is LearningAssetDraft => Boolean(draft))
    : []
  if (drafts.length === 0) return null
  const activeDraftId = typeof workspace.activeDraftId === 'string'
    && drafts.some((draft) => draft.draftId === workspace.activeDraftId)
    ? workspace.activeDraftId
    : drafts[0].draftId
  return {
    conversationId,
    activeDraftId,
    drafts,
  }
}

function sanitizeDraft(value: unknown, fallbackConversationId?: string): LearningAssetDraft | null {
  const draft = value as Partial<LearningAssetDraft> | null
  if (!draft || typeof draft !== 'object') return null
  const type = normalizeLearningAssetType(draft.type)
  const sourceConversationId = typeof draft.sourceConversationId === 'string' && draft.sourceConversationId.trim()
    ? draft.sourceConversationId
    : fallbackConversationId
  if (!sourceConversationId) return null
  if (typeof draft.title !== 'string' || !draft.title.trim()) return null
  if (typeof draft.contentMarkdown !== 'string') return null
  const sourceMessageId = typeof draft.sourceMessageId === 'string' ? draft.sourceMessageId : undefined
  const draftId = typeof draft.draftId === 'string' && draft.draftId.trim()
    ? draft.draftId
    : [type, sourceMessageId, draft.title.trim()].filter(Boolean).join(':')
  return {
    draftId,
    noteUid: typeof draft.noteUid === 'string' ? draft.noteUid : undefined,
    type,
    title: draft.title,
    contentMarkdown: draft.contentMarkdown,
    structuredPayload: typeof draft.structuredPayload === 'string' ? draft.structuredPayload : null,
    sourceConversationId,
    sourceMessageId,
    sourceText: typeof draft.sourceText === 'string' ? draft.sourceText : undefined,
    selectedText: typeof draft.selectedText === 'string' ? draft.selectedText : draft.title,
    contextText: typeof draft.contextText === 'string' ? draft.contextText : '',
    updatedAt: typeof draft.updatedAt === 'number' ? draft.updatedAt : Date.now(),
  }
}

function writeState(
  storage: Storage | undefined,
  storageKey: string,
  state: PersistedLearningAssetDraftState,
) {
  if (!storage) return
  try {
    storage.setItem(storageKey, JSON.stringify(state))
  } catch {
    // Storage can be unavailable or full; the in-memory canvas still works.
  }
}

export function createLearningAssetDraftStore(options: CreateLearningAssetDraftStoreOptions = {}) {
  const storage = options.storage ?? fallbackStorage()
  const storageKey = options.storageKey ?? LEARNING_ASSET_DRAFT_STORAGE_KEY
  const state = readState(storage, storageKey)

  function saveDraft(draft: LearningAssetDraft) {
    saveWorkspace({
      conversationId: draft.sourceConversationId,
      activeDraftId: draft.draftId,
      drafts: [draft],
    })
  }

  function saveWorkspace(workspace: LearningAssetWorkspace) {
    state.workspacesByConversationId[workspace.conversationId] = {
      conversationId: workspace.conversationId,
      activeDraftId: workspace.activeDraftId,
      drafts: workspace.drafts.map((draft) => ({
        ...draft,
        updatedAt: Date.now(),
      })),
    }
    writeState(storage, storageKey, state)
  }

  function readDraft(conversationId: string) {
    return state.workspacesByConversationId[conversationId]?.drafts[0] ?? null
  }

  function readWorkspace(conversationId: string) {
    return state.workspacesByConversationId[conversationId] ?? null
  }

  function clearDraft(conversationId: string) {
    delete state.workspacesByConversationId[conversationId]
    writeState(storage, storageKey, state)
  }

  return {
    saveDraft,
    saveWorkspace,
    readDraft,
    readWorkspace,
    clearDraft,
  }
}
