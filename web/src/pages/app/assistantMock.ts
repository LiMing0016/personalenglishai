export type AssistantMessageRole = 'user' | 'assistant'
export type AssistantMessageStatus = 'done' | 'loading'
export type AssistantAttachmentKind = 'image' | 'file'
export type AssistantMode = 'default' | 'exam'

export interface AssistantAttachment {
  id: string
  name: string
  size: number
  type: string
  kind: AssistantAttachmentKind
  file: File
}

export interface AssistantMessage {
  id: string
  role: AssistantMessageRole
  content: string
  status: AssistantMessageStatus
  attachments?: AssistantAttachment[]
}

export interface AssistantConversation {
  id: string
  title: string
  summary: string
  updatedAt: number
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
  attachments: AssistantAttachment[]
}

export async function buildMockAssistantReply(request: AssistantReplyRequest): Promise<string> {
  const attachmentHint =
    request.attachments.length > 0
      ? `\n\n已收到 ${request.attachments.length} 个附件：${request.attachments.map((attachment) => attachment.name).join('、')}`
      : ''
  return `这是学习助手的前端占位回复：\n\n${request.input}${attachmentHint}`
}
