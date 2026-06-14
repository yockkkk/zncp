<template>
  <div class="slider-captcha" :class="{ verified: passed }">
    <div v-if="!passed" class="slider-track" ref="trackRef">
      <div class="slider-bg" :style="{ width: sliderLeft + 'px' }"></div>
      <div class="slider-text" :class="{ moving: isMoving }">
        {{ isMoving ? '' : '按住滑块，拖动到最右边' }}
      </div>
      <div
        class="slider-btn"
        :style="{ left: sliderLeft + 'px' }"
        @mousedown="onStart"
        @touchstart.prevent="onStart"
      >
        <span class="slider-arrow">→</span>
      </div>
    </div>
    <div v-else class="verified-mark">
      <el-icon :size="16"><Check /></el-icon>
      <span>验证通过</span>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Check } from '@element-plus/icons-vue'

const emit = defineEmits(['verify'])
const passed = ref(false)
const isMoving = ref(false)
const trackRef = ref(null)
const sliderLeft = ref(0)
const maxSlide = 260

let startX = 0

function onStart(e) {
  if (passed.value) return
  isMoving.value = true
  startX = e.type === 'touchstart' ? e.touches[0].clientX : e.clientX

  const onMove = (ev) => {
    const clientX = ev.type === 'touchmove' ? ev.touches[0].clientX : ev.clientX
    const diff = clientX - startX
    sliderLeft.value = Math.max(0, Math.min(diff, maxSlide))
  }

  const onEnd = () => {
    isMoving.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onEnd)
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onEnd)

    if (sliderLeft.value >= maxSlide - 5) {
      passed.value = true
      emit('verify', true)
    } else {
      sliderLeft.value = 0
    }
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onEnd)
  document.addEventListener('touchmove', onMove)
  document.addEventListener('touchend', onEnd)
}

function reset() {
  passed.value = false
  sliderLeft.value = 0
  isMoving.value = false
}

defineExpose({ reset })
</script>

<style scoped>
.slider-captcha {
  width: 100%;
  height: 38px;
  user-select: none;
}
.slider-track {
  position: relative;
  width: 100%;
  height: 38px;
  background: #eceff4;
  border-radius: 19px;
  overflow: hidden;
  border: 1px solid #dcdfe6;
}
.slider-bg {
  position: absolute;
  left: 0; top: 0; bottom: 0;
  background: linear-gradient(90deg, #67c23a, #85ce61);
  border-radius: 19px 0 0 19px;
  transition: width 0.05s linear;
}
.slider-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #909399;
  pointer-events: none;
}
.slider-text.moving { color: transparent; }
.slider-btn {
  position: absolute;
  top: 2px;
  width: 34px; height: 34px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0,0,0,0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  z-index: 2;
}
.slider-btn:active { cursor: grabbing; box-shadow: 0 2px 10px rgba(0,0,0,0.3); }
.slider-arrow { color: #909399; font-size: 14px; font-weight: bold; }
.verified-mark {
  display: flex; align-items: center; gap: 6px;
  height: 38px; justify-content: center;
  color: #67c23a; font-size: 13px; font-weight: 500;
  background: #f0f9eb; border-radius: 6px; border: 1px solid #c2e7b0;
}
.verified { }
</style>
