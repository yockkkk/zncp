import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 180000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器：自动附加 JWT Token
api.interceptors.request.use(config => {
  try {
    const auth = useAuthStore()
    if (auth.token) {
      config.headers.Authorization = `Bearer ${auth.token}`
    }
  } catch (e) { /* Pinia not initialized yet */ }
  return config
})

// 响应拦截器
api.interceptors.response.use(
  res => {
    if (res.data.code !== 200) {
      ElMessage.error(res.data.message || '请求失败')
      return Promise.reject(new Error(res.data.message || '请求失败'))
    }
    return res.data
  },
  err => {
    if (err.response?.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      try {
        const auth = useAuthStore()
        auth.logout()
      } catch (e) { /* ignore */ }
      setTimeout(() => { window.location.href = '/login' }, 1000)
    } else if (err.response?.status === 403) {
      ElMessage.error('权限不足，无法访问该资源')
    } else {
      const msg = err.response?.data?.message || err.message || '网络错误'
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export default api
