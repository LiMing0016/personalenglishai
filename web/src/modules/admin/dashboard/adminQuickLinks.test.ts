import test from 'node:test'
import assert from 'node:assert/strict'

import { buildAdminDashboardQuickLinks } from './adminQuickLinks.ts'

test('buildAdminDashboardQuickLinks always exposes the AI debug console', () => {
  const links = buildAdminDashboardQuickLinks({
    canViewUsers: false,
    canViewWriting: false,
    canViewContent: false,
    canViewAudit: false,
  })

  assert.deepEqual(
    links.map((link) => link.to),
    ['/admin/dashboard', '/ops/agent/runs'],
  )
})
