import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { useDebounceFn } from '@vueuse/core'
import {
  WRITING_STORAGE_KEYS,
  loadDraft,
  loadDraftNow,
  saveDraftNow,
  clearDraftNow,
  loadWritingMode,
  loadTaskPrompt,
  loadConversationId,
  clearConversationId,
  createConversationId,
  loadAiNoteDraftNow,
  saveAiNoteDraftNow,
  clearAiNoteDraftNow,
  loadAiProvider,
  saveAiProviderNow,
  clearAiProviderNow,
  type WritingAiProvider,
} from '@/components/writing/editorShellStorage'
import { getDocumentContent } from '@/api/document'

const DEFAULT_AI_PROVIDER: WritingAiProvider = 'openai'

export const useWritingDraftStore = defineStore('writingDraft', () => {
  const draftText = ref('')
  const writingMode = ref<'free' | 'exam'>('free')
  const taskPrompt = ref('')
  const aiNote = ref('')
  const aiConversationId = ref(createConversationId())
  const aiProvider = ref<WritingAiProvider>(DEFAULT_AI_PROVIDER)
  const docId = ref<string | null>(null)
  const title = ref('')
  const docRevision = ref<number | null>(null)
  const submitCount = ref(0)
  const archived = ref(false)

  /** When true, watches that clear evaluateResult / schedule grammar should be skipped. */
  const isHydrating = ref(false)

  const getScope = () => (docId.value?.trim() ? docId.value.trim() : null)

  // Debounced persistence
  const debouncedSaveDraft = useDebounceFn((v: string) => {
    saveDraftNow(v ?? '', getScope())
  }, 500)

  /** Flush pending debounced draft write immediately (for beforeunload). */
  function flushDraft() {
    saveDraftNow(draftText.value ?? '', getScope())
  }

  function flushAll() {
    const scope = getScope()
    saveDraftNow(draftText.value ?? '', scope)
    saveAiNoteDraftNow(aiNote.value ?? '', scope)
    if (scope) saveAiProviderNow(aiProvider.value, scope)
  }

  const debouncedSaveAiNote = useDebounceFn((v: string) => {
    saveAiNoteDraftNow(v ?? '', getScope())
  }, 400)

  watch(
    () => draftText.value,
    (v) => {
      if (isHydrating.value) return
      debouncedSaveDraft(v ?? '')
    },
    { immediate: false },
  )

  watch(
    () => aiNote.value,
    (v) => {
      if (isHydrating.value) return
      debouncedSaveAiNote(v ?? '')
    },
    { immediate: false },
  )

  watch(
    writingMode,
    (val) => {
      try { localStorage.setItem(WRITING_STORAGE_KEYS.writingMode, val) } catch (_) {}
    },
    { immediate: true },
  )

  watch(
    taskPrompt,
    (val) => {
      try { localStorage.setItem(WRITING_STORAGE_KEYS.taskPrompt, val) } catch (_) {}
    },
    { immediate: true },
  )

  watch(
    aiConversationId,
    (val) => {
      try {
        if (val?.trim()) localStorage.setItem(`${WRITING_STORAGE_KEYS.aiConversationId}${getScope() ? `:${getScope()}` : ''}`, val.trim())
      } catch (_) {}
    },
    { immediate: false },
  )

  watch(
    docId,
    (val) => {
      try {
        if (val) sessionStorage.setItem('peai:writing:docId', val)
        else sessionStorage.removeItem('peai:writing:docId')
      } catch (_) {}
    },
    { immediate: false },
  )

  watch(
    aiProvider,
    (provider) => {
      const scope = getScope()
      if (!scope) return
      saveAiProviderNow(provider, scope)
    },
    { immediate: false },
  )

  /**
   * Lightweight init: only resolves docId and restores local caches.
   * Does NOT fetch from backend. Use hydrateByDocId() for full restore.
   */
  function init(options?: {
    initialWritingMode?: 'free' | 'exam'
    initialTaskPrompt?: string
    initialDocId?: string | null
    initialTitle?: string
    initialSubmitCount?: number
    initialArchived?: boolean
  }) {
    writingMode.value = options?.initialWritingMode ?? loadWritingMode()
    taskPrompt.value = options?.initialTaskPrompt ?? loadTaskPrompt()
    submitCount.value = options?.initialSubmitCount ?? 0
    archived.value = Boolean(options?.initialArchived)
    title.value = options?.initialTitle ?? ''

    const incomingDocId = options?.initialDocId?.trim() || null
    if (incomingDocId) {
      docId.value = incomingDocId
    } else {
      try {
        const savedDocId = sessionStorage.getItem('peai:writing:docId')
        docId.value = savedDocId?.trim() || null
      } catch {
        docId.value = null
      }
    }

    const scope = getScope()
    aiConversationId.value = loadConversationId(scope)
    aiProvider.value = loadAiProvider(scope) ?? DEFAULT_AI_PROVIDER

    // Restore draft by current document scope; global fallback only for non-doc mode.
    const saved = loadDraftNow(scope) ?? (scope ? null : loadDraftNow()) ?? (scope ? null : loadDraft())
    draftText.value = saved ?? ''

    // Restore AI note by scope.
    const savedAiNote = loadAiNoteDraftNow(scope)
    aiNote.value = savedAiNote ?? ''
  }

  /**
   * Atomic hydrate from backend by docId.
   * Fetches document content + metadata, sets all fields at once under isHydrating lock.
   * Local draft takes precedence over backend content if it exists (user may have unsaved edits).
   */
  async function hydrateByDocId(id: string): Promise<boolean> {
    isHydrating.value = true
    try {
      docId.value = id
      try { sessionStorage.setItem('peai:writing:docId', id) } catch {}

      const doc = await getDocumentContent(id)

      // Atomic write: set all fields without triggering clearing watches
      writingMode.value = doc.mode ?? (doc.taskPrompt ? 'exam' : 'free')
      title.value = doc.title ?? ''
      taskPrompt.value = doc.taskPrompt ?? ''
      docRevision.value = doc.latestRevision
      submitCount.value = doc.submitCount ?? 0
      archived.value = Boolean(doc.archived ?? doc.status === 2)

      // Local draft takes precedence (user may have typed since last backend save)
      const scope = id.trim() || null
      const localDraft = loadDraftNow(scope)
      if (localDraft != null) {
        draftText.value = localDraft
      } else if (doc.content) {
        draftText.value = doc.content
      } else {
        draftText.value = ''
      }

      // Restore AI note
      const savedAiNote = loadAiNoteDraftNow(scope)
      aiNote.value = savedAiNote ?? ''

      // Restore conversation
      aiConversationId.value = loadConversationId(scope)
      aiProvider.value = loadAiProvider(scope) ?? DEFAULT_AI_PROVIDER

      return true
    } catch (e) {
      console.warn('[writingDraftStore] hydrateByDocId failed', e)
      return false
    } finally {
      isHydrating.value = false
    }
  }

  function clearCurrentDraftContent() {
    const scope = getScope()
    draftText.value = ''
    aiNote.value = ''
    submitCount.value = 0
    try {
      clearDraftNow(scope)
      clearAiNoteDraftNow(scope)
    } catch (_) {}
  }

  function clearAll() {
    const scope = getScope()
    clearCurrentDraftContent()
    try {
      clearConversationId(scope)
      if (scope) clearAiProviderNow(scope)
      localStorage.removeItem(WRITING_STORAGE_KEYS.legacyDraft)
    } catch (_) {}
    aiProvider.value = DEFAULT_AI_PROVIDER
    docId.value = null
    title.value = ''
    docRevision.value = null
    archived.value = false
  }

  function resetConversation() {
    aiConversationId.value = createConversationId()
  }

  function setAiProvider(provider: WritingAiProvider) {
    if (aiProvider.value === provider) return
    aiProvider.value = provider
    resetConversation()
  }

  return {
    draftText,
    writingMode,
    taskPrompt,
    aiNote,
    aiConversationId,
    aiProvider,
    docId,
    title,
    docRevision,
    submitCount,
    archived,
    isHydrating,
    init,
    hydrateByDocId,
    flushDraft,
    flushAll,
    clearCurrentDraftContent,
    clearAll,
    resetConversation,
    setAiProvider,
  }
})
