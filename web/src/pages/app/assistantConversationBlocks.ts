import { normalizeAssistantBlocks } from '../../components/assistant/learning-blocks/registry.ts'
import type { AssistantBlockDiagnosticReporter } from '../../components/assistant/learning-blocks/registry.ts'
import type { AssistantConversation, AssistantMessage } from './assistantMock.ts'

interface RemoteMessageLike {
  id: string
  role: AssistantMessage['role']
  content: string
  status: 'done' | 'failed'
  parts?: unknown
}

interface RemoteConversationLike {
  id: string
  projectId?: number | null
  title: string
  summary?: string | null
  pinned: boolean
  archived: boolean
  createdAt?: string | null
  updatedAt?: string | null
  messages?: RemoteMessageLike[]
}

function parseRemoteTime(value: string | null | undefined, fallback: number) {
  if (!value) return fallback
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? fallback : parsed
}

export function fromRemoteConversation(
  dto: RemoteConversationLike,
  report?: AssistantBlockDiagnosticReporter,
): AssistantConversation {
  const now = Date.now()
  const createdAt = parseRemoteTime(dto.createdAt, now)
  return {
    id: dto.id,
    projectId: dto.projectId ?? null,
    title: dto.title || '新对话',
    summary: dto.summary ?? '',
    createdAt,
    updatedAt: parseRemoteTime(dto.updatedAt, createdAt),
    pinned: dto.pinned,
    archived: dto.archived,
    messages: (dto.messages ?? []).map((message) => ({
      id: message.id,
      role: message.role,
      content: message.content,
      status: 'done',
      parts: normalizeAssistantBlocks(message.parts, report),
    })),
  }
}
