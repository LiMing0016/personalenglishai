<template>
  <AuthShell
    panel-title="创建账号"
    panel-subtitle="注册 Personal English AI，开始你的英语写作与个性化提升计划。"
  >
    <form @submit.prevent="onSubmit" class="auth-form">
      <Input
        v-model="email"
        type="email"
        label="邮箱"
        placeholder="请输入邮箱"
        :error="errors.email"
        @blur="validateEmail"
      />
      <Input
        v-model="password"
        type="password"
        :show-password-toggle="true"
        label="密码"
        placeholder="请输入密码（至少 8 位）"
        :error="errors.password"
        @blur="validatePassword"
      />
      <Input
        v-model="confirmPassword"
        type="password"
        :show-password-toggle="true"
        label="确认密码"
        placeholder="请再次输入密码"
        :error="errors.confirmPassword"
        @blur="validateConfirm"
      />
      <Input
        v-model="nickname"
        type="text"
        label="昵称"
        placeholder="请输入昵称"
        :error="errors.nickname"
        @blur="validateNickname"
      />

      <p v-if="errorText" class="error-text">{{ errorText }}</p>

      <Button type="submit" variant="primary" :loading="loading" class="submit-btn">
        注册
      </Button>

      <div class="auth-footer">
        <span>已有账号？</span>
        <router-link to="/login">去登录</router-link>
      </div>
    </form>
  </AuthShell>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AuthShell from '@/components/auth/AuthShell.vue'
import Input from '@/components/Input.vue'
import Button from '@/components/Button.vue'
import { authApi } from '@/api/auth'
import { isValidEmail, isValidPassword } from '@/utils/validation'

const router = useRouter()
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const nickname = ref('')
const errors = reactive<{
  email: string
  password: string
  confirmPassword: string
  nickname: string
}>({ email: '', password: '', confirmPassword: '', nickname: '' })
const errorText = ref('')
const loading = ref(false)

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

function validateConfirm(): boolean {
  if (!confirmPassword.value) {
    errors.confirmPassword = '请确认密码'
    return false
  }
  if (password.value !== confirmPassword.value) {
    errors.confirmPassword = '两次密码不一致'
    return false
  }
  errors.confirmPassword = ''
  return true
}

function validateNickname(): boolean {
  if (!nickname.value.trim()) {
    errors.nickname = '请输入昵称'
    return false
  }
  if (nickname.value.length > 50) {
    errors.nickname = '昵称最多 50 个字符'
    return false
  }
  errors.nickname = ''
  return true
}

async function onSubmit() {
  errorText.value = ''
  if (
    !validateEmail() ||
    !validatePassword() ||
    !validateConfirm() ||
    !validateNickname()
  ) return
  loading.value = true
  try {
    const res = await authApi.register({
      email: email.value,
      password: password.value,
      nickname: nickname.value.trim(),
    })
    if (res.code === '0' || res.data?.userId != null) {
      router.replace('/login')
    } else {
      errorText.value = res.message ?? '注册失败，请重试'
    }
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    errorText.value = err.response?.data?.message ?? '注册失败，请重试'
  } finally {
    loading.value = false
  }
}

watch(email, () => {
  if (errors.email) validateEmail()
})

watch(password, () => {
  if (errors.password) validatePassword()
  if (confirmPassword.value) validateConfirm()
})

watch(confirmPassword, () => {
  if (errors.confirmPassword) validateConfirm()
})

watch(nickname, () => {
  if (errors.nickname) validateNickname()
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
  font-size: 14px;
  color: rgba(225, 235, 255, 0.85);
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
  gap: 8px;
}

:deep(.input-label) {
  color: rgba(236, 243, 255, 0.92);
  font-weight: 600;
}

:deep(.input) {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: #ffffff;
  border-radius: 12px;
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
  margin-top: 4px;
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
