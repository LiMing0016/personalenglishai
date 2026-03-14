<template>
  <AuthShell
    panel-brand="Personal English AI"
    panel-title="欢迎回来"
    panel-subtitle="登录 Personal English AI，继续你的英语写作训练与 AI 学习计划。"
  >
    <AuthTabs v-model="activeTab" :tabs="tabs" />

    <!-- 邮箱登录 -->
    <form v-if="activeTab === 'email'" @submit.prevent="onEmailSubmit" class="auth-form">
      <Input
        v-model="email"
        type="email"
        placeholder="用户名 / 邮箱"
        :error="errors.email"
        @blur="validateEmail"
      />
      <Input
        v-model="emailPassword"
        type="password"
        :show-password-toggle="true"
        placeholder="密码"
        :error="errors.emailPassword"
        @blur="validateEmailPassword"
      />

      <p v-if="errorText" class="error-text">{{ errorText }}</p>

      <div class="forgot-link-row">
        <router-link to="/forgot-password" class="forgot-link">忘记密码？</router-link>
      </div>

      <Button type="submit" variant="primary" :loading="loading" class="submit-btn">
        登录
      </Button>

      <div class="auth-footer auth-footer-split">
        <span class="line" aria-hidden="true"></span>
        <router-link :to="registerLink">去注册</router-link>
        <span class="line" aria-hidden="true"></span>
      </div>
    </form>

    <!-- 手机登录 -->
    <form v-else @submit.prevent="onPhoneSubmit" class="auth-form">
      <div class="sub-mode-toggle">
        <button type="button" :class="{ active: phoneMode === 'otp' }" @click="phoneMode = 'otp'">验证码登录</button>
        <button type="button" :class="{ active: phoneMode === 'password' }" @click="phoneMode = 'password'">密码登录</button>
      </div>

      <Input
        v-model="phone"
        type="tel"
        placeholder="手机号"
        :error="errors.phone"
        @blur="validatePhone"
      />

      <!-- 验证码模式 -->
      <div v-if="phoneMode === 'otp'" class="sms-code-row">
        <Input
          v-model="smsCode"
          type="text"
          placeholder="验证码"
          :error="errors.smsCode"
          @blur="validateSmsCode"
        />
        <button
          type="button"
          class="sms-send-btn"
          :disabled="smsCooldown > 0 || smsSending"
          @click="sendCode('login')"
        >
          {{ smsCooldown > 0 ? `${smsCooldown}s` : '发送验证码' }}
        </button>
      </div>

      <!-- 密码模式 -->
      <Input
        v-if="phoneMode === 'password'"
        v-model="phonePassword"
        type="password"
        :show-password-toggle="true"
        placeholder="密码"
        :error="errors.phonePassword"
        @blur="validatePhonePassword"
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

  <SliderCaptcha
    :visible="showCaptcha"
    @close="showCaptcha = false"
    @verified="onCaptchaVerified"
  />
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthShell from '@/components/auth/AuthShell.vue'
import AuthTabs from '@/components/auth/AuthTabs.vue'
import SliderCaptcha from '@/components/auth/SliderCaptcha.vue'
import Input from '@/components/Input.vue'
import Button from '@/components/Button.vue'
import { authApi } from '@/api/auth'
import { clearAdminMeCache, getAdminMe } from '@/api/admin'
import { setToken } from '@/utils/token'
import { isValidEmail, isValidPassword, isValidPhone, isValidSmsCode } from '@/utils/validation'

const router = useRouter()
const route = useRoute()

const BUSINESS_HOME = '/app'
const ADMIN_HOME = '/admin/dashboard'
const tabs = [
  { label: '邮箱', value: 'email' },
  { label: '手机', value: 'phone' },
]

const activeTab = ref(route.query.tab === 'phone' ? 'phone' : 'email')
const phoneMode = ref<'otp' | 'password'>('otp')

// email fields
const email = ref('')
const emailPassword = ref('')

// phone fields
const phone = ref('')
const smsCode = ref('')
const phonePassword = ref('')

