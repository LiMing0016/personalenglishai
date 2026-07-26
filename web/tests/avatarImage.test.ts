import assert from 'node:assert/strict'
import test from 'node:test'

import {
  AVATAR_MAX_SOURCE_BYTES,
  normalizeAvatarFile,
  resolveAvatarOutputSize,
  validateAvatarFile,
  type AvatarImageAdapter,
} from '../src/components/personal-center/avatarImage'

test('accepts JPEG, PNG and WebP source files up to five MiB', () => {
  for (const type of ['image/jpeg', 'image/png', 'image/webp']) {
    const file = new File([new Uint8Array(AVATAR_MAX_SOURCE_BYTES)], 'avatar', { type })
    assert.equal(validateAvatarFile(file), null)
  }
})

test('returns specific validation messages for unsupported type and oversized source', () => {
  const gif = new File([new Uint8Array([1])], 'avatar.gif', { type: 'image/gif' })
  const oversized = new File(
    [new Uint8Array(AVATAR_MAX_SOURCE_BYTES + 1)],
    'avatar.png',
    { type: 'image/png' },
  )

  assert.equal(validateAvatarFile(gif), '请选择 JPG、PNG 或 WebP 图片')
  assert.equal(validateAvatarFile(oversized), '头像不能超过 5MB')
})

test('calculates proportional output dimensions without upscaling', () => {
  assert.deepEqual(resolveAvatarOutputSize(2048, 1024), { width: 1024, height: 512 })
  assert.deepEqual(resolveAvatarOutputSize(900, 1800), { width: 512, height: 1024 })
  assert.deepEqual(resolveAvatarOutputSize(320, 240), { width: 320, height: 240 })
})

test('normalizes a WebP source into a PNG File and releases decoded resources', async () => {
  const events: string[] = []
  const adapter: AvatarImageAdapter = {
    async decode() {
      events.push('decode')
      return {
        source: {} as CanvasImageSource,
        width: 2048,
        height: 1024,
        release: () => events.push('release'),
      }
    },
    async encodePng(_source, width, height) {
      events.push(`encode:${width}x${height}`)
      return new Blob([new Uint8Array([1, 2, 3])], { type: 'image/png' })
    },
  }
  const source = new File([new Uint8Array([7])], 'portrait.webp', { type: 'image/webp' })

  const normalized = await normalizeAvatarFile(source, adapter)

  assert.equal(normalized.name, 'avatar.png')
  assert.equal(normalized.type, 'image/png')
  assert.equal(normalized.size, 3)
  assert.deepEqual(events, ['decode', 'encode:1024x512', 'release'])
})

test('releases decoded resources and surfaces a stable error when PNG encoding fails', async () => {
  let released = false
  const adapter: AvatarImageAdapter = {
    async decode() {
      return {
        source: {} as CanvasImageSource,
        width: 100,
        height: 100,
        release: () => {
          released = true
        },
      }
    },
    async encodePng() {
      throw new Error('canvas failed')
    },
  }
  const source = new File([new Uint8Array([7])], 'portrait.jpg', { type: 'image/jpeg' })

  await assert.rejects(
    () => normalizeAvatarFile(source, adapter),
    /图片处理失败，请重新选择/,
  )
  assert.equal(released, true)
})

test('rejects a normalized PNG that still exceeds five MiB', async () => {
  const adapter: AvatarImageAdapter = {
    async decode() {
      return {
        source: {} as CanvasImageSource,
        width: 100,
        height: 100,
        release: () => undefined,
      }
    },
    async encodePng() {
      return new Blob(
        [new Uint8Array(AVATAR_MAX_SOURCE_BYTES + 1)],
        { type: 'image/png' },
      )
    },
  }
  const source = new File([new Uint8Array([7])], 'portrait.png', { type: 'image/png' })

  await assert.rejects(() => normalizeAvatarFile(source, adapter), /头像不能超过 5MB/)
})
