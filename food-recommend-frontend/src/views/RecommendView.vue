<template>
  <div class="recommend-page">
    <!-- 顶部渐变 banner -->
    <div class="page-banner">
      <div class="banner-content">
        <h1>智味 AI 餐饮推荐</h1>
        <p>上传照片，AI 秒级读懂用餐场景，为您精准推荐</p>
      </div>
    </div>

    <div class="main-grid">
      <!-- 左侧上传区 -->
      <div class="left-panel">
        <!-- 图片上传卡片 -->
        <el-card shadow="never" class="upload-card">
          <template #header>
            <div class="card-hd">
              <el-icon color="#409eff"><Camera /></el-icon>
              <span>上传顾客图片</span>
            </div>
          </template>

          <div
            class="drop-zone"
            :class="{ 'has-image': store.previewUrl, 'dragging': isDragging }"
            @dragover.prevent="isDragging = true"
            @dragleave="isDragging = false"
            @drop.prevent="onDrop"
            @click="!store.previewUrl && $refs.fileInput.click()"
          >
            <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="onFilePicked" />
            <div v-if="!store.previewUrl" class="drop-placeholder">
              <div class="drop-icon">
                <el-icon :size="48" color="#c0c4cc"><UploadFilled /></el-icon>
              </div>
              <p class="drop-tip">拖拽或点击上传图片</p>
              <p class="drop-sub">支持 JPG、PNG，建议选用清晰的用餐场景照片</p>
            </div>
            <template v-else>
              <img :src="store.previewUrl" class="preview-img" />
              <div class="preview-actions">
                <el-button size="small" round @click.stop="$refs.fileInput.click()">
                  <el-icon><Edit /></el-icon> 更换图片
                </el-button>
                <el-button size="small" round type="danger" @click.stop="resetAll">
                  <el-icon><Delete /></el-icon> 清除
                </el-button>
              </div>
            </template>
          </div>
        </el-card>

        <!-- 偏好标签快选 -->
        <el-card shadow="never" class="pref-card">
          <template #header>
            <div class="card-hd">
              <el-icon color="#e6a23c"><Star /></el-icon>
              <span>口味偏好快选</span>
              <el-text size="small" type="info" style="margin-left:auto">可多选</el-text>
            </div>
          </template>
          <div class="tag-grid">
            <div
              v-for="tag in preferTags"
              :key="tag.label"
              class="prefer-tag"
              :class="{ active: store.selectedTags.includes(tag.label) }"
              @click="toggleTag(tag.label)"
            >
              <span class="tag-icon">{{ tag.icon }}</span>
              <span>{{ tag.label }}</span>
            </div>
          </div>
        </el-card>

        <!-- 特殊备注 -->
        <el-card shadow="never" class="remark-card">
          <template #header>
            <div class="card-hd">
              <el-icon color="#67c23a"><Edit /></el-icon>
              <span>特殊备注</span>
              <el-text size="small" type="info" style="margin-left:auto">备注优先级最高</el-text>
            </div>
          </template>
          <el-input
            v-model="store.remark"
            type="textarea"
            :rows="3"
            placeholder="例如：要辣一点的菜、口味新颖独特、不要太油腻、适合减肥的..."
            maxlength="100"
            show-word-limit
            resize="none"
          />
        </el-card>

        <!-- 推荐按钮 -->
        <el-button
          type="primary"
          size="large"
          :loading="store.loading"
          :disabled="!store.file"
          class="btn-start"
          @click="startRecommend"
        >
          <el-icon v-if="!store.loading"><MagicStick /></el-icon>
          <span>{{ store.loading ? 'AI 正在分析推荐中...' : '开始智能推荐' }}</span>
        </el-button>

        <!-- 加载步骤提示 -->
        <transition name="fade">
          <div v-if="store.loading" class="loading-steps">
            <div v-for="(step, i) in loadingSteps" :key="i"
              class="loading-step"
              :class="{ active: store.currentStep === i, done: store.currentStep > i }"
            >
              <el-icon v-if="store.currentStep > i" color="#67c23a"><CircleCheck /></el-icon>
              <el-icon v-else-if="store.currentStep === i" class="spin" color="#409eff"><Loading /></el-icon>
              <el-icon v-else color="#c0c4cc"><CirclePlus /></el-icon>
              <span>{{ step }}</span>
            </div>
            <el-button class="stop-btn" size="small" round @click="stopRecommend">
              <el-icon><CloseBold /></el-icon> 停止
            </el-button>
          </div>
        </transition>
      </div>

      <!-- 右侧结果区 -->
      <div class="right-panel">
        <div v-if="!store.result && !store.loading" class="empty-state">
          <el-empty description="" :image-size="100">
            <template #image>
              <div class="empty-icon">🍽️</div>
            </template>
          </el-empty>
          <p class="empty-title">上传图片，开始智能推荐</p>
          <p class="empty-sub">AI 将自动分析用餐人数、场景和偏好</p>
        </div>

        <template v-if="store.result">
          <!-- 用户画像 -->
          <el-card shadow="never" class="section-card profile-card">
            <template #header>
              <div class="card-hd">
                <el-icon color="#409eff"><User /></el-icon>
                <span>顾客画像分析</span>
                <el-tag size="small" type="success" round style="margin-left:auto">AI 识别完成</el-tag>
              </div>
            </template>
            <div class="profile-grid">
              <div class="p-item" v-for="item in profileItems" :key="item.label">
                <div class="p-icon">{{ item.icon }}</div>
                <div>
                  <div class="p-label">{{ item.label }}</div>
                  <div class="p-val">{{ item.value || '-' }}</div>
                </div>
              </div>
            </div>
            <div v-if="store.result.userProfile.possiblePreferences?.length" class="pref-tags-row">
              <span class="p-label">偏好标签：</span>
              <el-tag v-for="t in store.result.userProfile.possiblePreferences" :key="t" size="small" round type="info">{{ t }}</el-tag>
            </div>
            <div v-if="store.finalRemark" class="remark-badge">
              <el-icon><StarFilled /></el-icon>
              用户特别要求：{{ store.finalRemark }}
            </div>
          </el-card>

          <!-- 推荐总结 -->
          <div v-if="store.result.summary" class="summary-block">
            <el-icon color="#67c23a"><ChatLineRound /></el-icon>
            <span>{{ store.result.summary }}</span>
          </div>

          <!-- 推荐菜品 -->
          <div class="dishes-title">
            <el-icon color="#e6a23c"><Food /></el-icon>
            <span>推荐菜品</span>
            <el-tag size="small" round>共 {{ store.result.recommendations?.length || 0 }} 道</el-tag>
          </div>

          <transition-group name="dish-fade" tag="div">
            <div
              v-for="(dish, i) in store.result.recommendations"
              :key="dish.dishId"
              class="dish-card"
              :style="{ '--delay': i * 0.08 + 's' }"
            >
              <div class="dish-medal">
                <span v-if="i === 0">🥇</span>
                <span v-else-if="i === 1">🥈</span>
                <span v-else-if="i === 2">🥉</span>
                <span v-else class="rank-num">#{{ i + 1 }}</span>
              </div>

              <div class="dish-main">
                <div class="dish-header">
                  <h3 class="dish-name">{{ dish.name }}</h3>
                  <div class="score-badge">
                    <span class="score-num">{{ dish.score }}</span>
                    <span class="score-unit">分</span>
                  </div>
                </div>

                <div class="dish-meta">
                  <span class="meta-item price">¥{{ dish.price }}</span>
                  <span class="meta-item cal">{{ dish.calories }} kcal</span>
                  <span class="meta-item pro">蛋白质 {{ dish.protein }}g</span>
                </div>

                <div class="dish-reasons">
                  <div v-if="dish.reason" class="reason-row">
                    <span class="reason-icon">✨</span>
                    <span>{{ dish.reason }}</span>
                  </div>
                  <div v-if="dish.nutritionComment" class="reason-row">
                    <span class="reason-icon">🥗</span>
                    <span>{{ dish.nutritionComment }}</span>
                  </div>
                  <div v-if="dish.costPerformanceComment" class="reason-row">
                    <span class="reason-icon">💰</span>
                    <span>{{ dish.costPerformanceComment }}</span>
                  </div>
                </div>
              </div>
            </div>
          </transition-group>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Camera, MagicStick, UploadFilled, Edit, Delete, Star, StarFilled,
  User, Food, CircleCheck, CirclePlus, Loading, ChatLineRound, CloseBold
} from '@element-plus/icons-vue'
import api from '../api'
import { useRecommendStore } from '../stores/recommend'

