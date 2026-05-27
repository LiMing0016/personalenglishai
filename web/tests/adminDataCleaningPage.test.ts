import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../src/pages/admin/AdminDataCleaningPage.vue', import.meta.url), 'utf8')

test('admin data cleaning page exposes the staged cleaning tabs', () => {
  for (const label of ['词典源', '词条清洗', '例句清洗', '短语搭配', '入库审核', '任务记录']) {
    assert.match(source, new RegExp(label))
  }
  assert.match(source, /activeTab/)
  assert.match(source, /data-cleaning-tabs/)
})

test('admin data cleaning page keeps dictionary source as the default workspace', () => {
  assert.match(source, /key:\s*'dictionary'/)
  assert.match(source, /ref(?:<[^>]+>)?\(['"]dictionary['"]\)/)
})

test('admin data cleaning page exposes dictionary upload controls', () => {
  assert.match(source, /上传词典包/)
  assert.match(source, /type="file"/)
  assert.match(source, /uploadDictionaryPackage/)
  assert.match(source, /uploadDictionaryDataCleaningSource/)
})

test('admin data cleaning page shows upload feedback inside the upload panel', () => {
  assert.match(source, /uploadStatusMessage/)
  assert.match(source, /data-cleaning-upload-feedback/)
  assert.match(source, /正在上传并探查/)
})

test('admin data cleaning page refreshes source list after upload failure while keeping feedback', () => {
  assert.match(source, /loadAll\(\{\s*clearError:\s*false\s*\}\)/)
  assert.match(source, /上传失败后已刷新下方数据源列表/)
})

test('admin data cleaning page tells whether duplicate source exists in refreshed list', () => {
  assert.match(source, /findSourceByCode/)
  assert.match(source, /已在下方数据源列表中找到/)
  assert.match(source, /刷新后仍未在下方列表中找到/)
})

test('admin data cleaning page derives source code from selected dictionary package', () => {
  assert.match(source, /applyUploadFilePreset/)
  assert.match(source, /oxfordPrimary/)
  assert.match(source, /词库标识/)
  assert.match(source, /已根据上传文件识别词库标识/)
})

test('admin data cleaning page treats server paths as optional upload alternatives', () => {
  assert.match(source, /服务器 MDX 路径（可选）/)
  assert.match(source, /上传词典包时无需填写/)
  assert.doesNotMatch(source, /D:\\\\dictionary\\\\oald9/)
})

test('admin data cleaning page displays installed dictionary libraries', () => {
  assert.match(source, /已安装词库/)
  assert.match(source, /dictionaryLibraries/)
  assert.match(source, /listAdminDictionaries/)
  assert.match(source, /词条数/)
  assert.match(source, /MDD 资源/)
  assert.match(source, /开始正文入库/)
  assert.match(source, /createAdminDictionaryImportJob/)
})

test('admin data cleaning page displays import progress, samples and failures', () => {
  assert.match(source, /dictionaryImportJobs/)
  assert.match(source, /dictionaryEntrySamples/)
  assert.match(source, /dictionaryImportFailures/)
  assert.match(source, /listAdminDictionaryEntrySamples/)
  assert.match(source, /listAdminDictionaryImportFailures/)
  assert.match(source, /最近入库/)
  assert.match(source, /失败样例/)
  assert.match(source, /正文入库任务已/)
})
