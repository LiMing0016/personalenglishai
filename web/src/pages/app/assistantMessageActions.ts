import type { AssistantMessage } from './assistantMock.ts'
import type { AssistantSelection } from '../../types/assistantRequest.ts'

export const PENDING_ASSISTANT_PROMPT_KEY = 'peai:assistant:pending-prompt'
export const PENDING_ASSISTANT_SELECTION_KEY = 'peai:assistant:pending-selection'

export interface PendingAssistantSelection {
  text: string
  source: Extract<AssistantSelection['source'], 'page_selection'>
}

export function buildAskAssistantPrompt(selection: string): string {
  const selectedText = selection.trim()
  return selectedText ? `请帮我解释这段内容：\n\n「${selectedText}」` : ''
}

export function buildPendingAssistantSelection(selection: string): PendingAssistantSelection | null {
  const selectedText = selection.trim()
  return selectedText ? { text: selectedText, source: 'page_selection' } : null
}

export function parsePendingAssistantSelection(raw: string | null): PendingAssistantSelection | null {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Partial<PendingAssistantSelection>
    if (typeof parsed.text !== 'string' || !parsed.text.trim()) {
      return null
    }
    return {
      text: parsed.text.trim(),
      source: 'page_selection',
    }
  } catch {
    return null
  }
}

export function findRetryUserMessage(
  messages: AssistantMessage[],
  assistantMessageId: string,
): AssistantMessage | null {
  const assistantIndex = messages.findIndex(
    (message) =>
      message.id === assistantMessageId &&
      message.role === 'assistant' &&
      message.status === 'done',
  )
  if (assistantIndex <= 0) {
    return null
  }

  for (let index = assistantIndex - 1; index >= 0; index -= 1) {
    const message = messages[index]
    if (message?.role === 'user' && message.status === 'done') {
      return message
    }
  }
  return null
}
