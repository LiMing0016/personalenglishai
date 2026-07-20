<template>
  <section class="term-review" aria-labelledby="term-review-title">
    <header>
      <div>
        <h3 id="term-review-title">候选词</h3>
        <span>{{ candidates.length }} 个</span>
      </div>
      <div>
        <button type="button" :disabled="!candidates.length" @click="emit('command', { type: 'select_all' })">全选</button>
        <button type="button" :disabled="!candidates.length" @click="emit('command', { type: 'clear_selection' })">清空</button>
      </div>
    </header>

    <p v-if="candidateLimitReached" class="term-review__warning" role="status">
      单次最多保留 30 个图片候选词，请先处理当前结果。
    </p>

    <div v-if="candidates.length" class="term-review__list">
      <article v-for="candidate in candidates" :key="candidate.id" class="term-review__item">
        <template v-if="candidate.resolution === 'unresolved'">
          <div class="term-review__typo-heading">
            <label>
              <input
                type="checkbox"
                :checked="candidate.selected"
                :aria-label="`选择 ${candidate.observedText}`"
                @change="toggleCandidate(candidate.id, $event)"
              >
              <strong>{{ candidate.observedText }}</strong>
            </label>
            <span v-if="candidate.suggestions.some((item) => item.dictionaryVerified)">词典已验证</span>
          </div>
          <p>疑似拼写错误，请明确处理后再生成卡片。</p>
          <div class="term-review__suggestions">
            <button
              v-for="suggestion in candidate.suggestions"
              :key="suggestion.term"
              type="button"
              @click="emit('command', { type: 'apply_suggestion', candidateId: candidate.id, suggestion: suggestion.term })"
            >
              采用 {{ suggestion.term }}
            </button>
            <button type="button" @click="emit('command', { type: 'keep_original', candidateId: candidate.id })">保留原词</button>
            <button type="button" class="term-review__delete" @click="emit('command', { type: 'remove', candidateId: candidate.id })">删除</button>
          </div>
        </template>

        <template v-else>
          <label class="term-review__term">
            <input
              type="checkbox"
              :checked="candidate.selected"
              :aria-label="`选择 ${candidate.term}`"
              @change="toggleCandidate(candidate.id, $event)"
            >
            <input
              type="text"
              :value="candidate.term"
              :aria-label="`编辑词条 ${candidate.term}`"
              @change="updateTerm(candidate.id, $event)"
            >
          </label>
          <span class="term-review__source">{{ candidate.source === 'ocr_image' ? '图片' : '输入' }}</span>
          <button type="button" class="term-review__delete" @click="emit('command', { type: 'remove', candidateId: candidate.id })">删除</button>
        </template>
      </article>
    </div>
    <p v-else class="term-review__empty">输入或识别单词后，在这里统一确认。</p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { VocabularyRecognitionWarning } from '@/api/vocabulary'
import type { ImportCandidate } from '@/features/vocabulary/imageRecognition'

export type VocabularyReviewCommand =
  | { type: 'select_all' }
  | { type: 'clear_selection' }
  | { type: 'toggle_selected', candidateId: string, selected: boolean }
  | { type: 'update_term', candidateId: string, term: string }
  | { type: 'apply_suggestion', candidateId: string, suggestion: string }
  | { type: 'keep_original', candidateId: string }
  | { type: 'remove', candidateId: string }

const props = defineProps<{
  candidates: readonly ImportCandidate[]
  warnings: readonly VocabularyRecognitionWarning[]
}>()

const emit = defineEmits<{
  command: [command: VocabularyReviewCommand]
}>()

const candidateLimitReached = computed(() => props.warnings.includes('CANDIDATE_LIMIT_REACHED'))

function toggleCandidate(candidateId: string, event: Event) {
  emit('command', {
    type: 'toggle_selected',
    candidateId,
    selected: (event.target as HTMLInputElement).checked,
  })
}

function updateTerm(candidateId: string, event: Event) {
  emit('command', {
    type: 'update_term',
    candidateId,
    term: (event.target as HTMLInputElement).value,
  })
}
</script>

<style scoped>
.term-review { display: grid; min-width: 0; gap: 9px; }
.term-review header, .term-review header > div, .term-review__typo-heading, .term-review__typo-heading label, .term-review__suggestions { display: flex; align-items: center; gap: 8px; }
.term-review header { justify-content: space-between; }
.term-review h3 { margin: 0; color: #0f172a; font-size: 14px; }
.term-review header span { color: #64748b; font-size: 12px; }
.term-review header button, .term-review__suggestions button, .term-review__delete { min-height: 30px; border: 1px solid #dce7e1; border-radius: 6px; background: #fff; color: #475569; font: inherit; font-size: 12px; font-weight: 800; padding: 0 9px; cursor: pointer; }
.term-review header button:disabled { cursor: not-allowed; opacity: .5; }
.term-review__warning { margin: 0; border: 1px solid #fde68a; border-radius: 6px; background: #fffbeb; color: #92400e; font-size: 12px; padding: 8px 10px; }
.term-review__list { display: grid; max-height: 280px; overflow: auto; border: 1px solid #e5ece8; border-radius: 6px; }
.term-review__item { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 10px; min-width: 0; padding: 9px 10px; border-bottom: 1px solid #edf2f7; }
.term-review__item:last-child { border-bottom: 0; }
.term-review__term { display: grid; grid-template-columns: 18px minmax(0, 1fr); align-items: center; gap: 8px; min-width: 0; }
.term-review__term input[type='text'] { box-sizing: border-box; width: 100%; min-width: 0; min-height: 32px; border: 1px solid transparent; border-radius: 5px; background: transparent; color: #0f172a; font: inherit; font-weight: 700; padding: 0 7px; }
.term-review__term input[type='text']:focus { border-color: #14b8a6; background: #fff; outline: none; }
.term-review__source { color: #64748b; font-size: 11px; }
.term-review__item:has(.term-review__typo-heading) { grid-template-columns: 1fr; align-items: stretch; background: #fffbeb; }
.term-review__typo-heading { justify-content: space-between; }
.term-review__typo-heading strong { color: #0f172a; overflow-wrap: anywhere; }
.term-review__typo-heading > span { border-radius: 999px; background: #dcfce7; color: #047857; font-size: 11px; font-weight: 800; padding: 3px 7px; }
.term-review__item > p { margin: 0; color: #92400e; font-size: 12px; }
.term-review__suggestions { flex-wrap: wrap; }
.term-review__suggestions button { border-color: #a7c7b8; color: #047857; }
.term-review__suggestions .term-review__delete, .term-review__delete { border-color: #fecaca; color: #b91c1c; }
.term-review__empty { margin: 0; border: 1px dashed #dce7e1; border-radius: 6px; color: #64748b; font-size: 13px; padding: 18px; text-align: center; }
@media (max-width: 520px) { .term-review__item { grid-template-columns: minmax(0, 1fr) auto; }.term-review__source { display: none; }.term-review header { align-items: flex-start; }.term-review__suggestions button { flex: 1 1 auto; } }
</style>
