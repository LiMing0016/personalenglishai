/**
 * 认证服务层（仅负责调用接口并返回后端数据，不处理 token / UI / 路由）
 */
import { http } from './http'
import type {
  LoginRequest,
  RegisterRequest,
  LoginResponseBody,
  RegisterResponseBody,
} from '@/types/api'

export type { LoginRequest, RegisterRequest, LoginResponseBody, RegisterResponseBody }

export const authApi = {
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
}
