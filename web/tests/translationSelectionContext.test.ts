import assert from 'node:assert/strict'

import {
  buildDocumentSelectionContext,
  buildIntensiveReadingDocument,
  createTranslationWorkspaceDraftFromParsedDocument,
} from '../src/pages/app/translationWorkspaceData'

const parsedDocument = {
  documentId: 'doc-001',
  fileName: 'network.pdf',
  sourceType: 'PDF',
  parseStatus: 'SUCCEEDED',
  ocrStatus: 'SUCCEEDED',
  provider: 'PaddleOCR',
  pageCount: 3,
  blockCount: 1,
  blocks: [],
  elements: [
    {
      id: 'el-29',
      type: 'paragraph',
      order: 4,
      pageNumber: 2,
      text: 'Computer Networking: A Top-Down Approach',
      confidence: 0.94,
      bbox: '[[10,20],[300,20],[300,80],[10,80]]',
      provider: 'paddle_ppstructure',
      recognitionStatus: 'SUCCEEDED',
      qualityScore: 0.93,
      metadata: { rawType: 'text' },
    },
  ],
  knowledgeChunks: [],
  assets: [],
  diagnosis: {
    textLayer: 'GOOD',
    textCoverageRatio: 1,
    garbledRatio: 0,
    headerFooterRatio: 0,
    imageOnlyPages: [],
    ocrRecommended: false,
    highQualityProviderRecommended: false,
    fallbackRecommended: false,
    warnings: [],
  },
  quality: {
    documentQualityScore: 0.93,
    textCoverageRatio: 1,
    garbledRatio: 0,
    locationCoverageRatio: 1,
    chunkHighQualityRatio: 1,
    fallbackRecommended: false,
  },
  languageProfile: {
    primaryLanguage: 'en',
    secondaryLanguages: ['zh'],
    languageMixType: 'mixed',
    languageConfidence: 0.9,
  },
  parseJob: {
    jobId: 'job-001',
    documentId: 'doc-001',
    status: 'SUCCEEDED',
    stage: 'DONE',
    progress: 100,
    provider: 'PaddleOCR',
    fallbackUsed: false,
  },
  warnings: [],
}

const draft = createTranslationWorkspaceDraftFromParsedDocument(
  { mode: 'immersive', pdfPreviewUrl: 'blob:network' },
  parsedDocument,
)
const readingDocument = buildIntensiveReadingDocument(draft)
const block = readingDocument.blocks[0]

assert.equal(block.id, 'el-29')
assert.equal(block.elementId, 'el-29')
assert.equal(block.pageNumber, 2)
assert.equal(block.bbox, '[[10,20],[300,20],[300,80],[10,80]]')

const context = buildDocumentSelectionContext(readingDocument.id, block, 'Top-Down Approach')

assert.deepEqual(context, {
  documentId: 'doc-001',
  pageNumber: 2,
  blockId: 'el-29',
  elementId: 'el-29',
  bbox: '[[10,20],[300,20],[300,80],[10,80]]',
  text: 'Top-Down Approach',
})

console.log('translation-selection-context-ok')
