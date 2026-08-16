import assert from 'node:assert/strict'
import test from 'node:test'

import { resolveAppRailCollapsed } from './appRailResponsive.ts'

test('窄屏个人中心默认收起全局导航', () => {
  assert.equal(
    resolveAppRailCollapsed({
      routePath: '/app/me',
      narrowViewport: true,
      personalCenterExpanded: false,
      storedCollapsed: false,
    }),
    true,
  )
})

test('窄屏个人中心允许用户临时展开导航', () => {
  assert.equal(
    resolveAppRailCollapsed({
      routePath: '/app/me',
      narrowViewport: true,
      personalCenterExpanded: true,
      storedCollapsed: false,
    }),
    false,
  )
})

test('桌面个人中心和其他页面继续使用原偏好', () => {
  assert.equal(
    resolveAppRailCollapsed({
      routePath: '/app/me',
      narrowViewport: false,
      personalCenterExpanded: false,
      storedCollapsed: false,
    }),
    false,
  )
  assert.equal(
    resolveAppRailCollapsed({
      routePath: '/app/writing',
      narrowViewport: true,
      personalCenterExpanded: false,
      storedCollapsed: true,
    }),
    true,
  )
})