const store = useRecommendStore()

const fileInput = ref(null)
const isDragging = ref(false)

let abortController = null

// 所有响应式状态全部来自 store，离开页面不丢失
const { file, previewUrl, result, loading, remark, finalRemark, selectedTags, currentStep } = store

let stepTimer = null

const preferTags = [
  { icon: '🌶️', label: '要辣的' },
  { icon: '🥗', label: '清淡健康' },
  { icon: '💪', label: '高蛋白减脂' },
  { icon: '💰', label: '价格实惠' },
  { icon: '🎉', label: '适合聚餐' },
  { icon: '🍜', label: '口味新颖' },
  { icon: '🧸', label: '儿童友好' },
  { icon: '🌿', label: '素食优先' },
]

const loadingSteps = [
  '正在上传图片至云端...',
  'AI 多模态模型分析图片...',
  '生成用户画像...',
  '向量数据库语义检索...',
  'AI 综合评分排序...',
]

const profileItems = computed(() => {
  const p = store.result?.userProfile
  if (!p) return []
  return [
    { icon: '👥', label: '用餐人数', value: p.peopleCount ? p.peopleCount + ' 人' : null },
    { icon: '🎂', label: '年龄段', value: p.ageRange },
    { icon: '🍽️', label: '用餐场景', value: p.diningScene },
    { icon: '💳', label: '消费能力', value: p.estimatedConsumptionLevel },
    { icon: '🎯', label: '健康目标', value: p.healthGoal },
  ]
})

