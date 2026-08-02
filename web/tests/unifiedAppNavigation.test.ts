import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const { APP_NAV_ITEMS, isAppRouteActive } = await import('../src/components/appNavigation.ts')
const menuSource = readFileSync(new URL('../src/components/AppNavigationMenu.vue', import.meta.url), 'utf8')

assert.deepEqual(APP_NAV_ITEMS.map((item) => item.label), [
  '学习助手',
  '写作',
  '翻译',
  '单词',
  '听力',
  '口语',
])
assert.equal(isAppRouteActive('/app/writing/editor', '/app/writing'), true)
assert.equal(isAppRouteActive('/app/assistant', '/app/writing'), false)
assert.ok(menuSource.includes('APP_NAV_ITEMS'))
assert.ok(menuSource.includes('AppNavigationIcon'))
assert.ok(menuSource.includes('应用导航'))
assert.ok(!menuSource.includes('首页'))
assert.ok(!menuSource.includes('>‹<'), 'collapse affordance should reuse an existing icon instead of a text glyph')

console.log('unified-app-navigation-ok')
