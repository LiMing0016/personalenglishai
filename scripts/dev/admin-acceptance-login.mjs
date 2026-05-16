#!/usr/bin/env node
import { pathToFileURL } from 'node:url'

const DEFAULT_EMAIL = 'admin01@admin.com'
const DEFAULT_PASSWORD = 'Kiss497.*'
const DEFAULT_WEB_ORIGIN = 'http://127.0.0.1:5173'

function normalizeBaseUrl(value, fallback) {
  const raw = (value || fallback).replace(/\/+$/, '')
  return raw.endsWith('/api') ? raw : `${raw}/api`
}

function normalizeOrigin(value) {
  return new URL(value || DEFAULT_WEB_ORIGIN).origin
}

async function readJsonResponse(response, label) {
  const text = await response.text()
  let body = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      throw new Error(`${label} returned non-JSON response (${response.status})`)
    }
  }
  if (!response.ok) {
    const message = body?.message || body?.error || response.statusText
    throw new Error(`${label} failed (${response.status}): ${message}`)
  }
  return body || {}
}

function extractToken(body) {
  return body?.data?.token || body?.token || body?.accessToken || null
}

export async function runAdminAcceptanceLogin(options = {}) {
  const webOrigin = normalizeOrigin(options.webOrigin || process.env.PEAI_WEB_ORIGIN)
  const apiBase = normalizeBaseUrl(options.apiBase || process.env.PEAI_API_BASE, `${webOrigin}/api`)
  const email = options.email || process.env.PEAI_ADMIN_EMAIL || DEFAULT_EMAIL
  const password = options.password || process.env.PEAI_ADMIN_PASSWORD || DEFAULT_PASSWORD
  const targetPath = options.targetPath || process.env.PEAI_ADMIN_TARGET_PATH || '/admin/users'

  const loginResponse = await fetch(`${apiBase}/v1/auth/dev-login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  const loginBody = await readJsonResponse(loginResponse, 'Dev admin login')
  const token = extractToken(loginBody)
  if (!token) {
    throw new Error('Dev admin login did not return an access token')
  }

  const meResponse = await fetch(`${apiBase}/admin/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  const admin = await readJsonResponse(meResponse, 'Admin verification')

  return {
    webOrigin,
    apiBase,
    targetUrl: new URL(targetPath, webOrigin).toString(),
    bridgeUrl: new URL(
      `/dev/admin-login#token=${encodeURIComponent(token)}&target=${encodeURIComponent(targetPath)}`,
      webOrigin,
    ).toString(),
    localStorageKey: 'auth_token',
    token,
    admin,
  }
}

function parseArgs(argv) {
  const options = {}
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i]
    const next = argv[i + 1]
    if (arg === '--api-base') {
      options.apiBase = next
      i += 1
    } else if (arg === '--web-origin') {
      options.webOrigin = next
      i += 1
    } else if (arg === '--email') {
      options.email = next
      i += 1
    } else if (arg === '--password') {
      options.password = next
      i += 1
    } else if (arg === '--target-path') {
      options.targetPath = next
      i += 1
    }
  }
  return options
}

async function main() {
  const result = await runAdminAcceptanceLogin(parseArgs(process.argv.slice(2)))
  console.log(JSON.stringify(result, null, 2))
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((err) => {
    console.error(`[admin-acceptance-login] ${err.message}`)
    process.exitCode = 1
  })
}
