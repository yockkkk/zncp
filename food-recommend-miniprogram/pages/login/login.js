const auth = require('../../utils/auth')

Page({
  data: {
    loginType: 'wx', // 'wx' or 'pwd'
    agreed: false,
    username: '',
    password: '',
    loading: false
  },

  onShow() {
    // If already logged in, redirect to index
    const user = auth.checkSession()
    if (user) {
      wx.switchTab({
        url: '/pages/index/index'
      })
    }
  },

  switchLoginType(e) {
    this.setData({
      loginType: e.currentTarget.dataset.type
    })
  },

  onUsernameInput(e) {
    this.setData({
      username: e.detail.value
    })
  },

  onPasswordInput(e) {
    this.setData({
      password: e.detail.value
    })
  },

  onAgreementChange(e) {
    this.setData({
      agreed: e.detail.value.includes('agreed')
    })
  },

  async handleWxLogin() {
    if (!this.data.agreed) {
      wx.showModal({
        title: '提示',
        content: '请先阅读并同意《用户服务协议》与《隐私保护指引》',
        showCancel: false
      })
      return
    }

    this.setData({ loading: true })
    wx.showLoading({ title: '登录中...' })

    try {
      await auth.wxLogin()
      wx.hideLoading()
      wx.showToast({
        title: '登录成功',
        icon: 'success',
        duration: 1500
      })
      
      // Check binding and switch tab
      setTimeout(() => {
        wx.switchTab({
          url: '/pages/index/index'
        })
      }, 1500)
    } catch (err) {
      wx.hideLoading()
      wx.showModal({
        title: '登录失败',
        content: err.message || '网络连接异常，请稍后再试',
        showCancel: false
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  async handlePwdLogin() {
    if (!this.data.agreed) {
      wx.showModal({
        title: '提示',
        content: '请先阅读并同意《用户服务协议》与《隐私保护指引》',
        showCancel: false
      })
      return
    }

    const username = this.data.username.trim()
    const password = this.data.password.trim()

    if (!username) {
      wx.showToast({ title: '请输入用户名', icon: 'none' })
      return
    }
    if (!password) {
      wx.showToast({ title: '请输入密码', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    wx.showLoading({ title: '登录中...' })

    try {
      await auth.loginWithPassword(username, password)
      wx.hideLoading()
      wx.showToast({
        title: '登录成功',
        icon: 'success',
        duration: 1500
      })
      
      setTimeout(() => {
        wx.switchTab({
          url: '/pages/index/index'
        })
      }, 1500)
    } catch (err) {
      wx.hideLoading()
      wx.showModal({
        title: '登录失败',
        content: err.message || '用户名或密码不正确',
        showCancel: false
      })
    } finally {
      this.setData({ loading: false })
    }
  }
})
