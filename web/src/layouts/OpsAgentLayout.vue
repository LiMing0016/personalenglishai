<template>
  <div class="admin-layout ops-agent-layout">
    <aside class="admin-sidebar ops-agent-sidebar">
      <div class="admin-brand">PEAI AI Ops</div>
      <div class="admin-role" v-if="me">{{ me.nickname || me.email }}</div>
      <div class="admin-role" v-else-if="loading">正在验证管理员身份...</div>
      <div class="admin-role" v-else>Agent 调试端</div>

      <nav class="admin-nav">
        <router-link
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="admin-nav-link"
          active-class="admin-nav-link--active"
        >{{ item.label }}</router-link>
      </nav>

      <div class="ops-agent-sidebar__links">
        <router-link to="/admin/dashboard" class="admin-back-link">业务管理员端</router-link>
        <router-link to="/app" class="admin-back-link">返回主站</router-link>
      </div>
    </aside>

    <main class="admin-main">
      <header class="admin-topbar">
        <div>
          <div class="admin-title">Agent 调试端</div>
          <div class="admin-subtitle">查看 Agent 请求、Prompt、路由决策、模型输出、usage 与 eval 样本</div>
        </div>
        <router-link to="/admin/dashboard" class="admin-back-link">业务后台</router-link>
      </header>

      <div v-if="loading" class="admin-card admin-loading">正在加载管理员信息...</div>
      <div v-else-if="error" class="admin-card admin-error">{{ error }}</div>
      <router-view v-else />
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminMe, type AdminMe } from '@/api/admin'

const me = ref<AdminMe | null>(null)
const loading = ref(true)
const error = ref('')

const nav = [
  { to: '/ops/agent/runs', label: '请求记录' },
  { to: '/ops/agent/prompts', label: 'Prompt' },
  { to: '/ops/agent/eval-cases', label: 'Eval Cases' },
]

onMounted(async () => {
  try {
    me.value = await getAdminMe()
  } catch {
    error.value = 'Agent 调试端加载失败，请刷新后重试。'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.ops-agent-sidebar__links {
  display: grid;
  gap: 10px;
  margin-top: auto;
}

.ops-agent-sidebar__links .admin-back-link {
  justify-content: center;
  text-align: center;
}
</style>
