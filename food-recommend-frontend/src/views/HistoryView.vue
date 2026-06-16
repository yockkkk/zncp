<template>
  <div class="history-page">
    <div class="toolbar">
      <span class="hint">最近 50 条推荐记录 · 点击行查看溯源详情</span>
      <el-button :icon="RefreshRight" @click="fetchHistory" :loading="loading">刷新</el-button>
    </div>

    <el-table :data="records" stripe v-loading="loading" style="width:100%" row-key="id"
      @row-click="openDetail" highlight-current-row>
      <el-table-column prop="id" label="记录ID" width="80" />
      <el-table-column label="图片" width="100">
        <template #default="{ row }">
          <el-image v-if="row.imageUrl" :src="row.imageUrl" :preview-src-list="[row.imageUrl]"
            fit="cover" style="width:60px;height:60px;border-radius:6px" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="用户画像" min-width="180">
        <template #default="{ row }">
          <div class="profile-preview" v-if="row.userProfileJson">
            {{ formatProfile(row.userProfileJson) }}
          </div>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="推荐菜品" min-width="160">
        <template #default="{ row }">
          <div class="dish-preview" v-if="row.recommendedDishIds">
            <template v-if="parseDishes(row.resultJson).length">
              <el-tag v-for="d in parseDishes(row.resultJson).slice(0, 5)" :key="d.name"
                size="small" :type="isAdoptedDish(row, d.dishId) ? 'success' : 'warning'"
                effect="plain" round>
                {{ d.name }}{{ isAdoptedDish(row, d.dishId) ? ' ✓' : '' }}
              </el-tag>
            </template>
            <span v-else>{{ row.recommendedDishIds }}</span>
          </div>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="采纳" width="110">
        <template #default="{ row }">
          <template v-if="getAdoptedCount(row) > 0">
            <el-tag type="success" size="small">{{ getAdoptedCount(row) }}道已采纳</el-tag>
          </template>
          <el-tag v-else type="info" size="small">未采纳</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="推荐时间" width="180">
        <template #default="{ row }">{{ row.createTime || '-' }}</template>
      </el-table-column>
    </el-table>

    <!-- ====== 详情弹窗 ====== -->
    <el-dialog v-model="drawerVisible" :title="'推荐详情 #' + (detail?.id || '')" width="800px" top="5vh" destroy-on-close>
      <template v-if="detail">
        <div class="detail-grid">
          <div class="detail-left">
            <div class="trace-section">
              <h4 class="sec-title">顾客画像</h4>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="用餐场景">{{ profileField('diningScene') }}</el-descriptions-item>
                <el-descriptions-item label="用餐人数">{{ profileField('peopleCount', '') || '-' }}人</el-descriptions-item>
                <el-descriptions-item label="消费水平">{{ profileField('estimatedConsumptionLevel') }}</el-descriptions-item>
                <el-descriptions-item label="用餐时段">{{ profileField('mealTime') || '-' }}</el-descriptions-item>
                <el-descriptions-item label="口味偏好" :span="2">
                  {{ profileField('tastePreferences') || profileField('possiblePreferences') || '-' }}
                </el-descriptions-item>
              </el-descriptions>
            </div>

            <div class="trace-section" v-if="detail.sceneImageUrl || detail.imageUrl">
              <h4 class="sec-title">场景照片</h4>
              <el-image :src="detail.sceneImageUrl || detail.imageUrl"
                :preview-src-list="[detail.sceneImageUrl || detail.imageUrl]"
                fit="contain" style="max-height:200px;border-radius:8px" />
            </div>

            <div class="trace-section" v-if="detail.feedbacks && detail.feedbacks.length > 0">
              <h4 class="sec-title">采纳记录</h4>
              <el-timeline>
                <el-timeline-item v-for="fb in detail.feedbacks" :key="fb.id"
                  :timestamp="fb.createTime || '-'" type="success" placement="top">
                  采纳了 <strong>{{ getDishName(detail, fb.adoptedDishId) }}</strong> × {{ fb.quantity || 1 }} 份
                </el-timeline-item>
              </el-timeline>
            </div>
          </div>

          <div class="detail-right">
            <h4 class="sec-title">
              推荐菜品
              <el-tag v-if="getAdoptedCount(detail) > 0" type="success" size="small" style="margin-left:8px">
                {{ getAdoptedCount(detail) }}道已采纳
              </el-tag>
            </h4>
            <div v-for="dish in parseDishesWithDetail(detail.resultJson)" :key="dish.dishId" class="detail-dish-card">
              <div class="dd-header">
                <span class="dd-rank">{{ findRank(dish) }}</span>
                <strong>{{ dish.name }}</strong>
                <el-tag :type="dish.score >= 85 ? 'danger' : dish.score >= 70 ? 'warning' : 'info'" size="small">
                  {{ dish.score ?? '-' }}分
                </el-tag>
                <el-tag size="small">¥{{ dish.price ?? '-' }}</el-tag>
                <el-tag v-if="isAdoptedDish(detail, dish.dishId)" type="success" size="small">
                  已采纳{{ getAdoptedQuantity(detail, dish.dishId) }}份
                </el-tag>
              </div>
              <div class="dd-reason">{{ dish.reason || '暂无推荐理由' }}</div>
              <div class="dd-extra" v-if="dish.nutritionComment || dish.costPerformanceComment">
                <span v-if="dish.nutritionComment">营养：{{ dish.nutritionComment }}</span>
                <span v-if="dish.costPerformanceComment"> · 性价比：{{ dish.costPerformanceComment }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onActivated } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'
