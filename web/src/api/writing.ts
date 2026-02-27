/**
 * 写作评分接口：POST /api/writing/evaluate
 */
import { http } from './http'

export interface WritingEvaluateRequest {
  essay: string
  aiHint?: string
  mode?: 'free' | 'guided'
  lang?: string
}

export interface WritingEvaluateResponse {
  requestId: string
  score: {
    overall: number
    task: number
    coherence: number
    lexical: number
    grammar: number
  }
  summary: string
  errors: Array<{
    id: string
    type: 'grammar' | 'spelling' | 'word_choice' | 'coherence' | 'punctuation'
    severity: 'minor' | 'major'
    span: { start: number; end: number }
    suggestion?: string
  }>
}

export function evaluateWriting(
  payload: WritingEvaluateRequest
): Promise<WritingEvaluateResponse> {
  return http
    .post<WritingEvaluateResponse>('/writing/evaluate', {
      essay: payload.essay,
      aiHint: payload.aiHint ?? undefined,
      mode: payload.mode ?? 'free',
      lang: payload.lang ?? 'en',
    })
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

export function chatWriting(payload: WritingChatRequest): Promise<WritingChatResponse> {
  return http
    .post<WritingChatResponse>('/writing/chat', {
      essay: payload.essay,
      instruction: payload.instruction,
      lang: payload.lang ?? 'en',
      mode: payload.mode ?? 'free',
      aiHint: payload.aiHint ?? undefined,
      selectedText: payload.selectedText?.trim() || undefined,
    })
    .then((res) => res.data)
}
