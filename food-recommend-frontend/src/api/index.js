import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 180000,
  headers: { 'Content-Type': 'application/json' }
})

api.interceptors.response.use(
  res => {
    if (res.data.code !== 200) {
      return Promise.reject(new Error(res.data.message || '请求失败'))
    }
    return res.data
  },
  err => {
    const msg = err.response?.data?.message || err.message || '网络错误'
    return Promise.reject(new Error(msg))
  }
)

export default api
