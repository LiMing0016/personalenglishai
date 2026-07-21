import test from 'node:test'
import assert from 'node:assert/strict'

import {
  definitionFor,
  isFallbackAssistantBlock,
  normalizeAssistantBlocks,
} from './registry.ts'

const legacyBlocks = [
  {
    id: 'vocab-1',
    type: 'vocab_card',
    version: 1,
    data: { word: 'adapt', meanings: [{ text: '适应' }] },
  },
  {
    id: 'grammar-1',
    type: 'grammar_tree',
    version: 1,
    data: { topic: '一般过去时', root: { id: 'root', label: 'did' } },
  },
  {
    id: 'plan-1',
    type: 'study_plan',
    version: 1,
    data: { title: '一周计划' },
  },
  {
    id: 'sentence-1',
    type: 'sentence_analysis',
    version: 1,
    data: { sentence: 'I learn English every day.' },
  },
]

test('normalizes all legacy learning blocks and synthesizes fallbacks', () => {
  const blocks = normalizeAssistantBlocks(legacyBlocks)

  assert.equal(blocks.length, 4)
  assert.deepEqual(blocks.map((block) => block.type), [
    'vocab_card',
    'grammar_tree',
    'study_plan',
    'sentence_analysis',
  ])
  assert.ok(blocks.every((block) => block.fallbackMarkdown.trim().length > 0))
  assert.ok(blocks.every((block) => definitionFor(block) !== undefined))
})

test('keeps an unknown block as a safe fallback when fallback markdown exists', () => {
  const [block] = normalizeAssistantBlocks([
    {
      id: 'future-1',
      type: 'future_card',
      version: 7,
      fallbackMarkdown: '## 暂不支持\n\n仍然可以阅读这段内容。',
      data: { value: true },
    },
  ])

  assert.ok(block)
  assert.equal(isFallbackAssistantBlock(block), true)
  if (!isFallbackAssistantBlock(block)) return
  assert.equal(block.originalType, 'future_card')
  assert.equal(block.originalVersion, 7)
  assert.equal(block.fallbackMarkdown, '## 暂不支持\n\n仍然可以阅读这段内容。')
})

test('keeps an unsupported known version as a fallback', () => {
  const [block] = normalizeAssistantBlocks([
    {
      id: 'vocab-v2',
      type: 'vocab_card',
      version: 2,
      fallbackMarkdown: '**adapt**：适应',
      data: { word: 'adapt' },
    },
  ])

  assert.ok(block)
  assert.equal(isFallbackAssistantBlock(block), true)
})

test('rejects malformed blocks without a usable fallback', () => {
  const blocks = normalizeAssistantBlocks([
    { id: '', type: 'vocab_card', version: 1, data: { word: 'adapt' } },
    { id: 'bad-data', type: 'vocab_card', version: 1, data: {} },
    { id: 'bad-type', type: 'unknown', version: 1, data: {} },
  ])

  assert.deepEqual(blocks, [])
})

test('preserves valid legacy prompt actions and drops malformed actions', () => {
  const [block] = normalizeAssistantBlocks([
    {
      ...legacyBlocks[0],
      actions: [
        { id: 'practice', label: '练一题', prompt: '请出一道练习题' },
        { id: 'bad', label: '无效' },
      ],
    },
  ])

  assert.ok(block && !isFallbackAssistantBlock(block))
  if (!block || isFallbackAssistantBlock(block)) return
  assert.deepEqual(block.actions, [
    { id: 'practice', label: '练一题', prompt: '请出一道练习题' },
  ])
})

test('registers sentence reorder version 1 as an interactive block', () => {
  const [block] = normalizeAssistantBlocks([
    {
      id: 'reorder-1',
      type: 'sentence_reorder',
      version: 1,
      data: {
        activityId: 'activity-1',
        items: [
          {
            id: 'item-1',
            instruction: '组成句子',
            tokens: [
              { id: 'hello', text: 'Hello' },
              { id: 'world', text: 'world' },
            ],
            initialOrder: ['world', 'hello'],
            acceptedOrders: [['hello', 'world']],
          },
        ],
      },
    },
  ])

  assert.ok(block && !isFallbackAssistantBlock(block))
  if (!block || isFallbackAssistantBlock(block)) return
  assert.equal(block.type, 'sentence_reorder')
  assert.equal(definitionFor(block)?.kind, 'interactive')
})
