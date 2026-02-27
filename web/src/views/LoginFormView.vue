<template>
  <AuthShell
    panel-brand="Personal English AI"
    panel-title="欢迎回来"
    panel-subtitle="登录 Personal English AI，继续你的英语写作训练与 AI 学习计划。"
  >
    <form @submit.prevent="onSubmit" class="auth-form">
      <Input
        v-model="email"
        type="email"
        placeholder="用户名 / 邮箱"
        :error="errors.email"
        @blur="validateEmail"
      />
      <Input
        v-model="password"
        type="password"
        :show-password-toggle="true"
        placeholder="密码"
        :error="errors.password"
        @blur="validatePassword"
      />

      <p v-if="errorText" class="error-text">{{ errorText }}</p>

      <Button type="submit" variant="primary" :loading="loading" class="submit-btn">
        登录
      </Button>

      <div class="auth-footer auth-footer-split">
        <span class="line" aria-hidden="true"></span>
        <router-link :to="registerLink">去注册</router-link>
        <span class="line" aria-hidden="true"></span>
      </div>
    </form>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthShell from '@/components/auth/AuthShell.vue'
import Input from '@/components/Input.vue'
import Button from '@/components/Button.vue'
import { authApi } from '@/api/auth'
import { setToken } from '@/utils/token'
import { isValidEmail, isValidPassword } from '@/utils/validation'

const router = useRouter()
const route = useRoute()
const email = ref('')
const password = ref('')
const errors = reactive<{ email: string; password: string }>({ email: '', password: '' })
const errorText = ref('')
const loading = ref(false)

const BUSINESS_HOME = '/app'

const registerLink = computed(() => {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return redirect ? { path: '/register', query: { redirect } } : { path: '/register' }
})

function validateEmail(): boolean {
  if (!email.value) {
    errors.email = '请输入邮箱'
    return false
  }
  if (!isValidEmail(email.value)) {
    errors.email = '邮箱格式不正确'
    return false
  }
  errors.email = ''
  return true
}

function validatePassword(): boolean {
  if (!password.value) {
    errors.password = '请输入密码'
    return false
  }
  if (!isValidPassword(password.value)) {
    errors.password = '密码至少 8 位'
    return false
  }
  errors.password = ''
  return true
}

function getTokenFromResponse(res: { data?: { token?: string }; token?: string; accessToken?: string }): string | null {
  return res.data?.token ?? res.token ?? res.accessToken ?? null
}

async function onSubmit() {
  errorText.value = ''
  if (!validateEmail() || !validatePassword()) return

  loading.value = true
  try {
    const res = await authApi.login({ email: email.value, password: password.value })
    const token = getTokenFromResponse(res)

    if (!token) {
      errorText.value = res.message ?? '登录失败，请重试'
      return
    }

    setToken(token)

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const safeRedirect =
      redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : BUSINESS_HOME

    await router.replace(safeRedirect)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    errorText.value = err.response?.data?.message ?? '登录失败，请重试'
  } finally {
    loading.value = false
  }
}

watch(email, () => {
  if (errors.email) validateEmail()
})

watch(password, () => {
  if (errors.password) validatePassword()
})
</script>

<style scoped>
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.error-text {
  margin: 2px 0 0;
  font-size: 13px;
  color: #ffb4b4;
}

.auth-footer {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: rgba(225, 235, 255, 0.85);
}

.auth-footer-split {
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.auth-footer-split .line {
  width: 80px;
  height: 1px;
  background: rgba(255, 255, 255, 0.12);
}

.auth-footer a {
  color: #8fd8ff;
  text-decoration: none;
  font-weight: 600;
}

.auth-footer a:hover {
  text-decoration: underline;
}

:deep(.input-wrapper) {
  gap: 4px;
}

:deep(.input-label) {
  display: none;
}

:deep(.input) {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: #ffffff;
  border-radius: 14px;
  height: 44px;
  padding: 10px 14px;
}

:deep(.input::placeholder) {
  color: rgba(226, 234, 248, 0.42);
}

:deep(.input:focus) {
  border-color: rgba(114, 198, 255, 0.9);
  box-shadow: 0 0 0 3px rgba(76, 161, 255, 0.15);
}

:deep(.input:disabled) {
  background: rgba(255, 255, 255, 0.03);
}

:deep(.input-error) {
  border-color: rgba(255, 110, 110, 0.9);
}

:deep(.input-error-text) {
  color: #ffb0b0;
}

:deep(.btn.submit-btn) {
  width: 100%;
  min-width: 0;
  border-radius: 999px;
  font-weight: 700;
  margin-top: 8px;
  box-shadow: 0 10px 24px rgba(64, 122, 255, 0.2);
}

:deep(.btn-primary.submit-btn) {
  background: linear-gradient(90deg, #35c0ff 0%, #6f6bff 52%, #a47dff 100%);
  color: #fff;
}

:deep(.btn-primary.submit-btn:hover:not(:disabled)) {
  background: linear-gradient(90deg, #2bb7f6 0%, #6662fa 52%, #9a72f9 100%);
  transform: translateY(-1px);
}
</style>
