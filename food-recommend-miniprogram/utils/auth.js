/**
 * 微信小程序认证工具
 */
// eslint-disable-next-line no-unused-vars
const api = require('./api')

const config = require('../config')
// 后端服务器地址（开发环境）
const BASE_URL = config.BASE_URL

function setBaseUrl(url) {
  getApp().globalData.baseUrl = url
}

function getBaseUrl() {
  return getApp().globalData.baseUrl || BASE_URL
}

function base64Decode(str) {
  str = str.replace(/=+$/, '').replace(/-/g, '+').replace(/_/g, '/');
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  let output = '';
  let buffer = 0;
  let bits = 0;
  
  for (let i = 0; i < str.length; i++) {
    const char = str.charAt(i);
    const value = chars.indexOf(char);
    if (value === -1) continue;
    
    buffer = (buffer << 6) | value;
    bits += 6;
    
    if (bits >= 8) {
      bits -= 8;
      output += String.fromCharCode((buffer >> bits) & 0xff);
    }
  }
  return output;
}

function sanitizeUser(user) {
  if (!user) return user
  if (user.phone === null || user.phone === undefined || user.phone === 'null' || user.phone === 'undefined') {
    user.phone = ''
  }
  if (user.realName === null || user.realName === undefined || user.realName === 'null' || user.realName === 'undefined') {
    user.realName = ''
  }
  return user
}

/**
 * 检查本地缓存的登录态是否有效
 */
function checkSession() {
  const token = wx.getStorageSync('token')
  if (token) {
    try {
      const payload = JSON.parse(decodeURIComponent(escape(base64Decode(token.split('.')[1]))))
      if (payload.exp * 1000 > Date.now()) {
        getApp().globalData.isLoggedIn = true
        getApp().globalData.token = token
        const cachedUser = wx.getStorageSync('user')
        if (cachedUser) {
          try {
            const parsed = sanitizeUser(JSON.parse(cachedUser))
            getApp().globalData.userInfo = parsed
            return parsed
          } catch (e) {
            console.log('解析本地缓存用户失败:', e)
          }
        }
      }
    } catch (e) {
      console.log('Token 解析或校验失败:', e)
    }
  }
  return null;
}

/**
 * 微信授权登录（主动调用，非自动）
 */
function wxLogin() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(res) {
        if (res.code) {
          const url = getBaseUrl() + '/api/auth/wx-login'
          console.log('[wxLogin] request ->', url)
          wx.request({
            url,
            method: 'POST',
            header: {
              'Bypass-Tunnel-Reminder': 'true'
            },
            data: { code: res.code },
            timeout: 15000,
            success(wxRes) {
              if (wxRes.data && wxRes.data.code === 200) {
                const data = sanitizeUser(wxRes.data.data)
                wx.setStorageSync('token', data.token)
                wx.setStorageSync('user', JSON.stringify(data))
                getApp().globalData.token = data.token
                getApp().globalData.isLoggedIn = true
                getApp().globalData.userInfo = data
                console.log('微信登录成功:', data.username)
                resolve(data)
              } else {
                reject(new Error((wxRes.data && wxRes.data.message) || '微信登录失败'))
              }
            },
            fail(err) {
              console.error('微信登录请求失败:', err, 'url=', url)
              const msg = err && err.errMsg ? err.errMsg : '网络错误'
              reject(new Error('无法连接后端 (' + url + '): ' + msg))
            }
          })
        } else {
          reject(new Error('wx.login 获取 code 失败'))
        }
      },
      fail(err) {
        reject(err)
      }
    })
  })
}

/**
 * 账号密码登录
 */
function loginWithPassword(username, password) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: getBaseUrl() + '/api/auth/login',
      method: 'POST',
      header: {
        'Bypass-Tunnel-Reminder': 'true'
      },
      data: { username, password },
      success(res) {
        if (res.data.code === 200) {
          const data = sanitizeUser(res.data.data)
          wx.setStorageSync('token', data.token)
          wx.setStorageSync('user', JSON.stringify(data))
          getApp().globalData.token = data.token
          getApp().globalData.isLoggedIn = true
          getApp().globalData.userInfo = data
          console.log('账号密码登录成功:', data.username)
          resolve(data)
        } else {
          reject(new Error(res.data.message || '登录失败'))
        }
      },
      fail(err) {
        reject(err)
      }
    })
  })
}

/**
 * 退出登录
 */
function logout() {
  wx.removeStorageSync('token')
  wx.removeStorageSync('user')
  getApp().globalData.token = null
  getApp().globalData.isLoggedIn = false
  getApp().globalData.userInfo = null
}

/**
 * 获取当前 token
 */
function getToken() {
  return wx.getStorageSync('token')
}

module.exports = { checkSession, wxLogin, loginWithPassword, logout, getToken, setBaseUrl, getBaseUrl }
