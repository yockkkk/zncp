<template>
  <div class="waiter-page">
    <div class="content-grid">
      <!-- 左侧：标签面板 + 场景拍照 -->
      <div class="left-panel">
        <TagPanel ref="tagPanelRef" />
        <el-card class="scene-card" shadow="never">
          <template #header><span class="panel-title">📷 场景拍照（可选）</span></template>
          <div class="scene-upload">
            <input ref="fileInput" type="file" accept="image/*" hidden @change="onFilePicked" />
            <div v-if="!previewUrl" class="upload-btn" @click="$refs.fileInput.click()">
              <el-icon :size="32"><Camera /></el-icon>
              <span>拍一张餐桌照片</span>
              <span class="hint">帮助 AI 理解用餐环境</span>
            </div>
            <div v-else class="preview-box">
              <img :src="previewUrl" alt="场景照片" />
              <el-button size="small" type="danger" plain @click="clearPhoto">删除照片</el-button>
            </div>
          </div>
        </el-card>
        <div class="btn-group">
          <el-button type="primary" size="large" :loading="loading" class="recommend-btn" @click="startRecommend">
            <el-icon><MagicStick /></el-icon> 生成推荐
          </el-button>
          <el-button v-if="loading" type="danger" size="large" class="stop-btn" @click="stopRecommend">
            <el-icon><Close /></el-icon> 停止
          </el-button>
        </div>
      </div>

      <!-- 右侧：结果展示 -->
      <div class="right-panel">
        <!-- 空状态 -->
        <el-empty v-if="!result && !loading" description="选择标签并点击「生成推荐」查看结果" />

        <!-- 加载中 -->
        <div v-if="loading" class="loading-box">
          <div class="loading-spin">
            <el-icon :size="40" class="spin-icon"><Loading /></el-icon>
            <p class="loading-text">AI 正在分析推荐中...</p>
            <p class="loading-sub">多模型协同工作，预计需要10-30秒</p>
          </div>
        </div>

        <!-- 结果 -->
        <template v-if="result">
          <!-- 步骤条 -->
          <el-steps :active="5" align-center class="steps">
            <el-step title="场景感知" description="Agent 1" />
            <el-step title="用户画像" description="Agent 2" />
            <el-step title="菜品匹配" description="Agent 3" />
            <el-step title="智能排序" description="Agent 4" />
            <el-step title="话术生成" description="Agent 5" />
          </el-steps>

          <!-- 场景分析 -->
          <el-card v-if="result.sceneContext" class="section-card" shadow="never">
            <template #header>🏠 场景分析</template>
            <div class="scene-grid">
              <div class="scene-item"><span class="s-label">桌型</span><span>{{ result.sceneContext.tableType || '-' }}</span></div>
              <div class="scene-item"><span class="s-label">时段</span><span>{{ result.sceneContext.mealTime || '-' }}</span></div>
              <div class="scene-item"><span class="s-label">氛围</span><span>{{ result.sceneContext.atmosphere || '-' }}</span></div>
              <div class="scene-item"><span class="s-label">人数</span><span>{{ result.sceneContext.estimatedPeopleCount || '-' }}</span></div>
            </div>
          </el-card>

          <!-- 用户画像 -->
          <el-card class="section-card" shadow="never">
            <template #header>👤 顾客画像</template>
            <div class="profile-grid">
              <div class="profile-item"><span class="p-label">人数</span><span>{{ result.userProfile?.peopleCount || '-' }}人</span></div>
              <div class="profile-item"><span class="p-label">场景</span><span>{{ result.userProfile?.diningScene || '-' }}</span></div>
              <div class="profile-item"><span class="p-label">消费力</span><span>{{ result.userProfile?.estimatedConsumptionLevel || '-' }}</span></div>
              <div class="profile-item"><span class="p-label">健康目标</span><span>{{ result.userProfile?.healthGoal || '-' }}</span></div>
            </div>
            <div v-if="result.userProfile?.phone" class="history-profile-info">
              <div class="h-info-item"><span class="h-label">📱 手机号</span><span>{{ result.userProfile.phone }}</span></div>
              <div class="h-info-item"><span class="h-label">🧠 长期记忆</span><span class="h-desc-text">{{ result.userProfile.historyDescription }}</span></div>
            </div>
            <div class="pref-tags">
              <el-tag v-for="pref in result.userProfile?.possiblePreferences" :key="pref" size="small">{{ pref }}</el-tag>
            </div>
          </el-card>

          <!-- 话术 -->
          <el-alert v-if="result.openingScript" :title="result.openingScript" type="success" :closable="false" show-icon class="script-alert" />

          <!-- 推荐菜品 -->
          <el-card class="section-card" shadow="never">
            <template #header>⭐ 推荐菜品</template>
            <div v-for="dish in result.recommendations" :key="dish.dishId" class="dish-row">
              <div class="dish-rank">
                <span v-if="dish.rank === 1">🥇</span>
                <span v-else-if="dish.rank === 2">🥈</span>
                <span v-else-if="dish.rank === 3">🥉</span>
                <span v-else class="rank-num">#{{ dish.rank }}</span>
              </div>
              <div class="dish-body">
                <div class="dish-header">
                  <h3>{{ dish.name }}</h3>
                  <span class="dish-score">{{ dish.score }}分</span>
                </div>
                <div class="dish-tags">
                  <el-tag type="warning" size="small">¥{{ dish.price }}</el-tag>
                  <el-tag size="small">{{ dish.calories }}kcal</el-tag>
                  <el-tag type="success" size="small">蛋白{{ dish.protein }}g</el-tag>
                  <el-tag v-if="dish.suitableFor && dish.suitableFor.length" type="danger" size="small" effect="dark">
                    适合: {{ dish.suitableFor.join('、') }}
                  </el-tag>
                </div>
                <div class="dish-reasons">
                  <p><b>推荐理由：</b>{{ dish.reason }}</p>
                  <p><b>营养评价：</b>{{ dish.nutritionComment }}</p>
                  <p><b>性价比：</b>{{ dish.costPerformanceComment }}</p>
                </div>
                <!-- 话术 -->
                <div v-if="getDishScript(dish.dishId)" class="dish-script">
                  <span class="script-label">💬 推荐话术：</span>
                  <span class="script-text">{{ getDishScript(dish.dishId) }}</span>
                </div>
                <!-- 采纳按钮 + 份数 -->
                <div class="dish-action">
                  <template v-if="adoptedDishes.has(dish.dishId)">
                    <el-tag type="success" size="small">已采纳 {{ adoptedDishes.get(dish.dishId) }} 份</el-tag>
                  </template>
                  <template v-else>
                    <span class="qty-label">份数</span>
                    <el-input-number v-model="adoptQtys[dish.dishId]" :min="1" :max="99" size="small" style="width:90px" />
                    <el-button type="success" size="small" @click="adoptDish(dish.dishId)">
                      顾客选了这道 ✓
                    </el-button>
                  </template>
                </div>
              </div>
            </div>
          </el-card>

          <!-- 操作区 -->
          <div class="result-actions">
            <el-button type="primary" size="large" @click="resetAll">
              <el-icon><MagicStick /></el-icon> 开始下一次推荐
            </el-button>
            <span class="action-hint">本次推荐已写入记录，可在「我的记录」中查看</span>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, MagicStick, Loading, Close } from '@element-plus/icons-vue'
