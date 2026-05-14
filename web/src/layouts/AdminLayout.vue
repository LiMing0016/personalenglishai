<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="admin-brand">PEAI Admin</div>
      <div class="admin-role" v-if="me">{{ me.nickname || me.email }}</div>
      <div class="admin-role" v-else-if="loading">正在验证管理员身份...</div>
      <div class="admin-role" v-else>管理员后台</div>
      <nav class="admin-nav">
        <section v-for="group in visibleNavGroups" :key="group.label" class="admin-nav-group">
          <div class="admin-nav-group__label">{{ group.label }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="admin-nav-link"
            active-class="admin-nav-link--active"
          >
            <span>{{ item.label }}</span>
            <span v-if="item.status === 'placeholder'" class="admin-nav-link__status">待接入</span>
          </router-link>
        </section>
      </nav>
    </aside>
    <main class="admin-main">
      <header class="admin-topbar">
        <div>
          <div class="admin-title">管理员后台</div>
          <div class="admin-subtitle">运营治理、用户权益、作文排查、内容资产与 AI 调试</div>
        </div>
        <router-link to="/app" class="admin-back-link">返回主站</router-link>
      </header>
      <div v-if="loading" class="admin-card admin-loading">正在加载管理员信息...</div>
      <div v-else-if="error" class="admin-card admin-error">{{ error }}</div>
      <router-view v-else />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAdminMe, type AdminMe } from '@/api/admin'

type AdminNavStatus = 'implemented' | 'placeholder'

interface AdminNavItem {
  to: string
  label: string
  permission?: string
  status?: AdminNavStatus
}

interface AdminNavGroup {
  label: string
  items: AdminNavItem[]
}

const me = ref<AdminMe | null>(null)
const loading = ref(true)
const error = ref('')

const navGroups: AdminNavGroup[] = [
  {
    label: '总览',
    items: [
      { to: '/admin/dashboard', label: 'Dashboard', status: 'implemented' },
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
      { to: '/admin/agent-debug/runs', label: 'Agent Debug', status: 'placeholder' },
      { to: '/admin/model-usage', label: '模型用量', status: 'placeholder' },
    ],
  },
  {
    label: '审计与系统',
    items: [
      { to: '/admin/audit-logs', label: '审计日志', permission: 'admin.audit.read', status: 'implemented' },
      { to: '/admin/admin-users', label: '管理员权限', permission: 'admin.users.write', status: 'placeholder' },
    ],
  },
]

const visibleNavGroups = computed(() => {
  const permissions = new Set(me.value?.permissions ?? [])
  return navGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.permission || permissions.has(item.permission)),
    }))
    .filter((group) => group.items.length > 0)
})

onMounted(async () => {
  try {
    me.value = await getAdminMe()
  } catch {
    error.value = '管理员信息加载失败，请刷新后重试。'
  } finally {
    loading.value = false
  }
})
</script>

