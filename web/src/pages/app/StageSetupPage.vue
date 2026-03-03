<template>
  <!-- Loading -->
  <div v-if="loading" class="gate-center">
    <div class="gate-spinner" />
    <p class="gate-hint">加载中…</p>
  </div>

  <!-- Stage select -->
  <div v-else class="gate-center">
    <h2 class="gate-title">选择你的学段</h2>
    <p class="gate-desc">我们将根据学段匹配评分标准和 AI 辅助策略</p>
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
    <button
      class="gate-btn"
      :disabled="!pendingStage || saving"
      @click="confirmStage"
    >
      {{ saving ? '保存中…' : '确认' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userApi } from '@/api/user'
import { showToast } from '@/utils/toast'
import { stageCache } from '@/stores/stageCache'
import { STAGE_OPTIONS } from '@/constants/stage'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const pendingStage = ref<string | null>(null)
const saving = ref(false)

onMounted(async () => {
  // Use cached stage if available, otherwise fetch
  const cached = stageCache.value
  if (cached && cached !== '__error__') {
    pendingStage.value = cached
    loading.value = false
    return
  }
  try {
    const res = await userApi.getMyProfile()
    const stage = res.data?.studyStage
    if (stage) pendingStage.value = stage
  } catch {
    // ignore, user can still pick
  } finally {
    loading.value = false
  }
})

async function confirmStage() {
  if (!pendingStage.value) return
  saving.value = true
  try {
    await userApi.updateStudyStage(pendingStage.value)
    stageCache.value = pendingStage.value
    const redirect = (route.query.redirect as string) || '/app'
    router.replace(redirect)
  } catch {
    showToast('保存学段失败，请重试', 'error')
  } finally {
    saving.value = false
  }
}
</script>

<style src="@/styles/gate.css" />
<style scoped>
.stage-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  max-width: 560px;
  width: 100%;
  margin-bottom: 28px;
}

.stage-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 18px 8px;
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
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
  font-size: 24px;
  line-height: 1;
}

.stage-label {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

@media (max-width: 560px) {
  .stage-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
