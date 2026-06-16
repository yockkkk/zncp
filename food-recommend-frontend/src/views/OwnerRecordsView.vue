<template>
  <div class="records-page">
    <div class="toolbar">
      <h2 class="page-title">全部推荐记录</h2>
      <el-button @click="fetchRecords" :loading="loading">刷新</el-button>
    </div>
    <el-table :data="records" stripe v-loading="loading" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="服务员" width="100">
        <template #default="{ row }">{{ waiterName(row.waiterId) }}</template>
      </el-table-column>
      <el-table-column label="标签输入" min-width="180">
        <template #default="{ row }">
          <span class="tag-json">{{ formatTags(row.tagInputJson) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="推荐菜品" min-width="180">
        <template #default="{ row }">
          <div class="dish-tags" v-if="parseDishNames(row.resultJson).length">
            <el-tag v-for="d in parseDishNames(row.resultJson).slice(0, 5)" :key="d.name"
              size="small" :type="isAdoptedDish(row, d.dishId) ? 'success' : 'warning'"
              effect="plain" round>
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
  </div>
</template>

<script setup>
import { ref, onActivated } from 'vue'
import api from '../api'

const loading = ref(false)
const records = ref([])
const waiterMap = ref({})

function formatTags(json) {
  try {
    const obj = typeof json === 'string' ? JSON.parse(json) : json
    if (!obj) return '-'
    const parts = []
    if (obj.peopleCount) parts.push(`${obj.peopleCount}人`)
    if (obj.diningScene) parts.push(obj.diningScene)
    if (obj.budgetLevel) parts.push(obj.budgetLevel)
    return parts.join(' · ') || '-'
  } catch { return '-' }
}

function parseDishNames(json) {
  try {
    const arr = typeof json === 'string' ? JSON.parse(json) : json
    if (Array.isArray(arr)) return arr
    if (arr && Array.isArray(arr.recommendations)) return arr.recommendations
    return []
  } catch { return [] }
}

function getFeedbacks(row) { return row.feedbacks || [] }
function getAdoptedDishIds(row) { return new Set(getFeedbacks(row).map(f => f.adoptedDishId).filter(Boolean)) }
function isAdoptedDish(row, dishId) { return getAdoptedDishIds(row).has(dishId) }
function getAdoptedCount(row) { return getAdoptedDishIds(row).size }
function waiterName(id) { return waiterMap.value[id] || `#${id}` }

async function fetchRecords() {
  loading.value = true
  try {
    const [recRes, staffRes] = await Promise.all([
      api.get('/owner/records'),
      api.get('/owner/staff')
    ])
    records.value = recRes.data || []
    const staff = staffRes.data || []
    staff.forEach(u => { waiterMap.value[u.id] = u.realName || u.username })
  } catch (e) { /* handled */ }
  finally { loading.value = false }
}

onActivated(fetchRecords)
</script>

<style scoped>
.records-page { width: 100%; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 600; margin: 0; }
.tag-json { font-size: 12px; color: #606266; }
.dish-tags { display: flex; flex-wrap: wrap; gap: 4px; }
</style>
