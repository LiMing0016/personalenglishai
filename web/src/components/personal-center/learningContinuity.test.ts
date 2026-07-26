import assert from 'node:assert/strict'
import test from 'node:test'

import { buildLearningContinuity } from './learningContinuity.ts'

test('最近写作记录成为真实的上次完成节点', () => {
  const model = buildLearningContinuity({
    recentItem: {
      essay_preview: 'Some people believe cities should build more parks.',
      created_at: '2026-07-24T21:18:00+08:00',
      overall_score: 7,
    },
    studyDays: 2,
  })

  assert.deepEqual(model.previous, {
    hasHistory: true,
    title: 'Some people believe cities should build more parks.',
    description: '写作评测 · 7 分',
    occurredAt: '2026-07-24T21:18:00+08:00',
  })
  assert.equal(model.weeklyProgress.completed, 2)
  assert.equal(model.weeklyProgress.total, 5)
})

test('没有历史记录时返回行动型空状态而不是伪造内容', () => {
  const model = buildLearningContinuity({
    recentItem: null,
    studyDays: 0,
  })

  assert.deepEqual(model.previous, {
    hasHistory: false,
    title: '还没有完成记录',
    description: '完成一次学习后会沉淀在这里',
    occurredAt: null,
  })
})

test('学习节点限制在零到五之间', () => {
  assert.equal(buildLearningContinuity({ recentItem: null, studyDays: -3 }).weeklyProgress.completed, 0)
  assert.equal(buildLearningContinuity({ recentItem: null, studyDays: 12 }).weeklyProgress.completed, 5)
})
