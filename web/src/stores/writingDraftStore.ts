import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { useDebounceFn } from '@vueuse/core'
import {
  WRITING_STORAGE_KEYS,
  loadDraft,
  loadDraftNow,
  saveDraftNow,
  loadWritingMode,
  loadTaskPrompt,
  loadConversationId,
  createConversationId,
  loadAiNoteDraftNow,
  saveAiNoteDraftNow,
} from '@/components/writing/editorShellStorage'

export const useWritingDraftStore = defineStore('writingDraft', () => {
  const draftText = ref(loadDraft())
  const writingMode = ref<'free' | 'exam'>('free')
  const taskPrompt = ref('')
  const aiNote = ref('')
  const aiConversationId = ref(loadConversationId())
  const evaluatedText = ref<string | null>(null)

  // Debounced persistence
  const debouncedSaveDraft = useDebounceFn((v: string) => {
    saveDraftNow(v ?? '')
  }, 500)

  const debouncedSaveAiNote = useDebounceFn((v: string) => {
    saveAiNoteDraftNow(v ?? '')
  }, 400)

  watch(
    () => draftText.value,
    (v) => {
      console.log('[draft] watch fired', { len: (v ?? '').length, head: (v ?? '').slice(0, 30) })
      debouncedSaveDraft(v ?? '')
    },
    { immediate: false },
  )

  watch(
    () => aiNote.value,
    (v) => {
      console.log('[aiNoteDraft] watch fired', { len: (v ?? '').length, head: (v ?? '').slice(0, 30) })
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
        if (val?.trim()) localStorage.setItem(WRITING_STORAGE_KEYS.aiConversationId, val.trim())
      } catch (_) {}
    },
    { immediate: true },
  )

  function init(options?: {
    initialWritingMode?: 'free' | 'exam'
    initialTaskPrompt?: string
  }) {
    writingMode.value = options?.initialWritingMode ?? loadWritingMode()
    taskPrompt.value = options?.initialTaskPrompt ?? loadTaskPrompt()

    // Restore draft
    const saved = loadDraftNow()
    console.log('[draft] init', { savedLen: saved?.length ?? 0 })
    if (saved && (!draftText.value || draftText.value.trim() === '')) {
      draftText.value = saved
      console.log('[draft] restored', { len: saved.length })
    }

    // Restore AI note
    const savedAiNote = loadAiNoteDraftNow()
    console.log('[aiNoteDraft] before', aiNote.value)
    if (savedAiNote && (!aiNote.value || aiNote.value.trim() === '')) {
      aiNote.value = savedAiNote
      console.log('[aiNoteDraft] restored', { len: savedAiNote.length })
    }
  }

  function clearAll() {
    draftText.value = ''
    aiNote.value = ''
    evaluatedText.value = null
    try {
      localStorage.removeItem(WRITING_STORAGE_KEYS.draft)
      localStorage.removeItem(WRITING_STORAGE_KEYS.legacyDraft)
      localStorage.removeItem(WRITING_STORAGE_KEYS.aiNoteDraft)
      localStorage.removeItem(WRITING_STORAGE_KEYS.evaluateResult)
      console.log('[draft] cleared')
      console.log('[aiNoteDraft] cleared')
    } catch (_) {}
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
    init,
    clearAll,
    resetConversation,
  }
})
