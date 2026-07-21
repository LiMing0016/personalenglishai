import type { RenderableAssistantBlock } from '../../types/assistantBlocks.ts'
import type {
  AssistantInteractionContext,
  AssistantIntent,
  AssistantSelection,
  InputScope,
} from '../../types/assistantRequest.ts'

export type AssistantMessageRole = 'user' | 'assistant'
export type AssistantMessageStatus = 'done' | 'loading'
export type AssistantAttachmentKind = 'image' | 'file'
export type AssistantMode = 'default' | 'exam' | 'learning'

export interface AssistantAttachmentMetadata {
  id: string
  name: string
  size: number
  type: string
  kind: AssistantAttachmentKind
}

export interface AssistantAttachment extends AssistantAttachmentMetadata {
  file: File
}

export interface AssistantMessage {
  id: string
  role: AssistantMessageRole
  content: string
  status: AssistantMessageStatus
  parts?: RenderableAssistantBlock[]
  attachments?: AssistantAttachment[]
  attachmentMetadata?: AssistantAttachmentMetadata[]
  interaction?: AssistantInteractionContext
}

export interface AssistantConversation {
  id: string
  projectId?: number | null
  title: string
  summary: string
  createdAt: number
  updatedAt: number
  pinned?: boolean
  archived?: boolean
  messages: AssistantMessage[]
}

export const assistantStarterPrompts = [
  '评价这段英文表达是否自然',
  '帮我把这句话润色得更高级',
  '给我设计一道英语写作训练题',
  '解释这个单词在语境里的用法',
] as const

export interface AssistantReplyRequest {
  input: string
  conversationId: string
  studyStage?: string
  assistantMode?: AssistantMode
  intent?: AssistantIntent
  scope?: InputScope
  selection?: AssistantSelection
  interaction?: AssistantInteractionContext
  attachments: AssistantAttachment[]
}

export interface AssistantReplyResult {
  reply: string
  parts?: RenderableAssistantBlock[]
}

export async function buildMockAssistantReply(request: AssistantReplyRequest): Promise<string> {
  const attachmentHint =
    request.attachments.length > 0
      ? `\n\n已收到 ${request.attachments.length} 个附件：${request.attachments.map((attachment) => attachment.name).join('、')}`
      : ''
  return `这是学习助手的前端占位回复：\n\n${request.input}${attachmentHint}`
}
