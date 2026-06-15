<template>
  <el-card class="tag-panel" shadow="never">
    <template #header>
      <div class="panel-header">
        <span class="panel-title">🎯 推荐模式</span>
        <el-radio-group v-model="selected.mode" size="small" @change="handleModeChange">
          <el-radio-button label="single">常规推荐</el-radio-button>
          <el-radio-button label="multi">多人配菜</el-radio-button>
        </el-radio-group>
      </div>
    </template>

    <!-- 1. 常规推荐模式 -->
    <div v-if="selected.mode === 'single'">
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
    </div>

    <!-- 2. 多人配菜模式 -->
    <div v-else>
      <!-- 共享属性：场景、预算、时段 -->
      <div class="shared-row">
        <el-form label-position="top" size="small">
          <el-row :gutter="10">
            <el-col :span="8">
              <el-form-item label="用餐场景">
                <el-select v-model="selected.diningScene" placeholder="选择场景">
                  <el-option v-for="opt in SCENE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="预算等级">
                <el-select v-model="selected.budgetLevel" placeholder="选择预算">
                  <el-option v-for="opt in BUDGET_OPTIONS_NO_ALL" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="用餐时段">
                <el-select v-model="selected.mealTime" placeholder="选择时段">
                  <el-option v-for="opt in MEAL_TIME_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>

      <!-- 顾客列表 -->
      <div class="guests-section">
        <div class="guests-header">
          <span class="section-title">顾客画像与要求 ({{ selected.guests.length }}人)</span>
          <el-button type="primary" size="small" plain @click="addGuest">添加顾客</el-button>
        </div>

        <el-collapse v-model="activeGuestNames" class="guest-collapse">
          <el-collapse-item v-for="(guest, idx) in selected.guests" :key="idx" :name="idx.toString()">
            <template #title>
              <div class="guest-title-header">
                <span class="guest-name-text">👤 {{ guest.name }}</span>
                <el-button type="danger" size="small" link @click.stop="removeGuest(idx)">删除</el-button>
              </div>
            </template>
            <div class="guest-form">
              <div class="guest-input-row">
                <span class="g-label">顾客称呼</span>
                <el-input v-model="guest.name" size="small" placeholder="如：顾客A、王先生" style="width: 150px" />
              </div>

              <!-- 忌口/不吃 -->
              <div class="guest-field">
                <span class="g-field-label">忌口不吃</span>
                <el-checkbox-group v-model="guest.avoidIngredients" size="small">
                  <el-checkbox v-for="opt in GUEST_AVOID_OPTIONS" :key="opt" :label="opt" />
                </el-checkbox-group>
              </div>

              <!-- 过敏源 -->
              <div class="guest-field">
                <span class="g-field-label">过敏源</span>
                <el-checkbox-group v-model="guest.allergens" size="small">
                  <el-checkbox v-for="opt in GUEST_ALLERGEN_OPTIONS" :key="opt" :label="opt" />
                </el-checkbox-group>
              </div>

              <!-- 疾病禁忌 -->
              <div class="guest-field">
                <span class="g-field-label">疾病禁忌</span>
                <el-checkbox-group v-model="guest.diseases" size="small">
                  <el-checkbox v-for="opt in GUEST_DISEASE_OPTIONS" :key="opt" :label="opt" />
                </el-checkbox-group>
              </div>

              <!-- 习惯/宗教 -->
              <div class="guest-field">
                <span class="g-field-label">饮食习惯</span>
                <el-checkbox-group v-model="guest.dietLifestyles" size="small">
                  <el-checkbox v-for="opt in GUEST_LIFESTYLE_OPTIONS" :key="opt" :label="opt" />
                </el-checkbox-group>
              </div>

              <!-- 口味偏好 -->
              <div class="guest-field">
                <span class="g-field-label">口味偏好</span>
                <el-checkbox-group v-model="guest.tastes" size="small">
                  <el-checkbox v-for="opt in GUEST_TASTE_OPTIONS" :key="opt" :label="opt" />
                </el-checkbox-group>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>

    <div class="tag-footer">
      <el-button size="small" @click="reset">重置</el-button>
      <span class="selected-summary">已选: {{ summaryText }}</span>
    </div>
  </el-card>
