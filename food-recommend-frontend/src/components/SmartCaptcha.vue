<template>
  <div class="smart-captcha">
    <!-- 模式 0: 滑块验证 -->
    <div v-if="mode === 'slider' && !passed" class="captcha-slider">
      <div class="slider-track" ref="trackRef">
        <div class="slider-bg" :style="{ width: sliderLeft + 'px' }"></div>
        <div class="slider-text" :class="{ moving: isMoving }">
          {{ isMoving ? '' : '→ 按住滑块，拖动到最右边 →' }}
        </div>
        <div class="slider-btn" :style="{ left: sliderLeft + 'px' }"
          @mousedown="onSliderStart" @touchstart.prevent="onSliderStart">
          <span class="slider-arrow">→→</span>
        </div>
      </div>
    </div>

    <!-- 模式 1: 数学计算 -->
    <div v-else-if="mode === 'math' && !passed" class="captcha-math">
      <div class="math-question">
        <span class="math-icon">🔢</span>
        <span class="math-text">{{ mathQuestion }}</span>
        <span class="math-hint">= ?</span>
      </div>
      <div class="math-options">
        <span v-for="opt in mathOptions" :key="opt"
          class="math-opt" :class="{ wrong: opt === wrongAnswer }"
          @click="checkMathAnswer(opt)">
          {{ opt }}
        </span>
      </div>
    </div>

    <!-- 模式 2: 点击选中指定文字 -->
    <div v-else-if="mode === 'clickText' && !passed" class="captcha-click">
      <div class="click-prompt">
        <span>👆</span> 请依次点击：<b>{{ clickTarget }}</b>
      </div>
      <div class="click-grid">
        <span v-for="(ch, i) in clickChars" :key="i"
          class="click-char"
          :class="{ selected: clickSelected.includes(i), wrong: clickWrongIdx === i }"
          @click="onClickChar(i)">
          {{ ch }}
        </span>
      </div>
      <div class="click-progress">
        已选 {{ clickSelected.length }} / {{ clickOrder.length }}
      </div>
    </div>

    <!-- 验证通过 -->
    <div v-if="passed" class="captcha-passed">
      <el-icon :size="16"><Check /></el-icon>
      <span>验证通过</span>
    </div>

    <!-- 模式切换提示 -->
    <div class="captcha-actions" v-if="!passed">
      <span class="mode-switch" @click="refreshCaptcha">🔄 换一种验证方式</span>
      <span class="attempt-hint" v-if="attempts > 0">失败 {{ attempts }} 次</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Check } from '@element-plus/icons-vue'

const emit = defineEmits(['verify'])
const props = defineProps({
  attempts: { type: Number, default: 0 }
})

const passed = ref(false)
const mode = ref('slider')
const isMoving = ref(false)
const sliderLeft = ref(0)
const maxSlide = 240

// 数学验证
const mathQuestion = ref('')
const mathAnswer = ref(0)
const mathOptions = ref([])
const wrongAnswer = ref(null)

// 点击验证
const clickTarget = ref('')
const clickOrder = ref([])
const clickChars = ref([])
const clickSelected = ref([])
const clickWrongIdx = ref(null)

onMounted(() => {
  pickMode()
})

function pickMode() {
  // 根据尝试次数动态切换验证方式
  const modes = ['slider', 'math', 'clickText']
  // 首次随机，失败后加大难度
  if (props.attempts === 0) {
    mode.value = modes[Math.floor(Math.random() * 2)] // slider or math
  } else if (props.attempts === 1) {
    mode.value = modes[Math.floor(Math.random() * 3)]
  } else {
    mode.value = modes[Math.floor(Math.random() * 3)]
  }
  initCaptcha()
}

function initCaptcha() {
  if (mode.value === 'slider') {
    initSlider()
  } else if (mode.value === 'math') {
    initMath()
  } else if (mode.value === 'clickText') {
    initClickText()
  }
}

// ===== 滑块 =====
function initSlider() {
  sliderLeft.value = 0
  isMoving.value = false
}
function onSliderStart(e) {
  if (passed.value) return
  isMoving.value = true
  const startX = e.type === 'touchstart' ? e.touches[0].clientX : e.clientX
  const onMove = (ev) => {
    const cx = ev.type === 'touchmove' ? ev.touches[0].clientX : ev.clientX
    sliderLeft.value = Math.max(0, Math.min(cx - startX, maxSlide))
  }
  const onEnd = () => {
    isMoving.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onEnd)
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onEnd)
    if (sliderLeft.value >= maxSlide - 5) {
      onPass()
    } else {
      sliderLeft.value = 0
      emit('verify', false)
    }
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onEnd)
  document.addEventListener('touchmove', onMove)
  document.addEventListener('touchend', onEnd)
}

// ===== 数学计算 =====
function initMath() {
  wrongAnswer.value = null
  const a = Math.floor(Math.random() * 20) + 5
  const b = Math.floor(Math.random() * 15) + 1
  const ops = [
    { sign: '×', result: a * b },
    { sign: '+', result: a + b },
    { sign: '−', result: a - b }
  ]
  const op = ops[Math.floor(Math.random() * ops.length)]
  mathQuestion.value = `${a} ${op.sign} ${b}`
  mathAnswer.value = op.result

  // 生成干扰选项
  const opts = new Set([op.result])
  while (opts.size < 4) {
    const offset = Math.floor(Math.random() * 20) - 10
    const fake = op.result + offset
    if (fake > 0 && fake !== op.result) opts.add(fake)
  }
  mathOptions.value = [...opts].sort(() => Math.random() - 0.5)
}
function checkMathAnswer(val) {
  if (val === mathAnswer.value) {
    onPass()
  } else {
    wrongAnswer.value = val
    setTimeout(() => { wrongAnswer.value = null; refreshCaptcha() }, 800)
    emit('verify', false)
  }
}

