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
  clearEvaluateResult,
} from '@/components/writing/editorShellStorage'

export const useWritingDraftStore = defineStore('writingDraft', () => {
  const draftText = ref('')
  const writingMode = ref<'free' | 'exam'>('free')
  const taskPrompt = ref('')
  const aiNote = ref('')
  const aiConversationId = ref(createConversationId())
  const evaluatedText = ref<string | null>(null)
  const docId = ref<string | null>(null)
  const docRevision = ref<number | null>(null)

  const getScope = () => (docId.value?.trim() ? docId.value.trim() : null)

  // Debounced persistence
  const debouncedSaveDraft = useDebounceFn((v: string) => {
    saveDraftNow(v ?? '', getScope())
  }, 500)

  const debouncedSaveAiNote = useDebounceFn((v: string) => {
    saveAiNoteDraftNow(v ?? '', getScope())
  }, 400)

  watch(
    () => draftText.value,
    (v) => {
      console.log('[draft] watch fired', { len: (v ?? '').length, head: (v ?? '').slice(0, 30), scope: getScope() ?? 'global' })
      debouncedSaveDraft(v ?? '')
    },
    { immediate: false },
  )

  watch(
    () => aiNote.value,
    (v) => {
      console.log('[aiNoteDraft] watch fired', { len: (v ?? '').length, head: (v ?? '').slice(0, 30), scope: getScope() ?? 'global' })
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

  function init(options?: {
    initialWritingMode?: 'free' | 'exam'
    initialTaskPrompt?: string
    initialDocId?: string | null
  }) {
    writingMode.value = options?.initialWritingMode ?? loadWritingMode()
    taskPrompt.value = options?.initialTaskPrompt ?? loadTaskPrompt()

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

    // Restore draft by current document scope; global fallback only for non-doc mode.
    const saved = loadDraftNow(scope) ?? (scope ? null : loadDraftNow()) ?? (scope ? null : loadDraft())
    console.log('[draft] init', { scope: scope ?? 'global', savedLen: saved?.length ?? 0 })
    draftText.value = saved ?? ''

    // Restore AI note by scope.
    const savedAiNote = loadAiNoteDraftNow(scope)
    aiNote.value = savedAiNote ?? ''
    console.log('[aiNoteDraft] init', { scope: scope ?? 'global', savedLen: savedAiNote?.length ?? 0 })

    evaluatedText.value = null
  }

  function clearCurrentDraftContent() {
    const scope = getScope()
    draftText.value = ''
    aiNote.value = ''
    evaluatedText.value = null
    try {
      clearDraftNow(scope)
      clearAiNoteDraftNow(scope)
      clearEvaluateResult(scope)
      console.log('[draft] cleared current scope', { scope: scope ?? 'global' })
    } catch (_) {}
  }

  function clearAll() {
    const scope = getScope()
    clearCurrentDraftContent()
    try {
      clearConversationId(scope)
      localStorage.removeItem(WRITING_STORAGE_KEYS.legacyDraft)
      console.log('[aiConversation] cleared', { scope: scope ?? 'global' })
    } catch (_) {}
    docId.value = null
    docRevision.value = null
  }

  function resetConversation() {
    aiConversationId.value = createConversationId()
  }

  return {
    draftText,
    writingMode,
    taskPrompt,
    aiNote,
    aiConversationId,
    evaluatedText,
    docId,
    docRevision,
    init,
    clearCurrentDraftContent,
    clearAll,
    resetConversation,
  }
})
