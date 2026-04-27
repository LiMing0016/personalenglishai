import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildPromptDesignSeedRequest,
  hasPromptDesignSeed,
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

test('prompt design seed is available when settings contain writing constraints', () => {
  assert.equal(hasPromptDesignSeed({ taskLabel: 'Task 1' }), false)
  assert.equal(hasPromptDesignSeed({ genreLabel: '图画作文' }), true)
  assert.equal(hasPromptDesignSeed({ wordRange: '160-200' }), true)
  assert.equal(hasPromptDesignSeed({ requirements: 'describe and comment' }), true)
  assert.equal(hasPromptDesignSeed({ hasImage: true }), true)
})

test('buildPromptDesignSeedRequest turns settings into an AI prompt design request', () => {
  const request = buildPromptDesignSeedRequest({
    studyStage: 'ielts',
    taskLabel: 'Task 1',
    genreLabel: '图画作文',
    wordRange: '160-200',
    requirements: 'describe the picture and give your comments',
    hasImage: true,
  })

  assert.match(request, /学段\/考试：ielts/)
  assert.match(request, /任务类型：Task 1/)
  assert.match(request, /体裁：图画作文/)
  assert.match(request, /字数要求：160-200词/)
  assert.match(request, /不要生成范文或答案/)
})