import api from '../api'

const records = ref([])
const loading = ref(false)
const drawerVisible = ref(false)
const detail = ref(null)

async function fetchHistory() {
  loading.value = true
  try {
    const res = await api.get('/waiter/history')
    records.value = res.data || []
  } catch (e) { /* error handled by interceptor */
  } finally { loading.value = false }
}

async function openDetail(row) {
  try {
    const res = await api.get(`/waiter/history/${row.id}`)
    detail.value = res.data
    drawerVisible.value = true
  } catch (e) { /* handled */ }
}

function formatProfile(json) {
  try {
    const p = typeof json === 'string' ? JSON.parse(json) : json
    return [p.diningScene, p.estimatedConsumptionLevel, `${p.peopleCount || '?'}人`].filter(Boolean).join(' · ')
  } catch { return '' }
}

function profileField(key, suffix = '') {
  try {
    const p = typeof detail.value?.userProfileJson === 'string'
      ? JSON.parse(detail.value.userProfileJson)
      : (detail.value?.userProfileJson || {})
    const v = p[key]
    if (Array.isArray(v)) return v.join(', ') || '-'
    return v ? v + suffix : '-'
  } catch { return '-' }
}

function parseDishes(json) {
  try {
    const arr = typeof json === 'string' ? JSON.parse(json) : json
    if (Array.isArray(arr)) return arr
    if (arr && Array.isArray(arr.recommendations)) {
      return arr.recommendations.map(r => ({ dishId: r.dishId, name: r.name }))
    }
    return []
  } catch { return [] }
}

function parseDishesWithDetail(json) {
  try {
    const arr = typeof json === 'string' ? JSON.parse(json) : json
    if (Array.isArray(arr)) return arr
    if (arr && Array.isArray(arr.recommendations)) return arr.recommendations
    return []
  } catch { return [] }
}

function getFeedbacks(row) {
  return row?.feedbacks || []
}

function getAdoptedDishIds(row) {
  return new Set(getFeedbacks(row).map(f => f.adoptedDishId).filter(Boolean))
}

function isAdoptedDish(row, dishId) {
  return getAdoptedDishIds(row).has(dishId)
}

function getAdoptedCount(row) {
  return getAdoptedDishIds(row).size
}

function getAdoptedQuantity(row, dishId) {
  const fb = getFeedbacks(row).find(f => f.adoptedDishId === dishId)
  return fb?.quantity || 1
}

function getDishName(row, dishId) {
  const dishes = parseDishesWithDetail(row.resultJson)
  const d = dishes.find(x => x.dishId === dishId)
  return d?.name || `菜品#${dishId}`
}

function findRank(dish) {
  const emojis = ['🥇', '🥈', '🥉']
  return emojis[dish.rank - 1] || `#${dish.rank}`
}

function formatJson(json) {
  try {
    const obj = typeof json === 'string' ? JSON.parse(json) : json
    return JSON.stringify(obj, null, 2)
  } catch { return json || '-' }
}

onActivated(fetchHistory)
</script>

<style scoped>
.history-page { width: 100%; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.hint { color: #909399; font-size: 13px; }
.profile-preview { font-size: 13px; color: #606266; }
.dish-preview { display: flex; flex-wrap: wrap; gap: 4px; }

.trace-section { margin-bottom: 20px; }
.sec-title {
  font-size: 15px; font-weight: 600; color: #303133;
  margin: 0 0 10px 0; padding-bottom: 6px;
  border-bottom: 2px solid #409eff; display: flex; align-items: center;
}
.detail-grid {
  display: grid; grid-template-columns: 320px 1fr; gap: 24px;
}
.detail-dish-card {
  background: #fafbfc; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 14px; margin-bottom: 10px;
}
.dd-header {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px;
}
.dd-header strong { font-size: 14px; }
.dd-rank { font-size: 18px; }
.dd-reason {
  font-size: 13px; color: #303133; line-height: 1.6;
  padding: 8px 12px; background: #fff; border-radius: 6px; margin-bottom: 6px;
}
.dd-extra {
  font-size: 12px; color: #909399;
}
</style>
