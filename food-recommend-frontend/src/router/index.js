import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'recommend',
    component: () => import('../views/RecommendView.vue'),
    meta: { title: '智能推荐' }
  },
  {
    path: '/dishes',
    name: 'dishes',
    component: () => import('../views/DishManageView.vue'),
    meta: { title: '菜品管理' }
  },
  {
    path: '/history',
    name: 'history',
    component: () => import('../views/HistoryView.vue'),
    meta: { title: '推荐历史' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
