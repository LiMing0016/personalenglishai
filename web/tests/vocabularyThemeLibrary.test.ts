import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import test from 'node:test'

function readSource(path: string) {
  const url = new URL(path, import.meta.url)
  return existsSync(url) ? readFileSync(url, 'utf8') : ''
}

const routerSource = readSource('../src/router/index.ts')
const pageSource = readSource('../src/pages/app/VocabularyThemesPage.vue')
const librarySource = readSource('../src/components/vocabulary/VocabularyThemeLibrary.vue')
const dialogSource = readSource('../src/components/vocabulary/VocabularyThemeDialog.vue')

test('registers the standalone theme library route and composes the theme query layer', () => {
  assert.match(routerSource, /path:\s*['"]vocabulary\/themes['"]/)
  assert.match(routerSource, /VocabularyThemesPage\.vue/)
  assert.match(pageSource, /useVocabularyThemes/)
  assert.match(pageSource, /VocabularyThemeLibrary/)
})

test('exposes scannable system and user sections with complete async states', () => {
  for (const label of [
    '系统主题',
    '我的主题',
    '新建主题',
    '加载主题中',
    '主题加载失败',
    '还没有自定义主题',
  ]) {
    assert.ok(librarySource.includes(label), `theme library should include ${label}`)
  }
  assert.match(librarySource, /type="search"/)
  assert.match(librarySource, /themesQuery\.isLoading\.value/)
  assert.match(librarySource, /themesQuery\.isError\.value/)
  assert.match(librarySource, /themesQuery\.refetch/)
})

test('keeps protected actions off system themes and confirms user-theme deletion semantics', () => {
  for (const action of ['使用', '编辑', '复制', '设为默认', '停用', '删除']) {
    assert.ok(librarySource.includes(action), `theme library should include ${action}`)
  }
  assert.match(librarySource, /v-if="theme\.ownerType === 'user'"/)
  assert.match(librarySource, /历史卡片仍会保留/)
  assert.match(librarySource, /deleteMutation\.mutateAsync/)
  assert.match(librarySource, /:disabled="isThemePending\(theme\.themeUid\)"/)
  assert.match(librarySource, /title="编辑"/)
  assert.match(librarySource, /aria-label="编辑"/)
  for (const label of ['复制', '设为默认', '停用', '删除']) {
    assert.match(librarySource, new RegExp(`title="${label}"`))
    assert.match(librarySource, new RegExp(`aria-label="${label}"`))
  }
})

test('exposes a perceivable pending state without resizing compact action controls', () => {
  assert.match(librarySource, /:aria-busy="isThemePending\(theme\.themeUid\)"/)
  assert.ok(librarySource.includes('处理中'), 'theme actions should announce pending work')
  assert.match(librarySource, /width:\s*36px/)
  assert.match(librarySource, /height:\s*36px/)
  assert.match(librarySource, /border-radius:\s*8px/)
})

test('validates the minimal dialog and closes it only after a successful mutation', () => {
  assert.match(dialogSource, /maxlength="80"/)
  assert.match(dialogSource, /maxlength="1000"/)
  assert.match(dialogSource, /主题名称不能为空/)
  assert.match(dialogSource, /用途说明不能为空/)
  assert.match(dialogSource, /:disabled="pending"/)
  assert.match(dialogSource, /computed\(\(\) => props\.mutation\.isPending\.value\)/)
  assert.match(dialogSource, /await props\.mutation\.mutateAsync/)
  assert.match(dialogSource, /emit\('close'\)/)
  assert.match(dialogSource, /catch \(error\)/)
})
