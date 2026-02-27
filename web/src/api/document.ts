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
