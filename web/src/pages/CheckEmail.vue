<template>
  <div class="page-container">
    <Card>
      <div class="check-email-content">
        <div class="icon">📧</div>
        <h1 class="page-title">请验证您的邮箱</h1>
        <p class="description">
          验证邮件已发送，请检查您的邮箱（包括垃圾箱）
        </p>
        <div v-if="email" class="email-display">
          <strong>{{ email }}</strong>
        </div>
        <div class="actions">
          <Button
            :disabled="countdown > 0"
            :loading="resending"
            @click="handleResend"
          >
            {{ countdown > 0 ? `重新发送 (${countdown}s)` : '重新发送验证邮件' }}
          </Button>
        </div>
        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>
        <div class="footer-links">
          <router-link to="/login" class="link">返回登录</router-link>
        </div>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import Card from '@/components/Card.vue'
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
.page-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 20px;
}

.check-email-content {
  text-align: center;
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
  margin: 0 0 24px 0;
  color: #666;
  font-size: 16px;
  line-height: 1.5;
}

.email-display {
  margin: 16px 0 32px 0;
  padding: 12px;
  background: #e3f2fd;
  border-radius: 6px;
  color: #1976d2;
}

.actions {
  margin-bottom: 16px;
}

.error-message {
  padding: 12px;
  background: #ffebee;
  color: #c62828;
  border-radius: 4px;
  font-size: 14px;
  margin-bottom: 16px;
}

.footer-links {
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

