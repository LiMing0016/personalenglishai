/**
 * 认证相关扩展 API（邮箱验证）
 * 登录/注册请使用 @/api/auth 的 authApi
 */
import { http } from './http'

export interface ResendVerificationResponse {
  code: string
  message: string
  success: boolean
}

export interface VerifyEmailResponse {
  code: string
  message: string
  data?: { status: 'VERIFIED' | 'EXPIRED' | 'INVALID' }
  success: boolean
  status?: 'VERIFIED' | 'EXPIRED' | 'INVALID'
}

export const authApi = {
  async resendVerification(email?: string): Promise<ResendVerificationResponse> {
    const res = await http.post<ResendVerificationResponse>('/v1/auth/resend-verification', { email })
    return { ...res.data, success: res.data.code === '0' }
  },

  async verifyEmail(token: string): Promise<VerifyEmailResponse> {
    const res = await http.get<VerifyEmailResponse>(
      `/v1/auth/verify-email?token=${encodeURIComponent(token)}`,
    )
    const status = res.data.data?.status ?? res.data.status
    return { ...res.data, success: res.data.code === '0', status }
  },
}
