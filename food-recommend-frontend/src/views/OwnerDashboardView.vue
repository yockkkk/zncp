<template>
  <div class="dashboard-page">
    <h2 class="page-title">数据看板</h2>

    <!-- 概览卡片 -->
    <div class="stat-cards">
      <el-card shadow="never" class="stat-card revenue-card">
        <div class="stat-num orange">¥{{ formatPrice(data.totalRevenue) }}</div>
        <div class="stat-label">总销售额 (流水)</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-num">{{ data.totalRecommendations || 0 }}</div>
        <div class="stat-label">总推荐次数</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-num green">{{ data.adoptionRate || 0 }}%</div>
        <div class="stat-label">采纳率</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-num">{{ data.totalDishes || 0 }}</div>
        <div class="stat-label">菜品总数</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-num blue">{{ data.activeWaiters || 0 }}</div>
        <div class="stat-label">在岗服务员</div>
      </el-card>
    </div>

    <!-- 热门菜品 -->
    <el-card shadow="never" class="section-card">
      <template #header>🔥 热门菜品 Top 10</template>
      <el-table v-loading="loading" :data="data.topDishes || []" stripe>
        <el-table-column label="排名" width="60">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column prop="dishName" label="菜品" min-width="140" />
        <el-table-column prop="recommendCount" label="推荐次数" width="100" />
        <el-table-column prop="adoptedCount" label="被采纳" width="100" />
      </el-table>
    </el-card>

    <!-- 服务员表现 -->
    <el-card shadow="never" class="section-card" style="margin-top: 16px">
      <template #header>👥 服务员表现</template>
      <el-table v-loading="loading" :data="data.waiterStats || []" stripe>
        <el-table-column prop="waiterName" label="姓名" min-width="120" />
        <el-table-column prop="totalRecs" label="总推荐" width="100" />
        <el-table-column prop="adoptedCount" label="被采纳" width="100" />
        <el-table-column label="采纳率" width="100">
          <template #default="{ row }">{{ row.adoptionRate || 0 }}%</template>
        </el-table-column>
        <el-table-column label="累计营业额" width="150" align="right">
          <template #default="{ row }">
            <span class="revenue-cell">¥{{ formatPrice(row.revenue) }}</span>
          </template>
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

function formatPrice(val) {
  if (val === undefined || val === null) return '0.00'
  return parseFloat(val).toFixed(2)
}

onMounted(fetchData)
</script>

<style scoped>
.dashboard-page {
  width: 100%;
}
.page-title {
  font-size: 20px;
  margin-bottom: 20px;
  font-weight: 600;
}
.stat-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
@media (max-width: 1024px) {
  .stat-cards {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 768px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }
}
.stat-card {
  text-align: center;
  border-radius: 10px;
  border: 1px solid #ebeef5;
}
.revenue-card {
  background: linear-gradient(135deg, #fffaf3 0%, #fff6e5 100%);
  border: 1px solid #ffe8cc !important;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 48px;
}
.stat-num.green {
  color: #67c23a;
}
.stat-num.blue {
  color: #409eff;
}
.stat-num.orange {
  color: #e6a23c;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.section-card {
  margin-bottom: 0;
  border-radius: 10px;
}
.revenue-cell {
  font-weight: 600;
  color: #e6a23c;
}
</style>
