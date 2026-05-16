<template>
  <form class="analytics-filter" @submit.prevent="emitApply">
    <label>
      <span>开始日期</span>
      <input v-model="draft.dateFrom" class="admin-input" type="date" />
    </label>
    <label>
      <span>结束日期</span>
      <input v-model="draft.dateTo" class="admin-input" type="date" />
    </label>
    <label>
      <span>学段</span>
      <select v-model="draft.studyStage" class="admin-select">
        <option value="">全部</option>
        <option value="ielts">IELTS</option>
        <option value="toefl">TOEFL</option>
        <option value="postgrad">考研</option>
        <option value="gaokao">高考</option>
      </select>
    </label>
    <label>
      <span>套餐</span>
      <select v-model="draft.planCode" class="admin-select">
        <option value="">全部</option>
        <option value="free">Free</option>
        <option value="basic">Basic</option>
        <option value="pro">Pro</option>
        <option value="premium">Premium</option>
      </select>
    </label>
    <label>
      <span>渠道</span>
      <select v-model="draft.channel" class="admin-select">
        <option value="">全部</option>
        <option value="web">Web</option>
        <option value="redeem">活动兑换</option>
        <option value="admin_import">管理员导入</option>
      </select>
    </label>
    <button class="admin-btn" type="submit">刷新</button>
  </form>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { AnalyticsFilters } from '../types/index.ts'

const props = defineProps<{
  filters: AnalyticsFilters
}>()

const emit = defineEmits<{
  apply: [filters: AnalyticsFilters]
}>()

const draft = reactive<AnalyticsFilters>({ ...props.filters })

watch(
  () => props.filters,
  (next) => {
    Object.assign(draft, next)
  },
  { deep: true },
)

function emitApply() {
  emit('apply', { ...draft })
}
</script>

<style scoped>
.analytics-filter {
  display: grid;
  grid-template-columns: repeat(5, minmax(130px, 1fr)) auto;
  gap: 12px;
  align-items: end;
}

.analytics-filter label {
  display: grid;
  gap: 6px;
  min-width: 0;
  color: var(--admin-muted);
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 1100px) {
  .analytics-filter {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
