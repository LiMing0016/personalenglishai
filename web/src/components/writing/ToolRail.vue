<template>
  <div class="tool-rail">
    <button
      v-for="item in items"
      :key="item.mode"
      type="button"
      class="rail-btn"
      :class="{ active: activePanel === item.mode }"
      :title="item.title"
      @click="$emit('select', item.mode)"
    >
      <span class="rail-icon" aria-hidden="true">
        <!-- Evaluate: 三颗星（深蓝小 + 青绿大 + 浅青小） -->
        <svg v-if="item.mode === 'score'" width="22" height="22" viewBox="0 0 32 32" fill="none">
          <path d="M7.5 6.5l1.8 3.6-1.8 3.6-1.8-3.6z" class="ic-accent"/>
          <path d="M7.2 5l2.3 4.8 5.2.8-3.7 3.7.8 5.2L7.2 17l-4.6 2.5.8-5.2L0 10.6l5.2-.8z" class="ic-accent" opacity="0.9"/>
          <path d="M16.5 2l3 6 6.5 1-4.7 4.7 1 6.5-5.8-3.2-5.8 3.2 1-6.5L7 9l6.5-1z" class="ic-primary"/>
          <path d="M25.5 7l1.5 3 3.4.5-2.4 2.5.5 3.4-3-1.6-3 1.6.5-3.4-2.4-2.5 3.4-.5z" class="ic-light"/>
        </svg>
        <!-- Grammar: 文档 + 绿色对勾圆圈 -->
        <svg v-else-if="item.mode === 'grammarCheck'" width="22" height="22" viewBox="0 0 32 32" fill="none">
          <rect x="4" y="2" width="18" height="24" rx="2.5" class="ic-stroke" stroke-width="1.8" fill="white"/>
          <line x1="8.5" y1="8" x2="18" y2="8" class="ic-primary-stroke" stroke-width="2" stroke-linecap="round"/>
          <line x1="8.5" y1="13" x2="18" y2="13" class="ic-primary-stroke" stroke-width="2" stroke-linecap="round"/>
          <line x1="8.5" y1="18" x2="14" y2="18" class="ic-primary-stroke" stroke-width="2" stroke-linecap="round"/>
          <circle cx="23" cy="23" r="7" class="ic-primary" />
          <path d="M19.5 23l2.5 2.5 4.5-4.5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
        </svg>
        <!-- Polish: 文档 + 笔 + 星星 -->
        <svg v-else-if="item.mode === 'rewrite'" width="22" height="22" viewBox="0 0 32 32" fill="none">
          <rect x="2" y="2" width="18" height="24" rx="2.5" class="ic-stroke" stroke-width="1.8" fill="white"/>
          <line x1="6" y1="8" x2="16" y2="8" class="ic-primary-stroke" stroke-width="2" stroke-linecap="round"/>
          <line x1="6" y1="13" x2="16" y2="13" class="ic-primary-stroke" stroke-width="2" stroke-linecap="round"/>
          <line x1="6" y1="18" x2="13" y2="18" class="ic-primary-stroke" stroke-width="2" stroke-linecap="round"/>
          <rect x="21" y="11" width="4" height="16" rx="1" transform="rotate(-30 23 19)" class="ic-accent" opacity="0.85"/>
          <rect x="22.5" y="10" width="4" height="3" rx="0.8" transform="rotate(-30 24.5 11.5)" class="ic-stroke" stroke-width="0" fill="#2d6a7a"/>
          <circle cx="22" cy="5" r="1.2" class="ic-light"/>
          <circle cx="26" cy="3" r="0.8" class="ic-primary"/>
          <circle cx="28" cy="7" r="1" class="ic-light"/>
        </svg>
        <!-- Structure: 范文 -->
        <svg v-else-if="item.mode === 'structure'" width="22" height="22" viewBox="0 0 32 32" fill="none">
          <rect x="3" y="3" width="18" height="22" rx="2.8" class="ic-stroke" stroke-width="1.6" fill="white"/>
          <rect x="11" y="8" width="18" height="22" rx="2.8" class="ic-primary" opacity="0.16"/>
          <line x1="7" y1="9" x2="17" y2="9" class="ic-primary-stroke" stroke-width="1.8" stroke-linecap="round"/>
          <line x1="7" y1="14" x2="17" y2="14" class="ic-primary-stroke" stroke-width="1.8" stroke-linecap="round"/>
          <line x1="15" y1="14" x2="25" y2="14" class="ic-light-stroke" stroke-width="1.8" stroke-linecap="round"/>
          <line x1="15" y1="19" x2="25" y2="19" class="ic-light-stroke" stroke-width="1.8" stroke-linecap="round"/>
        </svg>
        <!-- 模版: 拼图块 -->
        <svg v-else-if="item.mode === 'improve'" width="22" height="22" viewBox="0 0 32 32" fill="none">
          <path d="M14 2h-8a2 2 0 0 0-2 2v10h4.5a2.5 2.5 0 0 1 0 5H4v9a2 2 0 0 0 2 2h10v-4.5a2.5 2.5 0 0 1 5 0V30h5a2 2 0 0 0 2-2v-8h-4.5a2.5 2.5 0 0 1 0-5H28V4a2 2 0 0 0-2-2H16.5v4a2.5 2.5 0 0 1-5 0V2z" class="ic-stroke" stroke-width="1.5" fill="none"/>
          <path d="M14 2h-8a2 2 0 0 0-2 2v10h4.5a2.5 2.5 0 0 1 0 5H4v9a2 2 0 0 0 2 2h10v-4.5a2.5 2.5 0 0 1 5 0V30" class="ic-accent" opacity="0.7"/>
          <path d="M21 30h5a2 2 0 0 0 2-2v-8h-4.5a2.5 2.5 0 0 1 0-5H28V4a2 2 0 0 0-2-2H16.5v4a2.5 2.5 0 0 1-5 0V2" class="ic-light" opacity="0.6"/>
        </svg>
        <!-- 素材: 灯泡 + 光线 -->
        <svg v-else-if="item.mode === 'explain'" width="22" height="22" viewBox="0 0 32 32" fill="none">
          <line x1="16" y1="1" x2="16" y2="4.5" class="ic-light-stroke" stroke-width="1.8" stroke-linecap="round"/>
          <line x1="6" y1="5" x2="8.2" y2="7.2" class="ic-light-stroke" stroke-width="1.8" stroke-linecap="round"/>
          <line x1="26" y1="5" x2="23.8" y2="7.2" class="ic-light-stroke" stroke-width="1.8" stroke-linecap="round"/>
          <line x1="2" y1="16" x2="5.5" y2="16" class="ic-light-stroke" stroke-width="1.8" stroke-linecap="round"/>
          <line x1="26.5" y1="16" x2="30" y2="16" class="ic-light-stroke" stroke-width="1.8" stroke-linecap="round"/>
          <path d="M16 7a9 9 0 0 0-5.5 16.1V26h11v-2.9A9 9 0 0 0 16 7z" class="ic-stroke" stroke-width="1.8" fill="white"/>
          <path d="M16 7a9 9 0 0 0-5.5 16.1V26h11v-2.9A9 9 0 0 0 16 7z" class="ic-light" opacity="0.15"/>
          <rect x="11.5" y="27" width="9" height="2" rx="1" class="ic-stroke" stroke-width="1.5" fill="white"/>
          <rect x="12.5" y="30" width="7" height="1.5" rx="0.75" class="ic-primary" opacity="0.5"/>
        </svg>
        <!-- Translate: 地球 + A文 -->
        <svg v-else-if="item.mode === 'translate'" width="22" height="22" viewBox="0 0 32 32" fill="none">
          <circle cx="14" cy="14" r="11" class="ic-stroke" stroke-width="1.8" fill="white"/>
          <circle cx="14" cy="14" r="11" class="ic-light" opacity="0.15"/>
          <ellipse cx="14" cy="14" rx="5" ry="11" class="ic-stroke" stroke-width="1.5" fill="none"/>
          <line x1="3" y1="10" x2="25" y2="10" class="ic-stroke" stroke-width="1.3"/>
          <line x1="3" y1="18" x2="25" y2="18" class="ic-stroke" stroke-width="1.3"/>
          <rect x="19" y="19" width="12" height="12" rx="3" class="ic-primary"/>
          <text x="21.5" y="28" font-size="6.5" font-weight="800" fill="white" font-family="Arial,sans-serif">A</text>
          <text x="26" y="28.5" font-size="5.5" font-weight="700" fill="white" font-family="Arial,sans-serif">文</text>
        </svg>
        <!-- AI Coach: 机器人 -->
        <svg v-else-if="item.mode === 'aiNote'" width="22" height="22" viewBox="0 0 32 32" fill="none">
          <rect x="5" y="10" width="22" height="16" rx="5" class="ic-stroke" stroke-width="1.8" fill="white"/>
          <rect x="5" y="10" width="22" height="16" rx="5" class="ic-light" opacity="0.12" stroke="none"/>
          <circle cx="12" cy="18" r="2.8" class="ic-stroke" stroke-width="1.5" fill="white"/>
          <circle cx="12" cy="18" r="1.2" class="ic-primary"/>
          <circle cx="20" cy="18" r="2.8" class="ic-stroke" stroke-width="1.5" fill="white"/>
          <circle cx="20" cy="18" r="1.2" class="ic-primary"/>
          <line x1="16" y1="5" x2="16" y2="10" class="ic-stroke" stroke-width="1.8"/>
          <circle cx="16" cy="4" r="2" class="ic-primary"/>
          <line x1="3" y1="17" x2="5" y2="17" class="ic-stroke" stroke-width="2" stroke-linecap="round"/>
          <circle cx="2" cy="17" r="1.5" class="ic-primary" opacity="0.6"/>
          <line x1="27" y1="17" x2="29" y2="17" class="ic-stroke" stroke-width="2" stroke-linecap="round"/>
          <circle cx="30" cy="17" r="1.5" class="ic-primary" opacity="0.6"/>
        </svg>
      </span>
      <span class="rail-label">{{ item.label }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
export type PanelMode =
  | 'score'
  | 'rewrite'
  | 'grammarCheck'
  | 'structure'
  | 'improve'
  | 'explain'
  | 'translate'
  | 'aiNote'

defineProps<{
  activePanel: PanelMode | null
}>()

defineEmits<{
  select: [mode: PanelMode]
}>()

const items: { mode: PanelMode; label: string; title: string }[] = [
  { mode: 'score', label: '评价', title: '作文评价' },
  { mode: 'grammarCheck', label: '语法', title: '实时语法检查' },
  { mode: 'rewrite', label: '润色', title: '分级润色' },
  { mode: 'structure', label: '范文', title: '范文' },
  { mode: 'improve', label: '模版', title: '写作模版' },
  { mode: 'explain', label: '素材', title: '写作素材' },
  { mode: 'translate', label: '翻译', title: '翻译' },
  { mode: 'aiNote', label: 'AI助手', title: 'AI 助手' },
]
</script>

<style scoped>
.tool-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: 52px;
  padding: 10px 4px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
  box-sizing: border-box;
}
.rail-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 40px;
  min-height: 40px;
  padding: 4px 6px;
  font-size: 11px;
  color: #0f766e;
  background: transparent;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.rail-btn:hover {
  background: rgba(15, 118, 110, 0.08);
  color: #115e59;
}
.rail-btn.active {
  background: #0f766e;
  color: #fff;
}
.rail-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  line-height: 1;
}
.rail-label {
  margin-top: 2px;
  line-height: 1;
}

