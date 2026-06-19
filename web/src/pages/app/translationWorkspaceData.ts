import type { TranslationMode, TranslationRecord, TranslationSourceType } from './translationHubData'
import type {
  TranslationDocumentBlockDto,
  TranslationDocumentElementDto,
  TranslationDocumentParseResponse,
} from '../../api/translation'

export type WorkspaceSegmentStatus = 'pending' | 'translated'
export type IntensiveAgentMode = TranslationMode | 'foreign' | 'technical'
export type DocumentBlockType = 'title' | 'heading' | 'paragraph' | 'list' | 'table' | 'quote' | 'code' | 'question' | 'option'
export type LearningAssetType = 'vocabulary' | 'phrase' | 'sentence' | 'grammar' | 'note' | 'review-card'

export interface NewTranslationInput {
  mode: TranslationMode
  pastedText: string
  selectedFileName: string
}

export interface TranslationWorkspaceSegment {
  id: string
  source: string
  translation: string
  translationStatus: WorkspaceSegmentStatus
  blockType?: DocumentBlockType
  pageNumber?: number
  elementId?: string
  bbox?: string | null
  confidence?: number | null
}

export interface TranslationWorkspaceDraft extends TranslationRecord {
  createdAt: string
  fileName: string
  sourceText: string
  segments: TranslationWorkspaceSegment[]
  pdfPreviewUrl?: string
  parseStatus?: string
  ocrStatus?: string
  pageCount?: number
  warnings?: string[]
}

export interface IntensiveReadingDocument {
  id: string
  title: string
  subtitle: string
  sourceType: TranslationSourceType
  sourceLabel: string
  mode: IntensiveAgentMode
  parseStatus: string
  progress: number
  pdfPreviewUrl?: string
  pageCount?: number
  blocks: DocumentBlock[]
  insights: TranslationInsight[]
  assets: LearningAsset[]
}

export interface DocumentBlock {
  id: string
  elementId?: string
  type: DocumentBlockType
  order: number
  text: string
  pageNumber?: number
  bbox?: string | null
  confidence?: number | null
}

export interface DocumentSelectionContext {
  documentId: string
  pageNumber: number
  blockId: string
  elementId: string
  bbox: string | null
  text: string
}

export interface TranslationInsight {
  id: string
  blockId: string
  translation: string
  summary: string
  keySentence: string
  phrases: LearningChip[]
  vocabulary: LearningChip[]
  grammarPoints: LearningChip[]
  noteDraft: string
  cardCount: number
}

export interface LearningChip {
  text: string
  meaning: string
}

export interface LearningAsset {
  id: string
  type: LearningAssetType
  label: string
  text: string
  sourceBlockId: string
}

export interface AssetStat {
  id: LearningAssetType
  label: string
  value: number
}

export interface AgentModeCapability {
  id: string
  title: string
  description: string
}

export interface ValidationResult {
  valid: boolean
  message?: string
}

const STORAGE_PREFIX = 'peai:translation-workspace:'

export function validateNewTranslationInput(input: NewTranslationInput): ValidationResult {
  const hasFile = input.selectedFileName.trim().length > 0
  const pastedLength = input.pastedText.trim().length

  if (!hasFile && pastedLength === 0) {
    return { valid: false, message: '请上传文件或粘贴至少 10 个字符' }
  }

  if (!hasFile && pastedLength < 10) {
    return { valid: false, message: '粘贴文本至少需要 10 个字符' }
  }

  return { valid: true }
}

export function createTranslationWorkspaceDraft(
  input: NewTranslationInput,
  now = new Date(),
): TranslationWorkspaceDraft {
  const validation = validateNewTranslationInput(input)
  if (!validation.valid) {
    throw new Error(validation.message)
  }

  const fileName = input.selectedFileName.trim()
  const pastedText = normalizeWhitespace(input.pastedText)
  const sourceText = pastedText || `已上传 ${fileName}，PDF 高质量解析接入后将在这里显示原文。`
  const sourceLabel = getSourceLabel(fileName, pastedText)
  const sourceType = getSourceType(fileName, pastedText)
  const title = fileName ? stripFileExtension(fileName) : buildTitleFromText(pastedText)

  return {
    id: `translation-${now.getTime()}`,
    title,
    subtitle: buildSubtitle(fileName, sourceText),
    sourceLabel,
    sourceType,
    mode: input.mode,
    updatedAt: '刚刚',
    noteCount: 0,
    progress: 0,
    status: 'reading',
    createdAt: now.toISOString(),
    fileName,
    sourceText,
    segments: buildWorkspaceSegments(sourceText),
  }
}

