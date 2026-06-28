import { http } from './http'
import type { LearningAssetCopilotAction, LearningAssetType } from '../types/learningAssets.ts'
export type { LearningAssetCopilotAction }

interface ApiEnvelope<T> {
  code?: string
  message?: string
  data?: T | null
}

export class EmptyApiDataError extends Error {
  constructor(message = '接口没有返回数据') {
    super(message)
    this.name = 'EmptyApiDataError'
  }
}

export interface LearningNotePayload {
  type: LearningAssetType
  title: string
  contentMarkdown: string
  structuredPayload?: string | null
  sourceConversationId?: string
  sourceMessageId?: string
  sourceText?: string
}

export interface LearningNoteDto extends LearningNotePayload {
  noteUid: string
  status?: string
  createdAt?: string | null
  updatedAt?: string | null
}

export interface LearningNoteListResult {
  items: LearningNoteDto[]
  total: number
  page: number
  size: number
}

export interface LearningCanvasOrganizePayload {
  type: LearningAssetType
  title: string
  selectedText?: string
  contextText?: string
  currentMarkdown?: string
  mode?: 'create' | 'format'
  action: LearningAssetCopilotAction
  instruction?: string
}

export interface LearningCanvasOrganizeResult {
  candidateMarkdown: string
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object'
}

function isApiEnvelope<T>(body: unknown): body is ApiEnvelope<T> {
  return isRecord(body) && ('code' in body || 'message' in body || 'data' in body)
}

function unwrap<T>(body: ApiEnvelope<T> | T): T {
  if (!isApiEnvelope<T>(body)) {
    return body as T
  }

  if (body.code && body.code !== '0') {
    throw new Error(body.message || '请求失败')
  }

  if (body.data === undefined || body.data === null) {
    throw new EmptyApiDataError(body.message || '接口没有返回数据')
  }
  return body.data
}

export async function createLearningNote(payload: LearningNotePayload): Promise<LearningNoteDto> {
  const res = await http.post<ApiEnvelope<LearningNoteDto>>('/learning-notes', payload)
  return unwrap(res.data)
}

export async function updateLearningNote(
  noteUid: string,
  payload: LearningNotePayload,
): Promise<LearningNoteDto> {
  const res = await http.put<ApiEnvelope<LearningNoteDto>>(
    `/learning-notes/${encodeURIComponent(noteUid)}`,
    payload,
  )
  return unwrap(res.data)
}

export async function getLearningNote(noteUid: string): Promise<LearningNoteDto> {
  const res = await http.get<ApiEnvelope<LearningNoteDto>>(
    `/learning-notes/${encodeURIComponent(noteUid)}`,
  )
  return unwrap(res.data)
}

export async function deleteLearningNote(noteUid: string): Promise<void> {
  await http.delete(`/learning-notes/${encodeURIComponent(noteUid)}`)
}

export async function listLearningNotes(params: {
  type?: LearningAssetType
  page?: number
  size?: number
} = {}): Promise<LearningNoteListResult> {
  const res = await http.get<ApiEnvelope<LearningNoteListResult>>('/learning-notes', {
    params: {
      type: params.type ?? 'vocabulary',
      page: params.page ?? 1,
      size: params.size ?? 20,
    },
  })
  return unwrap(res.data)
}

export async function organizeLearningAssetMarkdown(
  payload: LearningCanvasOrganizePayload,
): Promise<LearningCanvasOrganizeResult> {
  const res = await http.post<ApiEnvelope<LearningCanvasOrganizeResult>>('/learning-notes/organize', payload)
  return unwrap(res.data)
}
