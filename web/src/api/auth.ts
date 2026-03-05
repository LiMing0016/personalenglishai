/**
 * 认证服务层（仅负责调用接口并返回后端数据，不处理 token / UI / 路由）
 */
import { http } from './http'
import type {
  LoginRequest,
  RegisterRequest,
  LoginResponseBody,
  RegisterResponseBody,
  SendSmsCodeRequest,
  PhoneLoginRequest,
  PhoneRegisterRequest,
  CaptchaResponseBody,
  CaptchaVerifyRequest,
  CaptchaVerifyResponseBody,
} from '@/types/api'

export type { LoginRequest, RegisterRequest, LoginResponseBody, RegisterResponseBody, PhoneLoginRequest, PhoneRegisterRequest }

export const authApi = {
  /** 获取滑动验证码 */
  async getCaptcha(): Promise<CaptchaResponseBody> {
    const res = await http.get<CaptchaResponseBody>('/v1/auth/captcha')
    return res.data
  },

  /** 验证滑动验证码 */
  async verifyCaptcha(data: CaptchaVerifyRequest): Promise<CaptchaVerifyResponseBody> {
    const res = await http.post<CaptchaVerifyResponseBody>('/v1/auth/captcha/verify', data)
    return res.data
  },

  /** 登录：仅调用接口并返回响应 */
  async login(data: LoginRequest): Promise<LoginResponseBody> {
    const res = await http.post<LoginResponseBody>('/v1/auth/login', data)
    return res.data
  },

  /** 注册：仅调用接口并返回响应 */
  async register(data: RegisterRequest): Promise<RegisterResponseBody> {
    const res = await http.post<RegisterResponseBody>('/v1/auth/register', data)
    return res.data
  },

  /** 刷新 token：refresh token 通过 httpOnly cookie 自动携带 */
  async refresh(): Promise<LoginResponseBody> {
    const res = await http.post<LoginResponseBody>('/v1/auth/refresh', null, {
      withCredentials: true,
    })
    return res.data
  },

  /** 退出登录：清除服务端 refresh cookie */
  async logout(): Promise<void> {
    await http.post('/v1/auth/logout', null, { withCredentials: true })
  },

  /** 发送短信验证码 */
  async sendSmsCode(data: SendSmsCodeRequest): Promise<void> {
    await http.post('/v1/auth/sms/send', data)
  },

  /** 手机登录（OTP 或密码模式） */
  async phoneLogin(data: PhoneLoginRequest): Promise<LoginResponseBody> {
    const res = await http.post<LoginResponseBody>('/v1/auth/phone/login', data)
    return res.data
  },

  /** 手机注册（免密，返回 JWT 自动登录） */
  async phoneRegister(data: PhoneRegisterRequest): Promise<LoginResponseBody> {
    const res = await http.post<LoginResponseBody>('/v1/auth/phone/register', data)
    return res.data
  },
}
