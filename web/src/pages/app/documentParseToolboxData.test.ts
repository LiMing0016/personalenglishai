import test from 'node:test'
import assert from 'node:assert/strict'

import {
  DEFAULT_DOCUMENT_PARSE_TOOLBOX_MODEL_ID,
  buildDocumentParseToolboxAssetPages,
  buildDocumentParseToolboxRecentItem,
  documentParseToolboxModelOptions,
  formatDocumentParseToolboxStatus,
  shouldRefreshDocumentParseToolboxResult,
} from './documentParseToolboxData.ts'
import type { TranslationDocumentParseResponse } from '../../api/translation.ts'

function buildParsedDocument(
  overrides: Partial<TranslationDocumentParseResponse> = {},
): TranslationDocumentParseResponse {
  return {
    documentId: 'doc-toolbox-1',
    fileName: '计算机组成与系统结构(第3版).pdf',
    sourceType: 'PDF',
    parseStatus: 'SUCCEEDED',
    ocrStatus: 'SUCCEEDED',
    provider: 'PaddleOCR',
    parseMode: 'standard',
    fileUrl: null,
    elapsedMs: 12600,
    pageCount: 364,
    blockCount: 2,
    blocks: [
      {
        id: 'p1-b1',
        type: 'title',
        order: 1,
        pageNumber: 1,
        text: '计算机组成与系统结构',
        confidence: 0.92,
      },
    ],
    elements: [],
    outline: [],
    knowledgeChunks: [],
    assets: [],
    diagnosis: {
      textLayer: 'NONE',
      textCoverageRatio: 0,
      garbledRatio: 0,
      headerFooterRatio: 0,
      imageOnlyPages: [1],
      ocrRecommended: true,
      highQualityProviderRecommended: false,
      fallbackRecommended: false,
      warnings: [],
    },
    quality: {
      documentQualityScore: 0.88,
      textCoverageRatio: 0,
      garbledRatio: 0,
      locationCoverageRatio: 0.8,
      chunkHighQualityRatio: 0.75,
      fallbackRecommended: false,
    },
    languageProfile: {
      primaryLanguage: 'zh',
      secondaryLanguages: [],
      languageMixType: 'single',
      languageConfidence: 0.9,
    },
    parseJob: {
      jobId: 'job-toolbox-1',
      documentId: 'doc-toolbox-1',
      status: 'SUCCEEDED',
      stage: 'DONE',
      progress: 100,
      provider: 'PaddleOCR',
      fallbackUsed: false,
    },
    warnings: [],
    ...overrides,
  }
}

test('toolbox recent item summarizes local PaddleOCR parsing result', () => {
  const item = buildDocumentParseToolboxRecentItem(buildParsedDocument(), '07-01 15:58')

  assert.equal(item.id, 'doc-toolbox-1')
  assert.equal(item.fileName, '计算机组成与系统结构(第3版).pdf')
  assert.equal(item.title, '计算机组成与系统结构(第3版)')
  assert.equal(item.providerLabel, 'PaddleOCR')
  assert.equal(item.pageLabel, '364 页')
  assert.equal(item.statusLabel, '解析完成')
  assert.equal(item.updatedAt, '07-01 15:58')
})

test('partial local OCR is presented as background parsing and keeps refreshing', () => {
  const partial = buildParsedDocument({
    ocrStatus: 'PARTIAL',
    parseJob: {
      jobId: 'job-toolbox-1',
      documentId: 'doc-toolbox-1',
      status: 'RUNNING',
      stage: 'OCR',
      progress: 28,
      provider: 'PaddleOCR',
      fallbackUsed: false,
    },
    warnings: ['已完成前 10 页本地 OCR 解析，剩余页面正在后台继续解析。'],
  })

  assert.equal(formatDocumentParseToolboxStatus(partial), '本地 OCR 后台解析中')
  assert.equal(shouldRefreshDocumentParseToolboxResult(partial), true)
})

test('completed or failed parse result does not keep polling forever', () => {
  assert.equal(shouldRefreshDocumentParseToolboxResult(buildParsedDocument()), false)
  assert.equal(
    shouldRefreshDocumentParseToolboxResult(buildParsedDocument({
      parseStatus: 'FAILED',
      ocrStatus: 'FAILED',
      parseJob: {
        jobId: 'job-toolbox-1',
        documentId: 'doc-toolbox-1',
        status: 'FAILED',
        stage: 'FAILED',
        progress: 100,
        provider: 'PaddleOCR',
        fallbackUsed: false,
      },
    })),
    false,
  )
})

test('toolbox defaults to VL quality and keeps PPStructureV3 as high quality parsing', () => {
  const ppstructure = documentParseToolboxModelOptions.find((model) => model.id === 'ppstructure-v3')
  const paddleVl = documentParseToolboxModelOptions.find((model) => model.id === 'paddle-vl')

  assert.equal(DEFAULT_DOCUMENT_PARSE_TOOLBOX_MODEL_ID, 'paddle-vl')
  assert.equal(ppstructure?.parseMode, 'high_quality')
  assert.equal(ppstructure?.provider, 'paddle-ocr')
  assert.equal(paddleVl?.parseMode, 'high_quality')
  assert.equal(paddleVl?.provider, 'local-paddle-vl')
})

test('toolbox converts parsed image assets into renderable page groups', () => {
  const pages = buildDocumentParseToolboxAssetPages([
    {
      id: 'p1-vl-a1',
      assetType: 'image',
      pageNumber: 1,
      bbox: '[[30,120],[330,120],[330,260],[30,260]]',
      provider: 'paddle_vl',
      recognitionStatus: 'READY',
      confidence: 0.87,
      metadata: {
        mimeType: 'image/jpeg',
        dataBase64: 'ZmFrZS1pbWFnZQ==',
        rawType: 'image',
      },
    },
  ])

  assert.equal(pages.length, 1)
  assert.equal(pages[0]?.pageNumber, 1)
  assert.equal(pages[0]?.assets[0]?.dataUrl, 'data:image/jpeg;base64,ZmFrZS1pbWFnZQ==')
  assert.equal(pages[0]?.assets[0]?.label, '图片')
})
