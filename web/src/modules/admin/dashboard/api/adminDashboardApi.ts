import type { AdminDashboardFilter, AdminDashboardPayload } from '../types'
import { getDashboardMock } from '../mocks/adminDashboardMock'

export const adminDashboardApi = {
  async getDashboard(filters: AdminDashboardFilter): Promise<AdminDashboardPayload> {
    return getDashboardMock(filters)
  },
}
