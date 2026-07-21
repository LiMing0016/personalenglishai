import axios from 'axios'
import type { AxiosResponse } from 'axios'

import { http } from './http'

export type VocabularyCardStatus = 'captured' | 'generating' | 'ready' | 'needs_review' | 'failed'
export type VocabularyTemplateKey = 'basic' | 'exam' | 'reading'
export type VocabularyConflictStatus = 'none' | 'needs_review'
export type VocabularyGenerationOutcome = 'complete' | 'partial' | 'failed'
export type VocabularyRecognitionStatus = 'accepted' | 'suspected_typo'
export type VocabularyRecognitionWarning =
  | 'CANDIDATE_LIMIT_REACHED'
  | 'DICTIONARY_VERIFICATION_UNAVAILABLE'

export interface VocabularyTemplate {
  key: VocabularyTemplateKey
  version: number
  name: string
  fields: string[]
}

export interface VocabularyTemplateCatalog {
  items: VocabularyTemplate[]
  defaultTemplateKey: VocabularyTemplateKey
}

export interface VocabularyCoreContent {
  schemaVersion: 1
  term: string
  phonetics: Array<{
    region: 'uk' | 'us' | 'other'
    text: string
    audioUrl: string | null
  }>
  senses: Array<{
    partOfSpeech: string
    meanings: Array<{
      definitionEn: string
      definitionZh: string
    }>
  }>
}

export interface VocabularyTheme {
  themeUid: string
  ownerType: 'system' | 'user'
  name: string
  purpose: string
  version: number
  status: 'active' | 'disabled'
  system: boolean
  defaultTheme: boolean
  recent: boolean
  promptStrategyKey: string
}

export interface VocabularyThemeCatalog {
  systemThemes: VocabularyTheme[]
  userThemes: VocabularyTheme[]
  defaultThemeUid: string
  recentThemeUids: string[]
}

export interface VocabularyThemeSnapshot {
  themeUid: string
  name: string
  purpose: string
}

export interface CreateVocabularyThemeRequest {
  name: string
  purpose: string
}

export type UpdateVocabularyThemeRequest = CreateVocabularyThemeRequest

export interface VocabularyCaptureSource {
  type: 'manual' | 'ocr_image'
  sourceRef?: string
  sourceTitle: string
  sourceUrl?: string
  contextText?: string
  metadata: Record<string, unknown>
}

export interface VocabularyCaptureItemSource {
  contextText?: string
  metadata: Record<string, unknown>
}

export interface VocabularyCaptureRequest {
  clientRequestId: string
  terms: string[]
  language: 'en'
  themeUid?: string
  templateKey?: VocabularyTemplateKey
  source: VocabularyCaptureSource
  itemSources?: VocabularyCaptureItemSource[]
}

export interface VocabularyImageRecognitionSuggestion {
  term: string
  dictionaryVerified: boolean
}

export interface VocabularyImageRecognitionItem {
  itemId: string
  observedText: string
  normalizedTerm: string
  status: VocabularyRecognitionStatus
  suggestions: VocabularyImageRecognitionSuggestion[]
  contextText: string | null
  confidence: number
}

export interface VocabularyImageRecognitionGeneration {
  provider: string
  model: string
  promptVersion: string
  modelCallCount: number
  traceId: string
  usage: {
    inputTokens: number
    outputTokens: number
  } | null
}

export interface VocabularyImageRecognitionResponse {
  contractVersion: 1
  traceId: string
  rawText: string
  warnings: VocabularyRecognitionWarning[]
  items: VocabularyImageRecognitionItem[]
  generation: VocabularyImageRecognitionGeneration
}

export interface VocabularyCaptureItem {
  term: string
  cardUid: string | null
  action: string
  status: VocabularyCardStatus
}

export interface VocabularyCaptureResponse {
  items: VocabularyCaptureItem[]
}

export type VocabularyProductEventName =
  | 'vocabulary_image_recognition_started'
  | 'vocabulary_image_recognition_completed'
  | 'vocabulary_image_candidates_confirmed'
  | 'vocabulary_capture_submitted'
  | 'vocabulary_cards_ready'
  | 'vocabulary_learning_started'

export type VocabularyProductEventProperty = string | number | boolean | Array<string | number | boolean>

export interface VocabularyProductEventBatch {
  events: Array<{
    eventUid: string
    eventName: VocabularyProductEventName
    traceId?: string
    sessionId: string
    cardUid?: string
    occurredAt: string
    properties: Record<string, VocabularyProductEventProperty>
  }>
}

