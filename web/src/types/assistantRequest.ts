import type { AssistantBlock } from './assistantBlocks.ts'

export type LearningMode = 'daily_explain' | 'exam_boost'

export type AssistantInteractionSource =
  | 'composer'
  | 'quick_action'
  | 'response_action'
  | 'activity_action'

export interface AssistantInteractionContext {
  source: AssistantInteractionSource
  uiIntent?: 'start_practice' | 'show_learning_card' | 'activity_action'
  activeActivityId?: string
  actionId?: string
  context?: {
    exerciseType?: 'sentence_reorder'
    topic?: string
    difficulty?: 'easy' | 'medium' | 'hard'
  }
}

export interface AssistantInteractionTrigger {
  displayText: string
  interaction: AssistantInteractionContext
}

export type AssistantIntent =
  | 'free_chat'
  | 'explain'
  | 'translate'
  | 'polish'
  | 'summarize'
  | 'grade_writing'
  | 'first_draft_coach'
  | 'generate_examples'
  | 'analyze_question'

export type InputScope =
  | 'message_only'
  | 'selection'
  | 'attachments'
  | 'selection_and_message'
  | 'attachments_and_message'
  | 'selection_attachments_and_message'

export type SelectionSource =
  | 'assistant_message'
  | 'writing_editor'
  | 'page_selection'
  | 'uploaded_image_ocr'

export type AttachmentProvider = 'app_storage' | 'openai_files' | 'external_url'

export type AttachmentKind = 'image' | 'pdf' | 'txt' | 'docx' | 'doc' | 'other'

export type AttachmentProcessingStatus = 'uploaded' | 'processing' | 'ready' | 'failed'

export type PreferredModelInputPart = 'input_image' | 'input_file' | 'input_text'

export type ImageDetail = 'low' | 'high' | 'auto'

export type ResponseLanguage = 'zh-CN' | 'en-US' | 'mixed'

export type StudyStage = string

export type CefrLevel = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2'

export type TargetExam = 'ielts' | 'toefl' | 'cet4' | 'cet6' | 'gaokao' | 'postgrad'

export interface AssistantAttachmentRef {
  attachmentId: string
  provider: AttachmentProvider
  openaiFileId?: string
  storageKey?: string
  url?: string
  name: string
  mimeType: string
  sizeBytes: number
  kind: AttachmentKind
  processing: {
    status: AttachmentProcessingStatus
    errorCode?: string
    extractedTextAvailable?: boolean
    extractedText?: string
    pageCount?: number
    checksum?: string
  }
  modelInput?: {
    preferredPart?: PreferredModelInputPart
    imageDetail?: ImageDetail
  }
}

export interface AssistantSelection {
  text: string
  source: SelectionSource
  sourceId?: string
  messageId?: string
  documentId?: string
  range?: {
    start?: number
    end?: number
  }
}

export interface AssistantConversationHistoryMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AssistantWritingCoachContext {
  schemaVersion?: string
  action?: 'coach' | 'analyze' | 'outline' | 'next' | 'topic' | 'polish' | 'draft'
  writingMode?: 'free' | 'exam'
  studyStage?: string | null
  taskType?: string | null
  essayQuestion?: string | null
  questionMaterials?: string | null
  imageDescriptions?: string[]
  attachments?: AssistantAttachmentRef[]
  essayGenre?: string | null
  minWords?: number | null
  maxWords?: number | null
  draftText?: string | null
  selectedText?: string | null
  includeDraft?: boolean
  topicAnalysisDone?: boolean
  topicBrief?: string | null
  centralTask?: string | null
  mustAnswerPoints?: string[]
  riskPoints?: string[]
  recommendedStructure?: string[]
  rubric?: {
    rubricKey?: string
    rubricVersion?: string
    rubricText?: string
    rubricFocus?: string[]
  }
}

export type WritingCoachEditActionType =
  | 'replace_selection'
  | 'insert_after_selection'
  | 'append_paragraph'

