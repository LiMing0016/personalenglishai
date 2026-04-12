/**
 * 写作评分接口：POST /api/writing/evaluate
 */
import { http } from './http'

export type WritingAiProvider = 'openai' | 'kimi' | 'qwen'

export interface WritingEvaluateRequest {
  essay: string
  aiHint?: string
  aiProvider?: WritingAiProvider
  mode?: 'free' | 'exam'
  lang?: string
  taskPrompt?: string
  documentId?: string
  studyStage?: string
  topicTitle?: string
  genre?: string | null
  examType?: string | null
  taskType?: string | null
  minWords?: number | null
  recommendedMaxWords?: number | null
  maxScore?: number | null
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
  display_error_count?: number
  raw_error_count?: number
  suggestion_count?: number
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
    lang_category?: string
    alternatives?: string[]
    /** Source engine: lt | sapling | trinka | textgears | gpt */
    engine?: string
    raw_engine_meta?: {
      type?: number
      top_category_id?: number
      top_category_name?: string
      comment?: string
      pipeline?: string
      error_category?: string
      lang_category?: string
      critical_error?: boolean
    }
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
      aiProvider: payload.aiProvider ?? undefined,
      mode: normalizedMode,
      lang: payload.lang ?? 'en',
      taskPrompt,
      documentId: payload.documentId ?? undefined,
      studyStage: payload.studyStage ?? undefined,
      topicTitle: payload.topicTitle ?? undefined,
      genre: payload.genre ?? undefined,
      examType: payload.examType ?? undefined,
      taskType: payload.taskType ?? undefined,
      minWords: payload.minWords ?? undefined,
      recommendedMaxWords: payload.recommendedMaxWords ?? undefined,
      maxScore: payload.maxScore ?? undefined,
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
      aiProvider: payload.aiProvider ?? undefined,
      mode: normalizedMode,
      lang: payload.lang ?? 'en',
      taskPrompt,
      documentId: payload.documentId ?? undefined,
      studyStage: payload.studyStage ?? undefined,
      topicTitle: payload.topicTitle ?? undefined,
      genre: payload.genre ?? undefined,
      examType: payload.examType ?? undefined,
      taskType: payload.taskType ?? undefined,
      minWords: payload.minWords ?? undefined,
      recommendedMaxWords: payload.recommendedMaxWords ?? undefined,
      maxScore: payload.maxScore ?? undefined,
    }, { timeout: 60000 })
    .then((res) => res.data)
}

