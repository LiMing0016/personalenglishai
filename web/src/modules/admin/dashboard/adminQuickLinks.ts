export interface AdminDashboardQuickLinkAccess {
  canViewUsers: boolean
  canViewWriting: boolean
  canViewContent: boolean
  canViewAudit: boolean
}

export interface AdminDashboardQuickLink {
  to: string
  label: string
}

export function buildAdminDashboardQuickLinks(access: AdminDashboardQuickLinkAccess): AdminDashboardQuickLink[] {
  const links: AdminDashboardQuickLink[] = [
    { to: '/admin/dashboard', label: 'Dashboard' },
    { to: '/ops/agent/runs', label: 'AI 调试端' },
  ]
  if (access.canViewUsers) links.push({ to: '/admin/users', label: '用户列表' })
  if (access.canViewWriting) links.push({ to: '/admin/essays', label: '作文排查' })
  if (access.canViewContent) links.push({ to: '/admin/prompts', label: '题库管理' })
  if (access.canViewContent) links.push({ to: '/admin/rubrics', label: 'Rubric 管理' })
  if (access.canViewAudit) links.push({ to: '/admin/audit-logs', label: '审计日志' })
  return links
}
