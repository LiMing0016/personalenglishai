import type {
  EssayPromptItem,
  GenerateExamDialogueTurnMessage,
  GenerateExamDialogueTurnResponse,
  GenerateExamPromptResponse,
} from '../../api/writing.ts'
import {
  normalizePromptSheet,
  parseWordRange,
} from './examPromptHelpers.ts'
import type { ExamPromptSheet, ExamTopicInfo } from './examPromptHelpers.ts'

export type ExamWorkbenchMessageRole = 'user' | 'assistant' | 'status'
export type ExamWorkbenchMessageKind = 'text' | 'asset' | 'reply' | 'status'
export type ExamWorkbenchAssetType = 'image' | 'material' | 'past_prompt'
export type ExamWorkbenchPreviewStatus = 'empty' | 'draft' | 'ready'

export interface ExamWorkbenchConversationMessage {
  id: string
  role: ExamWorkbenchMessageRole
  kind: ExamWorkbenchMessageKind
  text: string
  assetType?: ExamWorkbenchAssetType | null
  assetName?: string | null
  assetSummary?: string | null
  imageUrl?: string | null
  replyKind?: string | null
  tone?: 'info' | 'success' | 'warning' | 'muted'
}

export interface ExamWorkbenchActiveAssets {
  uploadedImage: string | null
  materialAttachmentText: string | null
  materialAttachmentName: string | null
  selectedPrompt: EssayPromptItem | null
}

export interface ExamWorkbenchPreviewResult {
  previewStatus: ExamWorkbenchPreviewStatus
  missingFields: string[]
  previewSheet: ExamPromptSheet | null
  previewTopicInfo: ExamTopicInfo | null
}

function createId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export function createUserTextMessage(text: string): ExamWorkbenchConversationMessage {
  return {
    id: createId('user-text'),
    role: 'user',
    kind: 'text',
    text: text.trim(),
  }
}

export function createUserAssetMessage(
  assetType: ExamWorkbenchAssetType,
  options: {
    text: string
    assetName?: string | null
    assetSummary?: string | null
    imageUrl?: string | null
  },
): ExamWorkbenchConversationMessage {
  return {
    id: createId(`user-${assetType}`),
    role: 'user',
    kind: 'asset',
    text: options.text.trim(),
    assetType,
    assetName: options.assetName ?? null,
    assetSummary: options.assetSummary ?? null,
    imageUrl: options.imageUrl ?? null,
  }
}

export function createAssistantReplyMessages(
  blocks: Array<{ kind: string; text: string }>,
  fallbackText?: string | null,
): ExamWorkbenchConversationMessage[] {
  const normalizedBlocks = blocks
    .filter((block) => block.text.trim().length > 0)
    .map((block) => ({
      id: createId(`assistant-${block.kind || 'reply'}`),
      role: 'assistant' as const,
      kind: 'reply' as const,
      text: block.text.trim(),
      replyKind: block.kind ?? 'reply',
    }))
  if (normalizedBlocks.length > 0) {
    return normalizedBlocks
  }
  if (!fallbackText?.trim()) {
    return []
  }
  return [{
    id: createId('assistant-reply'),
    role: 'assistant',
    kind: 'reply',
    text: fallbackText.trim(),
    replyKind: 'reply',
  }]
}

export function createStatusMessage(
  text: string,
  tone: ExamWorkbenchConversationMessage['tone'] = 'info',
): ExamWorkbenchConversationMessage {
  return {
    id: createId('status'),
    role: 'status',
    kind: 'status',
    text: text.trim(),
    tone,
  }
}

function promptTextExcerpt(text: string, limit = 140) {
  const normalized = text.trim().replace(/\s+/g, ' ')
  if (normalized.length <= limit) return normalized
  return `${normalized.slice(0, limit)}...`
}

export function buildDialogueTurnMessages(
  conversationMessages: ExamWorkbenchConversationMessage[],
  activeAssets: ExamWorkbenchActiveAssets,
  taskInstruction?: string | null,
): GenerateExamDialogueTurnMessage[] {
  const latestUserTextMessage = conversationMessages
    .filter((message) => message.role === 'user' && message.kind === 'text' && message.text.trim().length > 0)
  const latestUserText = latestUserTextMessage.length > 0
    ? latestUserTextMessage[latestUserTextMessage.length - 1]
    : null

  const textMessages: GenerateExamDialogueTurnMessage[] = []
  if (latestUserText) {
    textMessages.push({
      role: 'user' as const,
      kind: 'text' as const,
      text: latestUserText.text.trim(),
    })
  }

  if (taskInstruction?.trim()) {
    textMessages.push({
      role: 'user',
      kind: 'text',
      text: taskInstruction.trim(),
    })
  }

  const assetMessages: GenerateExamDialogueTurnMessage[] = []

  if (activeAssets.selectedPrompt) {
    assetMessages.push({
      role: 'user',
      kind: 'asset',
      assetType: 'past_prompt',
      assetSummary: `历年真题参考：${promptTextExcerpt(activeAssets.selectedPrompt.promptText)}`,
    })
  }

  if (activeAssets.materialAttachmentText?.trim()) {
    assetMessages.push({
      role: 'user',
      kind: 'asset',
      assetType: 'material',
      assetSummary: `附件材料：${promptTextExcerpt(activeAssets.materialAttachmentText, 220)}`,
    })
  }

  if (activeAssets.uploadedImage) {
    assetMessages.push({
      role: 'user',
      kind: 'asset',
      assetType: 'image',
      assetSummary: '已添加图片附件，请优先保留原图命题场景，并在信息不足时仅补全缺失要求。',
    })
  }

  return [...textMessages, ...assetMessages]
}

