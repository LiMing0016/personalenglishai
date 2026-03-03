/**
 * 统一 API 客户端（基础设施层）
 * - 所有前端 API 请求统一通过该实例发出
 * - baseURL 使用相对路径 /api，由前端代理或 nginx 转发
 * - 401：先尝试用 refresh token 静默续签，失败才跳登录
 * - 403：仅提示无权限
 */
import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { getToken, setToken, clearToken } from '@/utils/token'
import { clearStageCache } from '@/stores/stageCache'
import { showToast } from '@/utils/toast'

const BASE_URL = '/api'

export const http = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ---- 静默续签逻辑 ----

let isRefreshing = false
let pendingRequests: Array<{
  resolve: (token: string) => void
  reject: (err: unknown) => void
}> = []

function onRefreshSuccess(newToken: string) {
  pendingRequests.forEach(({ resolve }) => resolve(newToken))
  pendingRequests = []
}

function onRefreshFailure(err: unknown) {
  pendingRequests.forEach(({ reject }) => reject(err))
  pendingRequests = []
}

function redirectToLogin() {
  showToast('请先登录', 'error')
  clearToken()
  clearStageCache()
  const loginPath = '/login'
  const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
  if (!window.location.pathname.endsWith(loginPath)) {
    const redirect =
      currentPath && currentPath !== loginPath
        ? `?redirect=${encodeURIComponent(currentPath)}`
        : ''
    window.location.href = `${loginPath}${redirect}`
  }
}

http.interceptors.response.use(
  (res) => res,
  async (err: AxiosError) => {
    const status = err.response?.status
    const originalConfig = err.config as InternalAxiosRequestConfig & { _retried?: boolean }

    // 401 且不是 refresh 请求本身 → 尝试静默续签
    if (status === 401 && originalConfig && !originalConfig._retried) {
      // 如果是 refresh 请求失败，直接跳登录
      if (originalConfig.url?.includes('/v1/auth/refresh')) {
        redirectToLogin()
        return Promise.reject(err)
      }

      if (isRefreshing) {
        // 已有续签在进行中，排队等待
        return new Promise((resolve, reject) => {
          pendingRequests.push({
            resolve: (token: string) => {
              originalConfig.headers.Authorization = `Bearer ${token}`
              originalConfig._retried = true
              resolve(http(originalConfig))
            },
            reject,
          })
        })
      }

      isRefreshing = true
      originalConfig._retried = true

      try {
        const res = await http.post('/v1/auth/refresh', null, { withCredentials: true })
        const body = res.data as { data?: { token?: string }; token?: string }
        const newToken = body.data?.token ?? body.token ?? ''

        if (!newToken) {
          throw new Error('no token in refresh response')
        }

        setToken(newToken)
        onRefreshSuccess(newToken)

        // 重放原始请求
        originalConfig.headers.Authorization = `Bearer ${newToken}`
        return http(originalConfig)
      } catch (refreshErr) {
        onRefreshFailure(refreshErr)
        redirectToLogin()
        return Promise.reject(refreshErr)
      } finally {
        isRefreshing = false
      }
    }

    if (status === 403) {
      showToast('无权限访问', 'error')
    }

    return Promise.reject(err)
  },
)
