<template>
  <Teleport to="body">
    <div v-if="open" class="theme-dialog-backdrop" @click.self="closeDialog">
      <section
        class="theme-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="theme-dialog-title"
      >
        <header class="theme-dialog__header">
          <div>
            <p>Vocabulary theme</p>
            <h2 id="theme-dialog-title">{{ initialTheme ? '编辑主题' : '新建主题' }}</h2>
          </div>
          <button
            type="button"
            class="theme-dialog__icon-button"
            title="关闭"
            aria-label="关闭"
            :disabled="pending"
            @click="closeDialog"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12" /></svg>
          </button>
        </header>

        <form @submit.prevent="submit">
          <label class="theme-dialog__field">
            <span>主题名称</span>
            <input
              v-model="name"
              maxlength="80"
              autocomplete="off"
              :aria-invalid="Boolean(errors.name)"
              :aria-describedby="errors.name ? 'theme-name-error' : undefined"
              @input="errors.name = ''"
            >
            <small v-if="errors.name" id="theme-name-error" class="theme-dialog__error">{{ errors.name }}</small>
          </label>

          <label class="theme-dialog__field">
            <span>用途说明</span>
            <textarea
              v-model="purpose"
              maxlength="1000"
              rows="6"
              :aria-invalid="Boolean(errors.purpose)"
              :aria-describedby="errors.purpose ? 'theme-purpose-error' : undefined"
              @input="errors.purpose = ''"
            ></textarea>
            <div class="theme-dialog__field-meta">
              <small v-if="errors.purpose" id="theme-purpose-error" class="theme-dialog__error">{{ errors.purpose }}</small>
              <small>{{ purpose.length }}/1000</small>
            </div>
          </label>

          <p v-if="requestError" class="theme-dialog__request-error" role="alert">{{ requestError }}</p>

          <footer class="theme-dialog__actions">
            <button type="button" :disabled="pending" @click="closeDialog">取消</button>
            <button type="submit" class="theme-dialog__primary" :disabled="pending">
              {{ pending ? '保存中...' : '保存主题' }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, type Ref } from 'vue'

import type { CreateVocabularyThemeRequest, VocabularyTheme } from '@/api/vocabulary'

type ThemeMutation = {
  isPending: Ref<boolean>
  mutateAsync: (payload: CreateVocabularyThemeRequest) => Promise<unknown>
}

const props = defineProps<{
  open: boolean
  initialTheme: VocabularyTheme | null
  mutation: ThemeMutation
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const name = ref('')
const purpose = ref('')
const errors = reactive({ name: '', purpose: '' })
const requestError = ref('')
const pending = computed(() => props.mutation.isPending.value)

watch(
  () => [props.open, props.initialTheme] as const,
  ([open, theme]) => {
    if (!open) return
    name.value = theme?.name ?? ''
    purpose.value = theme?.purpose ?? ''
    errors.name = ''
    errors.purpose = ''
    requestError.value = ''
  },
  { immediate: true },
)

function closeDialog() {
  if (!pending.value) emit('close')
}

function validate() {
  errors.name = name.value.trim() ? '' : '主题名称不能为空'
  errors.purpose = purpose.value.trim() ? '' : '用途说明不能为空'
  return !errors.name && !errors.purpose
}

async function submit() {
  if (pending.value || !validate()) return
  requestError.value = ''
  try {
    await props.mutation.mutateAsync({
      name: name.value.trim(),
      purpose: purpose.value.trim(),
    })
    emit('saved')
    emit('close')
  } catch (error) {
    requestError.value = error instanceof Error ? error.message : '主题保存失败，请重试'
  }
}
</script>

<style scoped>
.theme-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 10001;
  display: grid;
  place-items: center;
  box-sizing: border-box;
  padding: 16px;
  background: rgba(15, 23, 42, .46);
}
.theme-dialog {
  box-sizing: border-box;
  width: min(100%, 520px);
  max-height: calc(100vh - 32px);
  overflow: auto;
  border-radius: 8px;
  background: #fff;
  color: #334155;
  padding: 22px;
  box-shadow: 0 20px 48px rgba(15, 23, 42, .24);
}
.theme-dialog__header, .theme-dialog__actions, .theme-dialog__field-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.theme-dialog__header p, .theme-dialog__header h2 { margin: 0; }
.theme-dialog__header p { color: #059669; font-size: 12px; font-weight: 800; text-transform: uppercase; }
.theme-dialog__header h2 { margin-top: 4px; color: #0f172a; font-size: 21px; }
.theme-dialog__icon-button {
  display: inline-grid;
  place-items: center;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid #dce7e1;
  border-radius: 6px;
  background: #fff;
  color: #475569;
  cursor: pointer;
}
.theme-dialog__icon-button svg { width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; }
.theme-dialog form { display: grid; gap: 16px; margin-top: 20px; }
.theme-dialog__field { display: grid; gap: 7px; min-width: 0; color: #334155; font-size: 13px; font-weight: 700; }
.theme-dialog input, .theme-dialog textarea {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #f8fafc;
  color: #0f172a;
  font: inherit;
  line-height: 1.5;
  padding: 10px 12px;
}
.theme-dialog textarea { resize: vertical; }
.theme-dialog input:focus, .theme-dialog textarea:focus { border-color: #0d9488; outline: 0; box-shadow: 0 0 0 3px rgba(13, 148, 136, .12); }
.theme-dialog__field-meta { align-items: start; color: #64748b; font-weight: 400; }
.theme-dialog__error, .theme-dialog__request-error { color: #b91c1c; }
.theme-dialog__request-error { margin: 0; font-size: 13px; overflow-wrap: anywhere; }
.theme-dialog__actions { justify-content: flex-end; margin-top: 4px; }
.theme-dialog__actions button { min-height: 36px; border: 1px solid #dce7e1; border-radius: 6px; background: #fff; color: #334155; font: inherit; font-size: 13px; font-weight: 700; padding: 0 14px; cursor: pointer; }
.theme-dialog__actions .theme-dialog__primary { border-color: #059669; background: #059669; color: #fff; }
.theme-dialog button:disabled { cursor: not-allowed; opacity: .55; }

@media (max-width: 520px) {
  .theme-dialog { padding: 18px; }
  .theme-dialog__actions button { flex: 1; min-width: 0; }
}
</style>
