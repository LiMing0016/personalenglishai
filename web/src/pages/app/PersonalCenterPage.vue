<template>
  <div class="pc-layout">
    <!-- Left Sidebar -->
    <aside class="pc-sidebar">
      <button class="back-btn" @click="router.push('/app')">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M10 12L6 8l4-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        返回
      </button>

      <!-- User Section -->
      <div class="user-section">
        <div class="avatar" @click="startEditNickname">
          {{ (profile?.nickname ?? '?')[0] }}
        </div>

        <div class="nickname-row" v-if="!editingNickname">
          <span class="nickname-text">{{ profile?.nickname ?? '加载中...' }}</span>
          <button class="icon-btn" @click="startEditNickname" title="编辑昵称">
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
              <path d="M11.5 1.5l3 3L5 14H2v-3L11.5 1.5z" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </div>
        <div class="nickname-edit" v-else>
          <input
            ref="nicknameInputRef"
            v-model="nicknameDraft"
            class="nickname-input"
            maxlength="32"
            @keydown.enter="confirmNickname"
            @keydown.escape="cancelEditNickname"
            @blur="confirmNickname"
          />
        </div>

        <!-- Stage Switcher -->
        <div class="stage-switcher" ref="stageDropdownRef">
          <button class="stage-btn" @click="stageDropdownOpen = !stageDropdownOpen">
            <span>{{ currentStageLabel }}</span>
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" :class="{ rotated: stageDropdownOpen }">
              <path d="M3 5l3 3 3-3" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
          <div class="stage-dropdown" v-if="stageDropdownOpen">
            <button
              v-for="opt in STAGE_OPTIONS"
              :key="opt.value"
              class="stage-option"
              :class="{ active: opt.value === profile?.studyStage }"
              @click="selectStage(opt.value)"
            >
              {{ opt.icon }} {{ opt.label }}
            </button>
          </div>
        </div>

        <div class="register-time" v-if="profile?.createdAt">
          注册于 {{ formatDate(profile.createdAt) }}
        </div>
      </div>

      <!-- Navigation -->
      <nav class="pc-nav">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: activeSection === item.key }"
          @click="switchSection(item.key)"
        >
          <component :is="item.icon" />
          <span>{{ item.label }}</span>
        </button>
      </nav>
    </aside>

    <!-- Right Content -->
    <main class="pc-content">
      <OverviewSection v-if="activeSection === 'overview'" />
      <MyEssaysSection v-else-if="activeSection === 'essays'" />
      <AbilityRadarSection v-else-if="activeSection === 'radar'" />
      <ReferralSection v-else-if="activeSection === 'referral'" />
      <AccountSettingsSection
        v-else-if="activeSection === 'settings'"
        :profile="profile!"
        @profile-updated="refreshProfile"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h, watch, nextTick } from 'vue'
import { onClickOutside } from '@vueuse/core'
import { useRouter, useRoute } from 'vue-router'
import { userApi, type MeProfile } from '@/api/user'
import { STAGE_OPTIONS, getStageLabel } from '@/constants/stage'
import { stageCache } from '@/stores/stageCache'
import { showToast } from '@/utils/toast'
import OverviewSection from '@/components/personal-center/OverviewSection.vue'
import MyEssaysSection from '@/components/personal-center/MyEssaysSection.vue'
import AbilityRadarSection from '@/components/personal-center/AbilityRadarSection.vue'
import AccountSettingsSection from '@/components/personal-center/AccountSettingsSection.vue'
import ReferralSection from '@/components/personal-center/ReferralSection.vue'

type SectionKey = 'overview' | 'essays' | 'radar' | 'referral' | 'settings'

const router = useRouter()
const route = useRoute()

const profile = ref<MeProfile | null>(null)
const activeSection = ref<SectionKey>('overview')
const editingNickname = ref(false)
const nicknameDraft = ref('')
const nicknameInputRef = ref<HTMLInputElement | null>(null)
const stageDropdownOpen = ref(false)
const stageDropdownRef = ref<HTMLElement | null>(null)

