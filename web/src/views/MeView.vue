<template>
  <div class="page">
    <Card>
      <h1 class="title">我的</h1>
      <div v-if="loading" class="loading">加载中…</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="profile">
        <p><strong>用户ID</strong> {{ profile?.userId ?? '-' }}</p>
        <p><strong>邮箱</strong> {{ profile?.email ?? '-' }}</p>
        <p><strong>昵称</strong> {{ profile?.nickname ?? '-' }}</p>
      </div>
      <div class="actions">
        <router-link to="/" class="link">首页</router-link>
        <button type="button" class="btn" @click="logout">退出登录</button>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Card from '@/components/Card.vue'
import { useRouter } from 'vue-router'
import { userApi, type MeProfile } from '@/api/user'
import { clearToken } from '@/utils/token'

const router = useRouter()
const profile = ref<MeProfile | null>(null)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    const res = await userApi.getMyProfile()
    if (res.data) {
      profile.value = res.data
    } else {
      error.value = res.message ?? '获取失败'
    }
  } catch {
    error.value = '获取个人信息失败'
  } finally {
    loading.value = false
  }
})

function logout() {
  clearToken()
  router.replace('/login')
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.title {
  margin: 0 0 24px;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
}
.loading, .error {
  margin: 0 0 20px;
  font-size: 14px;
}
.error {
  color: #c62828;
}
.profile {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}
.profile p {
  margin: 0;
  font-size: 14px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 16px;
}
.link {
  color: #1976d2;
  text-decoration: none;
  font-size: 14px;
}
.link:hover {
  text-decoration: underline;
}
.btn {
  padding: 8px 16px;
  font-size: 14px;
  color: #1976d2;
  background: transparent;
  border: 1px solid #1976d2;
  border-radius: 6px;
  cursor: pointer;
}
.btn:hover {
  background: #e3f2fd;
}
</style>
