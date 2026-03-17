import { ref, computed, watch } from 'vue'
import { defineStore } from 'pinia'
import { useDebounceFn } from '@vueuse/core'
import { clearTrustedRewrite as clearTrustedRewriteApi, grammarCheck as grammarCheckApi, grammarSuppress } from '@/api/writing'
import type {
  GrammarCheckMode,
  SuggestionErrorItem,
  SuggestionItem,
  TrustedRewriteSegment,
  WritingEvaluateResponse,
} from '@/api/writing'
import { findClosestMatch, hasValidSuggestion, resolveErrorSpan, shouldUseWordBoundary } from '@/components/writing/errorSpanResolver'
import {
  clearTrustedRewriteSegments as clearTrustedRewriteSegmentsCache,
  saveGrammarErrors,
  loadGrammarErrors,
  clearGrammarErrors,
  loadTrustedRewriteSegments,
  savePolishSuggestions,
  loadPolishSuggestions,
  clearPolishSuggestions,
  saveGrammarFixedIds,
  loadGrammarFixedIds,
  clearGrammarFixedIds,
  saveTrustedRewriteSegments,
} from '@/components/writing/editorShellStorage'
import { showToast } from '@/utils/toast'
import { useWritingDraftStore } from './writingDraftStore'
import { useEvaluateStore } from './evaluateStore'

