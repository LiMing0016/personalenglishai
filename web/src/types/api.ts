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
  captchaToken?: string
}

export interface CaptchaData {
  captchaId: string
  bgImage: string
  pieceImage: string
}

export type CaptchaResponseBody = ApiResponse<CaptchaData>

export interface CaptchaVerifyRequest {
  captchaId: string
  x: number
}

export interface CaptchaVerifyData {
  verified: boolean
  captchaToken?: string
}

export type CaptchaVerifyResponseBody = ApiResponse<CaptchaVerifyData>

export interface RegisterRequest {
  email: string
  password: string
  nickname: string
}

export interface SendSmsCodeRequest {
  phone: string
  purpose: 'login' | 'register'
}

export interface PhoneLoginRequest {
  phone: string
  mode: 'otp' | 'password'
  code?: string
  password?: string
}

export interface PhoneRegisterRequest {
  phone: string
  code: string
  nickname: string
}
