<template>
  <AuthShell
    panel-title="创建账号"
    panel-subtitle="注册 Personal English AI，开始你的英语写作与个性化提升计划。"
  >
    <AuthTabs v-model="activeTab" :tabs="tabs" />

    <!-- 邮箱注册 -->
    <form v-if="activeTab === 'email'" @submit.prevent="onEmailSubmit" class="auth-form">
      <Input
        v-model="email"
        type="email"
        label="邮箱"
        placeholder="请输入邮箱"
        :error="errors.email"
        @blur="validateEmail"
      />
      <Input
        v-model="emailPassword"
        type="password"
        :show-password-toggle="true"
        label="密码"
        placeholder="至少 8 位，含大小写字母和数字"
        :error="errors.emailPassword"
        @blur="validateEmailPassword"
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
        v-model="emailNickname"
        type="text"
        label="昵称"
        placeholder="请输入昵称"
        :error="errors.emailNickname"
        @blur="validateEmailNickname"
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

    <!-- 手机注册 -->
    <form v-else @submit.prevent="onPhoneSubmit" class="auth-form">
      <Input
        v-model="phone"
        type="tel"
        label="手机号"
        placeholder="请输入手机号"
        :error="errors.phone"
        @blur="validatePhone"
      />
      <div class="sms-code-row">
        <Input
          v-model="smsCode"
          type="text"
          label="验证码"
          placeholder="6 位验证码"
          :error="errors.smsCode"
          @blur="validateSmsCode"
        />
        <button
          type="button"
          class="sms-send-btn"
          :disabled="smsCooldown > 0 || smsSending"
          @click="sendCode"
        >
          {{ smsCooldown > 0 ? `${smsCooldown}s` : '发送验证码' }}
        </button>
      </div>
      <Input
        v-model="phoneNickname"
        type="text"
        label="昵称"
        placeholder="请输入昵称"
        :error="errors.phoneNickname"
        @blur="validatePhoneNickname"
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
import { reactive, ref, watch, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import AuthShell from '@/components/auth/AuthShell.vue'
import AuthTabs from '@/components/auth/AuthTabs.vue'
import Input from '@/components/Input.vue'
import Button from '@/components/Button.vue'
import { authApi } from '@/api/auth'
import { setToken } from '@/utils/token'
import { isValidEmail, isValidPassword, isValidPhone, isValidSmsCode } from '@/utils/validation'

const router = useRouter()

const BUSINESS_HOME = '/app'
const tabs = [
  { label: '邮箱', value: 'email' },
  { label: '手机', value: 'phone' },
]

const activeTab = ref('email')

// email fields
const email = ref('')
const emailPassword = ref('')
const confirmPassword = ref('')
const emailNickname = ref('')

// phone fields
const phone = ref('')
const smsCode = ref('')
const phoneNickname = ref('')

const errors = reactive<Record<string, string>>({
  email: '',
  emailPassword: '',
  confirmPassword: '',
  emailNickname: '',
  phone: '',
  smsCode: '',
  phoneNickname: '',
})
const errorText = ref('')
const loading = ref(false)

// SMS cooldown
const smsCooldown = ref(0)
const smsSending = ref(false)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})

watch(activeTab, () => { errorText.value = '' })

// ── validation ──

function validateEmail(): boolean {
  if (!email.value) { errors.email = '请输入邮箱'; return false }
  if (!isValidEmail(email.value)) { errors.email = '邮箱格式不正确'; return false }
  errors.email = ''
  return true
}

function validateEmailPassword(): boolean {
  if (!emailPassword.value) { errors.emailPassword = '请输入密码'; return false }
  if (!isValidPassword(emailPassword.value)) { errors.emailPassword = '密码至少 8 位，需包含大小写字母和数字'; return false }
  errors.emailPassword = ''
  return true
}

