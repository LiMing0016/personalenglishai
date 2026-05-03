import { http } from './http'

import type { AssistantAttachment } from '../pages/app/assistantMock.ts'
import type {
  AssistantIntent,
  AssistantRequest as AssistantAgentRequest,
  AssistantSelection,
  InputScope,
  LearningMode,
} from '../types/assistantRequest'
export type {
  AssistantAttachmentRef,
  AssistantErrorPayload,
  AssistantIntent,
  AssistantMessageResponse as AssistantAgentMessageResponse,
  AssistantRequest,
  AssistantRunMetadata,
  AssistantStreamEvent,
  InputScope,
  LearningMode,
} from '../types/assistantRequest'

interface ApiEnvelope<T> {
  code?: string
  message?: string
  data?: T
}

export interface AssistantProjectDto {
  id: number
  name: string
  description?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface AssistantMessageDto {
  id: string
  role: 'user' | 'assistant'
  content: string
  status: 'done' | 'failed'
  createdAt?: string | null
}

export interface AssistantConversationDto {
  id: string
  projectId?: number | null
  title: string
  summary?: string | null
  pinned: boolean
  archived: boolean
  createdAt?: string | null
  updatedAt?: string | null
  messages?: AssistantMessageDto[]
}

export interface AssistantShareDto {
  shareToken: string
  sharePath: string
  createdAt?: string | null
}

export interface PublicAssistantShareDto {
  title: string
  messages: AssistantMessageDto[]
  createdAt?: string | null
}

export interface AssistantChatPayload {
  input: string
  conversationId: string
  studyStage?: string
  assistantMode?: 'default' | 'exam'
  intent?: AssistantIntent
  scope?: InputScope
  selection?: AssistantSelection
  attachments: AssistantAttachment[]
}

export interface AssistantChatResult {
  reply: string
  conversation?: AssistantConversationDto
}

function createClientMessageId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID()
  }
  return `client-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function mapLearningMode(mode?: AssistantChatPayload['assistantMode']): LearningMode {
  return mode === 'exam' ? 'exam_boost' : 'daily_explain'
}

type AssistantStudyStage = NonNullable<AssistantAgentRequest['studyContext']>['studyStage']

function normalizeStudyStage(stage?: string): AssistantStudyStage | undefined {
  if (stage === 'beginner' || stage === 'intermediate' || stage === 'advanced') {
    return stage
  }
  return undefined
}

function toAssistantAgentRequest(payload: AssistantChatPayload): AssistantAgentRequest {
  const hasSelection = Boolean(payload.selection?.text?.trim())
  const text = payload.input.trim()
  return {
    appConversationId: payload.conversationId,
    clientMessageId: createClientMessageId(),
    mode: mapLearningMode(payload.assistantMode),
    intent: payload.intent ?? (hasSelection ? 'explain' : 'free_chat'),
    scope: payload.scope ?? (hasSelection ? (text ? 'selection_and_message' : 'selection') : 'message_only'),
    message: {
      text: payload.input,
    },
    selection: payload.selection,
    studyContext: {
      studyStage: normalizeStudyStage(payload.studyStage),
      locale: 'zh-CN',
      responseLanguage: 'zh-CN',
    },
  }
}

function unwrap<T>(body: ApiEnvelope<T>): T {
  if (body.data === undefined) {
    throw new Error(body.message || '接口没有返回数据')
  }
  return body.data
}

function latestAssistantReply(conversation: AssistantConversationDto) {
  const messages = conversation.messages ?? []
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const message = messages[i]
    if (message?.role === 'assistant' && message.content.trim()) {
      return message.content
    }
  }
  return ''
}

export const assistantApi = {
  async listProjects(): Promise<AssistantProjectDto[]> {
    const res = await http.get<ApiEnvelope<AssistantProjectDto[]>>('/assistant/projects')
    return unwrap(res.data)
  },

  async createProject(name: string, description = ''): Promise<AssistantProjectDto> {
    const res = await http.post<ApiEnvelope<AssistantProjectDto>>('/assistant/projects', { name, description })
    return unwrap(res.data)
  },

  async updateProject(projectId: number, name: string, description = ''): Promise<AssistantProjectDto> {
    const res = await http.patch<ApiEnvelope<AssistantProjectDto>>(`/assistant/projects/${projectId}`, {
      name,
      description,
    })
    return unwrap(res.data)
  },

  async deleteProject(projectId: number): Promise<void> {
    await http.delete(`/assistant/projects/${projectId}`)
  },

  async listConversations(params: { archived?: boolean; projectId?: number | null } = {}): Promise<AssistantConversationDto[]> {
    const res = await http.get<ApiEnvelope<AssistantConversationDto[]>>('/assistant/conversations', { params })
    return unwrap(res.data)
  },

  async createConversation(payload: { title?: string; projectId?: number | null } = {}): Promise<AssistantConversationDto> {
    const res = await http.post<ApiEnvelope<AssistantConversationDto>>('/assistant/conversations', payload)
    return unwrap(res.data)
  },

  async getConversation(conversationId: string): Promise<AssistantConversationDto> {
    const res = await http.get<ApiEnvelope<AssistantConversationDto>>(`/assistant/conversations/${conversationId}`)
    return unwrap(res.data)
  },

  async updateConversation(conversationId: string, payload: { title: string; summary?: string }): Promise<AssistantConversationDto> {
    const res = await http.patch<ApiEnvelope<AssistantConversationDto>>(`/assistant/conversations/${conversationId}`, payload)
    return unwrap(res.data)
  },

  async archiveConversation(conversationId: string): Promise<AssistantConversationDto> {
    const res = await http.post<ApiEnvelope<AssistantConversationDto>>(`/assistant/conversations/${conversationId}/archive`)
    return unwrap(res.data)
  },

  async restoreConversation(conversationId: string): Promise<AssistantConversationDto> {
    const res = await http.post<ApiEnvelope<AssistantConversationDto>>(`/assistant/conversations/${conversationId}/restore`)
    return unwrap(res.data)
  },

  async setPinned(conversationId: string, pinned: boolean): Promise<AssistantConversationDto> {
    const res = await http.post<ApiEnvelope<AssistantConversationDto>>(`/assistant/conversations/${conversationId}/pin`, { pinned })
    return unwrap(res.data)
  },

  async moveConversation(conversationId: string, projectId: number | null): Promise<AssistantConversationDto> {
    const res = await http.post<ApiEnvelope<AssistantConversationDto>>(`/assistant/conversations/${conversationId}/move`, {
      projectId,
    })
    return unwrap(res.data)
  },

  async deleteConversation(conversationId: string): Promise<void> {
    await http.delete(`/assistant/conversations/${conversationId}`)
  },

  async shareConversation(conversationId: string): Promise<AssistantShareDto> {
    const res = await http.post<ApiEnvelope<AssistantShareDto>>(`/assistant/conversations/${conversationId}/share`)
    return unwrap(res.data)
  },

  async getPublicShare(shareToken: string): Promise<PublicAssistantShareDto> {
    const res = await http.get<ApiEnvelope<PublicAssistantShareDto>>(`/public/assistant/shares/${shareToken}`)
    return unwrap(res.data)
  },
}

export async function assistantChat(payload: AssistantChatPayload): Promise<AssistantChatResult> {
  const conversation = await sendAssistantMessage(payload)
  const reply = latestAssistantReply(conversation)
  if (!reply.trim()) {
    throw new Error('学习助手没有返回内容')
  }
  return { reply, conversation }
}

export async function sendAssistantMessage(payload: AssistantChatPayload): Promise<AssistantConversationDto> {
  if (payload.attachments.length > 0) {
    const formData = new FormData()
    formData.append('message', payload.input)
    if (payload.studyStage) {
      formData.append('studyStage', payload.studyStage)
    }
    if (payload.assistantMode) {
      formData.append('assistantMode', payload.assistantMode)
    }
    for (const attachment of payload.attachments) {
      formData.append('files', attachment.file, attachment.name)
    }

    const res = await http.post<ApiEnvelope<AssistantConversationDto>>(
      `/assistant/conversations/${payload.conversationId}/messages`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    )
    return unwrap(res.data)
  }

  const agentRequest = toAssistantAgentRequest(payload)
  const agentConversation = await sendAssistantAgentMessage(agentRequest)
  return agentConversation
}

export async function sendAssistantAgentMessage(payload: AssistantAgentRequest): Promise<AssistantConversationDto> {
  const appConversationId = payload.appConversationId
  if (!appConversationId) {
    throw new Error('学习助手会话 ID 不能为空')
  }
  const res = await http.post<ApiEnvelope<AssistantConversationDto>>(
    `/assistant/conversations/${appConversationId}/messages/run`,
    payload,
  )
  return unwrap(res.data)
}
