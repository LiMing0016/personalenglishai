const TOKEN_KEY = 'auth_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
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
