import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const apiSource = readFileSync(new URL('../src/api/learningNotes.ts', import.meta.url), 'utf8')

for (const requiredText of [
  "from './http'",
  '/learning-notes',
  '/learning-notes/organize',
  'createLearningNote',
  'updateLearningNote',
  'deleteLearningNote',
  'listLearningNotes',
  'organizeLearningAssetMarkdown',
  'candidateMarkdown',
]) {
  assert.ok(apiSource.includes(requiredText), `learning notes API should include ${requiredText}`)
}

console.log('learning-notes-api-ok')
