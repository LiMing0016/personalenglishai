<template>
  <AuthShell
    panel-title="请验证您的邮箱"
    panel-subtitle="验证邮件已发送，请检查您的邮箱（包括垃圾箱）"
  >
    <div class="check-email-content">
      <div v-if="email" class="email-display">
        <strong>{{ email }}</strong>
      </div>

      <div class="actions">
        <Button
          variant="primary"
          class="submit-btn"
          :disabled="countdown > 0"
          :loading="resending"
          @click="handleResend"
        >
          {{ countdown > 0 ? `重新发送 (${countdown}s)` : '重新发送验证邮件' }}
        </Button>
      </div>

      <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>

      <div class="auth-footer">
        <router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </AuthShell>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import AuthShell from '@/components/auth/AuthShell.vue'
import Button from '@/components/Button.vue'
import { authApi } from '@/api/authApi'
import { getErrorMessage } from '@/utils/validation'
import { showToast } from '@/utils/toast'

const route = useRoute()

const email = ref<string>('')
const resending = ref(false)
const countdown = ref(0)
const errorMessage = ref('')
let countdownTimer: number | null = null

onMounted(() => {
  const emailParam = route.query.email as string
  if (emailParam) {
    email.value = emailParam
  }
})

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})

const startCountdown = () => {
  countdown.value = 60
  countdownTimer = window.setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      if (countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }
  }, 1000)
}

const handleResend = async () => {
  if (countdown.value > 0) {
    return
  }

  errorMessage.value = ''
  resending.value = true

  try {
    const response = await authApi.resendVerification(email.value || undefined)

    if (response.success) {
      showToast('验证邮件已重新发送', 'success')
      startCountdown()
    } else {
      errorMessage.value = getErrorMessage(response.code)
      showToast(getErrorMessage(response.code), 'error')
    }
  } catch (error) {
    errorMessage.value = getErrorMessage()
    showToast(getErrorMessage(), 'error')
  } finally {
    resending.value = false
  }
}
</script>

<style scoped>
@import '@/components/auth/auth-form.css';

.check-email-content {
  text-align: center;
}

.email-display {
  margin: 8px 0 20px;
  padding: 10px 14px;
  background: rgba(53, 192, 255, 0.08);
  border: 1px solid rgba(53, 192, 255, 0.2);
  border-radius: 10px;
  color: #8fd8ff;
  font-size: 14px;
}

.actions {
  margin-bottom: 12px;
}
</style>
