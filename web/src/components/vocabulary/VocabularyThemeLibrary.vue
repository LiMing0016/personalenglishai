<template>
  <div class="theme-library">
    <header class="theme-library__header">
      <div>
        <RouterLink to="/app/vocabulary" class="theme-library__back">返回单词卡中心</RouterLink>
        <h1>主题库</h1>
        <p>管理沉淀单词卡时使用的学习目标与内容侧重点。</p>
      </div>
      <button ref="createButtonRef" type="button" class="theme-library__create" :disabled="isThemeActionPending" @click="openCreateDialog">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>
        新建主题
      </button>
    </header>

    <label class="theme-library__search">
      <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m16 16 4 4" /></svg>
      <input v-model="search" type="search" placeholder="按名称搜索主题" aria-label="按名称搜索主题">
    </label>

    <div v-if="themesQuery.isLoading.value" class="theme-library__state" role="status">加载主题中...</div>
    <div v-else-if="themesQuery.isError.value" class="theme-library__state theme-library__state--error" role="alert">
      <p>主题加载失败，请检查网络后重试。</p>
      <button type="button" @click="themesQuery.refetch()">重新加载</button>
    </div>

    <template v-else>
      <p v-if="actionError" class="theme-library__action-error" role="alert">{{ actionError }}</p>
      <p v-if="isThemeActionPending" class="theme-library__sr-only" role="status" aria-live="polite">主题操作处理中</p>

      <section class="theme-library__section" aria-labelledby="system-theme-heading">
        <div class="theme-library__section-heading">
          <div>
            <h2 id="system-theme-heading">系统主题</h2>
            <p>由系统维护，可复制为自己的主题。</p>
          </div>
          <span>{{ filteredSystemThemes.length }}</span>
        </div>
        <div v-if="filteredSystemThemes.length" class="theme-library__grid">
          <article
            v-for="theme in filteredSystemThemes"
            :key="theme.themeUid"
            class="theme-card"
            :aria-busy="isThemeActionPending"
          >
            <div class="theme-card__content">
              <div class="theme-card__title-row">
                <h3>{{ theme.name }}</h3>
                <span v-if="theme.defaultTheme" class="theme-card__status">默认</span>
                <span v-if="isThemePending(theme.themeUid)" class="theme-card__status theme-card__status--pending" role="status">处理中</span>
              </div>
              <p>{{ theme.purpose }}</p>
            </div>
            <div class="theme-card__actions" :aria-busy="isThemeActionPending">
              <button type="button" title="复制" aria-label="复制" :disabled="isThemeActionPending" @click="copyTheme(theme)">
                <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="9" width="11" height="11" rx="2" /><path d="M15 9V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h3" /></svg>
              </button>
            </div>
          </article>
        </div>
        <div v-else class="theme-library__empty">没有匹配的系统主题</div>
      </section>

      <section class="theme-library__section" aria-labelledby="user-theme-heading">
        <div class="theme-library__section-heading">
          <div>
            <h2 id="user-theme-heading">我的主题</h2>
            <p>按自己的学习任务定义主题，可随时调整。</p>
          </div>
          <span>{{ filteredUserThemes.length }}</span>
        </div>
        <div v-if="filteredUserThemes.length" class="theme-library__grid">
          <article
            v-for="theme in filteredUserThemes"
            :key="theme.themeUid"
            class="theme-card"
            :class="{ 'theme-card--disabled': theme.status === 'disabled' }"
            :aria-busy="isThemeActionPending"
          >
            <div class="theme-card__content">
              <div class="theme-card__title-row">
                <h3>{{ theme.name }}</h3>
                <span v-if="theme.defaultTheme" class="theme-card__status">默认</span>
                <span v-else-if="theme.status === 'disabled'" class="theme-card__status theme-card__status--muted">已停用</span>
                <span v-if="isThemePending(theme.themeUid)" class="theme-card__status theme-card__status--pending" role="status">处理中</span>
              </div>
              <p>{{ theme.purpose }}</p>
            </div>
            <div class="theme-card__actions" :aria-busy="isThemeActionPending">
              <button type="button" title="复制" aria-label="复制" :disabled="isThemeActionPending" @click="copyTheme(theme)">
                <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="9" width="11" height="11" rx="2" /><path d="M15 9V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h3" /></svg>
              </button>
              <template v-if="theme.ownerType === 'user'">
                <button type="button" title="编辑" aria-label="编辑" :disabled="isThemeActionPending" @click="openEditDialog(theme)">
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z" /></svg>
                </button>
                <button type="button" title="设为默认" aria-label="设为默认" :disabled="isThemeActionPending || theme.defaultTheme || theme.status === 'disabled'" @click="setDefaultTheme(theme)">
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9Z" /></svg>
                </button>
                <button type="button" title="停用" aria-label="停用" :disabled="isThemeActionPending || theme.status === 'disabled'" @click="disableTheme(theme)">
                  <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M10 9v6M14 9v6" /></svg>
                </button>
                <button type="button" class="theme-card__danger" title="删除" aria-label="删除" :disabled="isThemeActionPending" @click="requestDelete(theme)">
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5" /></svg>
                </button>
              </template>
            </div>
          </article>
        </div>
        <div v-else class="theme-library__empty">
          {{ search.trim() ? '没有匹配的自定义主题' : '还没有自定义主题' }}
        </div>
      </section>
    </template>

    <VocabularyThemeDialog
      :open="dialogOpen"
      :initial-theme="editingTheme"
      :mutation="dialogMutation"
      @close="closeThemeDialog"
      @saved="actionError = ''"
    />

    <Teleport to="body">
      <div v-if="deletingTheme" class="theme-delete-backdrop" @click.self="closeDeleteDialog">
        <section
          ref="deleteDialogRef"
          class="theme-delete-dialog"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="delete-theme-title"
          aria-describedby="delete-theme-description"
          :aria-busy="deleteMutation.isPending.value"
          tabindex="-1"
          @keydown="handleDeleteDialogKeydown"
        >
          <h2 id="delete-theme-title">删除“{{ deletingTheme.name }}”？</h2>
          <p id="delete-theme-description">主题将不再出现在主题库中，但历史卡片仍会保留主题名称和已有内容。</p>
          <p v-if="actionError" class="theme-delete-dialog__error">{{ actionError }}</p>
          <div>
            <button ref="deleteCancelButtonRef" type="button" :disabled="deleteMutation.isPending.value" @click="closeDeleteDialog">取消</button>
            <button type="button" class="theme-delete-dialog__danger" :disabled="deleteMutation.isPending.value" @click="confirmDelete">
              {{ deleteMutation.isPending.value ? '删除中...' : '确认删除' }}
            </button>
          </div>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'

