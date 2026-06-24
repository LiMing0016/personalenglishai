import assert from 'node:assert/strict'

import {
  normalizeAssistantBlocks,
  type AssistantBlock,
} from '../src/types/assistantBlocks.ts'

function main() {
  const validBlocks = normalizeAssistantBlocks([
    {
      id: 'vocab-1',
      type: 'vocab_card',
      version: 1,
      title: '单词卡 · significant',
      fallbackMarkdown: 'significant: 重要的，显著的。',
      data: {
        word: 'significant',
        phonetic: '/sɪɡˈnɪfɪkənt/',
        partOfSpeech: 'adjective',
        meanings: [{ text: '重要的，意义重大的', usage: '强调影响或意义' }],
        examples: [{ en: 'This is a significant improvement.', zh: '这是一个显著的改进。' }],
      },
      actions: [
        {
          id: 'usage',
          label: '讲讲用法',
          prompt: '请详细讲解 significant 的使用场景',
        },
      ],
    },
    {
      id: 'plan-1',
      type: 'study_plan',
      version: 1,
      data: {
        title: '7 天作文提分规划',
        durationDays: 7,
        goal: '建立可执行的写作训练节奏',
        days: [
          {
            day: 1,
            title: '诊断表达问题',
            focus: '找出最影响分数的表达短板',
            tasks: [{ title: '完成一篇限时作文', minutes: 30, output: '一篇初稿' }],
            check: '能说清楚 3 个主要问题',
          },
        ],
      },
    },
  ])

  assert.equal(validBlocks.length, 2)
  assert.equal(validBlocks[0]?.type, 'vocab_card')
  assert.equal(validBlocks[0]?.actions?.[0]?.label, '讲讲用法')
  assert.equal(validBlocks[1]?.type, 'study_plan')

  const mixedBlocks = normalizeAssistantBlocks([
    null,
    { id: 'unknown', type: 'chart', version: 1, data: {} },
    { id: 'missing-data', type: 'vocab_card', version: 1 },
    { id: 123, type: 'sentence_analysis', version: 1, data: {} },
    {
      id: 'sentence-1',
      type: 'sentence_analysis',
      version: 1,
      data: {
        sentence: 'What matters most is how consistently you practice.',
        translation: '最重要的是你练习得有多持续。',
        chunks: [
          {
            text: 'What matters most',
            role: '主语从句',
            explanation: '整个从句作主语。',
          },
        ],
      },
    },
  ])

  assert.equal(mixedBlocks.length, 1)
  assert.equal(mixedBlocks[0]?.id, 'sentence-1')
  assert.equal(mixedBlocks[0]?.type, 'sentence_analysis')

  const emptyBlocks = normalizeAssistantBlocks(undefined)
  assert.deepEqual(emptyBlocks, [])

  const exhaustiveTypes: AssistantBlock['type'][] = [
    'vocab_card',
    'grammar_tree',
    'study_plan',
    'sentence_analysis',
  ]
  assert.equal(exhaustiveTypes.length, 4)

  console.log('assistant-blocks-ok')
}

main()
