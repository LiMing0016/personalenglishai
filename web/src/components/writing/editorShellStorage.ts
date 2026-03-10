import type { WritingEvaluateResponse } from '@/api/writing'

export const WRITING_STORAGE_KEYS = {
  layout: 'peai:writing:layout',
  scrollTop: 'peai:writing:scrollTop',
  draft: 'peai:writing:draft',
  legacyDraft: 'peai:draft:writing',
  aiNoteDraft: 'peai:writing:aiNoteDraft',
  aiConversationId: 'peai:writing:aiConversationId',
  writingMode: 'peai:writing:mode',
  taskPrompt: 'peai:writing:taskPrompt',
  evaluateResult: 'peai:writing:evaluateResult',
  splitRatio: 'writing.split.ratio',
  grammarErrors: 'peai:writing:grammarErrors',
  polishSuggestions: 'peai:writing:polishSuggestions',
  translateResult: 'peai:writing:translateResult',
  polishResult: 'peai:writing:polishResult',
  templateResult: 'peai:writing:templateResult',
  grammarFixedIds: 'peai:writing:grammarFixedIds',
  appliedSuggestionIds: 'peai:writing:appliedSuggestionIds',
  materialResult: 'peai:writing:materialResult',
  evaluatedText: 'peai:writing:evaluatedText',
  grammarReChecked: 'peai:writing:grammarReChecked',
  recentFixes: 'peai:writing:recentFixes',
} as const

function scopedKey(baseKey: string, scope?: string | null): string {
  const normalized = scope?.trim()
  return normalized ? `${baseKey}:${normalized}` : baseKey
}

export interface LayoutState<T extends string> {
  rightPanelOpen: boolean
  activePanel: T | null
}

export const DEFAULT_SPLIT_RATIO = 0.3

export function loadLayout<T extends string>(validPanels: readonly T[]): LayoutState<T> {
  try {
    const saved = localStorage.getItem(WRITING_STORAGE_KEYS.layout)
    if (!saved) {
      return { rightPanelOpen: false, activePanel: null }
    }

    const data = JSON.parse(saved) as Record<string, unknown>
    const rightPanelOpen = Boolean(data.rightPanelOpen)
    const panel = data.activePanel
    const activePanel =
      typeof panel === 'string' && validPanels.includes(panel as T) ? (panel as T) : null

    return {
      rightPanelOpen: rightPanelOpen && activePanel != null,
      activePanel: rightPanelOpen ? activePanel : null,
    }
  } catch {
    return { rightPanelOpen: false, activePanel: null }
  }
}

export function saveLayout<T extends string>(state: LayoutState<T>): void {
  try {
    localStorage.setItem(WRITING_STORAGE_KEYS.layout, JSON.stringify(state))
  } catch {
    // ignore localStorage failure
  }
}

export function clampRatio(ratio: number): number {
  if (!Number.isFinite(ratio)) return DEFAULT_SPLIT_RATIO
  return Math.min(0.82, Math.max(0.22, ratio))
}

export function createConversationId(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID()
    }
  } catch {
    // fallback below
  }
  return `conv_${Date.now()}_${Math.random().toString(16).slice(2)}`
}

export function loadConversationId(scope?: string | null): string {
  try {
    const saved = localStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.aiConversationId, scope))?.trim()
    if (saved) return saved
  } catch {
    // fallback below
  }
  return createConversationId()
}

export function clearConversationId(scope?: string | null): void {
  try {
    localStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.aiConversationId, scope))
  } catch {
    // ignore localStorage failure
  }
}

export function loadWritingMode(): 'free' | 'exam' {
  try {
    const saved = localStorage.getItem(WRITING_STORAGE_KEYS.writingMode)?.trim()
    return saved === 'exam' ? 'exam' : 'free'
  } catch {
    return 'free'
  }
}

export function loadTaskPrompt(): string {
  try {
    return localStorage.getItem(WRITING_STORAGE_KEYS.taskPrompt) ?? ''
  } catch {
    return ''
  }
}

export function computePanelWidthByRatio(
  ratio: number,
  viewportWidth: number,
  minPanelWidth: number,
  maxPanelWidth: number,
  minLeftWidth: number,
): number {
  const clampedRatio = clampRatio(ratio)
  const maxByEditor = Math.max(0, viewportWidth - minLeftWidth)
  const maxPanelByViewport = Math.min(maxPanelWidth, maxByEditor)
  if (maxPanelByViewport <= 0) return 0
  const minPanelByViewport = Math.min(minPanelWidth, maxPanelByViewport)
  const preferred = Math.round(viewportWidth * clampedRatio)
  return Math.max(minPanelByViewport, Math.min(preferred, maxPanelByViewport))
}

