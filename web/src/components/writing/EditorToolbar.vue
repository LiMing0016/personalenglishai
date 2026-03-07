<template>
  <div class="editor-menu" @mousedown.stop>
    <!-- 撤销 / 重做 -->
    <button class="menu-item" :disabled="!editor?.can().undo()" @click="editor?.chain().focus().undo().run()">
      <svg class="menu-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
      <span>撤销</span>
      <span class="menu-shortcut">Ctrl Z</span>
    </button>
    <button class="menu-item" :disabled="!editor?.can().redo()" @click="editor?.chain().focus().redo().run()">
      <svg class="menu-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.13-9.36L23 10"/></svg>
      <span>重做</span>
      <span class="menu-shortcut">Ctrl Y</span>
    </button>

    <div class="menu-sep" />

    <!-- 格式开关 -->
    <button class="menu-item" :class="{ active: editor?.isActive('bold') }" @click="editor?.chain().focus().toggleBold().run()">
      <span class="menu-icon"><strong>B</strong></span>
      <span>粗体</span>
      <span class="menu-shortcut">Ctrl B</span>
    </button>
    <button class="menu-item" :class="{ active: editor?.isActive('italic') }" @click="editor?.chain().focus().toggleItalic().run()">
      <span class="menu-icon"><em>I</em></span>
      <span>斜体</span>
      <span class="menu-shortcut">Ctrl I</span>
    </button>
    <button class="menu-item" :class="{ active: editor?.isActive('underline') }" @click="editor?.chain().focus().toggleUnderline().run()">
      <span class="menu-icon" style="text-decoration: underline">U</span>
      <span>下划线</span>
      <span class="menu-shortcut">Ctrl U</span>
    </button>

    <div class="menu-sep" />

    <!-- 字体 -->
    <div class="menu-sub" @mouseenter="openSub = 'font'" @mouseleave="openSub = null">
      <button class="menu-item">
        <span class="menu-icon">Aa</span>
        <span>字体</span>
        <svg class="menu-arrow" width="8" height="8" viewBox="0 0 8 8"><path d="M2 1l4 3-4 3" fill="none" stroke="currentColor" stroke-width="1.5"/></svg>
      </button>
      <div v-if="openSub === 'font'" class="sub-panel">
        <button
          v-for="f in fontFamilies"
          :key="f.value"
          class="sub-item"
          :class="{ active: currentFontFamily === f.value }"
          :style="{ fontFamily: f.value }"
          @click="onFontFamily(f.value)"
        >{{ f.label }}</button>
        <div class="menu-sep" />
        <button class="sub-item" @click="onFontFamily('')">恢复默认</button>
      </div>
    </div>

    <!-- 字号 -->
    <div class="menu-sub" @mouseenter="openSub = 'size'" @mouseleave="openSub = null">
      <button class="menu-item">
        <span class="menu-icon" style="font-size: 13px">A<small style="font-size:9px">a</small></span>
        <span>字号</span>
        <svg class="menu-arrow" width="8" height="8" viewBox="0 0 8 8"><path d="M2 1l4 3-4 3" fill="none" stroke="currentColor" stroke-width="1.5"/></svg>
      </button>
      <div v-if="openSub === 'size'" class="sub-panel sub-panel-size">
        <button
          v-for="s in fontSizes"
          :key="s"
          class="sub-item"
          :class="{ active: currentFontSize === s + 'px' }"
          @click="onFontSize(s + 'px')"
        >{{ s }}px</button>
        <div class="menu-sep" />
        <button class="sub-item" @click="onFontSize('')">恢复默认</button>
      </div>
    </div>

    <!-- 文字颜色 -->
    <div class="menu-sub" @mouseenter="openSub = 'color'" @mouseleave="openSub = null">
      <button class="menu-item">
        <span class="menu-icon">
          <span class="color-a" :style="{ borderBottomColor: currentColor || '#1a1a1a' }">A</span>
        </span>
        <span>文字颜色</span>
        <svg class="menu-arrow" width="8" height="8" viewBox="0 0 8 8"><path d="M2 1l4 3-4 3" fill="none" stroke="currentColor" stroke-width="1.5"/></svg>
      </button>
      <div v-if="openSub === 'color'" class="sub-panel sub-panel-color">
        <button class="sub-item" @click="onColorPick('')">
          <span class="color-a" style="border-bottom-color: #000">A</span>
          自动（默认）
        </button>
        <div class="menu-sep" />
        <div class="color-section-label">主题颜色</div>
        <div class="color-grid">
          <button
            v-for="c in themeColors"
            :key="c"
            class="color-swatch"
            :class="{ selected: currentColor === c }"
            :style="{ background: c }"
            :title="c"
            @click="onColorPick(c)"
          />
        </div>
        <div class="color-section-label">标准色</div>
        <div class="color-grid">
          <button
            v-for="c in standardColors"
            :key="c"
            class="color-swatch"
            :class="{ selected: currentColor === c }"
            :style="{ background: c }"
            :title="c"
            @click="onColorPick(c)"
          />
        </div>
        <div class="menu-sep" />
        <label class="sub-item color-custom">
          其他颜色...
          <input type="color" class="color-custom-input" :value="currentColor || '#000000'" @input="onColorPick(($event.target as HTMLInputElement).value)" />
        </label>
      </div>
    </div>

    <div class="menu-sep" />

    <!-- 清除格式 -->
    <button class="menu-item" @click="editor?.chain().focus().unsetAllMarks().run()">
      <span class="menu-icon" style="opacity: 0.6">T&#x338;</span>
      <span>清除格式</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Editor } from '@tiptap/vue-3'

