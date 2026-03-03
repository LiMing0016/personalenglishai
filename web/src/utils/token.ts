/**
 * Token 管理
 *
 * Access token 存内存变量（防 XSS），页面刷新时通过 refresh cookie 重新获取。
 * Refresh token 由后端设置 httpOnly cookie，前端无法读取。
 */

let accessToken: string | null = null

const TOKEN_KEY = 'auth_token'

export function getToken(): string | null {
  // 优先取内存，兜底取 localStorage（兼容旧 session）
  if (accessToken) return accessToken
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  accessToken = token
  // 同步写 localStorage，保证页面刷新前仍有 token 可用（刷新后会通过 refresh 续签）
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  accessToken = null
  localStorage.removeItem(TOKEN_KEY)
}

function decodeBase64Url(input: string): string | null {
  try {
    const normalized = input.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4)
    return atob(padded)
  } catch {
    return null
  }
}

export function getTokenExpiryMs(token: string | null): number | null {
  if (!token) return null
  const parts = token.split('.')
  if (parts.length !== 3) return null
  const payloadRaw = decodeBase64Url(parts[1])
  if (!payloadRaw) return null
  try {
    const payload = JSON.parse(payloadRaw) as { exp?: number }
    if (typeof payload.exp !== 'number') return null
    return payload.exp * 1000
  } catch {
    return null
  }
}

export function isTokenExpired(token: string | null, nowMs = Date.now()): boolean {
  const expiryMs = getTokenExpiryMs(token)
  if (expiryMs == null) return false
  return expiryMs <= nowMs
}
