import assert from 'node:assert/strict'
import {
  buildAgentModeCapabilities,
  buildAssetStats,
  buildIntensiveReadingDocument,
  createTranslationWorkspaceDraft,
  createTranslationWorkspaceDraftFromParsedDocument,
  listTranslationWorkspaceDrafts,
  loadTranslationWorkspaceDraft,
  saveTranslationWorkspaceDraft,
  validateNewTranslationInput,
} from '../src/pages/app/translationWorkspaceData.ts'

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>()

  get length() {
    return this.values.size
  }

  clear(): void {
    this.values.clear()
  }

  getItem(key: string): string | null {
    return this.values.get(key) ?? null
  }

  key(index: number): string | null {
    return Array.from(this.values.keys())[index] ?? null
  }

  removeItem(key: string): void {
    this.values.delete(key)
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value)
  }
}

const fixedNow = new Date('2026-06-08T08:30:00.000Z')

const pastedDraft = createTranslationWorkspaceDraft(
  {
    mode: 'immersive',
    pastedText: 'Artificial intelligence is changing how students read. Teachers need better tools to guide active learning.',
    selectedFileName: '',
  },
  fixedNow,
)

assert.equal(pastedDraft.id, 'translation-1780907400000')
assert.equal(pastedDraft.mode, 'immersive')
assert.equal(pastedDraft.sourceType, 'text')
assert.equal(pastedDraft.sourceLabel, '粘贴文本')
assert.equal(pastedDraft.progress, 0)
assert.equal(pastedDraft.status, 'reading')
assert.equal(pastedDraft.noteCount, 0)
assert.equal(pastedDraft.title, 'Artificial intelligence is changing how')
assert.ok(pastedDraft.segments.length >= 1, 'pasted draft should create readable segments')
assert.ok(
  pastedDraft.segments.every((segment) => segment.translationStatus === 'pending'),
  'new workspace segments should start as pending translation',
)

for (const [fileName, expectedType, expectedLabel] of [
  ['research-paper.pdf', 'pdf', 'PDF'],
  ['daily-reading.txt', 'text', 'TXT'],
  ['notes.md', 'text', 'MD'],
] as const) {
  const draft = createTranslationWorkspaceDraft(
    { mode: 'exam', pastedText: '', selectedFileName: fileName },
    fixedNow,
  )
  assert.equal(draft.sourceType, expectedType)
  assert.equal(draft.sourceLabel, expectedLabel)
  assert.equal(draft.title, fileName.replace(/\.[^.]+$/, ''))
  assert.equal(draft.mode, 'exam')
  assert.ok(draft.sourceText.includes(fileName), 'file-only draft should still show an import placeholder')
}

assert.deepEqual(
  validateNewTranslationInput({ mode: 'immersive', pastedText: '', selectedFileName: '' }),
  { valid: false, message: '请上传文件或粘贴至少 10 个字符' },
)

assert.deepEqual(
  validateNewTranslationInput({ mode: 'immersive', pastedText: 'too short', selectedFileName: '' }),
  { valid: false, message: '粘贴文本至少需要 10 个字符' },
)

assert.deepEqual(
  validateNewTranslationInput({ mode: 'exam', pastedText: 'A valid short paragraph.', selectedFileName: '' }),
  { valid: true },
)

const storage = new MemoryStorage()
saveTranslationWorkspaceDraft(storage, pastedDraft)
assert.deepEqual(loadTranslationWorkspaceDraft(storage, pastedDraft.id), pastedDraft)
assert.equal(loadTranslationWorkspaceDraft(storage, 'missing-id'), null)
assert.deepEqual(listTranslationWorkspaceDrafts(storage), [pastedDraft])

const readingDocument = buildIntensiveReadingDocument(pastedDraft)
assert.equal(readingDocument.title, pastedDraft.title)
assert.equal(readingDocument.mode, 'immersive')
assert.equal(readingDocument.parseStatus, 'AI 已生成精读材料')
assert.ok(readingDocument.blocks.length >= pastedDraft.segments.length, 'draft segments should become document blocks')
assert.ok(
  readingDocument.blocks.every((block) => block.id.startsWith('block-')),
  'document blocks should expose stable block ids',
)
assert.ok(
  readingDocument.insights.every((insight) => insight.blockId.startsWith('block-')),
  'translation insights should be linked to document blocks',
)