export interface VocabularyProductEventBatchResponse {
  accepted: number
  duplicate: number
}

export interface VocabularyCardSummary {
  cardUid: string
  displayTerm: string
  normalizedTerm: string
  templateKey: VocabularyTemplateKey
  status: VocabularyCardStatus
  activeRevisionUid: string | null
  sourceTypes: string[]
  lastCapturedAt: string | null
  updatedAt: string | null
  candidateRevisionUid: string | null
  conflictStatus: VocabularyConflictStatus
  generationStatus: string | null
  generationError: string | null
  generationOutcome: VocabularyGenerationOutcome | null
  warning: string | null
  phonetic: string | null
  coreDefinition: string | null
  sourceCount: number
}

export interface VocabularyCardSource {
  sourceUid: string
  sourceType: string
  sourceRef: string | null
  sourceTitle: string | null
  sourceUrl: string | null
  contextText: string | null
  rawTerm: string | null
  metadata: unknown
  capturedAt: string | null
  createdAt: string | null
}

export interface VocabularyCardDetail extends VocabularyCardSummary {
  language: string
  templateVersion: number | null
  content: unknown
  theme: VocabularyThemeSnapshot | null
  themeVersion: number | null
  core: VocabularyCoreContent | null
  markdown: string | null
  contentFormatVersion: number | null
  sources: VocabularyCardSource[]
  createdAt: string | null
  candidateContent: unknown
}

export interface VocabularyCardFilters {
  keyword?: string
  status?: VocabularyCardStatus
  sourceType?: string
  sort?: 'recent' | 'az'
  page?: number
  size?: number
}

export interface VocabularyCardPage {
  items: VocabularyCardSummary[]
  total: number
  page: number
  size: number
}

export interface VocabularyRevision {
  revisionUid: string
  baseRevisionUid: string | null
  authorType: string
  templateKey: VocabularyTemplateKey
  templateVersion: number | null
  content: unknown
  theme: VocabularyThemeSnapshot | null
  themeVersion: number | null
  core: VocabularyCoreContent | null
  markdown: string | null
  contentFormatVersion: number | null
  changeSummary: string | null
  active: boolean
  candidate: boolean
  createdAt: string | null
}

export interface VocabularyRevisionListResponse {
  currentRevisionUid: string | null
  candidateRevisionUid: string | null
  conflictStatus: VocabularyConflictStatus
  items: VocabularyRevision[]
}

export interface VocabularyGenerationJobResponse {
  jobUid: string
  status: string
}

export interface RegenerateVocabularyCardRequest {
  themeUid?: string
  useLatestThemeVersion?: boolean
  templateKey?: VocabularyTemplateKey
}

export interface UpdateVocabularyCardRequest {
  baseRevisionUid: string
  core?: VocabularyCoreContent | null
  markdown?: string | null
  content?: unknown
  changeSummary?: string
}

export interface ResolveVocabularyConflictRequest {
  choice: 'keep_current' | 'use_ai' | 'merge_fields'
  mergeFields?: Record<string, unknown>
}

export interface VocabularyConflictResponse {
  currentRevisionUid: string | null
  candidateRevisionUid: string | null
  currentContent: unknown
  candidateContent: unknown
  currentContentFormatVersion?: number | null
  candidateContentFormatVersion?: number | null
  conflictStatus: VocabularyConflictStatus
}

type ApiEnvelope<T> = {
  code?: string
  message?: string
  data?: T | null
}

const VOCABULARY_CONFLICT_CODE = '409030'

export class VocabularyConflictError extends Error {
  readonly conflict: VocabularyConflictResponse

  constructor(conflict: VocabularyConflictResponse) {
    super('Vocabulary card revision conflict')
    this.name = 'VocabularyConflictError'
    this.conflict = conflict
  }
}

function hasData<T>(body: ApiEnvelope<T>): body is ApiEnvelope<T> & { data: T } {
  return Object.prototype.hasOwnProperty.call(body, 'data')
}

async function unwrap<T>(
  request: Promise<AxiosResponse<ApiEnvelope<T>>>,
  allowEmptyData = false,
): Promise<T> {
  try {
    const response = await request
    if (allowEmptyData && response.data.data == null) {
      return undefined as T
    }
    if (!hasData(response.data)) {
      throw new Error('Vocabulary API response is missing data')
    }
    return response.data.data
  } catch (error) {
    if (axios.isAxiosError<ApiEnvelope<VocabularyConflictResponse>>(error)) {
      const body = error.response?.data
      if (body && body.code === VOCABULARY_CONFLICT_CODE && hasData(body)) {
        throw new VocabularyConflictError(body.data)
      }
    }
    throw error
  }
}

