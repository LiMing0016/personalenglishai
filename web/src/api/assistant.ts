import { http } from './http'
import { streamAssistantEvents } from './assistantStream.ts'
import { toAssistantAgentRequest } from './assistantRequestBuilder.ts'

import type { AssistantAttachment } from '../pages/app/assistantMock.ts'
import type { AssistantBlock } from '../types/assistantBlocks.ts'
import type {
  AssistantIntent,
  AssistantInteractionContext,
  AssistantRequest as AssistantAgentRequest,
  AssistantSelection,
  AssistantWritingCoachContext,
  InputScope,
} from '../types/assistantRequest'
export type {
  AssistantAttachmentRef,
  AssistantErrorPayload,
  AssistantIntent,
  AssistantWritingCoachContext,
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

const ASSISTANT_REQUEST_TIMEOUT_MS = 60000

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
  parts?: AssistantBlock[]
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

export interface AssistantArchiveSettingsDto {
  archiveDir: string
  defaultArchiveDir: string
  custom: boolean
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
  assistantMode?: 'default' | 'exam' | 'learning'
  intent?: AssistantIntent
  scope?: InputScope
  selection?: AssistantSelection
  writingCoachContext?: AssistantWritingCoachContext
  interaction?: AssistantInteractionContext
  attachments: AssistantAttachment[]
}

export interface AssistantChatResult {
  reply: string
  parts?: AssistantBlock[]
  conversation?: AssistantConversationDto
}

export interface AssistantChatStreamHandlers {
  onDelta?: (delta: string) => void
  onCompleted?: (content: string, parts?: AssistantBlock[]) => void
}

export interface AssistantChatStreamOptions {
  signal?: AbortSignal
}

export interface WritingCoachChatKitContext {
  inputAsText: string
  writingMode: 'free' | 'exam'
  studyStage?: string | null
  taskType?: string | null
  essayQuestion?: string | null
  questionMaterials?: string | null
  essayGenre?: string | null
  minWords?: number | null
  maxWords?: number | null
  includeDraft: boolean
  essayText?: string | null
  selectedText?: string | null
}

export interface WritingCoachChatKitSessionPayload {
  workflowId?: string
  conversationId?: string
  writingContext: WritingCoachChatKitContext
  stateVariables?: Record<string, string | number | boolean>
}

export interface WritingCoachChatKitSessionResult {
  clientSecret: string
  sessionId?: string | null
  expiresAt?: number | null
}

export { toAssistantAgentRequest } from './assistantRequestBuilder.ts'

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

function latestAssistantParts(conversation: AssistantConversationDto) {
  const messages = conversation.messages ?? []
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const message = messages[i]
    if (message?.role === 'assistant' && message.parts?.length) {
      return message.parts
    }
  }
  return undefined
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

  async getArchiveSettings(): Promise<AssistantArchiveSettingsDto> {
    const res = await http.get<ApiEnvelope<AssistantArchiveSettingsDto>>('/assistant/archive/settings')
    return unwrap(res.data)
  },

  async updateArchiveSettings(archiveDir: string): Promise<AssistantArchiveSettingsDto> {
    const res = await http.patch<ApiEnvelope<AssistantArchiveSettingsDto>>('/assistant/archive/settings', { archiveDir })
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
  return { reply, parts: latestAssistantParts(conversation), conversation }
}

export async function assistantChatStream(
  payload: AssistantChatPayload,
  handlers: AssistantChatStreamHandlers = {},
  options: AssistantChatStreamOptions = {},
): Promise<AssistantChatResult> {
  if (payload.attachments.length > 0) {
    return assistantChat(payload)
  }

  const agentRequest = toAssistantAgentRequest(payload)
  let seenEvent = false
  let failedMessage = ''
  let completedContent = ''
  let completedParts: AssistantBlock[] | undefined
  let accumulatedContent = ''

  try {
    await streamAssistantEvents(
      `/api/assistant/conversations/${payload.conversationId}/messages/run/stream`,
      agentRequest,
      (event) => {
        seenEvent = true
        if (event.type === 'message.delta' && 'delta' in event && typeof event.delta === 'string') {
          accumulatedContent += event.delta
          handlers.onDelta?.(event.delta)
          return
        }
        if (event.type === 'message.completed' && 'content' in event && typeof event.content === 'string') {
          completedContent = event.content
          completedParts = Array.isArray(event.parts) ? event.parts as AssistantBlock[] : undefined
          handlers.onCompleted?.(event.content, completedParts)
          return
        }
        if (event.type === 'run.failed' && 'error' in event) {
          const error = event.error as { message?: string }
          failedMessage = error.message || '学习助手暂时不可用'
        }
      },
      { signal: options.signal },
    )
  } catch (error) {
    if (isAbortError(error)) {
      throw Object.assign(new Error('已停止生成'), { canceled: true })
    }
    if (!seenEvent) {
      return assistantChat(payload)
    }
    throw error
  }

  if (failedMessage) {
    throw new Error(failedMessage)
  }

  const reply = completedContent || accumulatedContent
  if (!reply.trim()) {
    throw new Error('学习助手没有返回内容')
  }
  return { reply, parts: completedParts }
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
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
      {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: ASSISTANT_REQUEST_TIMEOUT_MS,
      },
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
    { timeout: ASSISTANT_REQUEST_TIMEOUT_MS },
  )
  return unwrap(res.data)
}

export async function createWritingCoachChatKitSession(
  payload: WritingCoachChatKitSessionPayload,
): Promise<WritingCoachChatKitSessionResult> {
  const res = await http.post<ApiEnvelope<WritingCoachChatKitSessionResult>>(
    '/assistant/chatkit/writing-coach/session',
    payload,
    { timeout: ASSISTANT_REQUEST_TIMEOUT_MS },
  )
  return unwrap(res.data)
}
