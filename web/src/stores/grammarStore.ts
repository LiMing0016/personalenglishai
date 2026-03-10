import { ref, computed, watch } from 'vue'
import { defineStore } from 'pinia'
import { useDebounceFn } from '@vueuse/core'
import { grammarCheck as grammarCheckApi, grammarSuppress } from '@/api/writing'
import type { WritingEvaluateResponse, SuggestionErrorItem, SuggestionItem } from '@/api/writing'
import { findClosestMatch, hasValidSuggestion, resolveErrorSpan, shouldUseWordBoundary } from '@/components/writing/errorSpanResolver'
import {
  saveGrammarErrors,
  loadGrammarErrors,
  clearGrammarErrors,
  savePolishSuggestions,
  loadPolishSuggestions,
  clearPolishSuggestions,
  saveGrammarFixedIds,
  loadGrammarFixedIds,
  clearGrammarFixedIds,
} from '@/components/writing/editorShellStorage'
import { showToast } from '@/utils/toast'
import { useWritingDraftStore } from './writingDraftStore'
import { useEvaluateStore } from './evaluateStore'

type CorrectionError = WritingEvaluateResponse['errors'][number]

/** Context window chars before/after error span for suppress context hash. */
const CONTEXT_WINDOW = 30

export const useGrammarStore = defineStore('grammar', () => {
  const draftStore = useWritingDraftStore()
  const evaluateStore = useEvaluateStore()

  // ── State ──
  const grammarErrors = ref<CorrectionError[]>([])
  const grammarCheckActive = ref(true)
  const grammarChecking = ref(false)
  const grammarCheckError = ref<string | null>(null)
  const preferEvaluateErrors = ref(false)

  /** Errors the user explicitly dismissed (local UI hide, backend Redis handles persistence). */
  const dismissedLocally = ref<Set<string>>(new Set())
  /** Errors the user applied a fix to (local UI hide, backend Redis handles persistence). */
  const recentFixedLocally = ref<Set<string>>(new Set())

  function getScope() { return draftStore.docId?.trim() || null }

  /** Combined set of locally hidden error IDs (for panel display). */
  const locallyHiddenIds = computed(() => {
    const combined = new Set(dismissedLocally.value)
    for (const id of recentFixedLocally.value) combined.add(id)
    return combined
  })

  function persistLocalIds() {
    saveGrammarFixedIds([...locallyHiddenIds.value], getScope())
  }

  const gptErrors = ref<CorrectionError[]>([])
  const gptSuggestionErrors = ref<CorrectionError[]>([])
  /** Text snapshot of the last completed grammar check, used to detect stale/pending checks. */
  let lastCheckedText = ''
  let abortController: AbortController | null = null

  // Re-enable grammar checking when evaluateResult arrives
  watch(() => evaluateStore.evaluateResult, (result) => {
    if (result != null) {
      grammarCheckActive.value = true
    }
  })

  // ── Computed ──
  const grammarPanelErrors = computed(() => {
    if (evaluateStore.grammarReChecked && grammarErrors.value.length > 0) {
      return grammarErrors.value
    }
    if (preferEvaluateErrors.value && !evaluateStore.grammarReChecked && evaluateStore.evaluateResult?.errors?.length) {
      return evaluateStore.evaluateResult.errors.filter((e) => e.category !== 'suggestion')
    }
    return grammarErrors.value
  })

  const grammarPanelFixedIds = computed(() => locallyHiddenIds.value)

  const displayEditorErrors = computed(() => {
    let base: CorrectionError[] | undefined
    const hidden = locallyHiddenIds.value
    if (evaluateStore.grammarReChecked && grammarErrors.value.length > 0) {
      base = grammarErrors.value.filter((e) => !hidden.has(e.id))
    } else if (preferEvaluateErrors.value && !evaluateStore.grammarReChecked && evaluateStore.evaluateResult?.errors?.length) {
      base = evaluateStore.evaluateResult.errors
    } else if (grammarErrors.value.length > 0) {
      base = grammarErrors.value.filter((e) => !hidden.has(e.id))
    }
    const extras = [...gptErrors.value, ...gptSuggestionErrors.value]
    let all: CorrectionError[] | undefined
    if (extras.length > 0) {
      all = [...(base ?? []), ...extras]
    } else {
      all = base
    }
    // Re-resolve spans against current editor text to fix any offset drift
    if (!all || all.length === 0) return all
    const text = draftStore.draftText
    if (!text) return all
    return all.map((e) => {
      if (!e.original || !e.span) return e
      const resolved = resolveErrorSpan(e, text)
      if (!resolved) return e
      if (resolved.start === e.span.start && resolved.end === e.span.end) return e
      return { ...e, span: resolved }
    })
  })

  /** True when the user has typed since the last grammar check completed (debounce pending). */
  const hasUncheckedChanges = computed(() => {
    const current = draftStore.draftText.replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim()
    return current.length >= 10 && current !== lastCheckedText
  })

  /** Number of fixable grammar errors that the user has NOT yet fixed or dismissed. */
  const unfixedFixableCount = computed(() => {
    const hidden = locallyHiddenIds.value
    return grammarPanelErrors.value.filter(
      (e) => !hidden.has(e.id) && hasValidSuggestion(e),
    ).length
  })

  const rewritePanelSuggestions = computed(() => {
    const fromEvaluate = preferEvaluateErrors.value
      ? (evaluateStore.evaluateResult?.errors ?? []).filter((e) => e.category === 'suggestion')
      : []
    if (gptSuggestionErrors.value.length === 0) return fromEvaluate

    const merged = [...fromEvaluate]
    const seen = new Set(merged.map((e) => e.id))
    for (const s of gptSuggestionErrors.value) {
      if (seen.has(s.id)) continue
      merged.push(s)
      seen.add(s.id)
    }
    return merged
  })

  // ── Grammar Check Scheduling ──
  const debouncedGrammarCheck = useDebounceFn(() => {
    runGrammarCheck()
  }, 800)

  function scheduleGrammarCheck() {
    if (!grammarCheckActive.value || draftStore.draftText.trim().length < 10) {
      if (draftStore.draftText.trim().length < 10) {
        grammarErrors.value = []
        grammarCheckError.value = null
      }
      return
    }
    abortController?.abort()
    debouncedGrammarCheck()
  }

  async function runGrammarCheck() {
    const text = draftStore.draftText.replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim()
    if (!text || text.length < 10) return
    abortController = new AbortController()
    grammarChecking.value = true
    grammarCheckError.value = null
    try {
      const docId = draftStore.docId ?? undefined
      const res = await grammarCheckApi({ text, docId }, { signal: abortController.signal })
      // Suppressed errors are already filtered by backend (Redis-based suppress)
      grammarErrors.value = res.errors ?? []
      dismissedLocally.value = new Set()
      recentFixedLocally.value = new Set()
      persistLocalIds()
      gptErrors.value = []
      gptSuggestionErrors.value = []
      lastCheckedText = text
      saveGrammarErrors(grammarErrors.value, getScope())
      if (evaluateStore.evaluateResult) evaluateStore.grammarReChecked = true
    } catch (e: any) {
      if (e?.name === 'CanceledError' || e?.name === 'AbortError' || e?.code === 'ERR_CANCELED') return
      grammarErrors.value = []
      grammarCheckError.value = e?.message ?? '语法检查失败'
    } finally {
      grammarChecking.value = false
    }
  }

  // ── Fix Actions ──
  function fixError(errorId: string) {
    const err = grammarPanelErrors.value.find((e) => e.id === errorId)
    if (!err || !err.original || !hasValidSuggestion(err)) return
    if (locallyHiddenIds.value.has(errorId)) return

    const text = draftStore.draftText
    const resolved = resolveErrorSpan(err, text)
    if (!resolved) {
      showToast(`无法定位「${err.original.slice(0, 20)}…」，可能已被修改`, 'info')
      return
    }

    recentFixedLocally.value = new Set([...recentFixedLocally.value, errorId])
    persistLocalIds()
    // Suppress in backend (Redis, short TTL) to prevent reverse suggestions
    const context = extractContextWindow(text, resolved.start, resolved.end)
    grammarSuppress({
      docId: draftStore.docId ?? '',
      original: err.original,
      suggestion: err.suggestion!,
      ruleType: err.type,
      engine: err.engine ?? '',
      context,
      action: 'fix',
    }).catch(() => {}) // fire-and-forget
    const newText = text.slice(0, resolved.start) + err.suggestion! + text.slice(resolved.end)
    evaluateStore.evaluatedText = newText
    grammarCheckActive.value = true
    draftStore.draftText = newText
  }

  function fixAll() {
    const unfixed = grammarPanelErrors.value
      .filter((e) => !locallyHiddenIds.value.has(e.id) && e.original && hasValidSuggestion(e))

    let text = draftStore.draftText
    const newFixed = new Set(recentFixedLocally.value)

    const resolved: { err: typeof unfixed[number]; start: number; end: number }[] = []
    for (const err of unfixed) {
      const pos = resolveErrorSpan(err, text)
      if (pos) resolved.push({ err, ...pos })
    }
    resolved.sort((a, b) => b.start - a.start)

    for (const { err, start, end } of resolved) {
      const context = extractContextWindow(text, start, end)
      text = text.slice(0, start) + err.suggestion! + text.slice(end)
      newFixed.add(err.id)
      // Suppress in backend (fire-and-forget, short TTL)
      grammarSuppress({
        docId: draftStore.docId ?? '',
        original: err.original!,
        suggestion: err.suggestion!,
        ruleType: err.type,
        engine: err.engine ?? '',
        context,
        action: 'fix',
      }).catch(() => {})
    }

    evaluateStore.evaluatedText = text
    grammarCheckActive.value = true
    draftStore.draftText = text
    recentFixedLocally.value = newFixed
    persistLocalIds()
  }

  function inlineFixError(errorId: string) {
    const panelErr = grammarPanelErrors.value.find((e) => e.id === errorId)
    if (panelErr) {
      fixError(errorId)
      return
    }
    const gptErr = gptErrors.value.find((e) => e.id === errorId)
      ?? gptSuggestionErrors.value.find((e) => e.id === errorId)
    if (gptErr && gptErr.original && gptErr.suggestion != null) {
      const text = draftStore.draftText
      const resolved = resolveErrorSpan(gptErr, text)
      if (!resolved) return
      const newText = text.slice(0, resolved.start) + gptErr.suggestion + text.slice(resolved.end)
      evaluateStore.evaluatedText = newText
      draftStore.draftText = newText
    }
  }

  function dismissError(errorId: string) {
    // Find the error to get its content for backend suppress
    const err = grammarPanelErrors.value.find((e) => e.id === errorId)
      ?? gptErrors.value.find((e) => e.id === errorId)
      ?? gptSuggestionErrors.value.find((e) => e.id === errorId)

    // Local immediate hide
    dismissedLocally.value = new Set([...dismissedLocally.value, errorId])
    persistLocalIds()
    gptErrors.value = gptErrors.value.filter((e) => e.id !== errorId)
    gptSuggestionErrors.value = gptSuggestionErrors.value.filter((e) => e.id !== errorId)

    // Suppress in backend (Redis, long TTL) so it doesn't come back
    if (err?.original) {
      const text = draftStore.draftText
      const context = err.span ? extractContextWindow(text, err.span.start, err.span.end) : ''
      grammarSuppress({
        docId: draftStore.docId ?? '',
        original: err.original,
        suggestion: err.suggestion ?? '',
        ruleType: err.type,
        engine: err.engine ?? '',
        context,
        action: 'dismiss',
      }).catch(() => {})
    }
  }

  // ── GPT Error/Suggestion Handlers ──
  function setGptErrors(errors: SuggestionErrorItem[]) {
    const text = draftStore.draftText
    const converted: CorrectionError[] = []
    for (const e of errors) {
      const idx = findClosestMatch(text, e.original, 0, true, shouldUseWordBoundary(e.original))
      if (idx === -1) continue
      converted.push({
        id: e.id,
        type: e.type as any,
        category: 'error',
        severity: (e.severity === 'minor' ? 'minor' : 'major') as 'minor' | 'major',
        span: { start: idx, end: idx + e.original.length },
        original: e.original,
        suggestion: e.suggestion,
        reason: e.reason,
        engine: 'gpt',
      })
    }
    gptErrors.value = converted
    savePolishSuggestions({ errors: gptErrors.value, suggestions: gptSuggestionErrors.value }, getScope())
  }

  function setGptSuggestions(suggestions: SuggestionItem[]) {
    const text = draftStore.draftText
    const converted: CorrectionError[] = []
    for (const s of suggestions) {
      const idx = findClosestMatch(text, s.original, 0, true, shouldUseWordBoundary(s.original))
      if (idx === -1) continue
      converted.push({
        id: s.id,
        type: s.type as any,
        category: 'suggestion',
        severity: 'minor',
        span: { start: idx, end: idx + s.original.length },
        original: s.original,
        suggestion: s.suggestion,
        reason: s.reason,
        engine: 'gpt',
      })
    }
    gptSuggestionErrors.value = converted
    savePolishSuggestions({ errors: gptErrors.value, suggestions: gptSuggestionErrors.value }, getScope())
  }

  // ── Lifecycle ──
  function restoreFromCache() {
    const scope = getScope()
    const cached = loadGrammarErrors(scope)
    if (cached && cached.length > 0 && grammarErrors.value.length === 0) {
      grammarErrors.value = cached as CorrectionError[]
    }
    const cachedFixedIds = loadGrammarFixedIds(scope)
    if (cachedFixedIds && cachedFixedIds.length > 0 && locallyHiddenIds.value.size === 0) {
      // We can't distinguish dismiss vs fix from storage, treat all as dismissed
      dismissedLocally.value = new Set(cachedFixedIds)
    }
    const cachedPolish = loadPolishSuggestions(scope)
    if (cachedPolish) {
      if (cachedPolish.errors.length > 0 && gptErrors.value.length === 0) {
        gptErrors.value = cachedPolish.errors as CorrectionError[]
      }
      if (cachedPolish.suggestions.length > 0 && gptSuggestionErrors.value.length === 0) {
        gptSuggestionErrors.value = cachedPolish.suggestions as CorrectionError[]
      }
    }
  }

  function pauseForSubmit() {
    grammarCheckActive.value = false
    grammarErrors.value = []
    gptErrors.value = []
    gptSuggestionErrors.value = []
    grammarCheckError.value = null
    preferEvaluateErrors.value = false
    dismissedLocally.value = new Set()
    recentFixedLocally.value = new Set()
    evaluateStore.grammarReChecked = false
    lastCheckedText = ''
    abortController?.abort()
    clearGrammarFixedIds(getScope())
  }

  /**
   * Reset in-memory state only. Does NOT clear sessionStorage —
   * that is done explicitly via clearAllCaches() when user discards/exits.
   * This allows restoreFromCache() to recover state after a page refresh.
   */
  function resetAll() {
    grammarErrors.value = []
    grammarCheckError.value = null
    preferEvaluateErrors.value = false
    dismissedLocally.value = new Set()
    recentFixedLocally.value = new Set()
    gptErrors.value = []
    gptSuggestionErrors.value = []
    grammarCheckActive.value = true
    lastCheckedText = ''
    abortController?.abort()
  }

  /** Clear all sessionStorage caches — call on explicit discard/exit, not on refresh */
  function clearAllCaches(scopeOverride?: string | null) {
    const scope = scopeOverride?.trim() || getScope()
    clearGrammarErrors(scope)
    clearPolishSuggestions(scope)
    clearGrammarFixedIds(scope)
  }

  /**
   * Extract a context window of ±CONTEXT_WINDOW chars around the error span.
   * This is stable against edits outside the window (unlike full-sentence hash).
   */
  function extractContextWindow(text: string, start: number, end: number): string {
    if (!text) return ''
    const windowStart = Math.max(0, start - CONTEXT_WINDOW)
    const windowEnd = Math.min(text.length, end + CONTEXT_WINDOW)
    return text.slice(windowStart, windowEnd).trim()
  }

  function destroy() {
    abortController?.abort()
  }

  function useEvaluateErrorsForPanels() {
    preferEvaluateErrors.value = true
  }

  function clearEvaluateErrorSource() {
    preferEvaluateErrors.value = false
  }

  return {
    // State
    grammarErrors,
    grammarCheckActive,
    grammarChecking,
    grammarCheckError,
    dismissedLocally,
    recentFixedLocally,
    gptErrors,
    gptSuggestionErrors,
    // Computed
    grammarPanelErrors,
    grammarPanelFixedIds,
    displayEditorErrors,
    rewritePanelSuggestions,
    unfixedFixableCount,
    hasUncheckedChanges,
    // Actions
    scheduleGrammarCheck,
    fixError,
    fixAll,
    inlineFixError,
    dismissError,
    setGptErrors,
    setGptSuggestions,
    restoreFromCache,
    pauseForSubmit,
    resetAll,
    clearAllCaches,
    useEvaluateErrorsForPanels,
    clearEvaluateErrorSource,
    destroy,
  }
})
