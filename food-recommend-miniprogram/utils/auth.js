/**
 * 微信小程序认证工具
 */
// eslint-disable-next-line no-unused-vars
const api = require('./api')

// 后端服务器地址（开发环境）
const BASE_URL = 'http://localhost:8080'

function setBaseUrl(url) {
  getApp().globalData.baseUrl = url
}

function getBaseUrl() {
  return getApp().globalData.baseUrl || BASE_URL
}

/**
 * 自动登录：检查本地 token → 有效则跳过 → 无效则 wx.login
 */
async function autoLogin() {
  const token = wx.getStorageSync('token')
  if (token) {
    // 简单检查 token 是否过期（解析 JWT exp）
    try {
      const payload = JSON.parse(decodeURIComponent(atob(token.split('.')[1])))
      if (payload.exp * 1000 > Date.now()) {
        getApp().globalData.isLoggedIn = true
        getApp().globalData.token = token
        console.log('Token 有效，跳过登录')
        return
      }
    } catch (e) {
      console.log('Token 解析失败，重新登录')
    }
  }

  // 调用微信登录
  return new Promise((resolve, reject) => {
    wx.login({
      success(res) {
        if (res.code) {
          wx.request({
            url: getBaseUrl() + '/api/auth/wx-login',
            method: 'POST',
            data: { code: res.code },
            success(wxRes) {
              if (wxRes.data.code === 200) {
                const data = wxRes.data.data
                wx.setStorageSync('token', data.token)
                wx.setStorageSync('user', JSON.stringify(data))
                getApp().globalData.token = data.token
                getApp().globalData.isLoggedIn = true
                getApp().globalData.userInfo = data
                console.log('微信登录成功:', data.username)
                resolve(data)
              } else {
                reject(new Error(wxRes.data.message))
              }
            },
            fail(err) {
              console.error('微信登录请求失败:', err)
              reject(err)
            }
          })
        } else {
          reject(new Error('wx.login 失败'))
        }
      },
      fail(err) {
        reject(err)
      }
    })
  })
}

/**
 * 获取当前 token
 */
function getToken() {
  return wx.getStorageSync('token')
}

module.exports = { autoLogin, getToken, setBaseUrl, getBaseUrl }