import TagPanel from '../components/TagPanel.vue'
import api from '../api'

const tagPanelRef = ref(null)
const fileInput = ref(null)
const file = ref(null)
const previewUrl = ref('')
const loading = ref(false)
const result = ref(null)
const recordId = ref(null)
const adoptedDishes = ref(new Map())   // dishId → quantity
const adoptQtys = reactive({})          // dishId → 当前选择的份数(默认1)
const abortController = ref(null)

function onFilePicked(e) {
  const f = e.target.files?.[0]
  if (f) {
    file.value = f
    previewUrl.value = URL.createObjectURL(f)
  }
}

function clearPhoto() {
  file.value = null
  previewUrl.value = ''
  if (fileInput.value) fileInput.value.value = ''
}

function getDishScript(dishId) {
  if (!result.value?.dishScripts) return null
  const s = result.value.dishScripts.find(d => d.dishId === dishId)
  return s?.script || null
}

async function startRecommend() {
  const tags = tagPanelRef.value?.selected
  if (!tags) return
  if (!tags.peopleCount) { ElMessage.warning('请选择用餐人数'); return }
  if (!tags.diningScene) { ElMessage.warning('请选择用餐场景'); return }
  if (!tags.mealTime) { ElMessage.warning('请选择用餐时段'); return }

  loading.value = true
  result.value = null
  abortController.value = new AbortController()

  try {
    const fd = new FormData()
    fd.append('tagInputJson', JSON.stringify(tags))
    if (file.value) {
      fd.append('sceneImage', file.value)
    }

    const res = await api.post('/waiter/recommend', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      signal: abortController.value.signal
    })

    result.value = res.data
    recordId.value = res.data.recordId
    adoptedDishes.value = new Map()
    Object.keys(adoptQtys).forEach(k => delete adoptQtys[k])
    ElMessage.success('推荐生成成功！')
  } catch (e) {
    if (e.name === 'AbortError' || e.code === 'ERR_CANCELED') {
      ElMessage.info('已停止推荐')
    }
    // other errors handled by interceptor
  } finally {
    loading.value = false
    abortController.value = null
  }
}