import type { CreateVocabularyThemeRequest, VocabularyTheme } from '@/api/vocabulary'
import { useVocabularyThemes } from '@/composables/useVocabularyThemes'
import { showToast } from '@/utils/toast'
import VocabularyThemeDialog from './VocabularyThemeDialog.vue'

const props = defineProps<{
  themeState: ReturnType<typeof useVocabularyThemes>
}>()

const {
  themesQuery,
  createMutation,
  updateMutation,
  copyMutation,
  defaultMutation,
  disableMutation,
  deleteMutation,
} = props.themeState

const search = ref('')
const dialogOpen = ref(false)
const editingTheme = ref<VocabularyTheme | null>(null)
const deletingTheme = ref<VocabularyTheme | null>(null)
const createButtonRef = ref<HTMLButtonElement | null>(null)
const deleteDialogRef = ref<HTMLElement | null>(null)
const deleteCancelButtonRef = ref<HTMLButtonElement | null>(null)
const deleteTriggerElement = ref<HTMLElement | null>(null)
const pendingThemeUid = ref('')
const actionError = ref('')

const normalizedSearch = computed(() => search.value.trim().toLocaleLowerCase())
const filteredSystemThemes = computed(() => filterThemes(themesQuery.data.value?.systemThemes ?? []))
const filteredUserThemes = computed(() => filterThemes(themesQuery.data.value?.userThemes ?? []))
const isThemeActionPending = computed(() => Boolean(pendingThemeUid.value))
const dialogMutation = computed(() => editingTheme.value
  ? {
      isPending: updateMutation.isPending,
      mutateAsync: (payload: CreateVocabularyThemeRequest) => updateMutation.mutateAsync({
        themeUid: editingTheme.value!.themeUid,
        payload,
      }),
    }
  : {
      isPending: createMutation.isPending,
      mutateAsync: (payload: CreateVocabularyThemeRequest) => createMutation.mutateAsync(payload),
    })

function filterThemes(themes: VocabularyTheme[]) {
  if (!normalizedSearch.value) return themes
  return themes.filter((theme) => theme.name.toLocaleLowerCase().includes(normalizedSearch.value))
}

function isThemePending(themeUid: string) {
  return pendingThemeUid.value === themeUid
}

function openCreateDialog() {
  editingTheme.value = null
  dialogOpen.value = true
}

function openEditDialog(theme: VocabularyTheme) {
  editingTheme.value = theme
  dialogOpen.value = true
}

