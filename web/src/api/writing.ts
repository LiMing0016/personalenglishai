/**
 * 写作评分接口：POST /api/writing/evaluate
 */
import { http } from './http'

export interface WritingEvaluateRequest {
  essay: string
  aiHint?: string
  mode?: 'free' | 'exam'
  lang?: string
  taskPrompt?: string
}

export interface WritingEvaluateResponse {
  requestId: string
  mode?: 'free' | 'exam'
  source?: 'ai' | 'fallback'
  grades?: Partial<Record<DimensionKey, GradeLetter>>
  dimensionScores?: Partial<Record<DimensionKey, number>>
  analysis?: Partial<Record<DimensionKey, {
    quote?: string
    strength_quote?: string
    weakness_quote?: string
    strength: string
    weakness: string
    suggestion: string
  }>>
  improvement?: {
    previous_score: number
    current_score: number
    delta: number
    message: string
  }
  priority_focus?: DimensionKey[]
  priority_focus_detail?: {
    dimension: DimensionKey
    reason: string
    action_item: string
  }
  score: {
    overall: number
    task: number
    coherence: number
    lexical: number
    grammar: number
  }
  /** 高考预估分（后端换算） */
  gaokao_score?: {
    score: number
    max_score: number
    band: string
  }
  summary: string
  errors: Array<{
    id: string
    type:
      | 'spelling' | 'morphology' | 'subject_verb' | 'tense'
      | 'article' | 'preposition' | 'collocation' | 'syntax'
      | 'word_choice' | 'part_of_speech' | 'punctuation' | 'logic'
    category?: 'error' | 'suggestion'
    severity: 'minor' | 'major'
    span: { start: number; end: number }
    original?: string
    suggestion?: string
    reason?: string
  }>
}

export interface WritingEvaluateTaskSubmitResponse {
  requestId: string
  status: 'processing' | 'succeeded' | 'failed'
  message?: string
}

export interface WritingEvaluateTaskStatusResponse {
  requestId: string
  status: 'processing' | 'succeeded' | 'failed'
  error?: string
  submittedAt?: number
  completedAt?: number
  result?: WritingEvaluateResponse
}

export type GradeLetter = 'A' | 'B' | 'C' | 'D' | 'E'
export type DimensionKey =
  | 'content_quality'
  | 'task_achievement'
  | 'structure'
  | 'vocabulary'
  | 'grammar'
  | 'expression'

export interface RubricLevelItem {
  level: GradeLetter
  score: number
  criteria: string
}

export interface RubricDimensionItem {
  dimension_key: DimensionKey
  display_name: string
  levels: RubricLevelItem[]
}

export interface RubricActiveResponse {
  rubric_key: string
  mode: 'free' | 'exam'
  dimensions: RubricDimensionItem[]
}

export function evaluateWriting(
  payload: WritingEvaluateRequest
): Promise<WritingEvaluateResponse> {
  const normalizedMode = payload.mode === 'exam' ? 'exam' : 'free'
  const taskPrompt =
    normalizedMode === 'exam' ? payload.taskPrompt?.trim() || undefined : undefined
  return http
    .post<WritingEvaluateResponse>('/writing/evaluate', {
      essay: payload.essay,
      aiHint: payload.aiHint ?? undefined,
      mode: normalizedMode,
      lang: payload.lang ?? 'en',
      taskPrompt,
    }, { timeout: 60000 })
    .then((res) => res.data)
}

export function submitEvaluateWriting(
  payload: WritingEvaluateRequest
): Promise<WritingEvaluateTaskSubmitResponse> {
  const normalizedMode = payload.mode === 'exam' ? 'exam' : 'free'
  const taskPrompt =
    normalizedMode === 'exam' ? payload.taskPrompt?.trim() || undefined : undefined
  return http
    .post<WritingEvaluateTaskSubmitResponse>('/writing/evaluate/submit', {
      essay: payload.essay,
      aiHint: payload.aiHint ?? undefined,
      mode: normalizedMode,
      lang: payload.lang ?? 'en',
      taskPrompt,
    }, { timeout: 60000 })
    .then((res) => res.data)
}

export function getEvaluateTask(
  requestId: string
): Promise<WritingEvaluateTaskStatusResponse> {
  return http
    .get<WritingEvaluateTaskStatusResponse>(`/writing/evaluate/tasks/${encodeURIComponent(requestId)}`)
    .then((res) => res.data)
}

export function getActiveRubric(params: {
  stage?: string
  mode: 'free' | 'exam'
}): Promise<RubricActiveResponse> {
  return http
    .get<RubricActiveResponse>('/v1/rubric/active', {
      params: {
        stage: params.stage ?? 'highschool',
        mode: params.mode,
      },
    })
    .then((res) => {
      const raw = res.data as any
      return {
        rubric_key: raw.rubric_key ?? raw.rubricKey ?? '',
        mode: raw.mode === 'exam' ? 'exam' : 'free',
        dimensions: (raw.dimensions ?? []).map((d: any) => ({
          dimension_key: d.dimension_key ?? d.dimensionKey ?? 'content_quality',
          display_name: d.display_name ?? d.displayName ?? '',
          levels: (d.levels ?? []).map((l: any) => ({
            level: (l.level ?? 'C') as GradeLetter,
            score: Number(l.score ?? 0),
            criteria: l.criteria ?? '',
          })),
        })),
      }
    })
}