function stopRecommend() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
    loading.value = false
    ElMessage.info('已停止推荐')
  }
}

function resetAll() {
  result.value = null
  recordId.value = null
  adoptedDishes.value = new Map()
  Object.keys(adoptQtys).forEach(k => delete adoptQtys[k])
  tagPanelRef.value?.reset()
  clearPhoto()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function adoptDish(dishId) {
  if (!recordId.value) return
  const qty = adoptQtys[dishId] || 1
  try {
    await api.post(`/waiter/feedback/${recordId.value}`, {
      adopted: true,
      adoptedDishId: dishId,
      quantity: qty
    })
    adoptedDishes.value.set(dishId, qty)
    ElMessage.success(`已采纳 × ${qty} 份`)
  } catch (e) {
    // error handled by interceptor
  }
}
</script>

<style scoped>
.waiter-page { width: 100%; }
.content-grid {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 20px;
  align-items: start;
}
@media (max-width: 860px) {
  .content-grid { grid-template-columns: 1fr; }
}
.left-panel { position: sticky; top: 24px; }
.panel-title { font-weight: 600; font-size: 15px; }

.scene-card { margin-bottom: 16px; }
.scene-upload { text-align: center; }
.upload-btn {
  border: 2px dashed #dcdfe6; border-radius: 8px; padding: 24px; cursor: pointer;
  display: flex; flex-direction: column; align-items: center; gap: 8px; color: #909399;
}
.upload-btn:hover { border-color: #409eff; color: #409eff; }
.upload-btn .hint { font-size: 12px; color: #c0c4cc; }
.preview-box { text-align: center; }
.preview-box img { max-width: 100%; max-height: 200px; border-radius: 8px; margin-bottom: 8px; }

.btn-group { display: flex; gap: 8px; margin-top: 8px; }
.recommend-btn { flex: 1; height: 48px; font-size: 16px; }
.stop-btn { width: 80px; height: 48px; font-size: 14px; }

.right-panel { min-height: 400px; }
.loading-box { padding: 48px 24px; background: #fff; border-radius: 8px; text-align: center; }
.loading-spin { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.loading-text { font-size: 15px; color: #303133; margin: 0; font-weight: 500; }
.loading-sub { font-size: 12px; color: #909399; margin: 0; }
.spin-icon { animation: spin 1.2s linear infinite; color: #409eff; }
@keyframes spin { to { transform: rotate(360deg); } }
.steps { margin-bottom: 24px; background: #fff; padding: 20px; border-radius: 8px; }
.section-card { margin-bottom: 16px; }

.scene-grid, .profile-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.scene-item, .profile-item { display: flex; gap: 8px; font-size: 14px; }
.s-label, .p-label { color: #909399; min-width: 40px; }
.pref-tags { margin-top: 12px; display: flex; gap: 6px; flex-wrap: wrap; }
.history-profile-info {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #ebeef5;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.h-info-item {
  display: flex;
  gap: 12px;
  font-size: 14px;
}
.h-label {
  color: #909399;
  min-width: 70px;
}
.h-desc-text {
  color: #303133;
  line-height: 1.4;
}
.script-alert { margin-bottom: 16px; }

.dish-row { display: flex; gap: 12px; padding: 16px 0; border-bottom: 1px solid #f2f3f5; }
.dish-row:last-child { border-bottom: none; }
.dish-rank { font-size: 28px; min-width: 50px; text-align: center; }
.rank-num { font-size: 18px; color: #909399; font-weight: 700; }
.dish-body { flex: 1; }
.dish-header { display: flex; justify-content: space-between; align-items: center; }
.dish-header h3 { margin: 0; font-size: 16px; }
.dish-score { font-size: 20px; font-weight: 700; color: #e6a23c; }
.dish-tags { margin: 8px 0; display: flex; gap: 6px; }
.dish-reasons { font-size: 13px; color: #606266; }
.dish-reasons p { margin: 4px 0; }
.dish-script {
  margin-top: 10px; padding: 10px 12px; background: #f0f9eb; border-radius: 6px;
  border-left: 3px solid #67c23a;
}
.script-label { font-size: 12px; color: #67c23a; font-weight: 600; }
.script-text { font-size: 13px; color: #303133; }
.dish-action { margin-top: 10px; display: flex; align-items: center; gap: 8px; }
.qty-label { font-size: 12px; color: #909399; }
.result-actions {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  padding: 24px 0; text-align: center;
}
.result-actions .action-hint {
  font-size: 12px; color: #909399;
}
</style>