function validateConfirm(): boolean {
  if (!confirmPassword.value) { errors.confirmPassword = '请确认密码'; return false }
  if (emailPassword.value !== confirmPassword.value) { errors.confirmPassword = '两次密码不一致'; return false }
  errors.confirmPassword = ''
  return true
}

function validateEmailNickname(): boolean {
  if (!emailNickname.value.trim()) { errors.emailNickname = '请输入昵称'; return false }
  if (emailNickname.value.length > 50) { errors.emailNickname = '昵称最多 50 个字符'; return false }
  errors.emailNickname = ''
  return true
}

function validatePhone(): boolean {
  if (!phone.value) { errors.phone = '请输入手机号'; return false }
  if (!isValidPhone(phone.value)) { errors.phone = '手机号格式不正确'; return false }
  errors.phone = ''
  return true
}

function validateSmsCode(): boolean {
  if (!smsCode.value) { errors.smsCode = '请输入验证码'; return false }
  if (!isValidSmsCode(smsCode.value)) { errors.smsCode = '验证码为 6 位数字'; return false }
  errors.smsCode = ''
  return true
}

function validatePhoneNickname(): boolean {
  if (!phoneNickname.value.trim()) { errors.phoneNickname = '请输入昵称'; return false }
  if (phoneNickname.value.length > 50) { errors.phoneNickname = '昵称最多 50 个字符'; return false }
  errors.phoneNickname = ''
  return true
}

// live validation
watch(email, () => { if (errors.email) validateEmail() })
watch(emailPassword, () => {
  if (errors.emailPassword) validateEmailPassword()
  if (confirmPassword.value) validateConfirm()
})
watch(confirmPassword, () => { if (errors.confirmPassword) validateConfirm() })
watch(emailNickname, () => { if (errors.emailNickname) validateEmailNickname() })
watch(phone, () => { if (errors.phone) validatePhone() })
watch(smsCode, () => { if (errors.smsCode) validateSmsCode() })
watch(phoneNickname, () => { if (errors.phoneNickname) validatePhoneNickname() })

// ── helpers ──

function getTokenFromResponse(res: { data?: { token?: string }; token?: string; accessToken?: string }): string | null {
  return res.data?.token ?? res.token ?? res.accessToken ?? null
}

// ── send SMS code ──

async function sendCode() {
  if (!validatePhone()) return
  smsSending.value = true
  try {
    await authApi.sendSmsCode({ phone: phone.value, purpose: 'register' })
    smsCooldown.value = 60
    cooldownTimer = setInterval(() => {
      smsCooldown.value--
      if (smsCooldown.value <= 0 && cooldownTimer) {
        clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }, 1000)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    errorText.value = err.response?.data?.message ?? '验证码发送失败'
  } finally {
    smsSending.value = false
  }
}

// ── submit handlers ──

async function onEmailSubmit() {
  errorText.value = ''
  if (!validateEmail() || !validateEmailPassword() || !validateConfirm() || !validateEmailNickname()) return

  loading.value = true
  try {
    const res = await authApi.register({
      email: email.value,
      password: emailPassword.value,
      nickname: emailNickname.value.trim(),
    })
    if (res.code === '0' || res.data?.userId != null) {
      router.replace({ path: '/check-email', query: { email: email.value } })
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

async function onPhoneSubmit() {
  errorText.value = ''
  if (!validatePhone() || !validateSmsCode() || !validatePhoneNickname()) return

  loading.value = true
  try {
    // 手机注册接口直接返回 JWT（自动登录）
    const res = await authApi.phoneRegister({
      phone: phone.value,
      code: smsCode.value,
      nickname: phoneNickname.value.trim(),
    })
    const token = getTokenFromResponse(res)
    if (!token) {
      errorText.value = res.message ?? '注册失败，请重试'
      return
    }
    setToken(token)
    await router.replace(BUSINESS_HOME)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    errorText.value = err.response?.data?.message ?? '注册失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
@import '@/components/auth/auth-form.css';

:deep(.input-wrapper) {
  gap: 8px;
}
</style>
