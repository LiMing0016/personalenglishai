import assert from 'node:assert/strict'
import test from 'node:test'

import type { VocabularyCoreContent } from '../src/api/vocabulary.ts'

test('header sense summaries prefer Chinese definitions and limit the compact header', async () => {
  const module = await import('../src/features/vocabulary/vocabularyCardHeader.ts').catch(() => ({}))
  const buildSummaries = (module as Record<string, unknown>).buildVocabularyHeaderSenseSummaries

  assert.equal(typeof buildSummaries, 'function')

  const core: VocabularyCoreContent = {
    schemaVersion: 1,
    term: 'record',
    phonetics: [],
    senses: [
      {
        partOfSpeech: 'noun',
        meanings: [
          { definitionEn: 'stored information', definitionZh: '记录；档案' },
          { definitionEn: 'the best performance', definitionZh: '纪录' },
        ],
      },
      {
        partOfSpeech: 'verb',
        meanings: [{ definitionEn: 'to store information', definitionZh: '' }],
      },
      {
        partOfSpeech: 'adjective',
        meanings: [{ definitionEn: 'record-breaking', definitionZh: '破纪录的' }],
      },
    ],
  }

  assert.deepEqual(
    (buildSummaries as (value: VocabularyCoreContent) => unknown)(core),
    [
      { partOfSpeech: 'noun', meaning: '记录；档案' },
      { partOfSpeech: 'verb', meaning: 'to store information' },
    ],
  )
})

test('header sense summaries omit empty senses and show one meaning per part of speech', async () => {
  const module = await import('../src/features/vocabulary/vocabularyCardHeader.ts').catch(() => ({}))
  const buildSummaries = (module as Record<string, unknown>).buildVocabularyHeaderSenseSummaries

  assert.equal(typeof buildSummaries, 'function')

  const core: VocabularyCoreContent = {
    schemaVersion: 1,
    term: 'anthropic',
    phonetics: [],
    senses: [
      { partOfSpeech: 'adjective', meanings: [] },
      { partOfSpeech: 'adjective', meanings: [{ definitionEn: 'caused by people', definitionZh: '由人类活动造成的' }] },
      { partOfSpeech: 'adjective', meanings: [{ definitionEn: 'relating to humans', definitionZh: '人类的；人为的' }] },
    ],
  }

  assert.deepEqual(
    (buildSummaries as (value: VocabularyCoreContent) => unknown)(core),
    [{ partOfSpeech: 'adjective', meaning: '由人类活动造成的' }],
  )
})
