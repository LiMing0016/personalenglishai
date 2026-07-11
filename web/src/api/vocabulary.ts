import axios from 'axios'
import type { AxiosResponse } from 'axios'

import { http } from './http'

export type VocabularyCardStatus = 'captured' | 'generating' | 'ready' | 'needs_review' | 'failed'
export type VocabularyTemplateKey = 'basic' | 'exam' | 'reading'
export type VocabularyConflictStatus = 'none' | 'needs_review'

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

export interface VocabularyCaptureSource {
  type: 'manual'
  sourceRef?: string
  sourceTitle: string
  sourceUrl?: string
  contextText?: string
  metadata: Record<string, unknown>
}

export interface VocabularyCaptureRequest {
  clientRequestId: string
  terms: string[]
  language: 'en'
  templateKey: VocabularyTemplateKey
  source: VocabularyCaptureSource
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
  sources: VocabularyCardSource[]
  createdAt: string | null
  candidateContent: unknown
}

export interface VocabularyCardFilters {
  keyword?: string
  status?: VocabularyCardStatus
  sourceType?: string
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

export interface UpdateVocabularyCardRequest {
  baseRevisionUid: string
  content: unknown
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

export const captureVocabulary = (payload: VocabularyCaptureRequest) =>
  unwrap<VocabularyCaptureResponse>(http.post('/vocabulary/captures', payload))

export const listVocabularyCards = (params: VocabularyCardFilters) =>
  unwrap<VocabularyCardPage>(http.get('/vocabulary/cards', { params }))

export const getVocabularyCard = (cardUid: string) =>
  unwrap<VocabularyCardDetail>(http.get(`/vocabulary/cards/${encodeURIComponent(cardUid)}`))

export const updateVocabularyCard = (cardUid: string, payload: UpdateVocabularyCardRequest) =>
  unwrap<VocabularyCardDetail>(http.put(`/vocabulary/cards/${encodeURIComponent(cardUid)}`, payload))

export const deleteVocabularyCard = (cardUid: string) =>
  unwrap<void>(http.delete(`/vocabulary/cards/${encodeURIComponent(cardUid)}`), true)

export const regenerateVocabularyCard = (cardUid: string) =>
  unwrap<VocabularyGenerationJobResponse>(
    http.post(`/vocabulary/cards/${encodeURIComponent(cardUid)}/regenerate`),
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