type CorrectionError = WritingEvaluateResponse['errors'][number]
type TrustedRange = { start: number; end: number; record: TrustedRewriteSegment }

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
  const trinkaMode = ref<GrammarCheckMode>('lite')
  const trustedRewriteSegments = ref<TrustedRewriteSegment[]>([])

  /** Errors the user explicitly dismissed (local UI hide, backend Redis handles persistence). */
  const dismissedLocally = ref<Set<string>>(new Set())
  /** Errors the user applied a fix to (local UI hide, backend Redis handles persistence). */
  const recentFixedLocally = ref<Set<string>>(new Set())

  function getScope() { return draftStore.docId?.trim() || null }
  function persistTrustedRewrites() {
    saveTrustedRewriteSegments(trustedRewriteSegments.value, getScope())
  }

  /** Combined set of locally hidden error IDs (for panel display). */
  const locallyHiddenIds = computed(() => {
    const combined = new Set(dismissedLocally.value)
    for (const id of recentFixedLocally.value) combined.add(id)
    return combined
  })

  function persistLocalIds() {
    saveGrammarFixedIds([...locallyHiddenIds.value], getScope())
  }

  function normalizeSnippet(text: string): string {
    return (text ?? '').replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim().replace(/\s+/g, ' ').toLowerCase()
  }

  function extractLeftContext(text: string, start: number): string {
    return text.slice(Math.max(0, start - CONTEXT_WINDOW), Math.max(0, start))
  }

  function extractRightContext(text: string, end: number): string {
    return text.slice(Math.min(end, text.length), Math.min(text.length, end + CONTEXT_WINDOW))
  }

  function findTrustedRange(text: string, record: TrustedRewriteSegment): TrustedRange | null {
    const needle = record?.sentence_text ?? ''
    if (!text || !needle) return null
    const matches: number[] = []
    let from = 0
    while (from <= text.length - needle.length) {
      const idx = text.indexOf(needle, from)
      if (idx < 0) break
      matches.push(idx)
      from = idx + 1
    }
    if (matches.length === 0) return null
    const targetLeft = normalizeSnippet(record.left_context ?? '')
    const targetRight = normalizeSnippet(record.right_context ?? '')
    let best = matches[0]
    let bestScore = Number.NEGATIVE_INFINITY
    for (const idx of matches) {
      let score = 0
      const left = normalizeSnippet(extractLeftContext(text, idx))
      const right = normalizeSnippet(extractRightContext(text, idx + needle.length))
      if (targetLeft && left.endsWith(targetLeft)) score += 2
      if (targetRight && right.startsWith(targetRight)) score += 2
      if (score > bestScore) {
        bestScore = score
        best = idx
      }
    }
    return { start: best, end: best + needle.length, record }
  }

  const activeTrustedRanges = computed<TrustedRange[]>(() => {
    const text = draftStore.draftText
    if (!text || trustedRewriteSegments.value.length === 0) return []
    return trustedRewriteSegments.value
      .map((record) => findTrustedRange(text, record))
      .filter((item): item is TrustedRange => item != null)
      .sort((a, b) => a.start - b.start)
  })

  function shouldSuppressTrustedSuggestion(error: CorrectionError | undefined): boolean {
    if (!error?.span) return false
    if (!error.engine || error.engine.toLowerCase() !== 'trinka') return false
    if ((error.category ?? 'error').toLowerCase() !== 'suggestion') return false
    return activeTrustedRanges.value.some((range) =>
      error.span!.start >= range.start && error.span!.end <= range.end,
    )
  }

  function filterTrustedSuggestions(errors: CorrectionError[]): CorrectionError[] {
    return errors.filter((error) => !shouldSuppressTrustedSuggestion(error))
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
    const hidden = locallyHiddenIds.value
    if (evaluateStore.grammarReChecked && grammarErrors.value.length > 0) {
      return grammarErrors.value.filter((e) => e.category !== 'suggestion' && !hidden.has(e.id))
    }
    if (preferEvaluateErrors.value && !evaluateStore.grammarReChecked && evaluateStore.evaluateResult?.errors?.length) {
      return evaluateStore.evaluateResult.errors.filter((e) => e.category !== 'suggestion' && !hidden.has(e.id))
    }
    return grammarErrors.value.filter((e) => e.category !== 'suggestion' && !hidden.has(e.id))
  })

  const grammarPanelSuggestions = computed(() => {
    const hidden = locallyHiddenIds.value
    let base: CorrectionError[] = []
    if (evaluateStore.grammarReChecked && grammarErrors.value.length > 0) {
      base = grammarErrors.value.filter((e) => e.category === 'suggestion' && !hidden.has(e.id))
    } else if (preferEvaluateErrors.value && !evaluateStore.grammarReChecked && evaluateStore.evaluateResult?.errors?.length) {
      base = evaluateStore.evaluateResult.errors.filter((e) => e.category === 'suggestion' && !hidden.has(e.id))
    } else {
      base = grammarErrors.value.filter((e) => e.category === 'suggestion' && !hidden.has(e.id))
    }

    const merged = [...base]
    const seen = new Set(merged.map((e) => e.id))
    for (const item of gptSuggestionErrors.value) {
      if (hidden.has(item.id) || seen.has(item.id)) continue
      merged.push(item)
      seen.add(item.id)
    }
    return filterTrustedSuggestions(merged)
  })

  const hiddenTrustedSuggestionCount = computed(() => {
    const hidden = locallyHiddenIds.value
    let base: CorrectionError[] = []
    if (evaluateStore.grammarReChecked && grammarErrors.value.length > 0) {
      base = grammarErrors.value.filter((e) => e.category === 'suggestion' && !hidden.has(e.id))
    } else if (preferEvaluateErrors.value && !evaluateStore.grammarReChecked && evaluateStore.evaluateResult?.errors?.length) {
      base = evaluateStore.evaluateResult.errors.filter((e) => e.category === 'suggestion' && !hidden.has(e.id))
    } else {
      base = grammarErrors.value.filter((e) => e.category === 'suggestion' && !hidden.has(e.id))
    }
    let total = 0
    for (const item of [...base, ...gptSuggestionErrors.value]) {
      if (hidden.has(item.id)) continue
      if (shouldSuppressTrustedSuggestion(item)) total++
    }
    return total
  })

  const grammarPanelFixedIds = computed(() => locallyHiddenIds.value)

  const displayEditorErrors = computed(() => {
    let base: CorrectionError[] | undefined
    const hidden = locallyHiddenIds.value
    if (evaluateStore.grammarReChecked && grammarErrors.value.length > 0) {
      base = grammarErrors.value.filter((e) => !hidden.has(e.id))
    } else if (preferEvaluateErrors.value && !evaluateStore.grammarReChecked && evaluateStore.evaluateResult?.errors?.length) {
      base = evaluateStore.evaluateResult.errors.filter((e) => !hidden.has(e.id))
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
    if (all) {
      all = filterTrustedSuggestions(all)
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
    return grammarPanelSuggestions.value
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
      const res = await grammarCheckApi({ text, docId, trinkaMode: trinkaMode.value }, { signal: abortController.signal })
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
      ?? grammarPanelSuggestions.value.find((e) => e.id === errorId)
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
      ?? grammarPanelSuggestions.value.find((e) => e.id === errorId)
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
      ?? grammarPanelSuggestions.value.find((e) => e.id === errorId)
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
    const cachedTrusted = loadTrustedRewriteSegments(scope)
    if (cachedTrusted && trustedRewriteSegments.value.length === 0) {
      trustedRewriteSegments.value = cachedTrusted
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
    trinkaMode.value = 'lite'
    grammarCheckActive.value = true
    lastCheckedText = ''
    abortController?.abort()
    trustedRewriteSegments.value = []
  }

  /** Clear all sessionStorage caches — call on explicit discard/exit, not on refresh */
  function clearAllCaches(scopeOverride?: string | null) {
    const scope = scopeOverride?.trim() || getScope()
    clearGrammarErrors(scope)
    clearPolishSuggestions(scope)
    clearGrammarFixedIds(scope)
    clearTrustedRewriteSegmentsCache(scope)
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

  function setTrinkaMode(mode: GrammarCheckMode) {
    if (trinkaMode.value === mode) return
    trinkaMode.value = mode
    abortController?.abort()
    if (!grammarCheckActive.value || draftStore.draftText.trim().length < 10) return
    void runGrammarCheck()
  }

  function destroy() {
    abortController?.abort()
  }

  function registerTrustedRewrite(segment: TrustedRewriteSegment | null | undefined) {
    if (!segment) return
    const fingerprint = [
      segment.normalized_text_hash,
      normalizeSnippet(segment.left_context ?? ''),
      normalizeSnippet(segment.right_context ?? ''),
      segment.tier,
    ].join('|')
    const next = trustedRewriteSegments.value.filter((item) => {
      const existingFingerprint = [
        item.normalized_text_hash,
        normalizeSnippet(item.left_context ?? ''),
        normalizeSnippet(item.right_context ?? ''),
        item.tier,
      ].join('|')
      return existingFingerprint !== fingerprint
    })
    next.push(segment)
    trustedRewriteSegments.value = next
    persistTrustedRewrites()
  }

  async function clearTrustedRewrites() {
    const scope = getScope()
    trustedRewriteSegments.value = []
    clearTrustedRewriteSegmentsCache(scope)
    if (scope) {
      try {
        await clearTrustedRewriteApi(scope)
      } catch {
        showToast('恢复 Trinka 检查失败，请稍后重试', 'error')
      }
    }
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
    trinkaMode,
    dismissedLocally,
    recentFixedLocally,
    gptErrors,
    gptSuggestionErrors,
    // Computed
    grammarPanelErrors,
    grammarPanelSuggestions,
    grammarPanelFixedIds,
    displayEditorErrors,
    rewritePanelSuggestions,
    hiddenTrustedSuggestionCount,
    trustedRewriteSegments,
    unfixedFixableCount,
    hasUncheckedChanges,
    // Actions
    scheduleGrammarCheck,
    fixError,
    fixAll,
    inlineFixError,
    dismissError,
    setTrinkaMode,
    setGptErrors,
    setGptSuggestions,
    registerTrustedRewrite,
    clearTrustedRewrites,
    restoreFromCache,
    pauseForSubmit,
    resetAll,
    clearAllCaches,
    useEvaluateErrorsForPanels,
    clearEvaluateErrorSource,
    destroy,
  }
})


