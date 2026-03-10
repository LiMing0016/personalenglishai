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
  documentId?: string
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
  error_count?: number
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
      documentId: payload.documentId ?? undefined,
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
      documentId: payload.documentId ?? undefined,
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


// ── Template Extract (作文模板提炼) ──

export interface WritingTemplateRequest {
  text: string
  taskPrompt?: string
  studyStage?: string | null
  writingMode?: 'free' | 'exam'
}

export interface TemplateItem {
  template: string
  placeholders?: Record<string, string[]>
}

export interface KeyExpression {
  expression: string
  usage?: string
  usageTips?: string[]
}

export interface ParagraphTemplate {
  paragraphIndex: number
  function: string
  summary: string
  templates: TemplateItem[]
  keyExpressions: KeyExpression[]
}

export interface WritingTemplateResponse {
  essayType?: string | null
  paragraphs: ParagraphTemplate[]
  usageTips: string[]
}

export function extractWritingTemplate(req: WritingTemplateRequest): Promise<WritingTemplateResponse> {
  return http
    .post<WritingTemplateResponse>('/writing/template', req, { timeout: 120000 })
    .then((res) => ({
      essayType: res.data.essayType ?? null,
      paragraphs: res.data.paragraphs ?? [],
      usageTips: res.data.usageTips ?? [],
    }))
}

// ── Material (写作素材) ──

export interface WritingMaterialRequest {
  taskPrompt: string
  essayText?: string
  studyStage?: string | null
  writingMode?: 'free' | 'exam'
}

export interface VocabularyItem {
  word: string
  meaning: string
}

export interface VocabularyGroup {
  category: string
  words: VocabularyItem[]
}

export interface PhraseItem {
  phrase: string
  meaning: string
}

export interface SentenceItem {
  sentence: string
  description: string
}

export interface WritingMaterialResponse {
  topic?: string | null
  stage?: string | null
  vocabulary: VocabularyGroup[]
  phrases: PhraseItem[]
  sentences: SentenceItem[]
}

export function generateWritingMaterial(req: WritingMaterialRequest): Promise<WritingMaterialResponse> {
  return http
    .post<WritingMaterialResponse>('/writing/material', req, { timeout: 120000 })
    .then((res) => ({
      topic: res.data.topic ?? null,
      stage: res.data.stage ?? null,
      vocabulary: res.data.vocabulary ?? [],
      phrases: res.data.phrases ?? [],
      sentences: res.data.sentences ?? [],
    }))
}

// ── Translate (全文翻译 / 逐句精讲) ──

export interface TranslateRequest {
  text: string
  mode: 'full' | 'detailed'
}

export interface HighlightItem {
  word: string
  meaning?: string | null
  detail?: string | null
}

export interface SentenceTranslation {
  english: string
  chinese: string
  structure?: string | null
  highlights?: HighlightItem[]
}

export interface TranslateResponse {
  translation?: string | null
  sentences?: SentenceTranslation[]
}

