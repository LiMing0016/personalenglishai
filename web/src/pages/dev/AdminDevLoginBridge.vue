<template>
  <main class="dev-login-bridge">
    <p>{{ message }}</p>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const message = ref('正在写入本地管理员验收登录态...')

function parseHash() {
  const raw = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : window.location.hash
  return new URLSearchParams(raw)
}

function getSafeTarget(value: string | null) {
  if (!value || !value.startsWith('/') || value.startsWith('//')) {
    return '/admin/users'
  }
  return value
}

onMounted(async () => {
  const params = parseHash()
  const token = params.get('token')
  if (!token) {
    message.value = '缺少 token，无法进入管理员验收页。'
    return
  }

  localStorage.setItem('auth_token', token)
  await router.replace(getSafeTarget(params.get('target')))
})
</script>

<style scoped>
.dev-login-bridge {
  display: grid;
  min-height: 100vh;
  place-items: center;
  background: #f5f7fb;
  color: #334155;
  font: 14px/1.6 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}
</style>
