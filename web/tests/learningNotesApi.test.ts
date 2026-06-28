import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const apiSource = readFileSync(new URL('../src/api/learningNotes.ts', import.meta.url), 'utf8')

for (const requiredText of [
  "from './http'",
  '/learning-notes',
  '/learning-notes/organize',
  'createLearningNote',
  'updateLearningNote',
  'getLearningNote',
  'deleteLearningNote',
  'listLearningNotes',
  'organizeLearningAssetMarkdown',
  'LearningAssetCopilotAction',
  'action: LearningAssetCopilotAction',
  'instruction?: string',
  'candidateMarkdown',
  'EmptyApiDataError',
  'isApiEnvelope',
  "body.code && body.code !== '0'",
  'body.data === undefined || body.data === null',
]) {
  assert.ok(apiSource.includes(requiredText), `learning notes API should include ${requiredText}`)
}

console.log('learning-notes-api-ok')
