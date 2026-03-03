import { http } from './http'

export interface MeProfile {
  userId?: number
  email?: string
  nickname?: string
  studyStage?: string | null
  aiMode?: number | null
  emailVerified?: boolean
  phone?: string | null
  phoneVerified?: boolean
  avatarUrl?: string | null
  registerSource?: string | null
  createdAt?: string | null
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
  },

  async updateStudyStage(studyStage: string): Promise<void> {
    await http.patch('/users/me/profile/stage', { studyStage })
  },

  async updateNickname(nickname: string): Promise<void> {
    await http.patch('/users/me/profile/nickname', { nickname })
  },

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await http.post('/users/me/password', { currentPassword, newPassword })
  },
}
