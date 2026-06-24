import type { LearningAssetDraft } from '../../types/learningAssets.ts'

export const LEARNING_ASSET_DRAFT_STORAGE_KEY = 'peai:learning-asset-drafts:v1'

interface PersistedLearningAssetDraftState {
  draftsByConversationId: Record<string, LearningAssetDraft>
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
  if (!storage) return { draftsByConversationId: {} }
  try {
    const raw = storage.getItem(storageKey)
    if (!raw) return { draftsByConversationId: {} }
    const parsed = JSON.parse(raw) as Partial<PersistedLearningAssetDraftState>
    return {
      draftsByConversationId: sanitizeDraftMap(parsed.draftsByConversationId),
    }
  } catch {
    return { draftsByConversationId: {} }
  }
}

function sanitizeDraftMap(value: unknown): Record<string, LearningAssetDraft> {
  if (!value || typeof value !== 'object') return {}
  const entries = Object.entries(value as Record<string, unknown>)
  return entries.reduce<Record<string, LearningAssetDraft>>((acc, [conversationId, draft]) => {
    const normalized = sanitizeDraft(draft)
    if (normalized && normalized.sourceConversationId === conversationId) {
      acc[conversationId] = normalized
    }
    return acc
  }, {})
}

function sanitizeDraft(value: unknown): LearningAssetDraft | null {
  const draft = value as Partial<LearningAssetDraft> | null
  if (!draft || typeof draft !== 'object') return null
  if (draft.type !== 'vocabulary') return null
  if (typeof draft.sourceConversationId !== 'string' || !draft.sourceConversationId.trim()) return null
  if (typeof draft.title !== 'string' || !draft.title.trim()) return null
  if (typeof draft.contentMarkdown !== 'string') return null
  return {
    noteUid: typeof draft.noteUid === 'string' ? draft.noteUid : undefined,
    type: 'vocabulary',
    title: draft.title,
    contentMarkdown: draft.contentMarkdown,
    structuredPayload: typeof draft.structuredPayload === 'string' ? draft.structuredPayload : null,
    sourceConversationId: draft.sourceConversationId,
    sourceMessageId: typeof draft.sourceMessageId === 'string' ? draft.sourceMessageId : undefined,
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
    state.draftsByConversationId[draft.sourceConversationId] = {
      ...draft,
      updatedAt: Date.now(),
    }
    writeState(storage, storageKey, state)
  }

  function readDraft(conversationId: string) {
    return state.draftsByConversationId[conversationId] ?? null
  }

  function clearDraft(conversationId: string) {
    delete state.draftsByConversationId[conversationId]
    writeState(storage, storageKey, state)
  }

  return {
    saveDraft,
    readDraft,
    clearDraft,
  }
}
