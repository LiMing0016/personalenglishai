import test from 'node:test'
import assert from 'node:assert/strict'

import { gradeSentenceReorder } from './grader.ts'
import { normalizeSentenceReorderData } from './schema.ts'

const validData = {
  activityId: 'activity-1',
  items: [
    {
      id: 'item-1',
      instruction: '把单词组成正确的句子',
      translation: '我每天学习英语。',
      tokens: [
        { id: 'i', text: 'I' },
        { id: 'learn', text: 'learn' },
        { id: 'english', text: 'English' },
        { id: 'daily', text: 'every day' },
      ],
      initialOrder: ['daily', 'english', 'i', 'learn'],
      acceptedOrders: [['i', 'learn', 'english', 'daily']],
      explanation: '一般现在时表示习惯性动作。',
      hint: '先找主语。',
    },
  ],
}

test('normalizes a valid sentence reorder activity', () => {
  assert.deepEqual(normalizeSentenceReorderData(validData), validData)
})

test('rejects duplicate token ids', () => {
  const data = structuredClone(validData)
  data.items[0]!.tokens.push({ id: 'i', text: 'duplicate' })

  assert.equal(normalizeSentenceReorderData(data), null)
})

test('rejects missing accepted orders', () => {
  const data = structuredClone(validData)
  data.items[0]!.acceptedOrders = []

  assert.equal(normalizeSentenceReorderData(data), null)
})

test('rejects orders that omit or reference unknown tokens', () => {
  const omitted = structuredClone(validData)
  omitted.items[0]!.acceptedOrders = [['i', 'learn', 'english']]
  const unknown = structuredClone(validData)
  unknown.items[0]!.initialOrder = ['i', 'learn', 'english', 'unknown']

  assert.equal(normalizeSentenceReorderData(omitted), null)
  assert.equal(normalizeSentenceReorderData(unknown), null)
})

test('grades accepted token id orders deterministically', () => {
  const acceptedOrders = [
    ['i', 'learn', 'english', 'daily'],
    ['daily', 'i', 'learn', 'english'],
  ]

  assert.deepEqual(gradeSentenceReorder(['i', 'learn', 'english', 'daily'], acceptedOrders), {
    correct: true,
    answer: ['i', 'learn', 'english', 'daily'],
    expected: ['i', 'learn', 'english', 'daily'],
  })
  assert.deepEqual(gradeSentenceReorder(['english', 'i', 'learn', 'daily'], acceptedOrders), {
    correct: false,
    answer: ['english', 'i', 'learn', 'daily'],
    expected: ['i', 'learn', 'english', 'daily'],
  })
})