function closeThemeDialog() {
  dialogOpen.value = false
  editingTheme.value = null
}

async function runThemeMutation(theme: VocabularyTheme, action: () => Promise<unknown>, successMessage: string) {
  if (pendingThemeUid.value) return
  pendingThemeUid.value = theme.themeUid
  actionError.value = ''
  try {
    await action()
    showToast(successMessage, 'success')
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '主题操作失败，请重试'
  } finally {
    pendingThemeUid.value = ''
  }
}

function copyTheme(theme: VocabularyTheme) {
  return runThemeMutation(theme, () => copyMutation.mutateAsync(theme.themeUid), '主题副本已创建')
}

function setDefaultTheme(theme: VocabularyTheme) {
  return runThemeMutation(theme, () => defaultMutation.mutateAsync(theme.themeUid), '默认主题已更新')
}

function disableTheme(theme: VocabularyTheme) {
  return runThemeMutation(theme, () => disableMutation.mutateAsync(theme.themeUid), '主题已停用')
}

async function requestDelete(theme: VocabularyTheme) {
  if (isThemeActionPending.value) return
  actionError.value = ''
  deleteTriggerElement.value = document.activeElement instanceof HTMLElement
    ? document.activeElement
    : null
  deletingTheme.value = theme
  await nextTick()
  deleteCancelButtonRef.value?.focus()
}

function closeDeleteDialog() {
  if (deleteMutation.isPending.value || isThemeActionPending.value) return
  void dismissDeleteDialog()
}

async function dismissDeleteDialog() {
  deletingTheme.value = null
  await nextTick()
  if (deleteTriggerElement.value?.isConnected) {
    deleteTriggerElement.value.focus()
  } else {
    createButtonRef.value?.focus()
  }
  deleteTriggerElement.value = null
}

function handleDeleteDialogKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    if (deleteMutation.isPending.value || isThemeActionPending.value) return
    closeDeleteDialog()
    return
  }

  if (event.key === 'Tab') {
    const focusableElements = Array.from(
      deleteDialogRef.value?.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ) ?? [],
    )
    const firstElement = focusableElements[0]
    const lastElement = focusableElements[focusableElements.length - 1]

    if (!firstElement || !lastElement) {
      event.preventDefault()
      deleteDialogRef.value?.focus()
    } else if (event.shiftKey && document.activeElement === firstElement) {
      event.preventDefault()
      lastElement.focus()
    } else if (!event.shiftKey && document.activeElement === lastElement) {
      event.preventDefault()
      firstElement.focus()
    }
  }
}

async function confirmDelete() {
  const theme = deletingTheme.value
  if (!theme || deleteMutation.isPending.value) return
  actionError.value = ''
  pendingThemeUid.value = theme.themeUid
  try {
    await deleteMutation.mutateAsync(theme.themeUid)
    pendingThemeUid.value = ''
    await dismissDeleteDialog()
    showToast('主题已删除，历史卡片保持不变', 'success')
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '主题删除失败，请重试'
  } finally {
    pendingThemeUid.value = ''
  }
}
</script>

