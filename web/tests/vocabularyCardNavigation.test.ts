import assert from 'node:assert/strict'
import test from 'node:test'

import type { VocabularyCardPage, VocabularyCardSummary } from '../src/api/vocabulary'
import {
  buildVocabularyNavigationQuery,
  parseVocabularyNavigationQuery,
  resolveVocabularyCardSequence,
} from '../src/features/vocabulary/vocabularyCardNavigation'

function card(cardUid: string, displayTerm: string): VocabularyCardSummary {
  return {
    cardUid,
    displayTerm,
    normalizedTerm: displayTerm,
    templateKey: 'basic',
    status: 'ready',
    activeRevisionUid: `rev_${cardUid}`,
    sourceTypes: ['manual'],
    lastCapturedAt: null,
    updatedAt: null,
    candidateRevisionUid: null,
    conflictStatus: 'none',
    generationStatus: null,
    generationError: null,
    generationOutcome: 'complete',
    warning: null,
    phonetic: null,
    coreDefinition: null,
    sourceCount: 1,
  }
}

function page(pageNumber: number, items: VocabularyCardSummary[], total = 5, size = 2): VocabularyCardPage {
  return { items, total, page: pageNumber, size }
}

test('navigation query round-trips every collection filter', () => {
  const query = buildVocabularyNavigationQuery({
    keyword: 'rece',
    status: 'ready',
    sourceType: 'manual',
    sort: 'az',
    page: 2,
    size: 40,
  })

  assert.deepEqual(query, {
    vc: '1',
    keyword: 'rece',
    status: 'ready',
    source: 'manual',
    sort: 'az',
    page: '2',
    size: '40',
  })
  assert.deepEqual(parseVocabularyNavigationQuery(query), {
    keyword: 'rece',
    status: 'ready',
    sourceType: 'manual',
    sort: 'az',
    page: 2,
    size: 40,
  })
})

test('navigation query requires its marker and sanitizes unsupported values', () => {
  assert.equal(parseVocabularyNavigationQuery({}), null)
  assert.deepEqual(parseVocabularyNavigationQuery({
    vc: ['1'],
    keyword: [' receive '],
    status: 'unknown',
    source: ['dictionary'],
    sort: 'newest',
    page: '-3',
    size: '500',
  }), {
    keyword: 'receive',
    sourceType: 'dictionary',
    sort: 'recent',
    page: 1,
    size: 100,
  })
})

test('sequence resolves same-page neighbors and absolute position', () => {
  const sequence = resolveVocabularyCardSequence(page(2, [
    card('card_3', 'package'),
    card('card_4', 'receive'),
  ]), 'card_4')

  assert.deepEqual(sequence, {
    previous: { cardUid: 'card_3', displayTerm: 'package' },
    next: null,
    hasPrevious: true,
    hasNext: true,
    position: 4,
    total: 5,
  })
})

test('sequence consumes adjacent pages at page boundaries', () => {
  const current = page(2, [card('card_3', 'package'), card('card_4', 'receive')])
  const previous = page(1, [card('card_1', 'hello'), card('card_2', 'supposed')])
  const next = page(3, [card('card_5', 'scrutinize')])

  assert.deepEqual(resolveVocabularyCardSequence(current, 'card_3', previous, next), {
    previous: { cardUid: 'card_2', displayTerm: 'supposed' },
    next: { cardUid: 'card_4', displayTerm: 'receive' },
    hasPrevious: true,
    hasNext: true,
    position: 3,
    total: 5,
  })
  assert.deepEqual(resolveVocabularyCardSequence(current, 'card_4', previous, next)?.next, {
    cardUid: 'card_5',
    displayTerm: 'scrutinize',
  })
})

test('sequence disables the ends and rejects a card outside the context page', () => {
  assert.deepEqual(resolveVocabularyCardSequence(
    page(1, [card('card_1', 'hello'), card('card_2', 'supposed')]),
    'card_1',
  ), {
    previous: null,
    next: { cardUid: 'card_2', displayTerm: 'supposed' },
    hasPrevious: false,
    hasNext: true,
    position: 1,
    total: 5,
  })
  assert.equal(resolveVocabularyCardSequence(page(1, [card('card_1', 'hello')]), 'card_missing'), null)
  assert.deepEqual(resolveVocabularyCardSequence(page(3, [card('card_5', 'scrutinize')]), 'card_5'), {
    previous: null,
    next: null,
    hasPrevious: true,
    hasNext: false,
    position: 5,
    total: 5,
  })
})
