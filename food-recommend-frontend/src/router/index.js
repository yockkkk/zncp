import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  // 公开路由
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  // 根路径重定向（由守卫根据角色跳转）
  {
    path: '/',
    redirect: '/login'
  },
  // 服务员路由
  {
    path: '/waiter',
    component: () => import('../layouts/MainLayout.vue'),
    meta: { role: 'WAITER' },
    children: [
      {
        path: 'recommend',
        name: 'waiter-recommend',
        component: () => import('../views/WaiterRecommendView.vue'),
        meta: { title: '智能推荐', role: 'WAITER' }
      },
      {
        path: 'history',
        name: 'waiter-history',
        component: () => import('../views/HistoryView.vue'),
        meta: { title: '我的推荐记录', role: 'WAITER' }
      }
    ]
  },
  // 老板路由
  {
    path: '/owner',
    component: () => import('../layouts/MainLayout.vue'),
    meta: { role: 'OWNER' },
    children: [
      {
        path: 'dashboard',
        name: 'owner-dashboard',
        component: () => import('../views/OwnerDashboardView.vue'),
        meta: { title: '数据看板', role: 'OWNER' }
      },
      {
        path: 'dishes',
        name: 'owner-dishes',
        component: () => import('../views/DishManageView.vue'),
        meta: { title: '菜品管理', role: 'OWNER' }
      },
      {
        path: 'records',
        name: 'owner-records',
        component: () => import('../views/OwnerRecordsView.vue'),
        meta: { title: '推荐记录', role: 'OWNER' }
      },
      {
        path: 'staff',
        name: 'owner-staff',
        component: () => import('../views/StaffManageView.vue'),
        meta: { title: '员工管理', role: 'OWNER' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