export function saveSplitRatio(ratio: number): void {
  try {
    localStorage.setItem(WRITING_STORAGE_KEYS.splitRatio, String(clampRatio(ratio)))
  } catch {
    // ignore localStorage failure
  }
}

export function loadSplitRatio(): number {
  try {
    const raw = localStorage.getItem(WRITING_STORAGE_KEYS.splitRatio)
    if (!raw) return DEFAULT_SPLIT_RATIO
    return clampRatio(Number(raw))
  } catch {
    return DEFAULT_SPLIT_RATIO
  }
}

export function saveEvaluateResult(result: WritingEvaluateResponse | null, scope?: string | null): void {
  try {
    if (result) {
      localStorage.setItem(scopedKey(WRITING_STORAGE_KEYS.evaluateResult, scope), JSON.stringify(result))
    } else {
      localStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.evaluateResult, scope))
    }
  } catch {
    // ignore localStorage failure
  }
}

export function loadEvaluateResult(scope?: string | null): WritingEvaluateResponse | null {
  try {
    const raw = localStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.evaluateResult, scope))
    if (!raw) return null
    return JSON.parse(raw) as WritingEvaluateResponse
  } catch {
    return null
  }
}

export function clearEvaluateResult(scope?: string | null): void {
  try {
    localStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.evaluateResult, scope))
  } catch {
    // ignore localStorage failure
  }
}

export function saveDraftNow(text: string, scope?: string | null): void {
  try {
    const payload = { text, updatedAt: Date.now() }
    localStorage.setItem(scopedKey(WRITING_STORAGE_KEYS.draft, scope), JSON.stringify(payload))
    console.log('[draft] saved', { len: text.length, head: text.slice(0, 30), scope: scope ?? 'global' })
  } catch (e) {
    console.error('[draft] save failed', e)
  }
}

export function loadDraftNow(scope?: string | null): string | null {
  try {
    const raw = localStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.draft, scope))
    console.log('[draft] load raw', raw)
    if (!raw) return null
    const obj = JSON.parse(raw)
    return typeof obj?.text === 'string' ? obj.text : null
  } catch (e) {
    console.error('[draft] load failed', e)
    return null
  }
}

export function clearDraftNow(scope?: string | null): void {
  try {
    localStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.draft, scope))
  } catch {
    // ignore localStorage failure
  }
}

export function loadDraft(): string {
  try {
    const raw = localStorage.getItem(WRITING_STORAGE_KEYS.draft)
    if (raw) {
      const trimmed = raw.trim()
      if (trimmed.startsWith('{')) {
        try {
          const obj = JSON.parse(trimmed) as { text?: unknown }
          if (typeof obj.text === 'string') return obj.text
        } catch {
          // fall through and treat as legacy
        }
      } else {
        const text = raw
        localStorage.setItem(WRITING_STORAGE_KEYS.draft, JSON.stringify({ text, updatedAt: Date.now() }))
        return text
      }
    }

    const legacy = localStorage.getItem(WRITING_STORAGE_KEYS.legacyDraft)
    if (legacy) {
      let text = legacy
      const trimmedLegacy = legacy.trim()
      if (trimmedLegacy.startsWith('{')) {
        try {
          const obj = JSON.parse(trimmedLegacy) as { text?: unknown }
          if (typeof obj.text === 'string') text = obj.text
        } catch {
          // ignore legacy parse error
        }
      }
      localStorage.setItem(WRITING_STORAGE_KEYS.draft, JSON.stringify({ text, updatedAt: Date.now() }))
      localStorage.removeItem(WRITING_STORAGE_KEYS.legacyDraft)
      return text
    }
  } catch {
    // ignore localStorage failure
  }
  return ''
}

export function saveAiNoteDraftNow(text: string, scope?: string | null): void {
  try {
    const payload = { text, updatedAt: Date.now() }
    localStorage.setItem(scopedKey(WRITING_STORAGE_KEYS.aiNoteDraft, scope), JSON.stringify(payload))
    console.log('[aiNoteDraft] saved', { len: text.length, head: text.slice(0, 30), scope: scope ?? 'global' })
  } catch (e) {
    console.error('[aiNoteDraft] save failed', e)
  }
}

export function loadAiNoteDraftNow(scope?: string | null): string | null {
  try {
    const raw = localStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.aiNoteDraft, scope))
    console.log('[aiNoteDraft] load', raw)
    if (!raw) return null
    const obj = JSON.parse(raw)
    return typeof obj?.text === 'string' ? obj.text : null
  } catch (e) {
    console.error('[aiNoteDraft] load failed', e)
    return null
  }
}