const currentStageLabel = computed(() => getStageLabel(profile.value?.studyStage))

// Nav icons using render functions
const IconGrid = () =>
  h('svg', { width: 18, height: 18, viewBox: '0 0 18 18', fill: 'none', innerHTML: '<rect x="1" y="1" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.3"/><rect x="10" y="1" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.3"/><rect x="1" y="10" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.3"/><rect x="10" y="10" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.3"/>' })

const IconFile = () =>
  h('svg', { width: 18, height: 18, viewBox: '0 0 18 18', fill: 'none', innerHTML: '<path d="M10 1H4a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7l-6-6z" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/><path d="M10 1v6h6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>' })

const IconStar = () =>
  h('svg', { width: 18, height: 18, viewBox: '0 0 18 18', fill: 'none', innerHTML: '<path d="M9 1l2.47 5.01L17 6.76l-4 3.9.94 5.5L9 13.77l-4.94 2.4.94-5.5-4-3.9 5.53-.75L9 1z" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>' })

const IconGift = () =>
  h('svg', { width: 18, height: 18, viewBox: '0 0 18 18', fill: 'none', innerHTML: '<rect x="1.5" y="7" width="15" height="9" rx="1.5" stroke="currentColor" stroke-width="1.3"/><path d="M9 7v9M1.5 10h15" stroke="currentColor" stroke-width="1.3"/><path d="M9 7C9 7 9 4 6.5 3s-4 1-3 2.5S9 7 9 7zM9 7c0 0 0-3 2.5-4s4 1 3 2.5S9 7 9 7z" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>' })

const IconGear = () =>
  h('svg', { width: 18, height: 18, viewBox: '0 0 18 18', fill: 'none', innerHTML: '<circle cx="9" cy="9" r="2.5" stroke="currentColor" stroke-width="1.3"/><path d="M14.7 11.1a1.2 1.2 0 00.24 1.32l.04.04a1.45 1.45 0 11-2.06 2.06l-.04-.04a1.2 1.2 0 00-1.32-.24 1.2 1.2 0 00-.73 1.1v.12a1.45 1.45 0 01-2.9 0v-.06a1.2 1.2 0 00-.79-1.1 1.2 1.2 0 00-1.32.24l-.04.04a1.45 1.45 0 11-2.06-2.06l.04-.04a1.2 1.2 0 00.24-1.32 1.2 1.2 0 00-1.1-.73H3.45a1.45 1.45 0 010-2.9h.06a1.2 1.2 0 001.1-.79 1.2 1.2 0 00-.24-1.32l-.04-.04a1.45 1.45 0 112.06-2.06l.04.04a1.2 1.2 0 001.32.24h.06a1.2 1.2 0 00.73-1.1V3.45a1.45 1.45 0 012.9 0v.06a1.2 1.2 0 00.73 1.1 1.2 1.2 0 001.32-.24l.04-.04a1.45 1.45 0 112.06 2.06l-.04.04a1.2 1.2 0 00-.24 1.32v.06a1.2 1.2 0 001.1.73h.12a1.45 1.45 0 010 2.9h-.06a1.2 1.2 0 00-1.1.73z" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>' })

const navItems = [
  { key: 'overview' as SectionKey, label: '综合能力', icon: IconGrid },
  { key: 'essays' as SectionKey, label: '我的作文', icon: IconFile },
  { key: 'radar' as SectionKey, label: '能力雷达', icon: IconStar },
  { key: 'referral' as SectionKey, label: '邀请激励', icon: IconGift },
  { key: 'settings' as SectionKey, label: '账号设置', icon: IconGear },
]

function switchSection(key: SectionKey) {
  activeSection.value = key
  router.replace({ query: { tab: key } })
}

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// Nickname editing
function startEditNickname() {
  nicknameDraft.value = profile.value?.nickname ?? ''
  editingNickname.value = true
  nextTick(() => nicknameInputRef.value?.focus())
}

function cancelEditNickname() {
  editingNickname.value = false
}

async function confirmNickname() {
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
  }
  editingNickname.value = false
}