</template>

<script setup>
import { reactive, computed, ref, watch } from 'vue'

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

const BUDGET_OPTIONS_NO_ALL = BUDGET_OPTIONS.filter(o => o.value !== '不限')

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

// 多人选项
const GUEST_AVOID_OPTIONS = ['辣', '香菜', '葱', '蒜', '牛肉', '羊肉']
const GUEST_ALLERGEN_OPTIONS = ['花生', '海鲜', '鸡蛋', '牛奶']
const GUEST_DISEASE_OPTIONS = ['痛风', '糖尿病', '高血压', '胃病', '术后']
const GUEST_LIFESTYLE_OPTIONS = ['清真', '素食', 'Keto', '减脂']
const GUEST_TASTE_OPTIONS = ['爱吃酸', '爱吃麻', '不爱甜', '喜欢嫩', '要下饭', '要清淡']

const activeGuestNames = ref(['0'])

const selected = reactive({
  mode: 'single',
  peopleCount: null,
  diningScene: null,
  tastePreferences: [],
  budgetLevel: null,
  dietaryRestriction: null,
  mealTime: null,
  guests: []
})

watch(() => selected.guests.length, (newVal) => {
  if (selected.mode === 'multi') {
    selected.peopleCount = newVal > 0 ? String(newVal) : null
  }
})

watch(() => selected.mode, (newVal) => {
  if (newVal === 'multi') {
    selected.peopleCount = selected.guests.length > 0 ? String(selected.guests.length) : null
  } else {
    selected.peopleCount = null
  }
})

function handleModeChange(mode) {
  if (mode === 'multi') {
    if (selected.guests.length === 0) {
      addGuest()
      addGuest() // default to 2 guests
    }
  } else {
    selected.guests = []
  }
}

function addGuest() {
  const nextLetter = String.fromCharCode(65 + selected.guests.length) // A, B, C...
  selected.guests.push({
    name: `顾客${nextLetter}`,
    avoidIngredients: [],
    allergens: [],
    diseases: [],
    dietLifestyles: [],
    tastes: []
  })
  activeGuestNames.value = [String(selected.guests.length - 1)]
}

function removeGuest(idx) {
  selected.guests.splice(idx, 1)
  selected.guests.forEach((g, i) => {
    if (g.name.match(/^顾客[A-Z]$/)) {
      g.name = `顾客${String.fromCharCode(65 + i)}`
    }
  })
}

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
  selected.guests = []
  if (selected.mode === 'multi') {
    addGuest()
    addGuest()
  }
}

const summaryText = computed(() => {
  if (selected.mode === 'single') {
    const parts = []
    if (selected.peopleCount) parts.push(PEOPLE_OPTIONS.find(o => o.value === selected.peopleCount)?.label)
    if (selected.diningScene) parts.push(selected.diningScene)
    if (selected.tastePreferences.length) parts.push(selected.tastePreferences.join('、'))
    if (selected.budgetLevel) parts.push(selected.budgetLevel)
    if (selected.dietaryRestriction) parts.push(selected.dietaryRestriction)
    if (selected.mealTime) parts.push(selected.mealTime)
    return parts.length ? parts.join(' · ') : '未选择'
  } else {
    const parts = []
    parts.push(`多人: ${selected.guests.length}人`)
    if (selected.diningScene) parts.push(selected.diningScene)
    if (selected.budgetLevel) parts.push(`预算:${selected.budgetLevel}`)
    if (selected.mealTime) parts.push(selected.mealTime)
    return parts.join(' · ')
  }
})

defineExpose({ selected })
</script>

<style scoped>
.tag-panel { margin-bottom: 16px; }
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}
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

.shared-row {
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #ebeef5;
}
.guests-section {
  margin-top: 12px;
}
.guests-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.guest-collapse {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}
.guest-title-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 12px;
}
.guest-name-text {
  font-weight: 600;
  font-size: 13px;
}
.guest-form {
  padding: 8px 12px;
}
.guest-input-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.g-label {
  font-size: 12px;
  color: #606266;
  width: 60px;
}
.guest-field {
  margin-bottom: 10px;
}
.g-field-label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
  font-weight: 500;
}
</style>
