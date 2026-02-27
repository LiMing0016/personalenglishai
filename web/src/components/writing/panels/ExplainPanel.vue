<template>
  <div class="explain-panel">
    <div v-for="(item, i) in items" :key="i" class="explain-item">
      <h4 class="explain-term">{{ item.term }}</h4>
      <p class="explain-en">{{ item.en }}</p>
      <p class="explain-zh">{{ item.zh }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
const items = [
  {
    term: 'Past Simple',
    en: 'Used for completed actions in the past. E.g. "I went to school yesterday."',
    zh: '一般过去时：表示过去发生的已完成的动作。例如："我昨天去了学校。"',
  },
  {
    term: 'Article (a/an)',
    en: 'Use "an" before vowel sounds, "a" before consonant sounds. E.g. "an owl", "a book".',
    zh: '冠词 a/an：元音音素前用 an，辅音音素前用 a。如 "an owl"、"a book"。',
  },
  {
    term: 'Collocation',
    en: '"Do homework" is the correct collocation; "make homework" is not standard.',
    zh: '搭配："做作业" 应使用 do homework，make homework 非标准用法。',
  },
]
</script>

<style scoped>
.explain-panel {
  padding: 16px;
}
.explain-item {
  padding: 14px 0;
  border-top: 1px solid #e5e7eb;
}
.explain-item:first-child {
  border-top: none;
  padding-top: 0;
}
.explain-term {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  margin: 0 0 8px;
}
.explain-en {
  font-size: 13px;
  color: #374151;
  line-height: 1.5;
  margin: 0 0 6px;
}
.explain-zh {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
  margin: 0;
}
</style>
