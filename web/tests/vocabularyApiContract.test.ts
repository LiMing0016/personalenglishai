import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('../src/api/vocabulary.ts', import.meta.url), 'utf8')

test('matches the vocabulary endpoint and DTO contract', () => {
  for (const endpoint of [
    "'/vocabulary/templates'",
    "'/vocabulary/captures'",
    "'/vocabulary/cards'",
    '`/vocabulary/cards/${encodeURIComponent(cardUid)}`',
    '`/vocabulary/cards/${encodeURIComponent(cardUid)}/regenerate`',
    '`/vocabulary/cards/${encodeURIComponent(cardUid)}/retry`',
    '`/vocabulary/cards/${encodeURIComponent(cardUid)}/revisions`',
    '`/vocabulary/cards/${encodeURIComponent(cardUid)}/conflicts/${encodeURIComponent(revisionUid)}/resolve`',
  ]) {
    assert.ok(source.includes(endpoint), `vocabulary API should call ${endpoint}`)
  }

  for (const field of [
    'baseRevisionUid',
    'candidateRevisionUid',
    'conflictStatus',
    'VocabularyGenerationJobResponse',
    'VocabularyRevisionListResponse',
    'VocabularyConflictError',
    '409030',
  ]) {
    assert.ok(source.includes(field), `vocabulary API should include ${field}`)
  }

  const conflictStart = source.indexOf('export interface VocabularyConflictResponse')
  const conflictSource = source.slice(conflictStart, source.indexOf('\n}', conflictStart))
  assert.ok(conflictSource.includes('currentRevisionUid: string | null'))
  assert.ok(conflictSource.includes('candidateRevisionUid: string | null'))
})
