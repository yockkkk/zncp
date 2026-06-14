import { useAuthStore } from '../stores/auth'

/**
 * 路由守卫
 */
export function setupRouterGuards(router) {
  router.beforeEach((to, from, next) => {
    const auth = useAuthStore()

    // 公开页面直接放行
    if (to.meta.public) {
      next()
      return
    }

    // 未登录 → 登录页
    if (!auth.isLoggedIn) {
      next('/login')
      return
    }

    // 已登录访问登录页 → 跳转到首页
    if (to.path === '/login') {
      next(auth.getDefaultPath())
      return
    }

    // 根路径 → 根据角色跳转
    if (to.path === '/') {
      next(auth.getDefaultPath())
      return
    }

    // 角色权限检查
    const requiredRole = to.meta.role
    if (requiredRole && auth.role !== requiredRole) {
      next(auth.getDefaultPath())
      return
    }

    next()
  })
}
