import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { afterEach, test } from 'node:test'

import { runAdminAcceptanceLogin } from './admin-acceptance-login.mjs'

const servers = []

afterEach(async () => {
  await Promise.all(servers.splice(0).map((server) => new Promise((resolve) => server.close(resolve))))
})

function readJson(req) {
  return new Promise((resolve, reject) => {
    let body = ''
    req.setEncoding('utf8')
    req.on('data', (chunk) => {
      body += chunk
    })
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {})
      } catch (err) {
        reject(err)
      }
    })
    req.on('error', reject)
  })
}

async function startServer(handler) {
  const server = createServer(handler)
  servers.push(server)
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const address = server.address()
  return `http://127.0.0.1:${address.port}/api`
}

test('admin acceptance login returns token and verifies admin identity', async () => {
  const seen = []
  const apiBase = await startServer(async (req, res) => {
    seen.push({ method: req.method, url: req.url, authorization: req.headers.authorization })

    if (req.method === 'POST' && req.url === '/api/v1/auth/dev-login') {
      const body = await readJson(req)
      assert.deepEqual(body, {
        email: 'admin01@admin.com',
        password: 'Kiss497.*',
      })
      res.setHeader('Content-Type', 'application/json')
      res.end(JSON.stringify({ data: { token: 'access-token-1' } }))
      return
    }

    if (req.method === 'GET' && req.url === '/api/admin/auth/me') {
      assert.equal(req.headers.authorization, 'Bearer access-token-1')
      res.setHeader('Content-Type', 'application/json')
      res.end(JSON.stringify({
        userId: 1,
        email: 'admin01@admin.com',
        roles: ['super_admin'],
        permissions: ['admin.users.read'],
      }))
      return
    }

    res.statusCode = 404
    res.end()
  })

  const result = await runAdminAcceptanceLogin({
    apiBase,
    webOrigin: 'http://127.0.0.1:5173',
    targetPath: '/admin/users',
  })

  assert.equal(result.token, 'access-token-1')
  assert.equal(result.localStorageKey, 'auth_token')
  assert.equal(result.targetUrl, 'http://127.0.0.1:5173/admin/users')
  assert.equal(
    result.bridgeUrl,
    'http://127.0.0.1:5173/dev/admin-login#token=access-token-1&target=%2Fadmin%2Fusers',
  )
  assert.deepEqual(result.admin.roles, ['super_admin'])
  assert.deepEqual(seen.map((entry) => `${entry.method} ${entry.url}`), [
    'POST /api/v1/auth/dev-login',
    'GET /api/admin/auth/me',
  ])
})

test('admin acceptance login fails when admin verification fails', async () => {
  const apiBase = await startServer(async (req, res) => {
    if (req.method === 'POST' && req.url === '/api/v1/auth/dev-login') {
      res.setHeader('Content-Type', 'application/json')
      res.end(JSON.stringify({ data: { token: 'regular-token' } }))
      return
    }

    if (req.method === 'GET' && req.url === '/api/admin/auth/me') {
      res.statusCode = 403
      res.setHeader('Content-Type', 'application/json')
      res.end(JSON.stringify({ message: 'forbidden' }))
      return
    }

    res.statusCode = 404
    res.end()
  })

  await assert.rejects(
    () => runAdminAcceptanceLogin({
      apiBase,
      webOrigin: 'http://127.0.0.1:5173',
      email: 'user@example.com',
      password: 'Kiss497.*',
    }),
    /Admin verification failed/,
  )
})
