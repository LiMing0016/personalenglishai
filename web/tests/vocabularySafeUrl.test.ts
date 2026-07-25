import assert from 'node:assert/strict'
import test from 'node:test'

import { safeExternalUrl } from '../src/features/vocabulary/safeExternalUrl'

test('allows only absolute http and https source URLs', () => {
  assert.equal(safeExternalUrl('https://example.com/source'), 'https://example.com/source')
  assert.equal(safeExternalUrl('http://example.com/source'), 'http://example.com/source')
  assert.equal(safeExternalUrl('javascript:alert(1)'), null)
  assert.equal(safeExternalUrl('data:text/html,unsafe'), null)
  assert.equal(safeExternalUrl('/relative/source'), null)
  assert.equal(safeExternalUrl('not a url'), null)
  assert.equal(safeExternalUrl(null), null)
})
