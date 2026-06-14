<template>
  <div class="smart-captcha">
    <!-- 模式 0: 滑块验证 -->
    <div v-if="mode === 'slider' && !passed" class="captcha-slider">
      <div class="slider-track">
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
        👆 在下方文字中，按顺序点击所有 <b class="target-highlight">{{ clickTarget }}</b>
        <span class="click-hint">（共 {{ clickTargetCount }} 个）</span>
      </div>
      <div class="click-grid">
        <span v-for="(item, i) in clickItems" :key="i"
          class="click-char"
          :class="{ selected: item.selected, wrong: clickWrongIdx === i }"
          @click="onClickChar(i)">
          {{ item.char }}
        </span>
      </div>
      <div class="click-progress">
        已找到 {{ selectedCount }} / {{ clickTargetCount }}
      </div>
    </div>

    <!-- 模式 3: 图片方向判断 -->
    <div v-else-if="mode === 'direction' && !passed" class="captcha-direction">
      <div class="dir-prompt">
        <span>🧭</span> 请点击箭头指向 <b>{{ dirQuestion }}</b> 的图片
      </div>
      <div class="dir-grid">
        <div v-for="(item, i) in dirItems" :key="i"
          class="dir-card"
          :class="{ selected: i === dirSelected, wrong: i === dirWrong }"
          @click="checkDirection(i)">
          <span class="dir-emoji">{{ item.emoji }}</span>
          <span class="dir-label">{{ item.label }}</span>
        </div>
      </div>
    </div>

    <!-- 验证通过 -->
    <div v-if="passed" class="captcha-passed">
      <el-icon :size="16"><Check /></el-icon>
      <span>验证通过</span>
    </div>

    <!-- 底部操作栏 -->
    <div class="captcha-actions" v-if="!passed">
      <span class="mode-switch" @click.stop="refreshCaptcha">🔄 换一种验证方式</span>
      <span class="attempt-hint" v-if="failCount > 0">失败 {{ failCount }} 次</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Check } from '@element-plus/icons-vue'

const emit = defineEmits(['verify'])

const passed = ref(false)
const mode = ref('slider')
const failCount = ref(0)  // 内部追踪真实失败次数
const isMoving = ref(false)
const sliderLeft = ref(0)
const maxSlide = 240

// 数学验证
const mathQuestion = ref('')
const mathAnswer = ref(0)
const mathOptions = ref([])
const wrongAnswer = ref(null)

// 点击文字验证（修复：使用对象数组保持索引一致性）
const clickTarget = ref('')
const clickTargetCount = ref(0)
const clickItems = ref([])
const clickWrongIdx = ref(null)
const selectedCount = ref(0)
let clickOrder = []  // 正确的点击顺序（字符值）

// 方向验证
const dirQuestion = ref('')
const dirItems = ref([])
const dirSelected = ref(null)
const dirWrong = ref(null)

onMounted(() => {
  pickMode()
})

function pickMode() {
  const modes = ['slider', 'math', 'clickText', 'direction']
  // 根据失败次数增加模式多样性
  if (failCount.value === 0) {
    mode.value = modes[Math.floor(Math.random() * 3)] // 前三种
  } else {
    mode.value = modes[Math.floor(Math.random() * 4)]
  }
  initCaptcha()
}

function initCaptcha() {
  switch (mode.value) {
    case 'slider': initSlider(); break
    case 'math': initMath(); break
    case 'clickText': initClickText(); break
    case 'direction': initDirection(); break
  }
}

// ===== 滑块（修复：轻触不计数）=====
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
    } else if (sliderLeft.value > 30) {
      // 拖了一半以上才算真实尝试失败
      sliderLeft.value = 0
      failCount.value++
      emit('verify', false)
    } else {
      // 轻触/微动，不计数，直接回弹
      sliderLeft.value = 0
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
  const opts = new Set([op.result])
  while (opts.size < 4) {
    const offset = Math.floor(Math.random() * 20) - 10
    const fake = op.result + offset
    if (fake > 0 && fake !== op.result) opts.add(fake)
  }
  mathOptions.value = [...opts].sort(() => Math.random() - 0.5)
}
function checkMathAnswer(val) {
  if (passed.value) return
  if (val === mathAnswer.value) {
    onPass()
  } else {
    wrongAnswer.value = val
    failCount.value++
    emit('verify', false)
    setTimeout(() => { wrongAnswer.value = null; refreshCaptcha() }, 800)
  }
}