export function translateEssay(
  req: TranslateRequest,
  options?: { signal?: AbortSignal },
): Promise<TranslateResponse> {
  return http
    .post<TranslateResponse>('/writing/translate', req, {
      timeout: 120000,
      signal: options?.signal,
    })
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

// ── Audit Topic (千问题目解析) ──

export interface AuditTopicRequest {
  topic: string
  genre?: string | null
  wordRange?: string | null
  requirements?: string | null
}

export interface AuditTopicResponse {
  status: 'complete' | 'need_more_info' | 'invalid'
  topic?: string
  genre?: string | null
  wordRange?: string | null
  requirements?: string | null
  message?: string
}

export function auditTopic(req: AuditTopicRequest): Promise<AuditTopicResponse> {
  return http
    .post<AuditTopicResponse>('/writing/audit-topic', req, { timeout: 35000 })
    .then((res) => res.data)
}

// ── Recognize Topic Image (千问 VL 图片识别) ──

export interface RecognizeTopicImageRequest {
  imageBase64: string
}

export interface RecognizeTopicImageResponse {
  text: string | null
}

export function recognizeTopicImage(req: RecognizeTopicImageRequest): Promise<RecognizeTopicImageResponse> {
  return http
    .post<RecognizeTopicImageResponse>('/writing/recognize-topic-image', req, { timeout: 30000 })
    .then((res) => res.data)
}

// ── Writing Session (document binding) ──

export interface StartSessionRequest {
  mode: 'free' | 'exam'
  taskPrompt?: string
  title?: string
  draft?: boolean
}

export interface StartSessionResponse {
  docId: string
  latestRevision: number
  isNew?: boolean
  existingContent?: string | null
  initialScore?: number | null
  latestScore?: number | null
  submitCount?: number
  mode?: 'free' | 'exam'
}

export function startWritingSession(req: StartSessionRequest): Promise<StartSessionResponse> {
  return http
    .post<StartSessionResponse>('/writing/start-session', req)
    .then((res) => res.data)
}

export interface WritingDocumentItem {
  docId: string
  title: string
  taskPrompt: string | null
  initialScore: number | null
  latestScore: number | null
  submitCount: number
  status: number
  createdAt: string
  updatedAt: string
}

export interface WritingDocumentsResponse {
  items: WritingDocumentItem[]
  total: number
}

export function getWritingDocuments(page = 0, size = 10): Promise<WritingDocumentsResponse> {
  return http
    .get<WritingDocumentsResponse>('/writing/documents', { params: { page, size } })
    .then((res) => res.data)
}

export interface DocumentEvaluationItem {
  id: number
  overallScore: number | null
  gaokaoScore: number | null
  band: string | null
  contentQuality: number | null
  taskAchievement: number | null
  structureScore: number | null
  vocabularyScore: number | null
  grammarScore: number | null
  expressionScore: number | null
  grammarErrorCount: number | null
  spellingErrorCount: number | null
  vocabularyErrorCount: number | null
  createdAt: string
}

export interface DocumentEvaluationsResponse {
  items: DocumentEvaluationItem[]
  total: number
}

export function getDocumentEvaluations(docId: string, page = 0, size = 20): Promise<DocumentEvaluationsResponse> {
  return http
    .get<DocumentEvaluationsResponse>(`/writing/documents/${encodeURIComponent(docId)}/evaluations`, { params: { page, size } })
    .then((res) => res.data)
}

export interface WritingStatsResponse {
  avgContentQuality: number | null
  avgTaskAchievement: number | null
  avgStructureScore: number | null
  avgVocabularyScore: number | null
  avgGrammarScore: number | null
  avgExpressionScore: number | null
  totalGrammarErrors: number
  totalSpellingErrors: number
  totalVocabularyErrors: number
}

export function getWritingStats(): Promise<WritingStatsResponse> {
  return http.get<WritingStatsResponse>('/writing/stats').then((res) => res.data)
}

// ── Essay Prompts (历年真题) ──

export interface EssayPromptItem {
  id: number
  paper: string
  title: string
  promptText: string
  examYear: number | null
  imageUrl: string | null
  imageDescription: string | null
  materialText: string | null
  task: string | null
  wordCountMin: number | null
  wordCountMax: number | null
  maxScore: number | null
  source: string | null
}

export interface EssayPromptListResponse {
  items: EssayPromptItem[]
  total: number
  years: number[]
}

export function getEssayPrompts(params: {
  stageId?: number
  keyword?: string
  year?: number
  page?: number
  size?: number
}): Promise<EssayPromptListResponse> {
  return http
    .get<EssayPromptListResponse>('/writing/prompts', { params })
    .then((res) => res.data)
}

export interface StageConfigResponse {
  code: string
  name: string
  minWordCount: number
}

export function getStageConfig(stageCode: string): Promise<StageConfigResponse> {
  return http
    .get<StageConfigResponse>(`/writing/stage-config/${encodeURIComponent(stageCode)}`)
    .then((res) => res.data)
}