function toggleTag(label) {
  const idx = store.selectedTags.indexOf(label)
  if (idx >= 0) {
    store.selectedTags.splice(idx, 1)
  } else {
    store.selectedTags.push(label)
  }
}

function onFilePicked(e) {
  const f = e.target.files[0]
  if (!f) return
  setFile(f)
}

function onDrop(e) {
  isDragging.value = false
  const f = e.dataTransfer.files[0]
  if (!f || !f.type.startsWith('image/')) {
    ElMessage.warning('请上传图片文件')
    return
  }
  setFile(f)
}

function setFile(f) {
  store.file = f
  store.previewUrl = URL.createObjectURL(f)
  store.result = null
}

function resetAll() {
  store.reset()
  if (fileInput.value) fileInput.value.value = ''
}

function startStepAnimation() {
  store.currentStep = 0
  stepTimer = setInterval(() => {
    if (store.currentStep < loadingSteps.length - 1) {
      store.currentStep++
    }
  }, 2500)
}

function stopStepAnimation() {
  if (stepTimer) {
    clearInterval(stepTimer)
    stepTimer = null
  }
}

async function startRecommend() {
  if (!store.file) {
    ElMessage.warning('请先选择图片')
    return
  }

  abortController = new AbortController()
  store.loading = true
  store.result = null
  startStepAnimation()

  const combinedRemark = [
    ...store.selectedTags,
    store.remark.trim()
  ].filter(Boolean).join('，')

  store.finalRemark = combinedRemark

  try {
    const fd = new FormData()
    fd.append('file', store.file)
    if (combinedRemark) fd.append('remark', combinedRemark)

    const res = await api.post('/recommend/image', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      signal: abortController.signal,
      timeout: 120000
    })
    store.result = res.data
    store.currentStep = loadingSteps.length
    ElMessage.success('推荐完成！')
  } catch (e) {
    if (api.isCancel && api.isCancel(e)) return
    if (e.code === 'ERR_CANCELED' || e.name === 'CanceledError') return
    ElMessage.error(e.message || '推荐失败，请重试')
  } finally {
    store.loading = false
    stopStepAnimation()
    abortController = null
  }
}

function stopRecommend() {
  if (abortController) {
    abortController.abort()
    stopStepAnimation()
    store.loading = false
    ElMessage.info('已停止推荐')
  }
}

onBeforeUnmount(stopStepAnimation)
</script>

<style scoped>
.recommend-page {
  max-width: 1280px;
  margin: 0 auto;
}

/* Banner */
.page-banner {
  background: linear-gradient(135deg, #1d1e2c 0%, #2d3460 50%, #1d3461 100%);
  border-radius: 16px;
  padding: 32px 40px;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
}
.page-banner::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(64,158,255,0.15) 0%, transparent 70%);
  border-radius: 50%;
}
.banner-content h1 {
  font-size: 26px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 8px;
}
.banner-content p {
  color: rgba(255,255,255,0.65);
  font-size: 14px;
  margin: 0;
}

/* Grid */
.main-grid {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 20px;
  align-items: start;
}

/* Cards */
.upload-card, .pref-card, .remark-card {
  border-radius: 12px;
  margin-bottom: 16px;
}
.card-hd {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
}

