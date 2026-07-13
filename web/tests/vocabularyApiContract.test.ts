import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import axios from 'axios'

import { http } from '../src/api/http'
import {
  deleteVocabularyCard,
  regenerateVocabularyCard,
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
    'regenerateVocabularyCard = (cardUid: string, payload: RegenerateVocabularyCardRequest)',
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

test('adds theme selection and versioned card content without removing legacy fields', () => {
  for (const requiredPattern of [
    /interface VocabularyCoreContent/,
    /schemaVersion:\s*1/,
    /region:\s*'uk'\s*\|\s*'us'\s*\|\s*'other'/,
    /themeUid\?:\s*string/,
    /templateKey\?:\s*VocabularyTemplateKey/,
    /theme:\s*VocabularyThemeSnapshot\s*\|\s*null/,
    /themeVersion:\s*number\s*\|\s*null/,
    /core:\s*VocabularyCoreContent\s*\|\s*null/,
    /markdown:\s*string\s*\|\s*null/,
    /contentFormatVersion:\s*number\s*\|\s*null/,
    /currentContentFormatVersion\?:\s*number\s*\|\s*null/,
    /candidateContentFormatVersion\?:\s*number\s*\|\s*null/,
    /type VocabularyGenerationOutcome = 'complete' \| 'partial' \| 'failed'/,
    /generationOutcome:\s*VocabularyGenerationOutcome\s*\|\s*null/,
    /warning:\s*string\s*\|\s*null/,
    /content:\s*unknown/,
    /useLatestThemeVersion\?:\s*boolean/,
  ]) {
    assert.match(vocabularyApiSource, requiredPattern)
  }
})

test('regenerate sends the selected template in the request body', async () => {
  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'post')
    assert.equal(config.url, '/vocabulary/cards/card%20uid/regenerate')
    assert.deepEqual(JSON.parse(String(config.data)), { templateKey: 'exam' })
    return {
      config,
      data: { code: '200000', data: { jobUid: 'job_1', status: 'pending' } },
      headers: {},
      status: 200,
      statusText: 'OK',
    }
  }

  assert.deepEqual(await regenerateVocabularyCard('card uid', { templateKey: 'exam' }), {
    jobUid: 'job_1',
    status: 'pending',
  })
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
    currentContentFormatVersion: 1,
    candidateContentFormatVersion: null,
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
