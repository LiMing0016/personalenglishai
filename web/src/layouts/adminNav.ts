export type AdminNavStatus = 'implemented' | 'placeholder'

export interface AdminNavItem {
  to: string
  label: string
  permission?: string
  status?: AdminNavStatus
}

export interface AdminNavGroup {
  label: string
  items: AdminNavItem[]
}

export const adminNavGroups: AdminNavGroup[] = [
  {
    label: '总览',
    items: [
      { to: '/admin/dashboard', label: 'Dashboard', status: 'implemented' },
      { to: '/admin/docs', label: '文档首页', status: 'implemented' },
    ],
  },
  {
    label: '用户运营',
    items: [
      { to: '/admin/users', label: '用户', permission: 'admin.users.read', status: 'implemented' },
    ],
  },
  {
    label: '订阅与权益',
    items: [
      { to: '/admin/subscriptions', label: '订阅用户', permission: 'admin.subscription.read', status: 'implemented' },
      { to: '/admin/subscription/redeem-codes', label: '兑换码', permission: 'admin.subscription.write', status: 'placeholder' },
      { to: '/admin/subscription/quota-ledger', label: '权益流水', permission: 'admin.subscription.write', status: 'placeholder' },
    ],
  },
  {
    label: '作文与评测',
    items: [
      { to: '/admin/essays', label: '作文排查', permission: 'admin.essays.read', status: 'implemented' },
    ],
  },
  {
    label: '内容资产',
    items: [
      { to: '/admin/prompts', label: '题库', permission: 'admin.prompts.read', status: 'implemented' },
      { to: '/admin/rubrics', label: 'Rubric', permission: 'admin.rubrics.read', status: 'implemented' },
      { to: '/admin/prompt-assets', label: 'Prompt', permission: 'admin.prompts.read', status: 'placeholder' },
      { to: '/admin/materials', label: '素材', permission: 'admin.prompts.read', status: 'placeholder' },
      { to: '/admin/scoring-config', label: '评分配置', permission: 'admin.rubrics.read', status: 'placeholder' },
    ],
  },
  {
    label: 'AI 与 Agent',
    items: [
      { to: '/ops/agent/runs', label: 'Agent Debug', status: 'implemented' },
      { to: '/admin/model-usage', label: '模型用量', status: 'placeholder' },
    ],
  },
  {
    label: '数据分析',
    items: [
      { to: '/admin/analytics', label: 'BI 总览', status: 'implemented' },
      { to: '/admin/analytics/users', label: '用户分析', status: 'placeholder' },
      { to: '/admin/analytics/subscriptions', label: '订阅分析', status: 'implemented' },
      { to: '/admin/analytics/writing', label: '写作分析', status: 'placeholder' },
      { to: '/admin/analytics/ai-usage', label: 'AI 用量', status: 'placeholder' },
      { to: '/admin/analytics/funnel', label: '转化漏斗', status: 'placeholder' },
    ],
  },
  {
    label: '审计与系统',
    items: [
      { to: '/admin/audit-logs', label: '审计日志', permission: 'admin.audit.read', status: 'implemented' },
      { to: '/admin/admin-users', label: '管理员权限', permission: 'admin.users.write', status: 'placeholder' },
      { to: '/admin/data-catalog', label: '数据地图', permission: 'admin.data_catalog.read', status: 'implemented' },
    ],
  },
]
