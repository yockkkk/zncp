<template>
  <div class="records-page">
    <div class="toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">全部推荐记录</h2>
        <span class="hint">点击行查看推荐溯源详情</span>
      </div>
      <el-button :loading="loading" @click="fetchRecords">刷新</el-button>
    </div>
    <el-table
      v-loading="loading"
      :data="records"
      stripe
      row-key="id"
      highlight-current-row
      style="width: 100%; cursor: pointer"
      @row-click="openDetail"
    >
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="服务员" width="120">
        <template #default="{ row }">{{ waiterName(row.waiterId) }}</template>
      </el-table-column>
      <el-table-column label="标签输入" min-width="180">
        <template #default="{ row }">
          <span class="tag-json">{{ formatTags(row.tagInputJson) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="推荐菜品" min-width="180">
        <template #default="{ row }">
          <div v-if="parseDishNames(row.resultJson).length" class="dish-tags">
            <el-tag
              v-for="d in parseDishNames(row.resultJson).slice(0, 5)"
              :key="d.name"
              size="small"
              :type="isAdoptedDish(row, d.dishId) ? 'success' : 'warning'"
              effect="plain"
              round
            >
              {{ d.name }}{{ isAdoptedDish(row, d.dishId) ? ' ✓' : '' }}
            </el-tag>
          </div>
          <span v-else>{{ row.recommendedDishIds || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="采纳" width="100">
        <template #default="{ row }">
          <template v-if="getAdoptedCount(row) > 0">
            <el-tag type="success" size="small">{{ getAdoptedCount(row) }}道已采纳</el-tag>
          </template>
          <el-tag v-else type="info" size="small">未采纳</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="160" />
    </el-table>

    <!-- ====== 详情弹窗 ====== -->
    <el-dialog
      v-model="drawerVisible"
      :title="'推荐详情 #' + (detail?.id || '')"
      width="800px"
      top="5vh"
      destroy-on-close
    >
      <template v-if="detail">
        <div class="detail-grid">
          <div class="detail-left">
            <div class="trace-section">
              <h4 class="sec-title">顾客画像</h4>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="用餐场景">{{
                  profileField('diningScene')
                }}</el-descriptions-item>
                <el-descriptions-item label="用餐人数"
                  >{{ profileField('peopleCount', '') || '-' }}人</el-descriptions-item
                >
                <el-descriptions-item label="消费水平">{{
                  profileField('estimatedConsumptionLevel')
                }}</el-descriptions-item>
                <el-descriptions-item label="用餐时段">{{
                  profileField('mealTime') || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="口味偏好" :span="2">
                  {{
                    profileField('tastePreferences') || profileField('possiblePreferences') || '-'
                  }}
                </el-descriptions-item>
              </el-descriptions>
            </div>

            <!-- 多人模式各顾客明细 -->
            <div v-if="hasGuests(detail)" class="trace-section">
              <h4 class="sec-title">各顾客明细</h4>
              <div class="detail-guests-list" style="max-height: 250px; overflow-y: auto">
                <div
                  v-for="guest in getGuestsList(detail)"
                  :key="guest.name"
                  class="detail-guest-item"
                  style="
                    background: #fafbfc;
                    border: 1px solid #ebeef5;
                    border-radius: 6px;
                    padding: 10px;
                    margin-bottom: 8px;
                  "
                >
                  <div
                    style="font-size: 13px; font-weight: 600; color: #1d3461; margin-bottom: 6px"
                  >
                    👤 {{ guest.name }}
                  </div>
                  <div style="display: flex; flex-wrap: wrap; gap: 6px">
                    <el-tag v-if="guest.tastes && guest.tastes.length" size="small" type="warning"
                      >喜欢: {{ guest.tastes.join('、') }}</el-tag
                    >
                    <el-tag
                      v-if="guest.avoidIngredients && guest.avoidIngredients.length"
                      size="small"
                      type="danger"
                      >忌口: {{ guest.avoidIngredients.join('、') }}</el-tag
                    >
                    <el-tag
                      v-if="guest.allergens && guest.allergens.length"
                      size="small"
                      type="danger"
                      >过敏: {{ guest.allergens.join('、') }}</el-tag
                    >
                    <el-tag
                      v-if="guest.diseases && guest.diseases.length"
                      size="small"
                      type="danger"
                      >禁忌: {{ guest.diseases.join('、') }}</el-tag
                    >
                    <el-tag
                      v-if="guest.dietLifestyles && guest.dietLifestyles.length"
                      size="small"
                      type="primary"
                      >习惯: {{ guest.dietLifestyles.join('、') }}</el-tag
                    >
                    <span
                      v-if="
                        !guest.tastes?.length &&
                        !guest.avoidIngredients?.length &&
                        !guest.allergens?.length &&
                        !guest.diseases?.length &&
                        !guest.dietLifestyles?.length
                      "
                      style="font-size: 12px; color: #c0c4cc"
                      >无特殊要求</span
                    >
                  </div>
                </div>
              </div>
            </div>

            <div v-if="detail.sceneImageUrl || detail.imageUrl" class="trace-section">
              <h4 class="sec-title">场景照片</h4>
              <el-image
                :src="detail.sceneImageUrl || detail.imageUrl"
                :preview-src-list="[detail.sceneImageUrl || detail.imageUrl]"
                fit="contain"
                style="max-height: 200px; border-radius: 8px"
              />
            </div>

            <div v-if="detail.feedbacks && detail.feedbacks.length > 0" class="trace-section">
              <h4 class="sec-title">采纳记录</h4>
              <el-timeline>
                <el-timeline-item
                  v-for="fb in detail.feedbacks"
                  :key="fb.id"
                  :timestamp="fb.createTime || '-'"
                  type="success"
                  placement="top"
                >
                  采纳了 <strong>{{ getDishName(detail, fb.adoptedDishId) }}</strong> ×
                  {{ fb.quantity || 1 }} 份
                </el-timeline-item>
              </el-timeline>
            </div>
          </div>

          <div class="detail-right">
            <h4 class="sec-title">
              推荐菜品
              <el-tag
                v-if="getAdoptedCount(detail) > 0"
                type="success"
                size="small"
                style="margin-left: 8px"
              >
                {{ getAdoptedCount(detail) }}道已采纳
              </el-tag>
            </h4>
            <div
              v-for="dish in parseDishesWithDetail(detail.resultJson)"
              :key="dish.dishId"
              class="detail-dish-card"
            >
              <div class="dd-header">
                <span class="dd-rank">{{ findRank(dish) }}</span>
                <strong>{{ dish.name }}</strong>
                <el-tag
                  :type="dish.score >= 85 ? 'danger' : dish.score >= 70 ? 'warning' : 'info'"
                  size="small"
                >
                  {{ dish.score ?? '-' }}分
                </el-tag>
                <el-tag size="small">¥{{ dish.price ?? '-' }}</el-tag>
                <el-tag v-if="isAdoptedDish(detail, dish.dishId)" type="success" size="small">
                  已采纳{{ getAdoptedQuantity(detail, dish.dishId) }}份
                </el-tag>
              </div>
              <div class="dd-reason">{{ dish.reason || '暂无推荐理由' }}</div>
              <div v-if="dish.nutritionComment || dish.costPerformanceComment" class="dd-extra">
                <span v-if="dish.nutritionComment">营养：{{ dish.nutritionComment }}</span>
                <span v-if="dish.costPerformanceComment">
                  · 性价比：{{ dish.costPerformanceComment }}</span
                >
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
import api from '../api'

const loading = ref(false)
const records = ref([])
const waiterMap = ref({})
const drawerVisible = ref(false)
const detail = ref(null)

function formatTags(json) {
  try {
    const obj = typeof json === 'string' ? JSON.parse(json) : json
    if (!obj) return '-'
    const parts = []
    if (obj.peopleCount) parts.push(`${obj.peopleCount}人`)
    if (obj.diningScene) parts.push(obj.diningScene)
    if (obj.budgetLevel) parts.push(obj.budgetLevel)
    return parts.join(' · ') || '-'
  } catch {
    return '-'
  }
}

function parseDishNames(json) {
  try {
    const arr = typeof json === 'string' ? JSON.parse(json) : json
    if (Array.isArray(arr)) return arr
    if (arr && Array.isArray(arr.recommendations)) return arr.recommendations
    return []
  } catch {
    return []
  }
}

function parseDishesWithDetail(json) {
  return parseDishNames(json)
}

function getFeedbacks(row) {
  return row?.feedbacks || []
}
function getAdoptedDishIds(row) {
  return new Set(
    getFeedbacks(row)
      .map((f) => f.adoptedDishId)
      .filter(Boolean)
  )
}
function isAdoptedDish(row, dishId) {
  return getAdoptedDishIds(row).has(dishId)
}
function getAdoptedCount(row) {
  return getAdoptedDishIds(row).size
}
function waiterName(id) {
  return waiterMap.value[id] || `#${id}`
}

async function fetchRecords() {
  loading.value = true
  try {
    const [recRes, staffRes] = await Promise.all([
      api.get('/owner/records'),
      api.get('/owner/staff')
    ])
    records.value = recRes.data || []
    const staff = staffRes.data || []
    staff.forEach((u) => {
      waiterMap.value[u.id] = u.realName || u.username
    })
  } catch (e) {
    /* handled */
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  try {
    const res = await api.get(`/owner/records/${row.id}`)
    detail.value = res.data
    drawerVisible.value = true
  } catch (e) {
    /* handled */
  }
}

function profileField(key, suffix = '') {
  try {
    const p =
      typeof detail.value?.userProfileJson === 'string'
        ? JSON.parse(detail.value.userProfileJson)
        : detail.value?.userProfileJson || {}
    const v = p[key]
    if (Array.isArray(v)) return v.join(', ') || '-'
    return v ? v + suffix : '-'
  } catch {
    return '-'
  }
}

function getAdoptedQuantity(row, dishId) {
  const fb = getFeedbacks(row).find((f) => f.adoptedDishId === dishId)
  return fb?.quantity || 1
}

function getDishName(row, dishId) {
  const dishes = parseDishesWithDetail(row.resultJson)
  const d = dishes.find((x) => x.dishId === dishId)
  return d?.name || `菜品#${dishId}`
}

function findRank(dish) {
  const emojis = ['🥇', '🥈', '🥉']
  return emojis[dish.rank - 1] || `#${dish.rank}`
}

function hasGuests(row) {
  try {
    const p =
      typeof row?.userProfileJson === 'string'
        ? JSON.parse(row.userProfileJson)
        : row?.userProfileJson || {}
    return Array.isArray(p.guests) && p.guests.length > 0
  } catch {
    return false
  }
}

function getGuestsList(row) {
  try {
    const p =
      typeof row?.userProfileJson === 'string'
        ? JSON.parse(row.userProfileJson)
        : row?.userProfileJson || {}
    return p.guests || []
  } catch {
    return []
  }
}

onActivated(fetchRecords)
</script>

<style scoped>
.records-page {
  width: 100%;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.toolbar-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}
.hint {
  color: #909399;
  font-size: 13px;
}
.tag-json {
  font-size: 12px;
  color: #606266;
}
.dish-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.trace-section {
  margin-bottom: 20px;
}
.sec-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 10px 0;
  padding-bottom: 6px;
  border-bottom: 2px solid #409eff;
  display: flex;
  align-items: center;
}
.detail-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
}
.detail-dish-card {
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 10px;
}
.dd-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.dd-header strong {
  font-size: 14px;
}
.dd-rank {
  font-size: 18px;
}
.dd-reason {
  font-size: 13px;
  color: #303133;
  line-height: 1.6;
  padding: 8px 12px;
  background: #fff;
  border-radius: 6px;
  margin-bottom: 6px;
}
.dd-extra {
  font-size: 12px;
  color: #909399;
}
</style>
