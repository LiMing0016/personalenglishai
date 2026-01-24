const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export interface ApiResponse<T = any> {
  success: boolean
  data?: T
  code?: string
  message?: string
  user?: {
    email: string
    emailVerified: boolean
  }
  needEmailVerify?: boolean
  status?: 'VERIFIED' | 'EXPIRED' | 'INVALID'
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  headers?: Record<string, string>
  body?: any
  timeout?: number
}

class HttpClient {
  private baseURL: string
  private defaultTimeout: number = 10000

  constructor(baseURL: string) {
    this.baseURL = baseURL
  }

  private async request<T>(
    endpoint: string,
    options: RequestOptions = {}
  ): Promise<ApiResponse<T>> {
    const { method = 'GET', headers = {}, body, timeout = this.defaultTimeout } = options

    const url = `${this.baseURL}${endpoint}`
    const requestHeaders: HeadersInit = {
      'Content-Type': 'application/json',
      ...headers
    }

    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), timeout)

    try {
      const response = await fetch(url, {
        method,
        headers: requestHeaders,
        body: body ? JSON.stringify(body) : undefined,
        signal: controller.signal
      })

      clearTimeout(timeoutId)

      const data: ApiResponse<T> = await response.json()

      // 如果 HTTP 状态码不成功，返回错误信息
      if (!response.ok) {
        return {
          success: false,
          code: data.code || 'REQUEST_FAILED',
          message: data.message || '请求失败',
          ...data
        }
      }

      // 返回数据（可能包含 success 字段）
      return data
    } catch (error: any) {
      clearTimeout(timeoutId)

      if (error.name === 'AbortError') {
        return {
          success: false,
          code: 'TIMEOUT',
          message: '请求超时，请稍后重试'
        }
      }

      if (error instanceof TypeError && error.message.includes('fetch')) {
        return {
          success: false,
          code: 'NETWORK_ERROR',
          message: '网络错误，请检查网络连接'
        }
      }

      return {
        success: false,
        code: 'UNKNOWN_ERROR',
        message: '未知错误，请稍后重试'
      }
    }
  }

  async get<T>(endpoint: string, options?: Omit<RequestOptions, 'method' | 'body'>): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, { ...options, method: 'GET' })
  }

  async post<T>(endpoint: string, body?: any, options?: Omit<RequestOptions, 'method' | 'body'>): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, { ...options, method: 'POST', body })
  }
}

export const http = new HttpClient(API_BASE_URL)

