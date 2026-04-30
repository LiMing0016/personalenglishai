<template>
  <main class="share-page">
    <section class="share-shell">
      <header class="share-header">
        <span class="brand">PEAI</span>
        <h1>{{ title }}</h1>
        <p v-if="createdAt">分享时间 {{ createdAt }}</p>
      </header>

      <div v-if="loading" class="state-text">加载中...</div>
      <div v-else-if="errorMessage" class="state-text state-text--error">{{ errorMessage }}</div>
      <div v-else class="message-list">
        <article
          v-for="message in messages"
          :key="message.id"
          class="message-item"
          :class="`message-item--${message.role}`"
        >
          <span class="role-label">{{ message.role === 'user' ? '我' : '学习助手' }}</span>
          <p>{{ message.content }}</p>
        </article>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { assistantApi, type AssistantMessageDto } from '@/api/assistant'

const route = useRoute()
const title = ref('分享对话')
const messages = ref<AssistantMessageDto[]>([])
const createdAtRaw = ref<string | null>(null)
const loading = ref(true)
const errorMessage = ref('')

const createdAt = computed(() => {
  if (!createdAtRaw.value) return ''
  const parsed = Date.parse(createdAtRaw.value)
  if (Number.isNaN(parsed)) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(parsed)
})

onMounted(async () => {
  const shareToken = String(route.params.shareToken || '')
  try {
    const share = await assistantApi.getPublicShare(shareToken)
    title.value = share.title
    messages.value = share.messages
    createdAtRaw.value = share.createdAt ?? null
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '分享不存在或已失效'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.share-page {
  min-height: 100vh;
  background: #f8fafc;
  color: #0f172a;
}

.share-shell {
  width: min(860px, calc(100% - 32px));
  margin: 0 auto;
  padding: 36px 0 56px;
}

.share-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 0 24px;
  border-bottom: 1px solid #e2e8f0;
}

.brand {
  color: #047857;
  font-size: 15px;
  font-weight: 800;
}

.share-header h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.25;
}

.share-header p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 24px;
}

.message-item {
  max-width: 76%;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.message-item--user {
  align-self: flex-end;
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.role-label {
  display: block;
  margin-bottom: 6px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.message-item p {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.65;
}

.state-text {
  padding: 32px 0;
  color: #64748b;
}

.state-text--error {
  color: #dc2626;
}

@media (max-width: 720px) {
  .message-item {
    max-width: 100%;
  }
}
</style>