export type WritingPatch =
  | {
      op: 'replace_selection'
      range: { start: number; end: number }
      originalText: string
      newText: string
      reason?: string
    }
  | {
      op: 'search_replace'
      searchText: string
      replaceText: string
      reason?: string
    }
  | {
      op: 'insert_after_anchor'
      anchorText: string
      insertText: string
      reason?: string
    }
  | {
      op: 'append_paragraph'
      text: string
      reason?: string
    }
  | {
      op: 'replace_document'
      text: string
      reason?: string
    }

export interface WritingCoachEditAction {
  id: string
  type: WritingCoachEditActionType
  title: string
  text: string
  reason?: string
  patch?: WritingPatch
  target?: {
    mode: 'selected_range' | 'semantic_match' | 'document_end'
    selectedText?: string
    range?: {
      start: number
      end: number
    }
  }
}

export interface AssistantRequest {
  appConversationId?: string
  clientMessageId: string
  idempotencyKey?: string
  mode: LearningMode
  intent: AssistantIntent
  scope?: InputScope
  message: {
    text?: string
  }
  interaction?: AssistantInteractionContext
  selection?: AssistantSelection
  attachments?: AssistantAttachmentRef[]
  studyContext?: {
    studyStage?: StudyStage
    cefrLevel?: CefrLevel
    targetExam?: TargetExam
    locale?: 'zh-CN' | 'en-US'
    responseLanguage?: ResponseLanguage
  }
  writingCoachContext?: AssistantWritingCoachContext
  clientMeta?: {
    sourcePage?: string
    timezone?: string
    userAgent?: string
  }
  conversationHistory?: AssistantConversationHistoryMessage[]
}

export interface AssistantRunMetadata {
  runId: string
  traceId?: string
  agentName: string
  model: string
  mode: LearningMode
  intent: AssistantIntent
  scope: InputScope
  finishReason?: string
}

export interface AssistantUsage {
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
  requests?: number
}

export interface AssistantOpenAiState {
  responseId?: string
  conversationId?: string
  previousResponseId?: string
}

export interface AssistantMessageResponse {
  appConversationId: string
  messageId: string
  role: 'assistant'
  content: string
  parts?: AssistantBlock[]
  run: AssistantRunMetadata
  usage?: AssistantUsage
  openai?: AssistantOpenAiState
  createdAt: string
}

export interface AssistantErrorPayload {
  code:
    | 'INVALID_REQUEST'
    | 'IDEMPOTENCY_CONFLICT'
    | 'MISSING_INPUT'
    | 'UNSUPPORTED_INTENT'
    | 'UNSUPPORTED_MODE'
    | 'ATTACHMENT_NOT_READY'
    | 'ATTACHMENT_TOO_LARGE'
    | 'ATTACHMENT_KIND_UNSUPPORTED'
    | 'ATTACHMENT_IMAGE_NOT_READABLE'
    | 'ATTACHMENT_FILE_NOT_READABLE'
    | 'MODEL_CAPABILITY_UNSUPPORTED'
    | 'GUARDRAIL_BLOCKED'
    | 'OPENAI_RUN_FAILED'
    | 'STREAM_CANCELLED'
    | 'TIMEOUT'
  message: string
  details?: unknown
}

export type AssistantStreamEvent =
  | {
      type: 'run.started'
      runId: string
      traceId?: string
      agentName: string
      model: string
    }
  | {
      type: 'handoff'
      runId: string
      fromAgent: string
      toAgent: string
    }
  | {
      type: 'message.created'
      runId: string
      messageId: string
      role: 'assistant'
    }
  | {
      type: 'message.delta'
      runId: string
      messageId: string
      delta: string
    }
  | {
      type: 'message.completed'
      runId: string
      messageId: string
      content: string
      parts?: AssistantBlock[]
    }
  | {
      type: 'run.completed'
      runId: string
      usage?: AssistantUsage
      openai?: AssistantOpenAiState
    }
  | {
      type: 'run.failed'
      runId: string
      error: AssistantErrorPayload
    }
