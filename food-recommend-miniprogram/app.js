// eslint-disable-next-line no-unused-vars
const api = require('./utils/api')
const auth = require('./utils/auth')

App({
  onLaunch() {
    // 自动登录
    auth.autoLogin().catch(err => {
      console.error('登录失败:', err)
    })
  },

  globalData: {
    userInfo: null,
    isLoggedIn: false
  }
})