export interface EvaluationHistoryItem {
  id: number
  mode: 'free' | 'exam'
  gaokao_score: number | null
  max_score: number | null
  band: string | null
  overall_score: number | null
  essay_preview: string
  created_at: string
  favorited: boolean
}

export interface EvaluationHistoryResponse {
  items: EvaluationHistoryItem[]
  total: number
}

export function getEvaluationHistory(
  page = 0,
  size = 10
): Promise<EvaluationHistoryResponse> {
  return http
    .get<EvaluationHistoryResponse>('/writing/history', { params: { page, size } })
    .then((res) => res.data)
}

export interface EvaluationDetailResponse {
  essayText: string
  result: WritingEvaluateResponse
}

export function getEvaluationDetail(id: number): Promise<EvaluationDetailResponse> {
  return http
    .get<EvaluationDetailResponse>(`/writing/history/${id}`)
    .then((res) => res.data)
}

/** POST /api/writing/chat 改写/指令 */
export interface WritingChatRequest {
  essay: string
  instruction: string
  lang?: string
  mode?: string
  aiHint?: string
  /** 选中文本作为提问上下文，为空则不传 */
  selectedText?: string
}

export interface WritingChatResponse {
  requestId: string
  assistantMessage: string
  rewrite?: {
    fullText: string
    summary?: string
  }
  /** 选区改写结果，用于「替换选中内容」 */
  resultText?: string
}

// ── Polish (分级润色) ──

export type PolishTier = 'basic' | 'steady' | 'advanced' | 'perfect'

export interface PolishRequest {
  original: string
  context?: string
  reason?: string
  tier: PolishTier
}

export interface PolishCandidate {
  polished: string
  explanation?: string
}

export interface PolishResponse {
  polished?: string | null
  explanation?: string
  candidates?: PolishCandidate[]
}

export function polishSuggestion(req: PolishRequest): Promise<PolishResponse> {
  return http
    .post<PolishResponse>('/writing/polish', req, { timeout: 60000 })
    .then((res) => res.data)
}

// ── Polish Essay (全文逐句润色) ──

export interface PolishEssayRequest {
  text: string
  tier: PolishTier
}

export interface SentencePolish {
  original: string
  polished: string
  explanation?: string
}

export interface PolishEssaySummary {
  strengths: string[]
  improvements: string[]
}

export interface PolishEssayResponse {
  summary?: PolishEssaySummary
  sentences: SentencePolish[]
}

export function polishEssay(req: PolishEssayRequest): Promise<PolishEssayResponse> {
  return http
    .post<PolishEssayResponse>('/writing/polish-essay', req, { timeout: 120000 })
    .then((res) => res.data)
}

export function toggleEssayFavorite(id: number): Promise<{ favorited: boolean }> {
  return http
    .post<{ favorited: boolean }>(`/writing/history/${id}/favorite`)
    .then((res) => res.data)
}

// ── Grammar Check (LanguageTool + Sapling) ──

export interface GrammarCheckRequest {
  text: string
}

export interface GrammarCheckResponse {
  errors: WritingEvaluateResponse['errors']
}

export function grammarCheck(
  payload: GrammarCheckRequest,
  options?: { signal?: AbortSignal }
): Promise<GrammarCheckResponse> {
  return http
    .post<GrammarCheckResponse>('/writing/grammar-check', payload, {
      timeout: 8000,
      signal: options?.signal,
    })
    .then((res) => res.data)
}

// ── AI Suggestions ──

/** GPT 复检的硬性错误 */
export interface SuggestionErrorItem {
  id: string
  type: string
  severity: string
  original: string
  suggestion: string
  reason: string
}

/** 软性建议 */
export interface SuggestionItem {
  id: string
  type: string
  original: string
  suggestion: string
  reason: string
}

export interface SuggestionsResponse {
  errors: SuggestionErrorItem[]
  suggestions: SuggestionItem[]
}

export function fetchWritingSuggestions(
  text: string,
  options?: { signal?: AbortSignal }
): Promise<SuggestionsResponse> {
  return http
    .post<SuggestionsResponse>('/writing/suggestions', { text }, {
      timeout: 30000,
      signal: options?.signal,
    })
    .then((res) => ({
      errors: res.data.errors ?? [],
      suggestions: res.data.suggestions ?? [],
    }))
}

export function chatWriting(payload: WritingChatRequest): Promise<WritingChatResponse> {
  return http
    .post<WritingChatResponse>('/writing/chat', {
      essay: payload.essay,
      instruction: payload.instruction,
      lang: payload.lang ?? 'en',
      mode: payload.mode ?? 'free',
      aiHint: payload.aiHint ?? undefined,
      selectedText: payload.selectedText?.trim() || undefined,
    }, { timeout: 60000 })
    .then((res) => res.data)
}
