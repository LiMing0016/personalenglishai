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
        <div class="admin-topbar__actions">
          <router-link to="/admin/docs" class="admin-btn">文档首页</router-link>
          <router-link to="/admin/agent-debug/runs" class="admin-btn">AI 调试端</router-link>
          <router-link to="/app" class="admin-back-link">返回主站</router-link>
        </div>
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
import { adminNavGroups } from './adminNav.ts'

const me = ref<AdminMe | null>(null)
const loading = ref(true)
const error = ref('')

const visibleNavGroups = computed(() => {
  const permissions = new Set(me.value?.permissions ?? [])
  return adminNavGroups
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

