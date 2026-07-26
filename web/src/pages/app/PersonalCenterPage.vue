<template>
  <div class="personal-center-page">
    <div class="personal-center-surface">
      <header class="profile-header">
        <div class="profile-identity">
          <button
            class="avatar"
            type="button"
            :aria-label="editingNickname ? '正在编辑昵称' : '编辑昵称'"
            @click="startEditNickname"
          >
            {{ avatarInitial }}
          </button>

          <div class="profile-copy">
            <p class="profile-eyebrow">个人中心</p>
            <div v-if="!editingNickname" class="nickname-row">
              <h1>{{ profile?.nickname ?? (profileLoading ? '正在加载' : '英语学习者') }}</h1>
              <button class="edit-name-button" type="button" title="编辑昵称" @click="startEditNickname">
                <Pencil :size="15" :stroke-width="1.8" />
              </button>
            </div>
            <div v-else class="nickname-edit">
              <input
                ref="nicknameInputRef"
                v-model="nicknameDraft"
                class="nickname-input"
                maxlength="32"
                aria-label="昵称"
                @keydown.enter.prevent="confirmNickname"
                @keydown.escape.prevent="cancelEditNickname"
                @blur="confirmNickname"
              />
            </div>

            <div class="profile-meta">
              <div ref="stageDropdownRef" class="stage-switcher">
                <button
                  class="meta-control stage-button"
                  type="button"
                  :aria-expanded="stageDropdownOpen"
                  @click="stageDropdownOpen = !stageDropdownOpen"
                >
                  <GraduationCap :size="15" :stroke-width="1.8" />
                  {{ currentStageLabel }}
                  <ChevronDown
                    :size="14"
                    :stroke-width="1.8"
                    :class="{ rotated: stageDropdownOpen }"
                  />
                </button>
                <div v-if="stageDropdownOpen" class="stage-dropdown">
                  <button
                    v-for="option in STAGE_OPTIONS"
                    :key="option.value"
                    class="stage-option"
                    :class="{ active: option.value === profile?.studyStage }"
                    type="button"
                    @click="selectStage(option.value)"
                  >
                    {{ option.label }}
                    <Check v-if="option.value === profile?.studyStage" :size="14" />
                  </button>
                </div>
              </div>

              <span v-if="profile?.createdAt" class="meta-item">
                <CalendarDays :size="15" :stroke-width="1.8" />
                注册于 {{ formatDate(profile.createdAt) }}
              </span>
              <span class="meta-item" :class="{ verified: profile?.emailVerified }">
                <BadgeCheck v-if="profile?.emailVerified" :size="15" :stroke-width="1.8" />
                <Mail v-else :size="15" :stroke-width="1.8" />
                {{ profile?.emailVerified ? '邮箱已验证' : '邮箱待验证' }}
              </span>
            </div>
          </div>
        </div>

        <RouterLink class="security-link" :to="{ path: '/app/me', query: { tab: 'security' } }">
          <ShieldCheck :size="17" :stroke-width="1.8" />
          管理账号安全
          <ArrowRight :size="15" :stroke-width="1.8" />
        </RouterLink>
      </header>

      <nav class="personal-tabs" aria-label="个人中心栏目">
        <button
          v-for="tab in PERSONAL_CENTER_TABS"
          :key="tab.key"
          type="button"
          class="tab-button"
          :class="{ active: activeSection === tab.key }"
          :aria-current="activeSection === tab.key ? 'page' : undefined"
          @click="switchSection(tab.key)"
        >
          {{ tab.label }}
        </button>
      </nav>

      <main class="personal-content">
        <OverviewSection
          v-if="activeSection === 'overview'"
          :stage-label="currentStageLabel"
          :preview-mode="isPreviewMode"
        />
        <MyEssaysSection v-else-if="activeSection === 'records'" />
        <WritingAssetsSection v-else-if="activeSection === 'assets'" />
        <AbilityRadarSection v-else-if="activeSection === 'profile'" />
        <SubscriptionSection v-else-if="activeSection === 'subscription'" />
        <AccountSettingsSection
          v-else-if="activeSection === 'security' && profile"
          :profile="profile"
          @profile-updated="refreshProfile"
        />
        <div v-else class="content-loading" role="status">
          <span></span>
          <p>正在加载账号信息</p>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { onClickOutside } from '@vueuse/core'
