<template>
  <AuthShell
    panel-title="忘记密码"
    panel-subtitle="输入你的注册邮箱，我们将发送密码重置链接。"
  >
    <form v-if="!sent" @submit.prevent="onSubmit" class="auth-form">
      <Input
        v-model="email"
        type="email"
        placeholder="请输入邮箱"
        :error="emailError"
        @blur="validateEmail"
      />

      <p v-if="errorText" class="error-text">{{ errorText }}</p>

      <Button type="submit" variant="primary" :loading="loading" class="submit-btn">
        发送重置链接
      </Button>

      <div class="auth-footer">
        <router-link to="/login">返回登录</router-link>
      </div>
    </form>

    <div v-else class="sent-state">
      <p class="sent-message">
        如果该邮箱已注册，重置链接已发送到 <strong>{{ email }}</strong>，请查收邮件（包括垃圾箱）。
      </p>
      <div class="auth-footer">
        <router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </AuthShell>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import AuthShell from '@/components/auth/AuthShell.vue'
import Input from '@/components/Input.vue'
import Button from '@/components/Button.vue'
import { http } from '@/api/http'
import { isValidEmail } from '@/utils/validation'

const email = ref('')
const emailError = ref('')
const errorText = ref('')
const loading = ref(false)
const sent = ref(false)

function validateEmail(): boolean {
  if (!email.value) {
    emailError.value = '请输入邮箱'
    return false
  }
  if (!isValidEmail(email.value)) {
    emailError.value = '邮箱格式不正确'
    return false
  }
  emailError.value = ''
  return true
}

async function onSubmit() {
  errorText.value = ''
  if (!validateEmail()) return

  loading.value = true
  try {
    await http.post('/v1/auth/forgot-password', { email: email.value })
    sent.value = true
  } catch {
    errorText.value = '发送失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

watch(email, () => {
  if (emailError.value) validateEmail()
})
</script>

<style scoped>
@import '@/components/auth/auth-form.css';

:deep(.input-label) {
  display: none;
}

.sent-state {
  text-align: center;
}

.sent-message {
  color: rgba(220, 231, 249, 0.92);
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 24px;
}

.sent-message strong {
  color: #8fd8ff;
}
</style>
