export type LearningAssetType = 'vocabulary' | 'sentence' | 'grammar' | 'expression'
export type LearningAssetCopilotAction =
  | 'complete'
  | 'organize'
  | 'format'
  | 'examples'
  | 'expand'
  | 'polish'
  | 'custom'

export interface LearningAssetCopilotRequest {
  action: LearningAssetCopilotAction
  instruction?: string
}

export interface LearningAssetMarkdownSeed {
  title?: string
  selectedText?: string
  contextText?: string
}

export interface CreateLearningAssetDraftInput extends LearningAssetMarkdownSeed {
  conversationId: string
  draftId?: string
  messageId?: string
  type?: LearningAssetType
  now?: number
}

export interface LearningAssetDraft {
  draftId: string
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
const MAX_CONTEXT_TEXT_LENGTH = 180

export const learningAssetTypeLabels: Record<LearningAssetType, string> = {
  vocabulary: '单词卡',
  grammar: '语法笔记',
  sentence: '句子笔记',
  expression: '表达笔记',
}

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
  const contextText = resolveLearningAssetContext({ selectedText, contextText: seed.contextText })
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

export function createDefaultGrammarMarkdown(seed: LearningAssetMarkdownSeed = {}) {
  const title = normalizeTitle(seed.title || seed.selectedText)
  const selectedText = safe(seed.selectedText)
  const contextText = resolveLearningAssetContext({ selectedText, contextText: seed.contextText })
  const originalSentence = contextText || selectedText

  return [
    `# ${title}`,
    '',
    '**类型：** 语法笔记',
    '',
    '**结构/规则：**',
    '',
    '**中文说明：**',
    '',
    `**原句：** ${originalSentence}`,
    '',
    '## 结构拆解',
    '',
    '- ',
    '',
    '## 使用提醒',
    '',
    '- ',
    '',
    '## 我的笔记',
    '',
  ].join('\n')
}

export function createDefaultSentenceMarkdown(seed: LearningAssetMarkdownSeed = {}) {
  const title = normalizeTitle(seed.title || seed.selectedText)

  return [
    `# ${title}`,
    '',
    '**中文含义：**',
    '',
    '**核心结构：**',
    '',
    '**可替换表达：**',
    '',
    '**适用场景：**',
    '',
    '## 句子拆解',
    '',
    '- ',
    '',
    '## 我的笔记',
    '',
  ].join('\n')
}

export function createDefaultExpressionMarkdown(seed: LearningAssetMarkdownSeed = {}) {
  const title = normalizeTitle(seed.title || seed.selectedText)

  return [
    `# ${title}`,
    '',
    '## 我的笔记',
    '',
  ].join('\n')
}

export function createDefaultLearningAssetMarkdown(
  type: LearningAssetType,
  seed: LearningAssetMarkdownSeed = {},
) {
  if (type === 'grammar') return createDefaultGrammarMarkdown(seed)
  if (type === 'sentence') return createDefaultSentenceMarkdown(seed)
  if (type === 'expression') return createDefaultExpressionMarkdown(seed)
  return createDefaultVocabularyMarkdown(seed)
}

export function createLearningAssetDraft(input: CreateLearningAssetDraftInput): LearningAssetDraft {
  const type = normalizeLearningAssetType(input.type)
  const title = normalizeTitle(input.title || input.selectedText)
  const selectedText = safe(input.selectedText || title)
  const contextText = resolveLearningAssetContext({ selectedText, contextText: input.contextText })
  const updatedAt = input.now ?? Date.now()
  return {
    draftId: input.draftId || createLocalDraftId({ type, title, messageId: input.messageId, updatedAt }),
    type,
    title,
    contentMarkdown: createDefaultLearningAssetMarkdown(type, { title, selectedText, contextText }),
    structuredPayload: null,
    sourceConversationId: input.conversationId,
    sourceMessageId: input.messageId,
    sourceText: contextText,
    selectedText,
    contextText,
    updatedAt,
  }
}

export function normalizeTitle(value: unknown) {
  const text = safe(typeof value === 'string' ? value : '')
  return text || '未命名单词'
}

export function resolveLearningAssetContext(seed: LearningAssetMarkdownSeed = {}) {
  const selectedText = safe(seed.selectedText)
  const selectedLower = selectedText.toLowerCase()
  const contextText = safe(seed.contextText)
  if (!contextText) return selectedText

  const candidates = contextText
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map((rawLine) => ({
      rawLine,
      text: cleanContextLine(rawLine),
    }))
    .filter((item) => item.text && !isMarkdownTableSeparator(item.rawLine))

  if (candidates.length === 0) return selectedText

  const selectedCandidates = selectedLower
    ? candidates.filter((item) => item.text.toLowerCase().includes(selectedLower))
    : []
  const candidatePool = selectedCandidates.length > 0 ? selectedCandidates : candidates
  const preferred = candidatePool.find((item) => isUsefulContextLine(item.rawLine, item.text, selectedText))
    ?? candidatePool.find((item) => item.text.toLowerCase() !== selectedLower)
    ?? candidatePool[0]

  return clampContextText(preferred.text || selectedText)
}

function safe(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function cleanContextLine(rawLine: string) {
  let line = rawLine.trim()
  if (!line) return ''

  if (line.includes('|')) {
    line = line
      .replace(/^\|/, '')
      .replace(/\|$/, '')
      .split('|')
      .map((cell) => cell.trim())
      .filter(Boolean)
      .join(' ')
  }

  return line
    .replace(/^#{1,6}\s+/, '')
    .replace(/^>\s*/, '')
    .replace(/^[-*+]\s+/, '')
    .replace(/^\d+[.)]\s+/, '')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/__([^_]+)__/g, '$1')
    .replace(/\s+/g, ' ')
    .trim()
}

function isMarkdownTableSeparator(rawLine: string) {
  return /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(rawLine)
}

function isUsefulContextLine(rawLine: string, text: string, selectedText: string) {
  const normalizedSelected = selectedText.trim().toLowerCase()
  if (!text || text.toLowerCase() === normalizedSelected) return false
  if (text.length <= selectedText.trim().length + 2) return false
  return !/^#{1,6}\s+/.test(rawLine.trim())
}

function clampContextText(text: string) {
  const normalized = text.replace(/\s+/g, ' ').trim()
  if (normalized.length <= MAX_CONTEXT_TEXT_LENGTH) return normalized
  return `${normalized.slice(0, MAX_CONTEXT_TEXT_LENGTH - 3).trimEnd()}...`
}

function createLocalDraftId(input: {
  type: LearningAssetType
  title: string
  messageId?: string
  updatedAt: number
}) {
  const slug = input.title
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 32) || 'asset'
  const randomSuffix = globalThis.crypto?.randomUUID?.()
    ?? `${input.updatedAt}-${Math.random().toString(36).slice(2, 8)}`
  return [input.type, input.messageId, slug, randomSuffix].filter(Boolean).join(':')
}
