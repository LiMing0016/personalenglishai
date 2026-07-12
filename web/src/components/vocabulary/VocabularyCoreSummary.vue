<template>
  <section class="core-summary" aria-labelledby="vocabulary-core-title">
    <div class="core-summary__heading">
      <span>核心词典数据</span>
      <h3 id="vocabulary-core-title">{{ core.term }}</h3>
    </div>

    <div class="core-summary__section">
      <h4>音标</h4>
      <p v-if="!core.phonetics.length" class="core-summary__empty">暂无音标</p>
      <ul v-else class="core-summary__phonetics">
        <li v-for="phonetic in core.phonetics" :key="`${phonetic.region}-${phonetic.text}`">
          <span>{{ regionLabel(phonetic.region) }}</span>
          <strong>{{ phonetic.text }}</strong>
        </li>
      </ul>
    </div>

    <div class="core-summary__section">
      <h4>释义</h4>
      <p v-if="!core.senses.length" class="core-summary__empty">暂无释义</p>
      <div v-else class="core-summary__senses">
        <section v-for="(sense, senseIndex) in core.senses" :key="`${sense.partOfSpeech}-${senseIndex}`">
          <h5>{{ sense.partOfSpeech || '未标注词性' }}</h5>
          <p v-if="!sense.meanings.length" class="core-summary__empty">暂无双语释义</p>
          <ol v-else>
            <li v-for="(meaning, meaningIndex) in sense.meanings" :key="meaningIndex">
              <p>{{ meaning.definitionEn || '暂无英文释义' }}</p>
              <p>{{ meaning.definitionZh || '暂无中文释义' }}</p>
            </li>
          </ol>
        </section>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { VocabularyCoreContent } from '@/api/vocabulary'

defineProps<{ core: VocabularyCoreContent }>()

function regionLabel(region: VocabularyCoreContent['phonetics'][number]['region']) {
  return ({ uk: '英', us: '美', other: '其他' } as const)[region]
}
</script>

<style scoped>
.core-summary { min-width: 0; display: grid; gap: 16px; }
.core-summary__heading span, .core-summary__section h4 { color: #64748b; font-size: 12px; font-weight: 800; }
.core-summary__heading h3 { margin: 3px 0 0; color: #0f172a; font-size: 20px; overflow-wrap: anywhere; }
.core-summary__section { min-width: 0; }
.core-summary__section h4 { margin: 0 0 8px; }
.core-summary__empty { margin: 0; color: #64748b; font-size: 13px; }
.core-summary__phonetics { display: flex; flex-wrap: wrap; gap: 8px; margin: 0; padding: 0; list-style: none; }
.core-summary__phonetics li { min-width: 0; display: flex; gap: 7px; align-items: baseline; padding: 7px 9px; border: 1px solid #dce7e1; border-radius: 6px; background: #f8fafc; }
.core-summary__phonetics span { color: #047857; font-size: 11px; font-weight: 800; }
.core-summary__phonetics strong { color: #334155; font-size: 14px; overflow-wrap: anywhere; }
.core-summary__senses { display: grid; gap: 10px; }
.core-summary__senses section { min-width: 0; padding-left: 10px; border-left: 2px solid #a7f3d0; }
.core-summary__senses h5 { margin: 0 0 6px; color: #0f172a; font-size: 13px; }
.core-summary__senses ol { display: grid; gap: 8px; margin: 0; padding-left: 20px; }
.core-summary__senses li, .core-summary__senses p { min-width: 0; }
.core-summary__senses p { margin: 0; color: #334155; font-size: 13px; line-height: 1.55; overflow-wrap: anywhere; }
.core-summary__senses p + p { margin-top: 2px; color: #64748b; }
</style>