export function clearAiNoteDraftNow(scope?: string | null): void {
  try {
    localStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.aiNoteDraft, scope))
  } catch {
    // ignore localStorage failure
  }
}

// ── 语法检查结果缓存（sessionStorage，按 docId 分桶） ──

export function saveGrammarErrors(errors: unknown[], scope?: string | null): void {
  try {
    sessionStorage.setItem(scopedKey(WRITING_STORAGE_KEYS.grammarErrors, scope), JSON.stringify(errors))
  } catch { /* ignore */ }
}

export function loadGrammarErrors(scope?: string | null): unknown[] | null {
  try {
    const raw = sessionStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.grammarErrors, scope))
    if (!raw) return null
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : null
  } catch { return null }
}

export function clearGrammarErrors(scope?: string | null): void {
  try { sessionStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.grammarErrors, scope)) } catch { /* ignore */ }
}

// ── 润色建议缓存（sessionStorage，按 docId 分桶） ──

export function savePolishSuggestions(data: { errors: unknown[]; suggestions: unknown[] }, scope?: string | null): void {
  try {
    sessionStorage.setItem(scopedKey(WRITING_STORAGE_KEYS.polishSuggestions, scope), JSON.stringify(data))
  } catch { /* ignore */ }
}

export function loadPolishSuggestions(scope?: string | null): { errors: unknown[]; suggestions: unknown[] } | null {
  try {
    const raw = sessionStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.polishSuggestions, scope))
    if (!raw) return null
    const obj = JSON.parse(raw)
    if (obj && Array.isArray(obj.errors) && Array.isArray(obj.suggestions)) return obj
    return null
  } catch { return null }
}

export function clearPolishSuggestions(scope?: string | null): void {
  try { sessionStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.polishSuggestions, scope)) } catch { /* ignore */ }
}

// ── 翻译结果缓存（sessionStorage） ──

export interface TranslateCache {
  mode: 'full' | 'detailed'
  fullTranslation: string | null
  sentences: unknown[]
  lastTranslatedText: string
}

export function saveTranslateResult(data: TranslateCache, scope?: string | null): void {
  try {
    sessionStorage.setItem(scopedKey(WRITING_STORAGE_KEYS.translateResult, scope), JSON.stringify(data))
  } catch { /* ignore */ }
}

export function loadTranslateResult(scope?: string | null): TranslateCache | null {
  try {
    const raw = sessionStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.translateResult, scope))
    if (!raw) return null
    const obj = JSON.parse(raw)
    if (obj && Array.isArray(obj.sentences)) return obj as TranslateCache
    return null
  } catch { return null }
}

export function clearTranslateResult(scope?: string | null): void {
  try { sessionStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.translateResult, scope)) } catch { /* ignore */ }
}

// ── 润色结果缓存（sessionStorage） ──

export interface PolishCache {
  tier: string
  summary: unknown | null
  sentences: unknown[]
  replacedIndices: number[]
  essaySnapshot: string
}

export function savePolishResult(data: PolishCache, scope?: string | null): void {
  try {
    sessionStorage.setItem(scopedKey(WRITING_STORAGE_KEYS.polishResult, scope), JSON.stringify(data))
  } catch { /* ignore */ }
}

export function loadPolishResult(scope?: string | null): PolishCache | null {
  try {
    const raw = sessionStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.polishResult, scope))
    if (!raw) return null
    const obj = JSON.parse(raw)
    if (obj && Array.isArray(obj.sentences)) return obj as PolishCache
    return null
  } catch { return null }
}

export function clearPolishResult(scope?: string | null): void {
  try { sessionStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.polishResult, scope)) } catch { /* ignore */ }
}


// ── 模板提炼结果缓存（sessionStorage） ──

export interface WritingTemplateCache {
  essayType: string | null
  paragraphs: unknown[]
  usageTips: string[]
  essaySnapshot: string
  taskPromptSnapshot: string
}

export function saveWritingTemplateResult(data: WritingTemplateCache): void {
  try {
    sessionStorage.setItem(WRITING_STORAGE_KEYS.templateResult, JSON.stringify(data))
  } catch { /* ignore */ }
}

export function loadWritingTemplateResult(): WritingTemplateCache | null {
  try {
    const raw = sessionStorage.getItem(WRITING_STORAGE_KEYS.templateResult)
    if (!raw) return null
    const obj = JSON.parse(raw)
    if (obj && Array.isArray(obj.paragraphs)) return obj as WritingTemplateCache
    return null
  } catch { return null }
}

export function clearWritingTemplateResult(): void {
  try { sessionStorage.removeItem(WRITING_STORAGE_KEYS.templateResult) } catch { /* ignore */ }
}
// ── 语法修正 ID 缓存（sessionStorage，按 docId 分桶） ──

