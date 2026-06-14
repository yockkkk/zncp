<template>
  <el-container class="layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <el-icon :size="28"><DishDot /></el-icon>
        <span>智能餐饮推荐</span>
      </div>

      <!-- 服务员菜单 -->
      <el-menu v-if="auth.isWaiter"
        :default-active="route.path"
        router
        background-color="#1d1e2c"
        text-color="#a0a4b8"
        active-text-color="#fff"
      >
        <el-menu-item index="/waiter/recommend">
          <el-icon><MagicStick /></el-icon>
          <span>智能推荐</span>
        </el-menu-item>
        <el-menu-item index="/waiter/history">
          <el-icon><Clock /></el-icon>
          <span>我的记录</span>
        </el-menu-item>
      </el-menu>

      <!-- 老板菜单 -->
      <el-menu v-if="auth.isOwner"
        :default-active="route.path"
        router
        background-color="#1d1e2c"
        text-color="#a0a4b8"
        active-text-color="#fff"
      >
        <el-menu-item index="/owner/dashboard">
          <el-icon><TrendCharts /></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/owner/dishes">
          <el-icon><Food /></el-icon>
          <span>菜品管理</span>
        </el-menu-item>
        <el-menu-item index="/owner/records">
          <el-icon><Document /></el-icon>
          <span>推荐记录</span>
        </el-menu-item>
        <el-menu-item index="/owner/staff">
          <el-icon><User /></el-icon>
          <span>员工管理</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <span>{{ auth.realName }} · {{ roleLabel }}</span>
      </div>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <h2>{{ route.meta.title }}</h2>
        <div class="topbar-right">
          <el-tag :type="auth.isOwner ? 'warning' : 'success'" effect="dark" round size="small">
            {{ roleLabel }}
          </el-tag>
          <el-button text size="small" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { DishDot, MagicStick, Food, Clock, TrendCharts, Document, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { ROLE_LABELS } from '../utils/roles'
import { computed } from 'vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const roleLabel = computed(() => ROLE_LABELS[auth.role] || '')

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100vh;
}
.sidebar {
  background: #1d1e2c;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 24px 20px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid #2a2b3d;
}
.sidebar .el-menu {
  border-right: none;
  flex: 1;
  padding-top: 8px;
}
.sidebar .el-menu-item {
  margin: 2px 8px;
  border-radius: 8px;
  font-size: 14px;
}
.sidebar .el-menu-item.is-active {
  background: linear-gradient(135deg, #409eff, #337ecc);
}
.sidebar-footer {
  padding: 16px 20px;
  color: #5a5c6e;
  font-size: 12px;
  border-top: 1px solid #2a2b3d;
  text-align: center;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  padding: 0 24px;
  height: 60px;
}
.topbar h2 {
  font-size: 18px;
  color: #303133;
  margin: 0;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.main-content {
  background: #f5f7fa;
  padding: 24px;
  overflow-y: auto;
}
</style>
