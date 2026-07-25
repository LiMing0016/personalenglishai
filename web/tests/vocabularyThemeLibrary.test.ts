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
  for (const action of ['编辑', '复制', '设为默认', '停用', '删除']) {
    assert.ok(librarySource.includes(action), `theme library should include ${action}`)
  }
  assert.match(librarySource, /v-if="theme\.ownerType === 'user'"/)
  assert.match(librarySource, /历史卡片仍会保留/)
  assert.match(librarySource, /deleteMutation\.mutateAsync/)
  assert.match(librarySource, /:disabled="isThemeActionPending"/)
  assert.match(librarySource, /title="编辑"/)
  assert.match(librarySource, /aria-label="编辑"/)
  for (const label of ['复制', '设为默认', '停用', '删除']) {
    assert.match(librarySource, new RegExp(`title="${label}"`))
    assert.match(librarySource, new RegExp(`aria-label="${label}"`))
  }
})

test('does not expose a cross-page use action that the vocabulary view cannot consume', () => {
  assert.doesNotMatch(librarySource, />使用<\/button>/)
  assert.doesNotMatch(librarySource, /useRouter/)
  assert.doesNotMatch(librarySource, /function useTheme/)
  assert.doesNotMatch(librarySource, /theme-card__use/)
  assert.doesNotMatch(librarySource, /query:\s*{[^}]*themeUid/)
})

test('uses one global pending lock for every theme operation', () => {
  assert.match(librarySource, /const isThemeActionPending = computed\(\(\) => Boolean\(pendingThemeUid\.value\)\)/)
  assert.match(librarySource, /:aria-busy="isThemeActionPending"/)
  assert.ok(
    (librarySource.match(/:disabled="isThemeActionPending/g) ?? []).length >= 6,
    'every theme action should use the global pending lock',
  )
  assert.ok(librarySource.includes('处理中'), 'theme actions should announce pending work')
  assert.match(librarySource, /width:\s*36px/)
  assert.match(librarySource, /height:\s*36px/)
  assert.match(librarySource, /border-radius:\s*8px/)
})

test('traps focus in the theme form dialog and restores the opening trigger', () => {
  assert.match(dialogSource, /ref="dialogRef"/)
  assert.match(dialogSource, /ref="nameInputRef"/)
  assert.match(dialogSource, /@keydown="handleDialogKeydown"/)
  assert.match(dialogSource, /document\.activeElement/)
  assert.match(dialogSource, /await nextTick\(\)/)
  assert.match(dialogSource, /nameInputRef\.value\?\.focus\(\)/)
  assert.match(dialogSource, /event\.key === 'Tab'/)
  assert.match(dialogSource, /event\.shiftKey/)
  assert.match(dialogSource, /event\.key === 'Escape'/)
  assert.match(dialogSource, /if \(pending\.value\) return/)
  assert.match(dialogSource, /previouslyFocusedElement\.value\?\.isConnected/)
  assert.match(dialogSource, /previouslyFocusedElement\.value\.focus\(\)/)
})

test('traps focus in delete confirmation, guards pending escape, and restores focus', () => {
  assert.match(librarySource, /ref="deleteDialogRef"/)
  assert.match(librarySource, /ref="deleteCancelButtonRef"/)
  assert.match(librarySource, /@keydown="handleDeleteDialogKeydown"/)
  assert.match(librarySource, /aria-describedby="delete-theme-description"/)
  assert.match(librarySource, /document\.activeElement/)
  assert.match(librarySource, /deleteCancelButtonRef\.value\?\.focus\(\)/)
  assert.match(librarySource, /event\.key === 'Tab'/)
  assert.match(librarySource, /event\.shiftKey/)
  assert.match(librarySource, /event\.key === 'Escape'/)
  assert.match(librarySource, /deleteMutation\.isPending\.value \|\| isThemeActionPending\.value/)
  assert.match(librarySource, /pendingThemeUid\.value = ''\s+await dismissDeleteDialog\(\)/)
  assert.match(librarySource, /deleteTriggerElement\.value\?\.isConnected/)
  assert.match(librarySource, /deleteTriggerElement\.value\.focus\(\)/)
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
