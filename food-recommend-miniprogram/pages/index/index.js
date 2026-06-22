const api = require('../../utils/api')

Page({
  data: {
    userInfo: null,
    listening: false,
    voiceText: '',
    voiceSupported: true,
    showTags: false,
    extractedTags: null,
    processing: false,
    loading: false,
    currentStep: 0,
    result: null,
    recordId: null,
    adoptedDishes: {}
  },

  onLoad() {
    const user = wx.getStorageSync('user')
    this.setData({
      userInfo: user ? JSON.parse(user) : null,
      voiceSupported: !!wx.getRecorderManager
    })

    // 初始化录音管理器
    if (wx.getRecorderManager) {
      this.recorder = wx.getRecorderManager()
      this.recorder.onStop((res) => {
        this.processRecording(res.tempFilePath)
      })
      this.recorder.onError((err) => {
        console.error('录音错误:', err)
        wx.showToast({ title: '录音失败', icon: 'error' })
        this.setData({ listening: false })
      })
    }
  },

  // ===== 录音 =====
  startRecord() {
    if (!this.recorder) {
      wx.showToast({ title: '请在微信中打开', icon: 'none' })
      return
    }
    this.setData({ listening: true })
    this.recorder.start({
      duration: 30000, // 最长 30 秒
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeBitRate: 48000,
      format: 'mp3'
    })
  },

  stopRecord() {
    if (this.recorder) {
      this.recorder.stop()
      this.setData({ listening: false })
    }
  },

  // ===== 语音转文字 =====
  // eslint-disable-next-line no-unused-vars
  processRecording(filePath) {
    wx.showLoading({ title: '识别语音中...' })

    // 使用微信同声传译插件做语音识别
    const plugin = requirePlugin('WechatSI')
    const manager = plugin.getRecordRecognitionManager()

    manager.onRecognize = (res) => {
      console.log('识别中:', res.result)
      this.setData({
        voiceText: res.result
      })
    }

    manager.onStop = (res) => {
      wx.hideLoading()
      const text = res.result || ''
      this.setData({ voiceText: text })
      if (text) {
        // 自动调用 Agent 0 提取标签
        this.processVoice()
      } else {
        wx.showToast({ title: '未识别到语音', icon: 'none' })
      }
    }

    manager.onError = (err) => {
      wx.hideLoading()
      console.error('语音识别错误:', err)
      // 降级：使用模拟文本
      this.setData({
        voiceText: '语音识别中，请稍候...'
      })
      wx.showToast({ title: '语音识别插件出错', icon: 'none' })
    }

    // 启动识别
    manager.start({
      lang: 'zh_CN',
      duration: 30000
    })
    // 传入录音文件路径
    manager.start({ lang: 'zh_CN' })
  },

  // ===== Agent 0：语音理解 =====
  async processVoice() {
    if (!this.data.voiceText || this.data.voiceText.length < 3) {
      wx.showToast({ title: '语音文本太短', icon: 'none' })
      return
    }

    this.setData({ processing: true })
    try {
      // eslint-disable-next-line no-unused-vars
      const fd = new FormData()
      // 小程序端用 wx.request 直接调 voice recommend
      // 先做 Agent 0 提取标签展示
      const tags = await api.post('/waiter/recommend/voice/preview', {
        voiceText: this.data.voiceText
      }).catch(() => {
        // 如果 preview 端点不存在，直接显示 voiceText 让用户确认
        return null
      })

      if (tags) {
        this.setData({ extractedTags: tags, showTags: true })
      } else {
        // 没有 preview 端点，直接走完整推荐
        this.setData({ showTags: true, extractedTags: null })
      }
    } catch (e) {
      wx.showToast({ title: e.message || '处理失败', icon: 'none' })
    } finally {
      this.setData({ processing: false })
    }
  },

  // ===== 完整推荐管线 =====
  async doRecommend() {
    const voiceText = this.data.voiceText
    if (!voiceText) {
      wx.showToast({ title: '请先说出顾客需求', icon: 'none' })
      return
    }

    this.setData({ loading: true, currentStep: 0, showTags: false })
    const stepTimer = setInterval(() => {
      if (this.data.currentStep < 5) {
        this.setData({ currentStep: this.data.currentStep + 1 })
      }
    }, 4000)

    try {
      // 用 FormData 方式发送
      const token = wx.getStorageSync('token')
      const res = await new Promise((resolve, reject) => {
        wx.request({
          url: 'http://localhost:8080/api/waiter/recommend/voice',
          method: 'POST',
          header: {
            'Authorization': 'Bearer ' + token,
            'Content-Type': 'application/x-www-form-urlencoded'
          },
          data: 'voiceText=' + encodeURIComponent(voiceText),
          timeout: 180000,
          success(r) {
            if (r.data.code === 200) resolve(r.data.data)
            else reject(new Error(r.data.message))
          },
          fail: reject
        })
      })

      this.setData({
        result: res,
        recordId: res.recordId,
        adoptedDishes: {},
        currentStep: 6
      })
    } catch (e) {
      wx.showToast({ title: e.message || '推荐失败', icon: 'none' })
    } finally {
      clearInterval(stepTimer)
      this.setData({ loading: false })
    }
  },

  // ===== 采纳反馈 =====
  async adoptDish(e) {
    const dishId = e.currentTarget.dataset.id
    if (!this.data.recordId) return
    try {
      await api.post('/waiter/feedback/' + this.data.recordId, {
        adopted: true,
        adoptedDishId: dishId,
        rating: 5
      })
      const adoptedDishes = { ...this.data.adoptedDishes }
      adoptedDishes[dishId] = true
      this.setData({ adoptedDishes })
      wx.showToast({ title: '已记录采纳', icon: 'success' })
    } catch (e) {
      wx.showToast({ title: e.message || '反馈失败', icon: 'none' })
    }
  },

  // ===== 重置 =====
  clearVoice() {
    this.setData({ voiceText: '', showTags: false, extractedTags: null })
  },

  resetAll() {
    this.setData({
      voiceText: '', showTags: false, extractedTags: null,
      loading: false, result: null, recordId: null,
      adoptedDishes: {}, currentStep: 0
    })
  }
})
