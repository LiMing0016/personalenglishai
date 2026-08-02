import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const pageSource = readFileSync(
  new URL('../src/pages/app/PersonalCenterPage.vue', import.meta.url),
  'utf8',
)

assert.ok(
  !pageSource.includes('<p class="profile-eyebrow">个人中心</p>'),
  'profile identity should not repeat the personal center page label',
)

console.log('personal-center-chrome-ok')
