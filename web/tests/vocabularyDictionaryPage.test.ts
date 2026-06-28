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
assert.ok(pageSource.includes("parseVocabularyView(route.query.tab) ?? 'search'"), 'vocabulary page should default to search')
assert.ok(pageSource.includes("key: 'search'"), 'vocabulary page should define a search view')
assert.ok(pageSource.includes("key: 'modes'"), 'vocabulary page should define a study modes view')
assert.ok(pageSource.includes("key: 'collection'"), 'vocabulary page should define a collection view')
assert.ok(pageSource.includes("key: 'stats'"), 'vocabulary page should define a statistics view')
assert.ok(pageSource.includes('展开更多'), 'vocabulary page should allow expanding longer entries')
assert.ok(pageSource.includes('收起'), 'vocabulary page should allow collapsing expanded entries')
assert.ok(pageSource.includes('Oxford Dictionaries'), 'vocabulary page should show the result source')
assert.ok(pageSource.includes('import.meta.env.DEV'), 'vocabulary page should show debug details only in development')
assert.ok(pageSource.includes('DictionaryDetail'), 'vocabulary page should render a dictionary detail component')
assert.ok(pageSource.includes('dictionary-detail-card'), 'vocabulary page should present lookup results as a full-width dictionary card')
assert.ok(pageSource.includes('result.entries'), 'dictionary detail should render definitions from lookup entries')
assert.ok(pageSource.includes('props.result?.phonetics'), 'dictionary detail should render phonetics from lookup results')
assert.ok(pageSource.includes('lookupResultWord'), 'dictionary detail should prefer the searched dictionary word')
assert.ok(pageSource.includes('加入今日复习'), 'vocabulary page should expose learning review actions')
assert.ok(pageSource.includes('标记已掌握'), 'vocabulary page should expose mastery actions')
assert.ok(!pageSource.includes('apiStatusItems'), 'vocabulary page should remove the API status checklist from student UI')

console.log('vocabulary-dictionary-page-ok')
