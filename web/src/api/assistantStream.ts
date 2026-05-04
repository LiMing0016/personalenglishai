import { getToken } from '../utils/token.ts'

import type { AssistantStreamEvent } from '../types/assistantRequest.ts'

export function parseAssistantStreamChunk(chunk: string): Partial<AssistantStreamEvent>[] {
  return chunk
    .replace(/\r\n/g, '\n')
    .split(/\n\n+/)
    .map((block) => block.trim())
    .filter(Boolean)
    .map((block) =>
      block
        .split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice('data:'.length).trim())
        .join('\n'),
    )
    .filter(Boolean)
    .flatMap((payload) => {
      try {
        return [JSON.parse(payload) as Partial<AssistantStreamEvent>]
      } catch {
        return []
      }
    })
}

export async function streamAssistantEvents(
  url: string,
  payload: unknown,
  onEvent: (event: Partial<AssistantStreamEvent>) => void,
) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(url, {
    method: 'POST',
    headers,
    credentials: 'include',
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    throw new Error(`Request failed with status code ${response.status}`)
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let pending = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    pending += decoder.decode(value, { stream: true })
    pending = pending.replace(/\r\n/g, '\n')
    const boundary = pending.lastIndexOf('\n\n')
    if (boundary < 0) continue
    const complete = pending.slice(0, boundary + 2)
    pending = pending.slice(boundary + 2)
    parseAssistantStreamChunk(complete).forEach(onEvent)
  }

  pending += decoder.decode()
  parseAssistantStreamChunk(pending).forEach(onEvent)
}