const props = defineProps<{
  editor: Editor | undefined
}>()

const openSub = ref<string | null>(null)

const themeColors = [
  '#000000', '#1a1a2e', '#1e3a5f', '#274c77', '#0d6efd',
  '#c62828', '#e65100', '#f9a825', '#2e7d32', '#00838f',
  '#333333', '#2c3e6b', '#2a5f8f', '#3a7abf', '#4da3ff',
  '#d32f2f', '#ef6c00', '#fbc02d', '#388e3c', '#00acc1',
  '#666666', '#4a6fa5', '#5b8fb9', '#6db3f8', '#99ccff',
  '#e57373', '#ffb74d', '#fff176', '#81c784', '#4dd0e1',
  '#999999', '#8aaad0', '#9ec5e0', '#b3d9ff', '#cce5ff',
  '#ef9a9a', '#ffcc80', '#fff59d', '#a5d6a7', '#80deea',
]

const standardColors = [
  '#c00000', '#ff0000', '#ffc000', '#ffff00', '#92d050',
  '#00b050', '#00b0f0', '#0070c0', '#002060', '#7030a0',
]

const fontFamilies = [
  { label: 'Georgia', value: 'Georgia, serif' },
  { label: 'Times New Roman', value: "'Times New Roman', serif" },
  { label: 'Arial', value: 'Arial, sans-serif' },
  { label: 'Helvetica', value: 'Helvetica, sans-serif' },
  { label: 'Verdana', value: 'Verdana, sans-serif' },
  { label: 'Calibri', value: 'Calibri, sans-serif' },
  { label: 'Cambria', value: 'Cambria, serif' },
  { label: 'Garamond', value: 'Garamond, serif' },
  { label: 'Courier New', value: "'Courier New', monospace" },
]

const fontSizes = [12, 14, 16, 18, 20, 22, 24, 28, 32, 36]

const currentFontFamily = computed(() => props.editor?.getAttributes('textStyle').fontFamily ?? '')
const currentFontSize = computed(() => props.editor?.getAttributes('textStyle').fontSize ?? '')
const currentColor = computed(() => props.editor?.getAttributes('textStyle').color ?? '')

function onFontFamily(value: string) {
  if (!props.editor) return
  value ? props.editor.chain().focus().setFontFamily(value).run()
        : props.editor.chain().focus().unsetFontFamily().run()
}

function onFontSize(value: string) {
  if (!props.editor) return
  value ? props.editor.chain().focus().setFontSize(value).run()
        : props.editor.chain().focus().unsetFontSize().run()
}

function onColorPick(value: string) {
  if (!props.editor) return
  value ? props.editor.chain().focus().setColor(value).run()
        : props.editor.chain().focus().unsetColor().run()
}
</script>

<style scoped>
.editor-menu {
  min-width: 220px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.14);
  padding: 6px 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 14px;
  animation: menu-in 0.12s ease-out;
}
@keyframes menu-in {
  from { opacity: 0; transform: translateY(-6px); }
  to   { opacity: 1; transform: translateY(0); }
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 16px;
  border: none;
  background: transparent;
  color: #374151;
  font-size: 14px;
  cursor: pointer;
  text-align: left;
  transition: background 0.1s;
}
.menu-item:hover:not(:disabled) {
  background: #f3f4f6;
}
.menu-item:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.menu-item.active {
  color: #0369a1;
  font-weight: 600;
}

.menu-icon {
  width: 20px;
  text-align: center;
  font-size: 15px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.menu-shortcut {
  margin-left: auto;
  font-size: 12px;
  color: #9ca3af;
}

.menu-arrow {
  margin-left: auto;
  opacity: 0.5;
}

.menu-sep {
  height: 1px;
  background: #f3f4f6;
  margin: 4px 12px;
}

/* ── 子菜单 ── */
.menu-sub {
  position: relative;
}
.sub-panel {
  position: absolute;
  right: 100%;
  top: -6px;
  min-width: 160px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  padding: 6px 0;
  margin-right: 4px;
  z-index: 10;
}
.sub-panel-size {
  min-width: 100px;
}
.sub-panel-color {
  min-width: 240px;
  padding: 8px 12px;
}
.sub-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 14px;
  border: none;
  background: transparent;
  color: #374151;
  font-size: 13px;
  cursor: pointer;
  text-align: left;
  transition: background 0.1s;
}
.sub-item:hover {
  background: #f3f4f6;
}
.sub-item.active {
  color: #0369a1;
  font-weight: 600;
}

/* ── 颜色面板 ── */
.color-a {
  font-weight: 700;
  font-size: 15px;
  border-bottom: 3px solid;
  line-height: 1;
  padding-bottom: 1px;
}
.color-section-label {
  font-size: 11px;
  font-weight: 600;
  color: #9ca3af;
  margin: 6px 0 4px;
}
.color-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 3px;
}
.color-swatch {
  width: 19px;
  height: 19px;
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 3px;
  cursor: pointer;
  padding: 0;
  transition: transform 0.1s, box-shadow 0.1s;
}
.color-swatch:hover {
  transform: scale(1.3);
  box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.15);
  z-index: 1;
}
.color-swatch.selected {
  box-shadow: 0 0 0 2px #0369a1;
}
.color-custom {
  margin-top: 4px;
}
.color-custom-input {
  width: 18px;
  height: 18px;
  border: none;
  padding: 0;
  cursor: pointer;
  background: transparent;
  margin-left: auto;
}
</style>
