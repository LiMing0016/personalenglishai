<template>
  <section class="admin-section">
    <div class="admin-card">
      <div class="admin-toolbar">
        <div>
          <h2 class="admin-card-title">{{ isNew ? '新建题目' : `编辑题目 #${route.params.id}` }}</h2>
          <div class="admin-subtle">支持完整字段覆盖式提交，适合管理员做题库校对与启停。</div>
        </div>
        <div class="admin-toolbar-right">
          <button v-if="!isNew" class="admin-btn admin-btn--secondary" @click="toggleActive">
            {{ Number(form.isActive) === 1 ? '停用题目' : '启用题目' }}
          </button>
          <button class="admin-btn" @click="save">保存</button>
        </div>
      </div>
      <div class="admin-grid-three" v-if="!isNew">
        <div class="admin-stat">
          <div class="admin-stat-label">当前状态</div>
          <div class="admin-stat-value">{{ Number(form.isActive) === 1 ? '启用' : '停用' }}</div>
        </div>
        <div class="admin-stat">
          <div class="admin-stat-label">字数要求</div>
          <div class="admin-stat-value">{{ wordRange }}</div>
        </div>
        <div class="admin-stat">
          <div class="admin-stat-label">分值</div>
          <div class="admin-stat-value">{{ form.maxScore || '-' }}</div>
        </div>
      </div>
      <div class="admin-form-grid">
        <label class="admin-field"><span>stageId</span><input v-model="form.stageId" class="admin-input" /></label>
        <label class="admin-field"><span>paper</span><input v-model="form.paper" class="admin-input" /></label>
        <label class="admin-field"><span>title</span><input v-model="form.title" class="admin-input" /></label>
        <label class="admin-field"><span>examYear</span><input v-model="form.examYear" class="admin-input" /></label>
        <label class="admin-field"><span>task</span><input v-model="form.task" class="admin-input" /></label>
        <label class="admin-field"><span>maxScore</span><input v-model="form.maxScore" class="admin-input" /></label>
        <label class="admin-field"><span>wordCountMin</span><input v-model="form.wordCountMin" class="admin-input" /></label>
        <label class="admin-field"><span>wordCountMax</span><input v-model="form.wordCountMax" class="admin-input" /></label>
        <label class="admin-field"><span>source</span><input v-model="form.source" class="admin-input" /></label>
        <label class="admin-field"><span>imageUrl</span><input v-model="form.imageUrl" class="admin-input" /></label>
        <label class="admin-field admin-field--full"><span>promptText</span><textarea v-model="form.promptText" class="admin-textarea"></textarea></label>
        <label class="admin-field admin-field--full"><span>imageDescription</span><textarea v-model="form.imageDescription" class="admin-textarea"></textarea></label>
        <label class="admin-field admin-field--full"><span>materialText</span><textarea v-model="form.materialText" class="admin-textarea"></textarea></label>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminApi, type AdminPromptDto } from '@/api/admin'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const isNew = computed(() => route.params.id === 'new')
const wordRange = computed(() => {
  const min = form.wordCountMin || '-'
  const max = form.wordCountMax || '-'
  return `${min} - ${max}`
})
const form = reactive<any>({
  stageId: '', paper: '', title: '', promptText: '', examYear: '', imageUrl: '', imageDescription: '', materialText: '', task: '',
  wordCountMin: '', wordCountMax: '', maxScore: '', source: '', isActive: 1,
})

async function load() {
  if (isNew.value) return
  try {
    const data = await adminApi.getPrompt(String(route.params.id))
    Object.assign(form, data)
  } catch {
    showToast('加载题目失败', 'error')
  }
}

function toPayload(): AdminPromptDto {
  return {
    stageId: form.stageId ? Number(form.stageId) : null,
    paper: form.paper,
    title: form.title,
    promptText: form.promptText,
    examYear: form.examYear ? Number(form.examYear) : null,
    imageUrl: form.imageUrl || null,
    imageDescription: form.imageDescription || null,
    materialText: form.materialText || null,
    task: form.task || null,
    wordCountMin: form.wordCountMin ? Number(form.wordCountMin) : null,
    wordCountMax: form.wordCountMax ? Number(form.wordCountMax) : null,
    maxScore: form.maxScore ? Number(form.maxScore) : null,
    source: form.source || null,
    isActive: Number(form.isActive || 0),
  }
}

async function save() {
  try {
    const payload = toPayload()
    const saved = isNew.value ? await adminApi.createPrompt(payload) : await adminApi.updatePrompt(String(route.params.id), payload)
    showToast('题目已保存', 'success')
    router.replace(`/admin/prompts/${saved.id}`)
  } catch {
    showToast('保存题目失败', 'error')
  }
}

async function toggleActive() {
  if (isNew.value) return
  try {
    const nextActive = Number(form.isActive) !== 1
    await adminApi.updatePromptActive(String(route.params.id), nextActive)
    form.isActive = nextActive ? 1 : 0
    showToast(nextActive ? '题目已启用' : '题目已停用', 'success')
  } catch {
    showToast('更新题目状态失败', 'error')
  }
}

onMounted(load)
</script>
