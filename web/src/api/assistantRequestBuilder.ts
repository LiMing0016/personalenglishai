import type { AssistantAttachment } from '../pages/app/assistantMock.ts'
import type {
  AssistantInteractionContext,
  AssistantIntent,
  AssistantRequest,
  AssistantSelection,
  AssistantWritingCoachContext,
  InputScope,
  LearningMode,
} from '../types/assistantRequest.ts'

export interface AssistantAgentRequestPayload {
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

function createClientMessageId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `client-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function mapLearningMode(mode?: AssistantAgentRequestPayload['assistantMode']): LearningMode {
  return mode === 'exam' ? 'exam_boost' : 'daily_explain'
}

type AssistantStudyContext = NonNullable<AssistantRequest['studyContext']>
type AssistantStudyStage = AssistantStudyContext['studyStage']
type AssistantTargetExam = AssistantStudyContext['targetExam']

function normalizeStudyStage(stage?: string): AssistantStudyStage | undefined {
  const normalized = stage?.trim()
  return normalized || undefined
}

function normalizeTargetExam(stage?: string): AssistantTargetExam | undefined {
  const normalized = stage?.trim().toLowerCase()
  if (['ielts', 'toefl', 'cet4', 'cet6', 'gaokao', 'postgrad'].includes(normalized ?? '')) {
    return normalized as AssistantTargetExam
  }
  return undefined
}

export function toAssistantAgentRequest(payload: AssistantAgentRequestPayload): AssistantRequest {
  const hasSelection = Boolean(payload.selection?.text?.trim())
  const text = payload.input.trim()
  return {
    appConversationId: payload.conversationId,
    clientMessageId: createClientMessageId(),
    mode: mapLearningMode(payload.assistantMode),
    intent: payload.intent ?? (hasSelection ? 'explain' : 'free_chat'),
    scope: payload.scope ?? (hasSelection ? (text ? 'selection_and_message' : 'selection') : 'message_only'),
    message: { text: payload.input },
    interaction: payload.interaction,
    selection: payload.selection,
    studyContext: {
      studyStage: normalizeStudyStage(payload.studyStage),
      targetExam: normalizeTargetExam(payload.studyStage),
      locale: 'zh-CN',
      responseLanguage: 'zh-CN',
    },
    writingCoachContext: payload.writingCoachContext,
  }
}
