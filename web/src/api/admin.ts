import { http } from './http'

export interface AdminMe {
  userId?: number
  email?: string
  nickname?: string
  permissions?: string[]
}

interface AdminMeResponse {
  code?: string
  message?: string
  data?: AdminMe
}

let adminMeCache: Promise<AdminMe> | null = null

export function clearAdminMeCache() {
  adminMeCache = null
}

export async function getAdminMe(): Promise<AdminMe> {
  if (!adminMeCache) {
    adminMeCache = http
      .get<AdminMeResponse>('/admin/me')
      .then((res) => res.data?.data ?? {})
      .catch((err) => {
        adminMeCache = null
        throw err
      })
  }
  return adminMeCache
}