// ===== 点击文字 =====
const targets = [
  { label: '「餐」字', chars: '餐饮推荐系统智能助理解析建模器' },
  { label: '「推」字', chars: '推荐引擎智能餐饮平台助理解析方案' },
  { label: '「智」字', chars: '智慧餐饮推荐系统工程助理解析模块' },
  { label: '「饮」字', chars: '饮食推荐餐饮顾问解析学习训练饮酒' }
]
function initClickText() {
  clickSelected.value = []
  clickWrongIdx.value = null
  const t = targets[Math.floor(Math.random() * targets.length)]
  clickTarget.value = t.label
  clickOrder.value = []
  const chars = t.chars.split('')
  // 找到目标字的所有位置
  const targetChar = t.label.charAt(1)
  chars.forEach((c, i) => { if (c === targetChar) clickOrder.value.push(i) })
  // 打乱显示
  clickChars.value = [...chars].sort(() => Math.random() - 0.5)
}
function onClickChar(idx) {
  if (passed.value) return
  const expectedIdx = clickOrder.value[clickSelected.value.length]
  if (idx === expectedIdx) {
    clickSelected.value.push(idx)
    if (clickSelected.value.length === clickOrder.value.length) {
      onPass()
    }
  } else {
    clickWrongIdx.value = idx
    setTimeout(() => { clickWrongIdx.value = null; clickSelected.value = []; emit('verify', false) }, 600)
  }
}

function onPass() {
  passed.value = true
  emit('verify', true)
}

function refreshCaptcha() {
  pickMode()
}

function reset() {
  passed.value = false
  clickSelected.value = []
  clickWrongIdx.value = null
  sliderLeft.value = 0
  pickMode()
}

defineExpose({ reset })
</script>

<style scoped>
.smart-captcha { width: 100%; }
.captcha-passed {
  display: flex; align-items: center; gap: 6px; justify-content: center;
  height: 38px; color: #67c23a; font-size: 13px; font-weight: 500;
  background: #f0f9eb; border-radius: 6px; border: 1px solid #c2e7b0;
}

/* === 滑块 === */
.captcha-slider { width: 100%; height: 38px; user-select: none; }
.slider-track {
  position: relative; width: 100%; height: 38px;
  background: #eceff4; border-radius: 19px; overflow: hidden; border: 1px solid #dcdfe6;
}
.slider-bg {
  position: absolute; left: 0; top: 0; bottom: 0;
  background: linear-gradient(90deg, #67c23a, #85ce61);
  border-radius: 19px 0 0 19px; transition: width 0.05s linear;
}
.slider-text {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  font-size: 12px; color: #909399; pointer-events: none; letter-spacing: 2px;
}
.slider-text.moving { color: transparent; }
.slider-btn {
  position: absolute; top: 2px; width: 34px; height: 34px;
  background: #fff; border-radius: 50%; box-shadow: 0 2px 6px rgba(0,0,0,0.2);
  display: flex; align-items: center; justify-content: center; cursor: grab; z-index: 2;
}
.slider-btn:active { cursor: grabbing; }
.slider-arrow { color: #909399; font-size: 12px; font-weight: bold; }

/* === 数学 === */
.captcha-math { }
.math-question {
  display: flex; align-items: center; gap: 10px; margin-bottom: 10px;
  font-size: 16px; font-weight: 600; color: #303133;
}
.math-icon { font-size: 20px; }
.math-text { letter-spacing: 3px; }
.math-hint { color: #409eff; }
.math-options { display: flex; gap: 10px; }
.math-opt {
  flex: 1; text-align: center; padding: 8px 0;
  border: 2px solid #dcdfe6; border-radius: 8px; cursor: pointer;
  font-size: 18px; font-weight: 700; color: #303133;
  transition: all 0.2s;
}
.math-opt:hover { border-color: #409eff; background: #ecf5ff; }
.math-opt.wrong { border-color: #f56c6c; background: #fef0f0; color: #f56c6c; animation: shake 0.4s; }

/* === 点击文字 === */
.captcha-click { }
.click-prompt { font-size: 13px; color: #606266; margin-bottom: 10px; }
.click-grid { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
.click-char {
  width: 38px; height: 38px; display: flex; align-items: center; justify-content: center;
  border: 2px solid #dcdfe6; border-radius: 6px; cursor: pointer; font-size: 16px;
  transition: all 0.2s;
}
.click-char:hover { border-color: #409eff; background: #ecf5ff; }
.click-char.selected { border-color: #67c23a; background: #f0f9eb; color: #67c23a; font-weight: 700; }
.click-char.wrong { border-color: #f56c6c; background: #fef0f0; color: #f56c6c; animation: shake 0.4s; }
.click-progress { font-size: 12px; color: #909399; }

/* === 通用 === */
.captcha-actions {
  display: flex; justify-content: space-between; margin-top: 8px;
}
.mode-switch {
  font-size: 12px; color: #909399; cursor: pointer;
}
.mode-switch:hover { color: #409eff; }
.attempt-hint { font-size: 12px; color: #f56c6c; }

@keyframes shake {
  0%,100% { transform: translateX(0); }
  25% { transform: translateX(-5px); }
  75% { transform: translateX(5px); }
}
</style>