// Stage dropdown
async function selectStage(value: string) {
  try {
    await userApi.updateStudyStage(value)
    if (profile.value) profile.value.studyStage = value
    stageCache.value = value
    showToast('学习阶段已更新', 'success')
  } catch {
    showToast('更新失败', 'error')
  }
  stageDropdownOpen.value = false
}

onClickOutside(stageDropdownRef, () => {
  stageDropdownOpen.value = false
})

async function refreshProfile() {
  try {
    const res = await userApi.getMyProfile()
    if (res.data) profile.value = res.data
  } catch {
    // silent
  }
}

// Init
onMounted(async () => {
  // Read tab from query
  const tab = route.query.tab as string | undefined
  if (tab && ['overview', 'essays', 'radar', 'referral', 'settings'].includes(tab)) {
    activeSection.value = tab as SectionKey
  }

  try {
    const res = await userApi.getMyProfile()
    if (res.data) {
      profile.value = res.data
      if (res.data.studyStage) stageCache.value = res.data.studyStage
    }
  } catch {
    showToast('加载用户信息失败', 'error')
  }
})


watch(() => route.query.tab, (val) => {
  if (val && ['overview', 'essays', 'radar', 'referral', 'settings'].includes(val as string)) {
    activeSection.value = val as SectionKey
  }
})
</script>

<style scoped>
.pc-layout {
  display: flex;
  min-height: 100vh;
  background: #f5f6f7;
}

.pc-sidebar {
  width: 220px;
  min-width: 220px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  padding: 20px 0;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  margin-bottom: 16px;
  background: none;
  border: none;
  font-size: 14px;
  color: #64748b;
  cursor: pointer;
  transition: color 0.15s;
}
.back-btn:hover {
  color: #047857;
}

/* User Section */
.user-section {
  padding: 0 20px 20px;
  border-bottom: 1px solid #e5e7eb;
  margin-bottom: 8px;
}

.avatar {
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
  margin-bottom: 12px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.avatar:hover {
  opacity: 0.85;
}

.nickname-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.nickname-text {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.icon-btn {
  background: none;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 2px;
  display: flex;
  align-items: center;
  transition: color 0.15s;
}
.icon-btn:hover {
  color: #047857;
}

.nickname-edit {
  margin-bottom: 10px;
}

.nickname-input {
  width: 100%;
  padding: 6px 10px;
  font-size: 14px;
  border: 1px solid #047857;
  border-radius: 6px;
  outline: none;
  box-sizing: border-box;
}

/* Stage Switcher */
.stage-switcher {
  position: relative;
  margin-bottom: 10px;
}

.stage-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 6px 10px;
  font-size: 13px;
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.stage-btn:hover {
  border-color: #047857;
}

.stage-btn svg.rotated {
  transform: rotate(180deg);
}

.stage-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 50;
  padding: 4px;
}

.stage-option {
  display: block;
  width: 100%;
  padding: 7px 10px;
  font-size: 13px;
  color: #334155;
  background: none;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  text-align: left;
  transition: background 0.12s;
}
.stage-option:hover {
  background: #ecfdf5;
}
.stage-option.active {
  background: #ecfdf5;
  color: #047857;
  font-weight: 600;
}

.register-time {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

/* Navigation */
.pc-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  font-size: 14px;
  color: #475569;
  background: none;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.nav-item:hover {
  background: #f1f5f9;
}
.nav-item.active {
  background: #ecfdf5;
  color: #047857;
  font-weight: 600;
}

/* Content */
.pc-content {
  flex: 1;
  padding: 32px 40px;
  overflow-y: auto;
  min-height: 100vh;
}

@media (max-width: 768px) {
  .pc-layout {
    flex-direction: column;
  }
  .pc-sidebar {
    width: 100%;
    min-width: unset;
    height: auto;
    position: static;
    border-right: none;
    border-bottom: 1px solid #e5e7eb;
  }
  .pc-nav {
    flex-direction: row;
    overflow-x: auto;
  }
  .pc-content {
    padding: 20px 16px;
  }
}
</style>
