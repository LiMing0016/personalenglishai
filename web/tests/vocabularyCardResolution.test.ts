import assert from 'node:assert/strict'
import test from 'node:test'

import {
  mapDictionaryLanguageToCardLanguage,
  normalizeVocabularyResolutionTerm,
} from '../src/composables/useVocabularyCardResolution'

test('maps both supported dictionary variants to the persisted English card language', () => {
  assert.equal(mapDictionaryLanguageToCardLanguage('en-gb'), 'en')
  assert.equal(mapDictionaryLanguageToCardLanguage('en-us'), 'en')
})

test('normalizes the resolution cache key without changing the searched word identity', () => {
  assert.equal(normalizeVocabularyResolutionTerm('  Wonder  '), 'wonder')
  assert.equal(normalizeVocabularyResolutionTerm('Ｓｔｕｄｙ  Plan'), 'study plan')
  assert.equal(normalizeVocabularyResolutionTerm(''), '')
})
