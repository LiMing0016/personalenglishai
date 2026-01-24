<template>
  <div class="page-container">
    <Card>
      <h1 class="page-title">登录</h1>
      <form @submit.prevent="handleSubmit" class="form">
        <Input
          v-model="formData.email"
          type="email"
          label="邮箱"
          placeholder="请输入邮箱"
          :error="errors.email"
          @blur="validateEmail"
        />
        <Input
          v-model="formData.password"
          type="password"
          label="密码"
          placeholder="请输入密码（至少8位）"
          :error="errors.password"
          @blur="validatePassword"
        />
        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>
        <Button type="submit" :loading="loading" class="submit-btn">
          登录
        </Button>
        <div class="form-footer">
          <router-link to="/register" class="link">还没有账号？立即注册</router-link>
        </div>
      </form>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/Card.vue'
import Input from '@/components/Input.vue'
import Button from '@/components/Button.vue'
import { authApi } from '@/api/authApi'
import { isValidEmail, isValidPassword, getErrorMessage } from '@/utils/validation'
import { showToast } from '@/utils/toast'

const router = useRouter()

const formData = reactive({
  email: '',
  password: ''
})

const errors = reactive({
  email: '',
  password: ''
})

const loading = ref(false)
const errorMessage = ref('')

const validateEmail = () => {
  if (!formData.email) {
    errors.email = '请输入邮箱'
    return false
  }
  if (!isValidEmail(formData.email)) {
    errors.email = '请输入有效的邮箱地址'
    return false
  }
  errors.email = ''
  return true
}

const validatePassword = () => {
  if (!formData.password) {
    errors.password = '请输入密码'
    return false
  }
  if (!isValidPassword(formData.password)) {
    errors.password = '密码至少需要8位'
    return false
  }
  errors.password = ''
  return true
}

const handleSubmit = async () => {
  errorMessage.value = ''

  if (!validateEmail() || !validatePassword()) {
    return
  }

  loading.value = true

  try {
    const response = await authApi.login({
      email: formData.email,
      password: formData.password
    })

    if (response.success && response.user) {
      if (response.user.emailVerified) {
        router.push('/')
      } else {
        router.push('/check-email')
      }
    } else {
      errorMessage.value = getErrorMessage(response.code)
    }
  } catch (error) {
    errorMessage.value = getErrorMessage()
  } finally {
    loading.value = false
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

.page-title {
  margin: 0 0 24px 0;
  font-size: 28px;
  font-weight: 600;
  color: #333;
  text-align: center;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
}

.error-message {
  padding: 12px;
  background: #ffebee;
  color: #c62828;
  border-radius: 4px;
  font-size: 14px;
}

.form-footer {
  text-align: center;
  margin-top: 16px;
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

