import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import type { WritingEvaluateResponse } from '@/api/writing'
import {
  saveEvaluateResult,
  loadEvaluateResult,
  clearEvaluateResult,
  saveEvaluatedText,
  loadEvaluatedText,
  clearEvaluatedText,
  saveGrammarReChecked,
  loadGrammarReChecked,
  clearGrammarReChecked,
} from '@/components/writing/editorShellStorage'

/**
 * Single source of truth for evaluation state.
 *
 * Owns: evaluateResult, evaluatedText, grammarReChecked, submitting, evaluateError.
 * All persisted to storage (scoped by docId) so they survive page refresh.
 */
export const useEvaluateStore = defineStore('evaluate', () => {
  // ── State ──
  const evaluateResult = ref<WritingEvaluateResponse | null>(null)
  const evaluateError = ref<string | null>(null)
  const submitting = ref(false)

  /** Snapshot of draftText at the time the last evaluation completed. */
  const evaluatedText = ref<string | null>(null)

  /** True once grammar re-check completes after an evaluate (switches panel data source). */
  const grammarReChecked = ref(false)

  /** Whether the current evaluateResult was produced by user-initiated submit (not cache restore). */
  const resultFromSubmit = ref(false)

  /** Document scope for storage keys. Set externally by EditorShell. */
  const docScope = ref<string | null>(null)
  /** Guard to avoid persisting while doing in-memory resets/restores. */
  const persistEnabled = ref(true)

  function getScope() { return docScope.value?.trim() || null }

  function runWithoutPersist(task: () => void) {
    const prev = persistEnabled.value
    persistEnabled.value = false
    try {
      task()
    } finally {
      persistEnabled.value = prev
    }
  }

  // ── Persistence watches ──
  watch(evaluateResult, (result) => {
    if (!persistEnabled.value) return
    saveEvaluateResult(result, getScope())
  }, { flush: 'sync' })

  watch(evaluatedText, (text) => {
    if (!persistEnabled.value) return
    saveEvaluatedText(text, getScope())
  }, { flush: 'sync' })

  watch(grammarReChecked, (val) => {
    if (!persistEnabled.value) return
    saveGrammarReChecked(val, getScope())
  }, { flush: 'sync' })

  // ── Actions ──

  /**
   * Set a new evaluation result.
   * Called by EditorShell when useEvaluateSubmission returns a result.
   */
  function setResult(result: WritingEvaluateResponse | null) {
    evaluateResult.value = result
    if (!result) {
      evaluatedText.value = null
    }
  }

  function clearResult() {
    evaluateResult.value = null
    evaluateError.value = null
  }

  /**
   * Restore persisted state from storage. Call during onMounted.
   * @param scope docId scope for storage keys
   */
  function restore(scope: string | null) {
    runWithoutPersist(() => {
      docScope.value = scope
      const cached = loadEvaluateResult(scope)
      if (cached && !evaluateResult.value) {
        evaluateResult.value = cached
      }
      const cachedText = loadEvaluatedText(scope)
      if (cachedText != null && evaluatedText.value == null) {
        evaluatedText.value = cachedText
      }
      const cachedReChecked = loadGrammarReChecked(scope)
      if (cachedReChecked) {
        grammarReChecked.value = true
      }
    })
  }

  /** Reset in-memory state only (safe for refresh — cache survives). */
  function resetAll() {
    runWithoutPersist(() => {
      evaluateResult.value = null
      evaluateError.value = null
      submitting.value = false
      evaluatedText.value = null
      grammarReChecked.value = false
      resultFromSubmit.value = false
    })
  }

  /** Clear all persisted caches — call on explicit discard/exit. */
  function clearAllCaches(scopeOverride?: string | null) {
    const scope = scopeOverride?.trim() || getScope()
    clearEvaluateResult(scope)
    clearEvaluatedText(scope)
    clearGrammarReChecked(scope)
  }

  return {
    // State
    evaluateResult,
    evaluateError,
    submitting,
    evaluatedText,
    grammarReChecked,
    resultFromSubmit,
    docScope,
    // Actions
    setResult,
    clearResult,
    restore,
    resetAll,
    clearAllCaches,
  }
})