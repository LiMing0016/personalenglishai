<template>
  <div class="page-container">
    <Card>
      <div class="verify-email-content">
        <div v-if="loading" class="loading-state">
          <div class="spinner"></div>
          <p>正在验证...</p>
        </div>

        <div v-else-if="status === 'VERIFIED'" class="success-state">
          <div class="icon">✅</div>
          <h1 class="page-title">邮箱验证成功</h1>
          <p class="description">您的邮箱已验证成功，现在可以登录了</p>
          <Button @click="goToLogin" class="action-btn">去登录</Button>
        </div>

        <div v-else-if="status === 'EXPIRED'" class="expired-state">
          <div class="icon">⏰</div>
          <h1 class="page-title">验证链接已过期</h1>
          <p class="description">验证链接已过期，请重新发送验证邮件</p>
          <Button @click="goToResend" class="action-btn">重新发送</Button>
        </div>

        <div v-else-if="status === 'INVALID'" class="invalid-state">
          <div class="icon">❌</div>
          <h1 class="page-title">验证链接无效</h1>
          <p class="description">验证链接无效或已被使用，请重新注册或登录</p>
          <div class="action-buttons">
            <Button variant="primary" @click="goToRegister" class="action-btn">重新注册</Button>
            <Button variant="outline" @click="goToLogin" class="action-btn">去登录</Button>
          </div>
        </div>

        <div v-else class="error-state">
          <div class="icon">⚠️</div>
          <h1 class="page-title">验证失败</h1>
          <p class="description">{{ errorMessage || '验证过程中出现错误，请稍后重试' }}</p>
          <div class="action-buttons">
            <Button variant="primary" @click="goToRegister" class="action-btn">重新注册</Button>
            <Button variant="outline" @click="goToLogin" class="action-btn">去登录</Button>
          </div>
        </div>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Card from '@/components/Card.vue'
import Button from '@/components/Button.vue'
import { authApi } from '@/api/authApi'
import { getErrorMessage } from '@/utils/validation'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const status = ref<'VERIFIED' | 'EXPIRED' | 'INVALID' | null>(null)
const errorMessage = ref('')

const verifyEmail = async (token: string) => {
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await authApi.verifyEmail(token)

    if (response.success && response.status === 'VERIFIED') {
      status.value = 'VERIFIED'
      showToast('邮箱验证成功', 'success')
    } else if (response.status === 'EXPIRED') {
      status.value = 'EXPIRED'
    } else if (response.status === 'INVALID') {
      status.value = 'INVALID'
    } else {
      status.value = null
      errorMessage.value = getErrorMessage(response.code, response.message)
    }
  } catch (error) {
    status.value = null
    errorMessage.value = getErrorMessage()
    showToast('验证失败，请稍后重试', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const token = route.query.token as string
  if (!token) {
    status.value = 'INVALID'
    loading.value = false
    return
  }

  verifyEmail(token)
})

const goToLogin = () => {
  router.push('/login')
}

const goToRegister = () => {
  router.push('/register')
}

const goToResend = () => {
  router.push('/check-email')
}
</script>

<style scoped>
.page-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 20px;
}

.verify-email-content {
  text-align: center;
}

.loading-state {
  padding: 40px 0;
}

.spinner {
  width: 48px;
  height: 48px;
  border: 4px solid #e3f2fd;
  border-top-color: #1976d2;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 16px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0 0 16px 0;
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.description {
  margin: 0 0 32px 0;
  color: #666;
  font-size: 16px;
  line-height: 1.5;
}

.action-btn {
  min-width: 140px;
}

.action-buttons {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.success-state .icon {
  color: #4caf50;
}

.expired-state .icon {
  color: #ff9800;
}

.invalid-state .icon,
.error-state .icon {
  color: #f44336;
}
</style>