// ===== 点击文字（修复：短字符串+多出现+清晰提示）=====
const textTargets = [
  { label: '智', chars: '智慧智能智造智力智联' },
  { label: '推', chars: '推进推动推理推演推手' },
  { label: '餐', chars: '餐饮早餐午餐晚餐餐桌' },
  { label: '系', chars: '系统系列联系体系系数' }
]
function initClickText() {
  clickSelected.value = []
  clickWrongIdx.value = null
  selectedCount.value = 0
  const t = textTargets[Math.floor(Math.random() * textTargets.length)]
  clickTarget.value = t.label
  const chars = t.chars.split('')
  clickTargetCount.value = chars.filter(c => c === t.label).length
  // 构建带位置信息的对象数组，随机排列
  clickItems.value = chars
    .map((char, idx) => ({ char, originalIdx: idx, selected: false }))
    .sort(() => Math.random() - 0.5)
  // 正确的点击顺序：按 originalIdx 升序点击所有目标字符
  clickOrder = chars
    .map((c, i) => c === t.label ? i : -1)
    .filter(i => i >= 0)
}
function onClickChar(displayIdx) {
  if (passed.value) return
  const item = clickItems.value[displayIdx]
  if (item.selected) return  // 已选过的跳过

  // 检查这个位置的字符是否是下一个应该点的
  const nextOriginalIdx = clickOrder[selectedCount.value]
  if (item.originalIdx === nextOriginalIdx) {
    item.selected = true
    selectedCount.value++
    if (selectedCount.value >= clickTargetCount.value) {
      onPass()
    }
  } else {
    clickWrongIdx.value = displayIdx
    failCount.value++
    emit('verify', false)
    setTimeout(() => {
      clickWrongIdx.value = null
      // 重置选中状态
      clickItems.value.forEach(it => it.selected = false)
      selectedCount.value = 0
    }, 600)
  }
}

// ===== 方向判断（新模式）=====
function initDirection() {
  dirSelected.value = null
  dirWrong.value = null
  const directions = [
    { emoji: '🐱', label: '小猫', dir: '上' },
    { emoji: '🐟', label: '鱼', dir: '下' },
    { emoji: '🌳', label: '树', dir: '左' },
    { emoji: '☕', label: '咖啡', dir: '右' }
  ]
  const target = directions[Math.floor(Math.random() * directions.length)]
  dirQuestion.value = target.dir
  dirItems.value = directions.sort(() => Math.random() - 0.5)
  // 存储正确答案
  dirItems.value.forEach((item, i) => {
    if (item.dir === target.dir) dirSelected.value = null
  })
}
function checkDirection(i) {
  if (passed.value) return
  if (dirItems.value[i].dir === dirQuestion.value) {
    dirSelected.value = i
    onPass()
  } else {
    dirWrong.value = i
    failCount.value++
    emit('verify', false)
    setTimeout(() => { dirWrong.value = null; refreshCaptcha() }, 800)
  }
}

// ===== 通用 =====
function onPass() {
  passed.value = true
  emit('verify', true)
}

function refreshCaptcha() {
  // 仅仅切换模式，不算失败
  pickMode()
}

function reset() {
  passed.value = false
  failCount.value = 0
  sliderLeft.value = 0
  clickItems.value.forEach(it => it.selected = false)
  selectedCount.value = 0
  clickWrongIdx.value = null
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
.math-question { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; font-size: 16px; font-weight: 600; color: #303133; }
.math-icon { font-size: 20px; }
.math-text { letter-spacing: 3px; }
.math-hint { color: #409eff; }
.math-options { display: flex; gap: 10px; }
.math-opt {
  flex: 1; text-align: center; padding: 8px 0;
  border: 2px solid #dcdfe6; border-radius: 8px; cursor: pointer;
  font-size: 18px; font-weight: 700; color: #303133; transition: all 0.2s;
}
.math-opt:hover { border-color: #409eff; background: #ecf5ff; }
.math-opt.wrong { border-color: #f56c6c; background: #fef0f0; color: #f56c6c; animation: shake 0.4s; }

/* === 点击文字 === */
.click-prompt { font-size: 13px; color: #606266; margin-bottom: 10px; line-height: 1.8; }
.target-highlight {
  color: #e6a23c; background: #fdf6ec; padding: 2px 6px; border-radius: 4px;
  font-size: 16px;
}
.click-hint { font-size: 12px; color: #909399; }
.click-grid { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; justify-content: center; }
.click-char {
  width: 38px; height: 38px; display: flex; align-items: center; justify-content: center;
  border: 2px solid #dcdfe6; border-radius: 6px; cursor: pointer; font-size: 16px; transition: all 0.2s;
}
.click-char:hover { border-color: #409eff; background: #ecf5ff; }
.click-char.selected { border-color: #67c23a; background: #f0f9eb; color: #67c23a; font-weight: 700; }
.click-char.wrong { border-color: #f56c6c; background: #fef0f0; color: #f56c6c; animation: shake 0.4s; }
.click-progress { font-size: 12px; color: #909399; }

/* === 方向判断 === */
.dir-prompt { font-size: 13px; color: #606266; margin-bottom: 10px; }
.dir-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.dir-card {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 12px 8px; border: 2px solid #dcdfe6; border-radius: 8px; cursor: pointer; transition: all 0.2s;
}
.dir-card:hover { border-color: #409eff; background: #ecf5ff; }
.dir-card.selected { border-color: #67c23a; background: #f0f9eb; }
.dir-card.wrong { border-color: #f56c6c; background: #fef0f0; animation: shake 0.4s; }
.dir-emoji { font-size: 32px; }
.dir-label { font-size: 12px; color: #909399; }

/* === 通用 === */
.captcha-actions { display: flex; justify-content: space-between; margin-top: 8px; }
.mode-switch { font-size: 12px; color: #909399; cursor: pointer; user-select: none; padding: 2px 4px; }
.mode-switch:hover { color: #409eff; }
.attempt-hint { font-size: 12px; color: #f56c6c; }

@keyframes shake {
  0%,100% { transform: translateX(0); }
  25% { transform: translateX(-5px); }
  75% { transform: translateX(5px); }
}
</style>
