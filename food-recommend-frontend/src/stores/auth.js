import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ROLES, getDefaultRoute } from '../utils/roles'

/**
 * 认证状态管理
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

  const isLoggedIn = computed(() => !!token.value)
  const isWaiter = computed(() => user.value?.role === ROLES.WAITER)
  const isOwner = computed(() => user.value?.role === ROLES.OWNER)
  const userId = computed(() => user.value?.userId)
  const realName = computed(() => user.value?.realName || user.value?.username)
  const role = computed(() => user.value?.role)

  function setAuth(newToken, userInfo) {
    token.value = newToken
    user.value = userInfo
    localStorage.setItem('token', newToken)
    localStorage.setItem('user', JSON.stringify(userInfo))
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  function getDefaultPath() {
    return getDefaultRoute(user.value?.role)
  }

  return {
    token,
    user,
    isLoggedIn,
    isWaiter,
    isOwner,
    userId,
    realName,
    role,
    setAuth,
    logout,
    getDefaultPath
  }
})
