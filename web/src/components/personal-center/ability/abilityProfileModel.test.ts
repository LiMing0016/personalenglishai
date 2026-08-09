import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildAbilityOverviewModel,
  type AbilityModuleKey,
} from './abilityProfileModel.ts'

const moduleKeys: AbilityModuleKey[] = [
  'writing',
  'vocabulary',
  'reading',
  'listening',
  'speaking',
]

test('总览固定展示五项英语能力且不包含学习助手', () => {
  const overview = buildAbilityOverviewModel(null)
  assert.deepEqual(overview.modules.map((item) => item.key), moduleKeys)
  assert.equal(overview.overallLevelLabel, '待形成')
  assert.equal(overview.coverageCount, 0)
})

test('写作评测只形成待校准证据，不由前端生成 CEFR', () => {
  const overview = buildAbilityOverviewModel({
    taskScore: 68,
    coherenceScore: 72,
    grammarScore: 61,
    vocabularyScore: 64,
    structureScore: 70,
    varietyScore: 58,
    assessedScore: 66,
    confidence: 0.7,
    sampleCount: 4,
    updatedAt: '2026-08-09T12:00:00+08:00',
  })
  const writing = overview.modules.find((item) => item.key === 'writing')
  assert.equal(overview.coverageCount, 1)
  assert.equal(writing?.levelLabel, '待校准')
  assert.equal(writing?.evidenceState, 'collecting')
  assert.equal(writing?.evidenceCount, 4)
  assert.equal(overview.modules.find((item) => item.key === 'vocabulary')?.levelLabel, '待测')
})