export function createTranslationWorkspaceDraftFromParsedDocument(
  input: Pick<NewTranslationInput, 'mode'> & { pdfPreviewUrl?: string },
  parsedDocument: TranslationDocumentParseResponse,
  now = new Date(),
): TranslationWorkspaceDraft {
  const fileName = parsedDocument.fileName || 'uploaded.pdf'
  const blocks = Array.isArray(parsedDocument.blocks) ? parsedDocument.blocks : []
  const elements = Array.isArray(parsedDocument.elements) ? parsedDocument.elements : []
  const sourceItems = elements.length > 0 ? elements : blocks
  const sourceText = sourceItems.map((block) => block.text.trim()).filter(Boolean).join('\n\n')
  const warningText = parsedDocument.warnings?.filter(Boolean).join('\n') ?? ''
  const sourceLabel = normalizeParsedSourceLabel(parsedDocument.sourceType, fileName)
  const fallbackText = warningText || '文档暂未解析出可展示的文本，请稍后重试或改用粘贴文本。'

  return {
    id: parsedDocument.documentId || `translation-${now.getTime()}`,
    title: stripFileExtension(fileName),
    subtitle: buildParsedDocumentSubtitle(fileName, parsedDocument),
    sourceLabel,
    sourceType: sourceLabel === 'PDF' ? 'pdf' : 'text',
    mode: input.mode,
    updatedAt: '刚刚',
    noteCount: 0,
    progress: 0,
    status: 'reading',
    createdAt: now.toISOString(),
    fileName,
    sourceText: sourceText || fallbackText,
    pdfPreviewUrl: input.pdfPreviewUrl,
    segments: sourceItems.length > 0
      ? sourceItems.map((block, index) => buildWorkspaceSegmentFromParsedItem(block, index))
      : buildWorkspaceSegments(fallbackText),
    parseStatus: parsedDocument.parseStatus,
    ocrStatus: parsedDocument.ocrStatus,
    pageCount: parsedDocument.pageCount,
    warnings: parsedDocument.warnings ?? [],
  }
}

export function buildWorkspaceSegments(sourceText: string): TranslationWorkspaceSegment[] {
  const normalized = normalizeWhitespace(sourceText)
  if (!normalized) return []

  const paragraphs = normalized
    .split(/\n+/)
    .map((item) => item.trim())
    .filter(Boolean)

  const units = paragraphs.length > 1 ? paragraphs : splitSentences(normalized)

  return units.map((source, index) => ({
    id: `segment-${index + 1}`,
    source,
    translation: '等待 AI 翻译',
    translationStatus: 'pending',
  }))
}

export function saveTranslationWorkspaceDraft(storage: Storage, draft: TranslationWorkspaceDraft): void {
  storage.setItem(`${STORAGE_PREFIX}${draft.id}`, JSON.stringify(draft))
}

export function loadTranslationWorkspaceDraft(storage: Storage, id: string): TranslationWorkspaceDraft | null {
  const raw = storage.getItem(`${STORAGE_PREFIX}${id}`)
  if (!raw) return null

  try {
    return JSON.parse(raw) as TranslationWorkspaceDraft
  } catch {
    return null
  }
}

export function listTranslationWorkspaceDrafts(storage: Storage): TranslationWorkspaceDraft[] {
  const drafts: TranslationWorkspaceDraft[] = []

  for (let index = 0; index < storage.length; index += 1) {
    const key = storage.key(index)
    if (!key?.startsWith(STORAGE_PREFIX)) continue

    const id = key.slice(STORAGE_PREFIX.length)
    const draft = loadTranslationWorkspaceDraft(storage, id)
    if (draft) drafts.push(draft)
  }

  return drafts.sort((left, right) => right.createdAt.localeCompare(left.createdAt))
}

export function buildIntensiveReadingDocument(draft: TranslationWorkspaceDraft): IntensiveReadingDocument {
  const blocks = draft.segments.map<DocumentBlock>((segment, index) => ({
    id: shouldPreserveSegmentId(segment.id) ? segment.id : `block-${index + 1}`,
    elementId: segment.elementId ?? (shouldPreserveSegmentId(segment.id) ? segment.id : undefined),
    type: segment.blockType ?? 'paragraph',
    order: index + 1,
    text: segment.source,
    pageNumber: segment.pageNumber ?? (index < 3 ? 1 : Math.floor(index / 3) + 1),
    bbox: segment.bbox ?? null,
    confidence: segment.confidence ?? null,
  }))

  const insights = blocks.map((block, index) => buildInsight(block, index))
  const assets = insights.flatMap((insight) => [
    ...insight.vocabulary.map((item, itemIndex) => buildAsset('vocabulary', '生词', item, insight.blockId, itemIndex)),
    ...insight.phrases.map((item, itemIndex) => buildAsset('phrase', '短语', item, insight.blockId, itemIndex)),
    ...insight.grammarPoints.map((item, itemIndex) => buildAsset('grammar', '语法', item, insight.blockId, itemIndex)),
    {
      id: `asset-review-card-${insight.blockId}`,
      type: 'review-card' as const,
      label: '复习卡',
      text: insight.keySentence,
      sourceBlockId: insight.blockId,
    },
  ])

  return {
    id: draft.id,
    title: draft.title,
    subtitle: draft.subtitle,
    sourceType: draft.sourceType,
    sourceLabel: draft.sourceLabel,
    mode: draft.mode,
    parseStatus: formatParseStatus(draft),
    progress: draft.progress,
    pdfPreviewUrl: draft.pdfPreviewUrl,
    pageCount: draft.pageCount,
    blocks,
    insights,
    assets,
  }
}

