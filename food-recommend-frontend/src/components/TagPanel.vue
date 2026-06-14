<template>
  <el-card class="tag-panel" shadow="never">
    <template #header><span class="panel-title">🎯 顾客画像</span></template>

    <!-- 用餐人数 -->
    <div class="tag-row">
      <span class="tag-label">用餐人数</span>
      <div class="tag-group">
        <el-tag v-for="opt in PEOPLE_OPTIONS" :key="opt.value"
          :type="selected.peopleCount === opt.value ? 'primary' : 'info'"
          :effect="selected.peopleCount === opt.value ? 'dark' : 'plain'"
          class="tag-item" @click="selected.peopleCount = opt.value">
          {{ opt.label }}
        </el-tag>
      </div>
    </div>

    <!-- 用餐场景 -->
    <div class="tag-row">
      <span class="tag-label">用餐场景</span>
      <div class="tag-group">
        <el-tag v-for="opt in SCENE_OPTIONS" :key="opt.value"
          :type="selected.diningScene === opt.value ? 'success' : 'info'"
          :effect="selected.diningScene === opt.value ? 'dark' : 'plain'"
          class="tag-item" @click="selected.diningScene = opt.value">
          {{ opt.label }}
        </el-tag>
      </div>
    </div>

    <!-- 口味偏好（多选） -->
    <div class="tag-row">
      <span class="tag-label">口味偏好</span>
      <div class="tag-group">
        <el-tag v-for="opt in TASTE_OPTIONS" :key="opt.value"
          :type="selected.tastePreferences.includes(opt.value) ? 'warning' : 'info'"
          :effect="selected.tastePreferences.includes(opt.value) ? 'dark' : 'plain'"
          class="tag-item" @click="toggleTaste(opt.value)">
          {{ opt.label }}
        </el-tag>
      </div>
    </div>

    <!-- 预算等级 -->
    <div class="tag-row">
      <span class="tag-label">预算等级</span>
      <div class="tag-group">
        <el-tag v-for="opt in BUDGET_OPTIONS" :key="opt.value"
          :type="selected.budgetLevel === opt.value ? 'danger' : 'info'"
          :effect="selected.budgetLevel === opt.value ? 'dark' : 'plain'"
          class="tag-item" @click="selected.budgetLevel = opt.value">
          {{ opt.label }}
        </el-tag>
      </div>
    </div>

    <!-- 饮食限制 -->
    <div class="tag-row">
      <span class="tag-label">饮食限制</span>
      <div class="tag-group">
        <el-tag v-for="opt in DIETARY_OPTIONS" :key="opt.value"
          :type="selected.dietaryRestriction === opt.value ? 'primary' : 'info'"
          :effect="selected.dietaryRestriction === opt.value ? 'dark' : 'plain'"
          class="tag-item" @click="selected.dietaryRestriction = opt.value">
          {{ opt.label }}
        </el-tag>
      </div>
    </div>

    <!-- 用餐时段 -->
    <div class="tag-row">
      <span class="tag-label">用餐时段</span>
      <div class="tag-group">
        <el-tag v-for="opt in MEAL_TIME_OPTIONS" :key="opt.value"
          :type="selected.mealTime === opt.value ? 'success' : 'info'"
          :effect="selected.mealTime === opt.value ? 'dark' : 'plain'"
          class="tag-item" @click="selected.mealTime = opt.value">
          {{ opt.label }}
        </el-tag>
      </div>
    </div>

    <div class="tag-footer">
      <el-button size="small" @click="reset">重置</el-button>
      <span class="selected-summary">已选: {{ summaryText }}</span>
    </div>
  </el-card>
</template>

<script setup>
import { reactive, computed } from 'vue'

const PEOPLE_OPTIONS = [
  { label: '1人', value: '1' },
  { label: '2人', value: '2' },
  { label: '3-4人', value: '3-4' },
  { label: '5人+', value: '5+' }
]

const SCENE_OPTIONS = [
  { label: '便餐', value: '便餐' },
  { label: '约会', value: '约会' },
  { label: '商务', value: '商务' },
  { label: '家庭', value: '家庭' },
  { label: '朋友聚餐', value: '朋友聚餐' }
]

const TASTE_OPTIONS = [
  { label: '辣', value: '辣' },
  { label: '清淡', value: '清淡' },
  { label: '甜', value: '甜' },
  { label: '咸', value: '咸' },
  { label: '无偏好', value: '无偏好' }
]

const BUDGET_OPTIONS = [
  { label: '实惠', value: '实惠' },
  { label: '中等', value: '中等' },
  { label: '高端', value: '高端' },
  { label: '不限', value: '不限' }
]

const DIETARY_OPTIONS = [
  { label: '无限制', value: '无' },
  { label: '素食', value: '素食' },
  { label: '低脂', value: '低脂' },
  { label: '高蛋白', value: '高蛋白' }
]

const MEAL_TIME_OPTIONS = [
  { label: '早餐', value: '早餐' },
  { label: '午餐', value: '午餐' },
  { label: '晚餐', value: '晚餐' },
  { label: '夜宵', value: '夜宵' }
]

const selected = reactive({
  peopleCount: null,
  diningScene: null,
  tastePreferences: [],
  budgetLevel: null,
  dietaryRestriction: null,
  mealTime: null
})

function toggleTaste(value) {
  const idx = selected.tastePreferences.indexOf(value)
  if (idx >= 0) {
    selected.tastePreferences.splice(idx, 1)
  } else {
    selected.tastePreferences.push(value)
  }
}

function reset() {
  selected.peopleCount = null
  selected.diningScene = null
  selected.tastePreferences = []
  selected.budgetLevel = null
  selected.dietaryRestriction = null
  selected.mealTime = null
}

const summaryText = computed(() => {
  const parts = []
  if (selected.peopleCount) parts.push(PEOPLE_OPTIONS.find(o => o.value === selected.peopleCount)?.label)
  if (selected.diningScene) parts.push(selected.diningScene)
  if (selected.tastePreferences.length) parts.push(selected.tastePreferences.join('、'))
  if (selected.budgetLevel) parts.push(selected.budgetLevel)
  if (selected.dietaryRestriction) parts.push(selected.dietaryRestriction)
  if (selected.mealTime) parts.push(selected.mealTime)
  return parts.length ? parts.join(' · ') : '未选择'
})

defineExpose({ selected })
</script>

<style scoped>
.tag-panel { margin-bottom: 16px; }
.panel-title { font-weight: 600; font-size: 15px; }
.tag-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 12px;
}
.tag-label {
  width: 80px;
  flex-shrink: 0;
  line-height: 28px;
  font-size: 13px;
  color: #606266;
}
.tag-group { display: flex; flex-wrap: wrap; gap: 6px; }
.tag-item { cursor: pointer; user-select: none; }
.tag-item:hover { transform: scale(1.05); transition: transform 0.15s; }
.tag-footer {
  display: flex; justify-content: space-between; align-items: center;
  margin-top: 8px; padding-top: 12px; border-top: 1px solid #ebeef5;
}
.selected-summary { font-size: 12px; color: #909399; }
</style>
