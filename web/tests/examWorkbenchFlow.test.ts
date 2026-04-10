import test from 'node:test'
import assert from 'node:assert/strict'

import {
  normalizePromptSheet,
  type ExamPromptSheet,
} from '../src/pages/app/examPromptHelpers.ts'
import type { GenerateExamPromptResponse } from '../src/api/writing.ts'

test('normalizePromptSheet maps chart prompt into visual attachment sheet', () => {
  const response: GenerateExamPromptResponse = {
    promptType: 'chart',
    topic: 'AI Agent adoption',
    promptText: 'Write an essay based on the table below.',
    requirements: 'describe the changes and give your comments',
    genre: '议论文',
    wordRange: '160-200',
    maxScore: 20,
    sourceType: 'ai_generated',
    taskType: 'chart',
    minWords: 160,
    recommendedMaxWords: 200,
    materialText: null,
    chartSpec: {
      title: 'Adoption of AI Agent Tools',
      displayType: 'table',
      columns: ['Year', 'Rate'],
      rows: [['2021', '18%'], ['2024', '63%']],
      summary: 'The rate rose steadily.',
    },
    comicScenes: [],
  }

  const sheet: ExamPromptSheet = normalizePromptSheet(response)

  assert.equal(sheet.attachmentType, 'visual')
  assert.equal(sheet.visualKind, 'table')
  assert.equal(sheet.attachmentTitle, 'Adoption of AI Agent Tools')
  assert.match(sheet.attachmentContent ?? '', /18%/)
})

test('normalizePromptSheet maps general prompt into no-attachment sheet', () => {
  const response: GenerateExamPromptResponse = {
    promptType: 'general',
    topic: 'Youth and family duty',
    promptText: 'Write an essay on the balance between personal ambition and family duty.',
    requirements: 'give reasons and comments',
    genre: '议论文',
    wordRange: '160-200',
    maxScore: 20,
    sourceType: 'ai_generated',
    taskType: 'general',
    minWords: 160,
    recommendedMaxWords: 200,
    materialText: null,
    chartSpec: null,
    comicScenes: [],
  }

  const sheet = normalizePromptSheet(response)

  assert.equal(sheet.attachmentType, 'none')
  assert.equal(sheet.attachmentContent ?? null, null)
})
