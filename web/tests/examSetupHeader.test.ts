import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const examSetupSource = readFileSync(
  new URL('../src/pages/app/ExamSetupPage.vue', import.meta.url),
  'utf8',
)

assert.ok(
  examSetupSource.includes('题目设计'),
  'exam setup page should use the new centered title copy',
)

assert.ok(
  !examSetupSource.includes('写作题目整理'),
  'old exam setup title copy should be removed',
)

console.log('exam-setup-header-ok')
