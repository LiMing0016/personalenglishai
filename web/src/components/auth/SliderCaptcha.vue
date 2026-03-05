<template>
  <Teleport to="body">
    <Transition name="captcha-fade">
      <div v-if="visible" class="captcha-overlay" @click.self="$emit('close')">
        <div class="captcha-card">
          <div class="captcha-header">
            <span>请完成安全验证</span>
            <button class="captcha-close" @click="$emit('close')">&times;</button>
          </div>

          <div v-if="loading" class="captcha-loading">加载中...</div>

          <template v-else-if="bgImage">
            <div
              class="captcha-image-area"
              :style="{ width: bgWidth + 'px', height: bgHeight + 'px' }"
            >
              <img :src="bgImage" class="captcha-bg" draggable="false" @load="onBgLoad" />
              <img
                :src="pieceImage"
                class="captcha-piece"
                :style="{ left: sliderX + 'px' }"
                draggable="false"
              />
            </div>

            <div class="slider-bar" :class="statusClass">
              <div class="slider-track">
                <div class="slider-filled" :style="{ width: sliderX + 'px' }"></div>
                <div class="slider-tip" v-if="status === 'idle'">向右拖动滑块完成验证</div>
                <div class="slider-tip" v-else-if="status === 'success'">验证成功</div>
                <div class="slider-tip" v-else-if="status === 'fail'">验证失败，请重试</div>
              </div>
              <div
                class="slider-thumb"
                :style="{ left: sliderX + 'px' }"
                @mousedown.prevent="startDrag"
                @touchstart.prevent="startDrag"
              >
                <span v-if="status === 'idle'">&rarr;</span>
                <span v-else-if="status === 'success'">&check;</span>
                <span v-else-if="status === 'fail'">&times;</span>
                <span v-else>&rarr;</span>
              </div>
            </div>
          </template>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import { authApi } from '@/api/auth'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  close: []
  verified: [token: string]
}>()

const bgImage = ref('')
const pieceImage = ref('')
const captchaId = ref('')
const loading = ref(false)
const sliderX = ref(0)
const status = ref<'idle' | 'dragging' | 'success' | 'fail'>('idle')
const statusClass = ref('')
const bgWidth = 300
const bgHeight = 150

let dragging = false
let startX = 0
const maxX = bgWidth - 50 // piece image width ~50px

watch(() => props.visible, (v) => {
  if (v) {
    fetchCaptcha()
  } else {
    reset()
  }
})

async function fetchCaptcha() {
  loading.value = true
  status.value = 'idle'
  sliderX.value = 0
  try {
    const res = await authApi.getCaptcha()
    const data = res.data
    if (data) {
      captchaId.value = data.captchaId
      bgImage.value = data.bgImage
      pieceImage.value = data.pieceImage
    }
  } catch {
    // silently fail, user can close and retry
  } finally {
    loading.value = false
  }
}

function onBgLoad() {
  // background image loaded
}

function startDrag(e: MouseEvent | TouchEvent) {
  if (status.value === 'success') return
  dragging = true
  status.value = 'dragging'
  statusClass.value = ''
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  startX = clientX - sliderX.value

  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', endDrag)
  document.addEventListener('touchmove', onDrag)
  document.addEventListener('touchend', endDrag)
}

function onDrag(e: MouseEvent | TouchEvent) {
  if (!dragging) return
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  let x = clientX - startX
  if (x < 0) x = 0
  if (x > maxX) x = maxX
  sliderX.value = x
}

async function endDrag() {
  if (!dragging) return
  dragging = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', endDrag)

  // verify
  try {
    const res = await authApi.verifyCaptcha({
      captchaId: captchaId.value,
      x: Math.round(sliderX.value),
    })
    const data = res.data
    if (data?.verified && data.captchaToken) {
      status.value = 'success'
      statusClass.value = 'slider-success'
      setTimeout(() => {
        emit('verified', data.captchaToken as string)
      }, 500)
    } else {
      onFail()
    }
  } catch {
    onFail()
  }
}

function onFail() {
  status.value = 'fail'
  statusClass.value = 'slider-fail shake'
  setTimeout(() => {
    fetchCaptcha()
  }, 800)
}

function reset() {
  bgImage.value = ''
  pieceImage.value = ''
  captchaId.value = ''
  sliderX.value = 0
  status.value = 'idle'
  statusClass.value = ''
}

onUnmounted(() => {
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', endDrag)
})
</script>

<style scoped>
.captcha-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
}

.captcha-card {
  background: #1e2233;
  border-radius: 16px;
  padding: 20px;
  width: 340px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.captcha-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  color: rgba(225, 235, 255, 0.9);
  font-size: 15px;
  font-weight: 600;
}

.captcha-close {
  background: none;
  border: none;
  color: rgba(225, 235, 255, 0.5);
  font-size: 22px;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.captcha-close:hover {
  color: rgba(225, 235, 255, 0.9);
}

.captcha-loading {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(225, 235, 255, 0.5);
  font-size: 14px;
}

.captcha-image-area {
  position: relative;
  overflow: hidden;
  border-radius: 8px;
  margin: 0 auto;
  user-select: none;
}

.captcha-bg {
  width: 100%;
  height: 100%;
  display: block;
}

.captcha-piece {
  position: absolute;
  top: 0;
  height: 100%;
  pointer-events: none;
}

/* ── Slider Bar ── */

.slider-bar {
  position: relative;
  margin-top: 12px;
  height: 40px;
  user-select: none;
}

.slider-track {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  overflow: hidden;
}

.slider-filled {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: rgba(53, 192, 255, 0.15);
  border-radius: 20px 0 0 20px;
  transition: none;
}

.slider-tip {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: rgba(225, 235, 255, 0.4);
  pointer-events: none;
}

.slider-thumb {
  position: absolute;
  top: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #35c0ff, #6f6bff);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(53, 192, 255, 0.3);
  transition: box-shadow 0.2s;
  z-index: 1;
}

.slider-thumb:active {
  cursor: grabbing;
  box-shadow: 0 2px 16px rgba(53, 192, 255, 0.5);
}

/* ── Status Styles ── */

.slider-success .slider-track {
  border-color: rgba(76, 217, 100, 0.4);
}

.slider-success .slider-filled {
  background: rgba(76, 217, 100, 0.15);
}

.slider-success .slider-thumb {
  background: linear-gradient(135deg, #4cd964, #34c759);
  box-shadow: 0 2px 8px rgba(76, 217, 100, 0.4);
}

.slider-success .slider-tip {
  color: rgba(76, 217, 100, 0.8);
}

.slider-fail .slider-track {
  border-color: rgba(255, 110, 110, 0.4);
}

.slider-fail .slider-filled {
  background: rgba(255, 110, 110, 0.15);
}

.slider-fail .slider-thumb {
  background: linear-gradient(135deg, #ff6b6b, #ff4757);
  box-shadow: 0 2px 8px rgba(255, 110, 110, 0.4);
}

.slider-fail .slider-tip {
  color: rgba(255, 176, 176, 0.8);
}

/* ── Shake Animation ── */

.shake {
  animation: shake 0.4s ease-in-out;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-6px); }
  40% { transform: translateX(6px); }
  60% { transform: translateX(-4px); }
  80% { transform: translateX(4px); }
}

/* ── Transition ── */

.captcha-fade-enter-active,
.captcha-fade-leave-active {
  transition: opacity 0.2s ease;
}

.captcha-fade-enter-from,
.captcha-fade-leave-to {
  opacity: 0;
}
</style>
