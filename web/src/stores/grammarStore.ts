import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useDebounceFn } from '@vueuse/core'
import { grammarCheck as grammarCheckApi } from '@/api/writing'
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

type CorrectionError = WritingEvaluateResponse['errors'][number]

export const useGrammarStore = defineStore('grammar', () => {
  const draftStore = useWritingDraftStore()

  // ── State ──
  const grammarErrors = ref<CorrectionError[]>([])
  const grammarCheckActive = ref(true)
  const grammarChecking = ref(false)
  const grammarCheckError = ref<string | null>(null)
  const grammarFixedErrorIds = ref<Set<string>>(new Set())
  function persistFixedIds() {
    saveGrammarFixedIds([...grammarFixedErrorIds.value])
  }
  const grammarReChecked = ref(false)
  const gptErrors = ref<CorrectionError[]>([])
  const gptSuggestionErrors = ref<CorrectionError[]>([])
  const evaluateResult = ref<WritingEvaluateResponse | null>(null)
  /** Text snapshot of the last completed grammar check, used to detect stale/pending checks. */
  let lastCheckedText = ''
  let abortController: AbortController | null = null

  // ── Computed ──
  const grammarPanelErrors = computed(() => {
    if (grammarReChecked.value && grammarErrors.value.length > 0) {
      return grammarErrors.value
    }
    if (!grammarReChecked.value && evaluateResult.value?.errors?.length) {
      return evaluateResult.value.errors.filter((e) => e.category !== 'suggestion')
    }
    return grammarErrors.value
  })

  const grammarPanelFixedIds = computed(() => grammarFixedErrorIds.value)

  const displayEditorErrors = computed(() => {
    let base: CorrectionError[] | undefined
    if (grammarReChecked.value && grammarErrors.value.length > 0) {
      const fixed = grammarFixedErrorIds.value
      base = grammarErrors.value.filter((e) => !fixed.has(e.id))
    } else if (!grammarReChecked.value && evaluateResult.value?.errors?.length) {
      base = evaluateResult.value.errors
    } else if (grammarErrors.value.length > 0) {
      const fixed = grammarFixedErrorIds.value
      base = grammarErrors.value.filter((e) => !fixed.has(e.id))
    }
    const extras = [...gptErrors.value, ...gptSuggestionErrors.value]
    if (extras.length > 0) {
      return [...(base ?? []), ...extras]
    }
    return base
  })

  /** True when the user has typed since the last grammar check completed (debounce pending). */
  const hasUncheckedChanges = computed(() => {
    const current = draftStore.draftText.replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim()
    return current.length >= 10 && current !== lastCheckedText
  })

  /** Number of fixable grammar errors that the user has NOT yet fixed or dismissed. */
  const unfixedFixableCount = computed(() => {
    const fixed = grammarFixedErrorIds.value
    return grammarPanelErrors.value.filter(
      (e) => !fixed.has(e.id) && hasValidSuggestion(e),
    ).length
  })

  const rewritePanelSuggestions = computed(() => {
    const fromEvaluate = (evaluateResult.value?.errors ?? []).filter((e) => e.category === 'suggestion')
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
      const res = await grammarCheckApi({ text }, { signal: abortController.signal })
      grammarErrors.value = res.errors ?? []
      grammarFixedErrorIds.value = new Set()
      persistFixedIds()
      gptErrors.value = []
      gptSuggestionErrors.value = []
      lastCheckedText = text
      saveGrammarErrors(grammarErrors.value)
      if (evaluateResult.value) grammarReChecked.value = true
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
    if (grammarFixedErrorIds.value.has(errorId)) return

    const text = draftStore.draftText
    const resolved = resolveErrorSpan(err, text)
    if (!resolved) {
      showToast(`无法定位「${err.original.slice(0, 20)}…」，可能已被修改`, 'info')
      return
    }

    grammarFixedErrorIds.value = new Set([...grammarFixedErrorIds.value, errorId])
    persistFixedIds()
    const newText = text.slice(0, resolved.start) + err.suggestion! + text.slice(resolved.end)
    draftStore.evaluatedText = newText
    grammarCheckActive.value = true
    draftStore.draftText = newText
  }

  function fixAll() {
    const unfixed = grammarPanelErrors.value
      .filter((e) => !grammarFixedErrorIds.value.has(e.id) && e.original && hasValidSuggestion(e))

    let text = draftStore.draftText
    const newFixed = new Set(grammarFixedErrorIds.value)

    const resolved: { err: typeof unfixed[number]; start: number; end: number }[] = []
    for (const err of unfixed) {
      const pos = resolveErrorSpan(err, text)
      if (pos) resolved.push({ err, ...pos })
    }
    resolved.sort((a, b) => b.start - a.start)

    for (const { err, start, end } of resolved) {
      text = text.slice(0, start) + err.suggestion! + text.slice(end)
      newFixed.add(err.id)
    }

    draftStore.evaluatedText = text
    grammarCheckActive.value = true
    draftStore.draftText = text
    grammarFixedErrorIds.value = newFixed
    persistFixedIds()
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
      draftStore.evaluatedText = newText
      draftStore.draftText = newText
    }
  }

  function dismissError(errorId: string) {
    grammarFixedErrorIds.value = new Set([...grammarFixedErrorIds.value, errorId])
    persistFixedIds()
    gptErrors.value = gptErrors.value.filter((e) => e.id !== errorId)
    gptSuggestionErrors.value = gptSuggestionErrors.value.filter((e) => e.id !== errorId)
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
      })
    }
    gptErrors.value = converted
    savePolishSuggestions({ errors: gptErrors.value, suggestions: gptSuggestionErrors.value })
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
      })
    }
    gptSuggestionErrors.value = converted
    savePolishSuggestions({ errors: gptErrors.value, suggestions: gptSuggestionErrors.value })
  }

  function setEvaluateResult(result: WritingEvaluateResponse | null) {
    evaluateResult.value = result
  }

  // ── Lifecycle ──
  function restoreFromCache() {
    const cached = loadGrammarErrors()
    if (cached && cached.length > 0 && grammarErrors.value.length === 0) {
      grammarErrors.value = cached as CorrectionError[]
    }
    const cachedFixedIds = loadGrammarFixedIds()
    if (cachedFixedIds && cachedFixedIds.length > 0 && grammarFixedErrorIds.value.size === 0) {
      grammarFixedErrorIds.value = new Set(cachedFixedIds)
    }
    const cachedPolish = loadPolishSuggestions()
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
    grammarFixedErrorIds.value = new Set()
    grammarReChecked.value = false
    lastCheckedText = ''
    abortController?.abort()
    clearGrammarFixedIds()
  }

  function resetAll() {
    evaluateResult.value = null
    grammarErrors.value = []
    grammarCheckError.value = null
    grammarFixedErrorIds.value = new Set()
    gptErrors.value = []
    gptSuggestionErrors.value = []
    grammarCheckActive.value = true
    grammarReChecked.value = false
    lastCheckedText = ''
    abortController?.abort()
    clearGrammarErrors()
    clearPolishSuggestions()
    clearGrammarFixedIds()
  }

  function destroy() {
    abortController?.abort()
  }

  return {
    // State
    grammarErrors,
    grammarCheckActive,
    grammarChecking,
    grammarCheckError,
    grammarFixedErrorIds,
    grammarReChecked,
    gptErrors,
    gptSuggestionErrors,
    evaluateResult,
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
    setEvaluateResult,
    restoreFromCache,
    pauseForSubmit,
    resetAll,
    destroy,
  }
})
