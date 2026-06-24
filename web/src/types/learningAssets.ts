export type LearningAssetType = 'vocabulary' | 'sentence' | 'grammar' | 'expression'

export interface LearningAssetMarkdownSeed {
  title?: string
  selectedText?: string
  contextText?: string
}

export interface CreateLearningAssetDraftInput extends LearningAssetMarkdownSeed {
  conversationId: string
  messageId?: string
  now?: number
}

export interface LearningAssetDraft {
  noteUid?: string
  type: LearningAssetType
  title: string
  contentMarkdown: string
  structuredPayload?: string | null
  sourceConversationId: string
  sourceMessageId?: string
  sourceText?: string
  selectedText: string
  contextText: string
  updatedAt: number
}

const learningAssetTypes: LearningAssetType[] = ['vocabulary', 'sentence', 'grammar', 'expression']

export function isLearningAssetType(value: unknown): value is LearningAssetType {
  return typeof value === 'string' && learningAssetTypes.includes(value as LearningAssetType)
}

export function normalizeLearningAssetType(value: unknown): LearningAssetType {
  const normalized = typeof value === 'string' ? value.trim().toLowerCase() : ''
  return isLearningAssetType(normalized) ? normalized : 'vocabulary'
}

export function createDefaultVocabularyMarkdown(seed: LearningAssetMarkdownSeed = {}) {
  const title = normalizeTitle(seed.title || seed.selectedText)
  const selectedText = safe(seed.selectedText)
  const contextText = safe(seed.contextText)
  const originalSentence = contextText || selectedText

  return [
    `# ${title}`,
    '',
    '**词性：**',
    '',
    '**中文释义：**',
    '',
    '**English meaning：**',
    '',
    `**原句：** ${originalSentence}`,
    '',
    '**AI 例句：**',
    '',
    '**常见搭配：**',
    '',
    '## 我的笔记',
    '',
  ].join('\n')
}

export function createLearningAssetDraft(input: CreateLearningAssetDraftInput): LearningAssetDraft {
  const title = normalizeTitle(input.title || input.selectedText)
  const selectedText = safe(input.selectedText || title)
  const contextText = safe(input.contextText)
  return {
    type: 'vocabulary',
    title,
    contentMarkdown: createDefaultVocabularyMarkdown({ title, selectedText, contextText }),
    structuredPayload: null,
    sourceConversationId: input.conversationId,
    sourceMessageId: input.messageId,
    sourceText: contextText,
    selectedText,
    contextText,
    updatedAt: input.now ?? Date.now(),
  }
}

export function normalizeTitle(value: unknown) {
  const text = safe(typeof value === 'string' ? value : '')
  return text || '未命名单词'
}

function safe(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}
