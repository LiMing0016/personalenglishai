/**
 * 认证相关扩展 API（如邮箱验证）
 * 登录/注册请使用 @/api/auth 的 authApi
 */
import { http } from './http'

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
  async resendVerification(email?: string): Promise<ResendVerificationResponse> {
    const res = await http.post<ResendVerificationResponse>('/auth/resend-verification', { email })
    return res.data
  },

  async verifyEmail(token: string): Promise<VerifyEmailResponse> {
    const res = await http.get<VerifyEmailResponse>(
      `/auth/verify-email?token=${encodeURIComponent(token)}`
    )
    return res.data
  },
}