export function buildDocumentSelectionContext(
  documentId: string,
  block: DocumentBlock,
  selectedText?: string | null,
): DocumentSelectionContext {
  const text = selectedText?.trim() || block.text
  return {
    documentId,
    pageNumber: block.pageNumber || 1,
    blockId: block.id,
    elementId: block.elementId || block.id,
    bbox: block.bbox ?? null,
    text,
  }
}

export function buildAssetStats(document: IntensiveReadingDocument): AssetStat[] {
  const stats: AssetStat[] = [
    { id: 'vocabulary', label: '生词', value: 0 },
    { id: 'phrase', label: '短语', value: 0 },
    { id: 'sentence', label: '句型', value: 0 },
    { id: 'grammar', label: '语法', value: 0 },
    { id: 'review-card', label: '复习卡', value: 0 },
    { id: 'note', label: '笔记', value: 0 },
  ]

  for (const asset of document.assets) {
    const stat = stats.find((item) => item.id === asset.type)
    if (stat) stat.value += 1
  }

  return stats.map((stat) => {
    if (stat.id === 'sentence') {
      return { ...stat, value: Math.max(1, document.insights.length) }
    }
    if (stat.id === 'note') {
      return { ...stat, value: document.insights.filter((item) => item.noteDraft).length }
    }
    return stat
  })
}

export function buildAgentModeCapabilities(mode: IntensiveAgentMode): AgentModeCapability[] {
  const common = [
    { id: 'translation', title: '推荐译文', description: '根据当前段落生成适合学习的中文译文。' },
    { id: 'notes', title: '学习笔记', description: '把当前解释整理成可保存的笔记草稿。' },
  ]

  if (mode === 'exam') {
    return [
      { id: 'evidence', title: '定位依据', description: '提取题干关键词、定位句和答案依据。' },
      { id: 'distractor', title: '干扰项分析', description: '分析选项误导点和同义替换。' },
      ...common,
    ]
  }

  if (mode === 'technical') {
    return [
      { id: 'terms', title: '术语库', description: '沉淀技术术语、中文译法和上下文用法。' },
      { id: 'logic', title: '段落逻辑', description: '梳理论文或技术文档的论证结构。' },
      ...common,
    ]
  }

  if (mode === 'foreign') {
    return [
      { id: 'transfer', title: '表达迁移', description: '把外刊表达整理为写作可复用句型。' },
      { id: 'argument', title: '观点表达', description: '提炼观点、转折和论证方式。' },
      ...common,
    ]
  }

  return [
    { id: 'sentence', title: '长难句拆解', description: '拆解主干、修饰成分和从句关系。' },
    { id: 'phrases', title: '短语沉淀', description: '提取短语、生词和语法点。' },
    ...common,
  ]
}

function buildInsight(block: DocumentBlock, index: number): TranslationInsight {
  const firstSentence = splitSentences(block.text)[0] ?? block.text
  const phrase = extractPhrase(firstSentence, index)
  const word = extractWord(firstSentence, index)

  return {
    id: `insight-${block.id}`,
    blockId: block.id,
    translation: `等待 AI 生成译文：${block.text}`,
    summary: `本段说明 ${firstSentence.slice(0, 34)}${firstSentence.length > 34 ? '...' : ''}`,
    keySentence: firstSentence,
    phrases: [phrase],
    vocabulary: [word],
    grammarPoints: [
      {
        text: index % 2 === 0 ? '比较结构' : '非谓语结构',
        meaning: index % 2 === 0 ? '关注 faster than / more than 等比较表达。' : '关注动名词、分词短语承担的修饰作用。',
      },
    ],
    noteDraft: index === 0 ? '这段可以作为文章主旨和核心观点的起点。' : '',
    cardCount: 1,
  }
}

function buildAsset(
  type: LearningAssetType,
  label: string,
  chip: LearningChip,
  blockId: string,
  index: number,
): LearningAsset {
  return {
    id: `asset-${type}-${blockId}-${index + 1}`,
    type,
    label,
    text: chip.text,
    sourceBlockId: blockId,
  }
}

