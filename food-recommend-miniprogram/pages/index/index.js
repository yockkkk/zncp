const api = require('../../utils/api')
const auth = require('../../utils/auth')
const config = require('../../config')

Page({
  data: {
    // 基础认证及状态
    userInfo: null,
    listening: false,
    voiceText: '',
    voiceSupported: true,
    processing: false,
    loading: false,
    currentStep: 0,
    result: null,
    recordId: null,
    adoptedDishes: {},
    showProfileModal: false,
    editRealName: '',
    editPhone: '',

    // 1. 模式及现场照片
    mode: 'single', // 'single' 常规, 'multi' 多人
    phone: '',
    historyProfile: null,
    phoneLoading: false,
    sceneImagePath: '',

    // 2. 表单选择状态 (常规模式)
    peopleCount: '',
    diningScene: '',
    mealTime: '',
    budgetLevel: '',
    dietaryRestriction: '',
    tastePreferences: [],
    avoidIngredients: [],
    allergens: [],
    diseases: [],

    // 激活状态 Map (方便 WXML 渲染)
    tastePreferencesActive: {},
    avoidIngredientsActive: {},
    allergensActive: {},
    diseasesActive: {},

    // 3. 多人配菜状态
    guests: [],
    activeGuestIndex: 0,

    // 4. 结果采纳份数
    adoptQtys: {}, // dishId -> quantity (默认 1)

    // ===== 静态配置项 =====
    PEOPLE_OPTIONS: ['1', '2', '3-4', '5+'],
    SCENE_OPTIONS: ['便餐', '约会', '商务', '家庭', '朋友聚餐'],
    TASTE_OPTIONS: ['辣', '清淡', '甜', '咸', '爱吃酸', '爱吃麻', '不爱甜', '喜欢嫩', '无偏好'],
    BUDGET_OPTIONS: ['实惠', '中等', '高端', '不限'],
    BUDGET_OPTIONS_NO_ALL: ['实惠', '中等', '高端'],
    DIETARY_OPTIONS: ['无', '素食', '低脂', '高蛋白'],
    MEAL_TIME_OPTIONS: ['早餐', '午餐', '晚餐', '夜宵'],
    GUEST_AVOID_OPTIONS: ['辣', '香菜', '葱', '蒜', '牛肉', '羊肉'],
    GUEST_ALLERGEN_OPTIONS: ['花生', '海鲜', '鸡蛋', '牛奶'],
    GUEST_DISEASE_OPTIONS: ['痛风', '糖尿病', '高血压', '胃病', '术后'],
    GUEST_LIFESTYLE_OPTIONS: ['清真', '素食', 'Keto', '减脂'],
    GUEST_TASTE_OPTIONS: ['爱吃酸', '爱吃麻', '不爱甜', '喜欢嫩', '要下饭', '要清淡']
  },

  onLoad() {
    // 初始化录音管理器
    if (wx.getRecorderManager) {
      this.recorder = wx.getRecorderManager()
      this.recorder.onStop((res) => {
        if (res && res.duration < 1000) {
          wx.showToast({ title: '说话时间太短，请长按录音', icon: 'none' })
          return
        }
        this.processRecording(res.tempFilePath)
      })
      this.recorder.onError((err) => {
        console.error('录音错误:', err)
        wx.showToast({ title: '录音失败', icon: 'error' })
        this.setData({ listening: false })
      })
    }
    this.setData({
      voiceSupported: !!wx.getRecorderManager
    })
  },

  onShow() {
    const user = auth.checkSession()
    if (!user) {
      wx.reLaunch({
        url: '/pages/login/login'
      })
      return
    }
    this.setData({
      userInfo: user
    })
    this.checkProfileBinding(user)
  },

  onUnload() {
    if (this._phoneQueryTimer) {
      clearTimeout(this._phoneQueryTimer)
      this._phoneQueryTimer = null
    }
    if (this.stepInterval) {
      clearInterval(this.stepInterval)
      this.stepInterval = null
    }
    if (this.recorder) {
      try { this.recorder.stop() } catch (e) {}
      this.recorder = null
    }
  },

  checkProfileBinding(user) {
    if (!user) return
    const isUnbound = !user.phone || user.realName === '微信服务员' || !user.realName
    if (isUnbound) {
      this.setData({
        showProfileModal: true,
        editRealName: user.realName === '微信服务员' ? '' : (user.realName || ''),
        editPhone: user.phone || ''
      })
      wx.showModal({
        title: '基本信息绑定',
        content: '为了正常使用推荐系统，请先绑定真实姓名与手机号',
        showCancel: false
      })
    }
  },

  // ===== 切换模式 =====
  switchMode(e) {
    const mode = e.currentTarget.dataset.mode
    if (mode === this.data.mode) return

    if (mode === 'multi') {
      let count = 2
      if (this.data.peopleCount === '1') count = 1
      else if (this.data.peopleCount === '2') count = 2
      else if (this.data.peopleCount === '3-4') count = 3
      else if (this.data.peopleCount === '5+') count = 5

      const guests = []
      for (let i = 0; i < count; i++) {
        const nextLetter = String.fromCharCode(65 + i)
        guests.push({
          name: `顾客${nextLetter}`,
          avoidIngredients: [],
          allergens: [],
          diseases: [],
          dietLifestyles: [],
          tastes: [],
          _avoidActive: {},
          _allergenActive: {},
          _diseaseActive: {},
          _lifestyleActive: {},
          _tasteActive: {}
        })
      }
      this.setData({
        mode,
        guests,
        activeGuestIndex: 0,
        phone: '',
        historyProfile: null
      })
    } else {
      const count = this.data.guests.length
      let peopleCount = ''
      if (count === 1) peopleCount = '1'
      else if (count === 2) peopleCount = '2'
      else if (count >= 3 && count <= 4) peopleCount = '3-4'
      else if (count >= 5) peopleCount = '5+'

      this.setData({
        mode,
        peopleCount,
        guests: []
      })
    }
  },

  // ===== 长期记忆 / 手机号查询 =====
  onPhoneInput(e) {
    // 同步：立即清洗并回写输入，保证用户输入/删除即时响应
    const raw = e && e.detail && typeof e.detail.value === 'string' ? e.detail.value : ''
    const phone = raw.replace(/\D/g, '').slice(0, 11)
    this.setData({ phone })

    // 不是 11 位时立刻清空 historyProfile，且不发请求
    if (phone.length !== 11) {
      if (this.data.historyProfile) {
        this.setData({ historyProfile: null })
      }
      return
    }

    // 11 位时防抖查询，避免每个按键都打后端
    if (this._phoneQueryTimer) {
      clearTimeout(this._phoneQueryTimer)
    }
    this._phoneQueryTimer = setTimeout(() => {
      // 二次校验：用户可能在等待期间又删除了
      if (this.data.phone !== phone) return
      this.setData({ phoneLoading: true })
      api.get(`/waiter/customer/profile?phone=${phone}`)
        .then((profile) => {
          if (this.data.phone === phone) {
            this.setData({ historyProfile: profile || null })
          }
        })
        .catch((err) => {
          console.error('获取顾客长期记忆失败:', err)
          if (this.data.phone === phone) {
            this.setData({ historyProfile: null })
          }
        })
        .finally(() => {
          this.setData({ phoneLoading: false })
        })
    }, 350)
  },

  // 套用历史画像偏好
  applyHistoryTastes() {
    const profile = this.data.historyProfile
    if (!profile) return

    const tastePreferences = [...this.data.tastePreferences]
    const tastePreferencesActive = { ...this.data.tastePreferencesActive }
    if (profile.historyTastes) {
      profile.historyTastes.forEach(t => {
        if (!tastePreferences.includes(t)) {
          tastePreferences.push(t)
          tastePreferencesActive[t] = true
        }
      })
    }

    const avoidIngredients = [...this.data.avoidIngredients]
    const avoidIngredientsActive = { ...this.data.avoidIngredientsActive }
    if (profile.consolidatedAvoids) {
      profile.consolidatedAvoids.forEach(t => {
        if (!avoidIngredients.includes(t)) {
          avoidIngredients.push(t)
          avoidIngredientsActive[t] = true
        }
      })
    }

    const allergens = [...this.data.allergens]
    const allergensActive = { ...this.data.allergensActive }
    if (profile.consolidatedAllergens) {
      profile.consolidatedAllergens.forEach(t => {
        if (!allergens.includes(t)) {
          allergens.push(t)
          allergensActive[t] = true
        }
      })
    }

    const diseases = [...this.data.diseases]
    const diseasesActive = { ...this.data.diseasesActive }
    if (profile.consolidatedDiseases) {
      profile.consolidatedDiseases.forEach(t => {
        if (!diseases.includes(t)) {
          diseases.push(t)
          diseasesActive[t] = true
        }
      })
    }

    let dietaryRestriction = this.data.dietaryRestriction
    if (profile.consolidatedDietLifestyles) {
      profile.consolidatedDietLifestyles.forEach(l => {
        if (['素食', '低脂', '高蛋白'].includes(l)) {
          dietaryRestriction = l
        }
      })
    }

    this.setData({
      tastePreferences,
      tastePreferencesActive,
      avoidIngredients,
      avoidIngredientsActive,
      allergens,
      allergensActive,
      diseases,
      diseasesActive,
      dietaryRestriction
    })

    wx.showToast({ title: '已成功套用历史偏好', icon: 'success' })
  },

  // ===== 现场拍照环境 =====
  chooseSceneImage() {
    wx.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        this.setData({ sceneImagePath: res.tempFilePaths[0] })
      }
    })
  },

  clearSceneImage() {
    this.setData({ sceneImagePath: '' })
  },

  // ===== 常规模式单选 / 多选操作 =====
  selectPeople(e) { this.setData({ peopleCount: e.currentTarget.dataset.val }) },
  selectMealTime(e) { this.setData({ mealTime: e.currentTarget.dataset.val }) },
  selectScene(e) { this.setData({ diningScene: e.currentTarget.dataset.val }) },
  selectBudget(e) { this.setData({ budgetLevel: e.currentTarget.dataset.val }) },
  selectDietary(e) { this.setData({ dietaryRestriction: e.currentTarget.dataset.val }) },

  toggleTaste(e) {
    const val = e.currentTarget.dataset.val
    const arr = [...this.data.tastePreferences]
    const idx = arr.indexOf(val)
    const map = { ...this.data.tastePreferencesActive }

    if (idx >= 0) {
      arr.splice(idx, 1)
      map[val] = false
    } else {
      arr.push(val)
      map[val] = true
    }
    this.setData({ tastePreferences: arr, tastePreferencesActive: map })
  },

  toggleAvoid(e) {
    const val = e.currentTarget.dataset.val
    const arr = [...this.data.avoidIngredients]
    const idx = arr.indexOf(val)
    const map = { ...this.data.avoidIngredientsActive }

    if (idx >= 0) {
      arr.splice(idx, 1)
      map[val] = false
    } else {
      arr.push(val)
      map[val] = true
    }
    this.setData({ avoidIngredients: arr, avoidIngredientsActive: map })
  },

  toggleAllergen(e) {
    const val = e.currentTarget.dataset.val
    const arr = [...this.data.allergens]
    const idx = arr.indexOf(val)
    const map = { ...this.data.allergensActive }

    if (idx >= 0) {
      arr.splice(idx, 1)
      map[val] = false
    } else {
      arr.push(val)
      map[val] = true
    }
    this.setData({ allergens: arr, allergensActive: map })
  },

  toggleDisease(e) {
    const val = e.currentTarget.dataset.val
    const arr = [...this.data.diseases]
    const idx = arr.indexOf(val)
    const map = { ...this.data.diseasesActive }

    if (idx >= 0) {
      arr.splice(idx, 1)
      map[val] = false
    } else {
      arr.push(val)
      map[val] = true
    }
    this.setData({ diseases: arr, diseasesActive: map })
  },

  // ===== 多人模式下拉选择及顾客编辑 =====
  onMultiSceneChange(e) { this.setData({ diningScene: this.data.SCENE_OPTIONS[e.detail.value] }) },
  onMultiMealTimeChange(e) { this.setData({ mealTime: this.data.MEAL_TIME_OPTIONS[e.detail.value] }) },
  onMultiBudgetChange(e) { this.setData({ budgetLevel: this.data.BUDGET_OPTIONS_NO_ALL[e.detail.value] }) },

  addGuest() {
    const nextLetter = String.fromCharCode(65 + this.data.guests.length)
    const guests = [...this.data.guests]
    guests.push({
      name: `顾客${nextLetter}`,
      avoidIngredients: [],
      allergens: [],
      diseases: [],
      dietLifestyles: [],
      tastes: [],
      _avoidActive: {},
      _allergenActive: {},
      _diseaseActive: {},
      _lifestyleActive: {},
      _tasteActive: {}
    })
    this.setData({ guests, activeGuestIndex: guests.length - 1 })
  },

  removeGuest(e) {
    const idx = e.currentTarget.dataset.idx
    const guests = [...this.data.guests]
    guests.splice(idx, 1)

    guests.forEach((g, i) => {
      if (/^顾客[A-Z]$/.test(g.name)) {
        g.name = `顾客${String.fromCharCode(65 + i)}`
      }
    })

    let activeIdx = this.data.activeGuestIndex
    if (activeIdx >= guests.length) {
      activeIdx = Math.max(0, guests.length - 1)
    }
    this.setData({ guests, activeGuestIndex: activeIdx })
  },

  switchGuestTab(e) {
    this.setData({ activeGuestIndex: e.currentTarget.dataset.idx })
  },

  onGuestNameInput(e) {
    const name = e.detail.value
    const idx = this.data.activeGuestIndex
    const guests = [...this.data.guests]
    if (guests[idx]) {
      guests[idx].name = name
      this.setData({ guests })
    }
  },

  toggleGuestAvoid(e) {
    const val = e.currentTarget.dataset.val
    const idx = this.data.activeGuestIndex
    const guests = [...this.data.guests]
    const g = guests[idx]
    if (g) {
      const arr = [...g.avoidIngredients]
      const map = { ...g._avoidActive }
      const i = arr.indexOf(val)
      if (i >= 0) {
        arr.splice(i, 1); map[val] = false
      } else {
        arr.push(val); map[val] = true
      }
      g.avoidIngredients = arr
      g._avoidActive = map
      this.setData({ guests })
    }
  },

  toggleGuestAllergen(e) {
    const val = e.currentTarget.dataset.val
    const idx = this.data.activeGuestIndex
    const guests = [...this.data.guests]
    const g = guests[idx]
    if (g) {
      const arr = [...g.allergens]
      const map = { ...g._allergenActive }
      const i = arr.indexOf(val)
      if (i >= 0) {
        arr.splice(i, 1); map[val] = false
      } else {
        arr.push(val); map[val] = true
      }
      g.allergens = arr
      g._allergenActive = map
      this.setData({ guests })
    }
  },

  toggleGuestDisease(e) {
    const val = e.currentTarget.dataset.val
    const idx = this.data.activeGuestIndex
    const guests = [...this.data.guests]
    const g = guests[idx]
    if (g) {
      const arr = [...g.diseases]
      const map = { ...g._diseaseActive }
      const i = arr.indexOf(val)
      if (i >= 0) {
        arr.splice(i, 1); map[val] = false
      } else {
        arr.push(val); map[val] = true
      }
      g.diseases = arr
      g._diseaseActive = map
      this.setData({ guests })
    }
  },

  toggleGuestLifestyle(e) {
    const val = e.currentTarget.dataset.val
    const idx = this.data.activeGuestIndex
    const guests = [...this.data.guests]
    const g = guests[idx]
    if (g) {
      const arr = [...g.dietLifestyles]
      const map = { ...g._lifestyleActive }
      const i = arr.indexOf(val)
      if (i >= 0) {
        arr.splice(i, 1); map[val] = false
      } else {
        arr.push(val); map[val] = true
      }
      g.dietLifestyles = arr
      g._lifestyleActive = map
      this.setData({ guests })
    }
  },

  toggleGuestTaste(e) {
    const val = e.currentTarget.dataset.val
    const idx = this.data.activeGuestIndex
    const guests = [...this.data.guests]
    const g = guests[idx]
    if (g) {
      const arr = [...g.tastes]
      const map = { ...g._tasteActive }
      const i = arr.indexOf(val)
      if (i >= 0) {
        arr.splice(i, 1); map[val] = false
      } else {
        arr.push(val); map[val] = true
      }
      g.tastes = arr
      g._tasteActive = map
      this.setData({ guests })
    }
  },

  // ===== 语音录音录制 =====
  startRecord() {
    if (!this.recorder) {
      wx.showToast({ title: '请在微信环境中打开', icon: 'none' })
      return
    }
    wx.getSetting({
      success: (res) => {
        if (res.authSetting['scope.record'] === false) {
          wx.showModal({
            title: '需要录音权限',
            content: '需要录音权限，请在设置中开启',
            confirmText: '去设置',
            success: (r) => {
              if (r.confirm) wx.openSetting()
            }
          })
          return
        }
        if (res.authSetting['scope.record'] === true) {
          this._doStartRecord()
          return
        }
        wx.authorize({
          scope: 'scope.record',
          success: () => this._doStartRecord(),
          fail: () => {
            wx.showModal({
              title: '需要录音权限',
              content: '需要录音权限，请在设置中开启',
              confirmText: '去设置',
              success: (r) => {
                if (r.confirm) wx.openSetting()
              }
            })
          }
        })
      }
    })
  },

  _doStartRecord() {
    this.setData({ listening: true })
    this.recorder.start({
      duration: 30000,
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeBitRate: 96000,
      format: 'mp3'
    })
  },

  stopRecord() {
    if (this.recorder) {
      this.recorder.stop()
      this.setData({ listening: false })
    }
  },

  processRecording(filePath) {
    wx.showLoading({ title: '语音识别中...' })
    const token = wx.getStorageSync('token')

    wx.uploadFile({
      url: config.BASE_URL + '/api/waiter/audio/transcribe',
      filePath: filePath,
      name: 'file',
      header: { 
        'Authorization': 'Bearer ' + token,
        'Bypass-Tunnel-Reminder': 'true'
      },
      timeout: 120000,
      success: (res) => {
        wx.hideLoading()
        try {
          const data = JSON.parse(res.data)
          if (data.code === 200) {
            const text = (data.data || '').trim()
            this.setData({ voiceText: text })
            if (text) {
              wx.showToast({ title: '识别成功', icon: 'success' })
              // 自动拉取 Agent 0 预览解析
              this.processVoice()
            } else {
              wx.showToast({ title: '未识别到有效语音内容', icon: 'none' })
            }
          } else {
            wx.showToast({ title: data.message || '识别失败', icon: 'none' })
          }
        } catch (e) {
          console.error('音频转换解析失败:', e)
          wx.showToast({ title: '识别结果解析异常', icon: 'none' })
        }
      },
      fail: (err) => {
        wx.hideLoading()
        console.error('语音上传失败:', err)
        if (err.errMsg && err.errMsg.includes('timeout')) {
          wx.showToast({ title: '语音识别超时，请重试', icon: 'none' })
        } else {
          wx.showToast({ title: '网络异常，请检查后端服务', icon: 'none' })
        }
      }
    })
  },

  // Agent 0: 语音自动提取标签，并一键回填到表单中
  async processVoice() {
    if (!this.data.voiceText || this.data.voiceText.trim().length === 0) {
      wx.showToast({ title: '请输入顾客偏好描述', icon: 'none' })
      return
    }
    if (this.data.voiceText.trim().length < 3) {
      wx.showToast({ title: '请输入至少3个字的描述', icon: 'none' })
      return
    }

    this.setData({ processing: true })
    wx.showLoading({ title: 'AI 分析提取中...' })

    try {
      const token = wx.getStorageSync('token')
      const parsedTags = await new Promise((resolve, reject) => {
        wx.request({
          url: config.BASE_URL + '/api/waiter/recommend/voice/preview',
          method: 'POST',
          header: {
            'Authorization': 'Bearer ' + token,
            'Content-Type': 'application/json',
            'Bypass-Tunnel-Reminder': 'true'
          },
          data: { voiceText: this.data.voiceText },
          timeout: 120000,
          success(r) {
            if (r.data && r.data.code === 200) resolve(r.data.data)
            else reject(new Error((r.data && r.data.message) || '语音分析返回异常'))
          },
          fail(err) {
            const msg = (err && err.errMsg) ? err.errMsg : '网络异常'
            reject(new Error('Agent0 请求失败: ' + msg))
          }
        })
      })

      if (parsedTags) {
        const updates = {}
        const isMulti = parsedTags.mode === 'multi' || (parsedTags.guests && parsedTags.guests.length > 0)
        
        if (isMulti) {
          updates.mode = 'multi'
          const parsedGuests = parsedTags.guests || []
          const guests = parsedGuests.map((g, index) => {
            const nextLetter = String.fromCharCode(65 + index)
            const avoidIngredients = g.avoidIngredients || []
            const allergens = g.allergens || []
            const diseases = g.diseases || []
            const dietLifestyles = g.dietLifestyles || []
            const tastes = g.tastes || []

            const _avoidActive = {}
            avoidIngredients.forEach(x => _avoidActive[x] = true)

            const _allergenActive = {}
            allergens.forEach(x => _allergenActive[x] = true)

            const _diseaseActive = {}
            diseases.forEach(x => _diseaseActive[x] = true)

            const _lifestyleActive = {}
            dietLifestyles.forEach(x => _lifestyleActive[x] = true)

            const _tasteActive = {}
            tastes.forEach(x => _tasteActive[x] = true)

            return {
              name: g.name || `顾客${nextLetter}`,
              avoidIngredients,
              allergens,
              diseases,
              dietLifestyles,
              tastes,
              _avoidActive,
              _allergenActive,
              _diseaseActive,
              _lifestyleActive,
              _tasteActive
            }
          })
          updates.guests = guests
          updates.activeGuestIndex = 0
          
          // 共享属性
          updates.diningScene = parsedTags.diningScene || this.data.diningScene || '朋友聚餐'
          updates.budgetLevel = parsedTags.budgetLevel || this.data.budgetLevel || '中等'
          updates.mealTime = parsedTags.mealTime || this.data.mealTime
        } else {
          updates.mode = 'single'
          updates.peopleCount = parsedTags.peopleCount || this.data.peopleCount
          updates.diningScene = parsedTags.diningScene || this.data.diningScene
          updates.budgetLevel = parsedTags.budgetLevel || this.data.budgetLevel
          updates.mealTime = parsedTags.mealTime || this.data.mealTime
          updates.dietaryRestriction = parsedTags.dietaryRestriction || this.data.dietaryRestriction

          // 口味偏好
          if (parsedTags.tastePreferences && parsedTags.tastePreferences.length) {
            const tastePreferences = [...this.data.tastePreferences]
            const tastePreferencesActive = { ...this.data.tastePreferencesActive }
            parsedTags.tastePreferences.forEach(t => {
              if (!tastePreferences.includes(t)) {
                tastePreferences.push(t)
                tastePreferencesActive[t] = true
              }
            })
            updates.tastePreferences = tastePreferences
            updates.tastePreferencesActive = tastePreferencesActive
          }

          // 忌口
          if (parsedTags.avoidIngredients && parsedTags.avoidIngredients.length) {
            const avoidIngredients = [...this.data.avoidIngredients]
            const avoidIngredientsActive = { ...this.data.avoidIngredientsActive }
            parsedTags.avoidIngredients.forEach(t => {
              if (!avoidIngredients.includes(t)) {
                avoidIngredients.push(t)
                avoidIngredientsActive[t] = true
              }
            })
            updates.avoidIngredients = avoidIngredients
            updates.avoidIngredientsActive = avoidIngredientsActive
          }

          // 过敏源
          if (parsedTags.allergens && parsedTags.allergens.length) {
            const allergens = [...this.data.allergens]
            const allergensActive = { ...this.data.allergensActive }
            parsedTags.allergens.forEach(t => {
              if (!allergens.includes(t)) {
                allergens.push(t)
                allergensActive[t] = true
              }
            })
            updates.allergens = allergens
            updates.allergensActive = allergensActive
          }

          // 疾病禁忌
          if (parsedTags.diseases && parsedTags.diseases.length) {
            const diseases = [...this.data.diseases]
            const diseasesActive = { ...this.data.diseasesActive }
            parsedTags.diseases.forEach(t => {
              if (!diseases.includes(t)) {
                diseases.push(t)
                diseasesActive[t] = true
              }
            })
            updates.diseases = diseases
            updates.diseasesActive = diseasesActive
          }
        }

        this.setData(updates)
        wx.showToast({ title: 'AI 标签已回填至表单', icon: 'success' })
      }
    } catch (e) {
      console.warn('Agent 0 语音分析失败:', e.message)
      wx.showToast({ title: '语音分析失败，请手动调整标签', icon: 'none' })
    } finally {
      this.setData({ processing: false })
      wx.hideLoading()
    }
  },

  onVoiceTextInput(e) {
    this.setData({
      voiceText: e.detail.value
    })
  },

  // ===== 智能生成配餐推荐 (整合了多智能体管线) =====
  async generateRecommend() {
    // 校验必填属性
    if (this.data.mode === 'single' && !this.data.peopleCount) {
      wx.showToast({ title: '请选择用餐人数', icon: 'none' })
      return
    }
    if (!this.data.diningScene) {
      wx.showToast({ title: '请选择用餐场景', icon: 'none' })
      return
    }
    if (!this.data.mealTime) {
      wx.showToast({ title: '请选择用餐时段', icon: 'none' })
      return
    }

    // 构建 tagInputJson 属性
    const tags = {
      phone: this.data.phone || null,
      diningScene: this.data.diningScene,
      budgetLevel: this.data.budgetLevel || '不限',
      dietaryRestriction: this.data.dietaryRestriction || '无',
      mealTime: this.data.mealTime
    }

    if (this.data.mode === 'single') {
      tags.peopleCount = this.data.peopleCount
      tags.tastePreferences = this.data.tastePreferences
      tags.avoidIngredients = this.data.avoidIngredients
      tags.allergens = this.data.allergens
      tags.diseases = this.data.diseases
    } else {
      tags.peopleCount = String(this.data.guests.length)
      // 清洗私有 WXML 绑定渲染属性
      tags.guests = this.data.guests.map(g => ({
        name: g.name,
        avoidIngredients: g.avoidIngredients,
        allergens: g.allergens,
        diseases: g.diseases,
        dietLifestyles: g.dietLifestyles,
        tastes: g.tastes
      }))
    }

    this.setData({ loading: true, currentStep: 0, result: null })

    // Agent Pipeline 步骤条自动轮询计数
    const stepInterval = setInterval(() => {
      if (this.data.currentStep < 5) {
        this.setData({ currentStep: this.data.currentStep + 1 })
      }
    }, 3500)
    this.stepInterval = stepInterval

    try {
      const token = wx.getStorageSync('token')
      let resData = null

      if (this.data.sceneImagePath) {
        // 1. 如果有上传照片，调用 Multipart 上传管道
        resData = await new Promise((resolve, reject) => {
          wx.uploadFile({
            url: config.BASE_URL + '/api/waiter/recommend',
            filePath: this.data.sceneImagePath,
            name: 'sceneImage',
            header: {
              'Authorization': 'Bearer ' + token,
              'Bypass-Tunnel-Reminder': 'true'
            },
            formData: { 'tagInputJson': JSON.stringify(tags) },
            timeout: 180000,
            success(r) {
              try {
                const parsed = JSON.parse(r.data)
                if (parsed.code === 200) resolve(parsed.data)
                else reject(new Error(parsed.message))
              } catch (e) {
                reject(new Error('数据解析异常'))
              }
            },
            fail: reject
          })
        })
      } else {
        // 2. 无照片，发送常规 JSON 表单请求
        resData = await new Promise((resolve, reject) => {
          wx.request({
            url: config.BASE_URL + '/api/waiter/recommend',
            method: 'POST',
            header: {
              'Authorization': 'Bearer ' + token,
              'Content-Type': 'application/x-www-form-urlencoded',
              'Bypass-Tunnel-Reminder': 'true'
            },
            data: 'tagInputJson=' + encodeURIComponent(JSON.stringify(tags)),
            timeout: 180000,
            success(r) {
              if (r.data.code === 200) resolve(r.data.data)
              else reject(new Error(r.data.message))
            },
            fail: reject
          })
        })
      }

      this.setData({
        result: resData,
        recordId: resData.recordId,
        adoptedDishes: {},
        adoptQtys: {},
        currentStep: 6
      })
      wx.showToast({ title: 'AI 推荐成功！', icon: 'success' })

    } catch (e) {
      console.error('智能推荐管线异常:', e)
      wx.showToast({ title: e.message || 'AI 推荐管线失败，请检查配置或大模型', icon: 'none' })
      this.setData({ result: null })
    } finally {
      clearInterval(stepInterval)
      this.stepInterval = null
      this.setData({ loading: false, currentStep: this.data.result ? 6 : 0 })
    }
  },

  // ===== 份数调整器 =====
  increaseQty(e) {
    const dishId = e.currentTarget.dataset.id
    const adoptQtys = { ...this.data.adoptQtys }
    const current = adoptQtys[dishId] || 1
    adoptQtys[dishId] = current + 1
    this.setData({ adoptQtys })
  },

  decreaseQty(e) {
    const dishId = e.currentTarget.dataset.id
    const adoptQtys = { ...this.data.adoptQtys }
    const current = adoptQtys[dishId] || 1
    if (current > 1) {
      adoptQtys[dishId] = current - 1
      this.setData({ adoptQtys })
    }
  },

  // ===== 采纳推荐反馈 (直接关联库存折算和流水统计) =====
  async adoptDish(e) {
    const dishId = e.currentTarget.dataset.id
    if (!this.data.recordId) return

    const qty = this.data.adoptQtys[dishId] || 1

    try {
      wx.showLoading({ title: '记录采纳中...' })
      await api.post(`/waiter/feedback/${this.data.recordId}`, {
        adopted: true,
        adoptedDishId: dishId,
        quantity: qty,
        rating: 5
      })

      const adoptedDishes = { ...this.data.adoptedDishes }
      adoptedDishes[dishId] = qty
      this.setData({ adoptedDishes })

      wx.showToast({ title: `已采纳 ${qty} 份，已扣减库存`, icon: 'success' })
    } catch (err) {
      wx.showToast({ title: err.message || '采纳失败，可能库存不足', icon: 'none' })
    } finally {
      wx.hideLoading()
    }
  },

  // ===== 复制话术 =====
  copyScript(e) {
    const text = e.currentTarget.dataset.text
    if (text) {
      wx.setClipboardData({
        data: text,
        success() {
          wx.showToast({ title: '话术已复制', icon: 'success' })
        }
      })
    }
  },

  // ===== 修改个人信息 =====
  showProfileModal() {
    const user = this.data.userInfo || {}
    this.setData({
      showProfileModal: true,
      editRealName: user.realName || '',
      editPhone: user.phone || ''
    })
  },

  closeProfileModal() {
    const user = this.data.userInfo || {}
    const isUnbound = !user.phone || user.realName === '微信服务员' || !user.realName
    if (isUnbound) {
      wx.showToast({ title: '请先绑定真实姓名与手机号', icon: 'none' })
      return
    }
    this.setData({ showProfileModal: false })
  },

  onEditRealNameInput(e) {
    this.setData({ editRealName: e.detail.value })
  },

  onEditPhoneInput(e) {
    this.setData({ editPhone: e.detail.value })
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

    wx.showLoading({ title: '保存中...' })
    try {
      const updatedUser = await api.put('/waiter/profile', {
        realName,
        phone: phone || null
      })

      // 更新本地缓存和 Page 状态
      const user = { ...this.data.userInfo, realName: updatedUser.realName, phone: updatedUser.phone || '' }
      wx.setStorageSync('user', JSON.stringify(user))

      this.setData({
        userInfo: user,
        showProfileModal: false
      })
      wx.showToast({ title: '保存成功', icon: 'success' })
    } catch (err) {
      wx.showToast({ title: err.message || '保存失败', icon: 'none' })
    } finally {
      wx.hideLoading()
    }
  },

  // ===== 清空及重置 =====
  clearVoice() {
    this.setData({ voiceText: '' })
  },

  resetAll() {
    this.setData({
      voiceText: '',
      listening: false,
      processing: false,
      loading: false,
      result: null,
      recordId: null,
      adoptedDishes: {},
      adoptQtys: {},
      currentStep: 0,
      mode: 'single',
      phone: '',
      historyProfile: null,
      sceneImagePath: '',
      peopleCount: '',
      diningScene: '',
      mealTime: '',
      budgetLevel: '',
      dietaryRestriction: '',
      tastePreferences: [],
      avoidIngredients: [],
      allergens: [],
      diseases: [],
      tastePreferencesActive: {},
      avoidIngredientsActive: {},
      allergensActive: {},
      diseasesActive: {},
      guests: [],
      activeGuestIndex: 0
    })
    wx.showToast({ title: '配置已重置', icon: 'none' })
  }
})
