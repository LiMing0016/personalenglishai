<template>
  <section class="admin-section" v-if="detail">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <h2 class="admin-card-title">Rubric 版本 #{{ detail.id }}</h2>
          <div class="admin-subtle">当前版本 {{ detail.isActive ? '已激活' : '未激活' }}。线上版本禁止直接编辑，需先克隆。</div>
        </div>
        <div class="admin-toolbar-right">
          <button class="admin-btn admin-btn--secondary" @click="cloneVersion">克隆</button>
          <button class="admin-btn admin-btn--secondary" :disabled="Boolean(detail.isActive)" @click="activateVersion">激活</button>
          <button class="admin-btn" :disabled="Boolean(detail.isActive)" @click="save">保存</button>
        </div>
      </div>
      <div class="admin-grid-three">
        <div class="admin-stat">
          <div class="admin-stat-label">Rubric Key</div>
          <div class="admin-stat-value">{{ detail.rubricKey }}</div>
        </div>
        <div class="admin-stat">
          <div class="admin-stat-label">Stage</div>
          <div class="admin-stat-value">{{ detail.stage }}</div>
        </div>
        <div class="admin-stat">
          <div class="admin-stat-label">Dimension 数量</div>
          <div class="admin-stat-value">{{ detail.dimensions.length }}</div>
        </div>
      </div>
      <div class="admin-form-grid">
        <label class="admin-field"><span>rubricKey</span><input v-model="detail.rubricKey" class="admin-input" :disabled="Boolean(detail.isActive)" /></label>
        <label class="admin-field"><span>stage</span><input v-model="detail.stage" class="admin-input" :disabled="Boolean(detail.isActive)" /></label>
        <label class="admin-field admin-field--full"><span>dimensions JSON</span><textarea v-model="dimensionsJson" class="admin-textarea admin-textarea--lg" :disabled="Boolean(detail.isActive)"></textarea></label>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminApi, type AdminRubricVersionDto } from '@/api/admin'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const detail = ref<AdminRubricVersionDto | null>(null)
const dimensionsJson = ref('[]')

async function load() {
  try {
    detail.value = await adminApi.getRubric(String(route.params.id))
    dimensionsJson.value = JSON.stringify(detail.value.dimensions, null, 2)
  } catch {
    showToast('加载 Rubric 失败', 'error')
  }
}

async function save() {
  if (!detail.value || detail.value.isActive) return
  try {
    const dimensions = JSON.parse(dimensionsJson.value)
    detail.value = await adminApi.updateRubric(String(route.params.id), {
      rubricKey: detail.value.rubricKey,
      stage: detail.value.stage,
      dimensions,
    })
    dimensionsJson.value = JSON.stringify(detail.value.dimensions, null, 2)
    showToast('Rubric 已保存', 'success')
  } catch {
    showToast('保存 Rubric 失败，请检查 JSON 结构', 'error')
  }
}

async function cloneVersion() {
  try {
    const cloned = await adminApi.cloneRubric(String(route.params.id))
    showToast('Rubric 已克隆', 'success')
    router.push(`/admin/rubrics/${cloned.id}`)
  } catch {
    showToast('克隆失败', 'error')
  }
}

async function activateVersion() {
  if (!detail.value || detail.value.isActive) return
  try {
    detail.value = await adminApi.activateRubric(String(route.params.id))
    dimensionsJson.value = JSON.stringify(detail.value.dimensions, null, 2)
    showToast('Rubric 已激活', 'success')
  } catch {
    showToast('激活失败', 'error')
  }
}

onMounted(load)
</script>
