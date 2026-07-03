import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildDocumentParsePages,
  buildIntensiveReadingDocument,
  createTranslationWorkspaceDraftFromParsedDocument,
} from './translationWorkspaceData.ts'
import type { TranslationDocumentParseResponse } from '../../api/translation.ts'

test('partial local OCR response is shown as background parsing in workspace', () => {
  const parsedDocument = {
    documentId: 'doc-partial',
    fileName: 'book.pdf',
    sourceType: 'PDF',
    parseStatus: 'SUCCEEDED',
    ocrStatus: 'PARTIAL',
    pageCount: 12,
    blockCount: 1,
    blocks: [
      {
        id: 'p1-b1',
        type: 'paragraph',
        order: 1,
        pageNumber: 1,
        text: '本地 OCR 首批页面内容',
        confidence: null,
      },
    ],
    elements: [],
    outline: [],
    knowledgeChunks: [],
    assets: [],
    warnings: ['已完成前 10 页本地 OCR 解析，剩余页面正在后台继续解析。'],
  } as unknown as TranslationDocumentParseResponse

  const draft = createTranslationWorkspaceDraftFromParsedDocument({ mode: 'immersive' }, parsedDocument)
  const document = buildIntensiveReadingDocument(draft)

  assert.equal(document.parseStatus, '本地 OCR 后台解析中')
  assert.equal(document.ocrStatus, 'PARTIAL')
  assert.equal(document.pageCount, 12)
})

test('buildDocumentParsePages groups OCR blocks into readable document pages', () => {
  const pages = buildDocumentParsePages([
    {
      id: 'p2-b2',
      type: 'paragraph',
      order: 2,
      pageNumber: 2,
      text: '第二页正文段落',
      confidence: null,
    },
    {
      id: 'p1-title',
      type: 'title',
      order: 1,
      pageNumber: 1,
      text: '计算机组成与系统结构',
      confidence: 0.93,
    },
    {
      id: 'p1-b1',
      type: 'paragraph',
      order: 2,
      pageNumber: 1,
      text: '21世纪大学本科计算机专业系列教材',
      confidence: 0.86,
    },
  ])

  assert.equal(pages.length, 2)
  assert.equal(pages[0].pageNumber, 1)
  assert.equal(pages[0].blocks[0].displayType, 'title')
  assert.equal(pages[0].blocks[0].text, '计算机组成与系统结构')
  assert.equal(pages[0].blocks[1].displayType, 'paragraph')
  assert.equal(pages[1].pageNumber, 2)
})
