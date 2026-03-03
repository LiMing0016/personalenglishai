<template>
  <AuthShell panel-title="邮箱验证">
    <div class="verify-content">
      <!-- Loading -->
      <div v-if="loading" class="state-center">
        <div class="spinner"></div>
        <p class="state-text">正在验证...</p>
      </div>

      <!-- Verified -->
      <div v-else-if="status === 'VERIFIED'" class="state-center">
        <div class="status-icon status-success">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
        </div>
        <p class="state-text success-color">邮箱验证成功</p>
        <p class="state-desc">您的邮箱已验证成功，现在可以登录了</p>
        <Button variant="primary" class="submit-btn" @click="goToLogin">去登录</Button>
      </div>

      <!-- Expired -->
      <div v-else-if="status === 'EXPIRED'" class="state-center">
        <div class="status-icon status-warn">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
        </div>
        <p class="state-text warn-color">验证链接已过期</p>
        <p class="state-desc">验证链接已过期，请重新发送验证邮件</p>
        <Button variant="primary" class="submit-btn" @click="goToResend">重新发送</Button>
      </div>

      <!-- Invalid -->
      <div v-else-if="status === 'INVALID'" class="state-center">
        <div class="status-icon status-error">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
        </div>
        <p class="state-text error-color">验证链接无效</p>
        <p class="state-desc">验证链接无效或已被使用，请重新注册或登录</p>
        <div class="action-row">
          <Button variant="primary" class="submit-btn" @click="goToRegister">重新注册</Button>
        </div>
        <div class="auth-footer">
          <router-link to="/login">去登录</router-link>
        </div>
      </div>

      <!-- Error -->
      <div v-else class="state-center">
        <div class="status-icon status-error">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
        </div>
        <p class="state-text error-color">验证失败</p>
        <p class="state-desc">{{ errorMessage || '验证过程中出现错误，请稍后重试' }}</p>
        <div class="action-row">
          <Button variant="primary" class="submit-btn" @click="goToRegister">重新注册</Button>
        </div>
        <div class="auth-footer">
          <router-link to="/login">去登录</router-link>
        </div>
      </div>
    </div>
  </AuthShell>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthShell from '@/components/auth/AuthShell.vue'
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
@import '@/components/auth/auth-form.css';

.verify-content {
  text-align: center;
}

.state-center {
  padding: 8px 0;
}

.state-text {
  font-size: 18px;
  font-weight: 700;
  color: rgba(220, 231, 249, 0.92);
  margin: 0 0 8px;
}

.state-desc {
  font-size: 14px;
  color: rgba(200, 218, 255, 0.7);
  line-height: 1.6;
  margin: 0 0 20px;
}

.status-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  margin: 0 auto 16px;
}

.status-success {
  background: rgba(76, 217, 130, 0.12);
  color: #4cd982;
}

.status-warn {
  background: rgba(255, 184, 77, 0.12);
  color: #ffb84d;
}

.status-error {
  background: rgba(255, 110, 110, 0.12);
  color: #ff6e6e;
}

.success-color { color: #4cd982; }
.warn-color { color: #ffb84d; }
.error-color { color: #ff6e6e; }

.action-row {
  margin-bottom: 8px;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid rgba(255, 255, 255, 0.1);
  border-top-color: #6f6bff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 16px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
