import assert from 'node:assert/strict'
import test from 'node:test'

import type { VocabularyCardBlocks } from '../src/api/vocabulary.ts'
import {
  buildVocabularyMarkdownOutline,
  vocabularyCardBlocksToMarkdown,
} from '../src/features/vocabulary/vocabularyLearningMarkdown.ts'

const blocks: VocabularyCardBlocks = {
  schemaVersion: 1,
  blocks: [
    {
      id: 'usage',
      type: 'usageBoundary',
      title: '使用边界',
      meaningRefs: [],
      format: 'structured',
      content: {
        useWhen: ['讨论由人类活动造成的影响'],
        avoidWhen: ['泛指人类善意或文化'],
      },
      source: 'ai',
      sourceRef: null,
      sortOrder: 20,
      userEdited: false,
      locked: false,
    },
    {
      id: 'examples',
      type: 'exampleList',
      title: '例句',
      meaningRefs: [],
      format: 'structured',
      content: {
        items: [{
          sentence: 'The anthropic principle concerns observers.',
          translation: '人择原理关注观察者存在的条件。',
        }],
      },
      source: 'ai',
      sourceRef: null,
      sortOrder: 10,
      userEdited: false,
      locked: false,
    },
    {
      id: 'contrast',
      type: 'contrastTable',
      title: '易混辨析',
      meaningRefs: [],
      format: 'structured',
      content: {
        rows: [{
          term: 'human',
          focus: '与人类有关',
          typicalContext: '一般描述 | 生物学',
        }],
      },
      source: 'ai',
      sourceRef: null,
      sortOrder: 30,
      userEdited: false,
      locked: false,
    },
    {
      id: 'memory',
      type: 'memoryTip',
      title: '记忆提示',
      meaningRefs: [],
      format: 'structured',
      content: { points: ['anthrop- 表示“人类”'] },
      source: 'ai',
      sourceRef: null,
      sortOrder: 40,
      userEdited: false,
      locked: false,
    },
    {
      id: 'note',
      type: 'note',
      title: '个人笔记',
      meaningRefs: [],
      format: 'markdown',
      content: '## 个人笔记\n\n我在宇宙学文章中见过这个词。',
      source: 'user',
      sourceRef: null,
      sortOrder: 50,
      userEdited: true,
      locked: true,
    },
  ],
}

test('converts ordered Card Blocks into one editable Markdown document', () => {
  const markdown = vocabularyCardBlocksToMarkdown(blocks)

  assert.ok(markdown.indexOf('## 例句') < markdown.indexOf('## 使用边界'))
  assert.match(
    markdown,
    /\*\*1\. The anthropic principle concerns observers\.\*\*\n\n> 人择原理关注观察者存在的条件。/,
  )
  assert.match(markdown, /### 适合使用[\s\S]*讨论由人类活动造成的影响/)
  assert.match(markdown, /### 谨慎使用[\s\S]*泛指人类善意或文化/)
  assert.match(markdown, /## 易混辨析[\s\S]*### human[\s\S]*侧重点：与人类有关[\s\S]*典型语境：一般描述 \| 生物学/)
  assert.match(markdown, /## 记忆提示[\s\S]*anthrop- 表示“人类”/)
  assert.equal((markdown.match(/## 个人笔记/g) ?? []).length, 1)
  assert.deepEqual(
    buildVocabularyMarkdownOutline(markdown).map((item) => item.title),
    ['例句', '使用边界', '易混辨析', '记忆提示', '个人笔记'],
  )
})

test('builds a stable outline from second-level Markdown headings only', () => {
  assert.deepEqual(buildVocabularyMarkdownOutline([
    '# anthropic',
    '## 例句',
    '### 人择含义',
    '## 使用边界',
    '## 例句',
  ].join('\n')), [
    { id: 'markdown-outline-1', title: '例句', level: 2 },
    { id: 'markdown-outline-2', title: '使用边界', level: 2 },
    { id: 'markdown-outline-3', title: '例句', level: 2 },
  ])
})
