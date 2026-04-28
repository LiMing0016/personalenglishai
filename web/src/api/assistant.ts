import { getToken } from '../utils/token.ts'

import type { AssistantAttachment } from '../pages/app/assistantMock.ts'

export interface AssistantChatPayload {
  input: string
  conversationId: string
  studyStage?: string
  assistantMode?: 'default' | 'exam'
  attachments: AssistantAttachment[]
}

export interface AssistantChatResult {
  reply: string
  agentName?: string
}

const viteEnv = (import.meta as ImportMeta & { env?: Record<string, string | undefined> }).env
const assistantBaseUrl = viteEnv?.VITE_ASSISTANT_API_BASE_URL?.trim() || 'http://127.0.0.1:8002'

export async function assistantChat(payload: AssistantChatPayload): Promise<AssistantChatResult> {
  const formData = new FormData()
  formData.append('message', payload.input)
  formData.append('conversation_id', payload.conversationId)
  if (payload.studyStage?.trim()) {
    formData.append('study_stage', payload.studyStage.trim())
  }
  if (payload.assistantMode && payload.assistantMode !== 'default') {
    formData.append('assistant_mode', payload.assistantMode)
  }

  payload.attachments.forEach((attachment) => {
    formData.append('files', attachment.file, attachment.name)
  })

  const token = getToken()
  const response = await fetch(`${assistantBaseUrl}/chat`, {
    method: 'POST',
    body: formData,
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    credentials: assistantBaseUrl.startsWith('http') ? 'omit' : 'include',
  })

  if (!response.ok) {
    let message = '学习助手暂时不可用'
    try {
      const body = (await response.json()) as { detail?: string; message?: string }
      message = body.detail || body.message || message
    } catch {
      // Ignore parse failures and keep the fallback message.
    }
    throw new Error(message)
  }

  const body = (await response.json()) as {
    reply?: string
    output_text?: string
    agentName?: string
    agent_name?: string
  }

  const reply = body.reply || body.output_text || ''
  if (!reply.trim()) {
    throw new Error('学习助手没有返回内容')
  }

  return {
    reply,
    agentName: body.agentName || body.agent_name,
  }
}
