<template>
  <div class="records-page">
    <h2 class="page-title">全部推荐记录</h2>
    <el-table :data="records" stripe v-loading="loading" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="waiterId" label="服务员ID" width="90" />
      <el-table-column label="标签输入" min-width="180">
        <template #default="{ row }">
          <span class="tag-json">{{ formatTags(row.tagInputJson) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="推荐菜品" min-width="150">
        <template #default="{ row }">
          <span>{{ row.recommendedDishIds || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="采纳" width="80">
        <template #default="{ row }">
          <el-tag :type="row.adopted === 1 ? 'success' : 'info'" size="small">
            {{ row.adopted === 1 ? '已采纳' : '未采纳' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="160" />
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'

const loading = ref(false)
const records = ref([])

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

async function fetchRecords() {
  loading.value = true
  try {
    const res = await api.get('/owner/records')
    records.value = res.data
  } catch (e) { /* handled */ }
  finally { loading.value = false }
}

onMounted(fetchRecords)
</script>

<style scoped>
.records-page { width: 100%; }
.page-title { font-size: 20px; margin-bottom: 20px; font-weight: 600; }
.tag-json { font-size: 12px; color: #606266; }
</style>
