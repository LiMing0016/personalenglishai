import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const httpSource = readFileSync(new URL('../src/api/http.ts', import.meta.url), 'utf8')
const adminApiSource = readFileSync(new URL('../src/api/admin.ts', import.meta.url), 'utf8')

test('shared http client removes default json content-type for FormData uploads', () => {
  assert.match(httpSource, /data\s+instanceof\s+FormData/)
  assert.match(httpSource, /Content-Type/)
  assert.match(httpSource, /delete|setContentType/)
})

test('admin dictionary upload sends source metadata and files as form data', () => {
  assert.match(adminApiSource, /new FormData\(\)/)
  assert.match(adminApiSource, /append\(['"]sourceCode['"],\s*payload\.sourceCode\)/)
  assert.match(adminApiSource, /append\(['"]displayName['"],\s*payload\.displayName\)/)
  assert.match(adminApiSource, /append\(['"]files['"],\s*file\)/)
})

test('admin dictionary upload uses an extended timeout for large dictionary packages', () => {
  assert.match(adminApiSource, /dictionaryUploadTimeoutMs\s*=\s*600_000/)
  assert.match(adminApiSource, /dictionary-uploads['"],\s*formData,\s*\{\s*timeout:\s*dictionaryUploadTimeoutMs\s*\}/)
})

test('admin dictionary library APIs are available for installed dictionary list', () => {
  assert.match(adminApiSource, /interface AdminDictionaryLibrary/)
  assert.match(adminApiSource, /listAdminDictionaries/)
  assert.match(adminApiSource, /\/admin\/dictionaries/)
  assert.match(adminApiSource, /getAdminDictionary/)
  assert.match(adminApiSource, /interface AdminDictionaryImportJob/)
  assert.match(adminApiSource, /createAdminDictionaryImportJob/)
  assert.match(adminApiSource, /import-jobs/)
  assert.match(adminApiSource, /interface AdminDictionaryEntrySample/)
  assert.match(adminApiSource, /listAdminDictionaryEntrySamples/)
  assert.match(adminApiSource, /listAdminDictionaryImportFailures/)
  assert.match(adminApiSource, /entries\/samples/)
})
