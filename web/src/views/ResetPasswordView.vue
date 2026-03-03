<template>
  <AuthShell panel-title="重置密码">
    <!-- Loading -->
    <div v-if="validating" class="state-center">
      <p class="state-text">验证中...</p>
    </div>

    <!-- Token invalid / expired -->
    <div v-else-if="tokenStatus === 'INVALID' || tokenStatus === 'EXPIRED'" class="state-center">
      <p class="state-text">
        {{ tokenStatus === 'EXPIRED' ? '重置链接已过期，请重新申请。' : '重置链接无效或已被使用。' }}
      </p>
      <div class="auth-footer">
        <router-link to="/forgot-password">重新申请</router-link>
      </div>
    </div>

    <!-- Reset success -->
    <div v-else-if="resetDone" class="state-center">
      <p class="state-text success-text">密码重置成功！</p>
      <Button variant="primary" class="submit-btn" @click="$router.push('/login')">
        去登录
      </Button>
    </div>

    <!-- Reset form -->
    <form v-else @submit.prevent="onSubmit" class="auth-form">
      <Input
        v-model="password"
        type="password"
        :show-password-toggle="true"
        placeholder="新密码（至少 8 位，含大小写字母和数字）"
        :error="errors.password"
        @blur="validatePassword"
      />
      <Input
        v-model="confirmPassword"
        type="password"
        :show-password-toggle="true"
        placeholder="确认新密码"
        :error="errors.confirm"
        @blur="validateConfirm"
      />

      <p v-if="errorText" class="error-text">{{ errorText }}</p>

      <Button type="submit" variant="primary" :loading="loading" class="submit-btn">
        重置密码
      </Button>
    </form>
  </AuthShell>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import AuthShell from '@/components/auth/AuthShell.vue'
import Input from '@/components/Input.vue'
import Button from '@/components/Button.vue'
import { http } from '@/api/http'
import { isValidPassword } from '@/utils/validation'

const route = useRoute()

const validating = ref(true)
const tokenStatus = ref<'VALID' | 'INVALID' | 'EXPIRED' | null>(null)
const resetDone = ref(false)
const loading = ref(false)
const errorText = ref('')

const password = ref('')
const confirmPassword = ref('')
const errors = reactive<{ password: string; confirm: string }>({ password: '', confirm: '' })

const token = (route.query.token as string) || ''

onMounted(async () => {
  if (!token) {
    tokenStatus.value = 'INVALID'
    validating.value = false
    return
  }
  try {
    const res = await http.get(`/v1/auth/reset-password/validate?token=${encodeURIComponent(token)}`)
    const status = (res.data as { data?: { status?: string } }).data?.status
    tokenStatus.value = (status as 'VALID' | 'INVALID' | 'EXPIRED') ?? 'INVALID'
  } catch {
    tokenStatus.value = 'INVALID'
  } finally {
    validating.value = false
  }
})

function validatePassword(): boolean {
  if (!password.value) {
    errors.password = '请输入新密码'
    return false
  }
  if (!isValidPassword(password.value)) {
    errors.password = '密码至少 8 位，需包含大小写字母和数字'
    return false
  }
  errors.password = ''
  return true
}

function validateConfirm(): boolean {
  if (!confirmPassword.value) {
    errors.confirm = '请确认密码'
    return false
  }
  if (password.value !== confirmPassword.value) {
    errors.confirm = '两次密码不一致'
    return false
  }
  errors.confirm = ''
  return true
}

async function onSubmit() {
  errorText.value = ''
  if (!validatePassword() || !validateConfirm()) return

  loading.value = true
  try {
    await http.post('/v1/auth/reset-password', { token, password: password.value })
    resetDone.value = true
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    errorText.value = err.response?.data?.message ?? '重置失败，请重试'
  } finally {
    loading.value = false
  }
}

watch(password, () => {
  if (errors.password) validatePassword()
  if (confirmPassword.value) validateConfirm()
})

watch(confirmPassword, () => {
  if (errors.confirm) validateConfirm()
})
</script>

<style scoped>
@import '@/components/auth/auth-form.css';

:deep(.input-label) {
  display: none;
}

.state-center {
  text-align: center;
  padding: 12px 0;
}

.state-text {
  color: rgba(220, 231, 249, 0.92);
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 16px;
}

.success-text {
  color: #8fd8ff;
  font-weight: 600;
  font-size: 16px;
}
</style>
