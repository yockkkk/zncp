const api = require('../../utils/api')
const auth = require('../../utils/auth')

Page({
  data: {
    userInfo: null,
    editRealName: '',
    editPhone: '',
    totalAdopted: 0,
    revenue: '0.00',
    saving: false
  },

  onShow() {
    const user = auth.checkSession()
    if (!user) {
      wx.reLaunch({
        url: '/pages/login/login'
      })
      return
    }

    const dataToSet = { userInfo: user }
    if (!this.data.editRealName && !this.data.editPhone) {
      dataToSet.editRealName = user.realName === '微信服务员' ? '' : (user.realName || '')
      dataToSet.editPhone = user.phone || ''
    } else if (this.data.userInfo && this.data.userInfo.phone !== user.phone) {
      dataToSet.editRealName = user.realName === '微信服务员' ? '' : (user.realName || '')
      dataToSet.editPhone = user.phone || ''
    }

    this.setData(dataToSet)
    this.fetchStats()
  },

  async fetchStats() {
    try {
      const records = await api.get('/waiter/history')
      const totalAdopted = records.filter(r => r.adopted === 1).length

      const revRes = await api.get('/waiter/revenue')
      const revenue = revRes.revenue !== undefined ? parseFloat(revRes.revenue).toFixed(2) : '0.00'

      this.setData({
        totalAdopted,
        revenue
      })
    } catch (e) {
      console.error('获取业绩数据失败:', e)
    }
  },

  onRealNameInput(e) {
    this.setData({
      editRealName: e.detail.value
    })
  },

  onPhoneInput(e) {
    let val = e.detail.value || ''
    val = val.replace(/\D/g, '').slice(0, 11)
    this.setData({
      editPhone: val
    })
  },

  async saveProfile() {
    const realName = this.data.editRealName.trim()
    const phone = this.data.editPhone.trim()

    if (!realName) {
      wx.showToast({ title: '姓名不能为空', icon: 'none' })
      return
    }
    if (!phone) {
      wx.showToast({ title: '手机号不能为空', icon: 'none' })
      return
    }
    if (phone.length !== 11) {
      wx.showToast({ title: '手机号格式不正确', icon: 'none' })
      return
    }

    this.setData({ saving: true })
    wx.showLoading({ title: '保存中...' })

    try {
      const updatedUser = await api.put('/waiter/profile', {
        realName,
        phone
      })

      // Update local storage and state
      const user = { ...this.data.userInfo, realName: updatedUser.realName, phone: updatedUser.phone || '' }
      wx.setStorageSync('user', JSON.stringify(user))

      this.setData({
        userInfo: user,
        editRealName: user.realName,
        editPhone: user.phone || ''
      })
      wx.showToast({ title: '保存成功', icon: 'success' })
    } catch (err) {
      wx.showToast({ title: err.message || '保存失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
      wx.hideLoading()
    }
  },

  handleLogout() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          auth.logout()
          wx.reLaunch({
            url: '/pages/login/login'
          })
        }
      }
    })
  }
})
