/**
 * 统一 API 客户端（基础设施层）
 * - 所有前端 API 请求统一通过该实例发出
 * - baseURL 使用相对路径 /api，由前端代理或 nginx 转发
 * - 401：清 token 并跳登录（携带 redirect）
 * - 403：仅提示无权限
 */
import axios from 'axios'
import { getToken, clearToken } from '@/utils/token'
import { showToast } from '@/utils/toast'

const BASE_URL = '/api'

export const http = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err.response?.status
    if (status === 401) {
      showToast('请先登录', 'error')
      clearToken()
      const loginPath = '/login'
      const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
      if (!window.location.pathname.endsWith(loginPath)) {
        const redirect = currentPath && currentPath !== loginPath ? `?redirect=${encodeURIComponent(currentPath)}` : ''
        window.location.href = `${loginPath}${redirect}`
      }
    } else if (status === 403) {
      showToast('无权限访问', 'error')
    }
    return Promise.reject(err)
  }
)
