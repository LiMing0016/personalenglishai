import { http, type ApiResponse } from './http'

export interface RegisterRequest {
  email: string
  password: string
}

export interface RegisterResponse {
  success: boolean
  needEmailVerify?: boolean
  code?: string
  message?: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  success: boolean
  user?: {
    email: string
    emailVerified: boolean
  }
  code?: string
  message?: string
}

export interface ResendVerificationResponse {
  success: boolean
  code?: string
  message?: string
}

export interface VerifyEmailResponse {
  success: boolean
  status?: 'VERIFIED' | 'EXPIRED' | 'INVALID'
  code?: string
  message?: string
}

export const authApi = {
  /**
   * 用户注册
   */
  async register(data: RegisterRequest): Promise<RegisterResponse> {
    const response = await http.post<RegisterResponse>('/api/auth/register', data)
    return response as RegisterResponse
  },

  /**
   * 用户登录
   */
  async login(data: LoginRequest): Promise<LoginResponse> {
    const response = await http.post<LoginResponse>('/api/auth/login', data)
    return response as LoginResponse
  },

  /**
   * 重新发送验证邮件
   */
  async resendVerification(email?: string): Promise<ResendVerificationResponse> {
    const response = await http.post<ResendVerificationResponse>('/api/auth/resend-verification', { email })
    return response as ResendVerificationResponse
  },

  /**
   * 验证邮箱
   */
  async verifyEmail(token: string): Promise<VerifyEmailResponse> {
    const response = await http.get<VerifyEmailResponse>(`/api/auth/verify-email?token=${encodeURIComponent(token)}`)
    return response as VerifyEmailResponse
  }
}

