import assert from 'node:assert/strict'
import test from 'node:test'

import { buildVocabularyCardSections } from '../src/components/vocabulary/vocabularyCardSections.ts'

test('builds core markdown sources and history sections in display order', () => {
  assert.deepEqual(buildVocabularyCardSections([
    { id: 'markdown-section-1', title: '例句', level: 2 },
  ], true, true), [
    { id: 'core-information', title: '核心信息' },
    { id: 'markdown-section-1', title: '例句' },
    { id: 'card-sources', title: '来源' },
    { id: 'card-history', title: '历史' },
  ])
})

test('builds only the core section without optional content', () => {
  assert.deepEqual(buildVocabularyCardSections([], false, false), [
    { id: 'core-information', title: '核心信息' },
  ])
})
