<template>
  <AuthShell
    panel-title="请验证您的邮箱"
    panel-subtitle="验证邮件已发送，请检查您的邮箱（包括垃圾箱）"
  >
    <div class="check-email-content">
      <div v-if="email" class="email-display">
        <strong>{{ email }}</strong>
      </div>
      <Input
        v-else
        v-model="manualEmail"
        type="email"
        label="邮箱"
        placeholder="请输入注册邮箱"
        :error="emailError"
        @blur="validateManualEmail"
      />

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
import Input from '@/components/Input.vue'
import { authApi } from '@/api/authApi'
import { getErrorMessage, isValidEmail } from '@/utils/validation'
import { showToast } from '@/utils/toast'

const route = useRoute()

const email = ref<string>('')
const manualEmail = ref('')
const emailError = ref('')
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

const validateManualEmail = () => {
  if (email.value) {
    emailError.value = ''
    return true
  }
  if (!manualEmail.value.trim()) {
    emailError.value = '请输入邮箱'
    return false
  }
  if (!isValidEmail(manualEmail.value.trim())) {
    emailError.value = '邮箱格式不正确'
    return false
  }
  emailError.value = ''
  return true
}

const handleResend = async () => {
  if (countdown.value > 0) {
    return
  }
  if (!validateManualEmail()) {
    return
  }

  errorMessage.value = ''
  resending.value = true

  try {
    const targetEmail = email.value || manualEmail.value.trim()
    const response = await authApi.resendVerification(targetEmail)

    if (response.success) {
      showToast('验证邮件已重新发送', 'success')
      startCountdown()
    } else {
      errorMessage.value = getErrorMessage(response.code)
      showToast(getErrorMessage(response.code), 'error')
    }
  } catch (error) {
    const err = error as { response?: { data?: { code?: string; message?: string } } }
    errorMessage.value = getErrorMessage(err.response?.data?.code, err.response?.data?.message)
    showToast(errorMessage.value, 'error')
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
