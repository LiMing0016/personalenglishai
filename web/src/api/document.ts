import { http } from './http'

export interface CreateDocRequest {
  title?: string
  content?: string
}

export interface CreateDocResponse {
  docId: string
  latestRevision: number
}

export function createDocument(payload: CreateDocRequest): Promise<CreateDocResponse> {
  return http
    .post<CreateDocResponse>('/docs', {
      title: payload.title ?? '',
      content: payload.content ?? '',
    })
    .then((res) => res.data)
}

export interface DocContentResponse {
  title: string
  latestRevision: number
  content: string
  taskPrompt?: string | null
}

export function getDocumentContent(docId: string): Promise<DocContentResponse> {
  return http
    .get<DocContentResponse>(`/docs/${encodeURIComponent(docId)}`)
    .then((res) => res.data)
}

export interface SaveRevisionResponse {
  latestRevision: number
}

export function saveDocumentContent(
  docId: string,
  content: string,
  expectedLatestRevision: number,
): Promise<SaveRevisionResponse> {
  return http
    .post<SaveRevisionResponse>(`/docs/${encodeURIComponent(docId)}/revisions`, {
      content,
      expectedLatestRevision,
    })
    .then((res) => res.data)
}

export function renameDocument(docId: string, title: string): Promise<void> {
  return http
    .patch(`/docs/${encodeURIComponent(docId)}/title`, { title })
    .then(() => {})
}

export function deleteDocument(docId: string): Promise<void> {
  return http
    .delete(`/docs/${encodeURIComponent(docId)}`)
    .then(() => {})
}
