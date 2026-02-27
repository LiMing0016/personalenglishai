/**
 * 统一 API 响应体（与后端 ApiResponse 一致）
 */
export interface ApiResponse<T> {
  code: string
  message: string
  data?: T
  traceId?: string
}

export interface LoginData {
  token: string
  tokenType?: string
  expiresIn?: number
}

export interface RegisterData {
  userId: number
}

/** 登录接口 HTTP 响应体 */
export type LoginResponseBody = ApiResponse<LoginData> & {
  token?: string
  accessToken?: string
}

/** 注册接口 HTTP 响应体 */
export type RegisterResponseBody = ApiResponse<RegisterData>

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  nickname: string
}
