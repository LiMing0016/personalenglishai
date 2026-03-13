<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="admin-brand">PEAI Admin</div>
      <div class="admin-role" v-if="me">{{ me.nickname || me.email }}</div>
      <div class="admin-role" v-else-if="loading">正在验证管理员身份...</div>
      <div class="admin-role" v-else>管理员后台</div>
      <nav class="admin-nav">
        <router-link
          v-for="item in visibleNav"
          :key="item.to"
          :to="item.to"
          class="admin-nav-link"
          active-class="admin-nav-link--active"
        >{{ item.label }}</router-link>
      </nav>
    </aside>
    <main class="admin-main">
      <header class="admin-topbar">
        <div>
          <div class="admin-title">管理员后台</div>
          <div class="admin-subtitle">用户治理、作文排查、题库与 Rubric 管理</div>
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

const me = ref<AdminMe | null>(null)
const loading = ref(true)
const error = ref('')

const nav = [
  { to: '/admin/dashboard', label: 'Dashboard' },
  { to: '/admin/users', label: '用户', permission: 'admin.users.read' },
  { to: '/admin/essays', label: '作文', permission: 'admin.essays.read' },
  { to: '/admin/prompts', label: '题库', permission: 'admin.prompts.read' },
  { to: '/admin/rubrics', label: 'Rubric', permission: 'admin.rubrics.read' },
  { to: '/admin/audit-logs', label: '审计', permission: 'admin.audit.read' },
]

const visibleNav = computed(() => {
  const permissions = new Set(me.value?.permissions ?? [])
  return nav.filter((item) => !item.permission || permissions.has(item.permission))
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