export const listVocabularyTemplates = () =>
  unwrap<VocabularyTemplateCatalog>(http.get('/vocabulary/templates'))

export const listVocabularyThemes = () =>
  unwrap<VocabularyThemeCatalog>(http.get('/vocabulary/themes'))

export const createVocabularyTheme = (payload: CreateVocabularyThemeRequest) =>
  unwrap<VocabularyTheme>(http.post('/vocabulary/themes', payload))

export const updateVocabularyTheme = (themeUid: string, payload: UpdateVocabularyThemeRequest) =>
  unwrap<VocabularyTheme>(
    http.put(`/vocabulary/themes/${encodeURIComponent(themeUid)}`, payload),
  )

export const copyVocabularyTheme = (themeUid: string) =>
  unwrap<VocabularyTheme>(
    http.post(`/vocabulary/themes/${encodeURIComponent(themeUid)}/copy`),
  )

export const setDefaultVocabularyTheme = (themeUid: string) =>
  unwrap<void>(
    http.post(`/vocabulary/themes/${encodeURIComponent(themeUid)}/default`),
    true,
  )

export const disableVocabularyTheme = (themeUid: string) =>
  unwrap<void>(
    http.post(`/vocabulary/themes/${encodeURIComponent(themeUid)}/disable`),
    true,
  )

export const deleteVocabularyTheme = (themeUid: string) =>
  unwrap<void>(http.delete(`/vocabulary/themes/${encodeURIComponent(themeUid)}`), true)

export const captureVocabulary = (payload: VocabularyCaptureRequest) =>
  unwrap<VocabularyCaptureResponse>(http.post('/vocabulary/captures', payload))

export const submitVocabularyProductEvents = (payload: VocabularyProductEventBatch) =>
  unwrap<VocabularyProductEventBatchResponse>(http.post('/vocabulary/product-events/batch', payload))

export const recognizeVocabularyImage = ({
  file,
  signal,
}: {
  file: File
  signal: AbortSignal
}) => {
  const formData = new FormData()
  formData.append('file', file)
  return unwrap<VocabularyImageRecognitionResponse>(
    http.post('/vocabulary/image-recognitions', formData, { timeout: 60_000, signal }),
  )
}

export const listVocabularyCards = (params: VocabularyCardFilters) =>
  unwrap<VocabularyCardPage>(http.get('/vocabulary/cards', { params }))

export const getVocabularyCard = (cardUid: string) =>
  unwrap<VocabularyCardDetail>(http.get(`/vocabulary/cards/${encodeURIComponent(cardUid)}`))

export const updateVocabularyCard = (cardUid: string, payload: UpdateVocabularyCardRequest) =>
  unwrap<VocabularyCardDetail>(http.put(`/vocabulary/cards/${encodeURIComponent(cardUid)}`, payload))

export const deleteVocabularyCard = (cardUid: string) =>
  unwrap<void>(http.delete(`/vocabulary/cards/${encodeURIComponent(cardUid)}`), true)

export const regenerateVocabularyCard = (cardUid: string, payload: RegenerateVocabularyCardRequest) =>
  unwrap<VocabularyGenerationJobResponse>(
    http.post(`/vocabulary/cards/${encodeURIComponent(cardUid)}/regenerate`, payload),
  )

export const retryVocabularyCard = (cardUid: string) =>
  unwrap<VocabularyGenerationJobResponse>(
    http.post(`/vocabulary/cards/${encodeURIComponent(cardUid)}/retry`),
  )

export const listVocabularyRevisions = (cardUid: string) =>
  unwrap<VocabularyRevisionListResponse>(
    http.get(`/vocabulary/cards/${encodeURIComponent(cardUid)}/revisions`),
  )

export const resolveVocabularyConflict = (
  cardUid: string,
  revisionUid: string,
  payload: ResolveVocabularyConflictRequest,
) => unwrap<VocabularyCardDetail>(
  http.post(
    `/vocabulary/cards/${encodeURIComponent(cardUid)}/conflicts/${encodeURIComponent(revisionUid)}/resolve`,
    payload,
  ),
)
