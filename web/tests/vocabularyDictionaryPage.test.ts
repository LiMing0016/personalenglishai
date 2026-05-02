import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const pageSource = readFileSync(
  new URL('../src/views/VocabularyView.vue', import.meta.url),
  'utf8',
)

const apiSource = readFileSync(
  new URL('../src/api/dictionary.ts', import.meta.url),
  'utf8',
)

assert.ok(apiSource.includes("from './http'"), 'dictionary API should use the shared http client')
assert.ok(apiSource.includes('/dictionary/lookup'), 'dictionary API should call the backend lookup endpoint')
assert.ok(apiSource.includes('DictionaryLookupResponse'), 'dictionary API should expose a typed response')

assert.ok(pageSource.includes('lookupDictionary'), 'vocabulary page should call the dictionary API wrapper')
assert.ok(pageSource.includes('en-gb'), 'vocabulary page should support British English lookup')
assert.ok(pageSource.includes('en-us'), 'vocabulary page should support American English lookup')
assert.ok(pageSource.includes('展开更多'), 'vocabulary page should allow expanding longer entries')
assert.ok(pageSource.includes('收起'), 'vocabulary page should allow collapsing expanded entries')
assert.ok(pageSource.includes('Oxford Dictionaries'), 'vocabulary page should show the result source')
assert.ok(pageSource.includes('import.meta.env.DEV'), 'vocabulary page should show debug details only in development')
assert.ok(!pageSource.includes('认识'), 'first version should not include learning action buttons')
assert.ok(!pageSource.includes('模糊'), 'first version should not include learning action buttons')
assert.ok(!pageSource.includes('不认识'), 'first version should not include learning action buttons')

console.log('vocabulary-dictionary-page-ok')