const errors = reactive<Record<string, string>>({
  email: '',
  emailPassword: '',
  phone: '',
  smsCode: '',
  phonePassword: '',
})
const errorText = ref('')
const loading = ref(false)
const showCaptcha = ref(false)
const captchaToken = ref('')

// SMS cooldown
const smsCooldown = ref(0)
const smsSending = ref(false)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})

const registerLink = computed(() => {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return redirect ? { path: '/register', query: { redirect } } : { path: '/register' }
})

// clear errorText on tab/mode switch
watch([activeTab, phoneMode], () => {
  errorText.value = ''
})

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

function validatePhonePassword(): boolean {
  if (!phonePassword.value) { errors.phonePassword = '请输入密码'; return false }
  errors.phonePassword = ''
  return true
}

// live validation
watch(email, () => { if (errors.email) validateEmail() })
watch(emailPassword, () => { if (errors.emailPassword) validateEmailPassword() })
watch(phone, () => { if (errors.phone) validatePhone() })
watch(smsCode, () => { if (errors.smsCode) validateSmsCode() })
watch(phonePassword, () => { if (errors.phonePassword) validatePhonePassword() })

// ── helpers ──

function getTokenFromResponse(res: { data?: { token?: string }; token?: string; accessToken?: string }): string | null {
  return res.data?.token ?? res.token ?? res.accessToken ?? null
}

function getSafeRedirect(): string | null {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : null
}

async function resolvePostLoginTarget(): Promise<string> {
  const redirect = getSafeRedirect()
  if (redirect) return redirect

  try {
    await getAdminMe(true)
    return ADMIN_HOME
  } catch {
    return BUSINESS_HOME
  }
}

// ── send SMS code ──

async function sendCode(purpose: 'login' | 'register') {
  if (!validatePhone()) return
  smsSending.value = true
  try {
    await authApi.sendSmsCode({ phone: phone.value, purpose })
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

// ── captcha ──

let pendingLoginType: 'email' | 'phone' = 'email'

function onEmailSubmit() {
  errorText.value = ''
  if (!validateEmail() || !validateEmailPassword()) return
  pendingLoginType = 'email'
  showCaptcha.value = true
}

function onPhoneSubmit() {
  errorText.value = ''
  if (!validatePhone()) return
  if (phoneMode.value === 'otp') {
    if (!validateSmsCode()) return
  } else {
    if (!validatePhonePassword()) return
  }
  pendingLoginType = 'phone'
  showCaptcha.value = true
}

async function onCaptchaVerified(token: string) {
  showCaptcha.value = false
  captchaToken.value = token
  if (pendingLoginType === 'email') {
    await doEmailLogin()
  } else {
    await doPhoneLogin()
  }
}

// ── submit handlers ──

async function doEmailLogin() {
  loading.value = true
  try {
    const res = await authApi.login({
      email: email.value,
      password: emailPassword.value,
      captchaToken: captchaToken.value,
    })
    const token = getTokenFromResponse(res)
    if (!token) { errorText.value = res.message ?? '登录失败，请重试'; return }
    setToken(token)
    clearAdminMeCache()
    await router.replace(await resolvePostLoginTarget())
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    errorText.value = err.response?.data?.message ?? '登录失败，请重试'
  } finally {
    loading.value = false
  }
}

async function doPhoneLogin() {
  loading.value = true
  try {
    const data = phoneMode.value === 'otp'
      ? { phone: phone.value, mode: 'otp' as const, code: smsCode.value }
      : { phone: phone.value, mode: 'password' as const, password: phonePassword.value }

    const res = await authApi.phoneLogin(data)
    const token = getTokenFromResponse(res)
    if (!token) { errorText.value = res.message ?? '登录失败，请重试'; return }
    setToken(token)
    clearAdminMeCache()
    await router.replace(await resolvePostLoginTarget())
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    errorText.value = err.response?.data?.message ?? '登录失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
@import '@/components/auth/auth-form.css';

:deep(.input-label) {
  display: none;
}

.forgot-link-row {
  display: flex;
  justify-content: flex-end;
}

.forgot-link {
  font-size: 13px;
  color: rgba(143, 216, 255, 0.85);
  text-decoration: none;
}

.forgot-link:hover {
  text-decoration: underline;
}
</style>