/* ── Icon color tokens (normal state) ── */
.ic-primary { fill: #2a9d8f; }
.ic-accent  { fill: #1a6b7a; }
.ic-light   { fill: #7ecec5; }

.ic-stroke        { stroke: #2d6a7a; }
.ic-primary-stroke { stroke: #2a9d8f; }
.ic-light-stroke   { stroke: #7ecec5; }

/* ── Active state: all icon colors → white ── */
.rail-btn.active .ic-primary,
.rail-btn.active .ic-accent,
.rail-btn.active .ic-light { fill: #fff; }

.rail-btn.active .ic-primary { opacity: 1; }
.rail-btn.active .ic-accent  { opacity: 0.85; }
.rail-btn.active .ic-light   { opacity: 0.6; }

.rail-btn.active .ic-stroke,
.rail-btn.active .ic-primary-stroke,
.rail-btn.active .ic-light-stroke { stroke: #fff; }

.rail-btn.active .ic-stroke        { opacity: 1; }
.rail-btn.active .ic-light-stroke   { opacity: 0.6; }

/* Active 状态下白底元素变透明 */
.rail-btn.active svg rect[fill="white"],
.rail-btn.active svg circle[fill="white"],
.rail-btn.active svg path[fill="white"] {
  fill: rgba(255, 255, 255, 0.15);
}

/* Active 状态下文字保持白色 */
.rail-btn.active svg text { fill: #fff !important; }
</style>


