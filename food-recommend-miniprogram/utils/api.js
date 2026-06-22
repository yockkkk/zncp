/**
 * 网络请求封装
 */
const auth = require('./auth')

const BASE = 'http://localhost:8080/api'

// eslint-disable-next-line no-unused-vars
function request(method, path, data, options = {}) {
  return new Promise((resolve, reject) => {
    const token = auth.getToken()
    const header = {
      'Content-Type': 'application/json'
    }
    if (token) {
      header['Authorization'] = 'Bearer ' + token
    }

    wx.request({
      url: BASE + path,
      method,
      data,
      header,
      timeout: 180000, // 3 分钟，适配 LLM 调用
      success(res) {
        if (res.statusCode === 401) {
          wx.removeStorageSync('token')
          wx.redirectTo({ url: '/pages/index/index' })
          reject(new Error('登录已过期'))
          return
        }
        if (res.statusCode === 403) {
          reject(new Error('权限不足'))
          return
        }
        if (res.data.code === 200) {
          resolve(res.data.data)
        } else {
          reject(new Error(res.data.message || '请求失败'))
        }
      },
      fail(err) {
        reject(new Error('网络错误: ' + (err.errMsg || '未知')))
      }
    })
  })
}

module.exports = {
  get: (path) => request('GET', path),
  post: (path, data, options) => request('POST', path, data, options),
  put: (path, data) => request('PUT', path, data),
  delete: (path) => request('DELETE', path)
}