import {
  ArrowRight,
  BadgeCheck,
  CalendarDays,
  Check,
  ChevronDown,
  GraduationCap,
  Mail,
  Pencil,
  ShieldCheck,
} from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'

import { userApi, type MeProfile } from '@/api/user'
import AbilityRadarSection from '@/components/personal-center/AbilityRadarSection.vue'
import AccountSettingsSection from '@/components/personal-center/AccountSettingsSection.vue'
import MyEssaysSection from '@/components/personal-center/MyEssaysSection.vue'
import OverviewSection from '@/components/personal-center/OverviewSection.vue'
import SubscriptionSection from '@/components/personal-center/SubscriptionSection.vue'
import WritingAssetsSection from '@/components/personal-center/WritingAssetsSection.vue'
import { STAGE_OPTIONS, getStageLabel } from '@/constants/stage'
import { stageCache } from '@/stores/stageCache'
import { showToast } from '@/utils/toast'

import {
  PERSONAL_CENTER_TABS,
  parsePersonalCenterSection,
  type PersonalCenterSection,
} from './personalCenterModel'

const router = useRouter()
const route = useRoute()

const profile = ref<MeProfile | null>(null)
const profileLoading = ref(true)
const activeSection = ref<PersonalCenterSection>(
  parsePersonalCenterSection(route.query.tab as string | string[] | null | undefined),
)
const editingNickname = ref(false)
const nicknameDraft = ref('')
const nicknameInputRef = ref<HTMLInputElement | null>(null)
const stageDropdownOpen = ref(false)
const stageDropdownRef = ref<HTMLElement | null>(null)
const isPreviewMode = computed(
  () => import.meta.env.DEV && route.meta.personalCenterPreview === true,
)

const avatarInitial = computed(() => {
  const nickname = profile.value?.nickname?.trim()
  const email = profile.value?.email?.trim()
  return nickname?.[0]?.toUpperCase() ?? email?.[0]?.toUpperCase() ?? '?'
})
const currentStageLabel = computed(() => getStageLabel(profile.value?.studyStage))

function switchSection(key: PersonalCenterSection) {
  activeSection.value = key
  void router.replace({ query: { ...route.query, tab: key } })
}

function formatDate(dateString: string) {
  const date = new Date(dateString)
  if (Number.isNaN(date.getTime())) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function startEditNickname() {
  nicknameDraft.value = profile.value?.nickname ?? ''
  editingNickname.value = true
  void nextTick(() => nicknameInputRef.value?.focus())
}

function cancelEditNickname() {
  editingNickname.value = false
}

async function confirmNickname() {
  if (!editingNickname.value) return
  const trimmed = nicknameDraft.value.trim()
  if (!trimmed || trimmed === profile.value?.nickname) {
    editingNickname.value = false
    return
  }

  try {
    await userApi.updateNickname(trimmed)
    if (profile.value) profile.value.nickname = trimmed
    showToast('昵称已更新', 'success')
  } catch {
    showToast('昵称更新失败', 'error')
  } finally {
    editingNickname.value = false
  }
}

async function selectStage(value: string) {
  try {
    await userApi.updateStudyStage(value)
    if (profile.value) profile.value.studyStage = value
    stageCache.value = value
    showToast('学习阶段已更新', 'success')
  } catch {
    showToast('学习阶段更新失败', 'error')
  } finally {
    stageDropdownOpen.value = false
  }
}

onClickOutside(stageDropdownRef, () => {
  stageDropdownOpen.value = false
})

async function refreshProfile() {
  profileLoading.value = true
  if (isPreviewMode.value) {
    profile.value = {
      nickname: 'Catalina',
      email: 'catalina@example.com',
      studyStage: 'ielts',
      emailVerified: true,
      createdAt: '2026-03-15T09:30:00+08:00',
    }
    profileLoading.value = false
    return
  }

  try {
    const response = await userApi.getMyProfile()
    profile.value = response.data ?? null
    if (response.data?.studyStage) {
      stageCache.value = response.data.studyStage
    }
  } catch {
    showToast('加载用户信息失败', 'error')
  } finally {
    profileLoading.value = false
  }
}

watch(
  () => route.query.tab,
  (value) => {
    activeSection.value = parsePersonalCenterSection(
      value as string | string[] | null | undefined,
    )
  },
)

onMounted(refreshProfile)
</script>

<style scoped>
.personal-center-page {
  min-height: 100%;
  overflow-y: auto;
  background:
    radial-gradient(circle at 86% 0%, rgba(209, 231, 244, 0.5), transparent 30%),
    #f5f8fb;
  color: #10243f;
}

.personal-center-surface {
  width: min(100%, 1540px);
  min-height: 100vh;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 0 0 1px rgba(207, 219, 232, 0.72);
}

.profile-header {
  display: flex;
  min-height: 148px;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 28px clamp(28px, 4vw, 68px) 24px;
  background:
    linear-gradient(110deg, rgba(238, 248, 246, 0.9), rgba(255, 255, 255, 0.92) 48%),
    #fff;
}

.profile-identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 22px;
}