const activeInsight = readingDocument.insights[0]
assert.ok(activeInsight.translation.includes('等待 AI 生成译文'), 'initial insight should show pending translation copy')
assert.ok(activeInsight.phrases.length > 0, 'initial insight should include mock phrase learning assets')
assert.ok(activeInsight.vocabulary.length > 0, 'initial insight should include mock vocabulary learning assets')
assert.ok(activeInsight.grammarPoints.length > 0, 'initial insight should include mock grammar learning assets')

const assetStats = buildAssetStats(readingDocument)
assert.ok(assetStats.some((item) => item.label === '生词'), 'asset stats should include vocabulary')
assert.ok(assetStats.some((item) => item.label === '短语'), 'asset stats should include phrases')
assert.ok(assetStats.some((item) => item.label === '复习卡'), 'asset stats should include review cards')

const examCapabilities = buildAgentModeCapabilities('exam')
assert.ok(examCapabilities.some((item) => item.title === '定位依据'), 'exam mode should expose evidence analysis')
const technicalCapabilities = buildAgentModeCapabilities('technical')
assert.ok(technicalCapabilities.some((item) => item.title === '术语库'), 'technical mode should expose terminology features')

const parsedPdfDraft = createTranslationWorkspaceDraftFromParsedDocument(
  { mode: 'immersive', pdfPreviewUrl: 'blob:http://localhost/pdf-preview' },
  {
    documentId: 'parsed-doc-1',
    fileName: 'AI精读产品方案.pdf',
    sourceType: 'PDF',
    parseStatus: 'SUCCEEDED',
    ocrStatus: 'NOT_REQUIRED',
    pageCount: 2,
    blockCount: 2,
    blocks: [
      {
        id: 'p1-b1',
        type: 'heading',
        order: 1,
        pageNumber: 1,
        text: 'AI Intensive Reading Product Design',
        confidence: null,
      },
      {
        id: 'p2-b2',
        type: 'paragraph',
        order: 2,
        pageNumber: 2,
        text: 'The workspace should show real parsed document content instead of upload placeholders.',
        confidence: null,
      },
    ],
    warnings: [],
  },
  fixedNow,
)

assert.equal(parsedPdfDraft.id, 'parsed-doc-1')
assert.equal(parsedPdfDraft.sourceType, 'pdf')
assert.equal(parsedPdfDraft.sourceLabel, 'PDF')
assert.equal(parsedPdfDraft.pdfPreviewUrl, 'blob:http://localhost/pdf-preview')
assert.equal(parsedPdfDraft.parseStatus, 'SUCCEEDED')
assert.equal(parsedPdfDraft.ocrStatus, 'NOT_REQUIRED')
assert.equal(parsedPdfDraft.pageCount, 2)
assert.equal(parsedPdfDraft.sourceText.includes('已上传'), false)
assert.equal(parsedPdfDraft.segments[0].source, 'AI Intensive Reading Product Design')
assert.equal(parsedPdfDraft.segments[0].blockType, 'heading')
assert.equal(parsedPdfDraft.segments[1].pageNumber, 2)

const parsedReadingDocument = buildIntensiveReadingDocument(parsedPdfDraft)
assert.equal(parsedReadingDocument.parseStatus, 'PDF 结构解析完成')
assert.equal(parsedReadingDocument.pdfPreviewUrl, 'blob:http://localhost/pdf-preview')
assert.equal(parsedReadingDocument.blocks[0].id, 'p1-b1')
assert.equal(parsedReadingDocument.blocks[0].type, 'heading')
assert.equal(parsedReadingDocument.blocks[1].text.includes('real parsed document content'), true)

const parsedDocxDraft = createTranslationWorkspaceDraftFromParsedDocument(
  { mode: 'immersive' },
  {
    documentId: 'parsed-docx-1',
    fileName: 'lesson.docx',
    sourceType: 'DOCX',
    parseStatus: 'SUCCEEDED',
    ocrStatus: 'NOT_REQUIRED',
    pageCount: 0,
    blockCount: 1,
    blocks: [
      {
        id: 'docx-b1',
        type: 'heading',
        order: 1,
        pageNumber: 0,
        text: 'Word Document Heading',
        confidence: null,
      },
    ],
    warnings: [],
  },
  fixedNow,
)

assert.equal(parsedDocxDraft.id, 'parsed-docx-1')
assert.equal(parsedDocxDraft.sourceType, 'text')
assert.equal(parsedDocxDraft.sourceLabel, 'DOCX')
assert.equal(parsedDocxDraft.subtitle.includes('lesson.docx'), true)
assert.equal(buildIntensiveReadingDocument(parsedDocxDraft).parseStatus, 'AI 已生成精读材料')

console.log('translation-workspace-data-ok')