function buildPreviewInfoFromDraft(
  promptDraft: GenerateExamPromptResponse,
  studyStage: string | null,
  selectedMode: 'free' | 'exam',
  activeAssets: ExamWorkbenchActiveAssets,
): ExamTopicInfo {
  const shouldUseUploadedImageFallback =
    !!activeAssets.uploadedImage
    && !promptDraft.attachmentImageUrl
    && (promptDraft.attachmentSource === 'user_upload' || !promptDraft.attachmentSource)
  const parsedWordRange = parseWordRange(promptDraft.wordRange ?? null)

  return {
    paper: promptDraft.paper ?? null,
    promptSheetId: promptDraft.promptSheetId ?? null,
    topic: promptDraft.promptText,
    genre: promptDraft.genre ?? null,
    wordRange: promptDraft.wordRange ?? null,
    requirements: promptDraft.requirements ?? null,
    imageDescription: activeAssets.uploadedImage
      ? (promptDraft.attachmentContent ?? '请结合附件图片完成写作。')
      : null,
    materialText: promptDraft.materialText ?? activeAssets.materialAttachmentText ?? null,
    attachmentImageUrl: promptDraft.attachmentImageUrl
      ?? (shouldUseUploadedImageFallback ? activeAssets.uploadedImage : null),
    maxScore: promptDraft.maxScore ?? 100,
    sourceType: promptDraft.sourceType ?? 'ai_generated',
    examType: selectedMode === 'exam' ? studyStage : null,
    taskType: promptDraft.taskType ?? null,
    minWords: promptDraft.minWords ?? parsedWordRange.minWords,
    recommendedMaxWords: promptDraft.recommendedMaxWords ?? parsedWordRange.recommendedMaxWords,
    promptType: promptDraft.promptType,
    chartSpec: promptDraft.chartSpec ?? null,
    comicScenes: promptDraft.comicScenes ?? [],
  }
}

export function buildPreviewResultFromDialogue(
  response: GenerateExamDialogueTurnResponse,
  options: {
    studyStage: string | null
    selectedMode: 'free' | 'exam'
    activeAssets: ExamWorkbenchActiveAssets
  },
): ExamWorkbenchPreviewResult {
  if (!response.promptSheetDraft) {
    return {
      previewStatus: response.previewStatus ?? 'empty',
      missingFields: response.missingFields ?? [],
      previewSheet: null,
      previewTopicInfo: null,
    }
  }

  const shouldUseUploadedImageFallback =
    !!options.activeAssets.uploadedImage
    && !response.promptSheetDraft.attachmentImageUrl
    && (
      response.promptSheetDraft.attachmentSource === 'user_upload'
      || !response.promptSheetDraft.attachmentSource
    )

  const previewSheet = normalizePromptSheet({
    ...response.promptSheetDraft,
    attachmentImageUrl: response.promptSheetDraft.attachmentImageUrl
      ?? (shouldUseUploadedImageFallback ? options.activeAssets.uploadedImage : null),
    attachmentType: shouldUseUploadedImageFallback && !response.promptSheetDraft.attachmentType
      ? 'visual'
      : response.promptSheetDraft.attachmentType ?? undefined,
    visualKind: shouldUseUploadedImageFallback && !response.promptSheetDraft.visualKind
      ? 'image'
      : response.promptSheetDraft.visualKind ?? undefined,
    attachmentContent: response.promptSheetDraft.attachmentContent
      ?? (shouldUseUploadedImageFallback ? '请结合附件图片完成写作。' : undefined),
  })

  return {
    previewStatus: response.previewStatus ?? 'empty',
    missingFields: response.missingFields ?? [],
    previewSheet,
    previewTopicInfo: buildPreviewInfoFromDraft(
      response.promptSheetDraft,
      options.studyStage,
      options.selectedMode,
      options.activeAssets,
    ),
  }
}
