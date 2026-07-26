import assert from 'node:assert/strict'
import test from 'node:test'

import { http } from '../src/api/http'
import { userApi } from '../src/api/user'

globalThis.localStorage = {
  getItem: () => null,
  setItem: () => undefined,
  removeItem: () => undefined,
  clear: () => undefined,
  key: () => null,
  get length() { return 0 },
} as Storage

const originalAdapter = http.defaults.adapter

test.afterEach(() => {
  http.defaults.adapter = originalAdapter
})

test('posts the normalized image in the multipart file field', async () => {
  const file = new File([new Uint8Array([1, 2, 3])], 'avatar.png', {
    type: 'image/png',
  })

  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'post')
    assert.equal(config.url, '/users/me/profile/avatar')
    assert.ok(config.data instanceof FormData)
    assert.deepEqual([...config.data.keys()], ['file'])
    assert.equal(config.data.get('file'), file)
    assert.notEqual(config.headers.get('Content-Type'), 'multipart/form-data')
    return {
      config,
      data: {
        code: '0',
        message: 'OK',
        data: { avatarUrl: '/uploads/avatars/42/new.png' },
      },
      headers: {},
      status: 200,
      statusText: 'OK',
    }
  }

  const response = await userApi.uploadAvatar(file)

  assert.equal(response.data?.avatarUrl, '/uploads/avatars/42/new.png')
})
