<template>
  <div class="profile-page">
    <h1 class="page-title">个人中心</h1>

    <!-- Loading / Error -->
    <div v-if="loading" class="card card-status">加载中...</div>
    <div v-else-if="fetchError" class="card card-error">{{ fetchError }}</div>

    <template v-else>
      <!-- Card 1: Personal info -->
      <div class="card info-card">
        <div class="avatar-row">
          <div class="avatar">{{ avatarLetter }}</div>
          <div class="user-meta">
            <span class="user-name">{{ profile?.nickname || profile?.email || '用户' }}</span>
            <span v-if="profile?.studyStage" class="stage-tag">{{ getStageLabel(profile.studyStage) }}</span>
          </div>
        </div>

        <div class="info-list">
          <div class="info-row">
            <span class="info-label">邮箱</span>
            <span class="info-value">
              {{ profile?.email || '--' }}
              <span v-if="profile?.email" class="badge" :class="profile.emailVerified ? 'badge-ok' : 'badge-no'">
                {{ profile.emailVerified ? '已验证' : '未验证' }}
              </span>
            </span>
          </div>
          <div class="info-row">
            <span class="info-label">手机</span>
            <span class="info-value">
              {{ profile?.phone || '--' }}
              <span v-if="profile?.phone" class="badge" :class="profile.phoneVerified ? 'badge-ok' : 'badge-no'">
                {{ profile.phoneVerified ? '已验证' : '未验证' }}
              </span>
            </span>
          </div>
          <div class="info-row">
            <span class="info-label">注册方式</span>
            <span class="info-value">{{ registerSourceLabel }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">注册时间</span>
            <span class="info-value">{{ profile?.createdAt || '--' }}</span>
          </div>
        </div>
      </div>

      <!-- Card 2: Account settings -->
      <div class="card settings-card">
        <h2 class="card-heading">账号设置</h2>

        <!-- Nickname -->
        <div class="setting-item" :class="{ open: openSection === 'nickname' }">
          <button class="setting-header" @click="toggleSection('nickname')">
            <span>修改昵称</span>
            <span class="arrow">&#9654;</span>
          </button>
          <div v-if="openSection === 'nickname'" class="setting-body">
            <Input
              v-model="nicknameInput"
              label="新昵称"
              placeholder="1-32 个字符"
              :error="nicknameError"
            />
            <div class="btn-row">
              <button class="btn btn-primary" :disabled="nicknameSaving" @click="saveNickname">
                {{ nicknameSaving ? '保存中...' : '保存' }}
              </button>
              <button class="btn btn-cancel" @click="openSection = null">取消</button>
            </div>
          </div>
        </div>

        <!-- Stage -->
        <div class="setting-item" :class="{ open: openSection === 'stage' }">
          <button class="setting-header" @click="toggleSection('stage')">
            <span>切换学习阶段</span>
            <span class="arrow">&#9654;</span>
          </button>
          <div v-if="openSection === 'stage'" class="setting-body">
            <div class="stage-grid">
              <button
                v-for="s in STAGE_OPTIONS"
                :key="s.value"
                class="stage-card"
                :class="{ active: pendingStage === s.value }"
                @click="pendingStage = s.value"
              >
                <span class="stage-icon">{{ s.icon }}</span>
                <span class="stage-label">{{ s.label }}</span>
              </button>
            </div>
            <div class="btn-row">
              <button class="btn btn-primary" :disabled="stageSaving || !pendingStage" @click="saveStage">
                {{ stageSaving ? '保存中...' : '确认' }}
              </button>
              <button class="btn btn-cancel" @click="openSection = null">取消</button>
            </div>
          </div>
        </div>

        <!-- Password -->
        <div class="setting-item" :class="{ open: openSection === 'password' }">
          <button class="setting-header" @click="toggleSection('password')">
            <span>修改密码</span>
            <span class="arrow">&#9654;</span>
          </button>
          <div v-if="openSection === 'password'" class="setting-body">
            <Input
              v-model="currentPwd"
              type="password"
              label="当前密码"
              :show-password-toggle="true"
              :error="pwdErrors.current"
            />
            <Input
              v-model="newPwd"
              type="password"
              label="新密码"
              placeholder="至少 8 位，含大小写字母和数字"
              :show-password-toggle="true"
              :error="pwdErrors.new"
            />
            <Input
              v-model="confirmPwd"
              type="password"
              label="确认新密码"
              :show-password-toggle="true"
              :error="pwdErrors.confirm"
            />
            <div class="btn-row">
              <button class="btn btn-primary" :disabled="pwdSaving" @click="savePassword">
                {{ pwdSaving ? '修改中...' : '确认修改' }}
              </button>
              <button class="btn btn-cancel" @click="openSection = null">取消</button>
            </div>
          </div>
        </div>

        <!-- Logout -->
        <div class="setting-item setting-logout">
          <button class="setting-header logout-header" @click="logout">
            <span class="logout-text">退出登录</span>
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { userApi, type MeProfile } from '@/api/user'
import { clearToken } from '@/utils/token'
import { clearStageCache, stageCache } from '@/stores/stageCache'
import { showToast } from '@/utils/toast'
import { isValidPassword } from '@/utils/validation'
import { STAGE_OPTIONS, getStageLabel } from '@/constants/stage'
import Input from '@/components/Input.vue'

const router = useRouter()
const profile = ref<MeProfile | null>(null)
const loading = ref(true)
const fetchError = ref('')

// accordion
type Section = 'nickname' | 'stage' | 'password' | null
const openSection = ref<Section>(null)

// nickname
const nicknameInput = ref('')
const nicknameError = ref('')
const nicknameSaving = ref(false)

// stage
const pendingStage = ref<string | null>(null)
const stageSaving = ref(false)

// password
const currentPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const pwdErrors = ref({ current: '', new: '', confirm: '' })
const pwdSaving = ref(false)

const avatarLetter = computed(() => {
  const name = profile.value?.nickname || profile.value?.email || ''
  return name.charAt(0).toUpperCase() || 'U'
})

const registerSourceLabel = computed(() => {
  const src = profile.value?.registerSource
  if (!src) return '--'
  const map: Record<string, string> = { email: '邮箱注册', phone: '手机注册' }
  return map[src] ?? src
})

onMounted(async () => {
  try {
    const res = await userApi.getMyProfile()
    if (res.data) {
      profile.value = res.data
    }
  } catch {
    fetchError.value = '获取个人信息失败'
  } finally {
    loading.value = false
  }
})

function toggleSection(section: Section) {
  if (openSection.value === section) {
    openSection.value = null
    return
  }
  // reset fields when opening
  if (section === 'nickname') {
    nicknameInput.value = profile.value?.nickname || ''
    nicknameError.value = ''
  } else if (section === 'stage') {
    pendingStage.value = profile.value?.studyStage || null
  } else if (section === 'password') {
    currentPwd.value = ''
    newPwd.value = ''
    confirmPwd.value = ''
    pwdErrors.value = { current: '', new: '', confirm: '' }
  }
  openSection.value = section
}

async function saveNickname() {
  const trimmed = nicknameInput.value.trim()
  if (!trimmed || trimmed.length > 32) {
    nicknameError.value = '昵称长度 1-32 个字符'
    return
  }
  nicknameError.value = ''
  nicknameSaving.value = true
  try {
    await userApi.updateNickname(trimmed)
    profile.value = { ...profile.value!, nickname: trimmed }
    openSection.value = null
    showToast('昵称修改成功', 'success')
  } catch {
    showToast('昵称修改失败', 'error')
  } finally {
    nicknameSaving.value = false
  }
}

async function saveStage() {
  if (!pendingStage.value) return
  stageSaving.value = true
  try {
    await userApi.updateStudyStage(pendingStage.value)
    profile.value = { ...profile.value!, studyStage: pendingStage.value }
    stageCache.value = pendingStage.value
    openSection.value = null
    showToast('学习阶段已更新', 'success')
  } catch {
    showToast('保存学段失败，请重试', 'error')
  } finally {
    stageSaving.value = false
  }
}

async function savePassword() {
  const errors = { current: '', new: '', confirm: '' }
  if (!currentPwd.value) errors.current = '请输入当前密码'
  if (!newPwd.value) {
    errors.new = '请输入新密码'
  } else if (!isValidPassword(newPwd.value)) {
    errors.new = '至少 8 位，含大小写字母和数字'
  }
  if (!confirmPwd.value) {
    errors.confirm = '请确认新密码'
  } else if (newPwd.value !== confirmPwd.value) {
    errors.confirm = '两次密码不一致'
  }
  pwdErrors.value = errors
  if (errors.current || errors.new || errors.confirm) return

  pwdSaving.value = true
  try {
    await userApi.changePassword(currentPwd.value, newPwd.value)
    showToast('密码修改成功，请重新登录', 'success')
    clearToken()
    clearStageCache()
    router.replace('/login')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '密码修改失败'
    showToast(msg, 'error')
  } finally {
    pwdSaving.value = false
  }
}

function logout() {
  clearToken()
  clearStageCache()
  router.replace('/login')
}
</script>

<style scoped>
.profile-page {
  max-width: 560px;
  margin: 0 auto;
  padding: 32px 24px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 24px;
}

.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  padding: 24px;
  margin-bottom: 20px;
}

