<template>
  <div class="page">
    <h1 class="app-home-title">App Home</h1>
    <Card>
      <h1 class="title">首页</h1>
      <div v-if="loading" class="status">校验登录态中…</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="success">
        <p>已通过受保护接口校验，当前用户已登录。</p>
        <p v-if="profile" class="profile">
          <span>用户：{{ profile.email ?? profile.nickname ?? profile.userId ?? '-' }}</span>
        </p>
      </div>
      <div class="actions">
        <router-link to="/me" class="link">个人中心</router-link>
        <router-link to="/" class="link">站点首页</router-link>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Card from '@/components/Card.vue'
import { userApi, type MeProfile } from '@/api/user'

const profile = ref<MeProfile | null>(null)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    const res = await userApi.getMyProfile()
    if (res.data) {
      profile.value = res.data
    }
  } catch {
    error.value = '鉴权失败（可能 token 已失效，将自动跳转登录页）'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.app-home-title {
  margin: 0 0 16px;
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  color: #333;
}
.title {
  margin: 0 0 24px;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
}
.status,
.error,
.success {
  margin: 0 0 20px;
  font-size: 14px;
}
.error {
  color: #c62828;
}
.success {
  color: #2e7d32;
}
.profile {
  margin-top: 8px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 24px;
}
.link {
  color: #1976d2;
  text-decoration: none;
  font-size: 14px;
}
.link:hover {
  text-decoration: underline;
}
</style>