export function saveGrammarFixedIds(ids: string[], scope?: string | null): void {
  try {
    sessionStorage.setItem(scopedKey(WRITING_STORAGE_KEYS.grammarFixedIds, scope), JSON.stringify(ids))
  } catch { /* ignore */ }
}

export function loadGrammarFixedIds(scope?: string | null): string[] | null {
  try {
    const raw = sessionStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.grammarFixedIds, scope))
    if (!raw) return null
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : null
  } catch { return null }
}

export function clearGrammarFixedIds(scope?: string | null): void {
  try { sessionStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.grammarFixedIds, scope)) } catch { /* ignore */ }
}

// ── 已应用建议 ID 缓存（sessionStorage，按 docId 分桶） ──

export function saveAppliedSuggestionIds(data: { suggestions: string[]; gptErrors: string[] }, scope?: string | null): void {
  try {
    sessionStorage.setItem(scopedKey(WRITING_STORAGE_KEYS.appliedSuggestionIds, scope), JSON.stringify(data))
  } catch { /* ignore */ }
}

export function loadAppliedSuggestionIds(scope?: string | null): { suggestions: string[]; gptErrors: string[] } | null {
  try {
    const raw = sessionStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.appliedSuggestionIds, scope))
    if (!raw) return null
    const obj = JSON.parse(raw)
    if (obj && Array.isArray(obj.suggestions) && Array.isArray(obj.gptErrors)) return obj
    return null
  } catch { return null }
}

export function clearAppliedSuggestionIds(scope?: string | null): void {
  try { sessionStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.appliedSuggestionIds, scope)) } catch { /* ignore */ }
}

// ── 写作素材结果缓存（sessionStorage） ──

export function saveWritingMaterialResult(data: unknown): void {
  try {
    sessionStorage.setItem(WRITING_STORAGE_KEYS.materialResult, JSON.stringify(data))
  } catch { /* ignore */ }
}

export function loadWritingMaterialResult(): unknown | null {
  try {
    const raw = sessionStorage.getItem(WRITING_STORAGE_KEYS.materialResult)
    if (!raw) return null
    const obj = JSON.parse(raw)
    if (obj && Array.isArray(obj.vocabulary)) return obj
    return null
  } catch { return null }
}

export function clearWritingMaterialResult(): void {
  try { sessionStorage.removeItem(WRITING_STORAGE_KEYS.materialResult) } catch { /* ignore */ }
}

// ── evaluatedText 缓存（sessionStorage，按 docId 分桶） ──

export function saveEvaluatedText(text: string | null, scope?: string | null): void {
  try {
    if (text != null) {
      sessionStorage.setItem(scopedKey(WRITING_STORAGE_KEYS.evaluatedText, scope), text)
    } else {
      sessionStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.evaluatedText, scope))
    }
  } catch { /* ignore */ }
}

export function loadEvaluatedText(scope?: string | null): string | null {
  try {
    return sessionStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.evaluatedText, scope))
  } catch { return null }
}

export function clearEvaluatedText(scope?: string | null): void {
  try { sessionStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.evaluatedText, scope)) } catch { /* ignore */ }
}

// ── grammarReChecked 缓存（sessionStorage，按 docId 分桶） ──

export function saveGrammarReChecked(val: boolean, scope?: string | null): void {
  try {
    sessionStorage.setItem(scopedKey(WRITING_STORAGE_KEYS.grammarReChecked, scope), val ? '1' : '0')
  } catch { /* ignore */ }
}

export function loadGrammarReChecked(scope?: string | null): boolean {
  try {
    return sessionStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.grammarReChecked, scope)) === '1'
  } catch { return false }
}

export function clearGrammarReChecked(scope?: string | null): void {
  try { sessionStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.grammarReChecked, scope)) } catch { /* ignore */ }
}

// ── recentFixes 缓存（sessionStorage，按 docId 分桶） ──

export function saveRecentFixes(fixes: { original: string; suggestion: string }[], scope?: string | null): void {
  try {
    sessionStorage.setItem(scopedKey(WRITING_STORAGE_KEYS.recentFixes, scope), JSON.stringify(fixes))
  } catch { /* ignore */ }
}

export function loadRecentFixes(scope?: string | null): { original: string; suggestion: string }[] | null {
  try {
    const raw = sessionStorage.getItem(scopedKey(WRITING_STORAGE_KEYS.recentFixes, scope))
    if (!raw) return null
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : null
  } catch { return null }
}

export function clearRecentFixes(scope?: string | null): void {
  try { sessionStorage.removeItem(scopedKey(WRITING_STORAGE_KEYS.recentFixes, scope)) } catch { /* ignore */ }
}

