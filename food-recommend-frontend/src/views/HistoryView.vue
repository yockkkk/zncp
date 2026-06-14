<template>
  <div class="history-page">
    <div class="toolbar">
      <span class="hint">最近 50 条推荐记录</span>
      <el-button :icon="RefreshRight" @click="fetchHistory" :loading="loading">刷新</el-button>
    </div>

    <el-table :data="records" stripe v-loading="loading" style="width:100%" row-key="id">
      <el-table-column prop="id" label="记录ID" width="80" />
      <el-table-column label="图片" width="100">
        <template #default="{ row }">
          <el-image
            v-if="row.imageUrl"
            :src="row.imageUrl"
            :preview-src-list="[row.imageUrl]"
            fit="cover"
            style="width:60px;height:60px;border-radius:6px"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="用户画像" min-width="200">
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
              <el-tag v-for="d in parseDishes(row.resultJson).slice(0, 5)" :key="d.name" size="small" type="warning" effect="plain" round>
                {{ d.name }}
              </el-tag>
            </template>
            <span v-else>{{ row.recommendedDishIds }}</span>
          </div>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="采纳" width="90">
        <template #default="{ row }">
          <el-tag :type="row.adopted === 1 ? 'success' : 'info'" size="small">
            {{ row.adopted === 1 ? '已采纳' : '未采纳' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="推荐时间" width="180">
        <template #default="{ row }">{{ row.createTime || '-' }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshRight } from '@element-plus/icons-vue'
import api from '../api'

const records = ref([])
const loading = ref(false)

async function fetchHistory() {
  loading.value = true
  try {
    const res = await api.get('/waiter/history')
    records.value = res.data || []
  } catch (e) { /* error handled by interceptor */
  } finally { loading.value = false }
}

function formatProfile(json) {
  try {
    const p = typeof json === 'string' ? JSON.parse(json) : json
    return [p.diningScene, p.estimatedConsumptionLevel, `${p.peopleCount || '?'}人`].filter(Boolean).join(' · ')
  } catch { return '' }
}

function parseDishes(json) {
  try {
    const arr = typeof json === 'string' ? JSON.parse(json) : json
    return Array.isArray(arr) ? arr : []
  } catch { return [] }
}

onMounted(fetchHistory)
</script>

<style scoped>
.history-page { width: 100%; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.hint { color: #909399; font-size: 13px; }
.profile-preview { font-size: 13px; color: #606266; }
.dish-preview { display: flex; flex-wrap: wrap; gap: 4px; }
</style>