function extractPhrase(sentence: string, index: number): LearningChip {
  const words = sentence.split(/\s+/).filter(Boolean)
  const text = words.slice(Math.min(index, Math.max(0, words.length - 3)), Math.min(words.length, index + 3)).join(' ')
  return {
    text: text || 'key expression',
    meaning: '可沉淀为短语表达，后续由 AI 生成精确释义。',
  }
}

function extractWord(sentence: string, index: number): LearningChip {
  const words = sentence
    .replace(/[^\w\s-]/g, '')
    .split(/\s+/)
    .filter((word) => word.length >= 5)
  const text = words[index % Math.max(1, words.length)] || 'vocabulary'
  return {
    text,
    meaning: '待 AI 补充词义、词性和例句。',
  }
}

function normalizeWhitespace(value: string): string {
  return value
    .replace(/\r\n?/g, '\n')
    .split('\n')
    .map((line) => line.trim())
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function buildWorkspaceSegmentFromParsedItem(
  block: TranslationDocumentBlockDto | TranslationDocumentElementDto,
  index: number,
): TranslationWorkspaceSegment {
  const element = block as TranslationDocumentElementDto
  return {
    id: block.id || `segment-${index + 1}`,
    elementId: element.id || block.id || `segment-${index + 1}`,
    source: block.text,
    translation: '等待 AI 翻译',
    translationStatus: 'pending',
    blockType: normalizeDocumentBlockType(block.type),
    pageNumber: block.pageNumber,
    bbox: element.bbox ?? null,
    confidence: block.confidence ?? null,
  }
}

function splitSentences(text: string): string[] {
  const matches = text.match(/[^.!?。！？]+[.!?。！？]?/g) ?? [text]
  return matches.map((item) => item.trim()).filter(Boolean)
}

function getSourceType(fileName: string, pastedText: string): TranslationSourceType {
  if (!fileName) return pastedText ? 'text' : 'text'
  return fileName.toLowerCase().endsWith('.pdf') ? 'pdf' : 'text'
}

function getSourceLabel(fileName: string, pastedText: string): string {
  if (!fileName) return pastedText ? '粘贴文本' : 'TXT'
  if (fileName.toLowerCase().endsWith('.pdf')) return 'PDF'
  if (fileName.toLowerCase().endsWith('.md')) return 'MD'
  return 'TXT'
}

function stripFileExtension(fileName: string): string {
  return fileName.replace(/\.[^.]+$/, '')
}

function buildTitleFromText(text: string): string {
  const singleLine = text.replace(/\s+/g, ' ').trim()
  return singleLine.slice(0, 39)
}

function buildSubtitle(fileName: string, sourceText: string): string {
  if (fileName) return `用户导入 · ${fileName}`
  const preview = sourceText.replace(/\s+/g, ' ').slice(0, 48)
  return `${preview}${sourceText.length > 48 ? '...' : ''}`
}

function buildParsedDocumentSubtitle(fileName: string, parsedDocument: TranslationDocumentParseResponse): string {
  const pageText = parsedDocument.pageCount > 0 ? `${parsedDocument.pageCount} 页` : '页数待确认'
  const blockText = parsedDocument.blockCount > 0 ? `${parsedDocument.blockCount} 个段落` : '未解析出段落'
  return `用户导入 · ${fileName} · ${pageText} · ${blockText}`
}

function normalizeDocumentBlockType(type: string | null | undefined): DocumentBlockType {
  if (
    type === 'title'
    || type === 'heading'
    || type === 'paragraph'
    || type === 'list'
    || type === 'table'
    || type === 'quote'
    || type === 'code'
    || type === 'question'
    || type === 'option'
  ) {
    return type
  }
  return 'paragraph'
}

function normalizeParsedSourceLabel(sourceType: string | null | undefined, fileName: string): string {
  const normalized = sourceType?.toUpperCase()
  if (normalized === 'PDF' || normalized === 'TXT' || normalized === 'MD' || normalized === 'DOCX') {
    return normalized
  }
  return getSourceLabel(fileName, '')
}

function formatParseStatus(draft: TranslationWorkspaceDraft): string {
  if (draft.parseStatus === 'SUCCEEDED' && draft.ocrStatus === 'SUCCEEDED') return 'OCR 解析完成'
  if (draft.parseStatus === 'SUCCEEDED') return draft.sourceType === 'pdf' ? 'PDF 结构解析完成' : 'AI 已生成精读材料'
  if (draft.parseStatus === 'NEEDS_OCR') return '需要 OCR 识别'
  if (draft.parseStatus === 'FAILED') return '解析失败'
  return draft.sourceType === 'pdf' ? 'PDF 结构解析完成' : 'AI 已生成精读材料'
}

function shouldPreserveSegmentId(id: string | undefined): id is string {
  return !!id && !id.startsWith('segment-')
}
