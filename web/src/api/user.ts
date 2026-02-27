import { http } from './http'

export interface MeProfile {
  userId?: number
  email?: string
  nickname?: string
}

export interface MeProfileResponse {
  code?: string
  message?: string
  data?: MeProfile
}

export const userApi = {
  async getMyProfile(): Promise<MeProfileResponse> {
    const res = await http.get<MeProfileResponse>('/users/me/profile')
    return res.data
  }
}