<style scoped>
.theme-library { width: min(100%, 1120px); min-width: 0; margin: 0 auto; color: #334155; }
.theme-library__header, .theme-library__section-heading, .theme-card, .theme-card__title-row, .theme-card__actions { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.theme-library__header { align-items: end; }
.theme-library__header h1 { margin: 12px 0 0; color: #0f172a; font-size: 30px; line-height: 1.2; }
.theme-library__header p { margin: 7px 0 0; color: #64748b; font-size: 14px; line-height: 1.55; }
.theme-library__back { color: #047857; font-size: 13px; font-weight: 700; text-decoration: none; }
.theme-library__create, .theme-library__state button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 38px; border: 0; border-radius: 6px; background: #059669; color: #fff; font: inherit; font-size: 13px; font-weight: 800; padding: 0 14px; cursor: pointer; white-space: nowrap; }
.theme-library__create svg { width: 17px; height: 17px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; }
.theme-library__search { position: relative; display: block; width: min(100%, 380px); margin-top: 26px; }
.theme-library__search svg { position: absolute; top: 50%; left: 11px; width: 18px; height: 18px; transform: translateY(-50%); fill: none; stroke: #64748b; stroke-width: 2; stroke-linecap: round; }
.theme-library__search input { box-sizing: border-box; width: 100%; min-width: 0; height: 40px; border: 1px solid #cbd5e1; border-radius: 6px; background: #fff; color: #0f172a; font: inherit; padding: 0 12px 0 38px; }
.theme-library__search input:focus { border-color: #0d9488; outline: 0; box-shadow: 0 0 0 3px rgba(13, 148, 136, .12); }
.theme-library__state, .theme-library__empty { margin-top: 28px; border: 1px dashed #cbd5e1; border-radius: 6px; color: #64748b; padding: 26px; text-align: center; }
.theme-library__state p { margin: 0 0 12px; }.theme-library__state--error { color: #b91c1c; }
.theme-library__action-error { margin: 22px 0 0; color: #b91c1c; font-size: 13px; overflow-wrap: anywhere; }
.theme-library__section { margin-top: 34px; padding-top: 26px; border-top: 1px solid #dce7e1; }
.theme-library__section-heading { align-items: end; }
.theme-library__section-heading h2, .theme-library__section-heading p { margin: 0; }
.theme-library__section-heading h2 { color: #0f172a; font-size: 19px; }
.theme-library__section-heading p { margin-top: 5px; color: #64748b; font-size: 13px; }
.theme-library__section-heading > span { color: #64748b; font-size: 13px; }
.theme-library__grid { display: grid; gap: 10px; margin-top: 16px; }
.theme-card { min-width: 0; padding: 16px; border: 1px solid #dce7e1; border-radius: 8px; background: #fff; }
.theme-card--disabled { background: #f8fafc; }
.theme-card__content { min-width: 0; }
.theme-card__title-row { justify-content: flex-start; gap: 8px; min-width: 0; }
.theme-card h3 { min-width: 0; margin: 0; color: #0f172a; font-size: 15px; overflow-wrap: anywhere; }
.theme-card p { max-width: 720px; margin: 7px 0 0; color: #64748b; font-size: 13px; line-height: 1.55; overflow-wrap: anywhere; }
.theme-card__status { flex: 0 0 auto; border-radius: 999px; background: #dcfce7; color: #047857; font-size: 11px; font-weight: 800; padding: 3px 7px; }
.theme-card__status--muted { background: #e2e8f0; color: #64748b; }
.theme-card__status--pending { min-width: 48px; background: #ecfeff; color: #0e7490; text-align: center; }
.theme-card__actions { flex: 0 0 auto; justify-content: flex-end; gap: 6px; }
.theme-card__actions button { display: inline-grid; place-items: center; box-sizing: border-box; flex: 0 0 36px; width: 36px; height: 36px; padding: 0; border: 1px solid #dce7e1; border-radius: 6px; background: #fff; color: #475569; cursor: pointer; }
.theme-card__actions svg { width: 17px; height: 17px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.theme-card__actions button:hover:not(:disabled), .theme-card__actions button:focus-visible { border-color: #5eead4; background: #f0fdfa; color: #0f766e; }
.theme-card__actions button:disabled { cursor: not-allowed; opacity: .45; }
.theme-card__actions .theme-card__danger { border-color: #fecaca; color: #b91c1c; }
.theme-library__empty { margin-top: 16px; }
.theme-library__sr-only { position: absolute; width: 1px; height: 1px; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; }
.theme-delete-backdrop { position: fixed; inset: 0; z-index: 10002; display: grid; place-items: center; box-sizing: border-box; padding: 16px; background: rgba(15, 23, 42, .46); }
.theme-delete-dialog { box-sizing: border-box; width: min(100%, 460px); border-radius: 8px; background: #fff; color: #334155; padding: 22px; box-shadow: 0 20px 48px rgba(15, 23, 42, .24); }
.theme-delete-dialog h2, .theme-delete-dialog p { margin: 0; }.theme-delete-dialog h2 { color: #0f172a; font-size: 20px; overflow-wrap: anywhere; }.theme-delete-dialog p { margin-top: 9px; color: #64748b; font-size: 14px; line-height: 1.55; }.theme-delete-dialog .theme-delete-dialog__error { color: #b91c1c; }
.theme-delete-dialog > div { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }.theme-delete-dialog button { min-height: 36px; border: 1px solid #dce7e1; border-radius: 6px; background: #fff; color: #334155; font: inherit; font-size: 13px; font-weight: 700; padding: 0 13px; cursor: pointer; }.theme-delete-dialog button:disabled { cursor: not-allowed; opacity: .55; }.theme-delete-dialog .theme-delete-dialog__danger { border-color: #dc2626; background: #dc2626; color: #fff; }

@media (max-width: 760px) {
  .theme-library__header, .theme-card { align-items: stretch; flex-direction: column; }
  .theme-library__create { align-self: flex-start; }
  .theme-card__actions { flex-wrap: wrap; justify-content: flex-start; }
}

@media (max-width: 420px) {
  .theme-library__header h1 { font-size: 26px; }
  .theme-card { padding: 14px; }
}
</style>
