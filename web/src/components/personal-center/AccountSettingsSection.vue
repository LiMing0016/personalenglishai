<template>
  <div class="settings-section">
    <h2 class="section-title">账号设置</h2>

    <!-- Info Card -->
    <div class="card info-card">
      <div class="info-header">
        <div class="info-avatar">{{ (profile?.nickname ?? '?')[0] }}</div>
        <div class="info-main">
          <div class="info-name">{{ profile?.nickname ?? '--' }}</div>
          <span class="stage-tag" v-if="profile?.studyStage">{{ getStageLabel(profile.studyStage) }}</span>
        </div>
      </div>
      <div class="info-rows">
        <div class="info-row">
          <span class="info-label">邮箱</span>
          <span class="info-value">
            {{ profile?.email ?? '--' }}
            <span class="verify-badge verified" v-if="profile?.emailVerified">已验证</span>
            <span class="verify-badge unverified" v-else>未验证</span>
          </span>
        </div>
        <div class="info-row">
          <span class="info-label">手机</span>
          <span class="info-value">
            {{ profile?.phone ?? '未绑定' }}
            <span class="verify-badge verified" v-if="profile?.phone && profile.phoneVerified">已验证</span>
            <span class="verify-badge unverified" v-else-if="profile?.phone">未验证</span>
          </span>
        </div>
        <div class="info-row">
          <span class="info-label">注册来源</span>
          <span class="info-value">{{ profile?.registerSource ?? '--' }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">注册时间</span>
          <span class="info-value">{{ profile?.createdAt ? formatDate(profile.createdAt) : '--' }}</span>
        </div>
      </div>
    </div>

    <!-- Settings Card -->
    <div class="card settings-card">
      <!-- Nickname -->
      <div class="accordion" :class="{ open: openSection === 'nickname' }">
        <button class="accordion-header" @click="toggleSection('nickname')">
          <span>修改昵称</span>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" :class="{ rotated: openSection === 'nickname' }">
            <path d="M4 5.5l3 3 3-3" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <div class="accordion-body" v-if="openSection === 'nickname'">
          <Input
            v-model="nicknameVal"
            label="昵称"
            placeholder="1-32个字符"
            :error="nicknameError"
          />
          <button class="save-btn" :disabled="nicknameSaving" @click="saveNickname">
            {{ nicknameSaving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>

      <!-- Stage -->
      <div class="accordion" :class="{ open: openSection === 'stage' }">
        <button class="accordion-header" @click="toggleSection('stage')">
          <span>学习阶段</span>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" :class="{ rotated: openSection === 'stage' }">
            <path d="M4 5.5l3 3 3-3" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <div class="accordion-body" v-if="openSection === 'stage'">
          <div class="stage-grid">
            <button
              v-for="opt in STAGE_OPTIONS"
              :key="opt.value"
              class="stage-card"
              :class="{ selected: selectedStage === opt.value }"
              @click="selectedStage = opt.value"
            >
              <span class="stage-icon">{{ opt.icon }}</span>
              <span class="stage-name">{{ opt.label }}</span>
            </button>
          </div>
          <button class="save-btn" :disabled="stageSaving" @click="saveStage">
            {{ stageSaving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>

      <!-- Password -->
      <div class="accordion" :class="{ open: openSection === 'password' }">
        <button class="accordion-header" @click="toggleSection('password')">
          <span>修改密码</span>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" :class="{ rotated: openSection === 'password' }">
            <path d="M4 5.5l3 3 3-3" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <div class="accordion-body" v-if="openSection === 'password'">
          <Input
            v-model="currentPwd"
            type="password"
            label="当前密码"
            placeholder="请输入当前密码"
            :show-password-toggle="true"
          />
          <Input
            v-model="newPwd"
            type="password"
            label="新密码"
            placeholder="至少8位，含大小写字母和数字"
            :error="newPwdError"
            :show-password-toggle="true"
          />
          <Input
            v-model="confirmPwd"
            type="password"
            label="确认新密码"
            placeholder="再次输入新密码"
            :error="confirmPwdError"
            :show-password-toggle="true"
          />
          <button class="save-btn" :disabled="pwdSaving" @click="savePassword">
            {{ pwdSaving ? '保存中...' : '修改密码' }}
          </button>
        </div>
      </div>

      <!-- Logout -->
      <div class="accordion">
        <button class="accordion-header logout-header" @click="handleLogout">
          <span>退出登录</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { userApi, type MeProfile } from '@/api/user'
import { clearToken } from '@/utils/token'
import { stageCache, clearStageCache } from '@/stores/stageCache'
import { showToast } from '@/utils/toast'
import { isValidPassword } from '@/utils/validation'
import { STAGE_OPTIONS, getStageLabel } from '@/constants/stage'
import Input from '@/components/Input.vue'

const props = defineProps<{
  profile: MeProfile
}>()

const emit = defineEmits<{
  'profile-updated': []
}>()

const router = useRouter()

// Accordion
type AccordionSection = 'nickname' | 'stage' | 'password' | null
const openSection = ref<AccordionSection>(null)

function toggleSection(section: AccordionSection) {
  openSection.value = openSection.value === section ? null : section
}

// Nickname
const nicknameVal = ref(props.profile?.nickname ?? '')
const nicknameError = ref('')
const nicknameSaving = ref(false)

watch(() => props.profile?.nickname, (v) => {
  if (v) nicknameVal.value = v
})

async function saveNickname() {
  const trimmed = nicknameVal.value.trim()
  if (!trimmed || trimmed.length < 1 || trimmed.length > 32) {
    nicknameError.value = '昵称需要1-32个字符'
    return
  }
  nicknameError.value = ''
  nicknameSaving.value = true
  try {
    await userApi.updateNickname(trimmed)
    showToast('昵称已更新', 'success')
    emit('profile-updated')
    openSection.value = null
  } catch {
    showToast('更新失败', 'error')
  } finally {
    nicknameSaving.value = false
  }
}

// Stage
const selectedStage = ref(props.profile?.studyStage ?? '')
const stageSaving = ref(false)

watch(() => props.profile?.studyStage, (v) => {
  if (v) selectedStage.value = v
})

async function saveStage() {
  if (!selectedStage.value) return
  stageSaving.value = true
  try {
    await userApi.updateStudyStage(selectedStage.value)
    stageCache.value = selectedStage.value
    showToast('学习阶段已更新', 'success')
    emit('profile-updated')
    openSection.value = null
  } catch {
    showToast('更新失败', 'error')
  } finally {
    stageSaving.value = false
  }
}

// Password
const currentPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const newPwdError = ref('')
const confirmPwdError = ref('')
const pwdSaving = ref(false)

async function savePassword() {
  newPwdError.value = ''
  confirmPwdError.value = ''

  if (!isValidPassword(newPwd.value)) {
    newPwdError.value = '密码至少8位，需包含大小写字母和数字'
    return
  }
  if (newPwd.value !== confirmPwd.value) {
    confirmPwdError.value = '两次输入的密码不一致'
    return
  }

  pwdSaving.value = true
  try {
    await userApi.changePassword(currentPwd.value, newPwd.value)
    showToast('密码已更新', 'success')
    currentPwd.value = ''
    newPwd.value = ''
    confirmPwd.value = ''
    openSection.value = null
  } catch {
    showToast('密码修改失败，请检查当前密码', 'error')
  } finally {
    pwdSaving.value = false
  }
}

// Logout
function handleLogout() {
  clearToken()
  clearStageCache()
  router.push('/login')
}

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.settings-section {
  max-width: 640px;
}

.section-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 24px;
}

.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  padding: 24px;
  margin-bottom: 20px;
}

/* Info Card */
.info-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.info-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #047857;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 600;
  flex-shrink: 0;
}

.info-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-name {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

.stage-tag {
  display: inline-block;
  padding: 2px 10px;
  background: #ecfdf5;
  color: #047857;
  font-size: 12px;
  font-weight: 500;
  border-radius: 999px;
  width: fit-content;
}

.info-rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.info-label {
  width: 72px;
  font-size: 13px;
  color: #94a3b8;
  flex-shrink: 0;
}

.info-value {
  font-size: 14px;
  color: #334155;
  display: flex;
  align-items: center;
  gap: 8px;
}

.verify-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
}
.verify-badge.verified {
  background: #ecfdf5;
  color: #047857;
}
.verify-badge.unverified {
  background: #fef3c7;
  color: #92400e;
}

/* Settings Card */
.settings-card {
  padding: 0;
  overflow: hidden;
}

.accordion {
  border-bottom: 1px solid #e5e7eb;
}
.accordion:last-child {
  border-bottom: none;
}

.accordion-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 18px 24px;
  font-size: 14px;
  font-weight: 500;
  color: #334155;
  background: none;
  border: none;
  cursor: pointer;
  transition: background 0.12s;
}
.accordion-header:hover {
  background: #f8fafc;
}

.accordion-header svg {
  transition: transform 0.2s;
}
.accordion-header svg.rotated {
  transform: rotate(180deg);
}

.logout-header {
  color: #ef4444;
}
.logout-header:hover {
  background: #fef2f2;
}

.accordion-body {
  padding: 0 24px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.save-btn {
  align-self: flex-start;
  padding: 10px 28px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: #047857;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  margin-top: 4px;
}
.save-btn:hover:not(:disabled) {
  background: #065f46;
}
.save-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Stage Grid */
.stage-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.stage-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 8px;
  background: #f8fafc;
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.stage-card:hover {
  border-color: #047857;
}
.stage-card.selected {
  border-color: #047857;
  background: #ecfdf5;
}

.stage-icon {
  font-size: 22px;
}
.stage-name {
  font-size: 13px;
  color: #334155;
  font-weight: 500;
}

@media (max-width: 640px) {
  .stage-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .info-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>