export function getEvaluateTask(
  requestId: string,
  documentId?: string
): Promise<WritingEvaluateTaskStatusResponse> {
  const params = documentId ? { documentId } : undefined
  return http
    .get<WritingEvaluateTaskStatusResponse>(`/writing/evaluate/tasks/${encodeURIComponent(requestId)}`, { params })
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
  aiProvider?: WritingAiProvider
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

export interface TrustedRewriteSegment {
  doc_id: string
  sentence_text: string
  normalized_text_hash: string
  left_context: string
  right_context: string
  tier: 'advanced' | 'perfect'
  source: string
  updated_at: number
}

export interface PolishRequest {
  original: string
  aiProvider?: WritingAiProvider
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
  aiProvider?: WritingAiProvider
  tier: PolishTier
  studyStage?: string | null
  writingMode?: 'free' | 'exam'
  topicContent?: string
  taskPrompt?: string
  taskType?: string | null
  minWords?: number | null
  recommendedMaxWords?: number | null
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

export type PolishTopicAlignmentStatus = 'aligned' | 'partial' | 'off_topic'
export type PolishRewriteMode =
  | 'rubric_polish'
  | 'topic_correction_then_polish'
  | 'corrected_rewrite'
  | 'fallback_polish'

export interface PolishDirectionSnapshot {
  relevance?: string | null
  taskCompletion?: string | null
  coverage?: string | null
  maxBand?: string | null
}

export interface PolishEssayResponse {
  rubricKey?: string
  policyKey?: string | null
  polishRubricKey?: string | null
  route?: PolishRewriteMode | null
  processingModeLabel?: string | null
  topicAlignmentStatus?: PolishTopicAlignmentStatus | null
  rewriteMode?: PolishRewriteMode | null
  baselineBand?: string | null
  baselineScore?: number | null
  baselineGrades?: Partial<Record<DimensionKey, GradeLetter>>
  finalBand?: string | null
  finalScore?: number | null
  finalGrades?: Partial<Record<DimensionKey, GradeLetter>>
  sourceBandRank?: number | null
  targetBandRank?: number | null
  accepted?: boolean | null
  guardTriggered?: boolean | null
  fallbackToOriginal?: boolean | null
  targetMet?: boolean | null
  attemptCount?: number | null
  targetTier?: PolishTier | null
  targetGap?: string | null
  bestEffort?: boolean | null
  baselineDirection?: PolishDirectionSnapshot | null
  finalDirection?: PolishDirectionSnapshot | null
  bindingReason?: string | null
  unmetCoreDimensions?: string[]
  polishedEssay?: string | null
  summary?: PolishEssaySummary
  sentences: SentencePolish[]
}

export function polishEssay(req: PolishEssayRequest): Promise<PolishEssayResponse> {
  return http
    .post<PolishEssayResponse>('/writing/polish-essay', req, { timeout: 120000 })
    .then((res) => res.data)
}

// ── Model Essay (范文) ──

export interface WritingModelEssayRequest {
  essay: string
  aiProvider?: WritingAiProvider
  studyStage?: string | null
  writingMode?: 'free' | 'exam'
  taskType?: string | null
  topicContent?: string | null
  taskPrompt?: string | null
  minWords?: number | null
  recommendedMaxWords?: number | null
}

export interface ModelEssayCard {
  label: string
  essay: string
  summary?: string | null
  highScoreReasons: string[]
  improvementGuidance: string[]
}

export interface WritingModelEssayResponse {
  rubricKey?: string | null
  mode?: 'free' | 'exam'
  stage?: string | null
  topicContent?: string | null
  taskPrompt?: string | null
  excellentEssay: ModelEssayCard
  perfectEssay: ModelEssayCard
}

export function generateModelEssay(req: WritingModelEssayRequest): Promise<WritingModelEssayResponse> {
  return http
    .post<WritingModelEssayResponse>('/writing/model-essay', req, { timeout: 120000 })
    .then((res) => ({
      rubricKey: res.data.rubricKey ?? null,
      mode: res.data.mode === 'exam' ? 'exam' : 'free',
      stage: res.data.stage ?? null,
      topicContent: res.data.topicContent ?? null,
      taskPrompt: res.data.taskPrompt ?? null,
      excellentEssay: {
        label: res.data.excellentEssay?.label ?? '优秀作文',
        essay: res.data.excellentEssay?.essay ?? '',
        summary: res.data.excellentEssay?.summary ?? null,
        highScoreReasons: res.data.excellentEssay?.highScoreReasons ?? [],
        improvementGuidance: res.data.excellentEssay?.improvementGuidance ?? [],
      },
      perfectEssay: {
        label: res.data.perfectEssay?.label ?? '满分作文',
        essay: res.data.perfectEssay?.essay ?? '',
        summary: res.data.perfectEssay?.summary ?? null,
        highScoreReasons: res.data.perfectEssay?.highScoreReasons ?? [],
        improvementGuidance: res.data.perfectEssay?.improvementGuidance ?? [],
      },
    }))
}

export interface RewriteApplyRequest {
  docId: string
  essay: string
  start: number
  end: number
  original: string
  replacement: string
  tier: PolishTier
}

export interface RewriteApplyResponse {
  trusted: boolean
  hard_error_count: number
  message?: string
  record?: TrustedRewriteSegment | null
}

export function rewriteApply(req: RewriteApplyRequest): Promise<RewriteApplyResponse> {
  return http
    .post<RewriteApplyResponse>('/writing/rewrite/apply', req, { timeout: 25000 })
    .then((res) => res.data)
}

export function clearTrustedRewrite(docId: string): Promise<void> {
  return http.post('/writing/rewrite/trusted/clear', { docId }).then(() => {})
}


// ── Template Extract (作文模板提炼) ──

export interface WritingTemplateRequest {
  text: string
  aiProvider?: WritingAiProvider
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
  aiProvider?: WritingAiProvider
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
  aiProvider?: WritingAiProvider
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

// ── Handwriting Import ──

export interface RecognizeHandwritingImageRequest {
  imageBase64: string
  aiProvider?: WritingAiProvider
}

export interface RecognizeHandwritingImageResponse {
  imageUrl?: string | null
  recognizedText?: string | null
  normalizedText?: string | null
  confidence?: number | null
}

export interface BindHandwritingImportRequest {
  docId: string
  sourceType?: string | null
  imageUrl: string
  recognizedText: string
}

export function recognizeHandwritingImage(
  payload: RecognizeHandwritingImageRequest,
  options?: { signal?: AbortSignal },
): Promise<RecognizeHandwritingImageResponse> {
  return http
    .post<RecognizeHandwritingImageResponse>('/writing/recognize-handwriting-image', {
      imageBase64: payload.imageBase64,
      aiProvider: payload.aiProvider ?? undefined,
    }, {
      timeout: 120000,
      signal: options?.signal,
    })
    .then((res) => ({
      imageUrl: res.data.imageUrl ?? null,
      recognizedText: res.data.recognizedText ?? null,
      normalizedText: res.data.normalizedText ?? null,
      confidence:
        res.data.confidence == null
          ? null
          : Number(res.data.confidence),
    }))
}

export function bindHandwritingImport(
  payload: BindHandwritingImportRequest,
  options?: { signal?: AbortSignal },
): Promise<WritingSessionMetadataResponse> {
  return http
    .post<WritingSessionMetadataResponse>('/writing/bind-handwriting-import', {
      docId: payload.docId,
      sourceType: payload.sourceType ?? undefined,
      imageUrl: payload.imageUrl,
      recognizedText: payload.recognizedText,
    }, {
      timeout: 30000,
      signal: options?.signal,
    })
    .then((res) => ({
      documentId: res.data.documentId,
      metadataId: res.data.metadataId,
      promptSheetId: res.data.promptSheetId ?? null,
      mode: res.data.mode === 'exam' ? 'exam' : 'free',
      studyStage: res.data.studyStage ?? null,
      titleSnapshot: res.data.titleSnapshot,
      topicTitle: res.data.topicTitle ?? null,
      promptText: res.data.promptText ?? null,
      attachmentImageUrl: res.data.attachmentImageUrl ?? null,
      genre: res.data.genre ?? null,
      sourceType: res.data.sourceType,
      latestHandwrittenSourceType: res.data.latestHandwrittenSourceType ?? null,
      latestHandwrittenSourceImageUrl: res.data.latestHandwrittenSourceImageUrl ?? null,
      latestHandwrittenRecognizedText: res.data.latestHandwrittenRecognizedText ?? null,
      latestHandwrittenImportedAt: res.data.latestHandwrittenImportedAt ?? null,
      createdAt: res.data.createdAt,
      updatedAt: res.data.updatedAt,
      examMetadataId: res.data.examMetadataId ?? null,
      examType: res.data.examType ?? null,
      taskType: res.data.taskType ?? null,
      minWords: res.data.minWords ?? null,
      recommendedMaxWords: res.data.recommendedMaxWords ?? null,
      maxScore: res.data.maxScore ?? null,
    }))
}

export function toggleEssayFavorite(id: number): Promise<{ favorited: boolean }> {
  return http
    .post<{ favorited: boolean }>(`/writing/history/${id}/favorite`)
    .then((res) => res.data)
}

// ── Grammar Check (LanguageTool + Sapling) ──

export type GrammarCheckMode = 'lite' | 'power'

export interface GrammarCheckRequest {
  text: string
  docId?: string
  trinkaMode?: GrammarCheckMode
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
      timeout: 25000,
      signal: options?.signal,
    })
    .then((res) => res.data)
}

// ── Grammar Suppress (dismiss / fix) ──

export interface GrammarSuppressRequest {
  docId: string
  original: string
  suggestion?: string
  ruleType?: string
  engine?: string
  context?: string
  action: 'dismiss' | 'fix'
}

export function grammarSuppress(payload: GrammarSuppressRequest): Promise<void> {
  return http.post('/writing/grammar/suppress', payload).then(() => {})
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
  aiProvider?: WritingAiProvider,
  options?: { signal?: AbortSignal }
): Promise<SuggestionsResponse> {
  return http
    .post<SuggestionsResponse>('/writing/suggestions', { text, aiProvider: aiProvider ?? undefined }, {
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
      aiProvider: payload.aiProvider ?? undefined,
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
  genre?: string | null | null
  wordRange?: string | null
  requirements?: string | null
  studyStage?: string | null
}

export interface AuditTopicResponse {
  status: 'complete' | 'need_more_info' | 'invalid'
  topic?: string
  promptType?: 'general' | 'material' | 'chart' | 'comic'
  genre?: string | null | null
  wordRange?: string | null
  requirements?: string | null
  message?: string
}

export function auditTopic(
  req: AuditTopicRequest,
  options?: { signal?: AbortSignal },
): Promise<AuditTopicResponse> {
  return http
    .post<AuditTopicResponse>('/writing/audit-topic', req, {
      timeout: 35000,
      signal: options?.signal,
    })
    .then((res) => res.data)
}

export interface ExamPromptChartSpec {
  title?: string | null
  displayType?: string | null
  columns: string[]
  rows: string[][]
  summary?: string | null
}

export interface ExamPromptComicScene {
  title?: string | null
  description: string
  dialogue?: string | null
}

export interface GenerateExamPromptRequest {
  originalInput: string
  topic: string
  studyStage?: string | null
  promptType?: 'general' | 'material' | 'chart' | 'comic'
  taskType?: string | null
  genre?: string | null
  wordRange?: string | null
  requirements?: string | null
  maxScore?: number | null
  aiProvider?: WritingAiProvider
}

export interface GenerateExamPromptResponse {
  promptType: 'general' | 'material' | 'chart' | 'comic'
  paper?: string | null
  promptSheetId?: number | null
  topic: string
  promptText: string
  part?: string | null
  questionNo?: string | null
  directions?: string | null
  requirements?: string | null
  genre?: string | null
  wordRange?: string | null
  maxScore?: number | null
  sourceType: 'ai_generated'
  taskType?: string | null
  minWords?: number | null
  recommendedMaxWords?: number | null
  attachmentType?: 'none' | 'material' | 'visual' | null
  attachmentTitle?: string | null
  attachmentContent?: string | null
  attachmentImageUrl?: string | null
  visualKind?: 'image' | 'comic' | 'chart' | 'table' | null
  materialText?: string | null
  chartSpec?: ExamPromptChartSpec | null
  comicScenes?: ExamPromptComicScene[]
}

export function generateExamPrompt(
  req: GenerateExamPromptRequest,
  options?: { signal?: AbortSignal },
): Promise<GenerateExamPromptResponse> {
  return http
    .post<GenerateExamPromptResponse>('/writing/generate-exam-prompt', req, {
      timeout: 120000,
      signal: options?.signal,
    })
    .then((res) => ({
      promptType: res.data.promptType ?? 'general',
      paper: res.data.paper ?? null,
      promptSheetId: res.data.promptSheetId ?? null,
      topic: res.data.topic ?? req.topic,
      promptText: res.data.promptText ?? req.topic,
      part: res.data.part ?? null,
      questionNo: res.data.questionNo ?? null,
      directions: res.data.directions ?? null,
      requirements: res.data.requirements ?? null,
      genre: res.data.genre ?? null,
      wordRange: res.data.wordRange ?? null,
      maxScore: res.data.maxScore ?? null,
      sourceType: 'ai_generated',
      taskType: res.data.taskType ?? null,
      minWords: res.data.minWords ?? null,
      recommendedMaxWords: res.data.recommendedMaxWords ?? null,
      attachmentType: res.data.attachmentType ?? null,
      attachmentTitle: res.data.attachmentTitle ?? null,
      attachmentContent: res.data.attachmentContent ?? null,
      attachmentImageUrl: res.data.attachmentImageUrl ?? null,
      visualKind: res.data.visualKind ?? null,
      materialText: res.data.materialText ?? null,
      chartSpec: res.data.chartSpec
        ? {
            title: res.data.chartSpec.title ?? null,
            displayType: res.data.chartSpec.displayType ?? null,
            columns: res.data.chartSpec.columns ?? [],
            rows: res.data.chartSpec.rows ?? [],
            summary: res.data.chartSpec.summary ?? null,
          }
        : null,
      comicScenes: res.data.comicScenes ?? [],
    }))
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
  studyStage?: string | null
  sourceType?: 'manual' | 'past_prompt' | 'ai_generated' | 'free_input'
  titleSnapshot?: string
  topicTitle?: string
  promptText?: string
  promptSheetId?: number | null
  attachmentImageUrl?: string | null
  genre?: string | null
  examType?: string | null
  taskType?: string | null
  minWords?: number | null
  recommendedMaxWords?: number | null
  maxScore?: number | null
}


export interface WritingSessionMetadataResponse {
  documentId: string
  metadataId: number
  promptSheetId?: number | null
  mode: 'free' | 'exam'
  studyStage?: string | null
  titleSnapshot: string
  topicTitle?: string | null
  promptText?: string | null
  attachmentImageUrl?: string | null
  genre?: string | null | null
  sourceType: 'manual' | 'past_prompt' | 'ai_generated' | 'free_input'
  latestHandwrittenSourceType?: string | null
  latestHandwrittenSourceImageUrl?: string | null
  latestHandwrittenRecognizedText?: string | null
  latestHandwrittenImportedAt?: string | null
  createdAt: string
  updatedAt: string
  examMetadataId?: number | null
  examType?: string | null
  taskType?: string | null
  minWords?: number | null
  recommendedMaxWords?: number | null
  maxScore?: number | null
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
  writingMetadata?: WritingSessionMetadataResponse | null
}

export function startWritingSession(req: StartSessionRequest): Promise<StartSessionResponse> {
  return http
    .post<StartSessionResponse>('/writing/start-session', req)
    .then((res) => res.data)
}

export function getWritingSessionMetadata(docId: string): Promise<WritingSessionMetadataResponse> {
  return http
    .get<WritingSessionMetadataResponse>(`/writing/documents/${encodeURIComponent(docId)}/metadata`)
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