.avatar {
  display: grid;
  width: 76px;
  height: 76px;
  flex: 0 0 auto;
  place-items: center;
  border: 5px solid #fff;
  border-radius: 50%;
  background: linear-gradient(145deg, #0d8e68, #00694c);
  box-shadow:
    0 0 0 1px rgba(7, 112, 81, 0.14),
    0 12px 26px rgba(4, 120, 87, 0.18);
  color: #fff;
  cursor: pointer;
  font-size: 28px;
  font-weight: 750;
}

.profile-copy {
  min-width: 0;
}

.profile-eyebrow {
  margin: 0 0 5px;
  color: #6b7f95;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.nickname-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nickname-row h1 {
  overflow: hidden;
  margin: 0;
  color: #10243f;
  font-size: clamp(24px, 2.2vw, 34px);
  font-weight: 760;
  letter-spacing: -0.035em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.edit-name-button {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid #dbe5ee;
  border-radius: 9px;
  background: #fff;
  color: #70839a;
  cursor: pointer;
}

.edit-name-button:hover {
  border-color: #8ab6a8;
  color: #047857;
}

.nickname-edit {
  margin: 2px 0 4px;
}

.nickname-input {
  width: min(100%, 320px);
  height: 42px;
  border: 1px solid #72a895;
  border-radius: 10px;
  padding: 0 12px;
  outline: none;
  color: #10243f;
  font-size: 18px;
  font-weight: 700;
  box-shadow: 0 0 0 3px rgba(4, 120, 87, 0.1);
}

.profile-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 18px;
  margin-top: 12px;
}

.meta-item,
.meta-control {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #667a91;
  font-size: 13px;
}

.meta-item.verified {
  color: #087a59;
}

.stage-switcher {
  position: relative;
}

.stage-button {
  min-height: 32px;
  border: 1px solid #dbe5ee;
  border-radius: 9px;
  padding: 0 10px;
  background: #fff;
  cursor: pointer;
}

.stage-button:hover {
  border-color: #9dbbad;
  color: #047857;
}

.stage-button .rotated {
  transform: rotate(180deg);
}

.stage-dropdown {
  position: absolute;
  z-index: 20;
  top: calc(100% + 8px);
  left: 0;
  display: grid;
  width: 176px;
  gap: 2px;
  border: 1px solid #dbe5ee;
  border-radius: 12px;
  padding: 6px;
  background: #fff;
  box-shadow: 0 18px 36px rgba(27, 50, 75, 0.14);
}

.stage-option {
  display: flex;
  min-height: 36px;
  align-items: center;
  justify-content: space-between;
  border: 0;
  border-radius: 8px;
  padding: 0 10px;
  background: transparent;
  color: #43586f;
  cursor: pointer;
  text-align: left;
}

.stage-option:hover,
.stage-option.active {
  background: #edf8f4;
  color: #047857;
}

.stage-option.active {
  font-weight: 700;
}

.security-link {
  display: inline-flex;
  min-height: 42px;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  border: 1px solid #d7e2ec;
  border-radius: 12px;
  padding: 0 14px;
  background: rgba(255, 255, 255, 0.78);
  color: #41586f;
  font-size: 13px;
  font-weight: 650;
  text-decoration: none;
  transition: border-color 160ms ease, color 160ms ease;
}

.security-link:hover {
  border-color: #8ab6a8;
  color: #047857;
}

.personal-tabs {
  display: flex;
  overflow-x: auto;
  border-top: 1px solid #e8eef4;
  border-bottom: 1px solid #dfe7ef;
  padding: 0 clamp(28px, 4vw, 68px);
  background: rgba(255, 255, 255, 0.94);
  scrollbar-width: none;
}

.personal-tabs::-webkit-scrollbar {
  display: none;
}

.tab-button {
  position: relative;
  min-height: 64px;
  flex: 0 0 auto;
  border: 0;
  padding: 0 clamp(18px, 2.1vw, 34px);
  background: transparent;
  color: #53677e;
  cursor: pointer;
  font-size: 15px;
  font-weight: 620;
  white-space: nowrap;
}

.tab-button:first-child {
  padding-left: 0;
}

.tab-button::after {
  position: absolute;
  right: clamp(18px, 2.1vw, 34px);
  bottom: -1px;
  left: clamp(18px, 2.1vw, 34px);
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: transparent;
  content: '';
}

.tab-button:first-child::after {
  left: 0;
}

.tab-button:hover,
.tab-button.active {
  color: #047857;
}

.tab-button.active {
  font-weight: 750;
}

.tab-button.active::after {
  background: #0a805e;
}

.personal-content {
  padding: 34px clamp(28px, 4vw, 68px) 64px;
}

.content-loading {
  display: grid;
  min-height: 260px;
  place-items: center;
  align-content: center;
  gap: 14px;
  border: 1px solid #dfe7ef;
  border-radius: 20px;
  background: #fff;
  color: #6b7e93;
}

.content-loading span {
  width: 28px;
  height: 28px;
  border: 3px solid #d8e5e0;
  border-top-color: #047857;
  border-radius: 50%;
  animation: spin 800ms linear infinite;
}

.content-loading p {
  margin: 0;
  font-size: 14px;
}

.avatar:focus-visible,
.edit-name-button:focus-visible,
.stage-button:focus-visible,
.stage-option:focus-visible,
.security-link:focus-visible,
.tab-button:focus-visible {
  outline: 3px solid rgba(4, 120, 87, 0.22);
  outline-offset: 3px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 900px) {
  .profile-header {
    align-items: flex-start;
  }

  .security-link {
    padding: 0 12px;
  }
}

@media (max-width: 720px) {
  .profile-header {
    min-height: 0;
    flex-direction: column;
    padding: 24px 20px 20px;
  }

  .profile-identity {
    width: 100%;
    align-items: flex-start;
    gap: 16px;
  }

  .avatar {
    width: 60px;
    height: 60px;
    border-width: 4px;
    font-size: 22px;
  }

  .profile-copy {
    flex: 1;
  }

  .security-link {
    align-self: flex-end;
  }

  .personal-tabs {
    padding: 0 20px;
  }

  .tab-button {
    min-height: 56px;
    padding: 0 18px;
    font-size: 14px;
  }

  .tab-button::after {
    right: 18px;
    left: 18px;
  }

  .personal-content {
    padding: 24px 20px 48px;
  }
}

@media (max-width: 480px) {
  .profile-meta {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .security-link {
    width: 100%;
    justify-content: center;
  }
}
</style>
