// eslint-disable-next-line no-unused-vars
const api = require('./utils/api')
const auth = require('./utils/auth')
const config = require('./config')

App({
  onLaunch() {
    // 启动时网络自检：直接 ping 后端，不依赖 wx.login
    const probeUrl = config.BASE_URL + '/api/auth/login'
    console.log('[probe] -> POST', probeUrl)
    const t0 = Date.now()
    wx.request({
      url: probeUrl,
      method: 'POST',
      header: { 'Content-Type': 'application/json' },
      data: { username: '__probe__', password: '__probe__' },
      timeout: 8000,
      success(res) {
        console.log('[probe] OK statusCode=' + res.statusCode + ' in ' + (Date.now() - t0) + 'ms, body=', res.data)
      },
      fail(err) {
        console.error('[probe] FAIL in ' + (Date.now() - t0) + 'ms err=', err)
        wx.showModal({
          title: '后端连接失败',
          content: probeUrl + '\n' + (err.errMsg || '未知错误'),
          showCancel: false
        })
      }
    })

    // 检查本地缓存登录态
    const user = auth.checkSession()
    if (user) {
      console.log('用户已从缓存恢复登录:', user.username)
    } else {
      console.log('用户未登录或登录已过期')
    }
  },

  globalData: {
    userInfo: null,
    isLoggedIn: false
  }
})
