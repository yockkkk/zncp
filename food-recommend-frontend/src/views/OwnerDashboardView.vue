<template>
  <div class="dashboard-page">
    <h2 class="page-title">数据看板</h2>

    <!-- 概览卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-num">{{ data.totalRecommendations || 0 }}</div>
          <div class="stat-label">总推荐次数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-num green">{{ data.adoptionRate || 0 }}%</div>
          <div class="stat-label">采纳率</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-num">{{ data.totalDishes || 0 }}</div>
          <div class="stat-label">菜品总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-num blue">{{ data.activeWaiters || 0 }}</div>
          <div class="stat-label">在岗服务员</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 热门菜品 -->
    <el-card shadow="never" class="section-card">
      <template #header>🔥 热门菜品 Top 10</template>
      <el-table :data="data.topDishes || []" stripe v-loading="loading">
        <el-table-column label="排名" width="60">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column prop="dishName" label="菜品" min-width="140" />
        <el-table-column prop="recommendCount" label="推荐次数" width="100" />
        <el-table-column prop="adoptedCount" label="被采纳" width="100" />
      </el-table>
    </el-card>

    <!-- 服务员表现 -->
    <el-card shadow="never" class="section-card" style="margin-top: 16px;">
      <template #header>👥 服务员表现</template>
      <el-table :data="data.waiterStats || []" stripe v-loading="loading">
        <el-table-column prop="waiterName" label="姓名" min-width="120" />
        <el-table-column prop="totalRecs" label="总推荐" width="100" />
        <el-table-column prop="adoptedCount" label="被采纳" width="100" />
        <el-table-column label="采纳率" width="100">
          <template #default="{ row }">{{ row.adoptionRate || 0 }}%</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'

const loading = ref(false)
const data = ref({})

async function fetchData() {
  loading.value = true
  try {
    const res = await api.get('/owner/analytics/overview')
    data.value = res.data
  } catch (e) {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.dashboard-page { max-width: 1200px; }
.page-title { font-size: 20px; margin-bottom: 20px; }
.stat-cards { margin-bottom: 20px; }
.stat-card { text-align: center; }
.stat-num { font-size: 32px; font-weight: 700; color: #303133; }
.stat-num.green { color: #67c23a; }
.stat-num.blue { color: #409eff; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.section-card { margin-bottom: 0; }
</style>
