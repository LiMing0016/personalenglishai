import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import axios from 'axios'

import { http } from '../src/api/http'
import {
  deleteVocabularyCard,
  updateVocabularyCard,
  VocabularyConflictError,
  type VocabularyConflictResponse,
} from '../src/api/vocabulary'

const vocabularyApiSource = readFileSync(
  new URL('../src/api/vocabulary.ts', import.meta.url),
  'utf8',
)

test('keeps every vocabulary API function on its source-contract endpoint', () => {
  for (const requiredText of [
    "listVocabularyTemplates = () =>",
    "'/vocabulary/templates'",
    "captureVocabulary = (payload: VocabularyCaptureRequest)",
    "'/vocabulary/captures'",
    "listVocabularyCards = (params: VocabularyCardFilters)",
    "'/vocabulary/cards'",
    'getVocabularyCard = (cardUid: string)',
    "`/vocabulary/cards/${encodeURIComponent(cardUid)}`",
    'updateVocabularyCard = (cardUid: string, payload: UpdateVocabularyCardRequest)',
    "put(`/vocabulary/cards/${encodeURIComponent(cardUid)}`",
    'deleteVocabularyCard = (cardUid: string)',
    "delete(`/vocabulary/cards/${encodeURIComponent(cardUid)}`",
    'regenerateVocabularyCard = (cardUid: string)',
    "`/vocabulary/cards/${encodeURIComponent(cardUid)}/regenerate`",
    'retryVocabularyCard = (cardUid: string)',
    "`/vocabulary/cards/${encodeURIComponent(cardUid)}/retry`",
    'listVocabularyRevisions = (cardUid: string)',
    "`/vocabulary/cards/${encodeURIComponent(cardUid)}/revisions`",
    'resolveVocabularyConflict = (',
    "`/vocabulary/cards/${encodeURIComponent(cardUid)}/conflicts/${encodeURIComponent(revisionUid)}/resolve`",
    'baseRevisionUid: string',
  ]) {
    assert.ok(vocabularyApiSource.includes(requiredText), `vocabulary API should include ${requiredText}`)
  }
})

globalThis.localStorage = {
  getItem: () => null,
  setItem: () => undefined,
  removeItem: () => undefined,
  clear: () => undefined,
  key: () => null,
  get length() { return 0 },
} as Storage

const originalAdapter = http.defaults.adapter

test.afterEach(() => {
  http.defaults.adapter = originalAdapter
})

test('returns undefined when deleting a vocabulary card succeeds with missing or null data', async () => {
  let requestCount = 0

  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'delete')
    assert.equal(config.url, '/vocabulary/cards/card%20uid')
    requestCount += 1

    return {
      config,
      data: requestCount === 1
        ? { code: '200000', message: 'deleted' }
        : { code: '200000', message: 'deleted', data: null },
      headers: {},
      status: 200,
      statusText: 'OK',
    }
  }

  assert.equal(await deleteVocabularyCard('card uid'), undefined)
  assert.equal(await deleteVocabularyCard('card uid'), undefined)
})

test('converts a revision conflict envelope into VocabularyConflictError with its payload', async () => {
  const conflict: VocabularyConflictResponse = {
    currentRevisionUid: 'revision-current',
    candidateRevisionUid: 'revision-candidate',
    currentContent: { definition: 'current' },
    candidateContent: { definition: 'candidate' },
    conflictStatus: 'needs_review',
  }

  http.defaults.adapter = async (config) => {
    const response = {
      config,
      data: { code: '409030', message: 'revision conflict', data: conflict },
      headers: {},
      status: 409,
      statusText: 'Conflict',
    }
    throw new axios.AxiosError('Request failed with status code 409', undefined, config, undefined, response)
  }

  await assert.rejects(
    updateVocabularyCard('card uid', { baseRevisionUid: 'revision-base', content: { definition: 'edited' } }),
    (error: unknown) => {
      assert.ok(error instanceof VocabularyConflictError)
      assert.deepEqual(error.conflict, conflict)
      return true
    },
  )
})