.card-status,
.card-error {
  font-size: 14px;
  color: #6b7280;
  text-align: center;
  padding: 20px 0;
}

.card-error {
  color: #991b1b;
}

/* ── Info card ── */
.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f3f4f6;
}

.avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: #047857;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-name {
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}

.stage-tag {
  display: inline-block;
  font-size: 12px;
  font-weight: 500;
  color: #047857;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 999px;
  padding: 2px 10px;
  width: fit-content;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-label {
  font-size: 14px;
  color: #6b7280;
  flex-shrink: 0;
}

.info-value {
  font-size: 14px;
  color: #111827;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.badge {
  font-size: 11px;
  font-weight: 500;
  padding: 1px 6px;
  border-radius: 999px;
}

.badge-ok {
  color: #047857;
  background: #ecfdf5;
}

.badge-no {
  color: #92400e;
  background: #fef3c7;
}

/* ── Settings card ── */
.card-heading {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 12px;
}

.setting-item {
  border-top: 1px solid #f3f4f6;
}

.setting-item:last-child {
  border-bottom: none;
}

.setting-header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  font-size: 14px;
  font-weight: 500;
  color: #111827;
  background: none;
  border: none;
  cursor: pointer;
}

.setting-header:hover {
  color: #047857;
}

.arrow {
  font-size: 10px;
  color: #9ca3af;
  transition: transform 0.2s;
}

.setting-item.open .arrow {
  transform: rotate(90deg);
}

.setting-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 0 0 16px;
}

.btn-row {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}

.btn {
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: #047857;
  color: #fff;
  border-color: #047857;
}

.btn-primary:hover:not(:disabled) {
  background: #065f46;
}

.btn-cancel {
  background: #fff;
  color: #6b7280;
  border-color: #e5e7eb;
}

.btn-cancel:hover {
  background: #f9fafb;
}

/* ── Stage grid ── */
.stage-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.stage-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 14px 6px;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.stage-card:hover {
  border-color: #a7f3d0;
  background: #f0fdf4;
}

.stage-card.active {
  border-color: #047857;
  background: #ecfdf5;
  box-shadow: 0 0 0 3px rgba(4, 120, 87, 0.12);
}

.stage-icon {
  font-size: 22px;
  line-height: 1;
}

.stage-label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

/* ── Logout ── */
.setting-logout .logout-header {
  justify-content: center;
}

.logout-text {
  color: #991b1b;
  font-weight: 500;
}

.setting-logout .setting-header:hover .logout-text {
  color: #7f1d1d;
}

.setting-logout .arrow {
  display: none;
}

@media (max-width: 480px) {
  .stage-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