/* Drop zone */
.drop-zone {
  border: 2px dashed #dcdfe6;
  border-radius: 10px;
  min-height: 200px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
}
.drop-zone:hover:not(.has-image) {
  border-color: #409eff;
  background: #f0f7ff;
}
.drop-zone.dragging {
  border-color: #409eff;
  background: #ecf5ff;
}
.drop-placeholder {
  text-align: center;
  padding: 24px;
}
.drop-icon { margin-bottom: 12px; }
.drop-tip { font-size: 14px; color: #606266; margin: 0 0 4px; }
.drop-sub { font-size: 12px; color: #c0c4cc; margin: 0; }
.preview-img {
  width: 100%;
  max-height: 220px;
  object-fit: cover;
  display: block;
}
.preview-actions {
  position: absolute;
  bottom: 10px;
  display: flex;
  gap: 8px;
  background: rgba(0,0,0,0.5);
  padding: 6px 10px;
  border-radius: 20px;
}

/* 标签快选 */
.tag-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.prefer-tag {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 4px;
  border: 1.5px solid #ebeef5;
  border-radius: 10px;
  cursor: pointer;
  font-size: 12px;
  color: #606266;
  transition: all 0.2s;
}
.prefer-tag:hover { border-color: #409eff; color: #409eff; background: #f0f7ff; }
.prefer-tag.active { border-color: #409eff; background: #409eff; color: #fff; }
.tag-icon { font-size: 20px; }

/* 推荐按钮 */
.btn-start {
  width: 100%;
  height: 48px;
  font-size: 16px;
  border-radius: 12px;
  letter-spacing: 1px;
  background: linear-gradient(135deg, #409eff, #337ecc);
  border: none;
}
.btn-start:hover { opacity: 0.9; }

/* 加载步骤 */
.loading-steps {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 10px;
}
.loading-step {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #c0c4cc;
  padding: 5px 0;
  transition: all 0.3s;
}
.loading-step.active { color: #409eff; font-weight: 500; }
.loading-step.done { color: #67c23a; }
.spin { animation: spin 1s linear infinite; }
.stop-btn { width: 100%; margin-top: 8px; }
@keyframes spin { to { transform: rotate(360deg); } }

/* 右侧空状态 */
.empty-state { text-align: center; padding: 60px 20px; }
.empty-icon { font-size: 72px; }
.empty-title { font-size: 18px; color: #303133; margin: 12px 0 4px; font-weight: 600; }
.empty-sub { color: #909399; font-size: 13px; }

/* 画像卡片 */
.section-card { border-radius: 12px; margin-bottom: 16px; }
.profile-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}
.p-item { display: flex; align-items: flex-start; gap: 10px; }
.p-icon { font-size: 20px; line-height: 1.4; }
.p-label { font-size: 11px; color: #909399; }
.p-val { font-size: 14px; font-weight: 500; color: #303133; margin-top: 1px; }
.pref-tags-row { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.remark-badge {
  margin-top: 12px;
  background: linear-gradient(135deg, #fdf6ec, #fef0e6);
  border: 1px solid #f5dab1;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #e6a23c;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 推荐总结 */
.summary-block {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: linear-gradient(135deg, #f0f9f4, #e8f8f0);
  border: 1px solid #b2e4c8;
  border-radius: 10px;
  padding: 12px 16px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #2d7a4f;
  line-height: 1.7;
}

/* 菜品标题 */
.dishes-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

/* 菜品卡片 */
.dish-card {
  display: flex;
  gap: 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  transition: box-shadow 0.25s, transform 0.25s;
  animation: slideUp 0.4s ease both;
  animation-delay: var(--delay);
}
@keyframes slideUp {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}
.dish-card:hover { box-shadow: 0 4px 20px rgba(0,0,0,0.08); transform: translateY(-2px); }

.dish-medal { font-size: 28px; min-width: 40px; text-align: center; line-height: 1.3; }
.rank-num { font-size: 16px; font-weight: 700; color: #909399; }

.dish-main { flex: 1; }
.dish-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.dish-name { font-size: 16px; font-weight: 600; color: #1d1e2c; margin: 0; }
.score-badge { display: flex; align-items: baseline; gap: 2px; }
.score-num { font-size: 24px; font-weight: 700; color: #e6a23c; line-height: 1; }
.score-unit { font-size: 12px; color: #909399; }

.dish-meta { display: flex; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
.meta-item {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 20px;
  font-weight: 500;
}
.price { background: #fff7e6; color: #d48806; }
.cal { background: #f0f7ff; color: #3b82f6; }
.pro { background: #f0fdf4; color: #16a34a; }

.dish-reasons { display: flex; flex-direction: column; gap: 5px; }
.reason-row { display: flex; gap: 8px; font-size: 13px; color: #606266; line-height: 1.6; }
.reason-icon { flex-shrink: 0; }

/* 动画 */
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 900px) {
  .main-grid { grid-template-columns: 1fr; }
  .profile-grid { grid-template-columns: repeat(2, 1fr); }
  .tag-grid { grid-template-columns: repeat(4, 1fr); }
}
</style>
